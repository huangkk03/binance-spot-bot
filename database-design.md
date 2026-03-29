# 数据库设计文档

## 概述

本系统使用 MySQL 5.6 数据库，共 10 张数据表，采用 JPA (Hibernate) 作为 ORM 框架。

## ER 图

```
┌──────────────────┐     ┌──────────────────┐
│  sim_accounts    │     │   api_accounts   │
│──────────────────│     │──────────────────│
│ id (PK)          │     │ id (PK)          │
│ asset            │     │ account_name     │
│ free_balance     │     │ api_key          │
│ locked_balance   │     │ api_secret       │
│ is_simulation    │     │ use_proxy       │
└────────┬─────────┘     │ proxy_url       │
         │               │ testnet         │
         │               │ is_active       │
         │               └────────┬─────────┘
         │                        │
         ▼                        ▼
┌──────────────────────────────────────────────┐
│              cycle_instances                  │
│──────────────────────────────────────────────│
│ id (PK)                                      │
│ symbol                                       │
│ instance_id                                  │
│ cycle_id                                     │
│ is_simulation (0=真实, 1=模拟)                │
│ is_open (仓位是否开启)                         │
│ anchor_price (锚定价格/首轮买入价)              │
│ reentry_price (重新入场价格)                   │
│ cycle_start_price (本轮起始价格)                │
│ last_action_price (最后操作价格)               │
│ base_qty (持仓数量)                           │
│ spent_quote (已花费的USDT(含手续费))            │
│ quote_amount (可用于交易的USDT)                │
│ api_account_id (FK)                          │
└────────┬─────────────────────────────────────┘
         │
         ├───────────────────┬─────────────────┐
         ▼                   ▼                 ▼
┌──────────────────┐ ┌────────────────┐ ┌─────────────────┐
│instance_events   │ │cycle_open_     │ │  trade_records  │
│──────────────────│ │records         │ │─────────────────│
│ id (PK)          │ │ id (PK)        │ │ id (PK)         │
│ symbol           │ │ symbol         │ │ order_id        │
│ instance_id      │ │ instance_id    │ │ symbol          │
│ cycle_id         │ │ cycle_id       │ │ side            │
│ is_simulation    │ │ is_simulation  │ │ status          │
│ event            │ │ start_price    │ │ is_simulation   │
│ price            │ │ quote_amount   │ │ executed_qty    │
│ base_qty         │ │ opened_at_utc  │ │ cummulative_... │
│ quote_amount     │ │ api_account_id │ │ avg_price       │
│ note             │ └────────────────┘ │ payload_json    │
│ created_at_utc   │                   └─────────────────┘
└──────────────────┘

┌──────────────────┐ ┌────────────────┐ ┌─────────────────┐
│ strategy_config  │ │  rr_state      │ │  expected_free  │
│──────────────────│ │────────────────│ │─────────────────│
│ id (PK)          │ │ id (PK)        │ │ id (PK)         │
│ config_key       │ │ quote_asset    │ │ asset           │
│ config_value     │ │ last_symbol    │ │ expected_free   │
│ is_simulation    │ │ is_simulation  │ │ is_simulation   │
└──────────────────┘ └────────────────┘ └─────────────────┘

┌──────────────────┐ ┌────────────────┐
│   price_alerts   │ │   api_config   │
│──────────────────│ │────────────────│
│ id (PK)          │ │ id (PK)        │
│ symbol           │ │ config_key     │
│ kline_interval   │ │ config_value    │
│ alert_type       │ └────────────────┘
│ td_count         │
│ current_price    │
│ trigger_price    │
│ triggered        │
│ message          │
└──────────────────┘
```

---

## 表结构详解

### 1. sim_accounts (模拟账户表)

存储模拟交易的账户余额。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| asset | VARCHAR(20) | 资产名称，如 USDT, BTC, ETH |
| free_balance | DECIMAL(32,16) | 可用余额 |
| locked_balance | DECIMAL(32,16) | 锁定余额（预留） |
| is_simulation | BOOLEAN | 1=模拟, 0=真实 |
| created_at_utc | DATETIME | 创建时间 |
| updated_at_utc | DATETIME | 更新时间 |

