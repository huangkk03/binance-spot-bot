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
        """构造 python-binance Client

        重要: 仅在账户级别需要代理时才设置 (REST API 通常不需要代理)
        WebSocket 代理在 binance_ws.py 中单独处理
        """
        client_kwargs = {
            'api_key': self.account.api_key,
            'api_secret': self.account.get_secret(),
            'testnet': self.account.testnet,
        }

        # 仅当账户显式配置代理时才使用 (use_proxy=True + proxy_url 非空)
        if self.account.use_proxy and self.account.proxy_url:
            client_kwargs['requests_params'] = {
                'proxies': {
                    'http': self.account.proxy_url,
                    'https': self.account.proxy_url,
                }
            }
            logger.info(f'Binance client using account proxy: {self.account.proxy_url}')

        logger.debug(f'Building client: testnet={client_kwargs["testnet"]}, proxy_enabled={self.account.use_proxy}')
        return Client(**client_kwargs)

    def get_account_info(self) -> Optional[Dict]:
        """
        查询账户完整信息
        端点: GET /api/v3/account
        """
        try:
            logger.info(f'Calling get_account_info: testnet={self.account.testnet}')
            result = self.client.get_account()
            logger.info(f'get_account_info OK: canTrade={result.get("canTrade")}')
            return result
        except BinanceAPIException as e:
            logger.error(f'Binance API error ({e.status_code}): {e.message}')
            return None
        except BinanceRequestException as e:
            logger.error(f'Binance request error: {type(e).__name__}: {e}')
            return None
        except Exception as e:
            logger.error(f'Unexpected error fetching account info: {type(e).__name__}: {e}', exc_info=True)
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
        返回: { 'success': bool, 'message': str }
        """
        logger.info(f'Testing connection: testnet={self.account.testnet}, use_proxy={self.account.use_proxy}')
        try:
            info = self.get_account_info()
            if info is None:
                logger.warning('test_connection: get_account_info returned None')
                return {'success': False, 'message': '无法连接到 Binance API'}

            logger.info(f'Connection OK: canTrade={info.get("canTrade")}, accountType={info.get("accountType")}')
            return {
                'success': True,
                'message': '连接成功',
                'can_trade': info.get('canTrade', False),
                'account_type': info.get('accountType', ''),
            }
        except BinanceAPIException as e:
            logger.error(f'Binance API error: {e.status_code} {e.message}')
            return {'success': False, 'message': f'API 错误: {e.message}'}
        except Exception as e:
            logger.error(f'Connection test exception: {type(e).__name__}: {e}', exc_info=True)
            return {'success': False, 'message': f'连接失败: {str(e)}'}
