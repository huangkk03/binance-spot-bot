# Binance Compound Trading System

## 概述

Binance Compound Trading System 是一个基于 Vue 3 + Spring Boot 3 的加密货币量化交易系统，支持模拟交易和真实交易两种模式。系统采用模块化设计，实现了均值回归复合策略（Mean Reversion Compound Strategy）。

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                       │
│                    http://localhost:3000                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot 3)                   │
│                      http://localhost:8080                   │
│  ┌──────────────┐  ┌─────────────────┐  ┌───────────────┐  │
│  │ Compound     │  │ RealTrading     │  │ TDScanner     │  │
│  │ Controller  │  │ Service         │  │ Service       │  │
│  └──────────────┘  └─────────────────┘  └───────────────┘  │
│  ┌──────────────┐  ┌─────────────────┐  ┌───────────────┐  │
│  │ Simulation   │  │ BinanceApi      │  │ PriceService  │  │
│  │ Engine       │  │ Service         │  │               │  │
│  └──────────────┘  └─────────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────┘
          │                                    │
          ▼                                    ▼
┌──────────────────┐              ┌──────────────────────────┐
│      MySQL       │              │       Binance API        │
│   (模拟/配置数据) │              │    (真实交易执行)         │
└──────────────────┘              └──────────────────────────┘
          │
          ▼
┌──────────────────┐
│      Redis       │
│    (价格缓存)     │
└──────────────────┘
```

## 核心功能

### 1. 双模式交易系统

| 模式 | is_simulation | 说明 |
|------|---------------|------|
| 模拟交易 | 1 | 使用虚拟账户，完全模拟交易流程 |
| 真实交易 | 0 | 连接Binance API，执行真实买卖 |

### 2. 复合策略 (Compound Strategy)

**核心逻辑**：
- 当价格下跌时买入建仓
- 当价格上涨到止盈点时卖出平仓
- 利润重新投入，下一轮使用更大的资金量

**策略参数**：
| 参数 | 说明 | 默认值 |
|------|------|--------|
| TAKE_PROFIT_PCT | 止盈百分比 (如 0.03 = 3%) | 0.03 |
| QUOTE_RESERVE | 预留金额（不用于交易的USDT） | 10 |
| MAX_ORDERS_PER_TICK | 每轮最大订单数 | 5 |

### 3. Tick 执行流程

```
executeTick(symbols, isSimulation)
├── 1. 获取所有交易对当前价格
├── 2. 检测充值 (detectDeposits)
│   └── 发现新充值 → 创建新的 CycleInstance
├── 3. 分配充值到交易对 (selectSymbolForDeposit)
│   └── 轮询分配，避免单一交易对过热
├── 4. 遍历处理每个交易对的实例
│   ├── 未开仓 → tryOpenPosition (买入开仓)
│   └── 已开仓 → tryTakeProfitOrRebuy (检查止盈/补仓)
└── 5. 更新预期余额 (updateExpectedFree)
```

### 4. 手续费计算

**费率**：
- 挂单费率 (Maker Fee): 0.1%
- 吃单费率 (Taker Fee): 0.1%

**买入**：
```
baseQty = quoteToSpend / price
cumQuote = baseQty * price
buyFee = cumQuote * FEE_RATE (0.001)
totalCost = cumQuote + buyFee  // 从USDT账户扣除
```

**卖出**：
```
cumQuote = baseQty * sellPrice
sellFee = cumQuote * TAKER_FEE_RATE (0.001)
netQuote = cumQuote - sellFee
profit = netQuote - spentQuote  // spentQuote已包含买入手续费
```

### 5. WebSocket 实时价格

通过 Binance WebSocket 获取实时价格数据，缓存在 Redis 中。

## API 接口

### 账户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/deposit | 充值模拟资金 |
| GET | /api/v1/accounts/{asset} | 获取账户余额 |

### 交易执行

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/tick | 执行模拟 Tick |
| POST | /api/v1/real-tick | 执行真实 Tick |

### 策略配置

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/config | 获取策略配置 |
| PUT | /api/v1/config/{key} | 更新策略配置 |
| GET | /api/v1/mode | 获取当前模式 |
| PUT | /api/v1/mode | 切换模拟/真实模式 |

### 数据查询

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/instances | 获取交易实例列表 |
| GET | /api/v1/history/events | 获取事件历史 |
| GET | /api/v1/history/orders | 获取订单历史 |
| GET | /api/v1/prices | 获取当前价格 |

### TD 指标扫描

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/alerts/scan | 触发 TD 指标扫描 |
| GET | /api/v1/alerts | 获取价格提醒列表 |

### 数据清理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/simulation/clear | 清空所有模拟数据 |

## 前端页面

### SimulationConsole.vue

主要控制台页面，包含：

**模拟模式面板**：
- 模拟充值
- 策略参数配置
- 手动执行 Tick
- 清空模拟数据

**真实交易面板**：
- API 账户管理
- 真实交易策略参数
- 执行真实 Tick

### 状态管理 (Pinia Store)

```javascript
// compound store 主要状态
{
  isSimulation: true,        // 当前模式
  isLoading: false,         // 加载状态
  currentMode: 'simulation', // 'simulation' | 'real'
  // ...
}
```

## 数据库模型

系统使用 10 张数据表，详情请参考 `database-design.md`。

## 部署架构

```
┌─────────────────────────────────────────┐
│            docker-compose.yml           │
├─────────┬─────────┬──────────┬───────────┤
│  mysql  │  redis  │ backend  │ frontend  │
│  :3306  │  :6379  │  :8080   │   :3000   │
└─────────┴─────────┴──────────┴───────────┘
```

### 服务说明

| 服务 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| mysql | mysql:5.6 | 3306 | 主数据库 |
| redis | redis:7-alpine | 6379 | 价格缓存 |
| backend | 自建 | 8080 | Spring Boot 后端 |
| frontend | 自建 | 3000 | Vue 3 前端 |

## 配置管理

### application.yml 主要配置

```yaml
simulation:
  default-take-profit-pct: 0.03
  default-quote-reserve: 10
  default-max-orders-per-tick: 5
  auto-tick-enabled: true
  auto-tick-interval-ms: 30000

trading:
  default-take-profit-pct: 0.03
  default-quote-reserve: 10
  default-max-orders-per-tick: 5
```

### 环境变量

| 变量 | 说明 |
|------|------|
| DB_HOST | MySQL 主机 |
| DB_USER | 数据库用户名 |
| DB_PASSWORD | 数据库密码 |
| REDIS_HOST | Redis 主机 |
| SPRING_PROFILES_ACTIVE | Spring 配置文件 |

## 故障排查

### 常见问题

1. **真实交易无法执行**
   - 检查 API 账户是否激活
   - 确认 API Key/Secret 正确
   - 验证余额充足

2. **模拟交易无响应**
   - 检查 MySQL 连接
   - 确认策略参数配置正确
   - 查看后端日志

3. **价格数据获取失败**
   - 检查 Redis 服务状态
   - 确认网络连接 Binance

### 日志查看

```bash
# 查看后端日志
docker logs binance-compound-backend -f

# 查看前端日志
docker logs binance-compound-frontend -f
```
