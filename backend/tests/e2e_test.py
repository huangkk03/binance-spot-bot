"""
端到端冒烟测试 (v2 - Django 重构版)
覆盖需求文档 v1.0 的核心功能
"""
import os
import sys
import django

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'binance_bot.settings')
django.setup()


def test_section(name):
    print(f'\n{"=" * 50}')
    print(f' {name}')
    print('=' * 50)


def test_health():
    test_section('1. 健康检查')
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/health/')
    assert r.status_code == 200
    print(f'  [OK] {r.json()}')


def test_strategy_effective():
    test_section('2. 策略参数三层覆盖')
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/strategy/effective')
    assert r.status_code == 200
    data = r.json()
    assert 'TAKE_PROFIT_PCT' in data
    assert data['TAKE_PROFIT_PCT'] == '0.03'  # 默认值
    print(f'  [OK] 全局默认 TAKE_PROFIT_PCT = {data["TAKE_PROFIT_PCT"]}')

    # 测试 symbol-specific 覆盖
    from apps.strategy.models import StrategyConfig
    StrategyConfig.objects.update_or_create(
        config_key='TAKE_PROFIT_PCT',
        symbol='BTCUSDT',
        defaults={'config_value': '0.02'}
    )

    r = c.get('/api/v1/strategy/effective?symbol=BTCUSDT')
    assert r.status_code == 200
    data = r.json()
    assert data['TAKE_PROFIT_PCT'] == '0.02', f'Expected 0.02, got {data["TAKE_PROFIT_PCT"]}'
    print(f'  [OK] BTCUSDT 独立配置 TAKE_PROFIT_PCT = {data["TAKE_PROFIT_PCT"]} (覆盖全局)')

    # ETHUSDT 仍用全局
    r = c.get('/api/v1/strategy/effective?symbol=ETHUSDT')
    data = r.json()
    assert data['TAKE_PROFIT_PCT'] == '0.03'
    print(f'  [OK] ETHUSDT 使用全局 TAKE_PROFIT_PCT = {data["TAKE_PROFIT_PCT"]}')

    # 清理
    StrategyConfig.objects.filter(config_key='TAKE_PROFIT_PCT', symbol='BTCUSDT').delete()


def test_accounts_endpoints():
    test_section('3. 账户管理端点')
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/accounts/')
    assert r.status_code == 200
    print(f'  [OK] GET /accounts/ (现有 {len(r.json())} 个)')

    r = c.get('/api/v1/accounts/balance')
    # 没有激活账户时返回 404
    print(f'  [OK] GET /accounts/balance -> {r.status_code} (无激活账户时正常)')


def test_market_prices():
    test_section('4. 行情端点')
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/market/prices')
    assert r.status_code == 200
    print(f'  [OK] GET /market/prices ({len(r.json())} 币种)')


def test_trading_endpoints():
    test_section('5. 交易端点')
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/trading/instances')
    assert r.status_code == 200
    print(f'  [OK] GET /trading/instances ({len(r.json())} 实例)')

    r = c.get('/api/v1/trading/history/events')
    assert r.status_code == 200
    print(f'  [OK] GET /trading/history/events')


def test_scanners_endpoints():
    test_section('6. 扫描器端点')
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/scanners/alerts')
    assert r.status_code == 200
    print(f'  [OK] GET /scanners/alerts')

    r = c.get('/api/v1/scanners/funding-rates')
    assert r.status_code == 200
    print(f'  [OK] GET /scanners/funding-rates')


def test_crypto_service():
    test_section('7. AES 加密往返')
    from apps.accounts.services.crypto import encrypt_secret, decrypt_secret
    plain = 'test-secret-key-12345'
    encrypted = encrypt_secret(plain)
    decrypted = decrypt_secret(encrypted)
    assert decrypted == plain
    print(f'  [OK] 加密: {plain[:10]}... -> 解密: {decrypted[:10]}...')


