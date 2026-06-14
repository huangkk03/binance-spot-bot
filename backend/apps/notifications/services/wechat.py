"""
WeChat Notifier (替代 Java NotificationService)
支持:
- Server酱 (sctapi.ftqq.com)
- 通用企业微信 / 钉钉 webhook
- Markdown 格式
"""
import logging
import json
import urllib.parse
import httpx

from apps.notifications.models import ApiConfig

logger = logging.getLogger(__name__)


class WeChatNotifier:
    """企业微信 / Server酱 通知"""

    async def send_text(self, text: str):
        """发送文本消息"""
        webhook_url = ApiConfig.get_value('WECHAT_WEBHOOK_URL')
        if not webhook_url:
            logger.debug('WeChat webhook not configured')
            return

        try:
            if 'sctapi.ftqq.com' in webhook_url:
                await self._send_server_chan(webhook_url, text)
            else:
                await self._send_generic_json(webhook_url, text)
        except Exception as e:
            logger.error(f'Failed to send WeChat text: {e}')

    async def send_markdown(self, content: str):
        """发送 Markdown 消息"""
        webhook_url = ApiConfig.get_value('WECHAT_WEBHOOK_URL')
        if not webhook_url or 'sctapi.ftqq.com' in webhook_url:
            # Server酱不支持 markdown，回退到文本
            await self.send_text(content)
            return

        try:
            payload = {
                'msgtype': 'markdown',
                'markdown': {'content': content}
            }
            async with httpx.AsyncClient(timeout=10) as client:
                response = await client.post(webhook_url, json=payload)
                if response.status_code != 200:
                    logger.warning(f'Markdown send failed: {response.status_code} {response.text}')
        except Exception as e:
            logger.error(f'Failed to send WeChat markdown: {e}')

    @staticmethod
    async def _send_server_chan(base_url: str, text: str):
        """Server酱 GET 请求"""
        encoded = urllib.parse.quote(text)
        url = f'{base_url}?text=Binance通知&desp={encoded}'
        async with httpx.AsyncClient(timeout=10) as client:
            response = await client.get(url)
            if response.status_code != 200:
                logger.warning(f'ServerChan failed: {response.status_code}')

    @staticmethod
    async def _send_generic_json(webhook_url: str, text: str):
        """通用企业微信/钉钉 JSON POST"""
        # 转义换行
        escaped = text.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')
        payload = {
            'msgtype': 'text',
            'text': {'content': f'Binance通知\n{escaped}'}
        }
        async with httpx.AsyncClient(timeout=10) as client:
            response = await client.post(webhook_url, json=payload)
            if response.status_code != 200:
                logger.warning(f'Generic WeChat send failed: {response.status_code}')
