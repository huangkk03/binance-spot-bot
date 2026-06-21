from django.db import migrations, models


class Migration(migrations.Migration):
    initial = True
    dependencies = []

    operations = [
        migrations.CreateModel(
            name='FuturesInstance',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('symbol', models.CharField(db_index=True, max_length=20, verbose_name='交易对')),
                ('instance_id', models.IntegerField(verbose_name='实例编号')),
                ('direction', models.CharField(choices=[('LONG', '做多'), ('SHORT', '做空')], default='LONG', max_length=10, verbose_name='持仓方向')),
                ('cycle_id', models.IntegerField(default=1, verbose_name='当前周期数')),
                ('is_open', models.BooleanField(default=False, verbose_name='是否持仓中')),
                ('leverage', models.IntegerField(default=100, verbose_name='杠杆倍数')),
                ('anchor_price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='锚定价')),
                ('reentry_price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='复利入场价')),
                ('cycle_start_price', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='当前周期开仓价')),
                ('margin', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='占用保证金 USDT')),
                ('notional', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='名义仓位 USDT')),
                ('base_qty', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='持仓合约数量')),
                ('cumulative_profit', models.DecimalField(decimal_places=16, default=0, max_digits=32, verbose_name='累计盈利 USDT')),
                ('created_at', models.DateTimeField(auto_now_add=True, verbose_name='创建时间')),
                ('updated_at', models.DateTimeField(auto_now=True, verbose_name='更新时间')),
            ],
            options={
                'verbose_name': '合约实例',
                'verbose_name_plural': '合约实例',
                'db_table': 'futures_instances',
            },
        ),
        migrations.AddConstraint(
            model_name='futuresinstance',
            constraint=models.UniqueConstraint(fields=('symbol', 'instance_id'), name='uk_futures_symbol_instance'),
        ),
        migrations.AddIndex(
            model_name='futuresinstance',
            index=models.Index(fields=['symbol'], name='idx_futures_symbol'),
        ),
        migrations.AddIndex(
            model_name='futuresinstance',
            index=models.Index(fields=['is_open', 'symbol'], name='idx_futures_open'),
        ),
    ]
