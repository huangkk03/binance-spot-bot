<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="info-card">
      Tick 由 Celery Beat 每 30 秒自动调度。也可手动触发。
    </el-alert>

    <div class="actions">
      <el-button type="primary" @click="triggerTick" :loading="loading" round size="large">
        <el-icon :size="18"><VideoPlay /></el-icon>
        手动执行 Tick
      </el-button>
      <el-button @click="triggerScan" :loading="scanning" round size="large">触发报警扫描</el-button>
    </div>

    <div v-if="lastResult" class="result">
      <div class="result-header">最近结果</div>
      <pre>{{ lastResult }}</pre>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const scanning = ref(false)
const lastResult = ref('')

const symbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']

async function triggerTick() {
  loading.value = true
  try {
    const r = await compoundApi.executeTick(symbols)
    ElMessage.success('Tick 完成: ' + r.actions.length + ' 操作')
    lastResult.value = JSON.stringify(r, null, 2)
  } catch (e) { ElMessage.error('失败: ' + e.message) }
  finally { loading.value = false }
}

async function triggerScan() {
  scanning.value = true
  try {
    const r = await compoundApi.triggerScan()
    ElMessage.success(r.message || '已触发')
  } catch (e) { ElMessage.error('失败') }
  finally { scanning.value = false }
}
</script>

<style scoped>
.info-card { margin-bottom: 20px; }
.actions { display: flex; gap: 12px; margin-bottom: 20px; }
.result { margin-top: 16px; }
.result-header { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 8px; }
pre { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); padding: 16px; font-size: 12px; color: var(--text-secondary); overflow: auto; max-height: 400px; }
</style>
