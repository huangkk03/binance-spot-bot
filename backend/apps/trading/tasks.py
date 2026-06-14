"""
Trading Celery tasks
"""
import logging
from celery import shared_task
from django.conf import settings

logger = logging.getLogger(__name__)


@shared_task(name='apps.trading.tasks.execute_real_tick')
def execute_real_tick():
    """
    真实交易 tick (每 30 秒)
    """
    from apps.trading.services.engine import TradingEngine

    symbols = settings.DEFAULT_SYMBOLS
    try:
        engine = TradingEngine()
        actions = engine.execute_tick(symbols)
        if actions:
            logger.info(f'Real tick executed {len(actions)} actions: {actions[:5]}')
    except Exception as e:
        logger.error(f'Real tick failed: {e}', exc_info=True)