**唯一索引**：`UNIQUE(asset, isSimulation)`

**用途**：
- 模拟交易时记录各币种余额
- `is_simulation=1` 为模拟账户，`is_simulation=0` 记录真实账户余额快照

---

### 2. api_accounts (API 账户表)

存储 Binance API 连接配置。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| account_name | VARCHAR(50) | 账户名称/备注 |
| api_key | TEXT | Binance API Key |
| api_secret | TEXT | Binance API Secret |
| use_proxy | BOOLEAN | 是否使用代理 |
| proxy_url | VARCHAR(200) | 代理地址 |
| testnet | BOOLEAN | 是否使用测试网 |
| is_active | BOOLEAN | 是否激活（同一时间只能有一个激活账户） |
| created_at_utc | DATETIME | 创建时间 |
| updated_at_utc | DATETIME | 更新时间 |

**用途**：管理真实交易所需的 Binance API 凭证

---

### 3. cycle_instances (交易周期实例表)

核心表，记录每个交易对的开仓/平仓状态。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| symbol | VARCHAR(20) | 交易对，如 BTCUSDT, ETHUSDT |
| instance_id | INT | 同交易对的实例序号 |
| cycle_id | INT | 当前周期数（第几轮） |
| is_simulation | BOOLEAN | 1=模拟, 0=真实 |
| is_open | BOOLEAN | 仓位是否开启 |
| anchor_price | DECIMAL(32,16) | 锚定价格（首次买入价） |
| reentry_price | DECIMAL(32,16) | 重新入场价格（止盈后设置） |
| cycle_start_price | DECIMAL(32,16) | 本轮起始价格 |
| last_action_price | DECIMAL(32,16) | 最后操作价格 |
| base_qty | DECIMAL(32,16) | 持仓数量（买入的币数量） |
| spent_quote | DECIMAL(32,16) | 已花费的 USDT（含手续费） |
| quote_amount | DECIMAL(32,16) | 可用/待投资的 USDT |
| api_account_id | BIGINT (FK) | 关联的 API 账户 |
| created_at_utc | DATETIME | 创建时间 |
| updated_at_utc | DATETIME | 更新时间 |

**唯一索引**：`UNIQUE(symbol, instanceId, isSimulation)`

**cycle 生命周期**：

```
cycleId=0: DEPOSIT_ALLOC (充值分配，等待开仓)
    │
    ▼ (价格合适时)
cycleId=0: BUY_OPEN (开仓买入)
    │
    ▼ (价格上涨到止盈点)
cycleId=1: TAKE_PROFIT (止盈卖出)
    │
    ▼ (reentryPrice > 0 时需等待)
cycleId=1: WAIT_REENTRY (等待重新入场)
    │
    ▼ (价格回落到 reentryPrice)
cycleId=1: REBUY_COMPOUND (补仓复合)
```

---

### 4. instance_events (实例事件表)

记录所有交易操作的详细事件日志。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| symbol | VARCHAR(20) | 交易对 |
| instance_id | INT | 实例序号 |
| cycle_id | INT | 周期数 |
| is_simulation | BOOLEAN | 1=模拟, 0=真实 |
| event | VARCHAR(30) | 事件类型 |
| price | DECIMAL(32,16) | 事件发生时价格 |
| base_qty | DECIMAL(32,16) | 相关数量 |
| quote_amount | DECIMAL(32,16) | 相关金额 |
| note | VARCHAR(500) | 备注信息 |
| created_at_utc | DATETIME | 事件时间 |

**索引**：
- `idx_symbol_instance_created` on (symbol, instance_id, created_at_utc)
- `idx_event_type` on (event, is_simulation)

**event 类型**：

| 事件类型 | 说明 |
|----------|------|
| DEPOSIT_ALLOC | 充值分配到实例 |
| BUY_OPEN | 买入开仓 |
| TAKE_PROFIT | 止盈卖出 |
| REBUY_COMPOUND | 补仓复合 |
| WAIT_REENTRY | 等待重新入场 |

