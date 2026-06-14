"""
Notifications Views
"""
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
import asyncio

from .models import ApiConfig
from .services.wechat import WeChatNotifier


@api_view(['GET', 'PUT'])
def config_detail(request, key):
    """
    GET/PUT /api/v1/notifications/config/{key}
    """
    if request.method == 'GET':
        value = ApiConfig.get_value(key)
        if value is None:
            return Response({'key': key, 'value': None}, status=status.HTTP_404_NOT_FOUND)
        return Response({'key': key, 'value': value})

    elif request.method == 'PUT':
        new_value = request.data.get('value')
        if new_value is None:
            return Response({'error': 'value 必填'}, status=status.HTTP_400_BAD_REQUEST)
        obj, _ = ApiConfig.objects.update_or_create(
            config_key=key,
            defaults={'config_value': new_value}
        )
        return Response({'key': key, 'value': obj.config_value})


@api_view(['GET'])
def config_list(request):
    """GET /api/v1/notifications/config"""
    configs = ApiConfig.objects.all()
    return Response([
        {'key': c.config_key, 'value': c.config_value, 'updated_at': c.updated_at}
        for c in configs
    ])


@api_view(['POST'])
def test_notification(request):
    """
    POST /api/v1/notifications/test-notification
    Body: { "title": "...", "content": "..." }
    """
    title = request.data.get('title', 'Test')
    content = request.data.get('content', 'Test message')

    notifier = WeChatNotifier()
    asyncio.run(notifier.send_text(f'[{title}] {content}'))

    return Response({'success': True, 'message': '测试通知已发送'})


@api_view(['POST'])
def test_ai(request):
    """
    POST /api/v1/notifications/test-ai
    Body: { "url": "...", "key": "...", "model": "..." }
    """
    url = request.data.get('url')
    key = request.data.get('key')
    model = request.data.get('model')

    if not all([url, key, model]):
        return Response({'success': False, 'error': 'url/key/model 必填'}, status=400)

    try:
        import httpx
        headers = {
            'Authorization': f'Bearer {key}',
            'Content-Type': 'application/json'
        }
        body = {
            'model': model,
            'messages': [{'role': 'user', 'content': 'test'}],
            'max_tokens': 5
        }
        with httpx.Client(timeout=15) as client:
            response = client.post(f'{url}/chat/completions', headers=headers, json=body)
            return Response({
                'success': response.status_code == 200,
                'status': response.status_code,
                'response': response.text[:200]
            })
    except Exception as e:
        return Response({'success': False, 'error': str(e)}, status=500)
