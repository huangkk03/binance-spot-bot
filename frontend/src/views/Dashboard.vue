<template>
  <div class="dashboard">
    <el-card class="header-card">
      <div class="header-row">
        <h2>Binance Spot Bot - 真实交易</h2>
        <div class="account-info">
          <el-tag v-if="activeAccount" type="success">激活: {{ activeAccount.account_name }}</el-tag>
          <el-tag v-else type="warning">未激活账户</el-tag>
        </div>
      </div>
    </el-card>

    <el-card class="section">
      <template #header>
        <div class="card-header">
          <span>实时行情</span>
          <el-button @click="generateBtcReport" :loading="reportLoading">生成 BTC AI 报告 (PDF)</el-button>
        </div>
      </template>
      <div class="prices-grid">
        <div v-for="sym in symbols" :key="sym" class="price-card">
          <div class="price-symbol">{{ sym }}</div>
          <div class="price-value">${{ formatPrice(store.prices[sym]) }}</div>
          <div v-if="getTdInfo(sym)" class="td-info" :class="getTdInfo(sym).class">
            {{ getTdInfo(sym).text }}
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="section">
      <template #header>
        <div class="card-header">
          <span>交易实例</span>
          <el-button size="small" @click="refreshInstances">刷新</el-button>
        </div>
      </template>
      <el-table :data="instanceDetails" stripe>
        <el-table-column prop="symbolId" label="交易对" width="140" />
        <el-table-column prop="isOpen" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isOpen ? 'success' : 'info'" size="small">
              {{ row.isOpen ? '持仓中' : '已平仓' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cycleId" label="周期" width="60" />
        <el-table-column prop="anchorPrice" label="锚定价" width="120">
          <template #default="{ row }">{{ formatPrice(row.anchorPrice) }}</template>
        </el-table-column>
        <el-table-column prop="cycleStartPrice" label="开仓价" width="120">
          <template #default="{ row }">{{ formatPrice(row.cycleStartPrice) }}</template>
        </el-table-column>
        <el-table-column prop="baseQty" label="数量" width="100">
          <template #default="{ row }">{{ formatQty(row.baseQty) }}</template>
        </el-table-column>
        <el-table-column prop="quoteAmount" label="权益" width="100">
          <template #default="{ row }">{{ formatPrice(row.quoteAmount) }}</template>
        </el-table-column>
        <el-table-column prop="uPnL" label="未实现盈亏" width="100">
          <template #default="{ row }">{{ formatPrice(row.uPnL) }}</template>
        </el-table-column>
        <el-table-column prop="uPnLPct" label="盈亏/USDT" width="100">
          <template #default="{ row }">
            <span :class="Number(row.uPnLPct) >= 0 ? 'positive' : 'negative'">
              {{ Number(row.uPnLPct) >= 0 ? '+' : '' }}{{ formatPrice(row.uPnLPct) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="markPrice" label="标记价" width="120">
          <template #default="{ row }">{{ formatPrice(row.markPrice) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useCompoundStore } from '../stores/compound'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const store = useCompoundStore()
const symbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']
const reportLoading = ref(false)
let refreshTimer = null

const activeAccount = computed(() => {
  const allAccounts = store.accounts.find?.(a => a.is_active) || null
  return allAccounts
})

const instanceDetails = computed(() => {
  return store.instances.map(inst => {
    const currentPrice = store.prices[inst.symbol] ? Number(store.prices[inst.symbol]) : 0
    const anchorPrice = Number(inst.anchorPrice) || 0
    const cycleStartPrice = Number(inst.cycleStartPrice) || 0
    const baseQty = Number(inst.baseQty) || 0
    const spentQuote = Number(inst.spentQuote) || 0
    const quoteAmount = Number(inst.quoteAmount) || 0
    const cumulativeProfit = Number(inst.cumulativeProfit) || 0

    const uPnL = inst.isOpen && cycleStartPrice > 0
        ? spentQuote * (currentPrice - cycleStartPrice) / cycleStartPrice / 100
        : 0

    return {
      ...inst,
      symbolId: `${inst.symbol}#${inst.instance_id}`,
      uPnL: uPnL,
      uPnLPct: cumulativeProfit,
      markPrice: currentPrice,
    }
  })
})

function formatPrice(value) {
  if (!value && value !== 0) return '-'
  return Number(value).toFixed(2)
}

function formatQty(value) {
  if (!value) return '-'
  return Number(value).toFixed(4)
}

function getTdInfo(symbol) {
  const alert1h = store.alerts[`${symbol}_1h`]
  const alert4h = store.alerts[`${symbol}_4h`]
  if (!alert1h && !alert4h) return null

  const parts = []
  if (alert1h) parts.push(`1H:${alert1h.td_count}`)
  if (alert4h) parts.push(`4H:${alert4h.td_count}`)

  const maxCount = Math.max(alert1h?.td_count || 0, alert4h?.td_count || 0)
  const isBuy = alert1h?.alert_type === 'TD_BUY' || alert4h?.alert_type === 'TD_BUY'
  const isSell = alert1h?.alert_type === 'TD_SELL' || alert4h?.alert_type === 'TD_SELL'

  let tdClass = ''
  if (maxCount >= 9) tdClass = isBuy ? 'td-buy' : isSell ? 'td-sell' : ''
  else if (maxCount >= 6) tdClass = 'td-counting'

  return {
    text: parts.join(' / ') + (maxCount >= 9 ? '⚠️' : ''),
    class: tdClass,
  }
}

async function refreshInstances() {
  await store.fetchInstances()
}

async function generateBtcReport() {
  reportLoading.value = true
  ElMessage.info('正在生成 BTC AI 报告...')
  try {
    const url = await compoundApi.getBtcPredictionPdfUrl()
    window.open(url, '_blank')
    ElMessage.success('报告已在新窗口打开')
  } catch (e) {
    ElMessage.error('生成报告失败: ' + e.message)
  } finally {
    reportLoading.value = false
  }
}

onMounted(async () => {
  store.initWebSocket()
  await Promise.all([
    store.fetchInstances(),
    store.fetchPrices(),
    store.fetchAccounts(),
    store.fetchAlerts(),
    store.fetchFundingAlerts(),
  ])

  refreshTimer = setInterval(async () => {
    await store.fetchAlerts()
    await store.fetchFundingAlerts()
  }, 10000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.dashboard { max-width: 1600px; margin: 0 auto; padding: 1rem; }
.header-card { margin-bottom: 1rem; }
.header-row { display: flex; justify-content: space-between; align-items: center; }
.section { margin-bottom: 1rem; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.prices-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 1rem; }
.price-card {
  background: #1a1f2e; border: 1px solid #2a3042; border-radius: 8px;
  padding: 1rem; text-align: center;
}
.price-symbol { color: #f0b90b; font-weight: 600; margin-bottom: 0.5rem; }
.price-value { font-size: 1.25rem; color: #e0e6ed; }
.td-info { margin-top: 0.5rem; font-size: 0.875rem; }
.td-info.td-buy, .td-info.td-sell { color: #f6465d; font-weight: bold; }
.td-info.td-counting { color: #f0b90b; }
.positive { color: #0ecb81; }
.negative { color: #f6465d; }
</style>
