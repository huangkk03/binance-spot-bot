"""
Reports Celery tasks
"""
import logging
from celery import shared_task

logger = logging.getLogger(__name__)


@shared_task(name='apps.reports.tasks.generate_daily_btc_report')
def generate_daily_btc_report():
    """
    每日 8:00 自动生成 BTC AI 报告
    缓存到本地文件 (logs/btc_report_YYYYMMDD.pdf)
    """
    import os
    from datetime import datetime
    from django.conf import settings
    from .services.pdf import PdfReportService
    from apps.ai.services.client import AiClient

    try:
        # 1. 调用 AI 获取分析
        ai = AiClient()
        content = ai.get_btc_prediction()

        # 2. 生成 PDF
        pdf_bytes = PdfReportService.generate_btc_prediction_report(content, [])

        # 3. 保存到 logs 目录
        logs_dir = getattr(settings, 'BASE_DIR', '/app') / 'logs'
        logs_dir.mkdir(parents=True, exist_ok=True)

        filename = f'btc_report_{datetime.now().strftime("%Y%m%d")}.pdf'
        filepath = logs_dir / filename

        with open(filepath, 'wb') as f:
            f.write(pdf_bytes)

        logger.info(f'Daily BTC report generated: {filepath}')

        return {'success': True, 'path': str(filepath), 'size': len(pdf_bytes)}
    except Exception as e:
        logger.error(f'Failed to generate daily BTC report: {e}', exc_info=True)
        return {'success': False, 'error': str(e)}