---

### 5. cycle_open_records (开仓记录表)

记录每次开仓的详细信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| symbol | VARCHAR(20) | 交易对 |
| instance_id | INT | 实例序号 |
| cycle_id | INT | 周期数 |
| is_simulation | BOOLEAN | 1=模拟, 0=真实 |
| start_price | DECIMAL(32,16) | 开仓价格 |
| quote_amount | DECIMAL(32,16) | 开仓使用的 USDT 金额 |
| opened_at_utc | DATETIME | 开仓时间 |
| api_account_id | BIGINT (FK) | 关联的 API 账户 |
| created_at_utc | DATETIME | 创建时间 |

**索引**：`idx_symbol_instance_sim` on (symbol, instance_id, is_simulation)

**用途**：追踪每次开仓的历史，用于分析平均成本等

---

### 6. trade_records (交易记录表)

记录 Binance 真实交易的订单信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| order_id | VARCHAR(50) | Binance 订单号 |
| symbol | VARCHAR(20) | 交易对 |
| side | VARCHAR(10) | BUY / SELL |
| status | VARCHAR(20) | 订单状态 |
| is_simulation | BOOLEAN | 1=模拟, 0=真实 |
| executed_qty | DECIMAL(32,16) | 已执行数量 |
| cummulative_quote_qty | DECIMAL(32,16) | 累计成交金额 |
| avg_price | DECIMAL(32,16) | 平均成交价 |
| payload_json | TEXT | 完整订单响应 JSON |
| created_at_utc | DATETIME | 创建时间 |

**索引**：
- `idx_symbol_sim_created` on (symbol, is_simulation, created_at_utc)
- `idx_order_id` on (order_id)

**用途**：保存所有真实交易的 Binance 订单响应，便于追溯和审计

---

### 7. strategy_config (策略配置表)

存储策略参数配置，支持模拟和真实交易分离配置。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| config_key | VARCHAR(50) | 配置键名 |
| config_value | VARCHAR(200) | 配置值 |
| is_simulation | BOOLEAN | 1=模拟, 0=真实 |
| created_at_utc | DATETIME | 创建时间 |
| updated_at_utc | DATETIME | 更新时间 |

**唯一索引**：`UNIQUE(config_key, isSimulation)`

**关键配置项**：

| config_key | 说明 | 模拟默认 | 真实默认 |
|------------|------|----------|----------|
| CURRENT_MODE | 当前模式 | - | "true"=模拟 |
| TAKE_PROFIT_PCT | 止盈百分比 | 0.03 | 0.03 |
| QUOTE_RESERVE | 预留金额 | 10 | 10 |
| MAX_ORDERS_PER_TICK | 每轮最大订单 | 5 | 5 |
| AUTO_TICK_ENABLED | 自动 Tick | true | - |

**重要**：`is_simulation` 字段完全隔离模拟和真实配置

---

### 8. rr_state (轮询状态表)

Round-Robin 状态，记录每个报价资产的轮询分配位置。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| quote_asset | VARCHAR(20) | 报价资产（如 USDT） |
| last_symbol | VARCHAR(20) | 上次分配的交易对 |
| is_simulation | BOOLEAN | 1=模拟, 0=真实 |
| created_at_utc | DATETIME | 创建时间 |
| updated_at_utc | DATETIME | 更新时间 |

**唯一索引**：`UNIQUE(quoteAsset, isSimulation)`

**用途**：确保充值平均分配到各交易对，避免单一交易对过度集中

---

### 9. expected_free (预期余额表)

记录预期的账户余额，用于检测充值。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| asset | VARCHAR(20) | 资产名称 |
| expected_free | DECIMAL(32,16) | 预期余额 |
| is_simulation | BOOLEAN | 1=模拟, 0=真实 |
| created_at_utc | DATETIME | 创建时间 |
| updated_at_utc | DATETIME | 更新时间 |

**唯一索引**：`UNIQUE(asset, isSimulation)`

