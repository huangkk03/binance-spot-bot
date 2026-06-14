"""Initial migration for scanners"""
from django.db import migrations, models


class Migration(migrations.Migration):

    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name='PriceAlert',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('symbol', models.CharField(db_index=True, max_length=20, verbose_name='交易对')),
                ('kline_interval', models.CharField(db_index=True, max_length=10, verbose_name='K线周期')),
                ('alert_type', models.CharField(choices=[('RSI_OVERBOUGHT', 'RSI 超买'), ('RSI_OVERSOLD', 'RSI 超卖'), ('TD_BUY', 'TD 买入信号'), ('TD_SELL', 'TD 卖出信号')], max_length=20, verbose_name='报警类型')),
                ('td_count', models.IntegerField(default=0, verbose_name='TD 计数 (仅 TD 用)')),
                ('current_price', models.DecimalField(decimal_places=16, max_digits=32, verbose_name='当前价格')),
                ('trigger_price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='触发价格')),
                ('triggered', models.BooleanField(default=False, verbose_name='是否已触发')),
                ('message', models.TextField(blank=True, default='', verbose_name='报警消息')),
                ('created_at', models.DateTimeField(auto_now_add=True, db_index=True, verbose_name='创建时间')),
                ('last_notified_at', models.DateTimeField(blank=True, null=True, verbose_name='最后通知时间 (cooldown 用)')),
            ],
            options={
                'verbose_name': '价格报警',
                'verbose_name_plural': '价格报警',
                'db_table': 'price_alerts',
            },
        ),
        migrations.AddConstraint(
            model_name='pricealert',
            constraint=models.UniqueConstraint(fields=('symbol', 'kline_interval', 'alert_type'), name='uk_alert_symbol_interval_type'),
        ),
        migrations.AddIndex(
            model_name='pricealert',
            index=models.Index(fields=['symbol', 'kline_interval', 'alert_type'], name='idx_alert_symbol_interval'),
        ),

        migrations.CreateModel(
            name='FundingRateAlert',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('symbol', models.CharField(db_index=True, max_length=20, verbose_name='交易对')),
                ('alert_type', models.CharField(choices=[('LEVEL_1', '级别一：预警信号'), ('LEVEL_2', '级别二：绝对信号')], max_length=20, verbose_name='信号级别')),
                ('funding_rate', models.DecimalField(blank=True, decimal_places=16, max_digits=32, null=True, verbose_name='触发时的资金费率')),
                ('annualized_rate', models.DecimalField(blank=True, decimal_places=16, max_digits=32, null=True, verbose_name='年化费率')),
                ('next_funding_time', models.BigIntegerField(blank=True, null=True, verbose_name='下次结算时间戳')),
                ('last_notified_at', models.DateTimeField(blank=True, db_index=True, null=True, verbose_name='最后通知时间 (cooldown 用)')),
                ('created_at', models.DateTimeField(auto_now_add=True, verbose_name='创建时间')),
                ('updated_at', models.DateTimeField(auto_now=True, verbose_name='更新时间')),
            ],
            options={
                'verbose_name': '资金费率报警',
                'verbose_name_plural': '资金费率报警',
                'db_table': 'funding_rate_alerts',
            },
        ),
        migrations.AddConstraint(
            model_name='fundingratealert',
            constraint=models.UniqueConstraint(fields=('symbol', 'alert_type'), name='uk_funding_symbol_type'),
        ),
    ]
