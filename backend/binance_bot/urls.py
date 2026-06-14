"""
URL Configuration for binance_bot project.
"""
from django.contrib import admin
from django.urls import path, include
from django.http import JsonResponse


def health_check(request):
    return JsonResponse({'status': 'ok', 'service': 'binance-spot-bot-django'})


urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/v1/health/', health_check, name='health'),

    # API endpoints
    path('api/v1/accounts/', include('apps.accounts.urls')),
    path('api/v1/trading/', include('apps.trading.urls')),
    path('api/v1/market/', include('apps.market.urls')),
    path('api/v1/scanners/', include('apps.scanners.urls')),
    path('api/v1/notifications/', include('apps.notifications.urls')),
    path('api/v1/reports/', include('apps.reports.urls')),
    path('api/v1/ai/', include('apps.ai.urls')),
    path('api/v1/strategy/', include('apps.strategy.urls')),
]