**用途**：对比实际余额与预期余额，差额即为新充值

---

### 10. price_alerts (价格提醒表)

存储 TD 指标扫描触发的价格提醒。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| symbol | VARCHAR(20) | 交易对 |
| kline_interval | VARCHAR(10) | K线周期 (1m, 5m, 1h, etc.) |
| alert_type | VARCHAR(20) | 提醒类型 |
| td_count | INT | TD 计数 |
| current_price | DECIMAL(32,16) | 当前价格 |
| trigger_price | DECIMAL(32,16) | 触发价格 |
| triggered | BOOLEAN | 是否已触发 |
| message | VARCHAR(500) | 提醒消息 |
| created_at_utc | DATETIME | 创建时间 |

**索引**：
- `idx_symbol_interval_alert` on (symbol, kline_interval, alert_type)
- `idx_created_at` on (created_at_utc)

---

### 11. api_config (API 配置表)

存储 Binance API 全局配置。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| config_key | VARCHAR(50) | 配置键名 |
| config_value | TEXT | 配置值 |
| updated_at_utc | DATETIME | 更新时间 |

**唯一索引**：`UNIQUE(config_key)`

---

## 数据流向

```
┌─────────────────────────────────────────────────────────────────┐
│                         模拟交易流程                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. POST /api/v1/deposit                                        │
│     └── sim_accounts.free_balance += amount                    │
│                                                                  │
│  2. POST /api/v1/tick                                           │
│     ├── 检测充值: expected_free vs sim_accounts                │
│     │   └── 新充值 → cycle_instances (quote_amount = delta)    │
│     │                                                            │
│     ├── 未开仓实例 → tryOpenPosition                            │
│     │   ├── 计算 baseQty = quoteToSpend / price                │
│     │   ├── 计算 buyFee = baseQty * price * 0.001              │
│     │   ├── 扣除: sim_accounts (quote -= baseQty*price + fee)  │
│     │   ├── 增加: sim_accounts (asset += baseQty)              │
│     │   └── 更新: cycle_instances (is_open=true, base_qty=...) │
│     │                                                            │
│     └── 已开仓实例 → tryTakeProfitOrRebuy                       │
│         ├── 检查价格是否达到止盈点 (cycle_start_price * 1.03)   │
│         └── 达到 → executeTakeProfit                           │
│             ├── 计算 sellFee = baseQty * price * 0.001         │
│             ├── 扣除: sim_accounts (asset -= baseQty)           │
│             ├── 增加: sim_accounts (quote += baseQty*price-fee)│
│             └── 更新: cycle_instances (is_open=false, ...)     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         真实交易流程                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 配置 API 账户 (api_accounts 表)                             │
│                                                                  │
│  2. POST /api/v1/real-tick                                      │
│     ├── 获取 Binance 账户余额                                   │
│     ├── 检查待平仓实例                                           │
│     │   └── 价格达到止盈 → closePosition                       │
│     │       ├── Binance API: 市价卖出                           │
│     │       ├── trade_records: 保存订单记录                     │
│     │       └── cycle_instances: 更新平仓状态                   │
│     └── 处理新充值资金                                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 关键计算公式

### 买入成本
```
totalCost = quoteToSpend + (quoteToSpend * FEE_RATE)
           = quoteToSpend * (1 + 0.001)
```

### 止盈利润
```
grossSellQuote = baseQty * sellPrice
sellFee = grossSellQuote * TAKER_FEE_RATE
netSellQuote = grossSellQuote - sellFee
profit = netSellQuote - spentQuote  // spentQuote 已含买入手续费
```

### 可用余额
```
spendableQuote = max(freeBalance - reserve, 0)
```

---

## 索引使用建议

| 查询场景 | 索引 |
|----------|------|
| 按交易对查询实例 | (symbol, is_simulation) |
| 查询事件历史 | (symbol, instance_id, created_at_utc) |
| 查询订单历史 | (symbol, is_simulation, created_at_utc) |
| 按配置key查询 | (config_key, is_simulation) |
