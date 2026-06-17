<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="info-card">
      首次开仓需要手动触发。系统会自动按配置的止盈点卖出，并在价格回落到锚定价时复利再买入。
    </el-alert>

    <div class="open-form">
      <div class="form-row">
        <el-select v-model="form.symbol" placeholder="选择交易对" size="large">
          <el-option v-for="sym in symbols" :key="sym" :value="sym" :label="sym.replace('USDT','') + '/USDT'" />
        </el-select>
        <el-input-number v-model="form.quoteAmount" :min="10" :max="100000" :step="10" size="large" placeholder="USDT 金额" />
        <el-button type="primary" :loading="loading" @click="executeOpen" :disabled="!form.symbol" size="large" round>
          <el-icon :size="18"><Pointer /></el-icon>
          确认首次开仓
        </el-button>
      </div>
    </div>

    <div class="section-header">
      <h3>实例状态</h3>
    </div>

    <el-table :data="instances" size="small" stripe>
      <el-table-column label="交易对" width="100">
        <template #default="{ row }">{{ row.symbol }}<span class="dim">#{{ row.instance_id }}</span></template>
      </el-table-column>
      <el-table-column prop="cycle_id" label="周期" width="48" align="center" />
      <el-table-column label="状态" width="72">
        <template #default="{ row }">
          <span class="status-dot" :class="row.is_open ? 'open' : 'closed'" />
          {{ row.is_open ? '持仓' : '已平' }}
        </template>
      </el-table-column>
      <el-table-column label="锚定价" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.anchor_price) }}</template>
      </el-table-column>
      <el-table-column label="开仓价" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.cycle_start_price) }}</template>
      </el-table-column>
      <el-table-column label="数量" width="90" align="right">
        <template #default="{ row }">{{ fmtQty(row.base_qty) }}</template>
      </el-table-column>
      <el-table-column label="复利金额" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.quote_amount) }}</template>
      </el-table-column>
      <el-table-column label="累计盈利" width="100" align="right">
        <template #default="{ row }">
          <span :class="Number(row.cumulative_profit) >= 0 ? 'positive' : 'negative'">
            {{ Number(row.cumulative_profit) >= 0 ? '+' : '' }}{{ Number(row.cumulative_profit).toFixed(4) }}
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

const symbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']
const form = ref({ symbol: 'BTCUSDT', quoteAmount: 100 })
const loading = ref(false)
const instances = ref([])
let timer = null

async function refresh() { try { instances.value = await compoundApi.listInstances() } catch {} }

async function executeOpen() {
  if (!form.value.symbol) return
  loading.value = true
  try {
    const r = await compoundApi.manualOpen(form.value.symbol, form.value.quoteAmount)
    ElMessage({ message: r.success ? '开仓成功' : '开仓失败: ' + (r.errors || []).join('; '), type: r.success ? 'success' : 'error' })
    if (r.success) await refresh()
  } catch (e) { ElMessage.error('请求失败: ' + e.message) }
  finally { loading.value = false }
}

function fmt(v) { return v ? Number(v).toFixed(2) : '-' }
function fmtQty(v) { return v ? Number(v).toFixed(4) : '-' }

onMounted(() => { refresh(); timer = setInterval(refresh, 10000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.info-card { margin-bottom: 20px; }
.open-form { margin-bottom: 24px; }
.form-row { display: flex; gap: 12px; }
.form-row > *:first-child { flex: 1; }
.form-row > *:nth-child(2) { width: 180px; }
.section-header { margin-bottom: 12px; }
.section-header h3 { margin: 0; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }
.status-dot.open { background: var(--positive); box-shadow: 0 0 6px var(--positive); }
.status-dot.closed { background: var(--text-muted); }
.positive { color: var(--positive); font-weight: 600; }
.negative { color: var(--negative); font-weight: 600; }
.dim { color: var(--text-muted); }
</style>
