<template>
  <div class="trading-console">
    <el-tabs v-model="activeTab">
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
.trading-console { max-width: 1400px; margin: 0 auto; padding: 1rem; }
</style>
