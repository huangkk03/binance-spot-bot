from django.urls import path
from . import views

urlpatterns = [
    path('config', views.config_list, name='config-list'),
    path('config/<str:key>', views.config_detail, name='config-detail'),
    path('test-notification', views.test_notification, name='test-notification'),
    path('test-ai', views.test_ai, name='test-ai'),
]
