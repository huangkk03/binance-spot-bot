"""
Futures Celery tasks
"""
import logging
from celery import shared_task
from django.conf import settings

logger = logging.getLogger(__name__)


@shared_task(name='apps.futures.tasks.execute_futures_tick')
def execute_futures_tick():
    """合约 tick (30 秒)"""
    from apps.futures.services.engine import FuturesEngine
    try:
        engine = FuturesEngine()
        actions = engine.execute_tick()
        if actions:
            logger.info(f'Futures tick: {len(actions)} actions')
    except Exception as e:
        logger.error(f'Futures tick failed: {e}', exc_info=True)
