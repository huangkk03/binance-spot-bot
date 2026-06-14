"""
交易核心引擎
复利循环: 开仓 → 止盈 → 复利再买入 → 重复
替代 Java 的 RealTradingService (864 行)
"""
import logging
from decimal import Decimal, ROUND_DOWN
from typing import Dict, List, Optional

from django.utils import timezone
from django.db import transaction

from apps.accounts.models import ApiAccount
from apps.trading.models import (
    CycleInstance, TradeRecord, CycleOpenRecord, InstanceEvent
)
from apps.trading.services.binance_client import BinanceTradingClient
from apps.trading.services.precision import PrecisionService
from apps.market.services.price_cache import PriceCacheService

logger = logging.getLogger(__name__)

# 交易手续费率 (0.1% taker)
TAKER_FEE_RATE = Decimal('0.001')


class TradingEngine:
    """
    真实交易核心引擎
    """

    def __init__(self):
        self.active_account = ApiAccount.get_active()
        self.client = None
        self.trading_client = None
        if self.active_account:
            from binance.client import Client
            self.client = Client(
                api_key=self.active_account.api_key,
                api_secret=self.active_account.get_secret(),
                testnet=self.active_account.testnet,
            )
            self.trading_client = BinanceTradingClient(self.client)

    def get_take_profit_pct(self, symbol: str) -> Decimal:
        """获取止盈百分比（默认 0.03 = 3%）"""
        from django.conf import settings
        return Decimal(str(settings.TRADING['TAKE_PROFIT_PCT']))

    def get_stop_loss_pct(self, symbol: str) -> Decimal:
        """获取止损百分比（默认 0.10 = 10%）"""
        from django.conf import settings
        return Decimal(str(settings.TRADING['STOP_LOSS_PCT']))

    def get_quote_reserve(self) -> Decimal:
        """获取 quote 保留金额"""
        from django.conf import settings
        return Decimal(str(settings.TRADING['QUOTE_RESERVE']))

    def get_max_orders_per_tick(self) -> int:
        """每 tick 最大订单数"""
        from django.conf import settings
        return int(settings.TRADING['MAX_ORDERS_PER_TICK'])

    def execute_tick(self, symbols: List[str]) -> List[str]:
        """
        执行一轮 tick:
        - 获取所有实例
        - 对每个实例:
            - 如果 is_open=False，等待 reentry_price
            - 如果 is_open=True，检查止盈/止损
        """
        if not self.active_account:
            logger.warning('No active API account, skipping tick')
            return []

        if not self.trading_client:
            logger.error('Trading client not initialized')
            return []

        actions = []
        max_orders = self.get_max_orders_per_tick()
        remaining = max_orders

        for symbol in symbols:
            if remaining <= 0:
                break

            try:
                # 获取该币种所有实例
                instances = CycleInstance.objects.filter(symbol=symbol).order_by('instance_id')

                for inst in instances:
                    if remaining <= 0:
                        break

                    price = PriceCacheService.get_price(symbol)
                    if not price or price <= 0:
                        continue

                    if not inst.is_open:
                        # 平仓状态：检查是否可以重新入场
                        result = self._try_open_position(inst, price)
                    else:
                        # 开仓状态：检查止盈/止损
                        result = self._try_close_position(inst, price)

                    if result:
                        actions.append(result)
                        remaining -= 1

            except Exception as e:
                logger.error(f'Error in tick for {symbol}: {e}', exc_info=True)

        return actions

    def _try_open_position(self, inst: CycleInstance, price: Decimal) -> Optional[str]:
        """
        尝试开仓（reentry）
        条件: 当前价格 <= reentry_price
        """
        reentry_price = inst.reentry_price or inst.anchor_price
        if reentry_price <= 0:
            return None

        if price > reentry_price:
            return None  # 价格高于锚定价，等待

        # 计算可花费的 quote 金额
        quote_amount = inst.quote_amount
        if quote_amount <= 0:
            # 用默认值
            from django.conf import settings
            quote_amount = Decimal('10')  # 默认 10 USDT

        return self._execute_buy(inst, price, quote_amount)

    def _try_close_position(self, inst: CycleInstance, price: Decimal) -> Optional[str]:
        """
        尝试平仓（止盈/止损）
        条件:
            - take_profit: price >= cycle_start_price * (1 + take_profit_pct)
            - stop_loss: price <= cycle_start_price * (1 - stop_loss_pct)
        """
        if inst.base_qty <= 0 or inst.cycle_start_price <= 0:
            return None

        take_profit_pct = self.get_take_profit_pct(inst.symbol)
        stop_loss_pct = self.get_stop_loss_pct(inst.symbol)

        take_profit_price = inst.cycle_start_price * (Decimal('1') + take_profit_pct)
        stop_loss_price = inst.cycle_start_price * (Decimal('1') - stop_loss_pct)

        if take_profit_pct > 0 and price >= take_profit_price:
            return self._execute_sell_take_profit(inst, price)
        elif stop_loss_pct > 0 and price <= stop_loss_price:
            return self._execute_sell_stop_loss(inst, price)

        return None

    def _execute_buy(self, inst: CycleInstance, price: Decimal, quote_amount: Decimal) -> Optional[str]:
        """执行买入"""
        symbol = inst.symbol
        try:
            # 获取交易对精度
            symbol_info = PrecisionService.get_symbol_info(self.client, symbol)
            step_size = symbol_info.get('stepSize', Decimal('0.00000001'))
            tick_size = symbol_info.get('tickSize', Decimal('0.01'))

            # 量化 quote 金额
            quantized_quote = PrecisionService.quantize_quantity(quote_amount, tick_size)
            if quantized_quote <= 0:
                return None

            # 下买单
            order_result = self.trading_client.place_market_buy(symbol, quantized_quote)
            if not order_result['success']:
                logger.error(f'Buy order failed for {symbol}: {order_result.get("errors")}')
                return None

            executed_qty = Decimal(order_result['executed_qty'])
            cummulative_quote_qty = Decimal(order_result['cummulative_quote_qty'])

            # 记录交易
            with transaction.atomic():
                # 更新实例
                inst.is_open = True
                inst.base_qty = executed_qty
                inst.spent_quote = cummulative_quote_qty
                inst.quote_amount = cummulative_quote_qty  # 复利：本次花费=下次可用
                inst.cycle_start_price = price
                inst.last_action_price = price
                inst.reentry_price = Decimal('0')  # 开仓时清零
                if inst.anchor_price == 0:
                    inst.anchor_price = price
                inst.cycle_id = inst.cycle_id + 1
                inst.save()

                # 记录 trade
                TradeRecord.objects.create(
                    order_id=order_result['order_id'],
                    symbol=symbol,
                    side='BUY',
                    status=order_result['status'],
                    executed_qty=executed_qty,
                    cummulative_quote_qty=cummulative_quote_qty,
                    avg_price=price if executed_qty == 0 else cummulative_quote_qty / executed_qty,
                    payload_json=str(order_result.get('raw', {})),
                )

                # 记录开仓
                CycleOpenRecord.objects.create(
                    symbol=symbol,
                    instance_id=inst.instance_id,
                    cycle_id=inst.cycle_id,
                    start_price=price,
                    quote_amount=cummulative_quote_qty,
                    opened_at=timezone.now(),
                )

                # 记录事件
                InstanceEvent.objects.create(
                    symbol=symbol,
                    instance_id=inst.instance_id,
                    cycle_id=inst.cycle_id,
                    event='BUY_OPEN',
                    price=price,
                    base_qty=executed_qty,
                    quote_amount=cummulative_quote_qty,
                    note=f'order_id={order_result["order_id"]}',
                )

            msg = f'BUY_OPEN: {symbol}#{inst.instance_id} cycle={inst.cycle_id} qty={executed_qty} at {price}'
            logger.info(msg)
            return msg

        except Exception as e:
            logger.error(f'Error executing buy for {symbol}: {e}', exc_info=True)
            return None

    def _execute_sell_take_profit(self, inst: CycleInstance, price: Decimal) -> Optional[str]:
        """执行止盈卖出"""
        return self._execute_sell(inst, price, 'SELL_TP')

    def _execute_sell_stop_loss(self, inst: CycleInstance, price: Decimal) -> Optional[str]:
        """执行止损卖出"""
        return self._execute_sell(inst, price, 'SELL_SL')

    def _execute_sell(self, inst: CycleInstance, price: Decimal, event_type: str) -> Optional[str]:
        """执行卖出"""
        symbol = inst.symbol
        try:
            base_qty = inst.base_qty
            if base_qty <= 0:
                return None

            # 下卖单
            order_result = self.trading_client.place_market_sell(symbol, base_qty)
            if not order_result['success']:
                logger.error(f'Sell order failed for {symbol}: {order_result.get("errors")}')
                return None

            executed_qty = Decimal(order_result['executed_qty'])
            cummulative_quote_qty = Decimal(order_result['cummulative_quote_qty'])

            # 计算利润
            sell_fee = cummulative_quote_qty * TAKER_FEE_RATE
            net_quote = cummulative_quote_qty - sell_fee
            profit = net_quote - inst.spent_quote

            # 计算下次开仓金额（复利）
            reentry_price = inst.anchor_price if event_type == 'SELL_TP' else Decimal('0')

            with transaction.atomic():
                inst.is_open = False
                inst.base_qty = Decimal('0')
                inst.spent_quote = Decimal('0')
                inst.quote_amount = net_quote  # 复利本金
                inst.cycle_start_price = Decimal('0')
                inst.last_action_price = price
                inst.reentry_price = reentry_price
                inst.cumulative_profit = inst.cumulative_profit + profit
                inst.save()

                # 记录 trade
                TradeRecord.objects.create(
                    order_id=order_result['order_id'],
                    symbol=symbol,
                    side='SELL',
                    status=order_result['status'],
                    executed_qty=executed_qty,
                    cummulative_quote_qty=cummulative_quote_qty,
                    avg_price=price,
                    payload_json=str(order_result.get('raw', {})),
                )

                # 记录事件
                InstanceEvent.objects.create(
                    symbol=symbol,
                    instance_id=inst.instance_id,
                    cycle_id=inst.cycle_id,
                    event=event_type,
                    price=price,
                    base_qty=executed_qty,
                    quote_amount=cummulative_quote_qty,
                    note=f'order_id={order_result["order_id"]}, profit={profit}',
                )

            action = 'TAKE_PROFIT' if event_type == 'SELL_TP' else 'STOP_LOSS'
            msg = f'{action}: {symbol}#{inst.instance_id} cycle={inst.cycle_id} qty={executed_qty} at {price} profit={profit}'
            logger.info(msg)
            return msg

        except Exception as e:
            logger.error(f'Error executing sell for {symbol}: {e}', exc_info=True)
            return None

    def manual_open_position(self, symbol: str, quote_amount: Decimal) -> Dict:
        """手动开仓（API 端点）"""
        if not self.trading_client:
            return {'success': False, 'errors': ['没有激活的交易账户']}

        # 找一个未开仓的实例，或创建新实例
        inst = CycleInstance.objects.filter(symbol=symbol, is_open=False).first()
        if not inst:
            # 创建新实例
            next_id = CycleInstance.objects.filter(symbol=symbol).count() + 1
            inst = CycleInstance.objects.create(
                symbol=symbol,
                instance_id=next_id,
                is_open=False,
                quote_amount=quote_amount,
            )

        price = PriceCacheService.get_price(symbol)
        if not price or price <= 0:
            return {'success': False, 'errors': ['无法获取价格']}

        result = self._execute_buy(inst, price, quote_amount)
        if result:
            return {'success': True, 'message': result}
        return {'success': False, 'errors': ['下单失败']}
