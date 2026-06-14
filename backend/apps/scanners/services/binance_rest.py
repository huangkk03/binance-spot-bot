"""
Binance REST API 公共请求服务
用于扫描器获取 K线、资金费率等
"""
import logging
import httpx
from typing import List, Dict, Optional
from decimal import Decimal

logger = logging.getLogger(__name__)


class BinanceRestService:
    """异步调用 Binance 公共 REST API"""

    BINANCE_BASE = 'https://api.binance.com'
    BINANCE_FUTURES = 'https://fapi.binance.com'

    @staticmethod
    async def fetch_klines(symbol: str, interval: str, limit: int = 100) -> Optional[List]:
        """
        获取 K 线数据
        端点: GET /api/v3/klines
        返回: [[openTime, open, high, low, close, volume, ...], ...]
        """
        url = f'{BinanceRestService.BINANCE_BASE}/api/v3/klines'
        params = {'symbol': symbol, 'interval': interval, 'limit': limit}

        try:
            async with httpx.AsyncClient(timeout=10) as client:
                response = await client.get(url, params=params)
                if response.status_code == 200:
                    return response.json()
                logger.warning(f'Kline fetch failed: {response.status_code} {response.text}')
                return None
        except Exception as e:
            logger.error(f'Kline fetch exception: {e}')
            return None

    @staticmethod
    async def fetch_premium_index(symbols: List[str]) -> Optional[List[Dict]]:
        """
        获取合约资金费率
        端点: GET /fapi/v1/premiumIndex
        """
        url = f'{BinanceRestService.BINANCE_FUTURES}/fapi/v1/premiumIndex'

        try:
            async with httpx.AsyncClient(timeout=10) as client:
                response = await client.get(url)
                if response.status_code == 200:
                    data = response.json()
                    # 过滤指定 symbols
                    return [d for d in data if d.get('symbol') in symbols]
                logger.warning(f'Premium index fetch failed: {response.status_code}')
                return None
        except Exception as e:
            logger.error(f'Premium index fetch exception: {e}')
            return None
