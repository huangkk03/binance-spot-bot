"""Initial migration for strategy"""
from django.db import migrations, models


class Migration(migrations.Migration):

    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name='StrategyConfig',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('config_key', models.CharField(db_index=True, max_length=50, verbose_name='配置 Key')),
                ('config_value', models.CharField(max_length=200, verbose_name='配置值')),
                ('symbol', models.CharField(blank=True, max_length=20, null=True, verbose_name='交易对（NULL=全局）')),
                ('description', models.CharField(blank=True, default='', max_length=200, verbose_name='说明')),
                ('created_at', models.DateTimeField(auto_now_add=True, verbose_name='创建时间')),
                ('updated_at', models.DateTimeField(auto_now=True, verbose_name='更新时间')),
            ],
            options={
                'verbose_name': '策略配置',
                'verbose_name_plural': '策略配置',
                'db_table': 'strategy_config',
            },
        ),
        migrations.AddConstraint(
            model_name='strategyconfig',
            constraint=models.UniqueConstraint(fields=('config_key', 'symbol'), name='uk_strategy_key_symbol'),
        ),
        migrations.AddIndex(
            model_name='strategyconfig',
            index=models.Index(fields=['config_key', 'symbol'], name='idx_strategy_key_symbol'),
        ),
    ]
