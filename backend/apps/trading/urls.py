from django.urls import path
from . import views

urlpatterns = [
    # Tick
    path('tick', views.tick, name='trading-tick'),

    # 手动开仓
    path('real-trade/open', views.real_trade_open, name='trading-real-open'),

    # 实例列表
    path('instances', views.instances_list, name='trading-instances'),

    # 历史
    path('history/events', views.history_events, name='trading-events'),
    path('history/orders', views.history_orders, name='trading-orders'),
]
