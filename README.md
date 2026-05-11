# Binance Compound Trading System

加密货币量化交易系统，支持模拟交易和真实交易两种模式。

## 特性

- **双模式交易**: 模拟交易（使用虚拟账户）+ 真实交易（Binance API）
- **均值回归复合策略**: 下跌买入，上涨止盈，利润复合
- **自动化执行**: 支持自动 Tick 轮询
- **实时监控**: WebSocket 实时价格推送
- **止损保护**: 可配置止损比例
- **TD 指标扫描**: 技术分析信号提醒
- **PDF 报告**: 生成交易报告

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端 | Vue 3 + Vite | 3.4+ / 5.2+ |
| 后端 | Spring Boot 3 | 3.2.4 |
| 数据库 | MySQL 5.6 | 5.6 |
| 缓存 | Redis | 7-alpine |
| 构建 | Maven | 3.x |

## 快速开始

### 环境要求

- Docker & Docker Compose
- Git

### 1. 克隆项目

```bash
git clone <repository-url>
cd binance-spot-bot
```

### 2. 配置环境变量（可选）

创建 `.env` 文件：

```env
DB_HOST=mysql
DB_USER=root
DB_PASSWORD=rootpassword
REDIS_HOST=redis
BINANCE_API_KEY=your_api_key
BINANCE_API_SECRET=your_api_secret
BINANCE_PROXY=http://192.168.4.51:7897
```

### 3. 启动服务

```bash
docker-compose up -d
```

服务启动后访问：
- 前端: http://localhost:3000
- 后端 API: http://localhost:8080

### 4. 验证服务

```bash
# 检查容器状态
docker-compose ps

# 查看后端日志
docker logs binance-compound-backend -f

# 查看前端日志
docker logs binance-compound-frontend -f
```

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
│  │ Controller   │  │ Service         │  │ Service       │  │
│  └──────────────┘  └─────────────────┘  └───────────────┘  │
│  ┌──────────────┐  ┌─────────────────┐  ┌───────────────┐  │
│  │ Simulation   │  │ BinanceApi      │  │ PriceService  │  │
│  │ Engine       │  │ Service         │  │               │  │
│  └──────────────┘  └─────────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────┘
           │                                    │
           ▼                                    ▼
┌──────────────────┐              ┌──────────────────────────┐
│      MySQL        │              │       Binance API        │
│   (模拟/配置数据)  │              │    (真实交易执行)         │
└──────────────────┘              └──────────────────────────┘
           │
           ▼
┌──────────────────┐
│      Redis        │
│    (价格缓存)      │
└──────────────────┘
```

## 目录结构

```
binance-spot-bot/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/binance/compound/
│   │   │   │   ├── controller/    # REST 控制器
│   │   │   │   ├── service/       # 业务逻辑
│   │   │   │   ├── repository/    # 数据访问层
│   │   │   │   ├── entity/        # JPA 实体
│   │   │   │   ├── dto/           # 数据传输对象
│   │   │   │   ├── config/        # 配置类
│   │   │   │   └── websocket/     # WebSocket 处理
│   │   │   └── resources/
│   │   │       └── application.yml # 应用配置
│   │   └── test/                  # 测试
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── views/                 # 页面组件
│   │   │   ├── Dashboard.vue
│   │   │   ├── SimulationConsole.vue
│   │   │   └── History.vue
│   │   ├── components/           # 通用组件
│   │   ├── stores/               # Pinia 状态管理
│   │   └── api/                  # API 调用
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── docker-compose.yml
├── skill.md                      # 系统需求规格
├── database-design.md            # 数据库设计文档
├── API.md                        # API 接口文档
└── DEPLOYMENT.md                 # 部署指南
```

## 核心概念

### 交易周期 (Cycle Instance)

每个交易对可以有多个独立的交易实例，每个实例经历以下状态：

```
DEPOSIT_ALLOC → BUY_OPEN → TAKE_PROFIT/STOP_LOSS
                    ↓
              WAIT_REENTRY → REBUY_COMPOUND
```

| 状态 | 说明 |
|------|------|
| DEPOSIT_ALLOC | 充值分配到实例，等待开仓 |
| BUY_OPEN | 买入开仓 |
| TAKE_PROFIT | 止盈卖出 |
| STOP_LOSS | 止损卖出 |
| WAIT_REENTRY | 等待重新入场价格 |
| REBUY_COMPOUND | 补仓复合 |

### 策略参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| TAKE_PROFIT_PCT | 止盈百分比 (如 0.03 = 3%) | 0.03 |
| STOP_LOSS_PCT | 止损百分比 (如 0.10 = 10%) | 0.10 |
| QUOTE_RESERVE | 预留金额（不用于交易的USDT） | 10 |
| MAX_ORDERS_PER_TICK | 每轮最大订单数 | 5 |

### 手续费

- 挂单费率 (Maker): 0.1%
- 吃单费率 (Taker): 0.1%

## API 接口

详见 [API.md](API.md)

### 常用端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/deposit | 充值模拟资金 |
| POST | /api/v1/tick | 执行模拟 Tick |
| POST | /api/v1/real-tick | 执行真实 Tick |
| GET | /api/v1/instances | 获取交易实例列表 |
| GET | /api/v1/config | 获取策略配置 |
| PUT | /api/v1/config/{key} | 更新策略配置 |

## 配置说明

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| DB_HOST | MySQL 主机 | mysql |
| DB_USER | 数据库用户名 | root |
| DB_PASSWORD | 数据库密码 | rootpassword |
| REDIS_HOST | Redis 主机 | redis |
| BINANCE_API_KEY | Binance API Key | - |
| BINANCE_API_SECRET | Binance API Secret | - |
| BINANCE_PROXY | 代理地址 | - |
| TRADING_AUTO_TICK_ENABLED | 自动 Tick 开关 | true |

详见 [DEPLOYMENT.md](DEPLOYMENT.md)

## 开发

### 后端运行

```bash
cd backend
./mvnw spring-boot:run
```

### 前端运行

```bash
cd frontend
npm install
npm run dev
```

### 运行测试

```bash
cd backend
./mvnw test
```

## 相关文档

- [skill.md](skill.md) - 系统需求规格
- [database-design.md](database-design.md) - 数据库设计
- [API.md](API.md) - API 接口文档
- [DEPLOYMENT.md](DEPLOYMENT.md) - 部署指南

## License

MIT