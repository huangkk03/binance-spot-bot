"""
U本位合约实例模型
独立于现货 cycle_instances
"""
from django.db import models


class FuturesInstance(models.Model):
    """
    U本位合约实例
    支持做多(LONG)和做空(SHORT)双向持仓
    """
    DIRECTION_LONG = 'LONG'
    DIRECTION_SHORT = 'SHORT'
    DIRECTION_CHOICES = [
        (DIRECTION_LONG, '做多'),
        (DIRECTION_SHORT, '做空'),
    ]

    symbol = models.CharField(max_length=20, db_index=True, verbose_name='交易对')
    instance_id = models.IntegerField(verbose_name='实例编号')
    direction = models.CharField(
        max_length=10, choices=DIRECTION_CHOICES, default=DIRECTION_LONG,
        verbose_name='持仓方向'
    )
    cycle_id = models.IntegerField(default=1, verbose_name='当前周期数')

    is_open = models.BooleanField(default=False, verbose_name='是否持仓中')
    leverage = models.IntegerField(default=100, verbose_name='杠杆倍数')

    # 价格相关
    anchor_price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='锚定价'
    )
    reentry_price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='复利入场价'
    )
    cycle_start_price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='当前周期开仓价'
    )

    # 资金相关
    margin = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='占用保证金 USDT'
    )
    notional = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='名义仓位 USDT'
    )
    base_qty = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='持仓合约数量'
    )
    cumulative_profit = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='累计盈利 USDT'
    )

    created_at = models.DateTimeField(auto_now_add=True, verbose_name='创建时间')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='更新时间')

    class Meta:
        db_table = 'futures_instances'
        verbose_name = '合约实例'
        verbose_name_plural = '合约实例'
        constraints = [
            models.UniqueConstraint(
                fields=['symbol', 'instance_id'],
                name='uk_futures_symbol_instance'
            )
        ]
        indexes = [
            models.Index(fields=['symbol'], name='idx_futures_symbol'),
            models.Index(fields=['is_open', 'symbol'], name='idx_futures_open'),
        ]

    def __str__(self):
        return f'{self.symbol}#{self.instance_id} {self.direction} x{self.leverage} {"OPEN" if self.is_open else "CLOSED"}'
