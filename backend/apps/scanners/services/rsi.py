"""
RSI 指标计算与扫描
"""
import logging
from decimal import Decimal
from datetime import datetime
from typing import List, Tuple, Optional
import asyncio

from django.utils import timezone
from django.db import transaction

from .binance_rest import BinanceRestService
from apps.scanners.models import PriceAlert
from apps.notifications.services.wechat import WeChatNotifier

logger = logging.getLogger(__name__)


class RSIScanner:
    """
    RSI (Relative Strength Index) 扫描器
    - 周期: 14
    - 超买阈值: 80
    - 超卖阈值: 20
    - 冷却时间: 按 interval 不同 (15m=15min, 1h=60min, 4h=240min, 1d=1440min)
    """

    INTERVALS = ['15m', '1h', '4h', '1d']
    COOLDOWN_MINUTES = {
        '15m': 15,
        '1h': 60,
        '4h': 240,
        '1d': 1440,
    }
    RSI_PERIOD = 14
    OVERBOUGHT = 80
    OVERSOLD = 20

    def __init__(self):
        self.wechat = WeChatNotifier()

    async def scan_all(self, symbols: List[str]):
        """扫描所有币种的所有周期"""
        for symbol in symbols:
            for interval in self.INTERVALS:
                try:
                    await self.scan_symbol_interval(symbol, interval)
                except Exception as e:
                    logger.error(f'RSI scan error {symbol} {interval}: {e}')

    async def scan_symbol_interval(self, symbol: str, interval: str):
        """扫描单个币种单个周期"""
        klines = await BinanceRestService.fetch_klines(symbol, interval, 100)
        if not klines or len(klines) < self.RSI_PERIOD + 1:
            return

        closes = [Decimal(str(k[4])) for k in klines]
        rsi = self.calculate_rsi(closes, self.RSI_PERIOD)
        if rsi is None:
            return

        current_price = closes[-1]
        logger.info(f'RSI for {symbol} {interval}: {rsi:.2f}')

        if rsi >= self.OVERBOUGHT:
            await self._handle_alert(symbol, interval, 'RSI_OVERBOUGHT', rsi, current_price)
        elif rsi <= self.OVERSOLD:
            await self._handle_alert(symbol, interval, 'RSI_OVERSOLD', rsi, current_price)
        else:
            # 价格正常，重置 triggered 标记
            await self._reset_alert(symbol, interval, 'RSI_OVERBOUGHT')
            await self._reset_alert(symbol, interval, 'RSI_OVERSOLD')

    @staticmethod
    def calculate_rsi(closes: List[Decimal], period: int = 14) -> Optional[float]:
        """Wilder 平滑法计算 RSI"""
        if len(closes) < period + 1:
            return None

        # 初始计算前 period 个涨跌幅
        gains = []
        losses = []
        for i in range(1, period + 1):
            diff = closes[i] - closes[i - 1]
            if diff >= 0:
                gains.append(float(diff))
                losses.append(0.0)
            else:
                gains.append(0.0)
                losses.append(float(-diff))

        avg_gain = sum(gains) / period
        avg_loss = sum(losses) / period

        # Wilder 平滑
        for i in range(period + 1, len(closes)):
            diff = closes[i] - closes[i - 1]
            gain = float(diff) if diff > 0 else 0.0
            loss = float(-diff) if diff < 0 else 0.0
            avg_gain = (avg_gain * (period - 1) + gain) / period
            avg_loss = (avg_loss * (period - 1) + loss) / period

        if avg_loss == 0:
            return 100.0

        rs = avg_gain / avg_loss
        rsi = 100 - (100 / (1 + rs))
        return rsi

    async def _handle_alert(self, symbol: str, interval: str, alert_type: str,
                            rsi: float, current_price: Decimal):
        """处理报警（带冷却）"""
        with transaction.atomic():
            alert, created = PriceAlert.objects.get_or_create(
                symbol=symbol,
                kline_interval=interval,
                alert_type=alert_type,
                defaults={
                    'td_count': 0,
                    'current_price': current_price,
                    'trigger_price': current_price,
                    'triggered': False,
                    'message': f'RSI={rsi:.2f}',
                }
            )

            cooldown_minutes = self.COOLDOWN_MINUTES.get(interval, 60)
            now = timezone.now()
            in_cooldown = (alert.last_notified_at is not None and
                           now < alert.last_notified_at + timezone.timedelta(minutes=cooldown_minutes))

            if created or not in_cooldown:
                # 触发通知
                alert.current_price = current_price
                alert.trigger_price = current_price
                alert.triggered = True
                alert.last_notified_at = now
                alert.message = f'RSI={rsi:.2f}'
                alert.save()

                msg = (f'RSI ALERT: {symbol} {interval} {alert_type} '
                       f'RSI={rsi:.2f} price={current_price}')
                logger.info(msg)
                await self.wechat.send_text(f'[{alert_type}] {msg}')

    async def _reset_alert(self, symbol: str, interval: str, alert_type: str):
        """重置报警（价格回归正常）"""
        PriceAlert.objects.filter(
            symbol=symbol,
            kline_interval=interval,
            alert_type=alert_type,
            triggered=True
        ).update(triggered=False)
