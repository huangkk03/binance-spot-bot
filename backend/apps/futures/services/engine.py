"""
U本位合约交易引擎
做多(LONG)和做空(SHORT)对称复利
"""
import logging
from decimal import Decimal
from typing import Dict, List, Optional

from django.utils import timezone
from django.db import transaction

from apps.accounts.models import ApiAccount
from apps.futures.models import FuturesInstance
from apps.trading.models import TradeRecord, InstanceEvent
from apps.futures.services.binance_futures import BinanceFuturesClient
from apps.market.services.price_cache import PriceCacheService

logger = logging.getLogger(__name__)

FUTURES_SYMBOLS = ['BTCUSDT', 'ETHUSDT']


def _send_wechat_notify(content: str):
    """同步发送企业微信通知"""
    try:
        import requests as sync_requests
        from apps.notifications.models import ApiConfig
        webhook_url = ApiConfig.get_value('WECHAT_WEBHOOK_URL')
        if not webhook_url: return
        sync_requests.post(webhook_url, json={'msgtype': 'text', 'text': {'content': content}}, timeout=10)
    except Exception:
        pass


class FuturesEngine:
    """U本位合约交易引擎"""

    def __init__(self):
        self.active_account = ApiAccount.get_active()
        self.client = None
        self.futures_client = None
        if self.active_account:
            from binance.client import Client
            self.client = Client(
                api_key=self.active_account.api_key,
                api_secret=self.active_account.get_secret(),
                testnet=self.active_account.testnet,
            )
            self.futures_client = BinanceFuturesClient(self.client)

    def get_take_profit_pct(self, symbol: str) -> Decimal:
        from apps.strategy.models import StrategyConfig
        return StrategyConfig.objects.get_decimal('FUTURES_TAKE_PROFIT_PCT', Decimal('0.015'), symbol)

    def get_stop_loss_pct(self, symbol: str) -> Decimal:
        from apps.strategy.models import StrategyConfig
        return StrategyConfig.objects.get_decimal('FUTURES_STOP_LOSS_PCT', Decimal('0.10'), symbol)

    def get_default_leverage(self, symbol: str) -> int:
        from apps.strategy.models import StrategyConfig
        return StrategyConfig.objects.get_int('FUTURES_DEFAULT_LEVERAGE', 100, symbol)

    def get_max_instances(self, symbol: str) -> int:
        from apps.strategy.models import StrategyConfig
        return StrategyConfig.objects.get_int('FUTURES_MAX_INSTANCES', 5, symbol)

    def execute_tick(self) -> List[str]:
        if not self.active_account or not self.futures_client:
            return []

        actions = []
        for symbol in FUTURES_SYMBOLS:
            try:
                for inst in FuturesInstance.objects.filter(symbol=symbol, is_open=True):
                    price = PriceCacheService.get_price(symbol)
                    if not price or price <= 0: continue
                    result = self._try_close(inst, price)
                    if result: actions.append(result)

                for inst in FuturesInstance.objects.filter(symbol=symbol, is_open=False, reentry_price__gt=0):
                    price = PriceCacheService.get_price(symbol)
                    if not price or price <= 0: continue
                    result = self._try_reentry(inst, price)
                    if result: actions.append(result)
            except Exception as e:
                logger.error(f'Futures tick error for {symbol}: {e}', exc_info=True)

        return actions

    def _try_close(self, inst: FuturesInstance, price: Decimal) -> Optional[str]:
        """检查止盈/止损"""
        if inst.base_qty <= 0 or inst.cycle_start_price <= 0:
            return None

        tp_pct = self.get_take_profit_pct(inst.symbol)
        sl_pct = self.get_stop_loss_pct(inst.symbol)

        if inst.direction == 'LONG':
            tp_price = inst.cycle_start_price * (Decimal('1') + tp_pct)
            sl_price = inst.cycle_start_price * (Decimal('1') - sl_pct)
            if tp_pct > 0 and price >= tp_price: return self._execute_close(inst, price, 'SELL_TP')
            if sl_pct > 0 and price <= sl_price: return self._execute_close(inst, price, 'SELL_SL')
        else:  # SHORT
            tp_price = inst.cycle_start_price * (Decimal('1') - tp_pct)
            sl_price = inst.cycle_start_price * (Decimal('1') + sl_pct)
            if tp_pct > 0 and price <= tp_price: return self._execute_close(inst, price, 'SELL_TP')
            if sl_pct > 0 and price >= sl_price: return self._execute_close(inst, price, 'SELL_SL')
        return None

    def _try_reentry(self, inst: FuturesInstance, price: Decimal) -> Optional[str]:
        """检查是否可复利再开仓"""
        if inst.reentry_price <= 0: return None

        if inst.direction == 'LONG' and price <= inst.reentry_price:
            return self._execute_open(inst, price, inst.margin)
        if inst.direction == 'SHORT' and price >= inst.reentry_price:
            return self._execute_open(inst, price, inst.margin)
        return None

    def _execute_open(self, inst: FuturesInstance, price: Decimal, margin: Decimal) -> Optional[str]:
        """执行开仓 (市价单)"""
        symbol = inst.symbol
        notional = margin * inst.leverage
        try:
            order = self.futures_client.open_market_order(symbol, inst.direction, notional, inst.leverage)
            if not order['success']:
                logger.error(f'Futures open failed: {order.get("errors")}')
                return None

            avg_price = Decimal(order['avg_price'])
            qty = Decimal(order['executed_qty'])

            with transaction.atomic():
                inst.is_open = True
                inst.base_qty = qty
                inst.margin = margin
                inst.notional = notional
                inst.cycle_start_price = avg_price
                inst.cycle_id = inst.cycle_id + 1
                inst.reentry_price = Decimal('0')
                if inst.anchor_price == 0: inst.anchor_price = avg_price
                inst.save()

                TradeRecord.objects.create(
                    order_id=order['order_id'], symbol=symbol, side='F_OPEN',
                    status=order['status'], executed_qty=qty,
                    cummulative_quote_qty=Decimal(order['cummulative_quote_qty']),
                    avg_price=avg_price, payload_json=str(order.get('raw', {}))
                )

                tag = '复利' if inst.cycle_id > 1 else '首次'
                dir_cn = '做多' if inst.direction == 'LONG' else '做空'
                InstanceEvent.objects.create(
                    symbol=symbol, instance_id=inst.instance_id, cycle_id=inst.cycle_id,
                    event='FUTURES_OPEN', price=avg_price, base_qty=qty,
                    quote_amount=notional, note=f'order_id={order["order_id"]} {tag}{dir_cn} x{inst.leverage}'
                )

            msg = f'FUTURES {dir_cn}: {symbol}#{inst.instance_id} qty={qty} at {avg_price} margin={margin} x{inst.leverage}'
            logger.info(msg)
            _send_wechat_notify(f'📊【合约{tag}{dir_cn}】{symbol}#{inst.instance_id}\n入场价: {avg_price}\n数量: {qty}\n保证金: {margin} USDT\n杠杆: {inst.leverage}x\n名义仓位: {notional} USDT')
            return msg
        except Exception as e:
            logger.error(f'Futures open error: {e}', exc_info=True)
            return None

    def _execute_close(self, inst: FuturesInstance, price: Decimal, event_type: str) -> Optional[str]:
        """执行平仓"""
        symbol = inst.symbol
        try:
            order = self.futures_client.close_position(symbol, inst.direction, inst.base_qty)
            if not order['success']:
                logger.error(f'Futures close failed: {order.get("errors")}')
                return None

            avg_price = Decimal(order.get('avg_price', '0'))
            cum_quote = Decimal(order.get('cummulative_quote_qty', '0'))

            # 计算利润
            if inst.direction == 'LONG':
                profit = (avg_price - inst.cycle_start_price) / inst.cycle_start_price * inst.notional
            else:
                profit = (inst.cycle_start_price - avg_price) / inst.cycle_start_price * inst.notional

            net_margin = inst.margin + profit
            reentry = inst.anchor_price if event_type == 'SELL_TP' else Decimal('0')

            with transaction.atomic():
                inst.is_open = False
                inst.base_qty = Decimal('0')
                inst.margin = net_margin if net_margin > 0 else Decimal('0')
                inst.notional = Decimal('0')
                inst.cycle_start_price = Decimal('0')
                inst.reentry_price = reentry
                inst.cumulative_profit = inst.cumulative_profit + profit
                inst.save()

                TradeRecord.objects.create(
                    order_id=order['order_id'], symbol=symbol, side='F_CLOSE',
                    status=order['status'], executed_qty=inst.base_qty if inst.base_qty > 0 else Decimal(order['executed_qty']),
                    cummulative_quote_qty=cum_quote, avg_price=avg_price,
                    payload_json=str(order.get('raw', {}))
                )

                InstanceEvent.objects.create(
                    symbol=symbol, instance_id=inst.instance_id, cycle_id=inst.cycle_id,
                    event=event_type, price=avg_price, base_qty=inst.base_qty if inst.base_qty > 0 else Decimal(order['executed_qty']),
                    quote_amount=cum_quote, note=f'order_id={order["order_id"]} profit={profit}'
                )

            tag = '止盈' if event_type == 'SELL_TP' else '止损'
            emoji = '📈' if profit > 0 else '📉'
            logger.info(f'FUTURES {tag}: {symbol}#{inst.instance_id} profit={profit}')
            _send_wechat_notify(f'{emoji}【合约{tag}】{symbol}#{inst.instance_id}\n平仓价: {avg_price}\n盈亏: {profit} USDT\n保证金余额: {net_margin} USDT')
            return f'{tag}: {symbol}#{inst.instance_id} profit={profit}'
        except Exception as e:
            logger.error(f'Futures close error: {e}', exc_info=True)
            return None

    def manual_open(self, symbol: str, direction: str, notional: Decimal, leverage: int) -> Dict:
        """手动首次开仓"""
        if not self.futures_client:
            return {'success': False, 'errors': ['没有激活的账户']}

        margin = notional / leverage

        # 检查实例数
        max_inst = self.get_max_instances(symbol)
        count = FuturesInstance.objects.filter(symbol=symbol).count()
        is_new = False

        inst = FuturesInstance.objects.filter(symbol=symbol, is_open=False).first()
        if not inst:
            if count >= max_inst:
                return {'success': False, 'errors': [f'已达到最大实例数 {max_inst}']}
            inst = FuturesInstance.objects.create(
                symbol=symbol, instance_id=count + 1, direction=direction,
                leverage=leverage, margin=margin, notional=notional
            )
            is_new = True

        try:
            result = self._execute_open(inst, PriceCacheService.get_price(symbol) or Decimal('0'), margin)
            if not result and is_new: inst.delete()
            if not result: return {'success': False, 'errors': ['下单失败']}
            return {'success': True, 'message': result}
        except Exception as e:
            if is_new: inst.delete()
            return {'success': False, 'errors': [str(e)]}
