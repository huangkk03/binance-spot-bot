---
name: binance-compound-pro-refactor
description: 将 Python 脚本重构为 Vue 3 + Spring Boot 3 架构，包含 MySQL 持久化、真实行情模拟引擎及 Docker 全栈容器化。
version: 1.3.0
author: Gemini-Collaborator
tags: [binance-api, spring-boot-3, mysql, simulation-engine, docker-compose]
---

# 币安复利交易平台全链路重构技能 (OpenCode 增强版)

## 阶段 1：数据架构与 MySQL 设计 (Data & MySQL)
**目标**：设计支持“真实”与“模拟”双模式的 MySQL 数据库底座。
- **输出要求**：
  - 生成 `DB_SCHEMA.sql`：包含 `cycle_instances` (实例状态)、`trade_records` (交易记录)、`sim_accounts` (模拟账户余额)。
  - **核心约束**：
    - **数据库选型**：明确使用 **MySQL 8.0+** 作为持久化存储。
    - **双模式支持**：所有交易实体必须包含 `is_simulation` 字段（TINYINT/Boolean），以区分真实与模拟数据。
    - **精度要求**：所有金额、价格、数量字段必须使用 `DECIMAL(32, 16)`。

## 阶段 2：后端策略引擎与模拟执行器 (Backend & Simulation)
**目标**：实现 1% Step / 5% Cycle 逻辑，并挂载基于真实价格的模拟引擎。
- **模拟充值 (Mock Deposit)**：实现 Service 方法，支持手动向模拟账户“注资”，并触发实例分配逻辑。
- **模拟交易 (Simulation Engine)**：
  - 接入币安实时价格 (WebSocket)，当价格触达策略点位时，模拟模式下更新本地 MySQL 余额而非调用币安 API。
- **策略移植**：
  - 1% Step：价格相对上次买入涨 1% 触发“卖出 -> 再买入”滚动。
  - 5% Cycle：相对本轮初始价涨 5% 触发结算并开启下一轮。

## 阶段 3：前端监控与模拟控制台 (Frontend Dashboard)
**目标**：Vue 3.5+ 仪表盘，支持实时行情与模拟操作。
- **功能点**：
  - **模拟控制台**：提供“模拟充值”按钮及参数（STEP_PCT/CYCLE_PCT）动态调节界面。
  - **实时看板**：展示各实例（含模拟）的收益率、持仓均价及当前 Cycle ID。
## 阶段 4：QA 自动化与 Docker 部署 (DevOps)
**目标**：实现包含 MySQL 在内的全链路容器化。
- **自动化测试**：
  - 生成 `StrategySimulatorTest.java`：通过 Mock 价格序列验证模拟交易逻辑的准确性。
- **Docker 编排 (必须包含 MySQL)**：
  - 生成 `docker-compose.yml`：**必须**集成 `mysql` 容器、`backend`、`frontend` (Nginx) 及 `redis`。
  - **自动初始化**：配置 MySQL 容器自动加载 `DB_SCHEMA.sql`。
  - 生成多阶段构建的 `Dockerfile` 优化生产镜像。

## 示例触发指令 (Usage)
- "根据 binance-compound-pro-refactor 技能，开始阶段 1，生成 MySQL 数据库设计和 Entity 代码。"
- "执行阶段 4，生成完整的 docker-compose.yml，包含 MySQL 的自动初始化配置。"