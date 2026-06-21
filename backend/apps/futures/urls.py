from django.urls import path
from . import views

urlpatterns = [
    path('open', views.futures_open, name='futures-open'),
    path('close/<int:pk>', views.futures_close, name='futures-close'),
    path('instances', views.futures_instances, name='futures-instances'),
    path('tick', views.futures_tick, name='futures-tick'),
    path('history/events', views.futures_history_events, name='futures-events'),
]
