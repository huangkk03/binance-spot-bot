<template>
  <div class="settings">
    <h2>系统设置</h2>
    <p>交易参数、监控币种等配置通过环境变量或 settings.py 设置。</p>
    <el-card>
      <template #header>当前配置</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="item in store.config" :key="item.key" :label="item.key">
          {{ maskValue(item.value) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useCompoundStore } from '../stores/compound'

const store = useCompoundStore()
onMounted(() => store.fetchConfig())

function maskValue(v) {
  if (!v) return ''
  if (v.length > 16) return v.slice(0, 8) + '****' + v.slice(-4)
  return v
}
</script>

<style scoped>
.settings { max-width: 1200px; margin: 0 auto; padding: 1rem; }
</style>
