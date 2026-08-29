"""
Smoke tests - 端到端冒烟测试
快速验证整个服务栈是否正常启动
"""
import os
import sys
import django

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'binance_bot.settings')
django.setup()


def test_health():
    """测试健康检查端点"""
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/health/')
    assert r.status_code == 200, f'Health check failed: {r.status_code}'
    data = r.json()
    assert data['status'] == 'ok'
    print('[OK] Health check')


def test_accounts_list():
    """测试账户列表"""
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/accounts/')
    assert r.status_code == 200
    assert isinstance(r.json(), list)
    print(f'[OK] Accounts list ({len(r.json())} accounts)')


def test_market_prices():
    """测试行情 API"""
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/market/prices')
    assert r.status_code == 200
    print(f'[OK] Market prices ({len(r.json())} prices)')


def test_trading_instances():
    """测试交易实例列表"""
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/trading/instances')
    assert r.status_code == 200
    print(f'[OK] Trading instances ({len(r.json())} instances)')


def test_scanners_alerts():
    """测试扫描器报警"""
    from django.test import Client
    c = Client()
    r = c.get('/api/v1/scanners/alerts')
    assert r.status_code == 200
    print(f'[OK] Scanners alerts')


def test_crypto_service():
    """测试加密服务"""
    from apps.accounts.services.crypto import encrypt_secret, decrypt_secret

    plain = 'test-secret-12345'
    encrypted = encrypt_secret(plain)
    decrypted = decrypt_secret(encrypted)
    assert decrypted == plain, f'Crypto round-trip failed: {decrypted}'
    print('[OK] Crypto round-trip')


def test_rsi_calculation():
    """测试 RSI 计算"""
    from apps.scanners.services.rsi import RSIScanner
    from decimal import Decimal

    # 模拟递增价格 -> RSI 应该接近 100
    closes = [Decimal(str(100 + i)) for i in range(20)]
    rsi = RSIScanner.calculate_rsi(closes, 14)
    assert rsi is not None
    assert 80 < rsi <= 100, f'RSI for uptrend should be > 80, got {rsi}'
    print(f'[OK] RSI uptrend = {rsi:.2f}')

    # 模拟递减价格 -> RSI 应该接近 0
    closes = [Decimal(str(120 - i)) for i in range(20)]
    rsi = RSIScanner.calculate_rsi(closes, 14)
    assert rsi is not None
    assert 0 <= rsi < 20, f'RSI for downtrend should be < 20, got {rsi}'
    print(f'[OK] RSI downtrend = {rsi:.2f}')


def test_td_calculation():
    """测试 TD 计算"""
    from apps.scanners.services.td import TDScanner
    from decimal import Decimal

    # 模拟连续 9 根下跌 -> TD count=9, is_buy=True
    closes = [Decimal('100')]
    for i in range(9):
        closes.append(closes[-1] - Decimal('1'))  # 收盘 < 4 根前
    count, is_buy = TDScanner.calculate_td_setup(closes)
    assert count >= 9, f'TD count should be >= 9, got {count}'
    print(f'[OK] TD downtrend = {count} (buy={is_buy})')


def test_funding_rate_format():
    """测试资金费率格式化"""
    from apps.scanners.services.funding_rate import FundingRateScanner
    from decimal import Decimal

    rate = Decimal('-0.0025')
    annualized = FundingRateScanner._calculate_annualized(rate)
    expected = Decimal('-2.7375')  # -0.0025 * 3 * 365
    assert abs(annualized - expected) < Decimal('0.01'), f'Annualized calc wrong: {annualized}'
    print(f'[OK] Annualized rate: {rate} -> {annualized}')


if __name__ == '__main__':
    print('=' * 50)
    print('Binance Spot Bot - Django Smoke Tests')
    print('=' * 50)

    tests = [
        test_health,
        test_accounts_list,
        test_market_prices,
        test_trading_instances,
        test_scanners_alerts,
        test_crypto_service,
        test_rsi_calculation,
        test_td_calculation,
        test_funding_rate_format,
    ]

    passed = 0
    failed = 0
    for test in tests:
        try:
            test()
            passed += 1
        except Exception as e:
            print(f'[FAIL] {test.__name__}: {e}')
            failed += 1

    print('=' * 50)
    print(f'Results: {passed} passed, {failed} failed')
    print('=' * 50)

    sys.exit(0 if failed == 0 else 1)
