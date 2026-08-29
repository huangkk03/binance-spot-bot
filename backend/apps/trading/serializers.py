"""
Trading serializers
"""
from rest_framework import serializers
from .models import (
    CycleInstance, TradeRecord, CycleOpenRecord, InstanceEvent
)


class CycleInstanceSerializer(serializers.ModelSerializer):
    symbol_id = serializers.SerializerMethodField()

    class Meta:
        model = CycleInstance
        fields = '__all__'

    def get_symbol_id(self, obj):
        return f'{obj.symbol}#{obj.instance_id}'


class TradeRecordSerializer(serializers.ModelSerializer):
    class Meta:
        model = TradeRecord
        fields = '__all__'


class CycleOpenRecordSerializer(serializers.ModelSerializer):
    class Meta:
        model = CycleOpenRecord
        fields = '__all__'


class InstanceEventSerializer(serializers.ModelSerializer):
    class Meta:
        model = InstanceEvent
        fields = '__all__'
