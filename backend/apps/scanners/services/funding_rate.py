"""
资金费率扫描器
每 5 分钟轮询 Binance 合约 Premium Index
检测负费率极端值（空头拥挤 = 抄底机会）
"""
import logging
from decimal import Decimal
from datetime import datetime, timezone as dt_timezone
from typing import List, Optional
import asyncio
import time

from django.utils import timezone
from django.db import transaction
from django.conf import settings

from .binance_rest import BinanceRestService
from apps.scanners.models import FundingRateAlert
from apps.notifications.services.wechat import WeChatNotifier

logger = logging.getLogger(__name__)


class FundingRateScanner:
    """
    资金费率监控
    - 主流币 (BTC, ETH): L1=-0.05%, L2=-0.10%
    - 山寨币: L1=-0.10%, L2=-0.20%
    - 冷却: 2小时
    """

    MAIN_SYMBOLS = ['BTCUSDT', 'ETHUSDT']
    MAIN_THRESHOLD_L1 = Decimal('-0.0005')  # -0.05%
    MAIN_THRESHOLD_L2 = Decimal('-0.001')   # -0.10%
    ALT_THRESHOLD_L1 = Decimal('-0.001')    # -0.10%
    ALT_THRESHOLD_L2 = Decimal('-0.002')    # -0.20%
    COOLDOWN_HOURS = 2

    def __init__(self):
        self.wechat = WeChatNotifier()
        self.config = settings.FUNDING_RATE

    async def scan(self):
        """执行一次扫描"""
        symbols = self.config.get('SYMBOLS', [])
        if not symbols:
            return

        data = await BinanceRestService.fetch_premium_index(symbols)
        if not data:
            logger.warning('No funding rate data fetched')
            return

        for entry in data:
            symbol = entry.get('symbol', '')
            try:
                rate = Decimal(str(entry.get('lastFundingRate', '0')))
                next_time = int(entry.get('nextFundingTime', 0))
            except (ValueError, TypeError):
                continue

            if not rate:
                continue

            is_main = symbol in self.MAIN_SYMBOLS
            threshold_l1 = self.MAIN_THRESHOLD_L1 if is_main else self.ALT_THRESHOLD_L1
            threshold_l2 = self.MAIN_THRESHOLD_L2 if is_main else self.ALT_THRESHOLD_L2

            logger.info(f'Funding rate for {symbol}: {rate} (L1={threshold_l1}, L2={threshold_l2})')

            if rate <= threshold_l2:
                await self._handle_alert(symbol, 'LEVEL_2', rate, next_time)
            elif rate <= threshold_l1:
                await self._handle_alert(symbol, 'LEVEL_1', rate, next_time)

    async def _handle_alert(self, symbol: str, alert_type: str,
                            rate: Decimal, next_funding_time: int):
        """处理资金费率报警"""
        with transaction.atomic():
            alert, created = FundingRateAlert.objects.get_or_create(
                symbol=symbol,
                alert_type=alert_type,
                defaults={
                    'funding_rate': rate,
                    'annualized_rate': self._calculate_annualized(rate),
                    'next_funding_time': next_funding_time,
                    'last_notified_at': timezone.now(),
                }
            )

            cooldown = timezone.timedelta(hours=self.COOLDOWN_HOURS)
            now = timezone.now()
            in_cooldown = (alert.last_notified_at is not None and
                           now < alert.last_notified_at + cooldown)

            if created or not in_cooldown:
                alert.funding_rate = rate
                alert.annualized_rate = self._calculate_annualized(rate)
                alert.next_funding_time = next_funding_time
                alert.last_notified_at = now
                alert.save()

                msg = self._format_message(symbol, alert_type, rate, next_funding_time)
                logger.info(f'Funding rate alert: {msg}')
                await self.wechat.send_markdown(msg)

    @staticmethod
    def _calculate_annualized(rate: Decimal) -> Decimal:
        """年化费率 = rate * 3 * 365"""
        return rate * Decimal('3') * Decimal('365')

    @staticmethod
    def _format_message(symbol: str, alert_type: str, rate: Decimal, next_time: int) -> str:
        """格式化微信 Markdown 消息"""
        level_text = ('💥 级别二：【绝对信号】（空头极度拥挤）'
                      if alert_type == 'LEVEL_2'
                      else '🚨 级别一：【预警信号】')

        # 计算年化
        annualized = rate * Decimal('3') * Decimal('365')

        # 计算倒计时
        now_ms = int(time.time() * 1000)
        diff_ms = max(0, next_time - now_ms)
        hours = diff_ms // (1000 * 60 * 60)
        minutes = (diff_ms % (1000 * 60 * 60)) // (1000 * 60)
        countdown = f'{hours:02d}小时{minutes:02d}分钟'

        rate_pct = rate * Decimal('100')
        ann_pct = annualized * Decimal('100')

        return (
            f'🟢 **【币安现货抄底提醒】—— 发现轧空信号！**\n'
            f'> **监控币种：** {symbol}\n'
            f'> **信号级别：** {level_text}\n'
            f'> **当前资金费率：** {rate_pct:.4f}%（年化约 {ann_pct:.2f}%）\n'
            f'> **下次结算时间：** 还有 {countdown}\n'
            f'> **持仓量(OI)状态：** 已达监控阈值'
        )
