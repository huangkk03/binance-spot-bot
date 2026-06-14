"""
API 账户管理 + 余额查询 Views
"""
import logging
import os
import urllib.parse
from django.db import transaction
from django.conf import settings
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from .models import ApiAccount
from .serializers import (
    ApiAccountSerializer,
    ApiAccountCreateSerializer,
    BalanceSerializer,
)
from .services.binance_account import BinanceAccountService

logger = logging.getLogger(__name__)


@api_view(['GET', 'POST'])
def account_list(request):
    """
    GET /api/v1/accounts/ - 列出所有 API 账户
    POST /api/v1/accounts/ - 创建 API 账户
    """
    if request.method == 'GET':
        accounts = ApiAccount.objects.all().order_by('-is_active', '-updated_at')
        serializer = ApiAccountSerializer(accounts, many=True)
        return Response(serializer.data)

    elif request.method == 'POST':
        serializer = ApiAccountCreateSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        # 如果新账户 is_active=True，先取消其他激活
        if serializer.validated_data.get('is_active'):
            with transaction.atomic():
                ApiAccount.objects.filter(is_active=True).update(is_active=False)
                account = serializer.save()
        else:
            account = serializer.save()

        return Response(
            ApiAccountSerializer(account).data,
            status=status.HTTP_201_CREATED
        )


@api_view(['GET', 'PUT', 'DELETE'])
def account_detail(request, pk):
    """
    GET/PUT/DELETE /api/v1/accounts/{id}
    """
    try:
        account = ApiAccount.objects.get(pk=pk)
    except ApiAccount.DoesNotExist:
        return Response({'error': '账户不存在'}, status=status.HTTP_404_NOT_FOUND)

    if request.method == 'GET':
        serializer = ApiAccountSerializer(account)
        return Response(serializer.data)

    elif request.method == 'PUT':
        serializer = ApiAccountCreateSerializer(account, data=request.data, partial=True)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        if serializer.validated_data.get('is_active'):
            with transaction.atomic():
                ApiAccount.objects.filter(is_active=True).exclude(pk=pk).update(is_active=False)
                account = serializer.save()
        else:
            account = serializer.save()

        return Response(ApiAccountSerializer(account).data)

    elif request.method == 'DELETE':
        account.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(['POST'])
def account_activate(request, pk):
    """
    POST /api/v1/accounts/{id}/activate
    激活指定账户（取消其他激活）
    """
    try:
        account = ApiAccount.objects.get(pk=pk)
    except ApiAccount.DoesNotExist:
        return Response({'error': '账户不存在'}, status=status.HTTP_404_NOT_FOUND)

    with transaction.atomic():
        ApiAccount.objects.filter(is_active=True).exclude(pk=pk).update(is_active=False)
        account.is_active = True
        account.save()

    return Response(ApiAccountSerializer(account).data)


@api_view(['POST'])
def account_test(request):
    """
    POST /api/v1/accounts/test
    测试 API 凭据（不存储）
    Body: { api_key, api_secret, testnet, proxy_url }
    """
    api_key = request.data.get('api_key')
    api_secret = request.data.get('api_secret')
    testnet = request.data.get('testnet', True)
    use_proxy = request.data.get('use_proxy', False)
    proxy_url = request.data.get('proxy_url', '')

    if not api_key or not api_secret:
        return Response(
            {'success': False, 'message': 'api_key 和 api_secret 必填'},
            status=status.HTTP_400_BAD_REQUEST
        )

    # 构造临时账户对象测试
    class TempAccount:
        def __init__(self):
            self.api_key = api_key
            self.api_secret = api_secret  # 直接传明文
            self.testnet = testnet
            self.use_proxy = use_proxy
            self.proxy_url = proxy_url

        def get_secret(self):
            return self.api_secret

    service = BinanceAccountService(TempAccount())
    result = service.test_connection()
    return Response(result)


@api_view(['GET'])
def account_balance_all(request):
    """
    GET /api/v1/accounts/balance
    查询当前激活账户的所有余额（实时调用 Binance API）
    """
    account = ApiAccount.get_active()
    if not account:
        return Response(
            {'error': '没有激活的 API 账户'},
            status=status.HTTP_404_NOT_FOUND
        )

    service = BinanceAccountService(account)
    balances = service.get_all_balances(only_nonzero=True)

    return Response({
        'account_id': account.id,
        'account_name': account.account_name,
        'testnet': account.testnet,
        'balances': BalanceSerializer(balances, many=True).data,
    })


@api_view(['GET'])
def proxy_status(request):
    """
    GET /api/v1/accounts/proxy-status
    返回代理配置状态（前端展示用，不泄露敏感信息）
    """
    proxy_url = getattr(settings, 'BINANCE_PROXY_URL', '') or os.environ.get('BINANCE_PROXY_URL', '')
    configured = bool(proxy_url and proxy_url.strip())
    host = None
    if configured:
        try:
            host = urllib.parse.urlparse(proxy_url).hostname
        except Exception:
            host = None
    return Response({
        'configured': configured,
        'host': host,
        'value': proxy_url if configured else '',
    })


@api_view(['GET'])
def account_balance_asset(request, asset):
    """
    GET /api/v1/accounts/balance/{asset}
    查询单个币种余额
    """
    account = ApiAccount.get_active()
    if not account:
        return Response(
            {'error': '没有激活的 API 账户'},
            status=status.HTTP_404_NOT_FOUND
        )

    service = BinanceAccountService(account)
    balance = service.get_balance(asset.upper())

    if not balance:
        return Response(
            {'error': f'查询 {asset} 余额失败'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR
        )

    return Response(BalanceSerializer(balance).data)


@api_view(['GET'])
def account_balance_by_id(request, pk):
    """
    GET /api/v1/accounts/{id}/balances
    查询指定账户的所有余额
    """
    try:
        account = ApiAccount.objects.get(pk=pk)
    except ApiAccount.DoesNotExist:
        return Response({'error': '账户不存在'}, status=status.HTTP_404_NOT_FOUND)

    service = BinanceAccountService(account)
    balances = service.get_all_balances(only_nonzero=True)

    return Response({
        'account_id': account.id,
        'account_name': account.account_name,
        'testnet': account.testnet,
        'balances': BalanceSerializer(balances, many=True).data,
    })


@api_view(['GET'])
def proxy_status(request):
    """
    GET /api/v1/accounts/proxy-status
    返回代理配置状态（前端展示用，不泄露敏感信息）
    """
    proxy_url = getattr(settings, 'BINANCE_PROXY_URL', '') or os.environ.get('BINANCE_PROXY_URL', '')
    configured = bool(proxy_url and proxy_url.strip())
    host = None
    if configured:
        try:
            host = urllib.parse.urlparse(proxy_url).hostname
        except Exception:
            host = None
    return Response({
        'configured': configured,
        'host': host,
        'value': proxy_url if configured else '',
    })
