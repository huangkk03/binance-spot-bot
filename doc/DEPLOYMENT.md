# 部署运行手册

> Django 重构版 Binance Spot Bot 完整部署流程

## 前置条件

| 工具 | 版本 | 用途 |
|------|------|------|
| Docker | 24.0+ | 容器运行时 |
| Docker Compose | v2 | 容器编排 |
| Git | 2.30+ | 拉取代码 |
| curl | - | 测试 API |

## 一、克隆代码

```bash
git clone https://github.com/huangkk03/binance-spot-bot.git
cd binance-spot-bot
git checkout refactor/django-migration
```

## 二、配置环境变量

### 1. 创建 `.env` 文件

```bash
cp .env.example .env
```

### 2. 编辑 `.env`

```bash
# 必填：数据库密码
DB_PASSWORD=YourStrongPassword123!

# 必填（国内部署）：Binance 代理
BINANCE_PROXY=http://your-proxy-host:7890

# 必填：企业微信 Webhook
WECHAT_WEBHOOK_URL=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=your-key

# 可选：AI 服务（用于 BTC 预测报告）
AI_API_URL=https://api.openai.com/v1
AI_API_KEY=sk-xxx
AI_API_MODEL=gpt-3.5-turbo
```

### 3. 修改 `backend/.env.example`

如使用 `binance_compound_v2`（新库名），可保持默认。

如需从旧库迁移，设置 `LEGACY_DB_NAME=binance_compound`。

## 三、启动服务

### 1. 首次启动

```bash
# 构建并启动
docker-compose up -d --build

# 查看启动状态
docker-compose ps
```

预期输出：
```
NAME                            STATUS    PORTS
binance-compound-mysql          Up        0.0.0.0:13306->3306/tcp
binance-compound-redis          Up        0.0.0.0:16379->6379/tcp
binance-compound-backend        Up        0.0.0.0:18080->8080/tcp
binance-compound-celery-worker  Up
binance-compound-celery-beat     Up
binance-compound-frontend       Up        0.0.0.0:3000->80/tcp
```

### 2. 初始化数据库

```bash
# 应用 Django 迁移
docker-compose exec backend python manage.py migrate

# 预期输出：
# Running migrations:
#   Applying accounts.0001_initial... OK
#   Applying trading.0001_initial... OK
#   ...
```

### 3. 创建管理员账户（可选）

```bash
docker-compose exec backend python manage.py createsuperuser
```

### 4. 验证启动

```bash
# 检查健康
curl http://localhost:18080/api/v1/health/
# 预期: {"status":"ok","service":"binance-spot-bot-django"}

# 检查前端
curl -I http://localhost:3000/
# 预期: HTTP/1.1 200 OK
```

## 四、配置 API 账户

### 1. 通过前端

1. 访问 http://localhost:3000/
2. 导航到 "交易控制台" → "API 账户" 标签
3. 点击 "添加 API 账户"
4. 填写：
   - 账户名: 主账户
   - API Key: 你的 Binance API Key
   - API Secret: 你的 Binance API Secret
   - 网络: Testnet（建议先测试）/ Mainnet
   - 激活: ✓
5. 点击 "测试连接" 验证
6. 点击 "保存"

### 2. 通过 API

```bash
# 创建账户
curl -X POST http://localhost:18080/api/v1/accounts/ \
  -H "Content-Type: application/json" \
  -d '{
    "account_name": "主账户",
    "api_key": "your-api-key",
    "api_secret": "your-api-secret",
    "testnet": false,
    "is_active": true
  }'

# 查询余额
curl http://localhost:18080/api/v1/accounts/balance
```

## 五、配置通知

### 1. 企业微信 Webhook

1. 在企业微信群中添加机器人，复制 Webhook URL
2. 通过前端："交易控制台" → "通知配置" 标签
3. 粘贴 URL，点击保存
4. 点击 "发送测试通知" 验证

### 2. 通过 API

```bash
curl -X PUT http://localhost:18080/api/v1/notifications/config/WECHAT_WEBHOOK_URL \
  -H "Content-Type: application/json" \
  -d '{
    "value": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
  }'
```

## 六、配置 AI 服务（可选）

通过前端 "AI 配置" 标签或 API：

```bash
curl -X PUT http://localhost:18080/api/v1/notifications/config/AI_API_URL \
  -H "Content-Type: application/json" -d '{"value": "https://api.openai.com/v1"}'

curl -X PUT http://localhost:18080/api/v1/notifications/config/AI_API_KEY \
  -H "Content-Type: application/json" -d '{"value": "sk-xxx"}'

curl -X PUT http://localhost:18080/api/v1/notifications/config/AI_API_MODEL \
  -H "Content-Type: application/json" -d '{"value": "gpt-3.5-turbo"}'
```

## 七、启动交易

### 1. 调整策略参数

编辑 `backend/binance_bot/settings.py`：

