from django.urls import path
from . import views

urlpatterns = [
    # 列表 + 创建
    path('', views.account_list, name='account-list'),

    # 测试 API 凭据（不存储）
    path('test', views.account_test, name='account-test'),

    # 代理状态
    path('proxy-status', views.proxy_status, name='account-proxy-status'),

    # 余额查询（激活账户）
    path('balance', views.account_balance_all, name='account-balance-all'),
    path('balance/<str:asset>', views.account_balance_asset, name='account-balance-asset'),

    # 详情 + 更新 + 删除
    path('<int:pk>', views.account_detail, name='account-detail'),

    # 激活
    path('<int:pk>/activate', views.account_activate, name='account-activate'),

    # 余额查询（指定账户）
    path('<int:pk>/balances', views.account_balance_by_id, name='account-balance-by-id'),
]
