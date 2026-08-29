<template>
  <div class="futures animate-fade-in">
    <div class="page-header">
      <h2>合约交易</h2>
      <span class="page-desc">U本位永续合约 · 逐仓模式 · 支持做多/做空</span>
    </div>

    <el-tabs v-model="tab" class="pill-tabs">
      <el-tab-pane label="首次开仓" name="open">
        <FuturesOpenPanel />
      </el-tab-pane>
      <el-tab-pane label="实例状态" name="instances">
        <FuturesInstancesPanel />
      </el-tab-pane>
      <el-tab-pane label="策略配置" name="strategy">
        <FuturesStrategyPanel />
      </el-tab-pane>
      <el-tab-pane label="Tick 控制" name="tick">
        <FuturesTickPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import FuturesOpenPanel from '../components/FuturesOpenPanel.vue'
import FuturesInstancesPanel from '../components/FuturesInstancesPanel.vue'
import FuturesStrategyPanel from '../components/FuturesStrategyPanel.vue'
import FuturesTickPanel from '../components/FuturesTickPanel.vue'

const tab = ref('open')
</script>

<style scoped>
.futures { max-width: 1200px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px; font-size: 18px; font-weight: 700; color: var(--text-primary); }
.page-desc { color: var(--text-secondary); font-size: 13px; }
.pill-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.pill-tabs :deep(.el-tabs__item) { padding: 0 18px; height: 36px; line-height: 36px; border-radius: 18px; margin-right: 6px; font-size: 13px; }
.pill-tabs :deep(.el-tabs__item.is-active) { background: var(--bg-accent-dim); }
.pill-tabs :deep(.el-tabs__active-bar) { display: none; }
.pill-tabs :deep(.el-tabs__header) { margin-bottom: 20px; }
</style>
