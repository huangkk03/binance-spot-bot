"""
Reports Views
"""
from django.http import HttpResponse
from rest_framework.decorators import api_view
from rest_framework.response import Response

from .services.pdf import PdfReportService, generate_btc_prediction_text
from apps.ai.services.client import AiClient


@api_view(['GET'])
def btc_prediction_pdf(request):
    """
    GET /api/v1/reports/btc-prediction/pdf
    """
    try:
        ai = AiClient()
        content = ai.get_btc_prediction()
    except Exception:
        content = 'AI 服务暂时不可用。'

    pdf_bytes = PdfReportService.generate_btc_prediction_report(content, [])

    response = HttpResponse(pdf_bytes, content_type='application/pdf')
    response['Content-Disposition'] = 'attachment; filename="btc_prediction.pdf"'
    return response


@api_view(['GET'])
def btc_prediction_text(request):
    """GET /api/v1/reports/btc-prediction/text"""
    try:
        ai = AiClient()
        content = ai.get_btc_prediction()
    except Exception:
        content = 'AI 服务暂时不可用。'

    text = generate_btc_prediction_text(content)
    return Response({'content': text})
