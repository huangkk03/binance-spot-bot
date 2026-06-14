# API 文档

> Django 重构版 - REST API 完整端点参考

## 基础信息

- **Base URL**: `http://localhost:8080/api/v1`
- **WebSocket**: `ws://localhost:8080/ws/frontend`
- **Content-Type**: `application/json`
- **认证**: 当前版本无认证（生产环境建议加 Basic Auth 或 Token）

## 通用响应格式

成功：
```json
{
  "success": true,
  "data": ...
}
```

错误：
```json
{
  "error": "错误描述"
}
```

---

## 1. 健康检查

### `GET /api/v1/health/`

```json
{
  "status": "ok",
  "service": "binance-spot-bot-django"
}
```

---

## 2. 账户管理

### `GET /api/v1/accounts/`
列出所有 API 账户。

**响应**:
```json
[
  {
    "id": 1,
    "account_name": "主账户",
    "api_key": "xxxxx",
    "api_secret_masked": "xxxx****xxxx",
    "use_proxy": false,
    "proxy_url": "",
    "testnet": true,
    "is_active": true,
    "created_at": "2026-06-14T...",
    "updated_at": "2026-06-14T..."
  }
]
```

### `POST /api/v1/accounts/`
创建新账户。

**Body**:
```json
{
  "account_name": "主账户",
  "api_key": "your-binance-api-key",
  "api_secret": "your-binance-api-secret",
  "testnet": false,
  "use_proxy": false,
  "proxy_url": "",
  "is_active": true
}
```

### `GET /api/v1/accounts/{id}`
获取账户详情。

### `PUT /api/v1/accounts/{id}`
更新账户（api_secret 不传则不修改）。

### `DELETE /api/v1/accounts/{id}`
删除账户。

### `POST /api/v1/accounts/{id}/activate`
激活账户（其他激活账户自动取消）。

### `POST /api/v1/accounts/test`
测试 API 凭据（不存储）。

**Body**:
```json
{
  "api_key": "xxx",
  "api_secret": "xxx",
  "testnet": false,
  "use_proxy": false,
  "proxy_url": ""
}
```

**响应**:
```json
{
  "success": true,
  "message": "连接成功",
  "can_trade": true,
  "account_type": "SPOT"
}
```

### `GET /api/v1/accounts/balance`
查询当前激活账户的所有非零余额（实时调用 Binance API）。

**响应**:
```json
{
  "account_id": 1,
  "account_name": "主账户",
  "testnet": false,
  "balances": [
    {
      "asset": "BTC",
      "free": "0.50000000",
      "locked": "0.00000000",
      "total": "0.50000000"
    },
    {
      "asset": "USDT",
      "free": "1000.50000000",
      "locked": "0.00000000",
      "total": "1000.50000000"
    }
  ]
}
```

### `GET /api/v1/accounts/balance/{asset}`
查询单个币种余额。

**响应**:
```json
{
  "asset": "BTC",
  "free": "0.50000000",
  "locked": "0.00000000",
  "total": "0.50000000"
}
```

### `GET /api/v1/accounts/{id}/balances`
查询指定账户的所有余额。

---

## 3. 交易

### `POST /api/v1/trading/tick`
执行一轮 tick（手动触发）。

**Body**:
```json
["BTCUSDT", "ETHUSDT", "BNBUSDT"]
```

**响应**:
```json
{
  "success": true,
  "actions": [
    "BUY_OPEN: BTCUSDT#1 cycle=1 qty=0.001 at 50000.00",
    "TAKE_PROFIT: ETHUSDT#1 cycle=2 qty=0.5 at 3500.00 profit=10.5"
  ]
}
```

### `POST /api/v1/trading/real-trade/open`
手动开仓。

**Body**:
```json
{
  "symbol": "BTCUSDT",
  "quote_amount": "50"
}
```

**响应**:
```json
{
  "success": true,
  "message": "BUY_OPEN: BTCUSDT#1 ..."
}
```

