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
          <div class="price-value">
            {{ formatPrice(store.prices[sym], sym) }}
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
        <!-- 1. 交易对#ID -->
        <el-table-column label="交易对" width="110">
          <template #default="{ row }">
            <span class="symbol-link">{{ row.symbol }}<span class="instance-id">#{{ row.instance_id }}</span></span>
          </template>
        </el-table-column>

        <!-- 2. 状态·周期 -->
        <el-table-column label="状态·周期" width="110">
          <template #default="{ row }">
            <template v-if="row.isOpen">
              <span class="status-dot open" />持仓·第{{ row.cycle_id || 1 }}轮
            </template>
            <template v-else>
              <span class="status-dot closed" />
              {{ row.cycle_id > 0 ? '等待入场' : '新创建' }}
              <span v-if="row.cycle_id > 0" class="dim cycle-hint">{{ row.cycle_id }}轮已结</span>
            </template>
          </template>
        </el-table-column>

        <!-- 3. 入场价 -->
        <el-table-column label="入场价" width="95" align="right">
          <template #default="{ row }">
            <span v-if="row.entryPrice > 0">{{ fmt(row.entryPrice) }}</span>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>

        <!-- 4. 投入(USDT) -->
        <el-table-column label="投入(USDT)" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.isOpen && row.spentQuote > 0" class="spent-value">{{ fmt(row.spentQuote) }}</span>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>

        <!-- 5. 持仓 -->
        <el-table-column label="持仓" width="90" align="right">
          <template #default="{ row }">
            <span v-if="row.isOpen && row.baseQty > 0">{{ formatQty(row.baseQty) }}</span>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>

        <!-- 6. 盈利 (核心列) -->
        <el-table-column label="盈利" width="135" align="right">
          <template #default="{ row }">
            <!-- 开仓中：浮盈 -->
            <template v-if="row.isOpen">
              <div :class="row.openProfitPct >= 0 ? 'profit-positive' : 'profit-negative'">
                {{ row.openProfitPct >= 0 ? '+' : '' }}{{ row.openProfitPct.toFixed(2) }}%
              </div>
              <div class="profit-sub" :class="row.openProfitUsdt >= 0 ? 'profit-positive' : 'profit-negative'">
                {{ row.openProfitUsdt >= 0 ? '+' : '' }}{{ row.openProfitUsdt.toFixed(2) }} USDT
              </div>
            </template>
            <!-- 已平仓：累计 -->
            <template v-else>
              <div :class="row.cumProfit >= 0 ? 'profit-positive' : 'profit-negative'">
                {{ row.cumProfit >= 0 ? '+' : '' }}{{ row.cumProfit.toFixed(2) }}
              </div>
              <div class="profit-sub dim">累计 (已扣费)</div>
            </template>
          </template>
        </el-table-column>

        <!-- 7. 复利本金 -->
        <el-table-column label="复利本金" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.quoteAmount > 0" class="compound-value">{{ fmt(row.quoteAmount) }}</span>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>

        <!-- 8. 止盈/止损 -->
        <el-table-column label="止盈/止损" width="140" align="right">
          <template #default="{ row }">
            <template v-if="row.isOpen && row.tpPrice > 0">
              <span class="tp-tag">{{ fmt(row.tpPrice) }}</span>
              <span class="divider">/</span>
              <span v-if="row.slPrice > 0" class="sl-tag">{{ fmt(row.slPrice) }}</span>
              <span v-else class="dim">关</span>
            </template>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>

        <!-- 9. 标记价 -->
        <el-table-column label="标记价" width="95" align="right">
          <template #default="{ row }">{{ formatMarkPrice(row) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useCompoundStore } from '../stores/compound'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const store = useCompoundStore()
const symbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']
const reportLoading = ref(false)

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
    const markPrice   = Number(store.prices[inst.symbol]) || 0
    const entryPrice  = Number(inst.cycle_start_price) || 0
    const baseQty     = Number(inst.base_qty) || 0
    const spentQuote  = Number(inst.spent_quote) || 0
    const quoteAmount = Number(inst.quote_amount) || 0
    const cumProfit   = Number(inst.cumulative_profit) || 0

    // 开仓中浮盈计算
    const openProfitPct  = entryPrice > 0 && inst.isOpen
      ? (markPrice - entryPrice) / entryPrice * 100 : 0
    const openProfitUsdt = entryPrice > 0 && inst.isOpen
      ? spentQuote * (markPrice - entryPrice) / entryPrice : 0

    // 止盈/止损触发价 (基于后端默认 3%/10%, 实际由后端策略决定)
    const tpPrice = entryPrice > 0 ? entryPrice * 1.03 : 0
    const slPrice = entryPrice > 0 ? entryPrice * 0.90 : 0

    return {
      ...inst,
      markPrice,
      entryPrice,
      baseQty,
      spentQuote,
      quoteAmount,
      cumProfit,
      openProfitPct,
      openProfitUsdt,
      tpPrice,
      slPrice,
    }
  })
})

function fmt(v) {
  if (!v && v !== 0) return '-'
  return Number(v).toFixed(2)
}

function getDecimals(symbol) {
  if (!symbol) return 2
  const s = symbol.toUpperCase()
  if (s === 'ADAUSDT') return 4
  if (s === 'DOGEUSDT') return 5
  return 2
}

function formatPrice(v, symbol) {
  if (!v && v !== 0) return '-'
  return Number(v).toFixed(getDecimals(symbol))
}

function formatMarkPrice(row) {
  const v = row.markPrice
  if (!v && v !== 0) return '-'
  return Number(v).toFixed(getDecimals(row.symbol))
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
  width: 7px; height: 7px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.status-dot.open { background: var(--positive); box-shadow: 0 0 6px var(--positive); }
.status-dot.closed { background: var(--text-muted); }

.cycle-hint { display: block; font-size: 10px; margin-top: 1px; }

.spent-value { font-weight: 600; color: var(--text-primary); }
.profit-positive { color: var(--positive); font-weight: 700; font-size: 13px; }
.profit-negative { color: var(--negative); font-weight: 700; font-size: 13px; }
.profit-sub { font-size: 10px; line-height: 1.2; }

.compound-value { color: var(--accent); font-weight: 700; }

.tp-tag {
  display: inline-block;
  padding: 1px 6px;
  background: var(--positive-bg);
  color: var(--positive);
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.divider { color: var(--text-muted); margin: 0 3px; }
.sl-tag {
  display: inline-block;
  padding: 1px 6px;
  background: var(--negative-bg);
  color: var(--negative);
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.dim { color: var(--text-muted); }

:deep(.el-table .cell) { padding: 6px 8px; line-height: 1.3; }
:deep(.el-table td.el-table__cell) { padding: 6px 0; }
:deep(.el-table__body-wrapper tbody tr) { height: 48px; }
</style>
