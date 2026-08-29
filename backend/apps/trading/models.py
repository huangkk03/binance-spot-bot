"""
Trading models
真实交易核心数据模型
"""
from django.db import models
from django.utils import timezone


class CycleInstance(models.Model):
    """
    真实交易循环实例
    每个币种可有多个实例，每个实例独立循环
    """
    symbol = models.CharField(
        max_length=20,
        db_index=True,
        verbose_name='交易对'
    )
    instance_id = models.IntegerField(
        verbose_name='实例 ID (按 symbol 自增)'
    )
    cycle_id = models.IntegerField(
        default=0,
        verbose_name='当前周期数'
    )

    is_open = models.BooleanField(
        default=False,
        verbose_name='是否持仓中'
    )
    anchor_price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='锚定价 (首次开仓价)'
    )
    reentry_price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='重新入场价格'
    )
    cycle_start_price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='当前周期开仓价'
    )
    last_action_price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='最后操作价'
    )

    base_qty = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='当前持有基础币数量'
    )
    spent_quote = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='本周期花费的 quote'
    )
    quote_amount = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='下次开仓 quote 金额 (复利)'
    )
    cumulative_profit = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='累计止盈利润 (USDT)'
    )

    updated_at = models.DateTimeField(auto_now=True, verbose_name='更新时间')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='创建时间')

    class Meta:
        db_table = 'cycle_instances'
        verbose_name = '交易循环实例'
        verbose_name_plural = '交易循环实例'
        constraints = [
            models.UniqueConstraint(
                fields=['symbol', 'instance_id'],
                name='uk_symbol_instance'
            )
        ]
        indexes = [
            models.Index(fields=['symbol'], name='idx_instance_symbol'),
            models.Index(fields=['is_open'], name='idx_instance_is_open'),
        ]

    def __str__(self):
        return f'{self.symbol}#{self.instance_id} cycle={self.cycle_id} {"OPEN" if self.is_open else "CLOSED"}'


class TradeRecord(models.Model):
    """
    交易记录 (每笔订单)
    """
    SIDE_BUY = 'BUY'
    SIDE_SELL = 'SELL'
    SIDE_CHOICES = [(SIDE_BUY, 'BUY'), (SIDE_SELL, 'SELL')]

    order_id = models.CharField(max_length=50, verbose_name='交易所订单 ID')
    symbol = models.CharField(max_length=20, db_index=True, verbose_name='交易对')
    side = models.CharField(max_length=10, choices=SIDE_CHOICES, verbose_name='买卖方向')
    status = models.CharField(max_length=20, verbose_name='订单状态')

    executed_qty = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='已成交基础币数量'
    )
    cummulative_quote_qty = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='累计 quote 数量'
    )
    avg_price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='平均成交价 (Binance 实际均价)'
    )
    commission = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='手续费'
    )
    commission_asset = models.CharField(
        max_length=10, blank=True, default='',
        verbose_name='手续费币种'
    )
    payload_json = models.TextField(
        blank=True, default='',
        verbose_name='完整订单响应 JSON'
    )

    created_at = models.DateTimeField(auto_now_add=True, db_index=True, verbose_name='创建时间')

    class Meta:
        db_table = 'trade_records'
        verbose_name = '交易记录'
        verbose_name_plural = '交易记录'
        indexes = [
            models.Index(fields=['order_id'], name='idx_trade_order_id'),
        ]

    def __str__(self):
        return f'{self.side} {self.symbol} @ {self.avg_price}'


class CycleOpenRecord(models.Model):
    """
    周期开仓记录
    每次新开仓创建一条
    """
    symbol = models.CharField(max_length=20, verbose_name='交易对')
    instance_id = models.IntegerField(verbose_name='实例 ID')
    cycle_id = models.IntegerField(verbose_name='周期 ID')

    start_price = models.DecimalField(
        max_digits=32, decimal_places=16,
        verbose_name='开仓均价'
    )
    quote_amount = models.DecimalField(
        max_digits=32, decimal_places=16,
        verbose_name='开仓 quote 金额'
    )

    opened_at = models.DateTimeField(verbose_name='开仓时间')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='记录创建时间')

    class Meta:
        db_table = 'cycle_open_records'
        verbose_name = '周期开仓记录'
        verbose_name_plural = '周期开仓记录'
        indexes = [
            models.Index(
                fields=['symbol', 'instance_id'],
                name='idx_open_symbol_instance'
            ),
        ]

    def __str__(self):
        return f'{self.symbol}#{self.instance_id} cycle={self.cycle_id} opened at {self.start_price}'


class InstanceEvent(models.Model):
    """
    实例事件日志
    记录开仓、平仓、止盈、止损等事件
    """
    EVENT_CHOICES = [
        ('BUY_OPEN', '开仓'),
        ('SELL_TP', '止盈平仓'),
        ('SELL_SL', '止损平仓'),
        ('REBUY_COMPOUND', '复利再买入'),
        ('FUTURES_OPEN', '合约开仓'),
    ]

    symbol = models.CharField(max_length=20, verbose_name='交易对')
    instance_id = models.IntegerField(verbose_name='实例 ID')
    cycle_id = models.IntegerField(verbose_name='周期 ID')

    event = models.CharField(max_length=30, choices=EVENT_CHOICES, verbose_name='事件类型')
    price = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='事件价格'
    )
    base_qty = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='基础币数量'
    )
    quote_amount = models.DecimalField(
        max_digits=32, decimal_places=16, default=0,
        verbose_name='quote 数量'
    )
    note = models.CharField(
        max_length=500, blank=True, default='',
        verbose_name='备注'
    )

    created_at = models.DateTimeField(auto_now_add=True, verbose_name='事件时间')

    class Meta:
        db_table = 'instance_events'
        verbose_name = '实例事件'
        verbose_name_plural = '实例事件'
        indexes = [
            models.Index(
                fields=['symbol', 'instance_id', '-created_at'],
                name='idx_event_symbol_instance'
            ),
            models.Index(
                fields=['event', '-created_at'],
                name='idx_event_type'
            ),
        ]

    def __str__(self):
        return f'{self.event} {self.symbol}#{self.instance_id} cycle={self.cycle_id}'
