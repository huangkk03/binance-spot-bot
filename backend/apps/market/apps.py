"""
Market App Config
启动时自动启动 Binance WebSocket 流
仅在 backend 容器中启动，避免 celery worker 重复启动
"""
import logging
import os
from django.apps import AppConfig

logger = logging.getLogger(__name__)


class MarketConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'apps.market'
    verbose_name = '行情'

    def ready(self):
        # 判断当前是否运行在 celery 进程中
        # celery worker/beat 会导入 Django，但不应启动 WebSocket
        is_celery = 'celery' in os.environ.get('_', '') or 'celery' in os.getcwd()

        # 仅在 backend (Daphne ASGI) 中启动 WebSocket
        # 1. 不是 celery 进程
        # 2. 没有禁用 WS 的环境变量
        if is_celery:
            logger.debug('Celery process detected, skipping WebSocket init')
            return

        if os.environ.get('DISABLE_WS') == 'true':
            logger.debug('WebSocket disabled by env var')
            return

        try:
            from apps.market.services.binance_ws import market_stream
            market_stream.start()
            logger.info('Binance market WebSocket stream initialized')
        except Exception as e:
            logger.error(f'Failed to start market stream: {e}')
