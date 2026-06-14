"""
PDF Report Generator (使用 WeasyPrint)
"""
import logging
from decimal import Decimal
from typing import List, Dict
from datetime import datetime
from django.http import HttpResponse
from django.template.loader import render_to_string

logger = logging.getLogger(__name__)


class PdfReportService:
    """生成 PDF 报告（WeasyPrint）"""

    @staticmethod
    def generate_btc_prediction_report(ai_content: str, kline_data: List[Dict]) -> bytes:
        """
        生成 BTC 预测 PDF
        :param ai_content: AI 分析内容 (markdown)
        :param kline_data: K线数据
        :return: PDF bytes
        """
        try:
            from weasyprint import HTML
            html_content = render_to_string('reports/btc_prediction.html', {
                'content': ai_content,
                'klines': kline_data,
                'generated_at': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            })
            pdf_bytes = HTML(string=html_content).write_pdf()
            return pdf_bytes
        except Exception as e:
            logger.error(f'PDF generation failed: {e}')
            # 返回空 PDF
            return b'%PDF-1.4\n%FDF-1.2\n'


def generate_btc_prediction_text(ai_content: str) -> str:
    """生成 BTC 预测纯文本"""
    return f"""=== BTC AI 预测报告 ===
生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

{ai_content}
"""
