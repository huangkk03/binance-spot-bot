"""
ASGI config for binance_bot project.
支持 HTTP (Daphne) + WebSocket (Channels)
"""
import os
from django.core.asgi import get_asgi_application

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'binance_bot.settings')

# 初始化 Django
django_asgi_app = get_asgi_application()

# 延迟导入 Channels（需要在 Django 初始化之后）
from channels.routing import ProtocolTypeRouter, URLRouter
from channels.auth import AuthMiddlewareStack
from apps.market.routing import websocket_urlpatterns as market_ws

application = ProtocolTypeRouter({
    'http': django_asgi_app,
    'websocket': AuthMiddlewareStack(
        URLRouter(
            market_ws
        )
    ),
})
