"""
TD Sequential 扫描器
TD Setup: 连续 9 根 K 线收盘价 < 前 4 根 K 线收盘价（卖出 Setup）
TD Setup: 连续 9 根 K 线收盘价 > 前 4 根 K 线收盘价（买入 Setup）
"""
import logging
from decimal import Decimal
from datetime import datetime
from typing import List, Optional
import asyncio

from django.utils import timezone
from django.db import transaction

from .binance_rest import BinanceRestService
from apps.scanners.models import PriceAlert
from apps.notifications.services.wechat import WeChatNotifier

logger = logging.getLogger(__name__)


class TDScanner:
    """
    TD Sequential (Tom DeMark) 扫描器
    监测 1h / 4h 周期的 TD9/13 信号
    """

    INTERVALS = ['1h', '4h']
    SETUP_COUNT = 9  # TD9 触发

    def __init__(self):
        self.wechat = WeChatNotifier()

    async def scan_all(self, symbols: List[str]):
        for symbol in symbols:
            for interval in self.INTERVALS:
                try:
                    await self.scan_symbol_interval(symbol, interval)
                except Exception as e:
                    logger.error(f'TD scan error {symbol} {interval}: {e}')

    async def scan_symbol_interval(self, symbol: str, interval: str):
        """扫描单个币种单个周期"""
        klines = await BinanceRestService.fetch_klines(symbol, interval, 30)
        if not klines or len(klines) < 5:
            return

        closes = [Decimal(str(k[4])) for k in klines]
        td_count, is_buy = self.calculate_td_setup(closes)

        current_price = closes[-1]
        logger.info(f'TD for {symbol} {interval}: count={td_count} buy={is_buy}')

        if td_count >= self.SETUP_COUNT:
            alert_type = 'TD_BUY' if is_buy else 'TD_SELL'
            await self._handle_alert(symbol, interval, alert_type, td_count, current_price)
        else:
            # 重置
            await self._reset_alerts(symbol, interval)

    @staticmethod
    def calculate_td_setup(closes: List[Decimal]) -> tuple:
        """
        计算 TD Setup
        规则:
        - 买入 Setup: 连续 N 根 K 线收盘价 < 前 4 根 K 线收盘价
        - 卖出 Setup: 连续 N 根 K 线收盘价 > 前 4 根 K 线收盘价
        返回: (count, is_buy)
        """
        if len(closes) < 5:
            return 0, False

        count = 0
        is_buy = None

        for i in range(4, len(closes)):
            if is_buy is None:
                # 第一根决定方向
                if closes[i] < closes[i - 4]:
                    is_buy = True
                elif closes[i] > closes[i - 4]:
                    is_buy = False
                else:
                    continue

            if is_buy and closes[i] < closes[i - 4]:
                count += 1
            elif not is_buy and closes[i] > closes[i - 4]:
                count += 1
            else:
                # Setup 中断
                is_buy = None
                count = 0
                # 重新从当前 K 线开始
                if closes[i] < closes[i - 4]:
                    is_buy = True
                    count = 1
                elif closes[i] > closes[i - 4]:
                    is_buy = False
                    count = 1

        return count, is_buy or False

    async def _handle_alert(self, symbol: str, interval: str, alert_type: str,
                            td_count: int, current_price: Decimal):
        """处理 TD 报警"""
        with transaction.atomic():
            alert, created = PriceAlert.objects.get_or_create(
                symbol=symbol,
                kline_interval=interval,
                alert_type=alert_type,
                defaults={
                    'td_count': td_count,
                    'current_price': current_price,
                    'trigger_price': current_price,
                    'triggered': False,
                    'message': f'TD={td_count}',
                }
            )

            cooldown_minutes = 60 if interval == '1h' else 240
            now = timezone.now()
            in_cooldown = (alert.last_notified_at is not None and
                           now < alert.last_notified_at + timezone.timedelta(minutes=cooldown_minutes))

            if created or not in_cooldown:
                alert.td_count = td_count
                alert.current_price = current_price
                alert.trigger_price = current_price
                alert.triggered = True
                alert.last_notified_at = now
                alert.message = f'TD={td_count}'
                alert.save()

                msg = f'TD ALERT: {symbol} {interval} {alert_type} count={td_count} price={current_price}'
                logger.info(msg)
                await self.wechat.send_text(f'[{alert_type}] {msg}')

    async def _reset_alerts(self, symbol: str, interval: str):
        """重置 TD 报警"""
        PriceAlert.objects.filter(
            symbol=symbol,
            kline_interval=interval,
            alert_type__in=['TD_BUY', 'TD_SELL'],
            triggered=True
        ).update(triggered=False)
