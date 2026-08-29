# 📋 BTC 现货复利交易程序 - 完整需求文档 v1.0

> 基于 Python + Django + Vue 3 的币安 BTC 现货复利交易系统

---

## 目录

1. [项目概述](#1-项目概述)
2. [核心交易功能](#2-核心交易功能)
3. [多交易对与实例管理](#3-多交易对与实例管理)
4. [交易策略参数](#4-交易策略参数)
5. [后台管理功能](#5-后台管理功能)
6. [技术指标扫描器](#6-技术指标扫描器)
7. [数据模型](#7-数据模型)
8. [API 端点清单](#8-api-端点清单)
9. [业务流程图](#9-业务流程图)
10. [前端页面结构](#10-前端页面结构)
11. [定时任务](#11-定时任务)
12. [部署架构](#12-部署架构)
13. [非功能性需求](#13-非功能性需求)
14. [验收标准](#14-验收标准)
15. [开发阶段](#15-开发阶段)
16. [项目文件结构](#16-项目文件结构)
17. [与 Java 版本对比](#17-与-java-版本对比)

---

## 1. 项目概述

### 1.1 项目名称

**Binance BTC 现货复利交易系统**（基于 Python + Django + Vue 3）

### 1.2 项目目标

实现一个自动化 BTC 现货复利交易系统，支持多账户、多交易对、实时监控和通知。

### 1.3 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Python 3.11 + Django 5.0 + DRF + Channels + Celery |
| 数据库 | MySQL 8.0 + Redis (broker/cache) |
| 前端 | Vue 3 + Vite + Pinia + Element Plus + Axios |
| 部署 | Docker Compose（单机）|

---

## 2. 核心交易功能

### 2.1 复利交易逻辑

**核心概念**：
- **首次开仓** = 手动触发，用户输入 quote 金额（如 100 USDT）
- **止盈** = 达到固定百分比（`TAKE_PROFIT_PCT`），自动卖出
- **复利再买入** = 价格回落至锚定价，用**止盈净收入**作为本金再次买入
- **止损** = 达到止损百分比，平仓但不创建新实例，`cumulative_profit` 减少
- **每个实例独立追踪**（#0, #1, #2...），不混淆

### 2.2 实例（Instance）

每个实例独立追踪自己的状态：

```
实例编号: BTCUSDT#N
锚定价: 首次买入价（用于复利入场判断）
当前周期: cycle_id
持仓状态: is_open
基础币数量: base_qty
花费 Quote: spent_quote
下次开仓 Quote: quote_amount (复利累计金额)
累计盈利: cumulative_profit
```

### 2.3 交易流程

```
[手动] 用户点击"首次开仓" + 输入 quote 金额
  → 创建新实例（如 BTCUSDT#2）
  → 下市价买单
  → 记录: anchor_price, cycle_start_price, base_qty, spent_quote
  → quote_amount = spent_quote (下次复利基础)

[自动] 每 30 秒 Tick
  ① 对每个 is_open=true 实例：
     - 计算止盈价 = cycle_start_price × (1 + TAKE_PROFIT_PCT)
     - 计算止损价 = cycle_start_price × (1 - STOP_LOSS_PCT)
     - 当前价 >= 止盈价 → 止盈平仓
       * 卖出 → net_quote = 卖出金额 × (1 - 手续费率)
       * profit = net_quote - spent_quote
       * quote_amount = net_quote (复利本金)
       * cumulative_profit += profit
       * reentry_price = anchor_price
     - 当前价 <= 止损价 → 止损平仓
       * 卖出 → net_quote = 卖出金额 × (1 - 手续费率)
       * profit = net_quote - spent_quote (负数)
       * cumulative_profit += profit (减少)
       * reentry_price = 0 (不复利)
  ② 对每个 is_open=false 实例：
     - reentry_price > 0 && 当前价 <= reentry_price → 复利再买入
       * quote = quote_amount
       * 买入 → 更新 cycle_id += 1, cycle_start_price, base_qty
       * quote_amount 不变
```

### 2.4 复利计算示例

```
T0: 手动开仓 BTCUSDT#0
    用户输入: 100 USDT
    当前价: 100 USDT
    数量 = 100 / 100 = 1.0 BTC
    实际花费 ≈ 99.9 USDT (含手续费)
    quote_amount = 99.9
    anchor_price = 100

T1: 价格上涨到 103 (+3%，TAKE_PROFIT_PCT=0.03)
    触发止盈:
    卖出 1.0 BTC @ 103 = 103 USDT
    手续费 (0.1%) = 0.103 USDT
    净收入 = 102.897 USDT
    profit = 102.897 - 99.9 = 2.997 USDT
    cumulative_profit = 2.997
    quote_amount = 102.897 (复利本金)
    reentry_price = 100 (等待回落)

T2: 价格回落到 100
    触发复利再买入:
    花费 = 102.897 USDT
    买入 = 102.897 / 100 ≈ 1.029 BTC
    cycle_id = 2
    cycle_start_price = 100
    base_qty ≈ 1.029
    spent_quote = 102.897
```

### 2.5 交易触发方式

| 操作 | 触发方式 |
|------|----------|
| 首次开仓 | **手动**（用户点按钮 + 输入金额）|
| 止盈/止损/复利再买入 | **自动**（每 30 秒 Tick）|

### 2.6 手续费计算

- Taker 手续费率：0.1%
- 利润公式：`profit = 净收入 - 净支出`
  - 净收入 = 卖出金额 × (1 - 0.001)
  - 净支出 = 买入金额 × (1 + 0.001)

---

## 3. 多交易对与实例管理

### 3.1 交易对支持

| 交易对 | 角色 | 默认启用 |
|--------|------|----------|
| BTCUSDT | 核心 | ✅ |
| ETHUSDT | 次要 | ❌ |
| BNBUSDT | 次要 | ❌ |
| ADAUSDT | 次要 | ❌ |
| DOGEUSDT | 次要 | ❌ |
| SOLUSDT | 次要 | ❌ |

- 用户可独立启用/禁用每个交易对
- 每个交易对独立配置策略参数
- 每个交易对独立追踪实例

### 3.2 实例数量管理

**配置层级**：
1. **全局默认**：`MAX_INSTANCES_PER_SYMBOL = 3`（可在 settings.py 修改）
2. **交易对独立配置**：DB `strategy_config` 表，可覆盖全局

**优先级**：交易对独立配置 > 全局默认

**行为**：
- 首次开仓前检查该交易对已有实例数
- 达到上限后，止盈后**等待**而不是自动创建
- 复利再买入不受上限限制（同一个实例继续循环）

### 3.3 首次开仓流程

```
[用户] 选中交易对 (如 BTCUSDT)
       输入 quote 金额 (如 100 USDT)
       点击 "首次开仓"
  ↓
[系统] 检查该交易对实例数 < MAX_INSTANCES
  ↓
[系统] 下市价买单
  ↓
[系统] 创建新实例
  - instance_id = (现有最大 + 1)
  - cycle_id = 1
  - is_open = true
  - anchor_price = 当前价
  - cycle_start_price = 当前价
  - base_qty = 买入数量
  - spent_quote = 实际花费（含手续费）
  - quote_amount = spent_quote
  - cumulative_profit = 0
```

---

## 4. 交易策略参数

### 4.1 参数清单

| 参数 | 默认值 | 范围 | 说明 |
|------|--------|------|------|
| `TAKE_PROFIT_PCT` | 0.03 (3%) | 0.001~0.50 | 固定止盈点（百分比）|
| `STOP_LOSS_PCT` | 0.10 (10%) | 0~0.50 | 止损点（0=关闭）|
| `QUOTE_RESERVE` | 10 (USDT) | 0~1000 | USDT 预留金额 |
| `MAX_ORDERS_PER_TICK` | 5 | 1~20 | 每轮最大订单数 |
| `AUTO_TICK_ENABLED` | true | bool | 自动 tick 开关 |
| `AUTO_TICK_INTERVAL_MS` | 30000 | 1000~300000 | tick 间隔（毫秒）|
| `MAX_INSTANCES_PER_SYMBOL` | 3 | 1~10 | 每交易对最大实例数 |

### 4.2 参数配置层级

```
┌─────────────────────────┐
│  全局默认 (settings.py) │  ← 最低优先级
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│  strategy_config 表     │  ← 中等优先级
│  (key + symbol 组合)     │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│  交易对独立配置          │  ← 最高优先级
│  (symbol-specific)      │
└─────────────────────────┘
```

**优先级规则**：
1. 查找 `strategy_config` 表中 `config_key = 'TAKE_PROFIT_PCT'` 且 `symbol = 'BTCUSDT'` 的记录
2. 如果找到 → 使用该值
3. 如果没找到 → 查找 `config_key = 'TAKE_PROFIT_PCT'` 且 `symbol` 为空的全局记录
4. 如果还是没找到 → 使用 settings.py 中的默认值

### 4.3 交易对参数覆盖示例

```python
# 全局默认
TAKE_PROFIT_PCT = 0.03

# BTCUSDT 特定配置（覆盖全局）
TAKE_PROFIT_PCT_BTCUSDT = 0.02  # BTC 用 2% 更保守

# ETHUSDT 用全局默认
```

**API 端点**：
- `GET /api/v1/strategy/config?symbol=BTCUSDT` - 获取该币种的生效配置
- `PUT /api/v1/strategy/config/TAKE_PROFIT_PCT` - 设置全局默认
- `PUT /api/v1/strategy/config/TAKE_PROFIT_PCT?symbol=BTCUSDT` - 设置交易对特定

### 4.4 RSI 报警参数

| 参数 | 默认值 | 范围 | 说明 |
|------|--------|------|------|
| `RSI_OVERBOUGHT` | 80 | 50~100 | 超买阈值 |
| `RSI_OVERSOLD` | 20 | 0~50 | 超卖阈值 |
| `RSI_PERIOD` | 14 | 5~30 | 计算周期 |

**存储**：同样支持全局/交易对独立配置。

---

## 5. 后台管理功能

### 5.1 多账户管理

**功能**：
- 添加/编辑/删除 API 账户
- 测试 API 连接
- 激活账户（仅 1 个激活账户）
- AES 加密存储 API Secret

**数据模型 `ApiAccount`**：
```python
{
    id: int,
    account_name: str(50),
    api_key: str(200),       # 明文（用于显示）
    api_secret: text,        # AES 加密
    use_proxy: bool,
    proxy_url: str(200),
    testnet: bool,
    is_active: bool,
    created_at: datetime,
    updated_at: datetime,
}
```

**API 端点**：
- `GET /api/v1/accounts/` - 列表
- `POST /api/v1/accounts/` - 创建
- `GET /api/v1/accounts/{id}` - 详情
- `PUT /api/v1/accounts/{id}` - 更新
- `DELETE /api/v1/accounts/{id}` - 删除
- `POST /api/v1/accounts/{id}/activate` - 激活
- `POST /api/v1/accounts/test` - 测试凭据（不存储）
- `GET /api/v1/accounts/balance` - 激活账户的实时余额
- `GET /api/v1/accounts/balance/{asset}` - 单币种
- `GET /api/v1/accounts/{id}/balances` - 指定账户余额

### 5.2 现货资产余额

**功能**：
- 显示当前激活账户的所有非零币种余额
- **每 30 秒自动刷新**
- 字段：资产名、可用、锁定、合计
- 调用 Binance API 实时获取（不存储本地）

**API 端点**：
- `GET /api/v1/accounts/balance` - 返回所有币种

**响应示例**：
```json
{
  "account_id": 1,
  "account_name": "主账户",
  "testnet": false,
  "balances": [
    {"asset": "BTC", "free": "0.5000", "locked": "0.0000", "total": "0.5000"},
    {"asset": "USDT", "free": "1000.50", "locked": "0.00", "total": "1000.50"}
  ]
}
```

### 5.3 AI 报告分析（仅 BTC）

**功能**：
- 配置 AI API（URL + Key + Model）
- **手动触发**：用户点击"生成 BTC 报告"
- **每日定时**：每天 8:00 自动生成
- 输出：PDF 报告

**配置项**：
- AI API URL
- AI API Key
- AI Model（默认 gpt-3.5-turbo）

**API 端点**：
- `GET /api/v1/reports/btc-prediction/pdf` - 下载 PDF
- `GET /api/v1/reports/btc-prediction/text` - 获取文本
- `POST /api/v1/notifications/test-ai` - 测试连接

### 5.4 通知配置（企业微信）

**功能**：
- 配置企业微信 Webhook URL
- 触发通知：
  - 止盈/止损成交
  - 复利再买入
  - RSI 报警
  - 资金费率报警
  - 异常错误
- 支持 Markdown 格式

**配置存储**：`api_config` 表，`config_key = 'WECHAT_WEBHOOK_URL'`

**API 端点**：
- `GET /api/v1/notifications/config/WECHAT_WEBHOOK_URL` - 读取
- `PUT /api/v1/notifications/config/WECHAT_WEBHOOK_URL` - 更新
- `POST /api/v1/notifications/test-notification` - 测试发送

### 5.5 多交易对支持

- 6 个交易对：BTC/ETH/BNB/ADA/DOGE/SOL
- 每个交易对可独立启用/禁用
- 每个交易对可独立配置策略参数

---

## 6. 技术指标扫描器

### 6.1 RSI 扫描器

- **执行周期**：60 秒
- **监控周期**：15m, 1h, 4h, 1d
- **冷却时间**：
  - 15m → 15 分钟
  - 1h → 60 分钟
  - 4h → 240 分钟
  - 1d → 1440 分钟
- **通知**：触发时发送企业微信
- **存储**：`price_alerts` 表

### 6.2 资金费率扫描器

- **执行周期**：5 分钟
- **API**：`GET https://fapi.binance.com/fapi/v1/premiumIndex`
- **监控币种**：BTCUSDT, ETHUSDT, SOLUSDT, BNBUSDT, DOGEUSDT
- **两级阈值**：

| 资产类别 | 币种 | 级别一（预警）| 级别二（绝对）|
|----------|------|--------------|--------------|
| 主流资产 | BTC, ETH | ≤ -0.05% | ≤ -0.10% |
| 山寨资产 | 其他 | ≤ -0.10% | ≤ -0.20% |

- **冷却时间**：2 小时
- **熔断覆盖**：级别二升级无视冷却
- **存储**：`funding_rate_alerts` 表

### 6.3 消息格式（资金费率）

```markdown
🟢 **【币安现货抄底提醒】—— 发现轧空信号！**
> **监控币种：** SOLUSDT
> **信号级别：** 💥 级别二：【绝对信号】（空头极度拥挤）
> **当前资金费率：** -0.2450%（年化约 -268.28%）
> **下次结算时间：** 还有 02小时15分钟
> **持仓量(OI)状态：** 已达监控阈值
```

---

## 7. 数据模型

### 7.1 `api_accounts`

```sql
id, account_name, api_key, api_secret (encrypted),
use_proxy, proxy_url, testnet, is_active,
created_at, updated_at
```

### 7.2 `cycle_instances`

```sql
id, symbol, instance_id, cycle_id, is_open,
anchor_price, reentry_price, cycle_start_price, last_action_price,
base_qty, spent_quote, quote_amount, cumulative_profit,
created_at, updated_at
UNIQUE (symbol, instance_id)
```

### 7.3 `trade_records`

```sql
id, order_id, symbol, side, status,
executed_qty, cummulative_quote_qty, avg_price,
payload_json, created_at
```

### 7.4 `cycle_open_records`

```sql
id, symbol, instance_id, cycle_id,
start_price, quote_amount, opened_at, created_at
```

### 7.5 `instance_events`

```sql
id, symbol, instance_id, cycle_id, event,
price, base_qty, quote_amount, note, created_at
```

### 7.6 `price_alerts`

```sql
id, symbol, kline_interval, alert_type, td_count,
current_price, trigger_price, triggered, message,
created_at, last_notified_at
UNIQUE (symbol, kline_interval, alert_type)
```

### 7.7 `funding_rate_alerts`

```sql
id, symbol, alert_type, funding_rate, annualized_rate,
next_funding_time, last_notified_at,
created_at, updated_at
UNIQUE (symbol, alert_type)
```

### 7.8 `strategy_config`

```sql
id, config_key, config_value, symbol (nullable),
created_at, updated_at
UNIQUE (config_key, symbol)
```

### 7.9 `api_config`

```sql
id, config_key, config_value, updated_at, created_at
UNIQUE (config_key)
```

**总计 9 张表**（比 Java 版减少 4 张：sim_accounts, expected_free, rr_state, 拆分 strategy_config）

---

## 8. API 端点清单

### 8.1 账户 (`/api/v1/accounts/`)

- `GET /` - 列表
- `POST /` - 创建
- `GET /{id}` - 详情
- `PUT /{id}` - 更新
- `DELETE /{id}` - 删除
- `POST /{id}/activate` - 激活
- `POST /test` - 测试凭据
- `GET /balance` - 激活账户实时余额
- `GET /balance/{asset}` - 单币种
- `GET /{id}/balances` - 指定账户余额

### 8.2 交易 (`/api/v1/trading/`)

- `POST /tick` - 手动 tick
- `POST /real-trade/open` - **手动首次开仓**（核心功能）
- `GET /instances` - 实例列表
- `GET /instances/{symbol}` - 按交易对
- `GET /history/events` - 事件历史
- `GET /history/orders` - 订单历史

### 8.3 行情 (`/api/v1/market/`)

- `GET /prices` - 所有价格
- `GET /prices/{symbol}` - 单个价格
- `POST /prices/subscribe/{symbol}` - 重新订阅
- `WS /ws/frontend` - 实时推送

### 8.4 策略 (`/api/v1/strategy/`)

- `GET /config` - 列出所有配置
- `GET /config/{key}` - 获取（带 symbol 查询参数支持覆盖）
- `PUT /config/{key}` - 更新（支持 symbol 覆盖）
- `DELETE /config/{key}` - 删除

### 8.5 扫描器 (`/api/v1/scanners/`)

- `GET /alerts` - 报警列表
- `GET /alerts/triggered` - 已触发
- `POST /alerts/scan` - 手动触发
- `GET /funding-rates` - 资金费率报警

### 8.6 通知 (`/api/v1/notifications/`)

- `GET /config` - 列出所有
- `GET /config/{key}` - 读取
- `PUT /config/{key}` - 更新
- `POST /test-notification` - 测试
- `POST /test-ai` - 测试 AI

### 8.7 报告 (`/api/v1/reports/`)

- `GET /btc-prediction/pdf` - BTC PDF
- `GET /btc-prediction/text` - BTC 文本

### 8.8 AI (`/api/v1/ai/`)

- `POST /chat` - AI 对话

---

## 9. 业务流程图

### 9.1 系统启动流程

```
┌────────────────────────────────────────────────────────────┐
│                       系统启动流程                           │
├────────────────────────────────────────────────────────────┤
│ 1. Django Daphne 启动 (端口 8080)                          │
│ 2. Channels WebSocket 启动                                  │
│ 3. Binance WebSocket 订阅 6 个币种 @trade                  │
│ 4. Celery Worker 启动 (2 个并发)                            │
│ 5. Celery Beat 启动 (调度任务)                              │
│ 6. 数据库迁移完成                                          │
└────────────────────────────────────────────────────────────┘
```

### 9.2 核心复利交易流程

```
┌────────────────────────────────────────────────────────────┐
│                   核心复利交易流程                           │
├────────────────────────────────────────────────────────────┤
│ [用户手动] 首次开仓                                         │
│   ↓                                                        │
│ [系统] 创建新实例 #N                                       │
│   ↓                                                        │
│ [自动 Tick 30s]                                            │
│   ↓                                                        │
│   ┌─ 当前价 >= 止盈价？                                   │
│   │   ├─ 是 → 卖出 → 记录 profit                          │
│   │   │     → quote_amount = 净收入 (复利)                │
│   │   │     → reentry_price = anchor_price                 │
│   │   │     → is_open = false                              │
│   │   └─ 否 ↓                                              │
│   ├─ 当前价 <= 止损价？                                   │
│   │   ├─ 是 → 卖出 → 记录负 profit                        │
│   │   │     → reentry_price = 0 (不复利)                   │
│   │   │     → is_open = false                              │
│   │   └─ 否 ↓                                              │
│   └─ 当前价 <= 复利入场价 && !is_open？                    │
│       ├─ 是 → 买入 → cycle_id += 1                         │
│       │     → is_open = true                               │
│       │     → cycle_start_price = 当前价                    │
│       └─ 否 → 等待下一 Tick                                │
└────────────────────────────────────────────────────────────┘
```

### 9.3 策略参数解析流程

```
[系统启动] / [Tick 调用] / [用户修改]
  ↓
[读取参数] getTakeProfitPct("BTCUSDT")
  ↓
  查 strategy_config 表:
    WHERE config_key = 'TAKE_PROFIT_PCT' AND symbol = 'BTCUSDT'
  ↓
  找到? → 返回
  未找到? → 查 symbol IS NULL
  ↓
  找到? → 返回
  未找到? → 返回 settings.py 默认值
```

---

## 10. 前端页面结构

### 10.1 路由

```
/                    → Dashboard
/dashboard          → Dashboard (行情 + 实例表格)
/trading            → 交易控制台（5 个标签）
/accounts           → 现货资产余额
/history            → 历史记录
/strategy           → 策略配置
/settings           → 系统设置
```

### 10.2 页面组件

| 页面 | 内容 |
|------|------|
| **Dashboard** | 6 币种实时行情 + 实例表格 + BTC AI 报告按钮 |
| **TradingConsole** | API 账户 / 策略配置 / Tick 控制 / AI 配置 / 通知配置 |
| **Accounts** | 现货资产余额（实时刷新）|
| **History** | 事件历史 / 订单历史 / 报警历史 |
| **Strategy** | 全局配置 + 交易对独立配置 |
| **Settings** | 系统配置展示 |

---

## 11. 定时任务

| 任务 | 周期 | 文件 |
|------|------|------|
| `execute_real_tick` | 30 秒 | `apps/trading/tasks.py` |
| `scan_rsi_indicators` | 60 秒 | `apps/scanners/tasks.py` |
| `scan_td_indicators` | 60 秒 | `apps/scanners/tasks.py` |
| `scan_funding_rates` | 5 分钟 | `apps/scanners/tasks.py` |
| `generate_daily_btc_report` | 每日 8:00 | `apps/reports/tasks.py` |

---

## 12. 部署架构

```
┌──────────────────────────────────────┐
│         Docker Compose               │
├──────────────────────────────────────┤
│  frontend (Vue + Nginx)     :3000   │
│  backend (Daphne ASGI)      :8080   │
│  celery-worker × 2 (并发)             │
│  celery-beat (调度)                  │
│  mysql (binance_compound_v2) :3306  │
│  redis (broker + cache)     :6379  │
└──────────────────────────────────────┘
```

**端口映射**：
- 前端 3000 → Nginx 80
- 后端 18080 → Daphne 8080
- MySQL 13306 → 3306
- Redis 16379 → 6379

---

## 13. 非功能性需求

### 13.1 性能

- WebSocket 实时推送延迟 < 1 秒
- Tick 处理每个币种 < 100ms
- API 响应 < 200ms

### 13.2 可靠性

- WebSocket 断线自动重连
- 数据库连接池
- 异常隔离（一个币种错误不影响其他）

### 13.3 安全性

- API Secret AES 加密存储
- 不暴露私钥到前端
- HTTPS 部署

### 13.4 可维护性

- 模块化设计（7+ 个 Django app）
- 完整日志
- 配置化参数

---

## 14. 验收标准

### 14.1 核心功能

- ✅ 手动开仓能创建新实例，instance_id 自增
- ✅ 止盈自动触发，复利金额正确
- ✅ 价格回落自动复利再买入
- ✅ 累计复利金额正确（加止盈、减止损）
- ✅ 止损时不创建新实例
- ✅ 手续费正确扣除

### 14.2 后台管理

- ✅ 多账户 CRUD 正常，AES 加密 secret
- ✅ 实时余额刷新（每 30 秒）
- ✅ 策略参数可编辑生效（支持交易对覆盖）
- ✅ 通知能正常发送（企业微信）
- ✅ AI 报告能生成 PDF

### 14.3 监控

- ✅ RSI 报警正常
- ✅ 资金费率报警正常
- ✅ 通知不重复（冷却机制）
- ✅ WebSocket 实时价格推送

---

## 15. 开发阶段

| 阶段 | 内容 | 估计代码量 |
|------|------|-----------|
| 1 | 基础设施（Django + Docker）| 300 行 |
| 2 | 数据模型 + 迁移 | 500 行 |
| 3 | 账户管理 | 400 行 |
| 4 | 行情 WebSocket | 200 行 |
| 5 | 交易核心引擎 | 1500 行 |
| 6 | 策略配置（多层覆盖）| 200 行 |
| 7 | 通知系统 | 200 行 |
| 8 | 扫描器（RSI/Funding）| 800 行 |
| 9 | AI 报告 | 400 行 |
| 10 | 前端重构 | 1500 行 |
| 11 | 端到端测试 | 200 行 |
| 12 | 部署文档 | 500 行 |
| **总计** | | **~6700 行** |

---

## 16. 项目文件结构

```
binance-spot-bot/
├── backend/                     # Django 后端
│   ├── binance_bot/
│   │   ├── settings.py
│   │   ├── urls.py
│   │   ├── asgi.py
│   │   ├── wsgi.py
│   │   └── celery.py
│   ├── apps/
│   │   ├── accounts/            # API 账户
│   │   ├── trading/             # 真实交易核心
│   │   ├── market/              # 行情 + WebSocket
│   │   ├── scanners/            # RSI/TD/Funding
│   │   ├── notifications/       # 通知
│   │   ├── reports/             # PDF 报告
│   │   ├── ai/                  # AI 预测
│   │   └── strategy/            # 策略配置（多层）
│   ├── tests/
│   ├── scripts/                 # 数据迁移
│   ├── manage.py
│   ├── requirements.txt
│   └── Dockerfile
├── frontend/                    # Vue 3 前端
│   ├── src/
│   │   ├── views/               # 5+ 个页面
│   │   ├── components/          # 7+ 个组件
│   │   ├── stores/
│   │   ├── api/
│   │   └── router/
│   ├── package.json
│   ├── vite.config.js
│   ├── Dockerfile
│   └── nginx.conf
├── docker-compose.yml
├── doc/
│   ├── API.md
│   ├── DEPLOYMENT.md
│   └── REQUIREMENTS.md          # 本文档
└── README.md
```

---

## 17. 与 Java 版本对比

| 维度 | Java Spring Boot | Django |
|------|------------------|--------|
| 后端代码 | ~4500 行 | ~4500 行 |
| 数据库表 | 12 张 | **9 张**（精简）|
| API 端点 | 36 个 | 30+ 个 |
| 模拟交易 | ✅ 支持 | ❌ **已删除**（按需求）|
| 充值功能 | ✅ 支持 | ❌ **已删除**（按需求）|
| 邮件通知 | ✅ 支持 | ❌ **已删除**（按需求）|
| 核心复利 | ✅ 支持 | ✅ **完整保留** |
| 多账户 | ✅ 支持 | ✅ **保留** |
| 资金费率扫描 | ✅ 支持 | ✅ **保留** |
| 策略参数分层 | ❌ 全局 | ✅ **全局+交易对覆盖** |
| 历史数据 | 90 天 | ✅ **永久** |

---

## 附录：术语表

| 术语 | 含义 |
|------|------|
| **复利** | 盈利再次投入作为本金，循环累加 |
| **锚定价** | 首次买入价，作为复利入场判断基准 |
| **止盈** | 价格上涨到目标百分比，卖出获利 |
| **止损** | 价格下跌到止损线，卖出避免更大损失 |
| **Tick** | 系统每 30 秒执行一次扫描 |
| **实例 (Instance)** | 每个 (交易对, 编号) 组合独立追踪 |
| **周期 (Cycle)** | 一次完整开仓-平仓过程 |
| **Reentry** | 价格回落至锚定价后再次买入 |
| **Quote** | 计价币种（USDT）|
| **Base** | 基础币种（BTC 等）|
| **Cooldown** | 通知冷却时间，避免重复推送 |
| **Taker Fee** | 主动吃单手续费（0.1%）|

---

**文档版本**：v1.0
**最后更新**：2026-06-14
**状态**：✅ 已定稿，待实施
