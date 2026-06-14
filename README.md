# Binance Spot Bot - Django 重构版

> 从 Java Spring Boot 重构到 Django + DRF + Channels + Celery 的币安现货复利交易平台

## 项目特点

- **真实交易** + 多 API 账户管理
- **复利循环**：开仓 → 止盈 → 复利再买入
- **技术指标扫描**：RSI、TD Sequential、资金费率
- **AI 预测报告**（PDF）
- **企业微信 / 钉钉通知**
- **WebSocket 实时行情推送**

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Django 5.0 + DRF + Channels + Celery |
| 数据库 | MySQL 8 + Redis (broker/cache) |
| WebSocket | Channels + channels-redis |
| 异步 | asyncio + httpx |
| 前端 | Vue 3 + Vite + Pinia + Element Plus |
| 部署 | Docker Compose |

## 服务架构

```
┌──────────────────────────────────────────────────────────┐
│                    Docker Compose                         │
├──────────────────────────────────────────────────────────┤
│  frontend (Vue 3 + Nginx)        :3000                     │
│  backend (Django + Daphne)        :8080                    │
│  celery-worker × 2 (并发执行)                              │
│  celery-beat (定时调度)                                   │
│  mysql (binance_compound_v2)     :3306                    │
│  redis (broker + cache)          :6379                    │
└──────────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 准备环境

- Docker 24.0+
- Docker Compose v2
- 网络可访问 Binance API（或配置代理）

### 2. 配置环境变量

复制并编辑 `.env` 文件：

```bash
cp .env.example .env
```

主要配置：
```bash
# 数据库（默认即可）
DB_HOST=mysql
DB_USER=root
DB_PASSWORD=rootpassword
DB_NAME=binance_compound_v2

# Binance 代理（国内必需）
BINANCE_PROXY=http://your-proxy:7890

# 企业微信 Webhook
WECHAT_WEBHOOK_URL=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
```

### 3. 启动服务

```bash
# 首次启动（自动构建镜像、初始化数据库）
docker-compose up -d --build

# 查看启动日志
docker-compose logs -f backend
```

### 4. 初始化数据库

```bash
# 进入 backend 容器
docker-compose exec backend bash

# 应用数据库迁移
python manage.py migrate

# 创建超级用户（可选，用于 admin 后台）
python manage.py createsuperuser

# 如需从旧库迁移数据
python manage.py migrate_legacy_data
```

### 5. 访问服务

- 前端：http://localhost:3000
- API：http://localhost:8080/api/v1/health/
- Django Admin：http://localhost:8080/admin/

## API 端点

完整 API 文档参考 [API.md](API.md)

主要端点：

| 模块 | 端点 | 说明 |
|------|------|------|
| 账户 | `GET/POST /api/v1/accounts/` | 列表/创建 |
| 账户 | `GET/PUT/DELETE /api/v1/accounts/{id}` | 详情/更新/删除 |
| 账户 | `POST /api/v1/accounts/{id}/activate` | 激活 |
| 账户 | `POST /api/v1/accounts/test` | 测试凭据 |
| 账户 | `GET /api/v1/accounts/balance` | 实时余额（调用 Binance API）|
| 交易 | `POST /api/v1/trading/tick` | 手动 tick |
| 交易 | `POST /api/v1/trading/real-trade/open` | 手动开仓 |
| 交易 | `GET /api/v1/trading/instances` | 实例列表 |
| 交易 | `GET /api/v1/trading/history/{events,orders}` | 历史 |
| 行情 | `GET /api/v1/market/prices` | 实时价格 |
| 行情 | `WS /ws/frontend` | 实时推送 |
| 扫描器 | `GET /api/v1/scanners/alerts` | 报警 |
| 扫描器 | `POST /api/v1/scanners/alerts/scan` | 手动触发 |
| 通知 | `GET/PUT /api/v1/notifications/config/{key}` | 配置 |
| 报告 | `GET /api/v1/reports/btc-prediction/pdf` | PDF 报告 |
| AI | `POST /api/v1/ai/chat` | AI 对话 |

## 定时任务

由 Celery Beat 调度：

| 任务 | 频率 | 文件 |
|------|------|------|
| `execute_real_tick` | 30 秒 | `apps/trading/tasks.py` |
| `scan_rsi_indicators` | 60 秒 | `apps/scanners/tasks.py` |
| `scan_td_indicators` | 60 秒 | `apps/scanners/tasks.py` |
| `scan_funding_rates` | 5 分钟 | `apps/scanners/tasks.py` |

## 数据迁移（从 Java 旧版）

如果你有旧 Java 版本的数据库（`binance_compound`），可以迁移数据：

1. 修改 `backend/.env.example`：
   ```bash
   LEGACY_DB_NAME=binance_compound
   ```

2. 启动新库（旧库需要可访问）

3. 执行迁移：
   ```bash
   docker-compose exec backend python manage.py migrate_legacy_data
   ```

迁移会跳过 `is_simulation` 列（模拟交易已废弃）。

## 配置化

| 配置 | 环境变量 | 默认值 |
|------|----------|--------|
| 数据库 | `DB_HOST`, `DB_PASSWORD` | mysql/rootpassword |
| Redis | `REDIS_HOST` | redis |
| Binance 代理 | `BINANCE_PROXY` | (空) |
| 资金费率监控 | `FUNDING_RATE_*` | 见 settings.py |
| 止盈/止损 | `settings.TRADING` | 3% / 10% |

修改 `settings.py` 中的：
- `TRADING.TAKE_PROFIT_PCT` - 止盈百分比
- `TRADING.STOP_LOSS_PCT` - 止损百分比
- `TRADING.QUOTE_RESERVE` - USDT 保留金额
- `TRADING.MAX_ORDERS_PER_TICK` - 每 tick 最大订单数
- `FUNDING_RATE.SYMBOLS` - 资金费率监控币种
- `FUNDING_RATE.MAIN_SYMBOLS` - 主流币列表

## 监控与日志

```bash
# 查看后端日志
docker-compose logs -f backend

