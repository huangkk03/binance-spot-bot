"""
Frontend WebSocket Consumer
接收前端连接，转发 Binance 行情数据
"""
import json
import logging
from channels.generic.websocket import AsyncWebsocketConsumer

logger = logging.getLogger(__name__)

# Redis key for tracking connected client count
CLIENT_COUNT_KEY = 'ws:frontend:client_count'


async def _incr_client_count(amount=1):
    """增加/减少 Redis 中记录的客户端计数"""
    from channels.layers import get_channel_layer
    channel_layer = get_channel_layer()
    if hasattr(channel_layer, 'connection'):
        try:
            redis = channel_layer.connection(None)
            await redis.incrby(CLIENT_COUNT_KEY, amount)
        except Exception:
            pass


async def has_connected_clients() -> bool:
    """检查是否有前端 WS 客户端连接"""
    try:
        from channels.layers import get_channel_layer
        channel_layer = get_channel_layer()
        if hasattr(channel_layer, 'connection'):
            redis = channel_layer.connection(None)
            count = await redis.get(CLIENT_COUNT_KEY)
            return int(count or 0) > 0
    except Exception:
        pass
    return True  # 默认发送，避免 Redis 故障时丢数据


class FrontendConsumer(AsyncWebsocketConsumer):
    """
    接收前端 WebSocket 连接
    接收消息类型: PING, SUBSCRIBE
    推送消息类型: PRICE_UPDATE
    """

    async def connect(self):
        await self.channel_layer.group_add('frontend_clients', self.channel_name)
        await self.accept()
        await _incr_client_count(1)
        logger.info(f'Frontend WebSocket connected: {self.channel_name}')

    async def disconnect(self, close_code):
        await self.channel_layer.group_discard('frontend_clients', self.channel_name)
        await _incr_client_count(-1)
        logger.info(f'Frontend WebSocket disconnected: {self.channel_name}')

    async def receive(self, text_data=None, bytes_data=None):
        if not text_data:
            return
        try:
            data = json.loads(text_data)
            if data.get('type') == 'PING':
                await self.send(text_data=json.dumps({'type': 'PONG'}))
        except json.JSONDecodeError:
            pass

    async def price_update(self, event):
        await self.send(text_data=json.dumps({
            'type': 'PRICE_UPDATE',
            'data': event['data']
        }))
