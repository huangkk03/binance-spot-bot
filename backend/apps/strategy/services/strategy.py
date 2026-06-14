"""
策略参数解析服务
核心: 交易对独立配置 > 全局数据库配置 > settings.py 默认值
"""
import logging
from decimal import Decimal
from typing import Optional

from django.conf import settings
from apps.strategy.models import StrategyConfig

logger = logging.getLogger(__name__)


class StrategyService:
    """
    策略参数解析
    用法:
        strategy = StrategyService()
        tp_pct = strategy.get_take_profit_pct('BTCUSDT')  # 0.03
        stop = strategy.get_stop_loss_pct('ETHUSDT')      # 0.10
    """

    def __init__(self):
        self.defaults = getattr(settings, 'TRADING', {})

    # ============ 交易策略参数 ============

    def get_take_profit_pct(self, symbol: Optional[str] = None) -> Decimal:
        """获取止盈百分比"""
        default = Decimal(str(self.defaults.get('TAKE_PROFIT_PCT', '0.03')))
        return StrategyConfig.objects.get_decimal(
            'TAKE_PROFIT_PCT', default, symbol
        )

    def get_stop_loss_pct(self, symbol: Optional[str] = None) -> Decimal:
        """获取止损百分比 (0=关闭)"""
        default = Decimal(str(self.defaults.get('STOP_LOSS_PCT', '0.10')))
        return StrategyConfig.objects.get_decimal(
            'STOP_LOSS_PCT', default, symbol
        )

    def get_quote_reserve(self, symbol: Optional[str] = None) -> Decimal:
        """获取 USDT 预留金额"""
        default = Decimal(str(self.defaults.get('QUOTE_RESERVE', '10')))
        return StrategyConfig.objects.get_decimal(
            'QUOTE_RESERVE', default, symbol
        )

    def get_max_orders_per_tick(self, symbol: Optional[str] = None) -> int:
        """获取每 tick 最大订单数"""
        default = int(self.defaults.get('MAX_ORDERS_PER_TICK', 5))
        return StrategyConfig.objects.get_int(
            'MAX_ORDERS_PER_TICK', default, symbol
        )

    def get_max_instances_per_symbol(self, symbol: Optional[str] = None) -> int:
        """获取每交易对最大实例数"""
        default = int(self.defaults.get('MAX_INSTANCES_PER_SYMBOL', 3))
        return StrategyConfig.objects.get_int(
            'MAX_INSTANCES_PER_SYMBOL', default, symbol
        )

    def is_auto_tick_enabled(self) -> bool:
        """是否启用自动 tick"""
        default = bool(self.defaults.get('AUTO_TICK_ENABLED', True))
        return StrategyConfig.objects.get_bool('AUTO_TICK_ENABLED', default)

    def get_auto_tick_interval_ms(self) -> int:
        """获取 tick 间隔（毫秒）"""
        default = int(self.defaults.get('AUTO_TICK_INTERVAL_MS', 30000))
        return StrategyConfig.objects.get_int('AUTO_TICK_INTERVAL_MS', default)

    # ============ RSI 参数 ============

    def get_rsi_overbought(self, symbol: Optional[str] = None) -> Decimal:
        default = Decimal(str(getattr(settings, 'RSI', {}).get('OVERBOUGHT', '80')))
        return StrategyConfig.objects.get_decimal(
            'RSI_OVERBOUGHT', default, symbol
        )

    def get_rsi_oversold(self, symbol: Optional[str] = None) -> Decimal:
        default = Decimal(str(getattr(settings, 'RSI', {}).get('OVERSOLD', '20')))
        return StrategyConfig.objects.get_decimal(
            'RSI_OVERSOLD', default, symbol
        )

    def get_rsi_period(self, symbol: Optional[str] = None) -> int:
        default = int(getattr(settings, 'RSI', {}).get('PERIOD', 14))
        return StrategyConfig.objects.get_int('RSI_PERIOD', default, symbol)

    # ============ 批量获取 ============

    def get_effective_config(self, symbol: Optional[str] = None) -> dict:
        """
        获取该交易对（或全局）的所有生效配置
        """
        return {
            'symbol': symbol or 'GLOBAL',
            'TAKE_PROFIT_PCT': str(self.get_take_profit_pct(symbol)),
            'STOP_LOSS_PCT': str(self.get_stop_loss_pct(symbol)),
            'QUOTE_RESERVE': str(self.get_quote_reserve(symbol)),
            'MAX_ORDERS_PER_TICK': self.get_max_orders_per_tick(symbol),
            'MAX_INSTANCES_PER_SYMBOL': self.get_max_instances_per_symbol(symbol),
            'RSI_OVERBOUGHT': str(self.get_rsi_overbought(symbol)),
            'RSI_OVERSOLD': str(self.get_rsi_oversold(symbol)),
            'RSI_PERIOD': self.get_rsi_period(symbol),
        }

    def set_config(self, key: str, value: str, symbol: Optional[str] = None) -> StrategyConfig:
        """
        设置配置
        symbol=None → 全局配置
        symbol='BTCUSDT' → 交易对特定配置
        """
        obj, created = StrategyConfig.objects.update_or_create(
            config_key=key,
            symbol=symbol,
            defaults={'config_value': str(value)}
        )
        logger.info(f'{"Created" if created else "Updated"} config: {key}={value} (symbol={symbol or "GLOBAL"})')
        return obj

    def delete_config(self, key: str, symbol: Optional[str] = None) -> bool:
        """删除配置（回退到上一级）"""
        deleted, _ = StrategyConfig.objects.filter(
            config_key=key, symbol=symbol
        ).delete()
        return deleted > 0
