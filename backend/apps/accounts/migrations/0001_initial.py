"""Initial migration for accounts"""
from django.db import migrations, models


class Migration(migrations.Migration):

    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name='ApiAccount',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('account_name', models.CharField(max_length=50, verbose_name='账户显示名')),
                ('api_key', models.CharField(max_length=200, verbose_name='API Key')),
                ('api_secret', models.TextField(verbose_name='API Secret (加密存储)')),
                ('use_proxy', models.BooleanField(default=False, verbose_name='是否使用代理')),
                ('proxy_url', models.CharField(blank=True, default='', max_length=200, verbose_name='代理 URL')),
                ('testnet', models.BooleanField(default=True, verbose_name='是否 testnet')),
                ('is_active', models.BooleanField(default=False, verbose_name='是否当前激活')),
                ('created_at', models.DateTimeField(auto_now_add=True, verbose_name='创建时间')),
                ('updated_at', models.DateTimeField(auto_now=True, verbose_name='更新时间')),
            ],
            options={
                'verbose_name': 'API 账户',
                'verbose_name_plural': 'API 账户',
                'db_table': 'api_accounts',
            },
        ),
        migrations.AddIndex(
            model_name='apiaccount',
            index=models.Index(fields=['is_active'], name='idx_account_active'),
        ),
    ]
