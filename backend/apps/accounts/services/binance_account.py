"""
Binance 账户服务
通过 python-binance Client 调用 /api/v3/account 实时查询余额
"""
import logging
import os
from decimal import Decimal
from typing import Optional, Dict, List
from binance.client import Client
from binance.exceptions import BinanceAPIException, BinanceRequestException
from django.conf import settings

from apps.accounts.models import ApiAccount

logger = logging.getLogger(__name__)


class BinanceAccountService:
    """
    调用 Binance API 实时查询账户信息
    """

    def __init__(self, account: ApiAccount):
        self.account = account
        self.client = self._build_client()

    def _build_client(self) -> Client:
        """构造 python-binance Client"""
        # 优先使用全局代理 (settings.BINANCE_PROXY_URL)
        global_proxy = getattr(settings, 'BINANCE_PROXY_URL', '') or os.environ.get('BINANCE_PROXY_URL', '')
        account_proxy = self.account.proxy_url if (self.account.use_proxy and self.account.proxy_url) else ''

        # 决定使用哪个代理
        proxy_url = account_proxy or global_proxy

        client_kwargs = {
            'api_key': self.account.api_key,
            'api_secret': self.account.get_secret(),
            'testnet': self.account.testnet,
        }

        if proxy_url:
            # python-binance 1.0.x: 通过 requests_params 传 proxies 给底层 requests
            # 参考: https://github.com/binance/binance-spot-api-docs
            client_kwargs['requests_params'] = {
                'proxies': {'http': proxy_url, 'https': proxy_url}
            }
            # 同时设置环境变量 (给 aiohttp 等子进程用)
            os.environ['HTTP_PROXY'] = proxy_url
            os.environ['HTTPS_PROXY'] = proxy_url
            logger.info(f'Binance client using proxy: {proxy_url}')

        return Client(**client_kwargs)

    def get_account_info(self) -> Optional[Dict]:
        """
        查询账户完整信息
        端点: GET /api/v3/account
        """
        try:
            return self.client.get_account()
        except BinanceAPIException as e:
            logger.error(f'Binance API error: {e.status_code} {e.message}')
            return None
        except BinanceRequestException as e:
            logger.error(f'Binance request error: {e}')
            return None
        except Exception as e:
            logger.error(f'Unexpected error fetching account info: {e}')
            return None

    def get_balance(self, asset: str) -> Optional[Dict]:
        """
        查询单个币种余额
        """
        info = self.get_account_info()
        if not info:
            return None

        for balance in info.get('balances', []):
            if balance['asset'] == asset:
                return {
                    'asset': asset,
                    'free': Decimal(balance['free']),
                    'locked': Decimal(balance['locked']),
                    'total': Decimal(balance['free']) + Decimal(balance['locked']),
                }
        # 未找到，视为 0
        return {
            'asset': asset,
            'free': Decimal(0),
            'locked': Decimal(0),
            'total': Decimal(0),
        }

    def get_all_balances(self, only_nonzero: bool = True) -> List[Dict]:
        """
        查询所有币种余额
        """
        info = self.get_account_info()
        if not info:
            return []

        result = []
        for balance in info.get('balances', []):
            free = Decimal(balance['free'])
            locked = Decimal(balance['locked'])
            total = free + locked

            if only_nonzero and total == 0:
                continue

            result.append({
                'asset': balance['asset'],
                'free': free,
                'locked': locked,
                'total': total,
            })

        return result

    def test_connection(self) -> Dict:
        """
        测试 API 凭据是否有效
        返回: { 'success': bool, 'message': str, 'account_info': dict (optional) }
        """
        try:
            info = self.get_account_info()
            if info is None:
                return {'success': False, 'message': '无法连接到 Binance API'}

            return {
                'success': True,
                'message': '连接成功',
                'can_trade': info.get('canTrade', False),
                'account_type': info.get('accountType', ''),
            }
        except BinanceAPIException as e:
            return {'success': False, 'message': f'API 错误: {e.message}'}
        except Exception as e:
            return {'success': False, 'message': f'连接失败: {str(e)}'}