### `GET /api/v1/trading/instances?symbol=BTCUSDT`
列出实例。

**响应**:
```json
[
  {
    "id": 1,
    "symbol": "BTCUSDT",
    "instance_id": 1,
    "cycle_id": 2,
    "is_open": true,
    "anchor_price": "50000.0000000000000000",
    "reentry_price": "0.0000000000000000",
    "cycle_start_price": "50000.0000000000000000",
    "last_action_price": "50000.0000000000000000",
    "base_qty": "0.0010000000000000",
    "spent_quote": "50.0000000000000000",
    "quote_amount": "50.0000000000000000",
    "cumulative_profit": "0.0000000000000000",
    "created_at": "...",
    "updated_at": "..."
  }
]
```

### `GET /api/v1/trading/history/events?symbol=BTCUSDT&limit=100`
事件历史。

### `GET /api/v1/trading/history/orders?symbol=BTCUSDT&limit=100`
订单历史。

---

## 4. 行情

### `GET /api/v1/market/prices`
所有币种实时价格。

**响应**:
```json
{
  "BTCUSDT": "50000.50",
  "ETHUSDT": "3500.25",
  ...
}
```

### `GET /api/v1/market/prices/{symbol}`
单个币种价格。

### `POST /api/v1/market/prices/subscribe/{symbol}`
重新订阅（重启 WebSocket）。

---

## 5. WebSocket

### `WS /ws/frontend`

**客户端 → 服务端**:
```json
{"type": "PING"}
```

**服务端 → 客户端**:
```json
{"type": "PONG"}

{
  "type": "PRICE_UPDATE",
  "data": {
    "symbol": "BTCUSDT",
    "price": "50000.50"
  }
}
```

---

## 6. 扫描器

### `GET /api/v1/scanners/alerts?symbol=BTCUSDT&interval=1h`
获取报警。

### `GET /api/v1/scanners/alerts/triggered`
获取已触发的报警。

### `POST /api/v1/scanners/alerts/scan`
手动触发所有扫描器（RSI/TD/Funding）。

### `GET /api/v1/scanners/funding-rates`
资金费率报警。

---

## 7. 通知

### `GET /api/v1/notifications/config`
列出所有配置。

### `GET /api/v1/notifications/config/{key}`
获取单个配置。

**已知 key**:
- `WECHAT_WEBHOOK_URL`
- `AI_API_URL`
- `AI_API_KEY`
- `AI_API_MODEL`
- `EMAIL_TO`
- `EMAIL_SMTP_HOST`
- `EMAIL_SMTP_PORT`
- `EMAIL_SMTP_USERNAME`
- `EMAIL_SMTP_PASSWORD`

### `PUT /api/v1/notifications/config/{key}`
更新配置。

**Body**:
```json
{
  "value": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
}
```

### `POST /api/v1/notifications/test-notification`
发送测试通知。

**Body**:
```json
{
  "title": "测试",
  "content": "测试消息"
}
```

### `POST /api/v1/notifications/test-ai`
测试 AI 服务连接。

**Body**:
```json
{
  "url": "https://api.openai.com/v1",
  "key": "sk-xxx",
  "model": "gpt-3.5-turbo"
}
```

---

## 8. 报告

### `GET /api/v1/reports/btc-prediction/pdf`
下载 BTC AI 预测报告 (PDF)。

**Response**: `application/pdf` 二进制

### `GET /api/v1/reports/btc-prediction/text`
获取 BTC AI 预测纯文本。

**响应**:
```json
{
  "content": "=== BTC AI 预测报告 ===\n..."
}
```

---

## 9. AI

### `POST /api/v1/ai/chat`
调用 AI 对话。

**Body**:
```json
{
  "system": "你是一个助手",
  "user": "你好"
}
```

**响应**:
```json
{
  "success": true,
  "content": "你好！有什么可以帮助你的？"
}
```

---

## 错误码

| 状态码 | 含义 |
|--------|------|
| 200 | 成功 |
| 201 | 已创建 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
