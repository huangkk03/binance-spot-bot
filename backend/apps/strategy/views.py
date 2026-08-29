"""
策略配置 Views
"""
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
from django.db.models import Q

from .models import StrategyConfig
from .serializers import StrategyConfigSerializer, StrategyConfigUpsertSerializer
from .services.strategy import StrategyService


@api_view(['GET'])
def config_list(request):
    """
    GET /api/v1/strategy/config
    Query: ?symbol=BTCUSDT
    """
    symbol = request.query_params.get('symbol')
    qs = StrategyConfig.objects.all()
    if symbol:
        qs = qs.filter(Q(symbol=symbol) | Q(symbol__isnull=True))
    qs = qs.order_by('config_key', 'symbol')
    serializer = StrategyConfigSerializer(qs, many=True)
    return Response(serializer.data)


@api_view(['GET'])
def config_effective(request, key):
    """
    GET /api/v1/strategy/config/{key}/effective?symbol=BTCUSDT
    获取该 key 在指定交易对的生效值
    """
    symbol = request.query_params.get('symbol') or None
    service = StrategyService()
    value = StrategyConfig.objects.get_effective_value(key, symbol)
    if value is None:
        return Response({'key': key, 'value': None, 'symbol': symbol}, status=status.HTTP_404_NOT_FOUND)
    return Response({'key': key, 'value': value, 'symbol': symbol})


@api_view(['GET', 'PUT', 'DELETE'])
def config_detail(request, key):
    """
    GET/PUT/DELETE /api/v1/strategy/config/{key}
    Query: ?symbol=BTCUSDT (可空表示全局)
    """
    symbol = request.query_params.get('symbol') or None

    if request.method == 'GET':
        obj = StrategyConfig.objects.filter(config_key=key, symbol=symbol).first()
        if not obj:
            return Response({'error': '未找到'}, status=status.HTTP_404_NOT_FOUND)
        return Response(StrategyConfigSerializer(obj).data)

    elif request.method == 'PUT':
        value = request.data.get('config_value')
        if value is None:
            return Response({'error': 'config_value 必填'}, status=status.HTTP_400_BAD_REQUEST)
        description = request.data.get('description', '')

        obj, _ = StrategyConfig.objects.update_or_create(
            config_key=key,
            symbol=symbol,
            defaults={'config_value': str(value), 'description': description}
        )
        return Response(StrategyConfigSerializer(obj).data)

    elif request.method == 'DELETE':
        deleted, _ = StrategyConfig.objects.filter(
            config_key=key, symbol=symbol
        ).delete()
        if deleted == 0:
            return Response({'error': '未找到'}, status=status.HTTP_404_NOT_FOUND)
        return Response({'deleted': True})


@api_view(['GET'])
def effective_all(request):
    """
    GET /api/v1/strategy/effective?symbol=BTCUSDT
    获取该交易对的所有生效配置
    """
    symbol = request.query_params.get('symbol') or None
    service = StrategyService()
    return Response(service.get_effective_config(symbol))
