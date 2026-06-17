<template>
  <div class="trading animate-fade-in">
    <div class="page-header">
      <h2>交易控制台</h2>
      <span class="page-desc">API 账户 · 首次开仓 · 策略 · Tick · AI · 通知</span>
    </div>

    <el-tabs v-model="activeTab" class="pill-tabs">
      <el-tab-pane label="API 账户" name="accounts">
        <AccountsPanel />
      </el-tab-pane>
      <el-tab-pane label="首次开仓" name="open">
        <FirstOpenPanel />
      </el-tab-pane>
      <el-tab-pane label="策略配置" name="strategy">
        <StrategyPanel />
      </el-tab-pane>
      <el-tab-pane label="Tick 控制" name="tick">
        <TickPanel />
      </el-tab-pane>
      <el-tab-pane label="AI 配置" name="ai">
        <AiConfigPanel />
      </el-tab-pane>
      <el-tab-pane label="通知配置" name="notification">
        <NotificationPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AccountsPanel from '../components/AccountsPanel.vue'
import FirstOpenPanel from '../components/FirstOpenPanel.vue'
import StrategyPanel from '../components/StrategyPanel.vue'
import TickPanel from '../components/TickPanel.vue'
import AiConfigPanel from '../components/AiConfigPanel.vue'
import NotificationPanel from '../components/NotificationPanel.vue'

const activeTab = ref('accounts')
</script>

<style scoped>
.trading { max-width: 1200px; }

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.page-desc {
  color: var(--text-secondary);
  font-size: 13px;
}

.pill-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.pill-tabs :deep(.el-tabs__item) {
  padding: 0 18px;
  height: 36px;
  line-height: 36px;
  border-radius: 18px;
  margin-right: 6px;
  font-size: 13px;
}

.pill-tabs :deep(.el-tabs__item.is-active) {
  background: var(--bg-accent-dim);
}

.pill-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.pill-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}
</style>
