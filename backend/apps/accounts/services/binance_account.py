"""
Binance 账户服务
通过 python-binance Client 调用 /api/v3/account 实时查询余额
"""
import logging
from decimal import Decimal
from typing import Optional, Dict, List
from binance.client import Client
from binance.exceptions import BinanceAPIException, BinanceRequestException

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
        client = Client(
            api_key=self.account.get_secret() if False else self.account.api_key,  # api_key 不加密
            api_secret=self.account.get_secret(),
            testnet=self.account.testnet,
        )

        if self.account.use_proxy and self.account.proxy_url:
            # python-binance 支持 request_params 自定义代理
            # 简化：使用环境变量
            import os
            os.environ['HTTP_PROXY'] = self.account.proxy_url
            os.environ['HTTPS_PROXY'] = self.account.proxy_url

        return client

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
