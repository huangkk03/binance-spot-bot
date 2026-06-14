"""
Frontend WebSocket Consumer
接收前端连接，转发 Binance 行情数据
"""
import json
import logging
from channels.generic.websocket import AsyncWebsocketConsumer

logger = logging.getLogger(__name__)


class FrontendConsumer(AsyncWebsocketConsumer):
    """
    接收前端 WebSocket 连接
    接收消息类型: PING, SUBSCRIBE
    推送消息类型: PRICE_UPDATE
    """

    async def connect(self):
        await self.channel_layer.group_add('frontend_clients', self.channel_name)
        await self.accept()
        logger.info(f'Frontend WebSocket connected: {self.channel_name}')

    async def disconnect(self, close_code):
        await self.channel_layer.group_discard('frontend_clients', self.channel_name)
        logger.info(f'Frontend WebSocket disconnected: {self.channel_name}')

    async def receive(self, text_data=None, bytes_data=None):
        if not text_data:
            return
        try:
            data = json.loads(text_data)
            msg_type = data.get('type', '')

            if msg_type == 'PING':
                await self.send(text_data=json.dumps({'type': 'PONG'}))
        except json.JSONDecodeError:
            logger.warning(f'Invalid JSON: {text_data}')

    # Handler called by channel_layer.group_send
    async def price_update(self, event):
        """收到价格更新消息，转发给前端"""
        await self.send(text_data=json.dumps({
            'type': 'PRICE_UPDATE',
            'data': event['data']
        }))
