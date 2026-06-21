from rest_framework import serializers
from .models import FuturesInstance


class FuturesInstanceSerializer(serializers.ModelSerializer):
    class Meta:
        model = FuturesInstance
        fields = '__all__'
