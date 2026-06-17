<template>
  <div class="dashboard animate-fade-in">
    <!-- 实时行情 -->
    <section class="prices-section">
      <div class="section-header">
        <h2>实时行情</h2>
        <el-button size="small" @click="generateBtcReport" :loading="reportLoading" round>
          <el-icon :size="14"><Document /></el-icon>
          BTC AI 报告
        </el-button>
      </div>
      <div class="prices-grid">
        <div v-for="sym in symbols" :key="sym" class="price-card" :class="getPriceFlash(sym)">
          <div class="card-top">
            <span class="price-symbol">{{ sym.replace('USDT', '') }}</span>
            <span class="price-pair">/USDT</span>
          </div>
          <div class="price-value" :ref="el => priceRefs[sym] = el">
            {{ formatPrice(store.prices[sym]) }}
          </div>
          <div v-if="getTdInfo(sym)" class="td-badge" :class="getTdInfo(sym).class">
            {{ getTdInfo(sym).text }}
          </div>
        </div>
      </div>
    </section>

    <!-- 交易实例 -->
    <section class="instances-section">
      <div class="section-header">
        <h2>交易实例</h2>
        <el-button size="small" @click="refreshInstances" round>刷新</el-button>
      </div>
      <el-table :data="instanceDetails" size="small" stripe>
        <el-table-column label="交易对" width="120">
          <template #default="{ row }">
            <span class="symbol-link">{{ row.symbol }}<span class="instance-id">#{{ row.instance_id }}</span></span>
          </template>
        </el-table-column>
        <el-table-column prop="isOpen" label="状态" width="72">
          <template #default="{ row }">
            <span class="status-dot" :class="row.isOpen ? 'open' : 'closed'" />
            {{ row.isOpen ? '持仓' : '已平' }}
          </template>
        </el-table-column>
        <el-table-column prop="cycleId" label="周期" width="48" align="center" />
        <el-table-column label="锚定价" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.anchor_price) }}</template>
        </el-table-column>
        <el-table-column label="开仓价" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.cycle_start_price) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="right">
          <template #default="{ row }">{{ formatQty(row.base_qty) }}</template>
        </el-table-column>
        <el-table-column label="复利金额" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.quote_amount) }}</template>
        </el-table-column>
        <el-table-column label="盈亏/USDT" width="100" align="right">
          <template #default="{ row }">
            <span :class="Number(row.uPnLPct) >= 0 ? 'profit-positive' : 'profit-negative'">
              {{ Number(row.uPnLPct) >= 0 ? '+' : '' }}{{ Number(row.uPnLPct).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="标记价" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.markPrice) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useCompoundStore } from '../stores/compound'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const store = useCompoundStore()
const symbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']
const reportLoading = ref(false)
const priceRefs = ref({})

// 跟踪每个币种的上次价格用于判断涨跌
const lastPrices = ref({})
const priceFlash = ref({})

watch(() => store.prices, (newPrices) => {
  for (const sym of symbols) {
    const price = Number(newPrices[sym])
    const last = lastPrices.value[sym]
    if (last && price !== last) {
      priceFlash.value[sym] = price > last ? 'up' : 'down'
      setTimeout(() => { priceFlash.value[sym] = '' }, 800)
    }
    lastPrices.value[sym] = price
  }
}, { deep: true })

function getPriceFlash(sym) {
  return priceFlash.value[sym] || ''
}

const instanceDetails = computed(() => {
  return store.instances.map(inst => {
    const currentPrice = Number(store.prices[inst.symbol]) || 0
    const cumulativeProfit = Number(inst.cumulative_profit) || 0
    return { ...inst, uPnLPct: cumulativeProfit, markPrice: currentPrice }
  })
})

function fmt(v) {
  if (!v && v !== 0) return '-'
  return Number(v).toFixed(2)
}

function formatPrice(v) {
  if (!v && v !== 0) return '-'
  return Number(v).toFixed(2)
}

function formatQty(v) {
  if (!v) return '-'
  return Number(v).toFixed(4)
}

function getTdInfo(sym) {
  const a1 = store.alerts[sym + '_1h']
  const a4 = store.alerts[sym + '_4h']
  if (!a1 && !a4) return null
  const parts = []
  if (a1) parts.push('1H:' + a1.td_count)
  if (a4) parts.push('4H:' + a4.td_count)
  const max = Math.max(a1?.td_count || 0, a4?.td_count || 0)
  const isBuy = a1?.alert_type === 'TD_BUY' || a4?.alert_type === 'TD_BUY'
  return { text: parts.join(' / ') + (max >= 9 ? '⚠️' : ''), class: max >= 9 ? (isBuy ? 'td-buy' : 'td-sell') : 'td-counting' }
}

async function refreshInstances() {
  await store.fetchInstances()
}

async function generateBtcReport() {
  reportLoading.value = true
  ElMessage.info('正在生成 BTC AI 报告...')
  try {
    window.open(await compoundApi.getBtcPredictionPdfUrl(), '_blank')
    ElMessage.success('报告已在新窗口打开')
  } catch (e) {
    ElMessage.error('生成报告失败')
  } finally {
    reportLoading.value = false
  }
}

onMounted(async () => {
  store.initWebSocket()
  await Promise.all([
    store.fetchInstances(), store.fetchAccounts(), store.fetchAlerts(), store.fetchFundingAlerts()
  ])
})

const refreshTimer = setInterval(() => { store.fetchAlerts(); store.fetchFundingAlerts() }, 10000)
onUnmounted(() => clearInterval(refreshTimer))
</script>

<style scoped>
.dashboard { max-width: 1400px; }

/* ========== 段落 ========== */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.prices-section { margin-bottom: 28px; }

/* ========== 行情卡片 ========== */
.prices-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}

.price-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  padding: 16px;
  cursor: default;
  transition: all var(--transition);
}

.price-card:hover {
  border-color: var(--accent);
  transform: translateY(-1px);
  box-shadow: var(--shadow-hover);
}

.card-top {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 8px;
}

.price-symbol {
  color: var(--accent);
  font-size: 14px;
  font-weight: 700;
}

.price-pair {
  color: var(--text-muted);
  font-size: 11px;
}

.price-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  transition: color 0.3s;
}

.price-card.up .price-value { animation: priceFlashGreen 0.8s ease-out; }
.price-card.down .price-value { animation: priceFlashRed 0.8s ease-out; }

.td-badge {
  margin-top: 10px;
  padding: 3px 8px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  display: inline-block;
}

.td-badge.td-buy, .td-badge.td-sell { background: var(--negative-bg); color: var(--negative); }
.td-badge.td-counting { background: var(--bg-accent-dim); color: var(--accent); }

/* ========== 表格 ========== */
.instances-section { margin-top: 4px; }

.symbol-link { font-weight: 600; color: var(--text-primary); }
.instance-id { color: var(--text-muted); font-weight: 400; margin-left: 2px; }

.status-dot {
  display: inline-block;
  width: 6px; height: 6px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.status-dot.open { background: var(--positive); box-shadow: 0 0 6px var(--positive); }
.status-dot.closed { background: var(--text-muted); }

.profit-positive { color: var(--positive); font-weight: 600; }
.profit-negative { color: var(--negative); font-weight: 600; }

:deep(.el-table .cell) { padding: 6px 8px; }
:deep(.el-table td.el-table__cell) { padding: 4px 0; }
:deep(.el-table__body-wrapper tbody tr) { height: 36px; }
</style>