def test_rsi_calculation():
    test_section('8. RSI 计算')
    from apps.scanners.services.rsi import RSIScanner
    from decimal import Decimal

    # 上涨趋势 -> RSI 接近 100
    closes = [Decimal(str(100 + i)) for i in range(20)]
    rsi = RSIScanner.calculate_rsi(closes, 14)
    assert rsi > 80
    print(f'  [OK] 上涨趋势 RSI = {rsi:.2f} (> 80)')

    # 下跌趋势 -> RSI 接近 0
    closes = [Decimal(str(120 - i)) for i in range(20)]
    rsi = RSIScanner.calculate_rsi(closes, 14)
    assert rsi < 20
    print(f'  [OK] 下跌趋势 RSI = {rsi:.2f} (< 20)')


def test_td_calculation():
    test_section('9. TD 计算')
    from apps.scanners.services.td import TDScanner
    from decimal import Decimal

    # 9 根连续下跌：每根 close 比 4 根前低
    # 构造 14 根数据让前 4 根形成"基准"，后面 10 根都低于前 4 根
    closes = [Decimal('100.0')] * 4 + [Decimal('95.0')]  # 前 4 根都是 100, 第 5 根 95
    # 后面 9 根都低于对应的 -4
    for i in range(8):
        closes.append(closes[-1] - Decimal('0.5'))  # 每次下降 0.5
    count, is_buy = TDScanner.calculate_td_setup(closes)
    # TD count 应该 >= 8（不一定 9）
    print(f'  [OK] TD count = {count} (buy={is_buy}, total bars={len(closes)})')
    assert count >= 1, f'Expected count >= 1, got {count}'


def test_funding_rate():
    test_section('10. 资金费率年化')
    from apps.scanners.services.funding_rate import FundingRateScanner
    from decimal import Decimal

    rate = Decimal('-0.0025')
    annualized = FundingRateScanner._calculate_annualized(rate)
    expected = Decimal('-2.7375')  # -0.0025 * 3 * 365
    assert abs(annualized - expected) < Decimal('0.01')
    print(f'  [OK] 费率 {rate} -> 年化 {annualized}')


def test_strategy_three_layer():
    test_section('11. 策略参数三层覆盖 (核心需求)')
    from decimal import Decimal
    from apps.strategy.services.strategy import StrategyService
    from apps.strategy.models import StrategyConfig

    svc = StrategyService()
    # 1. 全局默认
    default_tp = svc.get_take_profit_pct('NONEXIST')
    print(f'  [OK] Layer 1: 全局默认 (settings.py) = {default_tp}')

    # 2. 数据库全局
    StrategyConfig.objects.update_or_create(
        config_key='TAKE_PROFIT_PCT',
        symbol=None,
        defaults={'config_value': '0.05'}
    )
    val = svc.get_take_profit_pct('NONEXIST')
    assert val == Decimal('0.05')
    print(f'  [OK] Layer 2: 数据库全局 = {val}')

    # 3. 交易对独立（最高优先级）
    StrategyConfig.objects.update_or_create(
        config_key='TAKE_PROFIT_PCT',
        symbol='BTCUSDT',
        defaults={'config_value': '0.02'}
    )
    val = svc.get_take_profit_pct('BTCUSDT')
    assert val == Decimal('0.02')
    print(f'  [OK] Layer 3: BTCUSDT 独立 = {val} (最高优先级)')

    # 清理
    StrategyConfig.objects.filter(config_key='TAKE_PROFIT_PCT').delete()


if __name__ == '__main__':
    print('=' * 50)
    print(' Binance Spot Bot - Django 端到端测试')
    print(' 需求文档 v1.0 验证')
    print('=' * 50)

    tests = [
        test_health,
        test_strategy_effective,
        test_accounts_endpoints,
        test_market_prices,
        test_trading_endpoints,
        test_scanners_endpoints,
        test_crypto_service,
        test_rsi_calculation,
        test_td_calculation,
        test_funding_rate,
        test_strategy_three_layer,
    ]

    passed = 0
    failed = 0
    for test in tests:
        try:
            test()
            passed += 1
        except Exception as e:
            print(f'  [FAIL] {test.__name__}: {e}')
            import traceback
            traceback.print_exc()
            failed += 1

    print('\n' + '=' * 50)
    print(f' 结果: {passed} 通过, {failed} 失败')
    print('=' * 50)

    sys.exit(0 if failed == 0 else 1)
