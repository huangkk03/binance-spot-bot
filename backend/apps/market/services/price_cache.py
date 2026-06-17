"""
价格缓存服务
使用 Redis 存储实时价格 (容错: Redis 不可用时返回 None)
"""
import logging
from decimal import Decimal
from typing import Optional, Dict
from django.core.cache import cache

logger = logging.getLogger(__name__)

PRICE_TTL = 3600


class PriceCacheService:

    @staticmethod
    def set_price(symbol: str, price: Decimal) -> None:
        try:
            cache.set(f'price:{symbol}', str(price), PRICE_TTL)
        except Exception:
            pass  # Redis down, skip

    @staticmethod
    def get_price(symbol: str) -> Optional[Decimal]:
        try:
            price = cache.get(f'price:{symbol}')
            if price is None:
                return None
            return Decimal(str(price))
        except Exception:
            return None

    @staticmethod
    def get_all_prices() -> Dict[str, Decimal]:
        result = {}
        for symbol in ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']:
            price = PriceCacheService.get_price(symbol)
            if price is not None:
                result[symbol] = price
        return result
