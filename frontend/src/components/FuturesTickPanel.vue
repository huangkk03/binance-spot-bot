<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="info-card">
      合约 Tick 由 Celery Beat 每 30 秒自动调度。
    </el-alert>
    <el-button type="primary" @click="trigger" :loading="loading" round size="large">手动执行合约 Tick</el-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const loading = ref(false)
async function trigger() {
  loading.value = true
  try {
    const r = await compoundApi.futuresTick()
    ElMessage.success('Tick 完成: ' + r.actions.length + ' 操作')
  } catch { ElMessage.error('失败') }
  finally { loading.value = false }
}
</script>
<style scoped>
.info-card { margin-bottom: 20px; }
</style>
