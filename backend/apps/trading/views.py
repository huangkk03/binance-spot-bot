"""
Trading Views
"""
import logging
from decimal import Decimal, InvalidOperation
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from .models import CycleInstance, TradeRecord, CycleOpenRecord, InstanceEvent
from .serializers import (
    CycleInstanceSerializer,
    TradeRecordSerializer,
    CycleOpenRecordSerializer,
    InstanceEventSerializer,
)
from apps.trading.services.engine import TradingEngine

logger = logging.getLogger(__name__)


@api_view(['POST'])
def tick(request):
    """
    POST /api/v1/trading/tick
    Body: { "symbols": ["BTCUSDT", ...] }
    """
    symbols = request.data if isinstance(request.data, list) else request.data.get('symbols', [])
    if not symbols:
        from django.conf import settings
        symbols = settings.DEFAULT_SYMBOLS

    engine = TradingEngine()
    actions = engine.execute_tick(symbols)
    return Response({'success': True, 'actions': actions})


@api_view(['POST'])
def real_trade_open(request):
    """
    POST /api/v1/trading/real-trade/open
    Body: { "symbol": "BTCUSDT", "quote_amount": 50 }
    """
    symbol = request.data.get('symbol')
    quote_amount_str = request.data.get('quote_amount')

    if not symbol or not quote_amount_str:
        return Response(
            {'success': False, 'errors': ['symbol 和 quote_amount 必填']},
            status=status.HTTP_400_BAD_REQUEST
        )

    try:
        quote_amount = Decimal(str(quote_amount_str))
    except (InvalidOperation, ValueError):
        return Response(
            {'success': False, 'errors': ['quote_amount 格式错误']},
            status=status.HTTP_400_BAD_REQUEST
        )

    engine = TradingEngine()
    result = engine.manual_open_position(symbol, quote_amount)
    return Response(result)


@api_view(['GET'])
def instances_list(request):
    """
    GET /api/v1/trading/instances
    Query: ?symbol=BTCUSDT
    """
    symbol = request.query_params.get('symbol')

    qs = CycleInstance.objects.all()
    if symbol:
        qs = qs.filter(symbol=symbol)

    qs = qs.order_by('symbol', 'instance_id')
    serializer = CycleInstanceSerializer(qs, many=True)
    return Response(serializer.data)


@api_view(['GET'])
def history_events(request):
    """
    GET /api/v1/trading/history/events
    Query: ?symbol=&limit=100
    """
    symbol = request.query_params.get('symbol')
    limit = int(request.query_params.get('limit', 100))

    qs = InstanceEvent.objects.all()
    if symbol:
        qs = qs.filter(symbol=symbol)

    qs = qs.order_by('-created_at')[:limit]
    serializer = InstanceEventSerializer(qs, many=True)
    return Response(serializer.data)


@api_view(['GET'])
def history_orders(request):
    """
    GET /api/v1/trading/history/orders
    Query: ?symbol=&limit=100
    """
    symbol = request.query_params.get('symbol')
    limit = int(request.query_params.get('limit', 100))

    qs = TradeRecord.objects.all()
    if symbol:
        qs = qs.filter(symbol=symbol)

    qs = qs.order_by('-created_at')[:limit]
    serializer = TradeRecordSerializer(qs, many=True)
    return Response(serializer.data)
