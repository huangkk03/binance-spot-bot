<template>
  <div>
    <h3>手动首次开仓（核心复利交易入口）</h3>
    <el-alert type="info" :closable="false" style="margin-bottom: 1rem;">
      首次开仓需要手动触发。系统会自动按配置的止盈点卖出，并在价格回落到锚定价时复利再买入。
    </el-alert>

    <el-form :model="form" label-width="120px" style="max-width: 600px;">
      <el-form-item label="选择交易对">
        <el-select v-model="form.symbol" placeholder="选择交易对" style="width: 100%;">
          <el-option v-for="sym in availableSymbols" :key="sym" :value="sym" :label="sym" />
        </el-select>
      </el-form-item>
      <el-form-item label="买入金额 (USDT)">
        <el-input-number
          v-model="form.quoteAmount"
          :min="10"
          :max="100000"
          :step="10"
          placeholder="输入 USDT 金额"
          style="width: 100%;"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="executeOpen" :disabled="!form.symbol">
          确认首次开仓
        </el-button>
      </el-form-item>
    </el-form>

    <el-divider />

    <h4>当前实例状态</h4>
    <el-table :data="instances" stripe size="small">
      <el-table-column prop="symbol" label="交易对" width="100" />
      <el-table-column label="实例#ID" width="80">
        <template #default="{ row }">#{{ row.instance_id }}</template>
      </el-table-column>
      <el-table-column prop="cycle_id" label="周期" width="60" />
      <el-table-column prop="is_open" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.is_open ? 'success' : 'info'" size="small">
            {{ row.is_open ? '持仓中' : '已平仓' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="anchor_price" label="锚定价" width="120" />
      <el-table-column prop="cycle_start_price" label="开仓价" width="120" />
      <el-table-column prop="base_qty" label="数量" width="120" />
      <el-table-column prop="quote_amount" label="复利金额" width="120" />
      <el-table-column prop="cumulative_profit" label="累计盈利" width="120">
        <template #default="{ row }">
          <span :class="Number(row.cumulative_profit) >= 0 ? 'positive' : 'negative'">
            {{ Number(row.cumulative_profit).toFixed(4) }}
          </span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const form = ref({
  symbol: 'BTCUSDT',
  quoteAmount: 100,
})
const availableSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']
const loading = ref(false)
const instances = ref([])
let refreshTimer = null

async function refreshInstances() {
  try {
    instances.value = await compoundApi.listInstances()
  } catch (e) {
    console.error('refreshInstances error:', e)
  }
}

async function executeOpen() {
  if (!form.value.symbol || !form.value.quoteAmount) {
    ElMessage.warning('请填写完整')
    return
  }
  loading.value = true
  try {
    const result = await compoundApi.manualOpen(form.value.symbol, form.value.quoteAmount)
    if (result.success) {
      ElMessage.success('开仓成功: ' + result.message)
      await refreshInstances()
    } else {
      ElMessage.error('开仓失败: ' + (result.errors || []).join('; '))
    }
  } catch (e) {
    ElMessage.error('请求失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshInstances()
  refreshTimer = setInterval(refreshInstances, 10000)
})
onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.positive { color: #0ecb81; }
.negative { color: #f6465d; }
h3, h4 { color: #e0e6ed; }
</style>
