"""
Scanners models
技术指标扫描器相关表
"""
from django.db import models


class PriceAlert(models.Model):
    """
    RSI / TD Sequential 报警
    替代 Java 的 PriceAlert (移除 is_simulation)
    """
    ALERT_TYPE_CHOICES = [
        ('RSI_OVERBOUGHT', 'RSI 超买'),
        ('RSI_OVERSOLD', 'RSI 超卖'),
        ('TD_BUY', 'TD 买入信号'),
        ('TD_SELL', 'TD 卖出信号'),
    ]

    symbol = models.CharField(max_length=20, db_index=True, verbose_name='交易对')
    kline_interval = models.CharField(max_length=10, db_index=True, verbose_name='K线周期')
    alert_type = models.CharField(
        max_length=20, choices=ALERT_TYPE_CHOICES, verbose_name='报警类型'
    )

    td_count = models.IntegerField(default=0, verbose_name='TD 计数 (仅 TD 用)')
    current_price = models.DecimalField(
        max_digits=32, decimal_places=16,
        verbose_name='当前价格'
    )
    trigger_price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='触发价格'
    )
    triggered = models.BooleanField(default=False, verbose_name='是否已触发')
    message = models.TextField(blank=True, default='', verbose_name='报警消息')

    created_at = models.DateTimeField(auto_now_add=True, db_index=True, verbose_name='创建时间')
    last_notified_at = models.DateTimeField(
        null=True, blank=True,
        verbose_name='最后通知时间 (cooldown 用)'
    )

    class Meta:
        db_table = 'price_alerts'
        verbose_name = '价格报警'
        verbose_name_plural = '价格报警'
        constraints = [
            models.UniqueConstraint(
                fields=['symbol', 'kline_interval', 'alert_type'],
                name='uk_alert_symbol_interval_type'
            )
        ]
        indexes = [
            models.Index(
                fields=['symbol', 'kline_interval', 'alert_type'],
                name='idx_alert_symbol_interval'
            ),
        ]

    def __str__(self):
        return f'{self.symbol} {self.kline_interval} {self.alert_type} triggered={self.triggered}'


class FundingRateAlert(models.Model):
    """
    资金费率抄底信号
    替代 Java 的 FundingRateAlert
    """
    ALERT_TYPE_CHOICES = [
        ('LEVEL_1', '级别一：预警信号'),
        ('LEVEL_2', '级别二：绝对信号'),
    ]

    symbol = models.CharField(max_length=20, db_index=True, verbose_name='交易对')
    alert_type = models.CharField(
        max_length=20, choices=ALERT_TYPE_CHOICES, verbose_name='信号级别'
    )

    funding_rate = models.DecimalField(
        max_digits=32, decimal_places=16, null=True, blank=True,
        verbose_name='触发时的资金费率'
    )
    annualized_rate = models.DecimalField(
        max_digits=32, decimal_places=16, null=True, blank=True,
        verbose_name='年化费率'
    )
    next_funding_time = models.BigIntegerField(
        null=True, blank=True,
        verbose_name='下次结算时间戳'
    )
    last_notified_at = models.DateTimeField(
        null=True, blank=True, db_index=True,
        verbose_name='最后通知时间 (cooldown 用)'
    )

    created_at = models.DateTimeField(auto_now_add=True, verbose_name='创建时间')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='更新时间')

    class Meta:
        db_table = 'funding_rate_alerts'
        verbose_name = '资金费率报警'
        verbose_name_plural = '资金费率报警'
        constraints = [
            models.UniqueConstraint(
                fields=['symbol', 'alert_type'],
                name='uk_funding_symbol_type'
            )
        ]

    def __str__(self):
        return f'{self.symbol} {self.alert_type} rate={self.funding_rate}'
