from django.urls import path
from . import views

urlpatterns = [
    path('alerts', views.alerts_list, name='alerts-list'),
    path('alerts/triggered', views.alerts_triggered, name='alerts-triggered'),
    path('alerts/scan', views.trigger_scan, name='alerts-scan'),
    path('funding-rates', views.funding_rate_alerts, name='funding-rates'),
]
