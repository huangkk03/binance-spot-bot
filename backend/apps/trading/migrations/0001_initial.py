"""Initial migration for trading"""
from django.db import migrations, models


class Migration(migrations.Migration):

    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name='CycleInstance',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('symbol', models.CharField(db_index=True, max_length=20, verbose_name='交易对')),
                ('instance_id', models.IntegerField(verbose_name='实例 ID (按 symbol 自增)')),
                ('cycle_id', models.IntegerField(default=0, verbose_name='当前周期数')),
                ('is_open', models.BooleanField(default=False, verbose_name='是否持仓中')),
                ('anchor_price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='锚定价 (首次开仓价)')),
                ('reentry_price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='重新入场价格')),
                ('cycle_start_price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='当前周期开仓价')),
                ('last_action_price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='最后操作价')),
                ('base_qty', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='当前持有基础币数量')),
                ('spent_quote', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='本周期花费的 quote')),
                ('quote_amount', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='下次开仓 quote 金额 (复利)')),
                ('cumulative_profit', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='累计止盈利润 (USDT)')),
                ('updated_at', models.DateTimeField(auto_now=True, verbose_name='更新时间')),
                ('created_at', models.DateTimeField(auto_now_add=True, verbose_name='创建时间')),
            ],
            options={
                'verbose_name': '交易循环实例',
                'verbose_name_plural': '交易循环实例',
                'db_table': 'cycle_instances',
            },
        ),
        migrations.AddConstraint(
            model_name='cycleinstance',
            constraint=models.UniqueConstraint(fields=('symbol', 'instance_id'), name='uk_symbol_instance'),
        ),
        migrations.AddIndex(
            model_name='cycleinstance',
            index=models.Index(fields=['symbol'], name='idx_instance_symbol'),
        ),
        migrations.AddIndex(
            model_name='cycleinstance',
            index=models.Index(fields=['is_open'], name='idx_instance_is_open'),
        ),

        migrations.CreateModel(
            name='TradeRecord',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('order_id', models.CharField(max_length=50, verbose_name='交易所订单 ID')),
                ('symbol', models.CharField(db_index=True, max_length=20, verbose_name='交易对')),
                ('side', models.CharField(choices=[('BUY', 'BUY'), ('SELL', 'SELL')], max_length=10, verbose_name='买卖方向')),
                ('status', models.CharField(max_length=20, verbose_name='订单状态')),
                ('executed_qty', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='已成交基础币数量')),
                ('cummulative_quote_qty', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='累计 quote 数量')),
                ('avg_price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='平均成交价')),
                ('payload_json', models.TextField(blank=True, default='', verbose_name='完整订单响应 JSON')),
                ('created_at', models.DateTimeField(auto_now_add=True, db_index=True, verbose_name='创建时间')),
            ],
            options={
                'verbose_name': '交易记录',
                'verbose_name_plural': '交易记录',
                'db_table': 'trade_records',
            },
        ),
        migrations.AddIndex(
            model_name='traderecord',
            index=models.Index(fields=['order_id'], name='idx_trade_order_id'),
        ),

        migrations.CreateModel(
            name='CycleOpenRecord',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('symbol', models.CharField(max_length=20, verbose_name='交易对')),
                ('instance_id', models.IntegerField(verbose_name='实例 ID')),
                ('cycle_id', models.IntegerField(verbose_name='周期 ID')),
                ('start_price', models.DecimalField(decimal_places=16, max_digits=32, verbose_name='开仓均价')),
                ('quote_amount', models.DecimalField(decimal_places=16, max_digits=32, verbose_name='开仓 quote 金额')),
                ('opened_at', models.DateTimeField(verbose_name='开仓时间')),
                ('created_at', models.DateTimeField(auto_now_add=True, verbose_name='记录创建时间')),
            ],
            options={
                'verbose_name': '周期开仓记录',
                'verbose_name_plural': '周期开仓记录',
                'db_table': 'cycle_open_records',
            },
        ),
        migrations.AddIndex(
            model_name='cycleopenrecord',
            index=models.Index(fields=['symbol', 'instance_id'], name='idx_open_symbol_instance'),
        ),

        migrations.CreateModel(
            name='InstanceEvent',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('symbol', models.CharField(max_length=20, verbose_name='交易对')),
                ('instance_id', models.IntegerField(verbose_name='实例 ID')),
                ('cycle_id', models.IntegerField(verbose_name='周期 ID')),
                ('event', models.CharField(choices=[('BUY_OPEN', '开仓'), ('SELL_TP', '止盈平仓'), ('SELL_SL', '止损平仓'), ('REBUY_COMPOUND', '复利再买入')], max_length=30, verbose_name='事件类型')),
                ('price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='事件价格')),
                ('base_qty', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='基础币数量')),
                ('quote_amount', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='quote 数量')),
                ('note', models.CharField(blank=True, default='', max_length=500, verbose_name='备注')),
                ('created_at', models.DateTimeField(auto_now_add=True, verbose_name='事件时间')),
            ],
            options={
                'verbose_name': '实例事件',
                'verbose_name_plural': '实例事件',
                'db_table': 'instance_events',
            },
        ),
        migrations.AddIndex(
            model_name='instanceevent',
            index=models.Index(fields=['symbol', 'instance_id', '-created_at'], name='idx_event_symbol_instance'),
        ),
        migrations.AddIndex(
            model_name='instanceevent',
            index=models.Index(fields=['event', '-created_at'], name='idx_event_type'),
        ),
    ]
