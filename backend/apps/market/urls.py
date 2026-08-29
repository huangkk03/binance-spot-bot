from django.urls import path
from . import views

urlpatterns = [
    path('prices', views.prices_all, name='market-prices'),
    path('prices/<str:symbol>', views.price_by_symbol, name='market-price-symbol'),
    path('prices/subscribe/<str:symbol>', views.price_subscribe, name='market-subscribe'),
]