```python
TRADING = {
    'TAKE_PROFIT_PCT': 0.03,        # 止盈 3%
    'STOP_LOSS_PCT': 0.10,          # 止损 10%
    'MAX_ORDERS_PER_TICK': 5,       # 每 tick 最大订单数
    'QUOTE_RESERVE': 10,            # USDT 保留金额
    'AUTO_TICK_ENABLED': True,      # 启用自动 tick
    'AUTO_TICK_INTERVAL_MS': 30000, # tick 间隔 30 秒
}
```

### 2. 重启服务应用配置

```bash
docker-compose restart backend celery-worker celery-beat
```

### 3. 监控日志

```bash
# 实时查看后端日志
docker-compose logs -f backend

# 实时查看 Celery Worker
docker-compose logs -f celery-worker

# 实时查看 Celery Beat（调度）
docker-compose logs -f celery-beat
```

## 八、停止/重启服务

```bash
# 停止所有服务
docker-compose stop

# 启动所有服务
docker-compose start

# 重启特定服务
docker-compose restart backend

# 完全停止并删除容器
docker-compose down

# 完全停止并删除容器+数据卷（慎用！）
docker-compose down -v
```

## 九、数据备份

### 1. 备份 MySQL

```bash
# 备份到文件
docker-compose exec mysql mysqldump -uroot -p"rootpassword" binance_compound_v2 > backup_$(date +%Y%m%d).sql

# 恢复
docker-compose exec -T mysql mysql -uroot -p"rootpassword" binance_compound_v2 < backup_20260614.sql
```

### 2. 备份 Redis（可选）

```bash
docker-compose exec redis redis-cli SAVE
docker-compose cp redis:/data/dump.rdb ./redis_backup.rdb
```

## 十、监控与维护

### 1. 资源监控

```bash
# 容器资源使用
docker stats

# 特定容器
docker stats binance-compound-backend
```

### 2. 数据库清理

```bash
# 清理 30 天前的事件历史
docker-compose exec backend python manage.py shell -c "
from apps.trading.models import InstanceEvent
from django.utils import timezone
from datetime import timedelta
deleted = InstanceEvent.objects.filter(created_at__lt=timezone.now()-timedelta(days=30)).delete()
print(f'Deleted {deleted[0]} events')
"

# 清理 90 天前的订单
docker-compose exec backend python manage.py shell -c "
from apps.trading.models import TradeRecord
from django.utils import timezone
from datetime import timedelta
deleted = TradeRecord.objects.filter(created_at__lt=timezone.now()-timedelta(days=90)).delete()
print(f'Deleted {deleted[0]} orders')
"
```

### 3. 查看告警历史

```bash
# 最近报警
curl 'http://localhost:18080/api/v1/scanners/alerts/triggered' | python3 -m json.tool

# 资金费率报警
curl 'http://localhost:18080/api/v1/scanners/funding-rates' | python3 -m json.tool
```

## 十一、常见问题

### Q1: 容器无法连接 Binance API
**症状**: 后端日志 `Remote host terminated the handshake`
**解决**:
1. 检查 `BINANCE_PROXY` 配置
2. 容器内测试: `docker-compose exec backend curl -I https://api.binance.com/api/v3/ping`
3. 国内需要代理或海外服务器

### Q2: Celery 任务不执行
**症状**: Tick 不触发
**解决**:
1. 检查 `celery-beat` 日志
2. 确认 Redis 连接: `docker-compose exec redis redis-cli ping`
3. 重启: `docker-compose restart celery-beat celery-worker`

### Q3: 前端 WebSocket 频繁断开
**症状**: Dashboard 价格不更新
**解决**:
1. 检查后端日志是否有 WebSocket 错误
2. 检查 nginx 配置（前端代理 ws）
3. 确认后端 Channels 启动正常

### Q4: 数据库迁移失败
**症状**: `python manage.py migrate` 报错
**解决**:
```bash
# 查看具体错误
docker-compose logs backend

# 重建数据卷（数据会丢失！）
docker-compose down -v
docker-compose up -d --build
docker-compose exec backend python manage.py migrate
```

### Q5: 交易执行失败
**症状**: Tick 报 "Binance API error"
**解决**:
1. 检查账户是否激活: `GET /api/v1/accounts/`
2. 检查 USDT 余额: `GET /api/v1/accounts/balance`
3. 检查 API Key 权限: 需要 "启用现货交易"
4. 查看 `instance_events` 表中的事件

## 十二、安全建议

1. **API Key 权限最小化**：
   - 启用现货交易
   - 关闭提现
   - 限制 IP 白名单（在 Binance 后台配置）

2. **生产环境配置**：
   - 修改 `DJANGO_SECRET_KEY`（`settings.py`）
   - 修改 `ENCRYPTION_KEY`（不要用默认值）
   - 启用 HTTPS（添加 nginx 反向代理）
   - 关闭 `DEBUG` 模式

3. **数据库安全**：
   - 修改默认密码
   - 不要暴露 3306 端口到公网
   - 定期备份

4. **网络隔离**：
   - 后端容器不要直接暴露给公网
   - 使用反向代理（nginx/traefik）
