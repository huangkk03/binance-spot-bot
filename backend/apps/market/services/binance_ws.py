"""
Binance WebSocket Client (出站 → Binance)
使用 aiohttp 实现 WSS 客户端 (原生支持代理)

设计: 独立线程 + asyncio event loop
"""
import asyncio
import json
import logging
import os
import threading
import time
from decimal import Decimal

import aiohttp
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from django.conf import settings

from apps.market.services.price_cache import PriceCacheService

logger = logging.getLogger(__name__)


def _get_proxy_url() -> str:
    """从 settings 读取代理配置"""
    return (
        getattr(settings, 'BINANCE_PROXY_URL', '')
        or os.environ.get('BINANCE_PROXY_URL', '')
    )


def _setup_global_proxy():
    """
    设置全局代理环境变量 (供 REST API 使用)
    """
    proxy_url = _get_proxy_url()
    if proxy_url and proxy_url.strip():
        os.environ['HTTP_PROXY'] = proxy_url
        os.environ['HTTPS_PROXY'] = proxy_url
        no_proxy = 'localhost,127.0.0.1,172.16.0.0/12,192.168.0.0/16,10.0.0.0/8,mysql,redis,backend'
        os.environ['NO_PROXY'] = no_proxy
        os.environ['no_proxy'] = no_proxy
        logger.info(f'Proxy configured: {proxy_url} (NO_PROXY: {no_proxy})')
        return proxy_url
    return None


# 模块加载时设置一次
_GLOBAL_PROXY = _setup_global_proxy()


class BinanceMarketStream:
    """
    通过 aiohttp 实现 WSS 客户端 (原生支持代理)

    接收 trade 事件后:
    1. 更新本地 Redis 价格缓存
    2. 推送到前端 WebSocket
    """

    WS_BASE = 'wss://stream.binance.com:9443/ws/'

    def __init__(self):
        self._running = False
        self._thread: threading.Thread = None
        self._loop: asyncio.AbstractEventLoop = None
        self._first_trade_logged = False
        self._session: aiohttp.ClientSession = None

    def start(self):
        """启动 WebSocket 订阅"""
        if self._running:
            logger.warning('BinanceMarketStream already running')
            return

        self._running = True
        self._thread = threading.Thread(target=self._thread_main, daemon=True)
        self._thread.start()
        logger.info('BinanceMarketStream started')

    def _thread_main(self):
        """线程主入口"""
        self._loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        try:
            self._loop.run_until_complete(self._run())
        except Exception as e:
            logger.error(f'BinanceMarketStream thread error: {e}', exc_info=True)
            self._running = False
        finally:
            if self._session and not self._session.closed:
                try:
                    self._loop.run_until_complete(self._session.close())
                except Exception:
                    pass
            self._loop.close()

    async def _run(self):
        """asyncio 主循环: 自动重连"""
        proxy_url = _get_proxy_url()
        logger.info(f'WS using proxy: {proxy_url or "DIRECT"}')

        # trust_env=True 让 aiohttp 读 HTTP_PROXY/HTTPS_PROXY 环境变量
        # 同时显式传 proxy 参数确保生效
        self._session = aiohttp.ClientSession(
            trust_env=True,
            timeout=aiohttp.ClientTimeout(total=30),
        )

        while self._running:
            try:
                await self._connect_and_listen(proxy_url)
            except Exception as e:
                logger.error(f'Binance WS error: {e}', exc_info=True)
                if self._running:
                    logger.info('Reconnecting in 5s...')
                    await asyncio.sleep(5)

    async def _connect_and_listen(self, proxy_url: str):
        """建立 WSS 连接 (支持代理), 接收所有币种 trade 流

        combined stream URL 格式: /stream?streams=...
        单一 stream URL 格式: /ws/{symbol}@trade
        """
        streams = [f'{s.lower()}@trade' for s in settings.DEFAULT_SYMBOLS]
        # 使用单一 stream 格式（更稳定，combined 在某些代理下有兼容问题）
        # 每个币种单独连接
        url = f'wss://stream.binance.com:9443/ws/{"@trade/".join(s.replace("@trade", "") for s in streams)}@trade'

        logger.info(f'Connecting to single streams... (proxy={proxy_url or "DIRECT"})')

        # 单一合并 stream 实际上格式是: /ws/symbol@trade/symbol@trade/...
        # 重新构造
        url = 'wss://stream.binance.com:9443/stream?streams=' + '/'.join(streams)

        logger.info(f'Connecting to {url[:100]}... (proxy={proxy_url or "DIRECT"})')

        async with self._session.ws_connect(
            url,
            proxy=proxy_url if proxy_url else None,
            autoclose=True,
            autoping=True,
            heartbeat=20,
            timeout=30,
        ) as ws:
            logger.info(f'WSS connected! Subscribed to {len(streams)} trade streams')

            async for msg in ws:
                if not self._running:
                    break
                if msg.type == aiohttp.WSMsgType.TEXT:
                    try:
                        data = json.loads(msg.data)
                        # 启动后第一次收到 trade 打印 INFO
                        if not self._first_trade_logged:
                            inner = data.get('data', {})
                            sym = inner.get('s', '?')
                            price = inner.get('p', '?')
                            logger.info(f'WS first trade received: {sym} @ {price}')
                            self._first_trade_logged = True
                        self._handle_trade_message(data)
                    except json.JSONDecodeError:
                        logger.warning(f'Invalid JSON: {msg.data[:100]}')
                elif msg.type == aiohttp.WSMsgType.ERROR:
                    logger.error(f'WS error: {msg.data}')

    def _handle_trade_message(self, msg):
        """
        处理 trade 消息
        单一 stream: {'e': 'trade', 's': 'BTCUSDT', 'p': '50000.00', ...}
        combined stream: {'stream': 'btcusdt@trade', 'data': {'e': 'trade', ...}}
        """
        try:
            # combined stream 格式: 包了一层 {stream, data}
            if 'data' in msg and isinstance(msg.get('data'), dict):
                data = msg['data']
            elif len(msg) == 1 and isinstance(list(msg.values())[0], dict):
                # 兼容: {'btcusdt@trade': {...}} 这种 (无 stream/data 包装)
                data = list(msg.values())[0]
            else:
                data = msg

            if data.get('e') != 'trade':
                return

            symbol = data.get('s', '').upper()
            price_str = data.get('p', '0')
            price = Decimal(price_str)

            if not symbol or price <= 0:
                return

            # 1. 更新价格缓存
            PriceCacheService.set_price(symbol, price)

            # 2. 推送到前端 WebSocket
            channel_layer = get_channel_layer()
            if channel_layer and self._loop and self._loop.is_running():
                try:
                    asyncio.run_coroutine_threadsafe(
                        channel_layer.group_send(
                            'frontend_clients',
                            {
                                'type': 'price_update',
                                'data': {
                                    'symbol': symbol,
                                    'price': str(price),
                                }
                            }
                        ),
                        self._loop
                    )
                except Exception as e:
                    logger.debug(f'Channel send error: {e}')

            logger.debug(f'Price update: {symbol} = {price}')
        except Exception as e:
            logger.error(f'Trade handler error: {e}', exc_info=True)

    def stop(self):
        """停止 WebSocket 订阅"""
        self._running = False
        if self._loop and not self._loop.is_closed():
            try:
                self._loop.call_soon_threadsafe(self._loop.stop)
            except Exception:
                pass
        logger.info('BinanceMarketStream stopped')


# 单例
market_stream = BinanceMarketStream()
