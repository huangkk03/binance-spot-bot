from django.urls import path
from . import views

urlpatterns = [
    path('btc-prediction/pdf', views.btc_prediction_pdf, name='report-btc-pdf'),
    path('btc-prediction/text', views.btc_prediction_text, name='report-btc-text'),
]