# 查看 Celery Worker 日志
docker-compose logs -f celery-worker

# 查看 Celery Beat 日志
docker-compose logs -f celery-beat

# 进入容器调试
docker-compose exec backend bash
```

## 备份

旧 Java 代码备份在 `.archive/backup-2026.tar.gz`（如需回滚）。

定期备份 MySQL：
```bash
docker-compose exec mysql mysqldump -uroot -prootpassword binance_compound_v2 > backup.sql
```

## 故障排除

### 1. WebSocket 连接错误
检查 `BINANCE_PROXY` 是否正确配置。容器内 `curl https://api.binance.com/api/v3/ping` 测试。

### 2. Celery 任务不执行
- 查看 `celery-beat` 日志
- 确认 Redis 连接正常

### 3. 交易失败
- 检查 API 账户权限（需要现货交易权限）
- 检查 USDT 余额是否足够
- 查看 `instance_events` 表中的事件日志

### 4. 数据库迁移失败
- 删除容器数据卷重新初始化：`docker-compose down -v && docker-compose up -d`

## 注意事项

⚠️ **真实交易风险**：
- API 账户的权限请设置为**现货交易**且**只读提现关闭**
- 建议先用 testnet 测试
- 配置合理的止盈/止损
- 监控日志和通知

## 目录结构

```
.
├── backend/                     # Django 后端
│   ├── binance_bot/             # 项目配置
│   │   ├── settings.py
│   │   ├── urls.py
│   │   ├── asgi.py
│   │   ├── wsgi.py
│   │   └── celery.py
│   ├── apps/                    # 业务模块
│   │   ├── accounts/            # API 账户
│   │   ├── trading/             # 真实交易
│   │   ├── market/              # 行情 + WebSocket
│   │   ├── scanners/            # RSI/TD/Funding
│   │   ├── notifications/       # 通知
│   │   ├── reports/             # PDF 报告
│   │   └── ai/                  # AI 预测
│   ├── tests/                   # 测试
│   ├── scripts/                 # 数据迁移脚本
│   ├── manage.py
│   ├── requirements.txt
│   └── Dockerfile
├── frontend/                    # Vue 3 前端
│   ├── src/
│   │   ├── views/
│   │   ├── components/
│   │   ├── stores/
│   │   ├── api/
│   │   └── router/
│   ├── package.json
│   ├── vite.config.js
│   ├── Dockerfile
│   └── nginx.conf
├── docker-compose.yml           # 编排
├── .env.example                 # 环境变量样例
├── doc/                         # 文档
└── .archive/                    # 旧 Java 代码备份
```

## 变更历史

| 版本 | 日期 | 内容 |
|------|------|------|
| 2.0 | 2026-06 | 从 Java Spring Boot 重构到 Django |
| 1.x | 2025-2026 | 原始 Java 版本（已备份到 .archive/）|

## License

MIT
