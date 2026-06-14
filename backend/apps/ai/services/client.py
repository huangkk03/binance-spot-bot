"""
AI Client (OpenAI 兼容接口)
"""
import logging
import httpx

from apps.notifications.models import ApiConfig

logger = logging.getLogger(__name__)


class AiClient:
    """调用 OpenAI 兼容 API"""

    def __init__(self):
        self.url = ApiConfig.get_value('AI_API_URL', 'https://api.openai.com/v1')
        self.key = ApiConfig.get_value('AI_API_KEY', '')
        self.model = ApiConfig.get_value('AI_API_MODEL', 'gpt-3.5-turbo')

    def chat(self, system_prompt: str, user_prompt: str, max_tokens: int = 2000) -> str:
        """
        调用 chat/completions
        """
        if not self.key:
            return 'AI API Key 未配置。'

        try:
            headers = {
                'Authorization': f'Bearer {self.key}',
                'Content-Type': 'application/json',
            }
            body = {
                'model': self.model,
                'messages': [
                    {'role': 'system', 'content': system_prompt},
                    {'role': 'user', 'content': user_prompt},
                ],
                'max_tokens': max_tokens,
                'temperature': 0.7,
            }

            with httpx.Client(timeout=60) as client:
                response = client.post(
                    f'{self.url}/chat/completions',
                    headers=headers,
                    json=body
                )
                if response.status_code == 200:
                    data = response.json()
                    return data['choices'][0]['message']['content']
                else:
                    logger.error(f'AI API error: {response.status_code} {response.text[:200]}')
                    return f'AI 调用失败: {response.status_code}'
        except Exception as e:
            logger.error(f'AI client error: {e}')
            return f'AI 调用异常: {str(e)}'

    def get_btc_prediction(self) -> str:
        """获取 BTC 预测"""
        system = '你是一个加密货币交易分析师，专注于 BTC 市场分析。'
        user = '请基于近期 BTC 行情，给出短期价格预测、关键支撑位/阻力位、风险提示。回复控制在 500 字以内。'
        return self.chat(system, user, max_tokens=800)
