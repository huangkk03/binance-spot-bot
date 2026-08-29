"""
Market Views (REST API for prices)
"""
import logging
from rest_framework.decorators import api_view
from rest_framework.response import Response
from django.conf import settings
from apps.market.services.price_cache import PriceCacheService
from apps.market.services.binance_ws import market_stream

logger = logging.getLogger(__name__)


@api_view(['GET'])
def prices_all(request):
    """
    GET /api/v1/market/prices
    获取所有默认币种的实时价格
    """
    result = {}
    for symbol in settings.DEFAULT_SYMBOLS:
        price = PriceCacheService.get_price(symbol)
        if price is not None:
            result[symbol] = str(price)
    return Response(result)


@api_view(['GET'])
def price_by_symbol(request, symbol):
    """
    GET /api/v1/market/prices/{symbol}
    """
    symbol = symbol.upper()
    price = PriceCacheService.get_price(symbol)
    if price is None:
        return Response({'error': f'价格不可用: {symbol}'}, status=404)
    return Response({'symbol': symbol, 'price': str(price)})


@api_view(['POST'])
def price_subscribe(request, symbol):
    """
    POST /api/v1/market/prices/subscribe/{symbol}
    重新订阅某个币种
    """
    # 简化: 重启 stream
    try:
        market_stream.stop()
        market_stream.start()
        return Response({'success': True, 'message': f'{symbol} 重新订阅已触发'})
    except Exception as e:
        logger.error(f'Subscribe failed: {e}')
        return Response({'success': False, 'error': str(e)}, status=500)
