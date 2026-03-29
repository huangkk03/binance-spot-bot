<template>
  <div class="dashboard">
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">实例总数</div>
        <div class="stat-value">{{ store.instances.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">持仓中</div>
        <div class="stat-value">{{ store.openInstances.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总收益 (USDT)</div>
        <div class="stat-value" :class="{ positive: store.totalPnL > 0, negative: store.totalPnL < 0 }">
          {{ store.totalPnL.toFixed(2) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">USDT 余额</div>
        <div class="stat-value">{{ usdtBalance }}</div>
      </div>
      <div class="stat-card mode-toggle">
        <div class="stat-label">交易模式</div>
        <div class="mode-switch">
          <span :class="{ active: !store.isSimulation }">真实</span>
          <el-switch 
            v-model="isSimMode" 
            active-text="模拟" 
            inactive-text="" 
          />
          <span :class="{ active: store.isSimulation }">模拟</span>
        </div>
      </div>
    </div>

    <div class="prices-section">
      <h2>实时行情</h2>
      <div class="prices-grid">
        <div v-for="sym in symbols" :key="sym" class="price-card" @click="openKlineDetail(sym)">
          <div class="price-symbol">{{ sym }}</div>
          <div class="kline-data" v-if="store.klines[sym]">
            <div class="kline-row">
              <span class="label">最新价:</span>
              <span class="value" :class="getPriceClass(sym)">{{ formatPrice(store.prices[sym], sym) }}</span>
            </div>
            <div class="kline-row">
              <span class="label">开盘:</span>
              <span class="value">{{ formatPrice(store.klines[sym].open, sym) }}</span>
            </div>
            <div class="kline-row">
              <span class="label">最高:</span>
              <span class="value high">{{ formatPrice(store.klines[sym].high, sym) }}</span>
            </div>
            <div class="kline-row">
              <span class="label">最低:</span>
              <span class="value low">{{ formatPrice(store.klines[sym].low, sym) }}</span>
            </div>
            <div class="kline-row">
              <span class="label">成交量:</span>
              <span class="value">{{ formatQty(store.klines[sym].volume) }}</span>
            </div>
            <div class="kline-row" v-if="getTdInfo(sym)">
              <span class="label">TD计数:</span>
              <span class="value" :class="getTdInfo(sym).class">{{ getTdInfo(sym).text }}</span>
            </div>
          </div>
          <div v-else class="kline-loading">
            加载中...
          </div>
          <div class="click-hint">点击查看K线</div>
        </div>
      </div>
    </div>

    <div class="instances-section">
      <h2>活动实例</h2>
      <el-table :data="instanceDetails" style="width: 100%" :max-height="500" @row-click="handleRowClick">
        <el-table-column prop="symbolId" label="交易对#实例" width="130">
          <template #default="{ row }">
            <span class="symbol-link">{{ row.symbol }}#{{ row.instanceId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isOpen ? 'success' : 'info'" size="small">{{ row.isOpen ? '持仓中' : '已平仓' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cycleId" label="周期" width="70" />
        <el-table-column prop="stepCnt" label="步数" width="70" />
        <el-table-column prop="anchorPrice" label="锚定价" width="120">
          <template #default="{ row }">{{ formatPrice(row.anchorPrice, row.symbol) }}</template>
        </el-table-column>
        <el-table-column prop="reentryPrice" label="重新入场" width="120">
          <template #default="{ row }">{{ formatPrice(row.reentryPrice, row.symbol) }}</template>
        </el-table-column>
        <el-table-column prop="baseQty" label="数量" width="130">
          <template #default="{ row }">{{ formatQty(row.baseQty) }}</template>
        </el-table-column>
        <el-table-column prop="equity" label="权益" width="110">
          <template #default="{ row }">{{ formatPrice(row.equity, row.symbol) }}</template>
        </el-table-column>
        <el-table-column prop="uPnL" label="未实现盈亏" width="100">
          <template #default="{ row }">
            <span :class="row.uPnL >= 0 ? 'positive' : 'negative'">
              {{ row.uPnL >= 0 ? '+' : '' }}{{ formatPrice(row.uPnL, row.symbol) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="uPnLPct" label="盈亏%" width="80">
          <template #default="{ row }">
            <span :class="row.uPnL >= 0 ? 'positive' : 'negative'">{{ row.uPnLPct }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="roi" label="收益率" width="90">
          <template #default="{ row }">
            <span :class="row.roi >= 0 ? 'positive' : 'negative'">{{ row.roi }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="markPrice" label="标记价" width="120">
          <template #default="{ row }">{{ formatPrice(row.markPrice, row.symbol) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAtUtc" label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAtUtc) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showKlineDialog" :title="selectedSymbol + ' K线图'" width="95%" top="2vh" destroy-on-close>
      <template #default>
        <KlineChart :symbol="selectedSymbol" />
      </template>
    </el-dialog>

    <el-dialog v-model="showInstanceDialog" :title="selectedInstanceSymbol + ' 实例详情'" width="95%" top="5vh" destroy-on-close>
      <template #default>
        <InstanceDetail :symbol="selectedInstanceSymbol" :instance-id="selectedInstanceId" :is-simulation="store.isSimulation" />
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useCompoundStore } from '../stores/compound'
import KlineChart from '../components/KlineChart.vue'
import InstanceDetail from '../components/InstanceDetail.vue'

const store = useCompoundStore()

const symbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']
const showKlineDialog = ref(false)
const showInstanceDialog = ref(false)
const selectedSymbol = ref('')
const selectedInstanceSymbol = ref('')
const selectedInstanceId = ref(0)
const isSimMode = computed({
  get: () => store.isSimulation,
  set: (val) => store.setSimulationMode(val)
})

const usdtBalance = computed(() => {
  const account = store.accounts['USDT']
  return account ? Number(account.freeBalance).toFixed(2) : '0.00'
})

const instanceDetails = computed(() => {
  return store.instances.map(inst => {
    const currentPrice = store.prices[inst.symbol] ? Number(store.prices[inst.symbol]) : 0
    const anchorPrice = Number(inst.anchorPrice) || 0
    const cycleStartPrice = Number(inst.cycleStartPrice) || 0
    const baseQty = Number(inst.baseQty) || 0
    const quoteAmount = Number(inst.quoteAmount) || 0
    
    const equity = inst.isOpen ? baseQty * currentPrice : quoteAmount
    const uPnL = inst.isOpen ? (currentPrice - cycleStartPrice) * baseQty : 0
    const uPnLPct = cycleStartPrice > 0 ? ((currentPrice - cycleStartPrice) / cycleStartPrice * 100).toFixed(2) : '0.00'
    const roi = anchorPrice > 0 ? ((currentPrice - anchorPrice) / anchorPrice * 100).toFixed(2) : '0.00'
    
    return {
      ...inst,
      symbolId: `${inst.symbol}#${inst.instanceId}`,
      equity: equity,
      uPnL: uPnL,
      uPnLPct: uPnLPct,
      roi: roi,
      markPrice: currentPrice,
      stepCnt: 0
    }
  })
})

function getDecimalPlaces(symbol) {
  const highPriceSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT']
  if (highPriceSymbols.includes(symbol)) return 2
  const midPriceSymbols = ['ADAUSDT']
  if (midPriceSymbols.includes(symbol)) return 4
  return 8
}

function formatPrice(value, symbol) {
  if (value === null || value === undefined || value === '0' || value === 0) return '-'
  const decimals = getDecimalPlaces(symbol)
  return Number(value).toFixed(decimals)
}

function formatQty(value) {
  if (!value) return '-'
  return Number(value).toFixed(4)
}

function formatTime(time) {
  if (!time) return '-'
  if (Array.isArray(time)) {
    const d = new Date(Date.UTC(time[0], time[1] - 1, time[2], time[3], time[4], time[5]))
    return d.toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
  }
  return new Date(time).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
}

function getPriceClass(symbol) {
  const kline = store.klines[symbol]
  if (!kline) return ''
  const current = Number(store.prices[symbol])
  const open = Number(kline.open)
  if (current > open) return 'price-up'
  if (current < open) return 'price-down'
  return ''
}

function getTdInfo(symbol) {
  const alert1h = store.alerts[`${symbol}_1h`]
  const alert4h = store.alerts[`${symbol}_4h`]
  
  if (!alert1h && !alert4h) return null
  
  const parts = []
  if (alert1h) {
    parts.push(`1H:${alert1h.tdCount}`)
  }
  if (alert4h) {
    parts.push(`4H:${alert4h.tdCount}`)
  }
  
  const maxCount = Math.max(alert1h?.tdCount || 0, alert4h?.tdCount || 0)
  const isBuy = alert1h?.alertType === 'TD_BUY' || alert4h?.alertType === 'TD_BUY'
  const isSell = alert1h?.alertType === 'TD_SELL' || alert4h?.alertType === 'TD_SELL'
  
  let tdClass = ''
  if (maxCount >= 9) {
    tdClass = isBuy ? 'td-buy' : isSell ? 'td-sell' : ''
  } else if (maxCount >= 6) {
    tdClass = 'td-counting'
  }
  
  return {
    text: parts.join(' / ') + (maxCount >= 9 ? '⚠️' : ''),
    class: tdClass
  }
}

function openKlineDetail(symbol) {
  selectedSymbol.value = symbol
  showKlineDialog.value = true
}

function handleRowClick(row) {
  selectedInstanceSymbol.value = row.symbol
  selectedInstanceId.value = row.instanceId
  showInstanceDialog.value = true
}

onMounted(async () => {
  await store.fetchInstances()
  await store.fetchPrices()
  await store.fetchAccounts()
  await store.fetchKLines(symbols)
  await store.fetchConfig()
  await store.fetchAlerts()
  
  setInterval(async () => {
    await store.fetchPrices()
    await store.fetchKLines(symbols)
    await store.fetchAlerts()
  }, 5000)
})
</script>

<style scoped>
.dashboard {
  max-width: 1600px;
  margin: 0 auto;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  margin-bottom: 2rem;
}

.stat-card {
  background: #1a1f2e;
  border: 1px solid #2a3042;
  border-radius: 8px;
  padding: 1.5rem;
}

.stat-label {
  color: #848e9c;
  font-size: 0.875rem;
  margin-bottom: 0.5rem;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 600;
  color: #e0e6ed;
}

.stat-value.positive, .positive {
  color: #0ecb81 !important;
}

.stat-value.negative, .negative {
  color: #f6465d !important;
}

.prices-section,
.instances-section {
  margin-bottom: 2rem;
}

h2 {
  color: #e0e6ed;
  margin-bottom: 1rem;
  font-size: 1.25rem;
}

.prices-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.price-card {
  background: #1a1f2e;
  border: 1px solid #2a3042;
  border-radius: 8px;
  padding: 1rem;
  cursor: pointer;
  transition: all 0.2s;
}

.price-card:hover {
  border-color: #f0b90b;
  transform: translateY(-2px);
}

.price-symbol {
  color: #f0b90b;
  font-size: 1rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.kline-data {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.kline-row {
  display: flex;
  justify-content: space-between;
  font-size: 0.8rem;
}

.kline-row .label {
  color: #848e9c;
}

.kline-row .value {
  color: #e0e6ed;
}

.kline-row .value.high {
  color: #0ecb81;
}

.kline-row .value.low {
  color: #f6465d;
}

.kline-row .value.price-up {
  color: #0ecb81;
}

.kline-row .value.price-down {
  color: #f6465d;
}

.kline-row .value.td-counting {
  color: #f0b90b;
}

.kline-row .value.td-buy,
.kline-row .value.td-sell {
  color: #f6465d;
  font-weight: bold;
}

.mode-toggle {
  min-width: 180px;
}

.mode-switch {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  justify-content: center;
}

.mode-switch span {
  color: #848e9c;
  font-size: 0.875rem;
}

.mode-switch span.active {
  color: #f0b90b;
  font-weight: 600;
}

.kline-loading {
  color: #848e9c;
  font-size: 0.875rem;
  text-align: center;
  padding: 1rem;
}

.click-hint {
  color: #f0b90b;
  font-size: 0.75rem;
  text-align: center;
  margin-top: 0.5rem;
  opacity: 0;
  transition: opacity 0.2s;
}

.price-card:hover .click-hint {
  opacity: 1;
}

.symbol-link {
  color: #f0b90b;
  cursor: pointer;
  font-weight: 600;
}

.symbol-link:hover {
  text-decoration: underline;
}

:deep(.el-table) {
  --el-table-bg-color: #1a1f2e;
  --el-table-tr-bg-color: #1a1f2e;
  --el-table-header-bg-color: #1f2535;
  --el-table-row-hover-bg-color: #252a3d;
  --el-table-border-color: #2a3042;
  --el-table-text-color: #e0e6ed;
  --el-table-header-text-color: #848e9c;
}

:deep(.el-table th.el-table__cell) {
  background-color: #1f2535 !important;
}

:deep(.el-table__body-wrapper tbody tr) {
  background-color: #1a1f2e !important;
}

:deep(.el-table__body-wrapper tbody tr:hover) {
  background-color: #252a3d !important;
}

:deep(.el-dialog) {
  --el-dialog-bg-color: #1a1f2e;
}
</style>
