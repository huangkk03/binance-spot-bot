"""
价格缓存服务
使用 Redis 存储实时价格
"""
import logging
from decimal import Decimal
from typing import Optional, Dict
from django.core.cache import cache

logger = logging.getLogger(__name__)

# 缓存 TTL: 1小时
PRICE_TTL = 3600


class PriceCacheService:
    """
    实时价格缓存
    key: 'price:{symbol}'
    """

    @staticmethod
    def set_price(symbol: str, price: Decimal) -> None:
        cache.set(f'price:{symbol}', str(price), PRICE_TTL)

    @staticmethod
    def get_price(symbol: str) -> Optional[Decimal]:
        price = cache.get(f'price:{symbol}')
        if price is None:
            return None
        try:
            return Decimal(str(price))
        except Exception:
            return None

    @staticmethod
    def get_all_prices() -> Dict[str, Decimal]:
        """获取所有缓存价格（不推荐用于大量数据）"""
        result = {}
        for symbol in ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']:
            price = PriceCacheService.get_price(symbol)
            if price is not None:
                result[symbol] = price
        return result
