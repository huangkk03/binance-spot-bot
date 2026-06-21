"""
Futures API Views
"""
from decimal import Decimal, InvalidOperation
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from .models import FuturesInstance
from .serializers import FuturesInstanceSerializer
from .services.engine import FuturesEngine


@api_view(['POST'])
def futures_open(request):
    """
    POST /api/v1/futures/open
    Body: { symbol, direction, notional, leverage }
    """
    symbol = request.data.get('symbol', '').upper()
    direction = request.data.get('direction', 'LONG').upper()
    notional_str = request.data.get('notional', '0')
    leverage = int(request.data.get('leverage', 100))

    if not symbol or direction not in ('LONG', 'SHORT'):
        return Response({'success': False, 'errors': ['symbol 和 direction(LONG/SHORT) 必填']}, status=400)

    try:
        notional = Decimal(str(notional_str))
    except (InvalidOperation, ValueError):
        return Response({'success': False, 'errors': ['notional 格式错误']}, status=400)

    engine = FuturesEngine()
    result = engine.manual_open(symbol, direction, notional, leverage)
    return Response(result)


@api_view(['POST'])
def futures_close(request, pk):
    """POST /api/v1/futures/close/{id}"""
    try:
        inst = FuturesInstance.objects.get(pk=pk, is_open=True)
    except FuturesInstance.DoesNotExist:
        return Response({'success': False, 'errors': ['实例不存在或已平仓']}, status=404)

    engine = FuturesEngine()
    result = engine._execute_close(inst, Decimal('0'), 'MANUAL_CLOSE')
    return Response({'success': bool(result), 'message': result or '平仓失败'})


@api_view(['GET'])
def futures_instances(request):
    """GET /api/v1/futures/instances"""
    symbol = request.query_params.get('symbol')
    qs = FuturesInstance.objects.all()
    if symbol: qs = qs.filter(symbol=symbol.upper())
    qs = qs.order_by('symbol', 'instance_id')
    return Response(FuturesInstanceSerializer(qs, many=True).data)


@api_view(['POST'])
def futures_tick(request):
    """POST /api/v1/futures/tick"""
    engine = FuturesEngine()
    actions = engine.execute_tick()
    return Response({'success': True, 'actions': actions})


@api_view(['GET'])
def futures_history_events(request):
    """GET /api/v1/futures/history/events"""
    from apps.trading.models import InstanceEvent
    symbol = request.query_params.get('symbol')
    limit = int(request.query_params.get('limit', 100))
    qs = InstanceEvent.objects.filter(event__startswith='FUTURES_')
    if symbol: qs = qs.filter(symbol=symbol.upper())
    qs = qs.order_by('-created_at')[:limit]
    from apps.trading.serializers import InstanceEventSerializer
    return Response(InstanceEventSerializer(qs, many=True).data)
