<template>
  <div class="page animate-fade-in">
    <h2>设置</h2>
    <p class="desc">系统信息</p>
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item v-for="item in store.config" :key="item.key" :label="item.key">
        <code>{{ mask(item.value) }}</code>
      </el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useCompoundStore } from '../stores/compound'
const store = useCompoundStore()
onMounted(() => store.fetchConfig())
function mask(v) { return v && v.length > 16 ? v.slice(0, 8) + '****' + v.slice(-4) : v || '' }
</script>

<style scoped>
.page { max-width: 700px; }
h2 { margin: 0 0 4px; font-size: 18px; font-weight: 700; color: var(--text-primary); }
.desc { color: var(--text-secondary); font-size: 13px; margin: 0 0 20px; }
code { font-family: 'SF Mono', Monaco, monospace; font-size: 12px; color: var(--text-secondary); }
</style>
