# API 接口文档

## 概述

- **Base URL**: `http://localhost:8080/api/v1`
- **Content-Type**: `application/json`
- **认证**: 无（内部系统）

---

## 账户管理

### 1. 充值模拟资金

**POST** `/deposit`

向模拟账户充值资金。

**请求体**

```json
{
  "asset": "USDT",
  "amount": 1000.00,
  "isSimulation": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| asset | string | 否 | 资产类型，默认 USDT |
| amount | number | 是 | 充值金额 |
| isSimulation | boolean | 否 | 是否模拟，默认 true |

**响应**

```json
{
  "success": true,
  "message": "Deposited 1000.0 USDT"
}
```

---

### 2. 获取账户余额

**GET** `/accounts/{asset}`

获取指定资产的账户余额。

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| asset | string | 资产名称，如 USDT, BTC |

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| isSimulation | boolean | true | 是否模拟账户 |

**响应**

```json
{
  "id": 1,
  "asset": "USDT",
  "freeBalance": 5000.00,
  "lockedBalance": 0.00,
  "isSimulation": true,
  "updatedAtUtc": "2024-01-15T10:30:00"
}
```

---

## 交易执行

### 3. 执行模拟 Tick

**POST** `/tick`

执行模拟交易 Tick，处理充值分配和交易逻辑。

**请求体**

```json
["BTCUSDT", "ETHUSDT", "BNBUSDT"]
```

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| isSimulation | boolean | true | 是否模拟执行 |

**响应**

```json
{
  "success": true,
  "actions": [
    "DEPOSIT_ALLOC: 1000.0 allocated to BTCUSDT instance#0",
    "BUY_OPEN: BTCUSDT instance#0 qty=0.0321 at 31000.00"
  ]
}
```

**Action 类型**

| 类型 | 说明 |
|------|------|
| DEPOSIT_ALLOC | 充值分配到实例 |
| BUY_OPEN | 买入开仓 |
| TAKE_PROFIT | 止盈卖出 |
| STOP_LOSS | 止损卖出 |
| REBUY_COMPOUND | 补仓复合 |
| WAIT_REENTRY | 等待重新入场 |

---

### 4. 执行真实 Tick

**POST** `/real-tick`

使用真实 Binance 账户执行交易。

**请求体**

```json
["BTCUSDT", "ETHUSDT"]
```

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| quoteAmount | number | 否 | 指定交易金额 |

**响应**

```json
{
  "success": true,
  "actions": [
    "BUY_OPEN: BTCUSDT qty=0.0321 at 31000.00"
  ]
}
```

---

### 5. 手动开仓

**POST** `/real-trade/open`

手动对指定交易对开仓。

**请求体**

```json
{
  "symbol": "BTCUSDT",
  "quoteAmount": 1000.00
}
```

**响应**

```json
{
  "success": true,
  "message": "Position opened",
  "orderId": "12345678"
}
```

---

## 策略配置

### 6. 获取策略配置

**GET** `/config`

获取当前策略参数配置。

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| isSimulation | boolean | true | 是否模拟配置 |

**响应**

```json
[
  {
    "configKey": "TAKE_PROFIT_PCT",
    "configValue": "0.03",
    "isSimulation": true
  },
  {
    "configKey": "QUOTE_RESERVE",
    "configValue": "10",
    "isSimulation": true
  }
]
```

**关键配置项**

| 配置键 | 说明 | 模拟默认 | 真实默认 |
|--------|------|----------|----------|
| CURRENT_MODE | 当前模式 | true | false |
| TAKE_PROFIT_PCT | 止盈百分比 | 0.03 | 0.03 |
| STOP_LOSS_PCT | 止损百分比 | 0.10 | 0.10 |
| QUOTE_RESERVE | 预留金额 | 10 | 10 |
| MAX_ORDERS_PER_TICK | 每轮最大订单 | 5 | 5 |
| AUTO_TICK_ENABLED | 自动 Tick | true | - |

---

### 7. 更新策略配置

**PUT** `/config/{key}`

更新指定策略参数。

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| key | string | 配置键名 |

**请求体**

```json
{
  "configValue": "0.05",
  "isSimulation": true
}
```

**响应**

```json
{
  "success": true,
  "key": "TAKE_PROFIT_PCT",
  "value": "0.05"
}
```

---

### 8. 获取当前模式

**GET** `/mode`

获取当前交易模式（模拟/真实）。

**响应**

```json
{
  "isSimulation": true
}
```

---

### 9. 切换交易模式

**PUT** `/mode`

切换模拟/真实交易模式。

**请求体**

```json
{
  "isSimulation": false
}
```

**响应**

```json
{
  "success": true,
  "isSimulation": false
}
```

---

## 数据查询

### 10. 获取交易实例列表

**GET** `/instances`

获取所有交易周期实例。

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| symbol | string | - | 按交易对筛选 |
| isSimulation | boolean | true | 是否模拟 |

**响应**

```json
[
  {
    "id": 1,
    "symbol": "BTCUSDT",
    "instanceId": 0,
    "cycleId": 1,
    "isSimulation": true,
    "isOpen": true,
    "anchorPrice": "31000.00000000",
    "reentryPrice": "31000.00000000",
    "cycleStartPrice": "32000.00000000",
    "lastActionPrice": "32500.00000000",
    "baseQty": "0.03210000",
    "spentQuote": "1000.00000000",
    "quoteAmount": "1000.00000000",
    "createdAtUtc": "2024-01-15T10:00:00",
    "updatedAtUtc": "2024-01-15T12:30:00"
  }
]
```

---

### 11. 获取当前价格

**GET** `/prices`

获取所有交易对的当前价格。

**响应**

```json
{
  "BTCUSDT": 31000.00,
  "ETHUSDT": 1800.00,
  "BNBUSDT": 300.00
}
```

---

### 12. 获取单个交易对价格

**GET** `/prices/{symbol}`

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| symbol | string | 交易对，如 BTCUSDT |

**响应**

```json
31000.00
```

---

### 13. 获取 K 线数据

**GET** `/kline/{symbol}`

获取指定交易对的 K 线数据。

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| symbol | string | 交易对 |

**响应**

```json
{
  "success": true,
  "symbol": "BTCUSDT",
  "open": 30800.00,
  "high": 31200.00,
  "low": 30600.00,
  "close": 31000.00,
  "volume": 12345.67,
  "openTime": 1705312800000,
  "closeTime": 1705316400000
}
```

---

### 14. 订阅价格

**POST** `/prices/subscribe/{symbol}`

订阅指定交易对的实时价格推送。

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| symbol | string | 交易对 |

**响应**

```json
{
  "success": true,
  "symbol": "BTCUSDT"
}
```

---

## 历史数据

### 15. 获取事件历史

**GET** `/history/events`

获取交易事件历史记录。

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| symbol | string | - | 按交易对筛选 |
| isSimulation | boolean | true | 是否模拟 |
| limit | integer | 100 | 返回数量 |

**响应**

```json
[
  {
    "id": 1,
    "symbol": "BTCUSDT",
    "instanceId": 0,
    "cycleId": 1,
    "event": "BUY_OPEN",
    "price": "31000.00000000",
    "baseQty": "0.03210000",
    "quoteAmount": "1000.00000000",
    "note": "open_position fee=0.001",
    "createdAtUtc": "2024-01-15T10:00:00"
  }
]
```

---

### 16. 获取订单历史

**GET** `/history/orders`

获取订单/实例历史记录。

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| symbol | string | - | 按交易对筛选 |
| isSimulation | boolean | true | 是否模拟 |
| limit | integer | 100 | 返回数量 |

**响应**

```json
[
  {
    "id": 1,
    "symbol": "BTCUSDT",
    "instanceId": 0,
    "cycleId": 1,
    "isSimulation": true,
    "isOpen": false,
    "anchorPrice": "31000.00000000",
    "reentryPrice": "31000.00000000",
    "cycleStartPrice": "32000.00000000",
    "lastActionPrice": "33000.00000000",
    "baseQty": "0.00000000",
    "spentQuote": "0.00000000",
    "quoteAmount": "1050.00000000",
    "createdAtUtc": "2024-01-15T10:00:00",
    "updatedAtUtc": "2024-01-15T14:00:00"
  }
]
```

---

## 价格提醒

### 17. 获取价格提醒列表

**GET** `/alerts`

获取所有价格提醒。

**查询参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| symbol | string | 按交易对筛选 |
| interval | string | 按 K 线周期筛选 |

**响应**

```json
[
  {
    "id": 1,
    "symbol": "BTCUSDT",
    "interval": "1h",
    "alertType": "TD_BUY",
    "tdCount": 9,
    "currentPrice": "31000.00",
    "triggerPrice": "30500.00",
    "triggered": false,
    "message": "TD Buy 9 setup detected",
    "createdAtUtc": "2024-01-15T10:00:00"
  }
]
```

---

### 18. 获取已触发提醒

**GET** `/alerts/triggered`

获取所有已触发的价格提醒。

**响应**

```json
[
  {
    "id": 1,
    "symbol": "BTCUSDT",
    "interval": "1h",
    "alertType": "TD_BUY",
    "tdCount": 9,
    "currentPrice": "30500.00",
    "triggerPrice": "30500.00",
    "triggered": true,
    "message": "TD Buy 9 setup triggered at 30500",
    "createdAtUtc": "2024-01-15T10:00:00"
  }
]
```

---

### 19. 触发 TD 指标扫描

**POST** `/alerts/scan`

手动触发 TD 指标和 RSI 指标扫描。

**响应**

```json
{
  "success": true,
  "message": "TD & RSI scan triggered"
}
```

---

## 数据清理

### 20. 清空模拟数据

**POST** `/simulation/clear`

清空所有模拟交易数据，包括：
- 交易实例
- 事件记录
- 订单记录
- 账户余额
- 轮询状态
- 价格提醒

**响应**

```json
{
  "success": true,
  "message": "All simulation data cleared"
}
```

---

## 错误响应

所有 API 错误响应格式：

```json
{
  "success": false,
  "message": "Error description"
}
```

**常见错误**

| 状态码 | 说明 |
|--------|------|
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |