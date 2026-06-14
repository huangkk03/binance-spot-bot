"""
Market App Config
启动时自动启动 Binance WebSocket 流
"""
import logging
from django.apps import AppConfig

logger = logging.getLogger(__name__)


class MarketConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'apps.market'
    verbose_name = '行情'

    def ready(self):
        # 仅在主进程中启动 WebSocket（避免 runserver 重复启动）
        import os
        if os.environ.get('RUN_MAIN') == 'true' or 'DISABLE_WS' not in os.environ:
            try:
                from apps.market.services.binance_ws import market_stream
                market_stream.start()
                logger.info('Binance market WebSocket stream initialized')
            except Exception as e:
                logger.error(f'Failed to start market stream: {e}')
