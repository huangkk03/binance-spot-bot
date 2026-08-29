"""
Scanners Views
"""
import asyncio
import logging
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
from django.conf import settings

from .models import PriceAlert, FundingRateAlert
from .serializers import PriceAlertSerializer, FundingRateAlertSerializer

logger = logging.getLogger(__name__)


@api_view(['GET'])
def alerts_list(request):
    """
    GET /api/v1/scanners/alerts
    Query: ?symbol=BTCUSDT&interval=1h
    """
    symbol = request.query_params.get('symbol')
    interval = request.query_params.get('interval')

    qs = PriceAlert.objects.all()
    if symbol:
        qs = qs.filter(symbol=symbol)
    if interval:
        qs = qs.filter(kline_interval=interval)

    qs = qs.order_by('-created_at')[:200]
    serializer = PriceAlertSerializer(qs, many=True)
    return Response(serializer.data)


@api_view(['GET'])
def alerts_triggered(request):
    """GET /api/v1/scanners/alerts/triggered"""
    qs = PriceAlert.objects.filter(triggered=True).order_by('-last_notified_at')[:200]
    serializer = PriceAlertSerializer(qs, many=True)
    return Response(serializer.data)


@api_view(['GET'])
def funding_rate_alerts(request):
    """GET /api/v1/scanners/funding-rates"""
    qs = FundingRateAlert.objects.all().order_by('-updated_at')[:200]
    serializer = FundingRateAlertSerializer(qs, many=True)
    return Response(serializer.data)


@api_view(['POST'])
def trigger_scan(request):
    """
    POST /api/v1/scanners/alerts/scan
    手动触发所有扫描器
    """
    from .tasks import scan_rsi_indicators, scan_td_indicators, scan_funding_rates

    try:
        scan_rsi_indicators.delay()
        scan_td_indicators.delay()
        scan_funding_rates.delay()
        return Response({'success': True, 'message': '扫描已触发'})
    except Exception as e:
        logger.error(f'Trigger scan failed: {e}')
        return Response({'success': False, 'error': str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
