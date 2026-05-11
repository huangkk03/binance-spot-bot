# 部署指南

## 目录

- [环境要求](#环境要求)
- [Docker Compose 部署](#docker-compose-部署)
- [手动部署](#手动部署)
- [环境变量配置](#环境变量配置)
- [服务访问](#服务访问)
- [健康检查](#健康检查)
- [日志查看](#日志查看)
- [故障排查](#故障排查)

---

## 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| Docker | 20.10+ | 容器运行时 |
| Docker Compose | 2.0+ | 容器编排 |
| Git | - | 代码克隆 |

---

## Docker Compose 部署

### 1. 克隆项目

```bash
git clone <repository-url>
cd binance-spot-bot
```

### 2. 配置环境变量（可选）

```bash
cat > .env << EOF
DB_HOST=mysql
DB_USER=root
DB_PASSWORD=rootpassword
REDIS_HOST=redis
BINANCE_API_KEY=your_api_key
BINANCE_API_SECRET=your_api_secret
BINANCE_PROXY=http://192.168.4.51:7897
EOF
```

### 3. 启动服务

```bash
docker-compose up -d
```

### 4. 验证服务状态

```bash
docker-compose ps
```

输出示例：

```
NAME                        IMAGE                  COMMAND                  SERVICE      CREATED          STATUS          PORTS
binance-compound-backend    compound-backend       "java -jar app.jar"      backend      5 minutes ago    Up             0.0.0.0:18080->8080/tcp
binance-compound-frontend   compound-frontend      "/docker-entrypoint…"    frontend     5 minutes ago    Up             0.0.0.0:3000->80/tcp
binance-compound-mysql      mysql:5.6              "docker-entrypoint.s…"   mysql        5 minutes ago    Up (healthy)   0.0.0.0:13306->3306/tcp
binance-compound-redis      redis:7-alpine         "docker-entrypoint.s…"   redis        5 minutes ago    Up (healthy)   0.0.0.0:16379->6379/tcp
```

---

## 手动部署

### 后端部署

#### 1. 安装依赖

```bash
# JDK 17
apt install openjdk-17-jdk

# Maven
apt install maven
```

#### 2. 构建项目

```bash
cd backend
mvn clean package -DskipTests
```

#### 3. 配置环境变量

```bash
export DB_HOST=localhost
export DB_USER=root
export DB_PASSWORD=rootpassword
export REDIS_HOST=localhost
```

#### 4. 启动服务

```bash
java -jar target/compound-pro-1.0.0.jar
```

---

### 前端部署

#### 1. 安装 Node.js

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
apt install nodejs
```

#### 2. 安装依赖

```bash
cd frontend
npm install
```

#### 3. 构建项目

```bash
npm run build
```

构建产物在 `dist/` 目录，可使用 Nginx 部署。

#### 4. Nginx 配置

```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://backend:8080;
    }
}
```

---

## 环境变量配置

### 后端配置 (application.yml)

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_HOST` | MySQL 主机地址 | mysql |
| `DB_USER` | 数据库用户名 | root |
| `DB_PASSWORD` | 数据库密码 | rootpassword |
| `REDIS_HOST` | Redis 主机地址 | redis |
| `BINANCE_API_KEY` | Binance API Key | (空) |
| `BINANCE_API_SECRET` | Binance API Secret | (空) |
| `BINANCE_PROXY` | Binance API 代理地址 | (空) |
| `TRADING_AUTO_TICK_ENABLED` | 自动 Tick 开关 | true |

### Docker Compose 环境变量

在 `docker-compose.yml` 中配置：

```yaml
services:
  backend:
    environment:
      DB_HOST: mysql
      DB_USER: root
      DB_PASSWORD: rootpassword
      REDIS_HOST: redis
      BINANCE_PROXY: ${BINANCE_PROXY:-http://192.168.4.51:7897}
      TRADING_AUTO_TICK_ENABLED: "true"
```

---

## 服务访问

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost:3000 | Web UI |
| 后端 API | http://localhost:8080 | REST API |
| MySQL | localhost:13306 | 数据库 |
| Redis | localhost:16379 | 缓存 |

### 初始化数据库

首次启动时，MySQL 容器会自动执行 `DB_SCHEMA.sql` 初始化脚本。

---

## 健康检查

### 后端健康检查

```bash
curl http://localhost:8080/actuator/health
```

### Docker 健康检查

```bash
# 检查所有服务健康状态
docker-compose ps

# 检查 MySQL
docker exec binance-compound-mysql mysqladmin ping -h localhost

# 检查 Redis
docker exec binance-compound-redis redis-cli ping
```

---

## 日志查看

### Docker 日志

```bash
# 后端日志
docker logs binance-compound-backend -f

# 前端日志
docker logs binance-compound-frontend -f

# MySQL 日志
docker logs binance-compound-mysql -f

# Redis 日志
docker logs binance-compound-redis -f
```

### 后端应用日志

日志目录在容器内：`/app/logs/`

```bash
# 查看日志文件
docker exec binance-compound-backend ls -la /app/logs/

# 实时跟踪日志
docker exec binance-compound-backend tail -f /app/logs/spring.log
```

---

## 故障排查

### 常见问题

#### 1. 容器启动失败

```bash
# 查看所有容器状态
docker-compose ps -a

# 查看具体容器日志
docker-compose logs backend
```

#### 2. 数据库连接失败

```bash
# 检查 MySQL 容器是否运行
docker-compose ps mysql

# 检查 MySQL 日志
docker-compose logs mysql

# 测试 MySQL 连接
docker exec -it binance-compound-mysql mysql -uroot -prootpassword
```

#### 3. Redis 连接失败

```bash
# 检查 Redis 容器是否运行
docker-compose ps redis

# 测试 Redis 连接
docker exec -it binance-compound-redis redis-cli ping
```

#### 4. Binance API 请求失败

检查代理配置：

```bash
# 查看代理配置
docker exec binance-compound-backend env | grep PROXY

# 测试 API 连通性
docker exec binance-compound-backend curl -x http://192.168.4.51:7897 https://api.binance.com/api/v3/ping
```

#### 5. 前端无法访问后端

```bash
# 检查后端是否正常运行
docker-compose ps backend

# 检查后端端口
docker-compose port backend 8080

# 从前端容器测试后端连通性
docker exec binance-compound-frontend wget -qO- http://backend:8080/api/v1/prices
```

### 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启单个服务
docker-compose restart backend

# 强制重建
docker-compose up -d --force-recreate
```

### 清理环境

```bash
# 停止所有服务
docker-compose down

# 删除数据卷（慎用）
docker-compose down -v

# 完全清理
docker-compose down -v --remove-orphans
```