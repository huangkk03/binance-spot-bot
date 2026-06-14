"""
Binance WebSocket Client (出站 → Binance)
使用 ThreadedWebsocketManager (python-binance)
"""
import logging
import threading
from decimal import Decimal
from binance import ThreadedWebsocketManager
from binance.exceptions import BinanceAPIException

from django.conf import settings
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer

from apps.market.services.price_cache import PriceCacheService

logger = logging.getLogger(__name__)


class BinanceMarketStream:
    """
    通过 python-binance 订阅行情
    接收 trade 事件后:
    1. 更新本地 Redis 价格缓存
    2. 推送到前端 WebSocket
    """

    def __init__(self):
        self.twm: ThreadedWebsocketManager = None
        self._running = False
        self._thread: threading.Thread = None

    def start(self):
        """启动 WebSocket 订阅"""
        if self._running:
            logger.warning('BinanceMarketStream already running')
            return

        self._running = True
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()
        logger.info('BinanceMarketStream started')

    def _run(self):
        """WebSocket 线程主循环"""
        try:
            self.twm = ThreadedWebsocketManager(api_key='', api_secret='')
            self.twm.start()

            # 订阅所有默认币种的 trade 流
            for symbol in settings.DEFAULT_SYMBOLS:
                stream_name = f'{symbol.lower()}@trade'
                self.twm.start_trade_socket(
                    callback=self._handle_trade_message,
                    symbol=symbol.lower()
                )
                logger.info(f'Subscribed to {stream_name}')

            # 阻塞主线程（直到 stop）
            while self._running:
                import time
                time.sleep(1)
        except Exception as e:
            logger.error(f'BinanceMarketStream error: {e}', exc_info=True)
            self._running = False
        finally:
            if self.twm:
                try:
                    self.twm.stop()
                except Exception:
                    pass

    def _handle_trade_message(self, msg):
        """
        处理 trade 消息
        格式: {'e': 'trade', 'E': ..., 's': 'BTCUSDT', 'p': '50000.00', ...}
        """
        try:
            if msg.get('e') != 'trade':
                return

            symbol = msg.get('s', '').upper()
            price_str = msg.get('p', '0')
            price = Decimal(price_str)

            if not symbol or price <= 0:
                return

            # 1. 更新价格缓存
            PriceCacheService.set_price(symbol, price)

            # 2. 推送到前端 WebSocket
            channel_layer = get_channel_layer()
            if channel_layer:
                async_to_sync(channel_layer.group_send)(
                    'frontend_clients',
                    {
                        'type': 'price_update',
                        'data': {
                            'symbol': symbol,
                            'price': str(price),
                        }
                    }
                )
        except Exception as e:
            logger.error(f'Error handling trade message: {e}')

    def stop(self):
        """停止 WebSocket 订阅"""
        self._running = False
        if self.twm:
            try:
                self.twm.stop()
            except Exception:
                pass
        logger.info('BinanceMarketStream stopped')


# 单例
market_stream = BinanceMarketStream()
