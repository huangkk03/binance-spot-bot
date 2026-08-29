"""
Binance 合约客户端
U本位永续合约: /fapi/v1/*
"""
import logging
import time
from decimal import Decimal
from typing import Dict, Optional

from binance.client import Client
from binance.exceptions import BinanceAPIException, BinanceRequestException

logger = logging.getLogger(__name__)


class BinanceFuturesClient:
    """Binance U本位合约 API 封装"""

    def __init__(self, client: Client):
        self.client = client
        # 同步服务器时间偏移
        self._sync_time()

    def _sync_time(self):
        """同步 Binance 合约服务器时间"""
        try:
            server_time = self.client.futures_time()['serverTime']
            local_time = int(time.time() * 1000)
            offset = server_time - local_time
            self.client.timestamp_offset = offset
            logger.info(f'Futures time offset: {offset}ms (synced)')
        except Exception as e:
            logger.warning(f'Futures time sync failed: {e}')

    def set_leverage(self, symbol: str, leverage: int) -> Dict:
        """设置杠杆倍数"""
        try:
            result = self.client.futures_change_leverage(symbol=symbol, leverage=leverage)
            return {'success': True, 'leverage': result.get('leverage', leverage)}
        except Exception as e:
            logger.error(f'Set leverage failed for {symbol}: {e}')
            return {'success': False, 'errors': [str(e)]}

    def set_margin_type(self, symbol: str, margin_type: str = 'ISOLATED') -> Dict:
        """设置保证金模式 ISOLATED/CROSSED (已创建持仓时无法修改, 忽略)"""
        try:
            self.client.futures_change_margin_type(symbol=symbol, marginType=margin_type)
            return {'success': True}
        except BinanceAPIException as e:
            if 'No need to change margin type' in str(e) or 'position' in str(e).lower():
                logger.debug(f'Margin type already set or position exists: {e.message}')
                return {'success': True}
            logger.error(f'Set margin type failed: {e}')
            return {'success': False, 'errors': [str(e)]}
        except Exception as e:
            return {'success': False, 'errors': [str(e)]}

    def open_market_order(self, symbol: str, direction: str, notional: Decimal, leverage: int) -> Dict:
        """
        开仓（市价单）
        direction: LONG / SHORT
        notional: 名义仓位 USDT
        """
        try:
            # 1. 设置杠杆
            self.set_leverage(symbol, leverage)
            # 2. 设置逐仓
            self.set_margin_type(symbol, 'ISOLATED')

            # 3. 计算数量: qty = notional / mark_price
            mark_price = Decimal(self.client.futures_mark_price(symbol=symbol)['markPrice'])
            qty = notional / mark_price

            # 4. 获取数量精度并量化
            exchange_info = self.client.futures_exchange_info()
            for s in exchange_info.get('symbols', []):
                if s['symbol'] == symbol:
                    for f in s.get('filters', []):
                        if f['filterType'] == 'MARKET_LOT_SIZE':
                            step = Decimal(str(f.get('stepSize', '0.001')))
                            qty = (qty // step) * step
                            break
                    break

            side = 'BUY' if direction == 'LONG' else 'SELL'

            # 5. 下单
            result = self.client.futures_create_order(
                symbol=symbol,
                side=side,
                type='MARKET',
                quantity=str(qty),
                positionSide=direction,
            )

            executed_qty = Decimal(result.get('executedQty', '0'))
            cum_quote = Decimal(result.get('cumQuote', '0'))
            avg_price = cum_quote / executed_qty if executed_qty > 0 else Decimal('0')

            return {
                'success': True,
                'order_id': str(result.get('orderId', '')),
                'symbol': symbol,
                'direction': direction,
                'executed_qty': str(executed_qty),
                'cummulative_quote_qty': str(cum_quote),
                'avg_price': str(avg_price),
                'leverage': leverage,
                'status': result.get('status', ''),
                'raw': result,
            }
        except BinanceAPIException as e:
            logger.error(f'Futures open failed ({e.status_code}): {e.message}')
            return {'success': False, 'errors': [e.message]}
        except Exception as e:
            logger.error(f'Futures open exception: {e}')
            return {'success': False, 'errors': [str(e)]}

    def close_position(self, symbol: str, direction: str, quantity: Optional[Decimal] = None) -> Dict:
        """
        平仓
        """
        try:
            # 如果未传 quantity，查当前持仓量
            if quantity is None:
                positions = self.client.futures_position_information(symbol=symbol)
                for p in positions:
                    if p['positionSide'] == direction:
                        quantity = Decimal(p.get('positionAmt', '0'))
                        break
                if quantity is None or quantity == 0:
                    return {'success': False, 'errors': ['没有持仓']}

            side = 'SELL' if direction == 'LONG' else 'BUY'

            result = self.client.futures_create_order(
                symbol=symbol,
                side=side,
                type='MARKET',
                quantity=str(abs(quantity)),
                positionSide=direction,
                reduceOnly=True,
            )

            executed_qty = Decimal(result.get('executedQty', '0'))
            cum_quote = Decimal(result.get('cumQuote', '0'))
            avg_price = cum_quote / executed_qty if executed_qty > 0 else Decimal('0')

            return {
                'success': True,
                'order_id': str(result.get('orderId', '')),
                'symbol': symbol,
                'direction': direction,
                'executed_qty': str(executed_qty),
                'cummulative_quote_qty': str(cum_quote),
                'avg_price': str(avg_price),
                'status': result.get('status', ''),
                'raw': result,
            }
        except BinanceAPIException as e:
            logger.error(f'Futures close failed ({e.status_code}): {e.message}')
            return {'success': False, 'errors': [e.message]}
        except Exception as e:
            logger.error(f'Futures close exception: {e}')
            return {'success': False, 'errors': [str(e)]}
