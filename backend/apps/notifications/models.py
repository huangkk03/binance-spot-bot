"""
Notifications models
通知相关配置（key-value 形式存储于 MySQL）
"""
from django.db import models


class ApiConfig(models.Model):
    """
    通用配置 (微信 webhook, AI API key, SMTP 等)
    替代 Java 的 api_config 表
    """
    config_key = models.CharField(
        max_length=50, unique=True, db_index=True, verbose_name='配置 Key'
    )
    config_value = models.TextField(verbose_name='配置值')

    updated_at = models.DateTimeField(auto_now=True, verbose_name='更新时间')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='创建时间')

    class Meta:
        db_table = 'api_config'
        verbose_name = '通用配置'
        verbose_name_plural = '通用配置'

    def __str__(self):
        return f'{self.config_key}'

    @classmethod
    def get_value(cls, key: str, default=None) -> str:
        """获取配置值（无值返回 default）"""
        try:
            return cls.objects.get(config_key=key).config_value
        except cls.DoesNotExist:
            return default
