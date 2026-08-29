"""
Binance 交易客户端
封装 python-binance Client，提供下单/查询 API
"""
import logging
from decimal import Decimal
from typing import Optional, Dict
from binance.client import Client
from binance.exceptions import BinanceAPIException, BinanceRequestException

logger = logging.getLogger(__name__)


class BinanceTradingClient:
    """
    封装 Binance 现货交易
    - 下单 (市价单/限价单)
    - 查询订单状态
    - 签名由 Client 内部处理
    """

    def __init__(self, client: Client):
        self.client = client

    def place_market_buy(self, symbol: str, quote_quantity: Decimal) -> Dict:
        """
        市价买单（按 quote 数量买入）
        参数: symbol, quoteOrderQty (USDT 金额)
        """
        try:
            result = self.client.order_market_buy(
                symbol=symbol,
                quoteOrderQty=str(quote_quantity)
            )
            # 计算实际成交均价和手续费
            executed_qty = result.get('executedQty', '0')
            cummulative_quote_qty = result.get('cummulativeQuoteQty', '0')
            avg_price = '0'
            if float(executed_qty) > 0:
                avg_price = str(float(cummulative_quote_qty) / float(executed_qty))

            # 从 fills 中累加手续费
            total_commission = 0.0
            commission_asset = ''
            for fill in result.get('fills', []):
                total_commission += float(fill.get('commission', 0))
                commission_asset = fill.get('commissionAsset', '') or commission_asset

            return {
                'success': True,
                'order_id': str(result.get('orderId', '')),
                'symbol': result.get('symbol', symbol),
                'executed_qty': executed_qty,
                'cummulative_quote_qty': cummulative_quote_qty,
                'avg_price': avg_price,
                'commission': str(total_commission),
                'commission_asset': commission_asset,
                'status': result.get('status', ''),
                'raw': result,
            }
        except BinanceAPIException as e:
            logger.error(f'Binance API error on BUY {symbol}: {e.status_code} {e.message}')
            return {'success': False, 'errors': [e.message]}
        except BinanceRequestException as e:
            logger.error(f'Binance request error on BUY {symbol}: {e}')
            return {'success': False, 'errors': [str(e)]}
        except Exception as e:
            logger.error(f'Unexpected error on BUY {symbol}: {e}')
            return {'success': False, 'errors': [str(e)]}

    def place_market_sell(self, symbol: str, quantity: Decimal) -> Dict:
        """
        市价卖单（按 base 数量卖出）
        参数: symbol, quantity (基础币数量)
        """
        try:
            result = self.client.order_market_sell(
                symbol=symbol,
                quantity=str(quantity)
            )
            executed_qty = result.get('executedQty', '0')
            cummulative_quote_qty = result.get('cummulativeQuoteQty', '0')
            avg_price = '0'
            if float(executed_qty) > 0:
                avg_price = str(float(cummulative_quote_qty) / float(executed_qty))

            total_commission = 0.0
            commission_asset = ''
            for fill in result.get('fills', []):
                total_commission += float(fill.get('commission', 0))
                commission_asset = fill.get('commissionAsset', '') or commission_asset

            return {
                'success': True,
                'order_id': str(result.get('orderId', '')),
                'symbol': result.get('symbol', symbol),
                'executed_qty': executed_qty,
                'cummulative_quote_qty': cummulative_quote_qty,
                'avg_price': avg_price,
                'commission': str(total_commission),
                'commission_asset': commission_asset,
                'status': result.get('status', ''),
                'raw': result,
            }
        except BinanceAPIException as e:
            logger.error(f'Binance API error on SELL {symbol}: {e.status_code} {e.message}')
            return {'success': False, 'errors': [e.message]}
        except BinanceRequestException as e:
            logger.error(f'Binance request error on SELL {symbol}: {e}')
            return {'success': False, 'errors': [str(e)]}
        except Exception as e:
            logger.error(f'Unexpected error on SELL {symbol}: {e}')
            return {'success': False, 'errors': [str(e)]}

    def get_order_status(self, symbol: str, order_id: str) -> Dict:
        """查询订单状态"""
        try:
            result = self.client.get_order(symbol=symbol, orderId=order_id)
            return {
                'success': True,
                'status': result.get('status', ''),
                'executed_qty': result.get('executedQty', '0'),
                'cummulative_quote_qty': result.get('cummulativeQuoteQty', '0'),
                'raw': result,
            }
        except Exception as e:
            logger.error(f'Failed to get order status {symbol}/{order_id}: {e}')
            return {'success': False, 'errors': [str(e)]}
