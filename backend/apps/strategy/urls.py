from django.urls import path
from . import views

urlpatterns = [
    path('config', views.config_list, name='strategy-config-list'),
    path('config/<str:key>', views.config_detail, name='strategy-config-detail'),
    path('config/<str:key>/effective', views.config_effective, name='strategy-config-effective'),
    path('effective', views.effective_all, name='strategy-effective-all'),
]
