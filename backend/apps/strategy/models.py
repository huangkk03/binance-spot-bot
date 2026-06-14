"""
策略配置模型
支持全局 + 交易对独立配置（独立覆盖全局）
"""
from django.db import models


class StrategyConfigManager(models.Manager):
    """自定义 Manager - 支持三层优先级查询"""

    def get_effective_value(self, key, symbol=None):
        """
        按优先级获取值:
        1. 交易对独立配置
        2. 全局配置
        """
        if symbol:
            specific = self.filter(config_key=key, symbol=symbol).first()
            if specific:
                return specific.config_value
        global_config = self.filter(config_key=key, symbol__isnull=True).first()
        if global_config:
            return global_config.config_value
        return None

    def get_decimal(self, key, default, symbol=None):
        from decimal import Decimal, InvalidOperation
        value = self.get_effective_value(key, symbol)
        if value is None:
            return default
        try:
            return Decimal(value)
        except (InvalidOperation, ValueError):
            return default

    def get_int(self, key, default, symbol=None):
        value = self.get_effective_value(key, symbol)
        if value is None:
            return default
        try:
            return int(float(value))
        except (ValueError, TypeError):
            return default

    def get_bool(self, key, default, symbol=None):
        value = self.get_effective_value(key, symbol)
        if value is None:
            return default
        return str(value).lower() in ('true', '1', 'yes', 'on')


class StrategyConfig(models.Model):
    """
    策略参数表
    symbol = NULL 表示全局默认
    symbol = 'BTCUSDT' 表示 BTCUSDT 特定配置
    """
    config_key = models.CharField(
        max_length=50,
        db_index=True,
        verbose_name='配置 Key'
    )
    config_value = models.CharField(
        max_length=200,
        verbose_name='配置值'
    )
    symbol = models.CharField(
        max_length=20,
        null=True, blank=True,
        verbose_name='交易对（NULL=全局）'
    )
    description = models.CharField(
        max_length=200, blank=True, default='',
        verbose_name='说明'
    )
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='创建时间')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='更新时间')

    # 使用自定义 Manager
    objects = StrategyConfigManager()

    class Meta:
        db_table = 'strategy_config'
        verbose_name = '策略配置'
        verbose_name_plural = '策略配置'
        constraints = [
            models.UniqueConstraint(
                fields=['config_key', 'symbol'],
                name='uk_strategy_key_symbol'
            )
        ]
        indexes = [
            models.Index(fields=['config_key', 'symbol'], name='idx_strategy_key_symbol'),
        ]

    def __str__(self):
        sym = self.symbol or 'GLOBAL'
        return f'{self.config_key} = {self.config_value} ({sym})'
