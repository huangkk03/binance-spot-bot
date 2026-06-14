"""
DRF Serializers for accounts app
"""
from rest_framework import serializers
from .models import ApiAccount


class ApiAccountSerializer(serializers.ModelSerializer):
    """
    API 账户序列化器
    返回时隐藏 api_secret (前端不展示)
    """
    api_secret_masked = serializers.SerializerMethodField()

    class Meta:
        model = ApiAccount
        fields = [
            'id',
            'account_name',
            'api_key',
            'api_secret_masked',  # 替代 api_secret，前端不展示明文
            'use_proxy',
            'proxy_url',
            'testnet',
            'is_active',
            'created_at',
            'updated_at',
        ]
        read_only_fields = ['id', 'created_at', 'updated_at']

    def get_api_secret_masked(self, obj):
        """返回掩码后的 secret"""
        secret = obj.api_secret
        if not secret:
            return ''
        if len(secret) <= 8:
            return '****'
        return f'{secret[:4]}{"*" * (len(secret) - 8)}{secret[-4:]}'


class ApiAccountCreateSerializer(serializers.ModelSerializer):
    """
    创建/更新 API 账户序列化器
    接收明文 api_secret 并加密存储
    """
    api_secret = serializers.CharField(write_only=True, required=True)

    class Meta:
        model = ApiAccount
        fields = [
            'account_name',
            'api_key',
            'api_secret',
            'use_proxy',
            'proxy_url',
            'testnet',
            'is_active',
        ]

    def create(self, validated_data):
        plain_secret = validated_data.pop('api_secret')
        account = ApiAccount(**validated_data)
        account.set_secret(plain_secret)
        account.save()
        return account

    def update(self, instance, validated_data):
        if 'api_secret' in validated_data:
            plain_secret = validated_data.pop('api_secret')
            instance.set_secret(plain_secret)
        for attr, value in validated_data.items():
            setattr(instance, attr, value)
        instance.save()
        return instance


class BalanceSerializer(serializers.Serializer):
    """余额序列化器"""
    asset = serializers.CharField()
    free = serializers.DecimalField(max_digits=32, decimal_places=16)
    locked = serializers.DecimalField(max_digits=32, decimal_places=16)
    total = serializers.DecimalField(max_digits=32, decimal_places=16)
