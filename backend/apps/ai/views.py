"""
AI Views
"""
from rest_framework.decorators import api_view
from rest_framework.response import Response
from .services.client import AiClient


@api_view(['POST'])
def chat(request):
    """
    POST /api/v1/ai/chat
    Body: { "system": "...", "user": "..." }
    """
    system = request.data.get('system', '你是一个助手。')
    user = request.data.get('user', '')
    if not user:
        return Response({'success': False, 'error': 'user 必填'}, status=400)

    ai = AiClient()
    content = ai.chat(system, user)
    return Response({'success': True, 'content': content})
