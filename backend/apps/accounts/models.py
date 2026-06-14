"""
Accounts models
多 API 账户管理 (AES 加密存储密钥)
"""
from django.db import models
from apps.accounts.services.crypto import encrypt_secret, decrypt_secret


class ApiAccount(models.Model):
    """
    多个 Binance API 账户支持
    一个 active 账户用于交易
    """
    account_name = models.CharField(
        max_length=50,
        verbose_name='账户显示名'
    )
    api_key = models.CharField(
        max_length=200,
        verbose_name='API Key'
    )
    api_secret = models.TextField(
        verbose_name='API Secret (加密存储)'
    )
    use_proxy = models.BooleanField(
        default=False,
        verbose_name='是否使用代理'
    )
    proxy_url = models.CharField(
        max_length=200,
        blank=True,
        default='',
        verbose_name='代理 URL'
    )
    testnet = models.BooleanField(
        default=True,
        verbose_name='是否 testnet'
    )
    is_active = models.BooleanField(
        default=False,
        verbose_name='是否当前激活'
    )
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='创建时间')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='更新时间')

    class Meta:
        db_table = 'api_accounts'
        verbose_name = 'API 账户'
        verbose_name_plural = 'API 账户'
        indexes = [
            models.Index(fields=['is_active'], name='idx_account_active'),
        ]

    def __str__(self):
        return f'{self.account_name} ({"active" if self.is_active else "inactive"})'

    def set_secret(self, plain_secret: str):
        """加密存储 API Secret"""
        self.api_secret = encrypt_secret(plain_secret)

    def get_secret(self) -> str:
        """解密获取 API Secret"""
        return decrypt_secret(self.api_secret)

    @classmethod
    def get_active(cls):
        """获取当前激活的账户"""
        try:
            return cls.objects.get(is_active=True)
        except cls.DoesNotExist:
            return None
        except cls.MultipleObjectsReturned:
            return cls.objects.filter(is_active=True).first()
