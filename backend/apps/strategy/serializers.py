"""
策略配置 Serializers
"""
from rest_framework import serializers
from .models import StrategyConfig


class StrategyConfigSerializer(serializers.ModelSerializer):
    class Meta:
        model = StrategyConfig
        fields = ['id', 'config_key', 'config_value', 'symbol', 'description', 'created_at', 'updated_at']
        read_only_fields = ['id', 'created_at', 'updated_at']


class StrategyConfigUpsertSerializer(serializers.Serializer):
    """用于 upsert 配置"""
    config_key = serializers.CharField(max_length=50)
    config_value = serializers.CharField(max_length=200)
    symbol = serializers.CharField(max_length=20, required=False, allow_null=True, allow_blank=True)
    description = serializers.CharField(max_length=200, required=False, allow_blank=True, default='')
