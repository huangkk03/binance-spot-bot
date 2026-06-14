<template>
  <div>
    <h3>Tick 控制</h3>
    <p>Tick 由 Celery Beat 每 30 秒自动调度。也可以手动触发：</p>
    <el-button type="primary" @click="triggerTick" :loading="loading">手动执行 Tick</el-button>
    <el-button @click="triggerScan" :loading="scanning">触发报警扫描</el-button>

    <div v-if="lastResult" class="result">
      <h4>最近结果：</h4>
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
    const result = await compoundApi.executeTick(symbols)
    ElMessage.success(`Tick 执行完成: ${result.actions.length} 个操作`)
    lastResult.value = JSON.stringify(result, null, 2)
  } catch (e) {
    ElMessage.error('Tick 失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function triggerScan() {
  scanning.value = true
  try {
    const result = await compoundApi.triggerScan()
    ElMessage.success(result.message || '扫描已触发')
  } catch (e) {
    ElMessage.error('扫描失败: ' + e.message)
  } finally {
    scanning.value = false
  }
}
</script>

<style scoped>
.result { margin-top: 1rem; }
pre { background: #1a1f2e; padding: 1rem; border-radius: 4px; overflow: auto; }
</style>
