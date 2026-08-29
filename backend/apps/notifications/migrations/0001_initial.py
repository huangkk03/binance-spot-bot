"""Initial migration for notifications"""
from django.db import migrations, models


class Migration(migrations.Migration):

    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name='ApiConfig',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('config_key', models.CharField(db_index=True, max_length=50, unique=True, verbose_name='配置 Key')),
                ('config_value', models.TextField(verbose_name='配置值')),
                ('updated_at', models.DateTimeField(auto_now=True, verbose_name='更新时间')),
                ('created_at', models.DateTimeField(auto_now_add=True, verbose_name='创建时间')),
            ],
            options={
                'verbose_name': '通用配置',
                'verbose_name_plural': '通用配置',
                'db_table': 'api_config',
            },
        ),
    ]
