<template>
  <div class="page animate-fade-in">
    <h2>策略配置</h2>
    <p class="desc">全局 + 交易对独立覆盖</p>
    <StrategyPanel />
  </div>
</template>

<script setup>
import StrategyPanel from '../components/StrategyPanel.vue'
</script>

<style scoped>
.page { max-width: 1000px; }
h2 { margin: 0 0 4px; font-size: 18px; font-weight: 700; color: var(--text-primary); }
.desc { color: var(--text-secondary); font-size: 13px; margin: 0 0 20px; }
</style>
