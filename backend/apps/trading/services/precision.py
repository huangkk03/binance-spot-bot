"""
精度量化服务
替代 Java BinanceApiService 的精度缓存逻辑
"""
import logging
from decimal import Decimal, ROUND_DOWN
from binance.client import Client
from binance.exceptions import BinanceAPIException
from django.core.cache import cache

logger = logging.getLogger(__name__)

# 缓存 TTL (秒): 24小时
CACHE_TTL = 86400


class PrecisionService:
    """
    获取并缓存交易对的:
    - stepSize (数量步长)
    - tickSize (价格步长)
    - minQty / minNotional
    """

    @staticmethod
    def get_symbol_info(client: Client, symbol: str) -> dict:
        """
        从 Binance 获取交易对精度信息（带缓存）
        """
        cache_key = f'symbol_info:{symbol}'
        cached = cache.get(cache_key)
        if cached:
            return cached

        try:
            info = client.get_symbol_info(symbol)
            if not info:
                return {}

            # 提取关键字段
            result = {
                'symbol': info['symbol'],
                'baseAsset': info['baseAsset'],
                'quoteAsset': info['quoteAsset'],
            }

            for f in info.get('filters', []):
                if f['filterType'] == 'LOT_SIZE':
                    result['minQty'] = Decimal(f['minQty'])
                    result['maxQty'] = Decimal(f['maxQty'])
                    result['stepSize'] = Decimal(f['stepSize'])
                elif f['filterType'] == 'PRICE_FILTER':
                    result['minPrice'] = Decimal(f['minPrice'])
                    result['maxPrice'] = Decimal(f['maxPrice'])
                    result['tickSize'] = Decimal(f['tickSize'])
                elif f['filterType'] == 'NOTIONAL':
                    result['minNotional'] = Decimal(f.get('minNotional', '0'))
                    result['applyMinToMarket'] = f.get('applyMinToMarket', True)
                elif f['filterType'] == 'MIN_NOTIONAL':
                    result['minNotional'] = Decimal(f.get('minNotional', '0'))

            cache.set(cache_key, result, CACHE_TTL)
            return result

        except BinanceAPIException as e:
            logger.error(f'Failed to get symbol info for {symbol}: {e}')
            return {}
        except Exception as e:
            logger.error(f'Error getting symbol info for {symbol}: {e}')
            return {}

    @staticmethod
    def quantize_quantity(quantity: Decimal, step_size: Decimal) -> Decimal:
        """按 stepSize 向下取整"""
        if step_size == 0:
            return quantity
        precision = -step_size.as_tuple().exponent
        return (quantity // step_size) * step_size

    @staticmethod
    def quantize_price(price: Decimal, tick_size: Decimal) -> Decimal:
        """按 tickSize 向下取整"""
        if tick_size == 0:
            return price
        return (price // tick_size) * tick_size

    @staticmethod
    def format_qty(quantity: Decimal, step_size: Decimal) -> str:
        """格式化为字符串（无尾随零）"""
        q = PrecisionService.quantize_quantity(quantity, step_size)
        if step_size == 0:
            return str(q)
        # 去除尾随零
        s = str(q.normalize())
        return s
