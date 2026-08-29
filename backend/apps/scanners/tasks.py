"""
Celery tasks for scanners
"""
import asyncio
import logging
from celery import shared_task
from django.conf import settings

logger = logging.getLogger(__name__)


@shared_task(name='apps.scanners.tasks.scan_rsi_indicators')
def scan_rsi_indicators():
    """每分钟扫描 RSI"""
    from apps.scanners.services.rsi import RSIScanner

    scanner = RSIScanner()
    try:
        asyncio.run(scanner.scan_all(settings.DEFAULT_SYMBOLS))
    except Exception as e:
        logger.error(f'RSI scan failed: {e}', exc_info=True)


@shared_task(name='apps.scanners.tasks.scan_td_indicators')
def scan_td_indicators():
    """每分钟扫描 TD Sequential"""
    from apps.scanners.services.td import TDScanner

    scanner = TDScanner()
    try:
        asyncio.run(scanner.scan_all(settings.DEFAULT_SYMBOLS))
    except Exception as e:
        logger.error(f'TD scan failed: {e}', exc_info=True)


@shared_task(name='apps.scanners.tasks.scan_funding_rates')
def scan_funding_rates():
    """每5分钟扫描资金费率"""
    from apps.scanners.services.funding_rate import FundingRateScanner

    scanner = FundingRateScanner()
    try:
        asyncio.run(scanner.scan())
    except Exception as e:
        logger.error(f'Funding rate scan failed: {e}', exc_info=True)
