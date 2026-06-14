"""
Scanners Serializers
"""
from rest_framework import serializers
from .models import PriceAlert, FundingRateAlert


class PriceAlertSerializer(serializers.ModelSerializer):
    class Meta:
        model = PriceAlert
        fields = '__all__'


class FundingRateAlertSerializer(serializers.ModelSerializer):
    class Meta:
        model = FundingRateAlert
        fields = '__all__'
