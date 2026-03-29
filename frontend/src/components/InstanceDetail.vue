<template>
  <div class="instance-detail">
    <el-table :data="events" style="width: 100%" :max-height="600" v-loading="loading">
      <el-table-column prop="createdAtUtc" label="时间" width="200">
        <template #default="{ row }">
          {{ formatTime(row.createdAtUtc) }}
        </template>
      </el-table-column>
      <el-table-column prop="instanceId" label="实例" width="70" />
      <el-table-column prop="cycleId" label="周期" width="70" />
      <el-table-column prop="event" label="事件" width="120">
        <template #default="{ row }">
          <el-tag :type="getEventTagType(row.event)" size="small">{{ getEventLabel(row.event) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="price" label="价格" width="130">
        <template #default="{ row }">{{ formatPrice(row.price, row.symbol) }}</template>
      </el-table-column>
      <el-table-column prop="baseQty" label="数量" width="140">
        <template #default="{ row }">{{ formatQty(row.baseQty) }}</template>
      </el-table-column>
      <el-table-column prop="quoteAmount" label="金额" width="130">
        <template #default="{ row }">{{ formatQty(row.quoteAmount) }}</template>
      </el-table-column>
      <el-table-column prop="buyPxRef" label="参考买入价" width="120">
        <template #default="{ row }">{{ formatPrice(row.buyPxRef, row.symbol) }}</template>
      </el-table-column>
      <el-table-column prop="costRef" label="参考成本" width="120">
        <template #default="{ row }">{{ formatQty(row.costRef) }}</template>
      </el-table-column>
      <el-table-column prop="pnl" label="盈亏" width="110">
        <template #default="{ row }">
          <span :class="row.pnl > 0 ? 'positive' : row.pnl < 0 ? 'negative' : ''">
            {{ row.pnl > 0 ? '+' : '' }}{{ formatQty(row.pnl) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="pnlPct" label="盈亏%" width="80">
        <template #default="{ row }">
          <span :class="row.pnlPct > 0 ? 'positive' : row.pnlPct < 0 ? 'negative' : ''">
            {{ row.pnlPct }}%
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="note" label="备注" min-width="200" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { compoundApi } from '../api/compound'

const props = defineProps({
  symbol: {
    type: String,
    required: true
  },
  instanceId: {
    type: Number,
    required: true
  },
  isSimulation: {
    type: Boolean,
    default: true
  }
})

const loading = ref(false)
const events = ref([])

function formatTime(time) {
  if (!time) return '-'
  if (Array.isArray(time)) {
    const d = new Date(Date.UTC(time[0], time[1] - 1, time[2], time[3], time[4], time[5]))
    return d.toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
  }
  const d = new Date(time)
  return d.toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
}

function getDecimalPlaces(symbol) {
  const highPriceSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT']
  if (highPriceSymbols.includes(symbol)) return 2
  const midPriceSymbols = ['ADAUSDT']
  if (midPriceSymbols.includes(symbol)) return 4
  return 8
}

function formatPrice(value, symbol) {
  if (!value || value === '0' || value === 0) return '-'
  const decimals = getDecimalPlaces(symbol)
  return Number(value).toFixed(decimals)
}

function formatQty(value) {
  if (!value || value === '0' || value === 0) return '-'
  return Number(value).toFixed(4)
}

function getEventTagType(event) {
  const typeMap = {
    'DEPOSIT_ALLOC': 'primary',
    'BUY_OPEN': 'success',
    'SELL_STEP': 'warning',
    'SELL_CYCLE': 'danger',
    'BUY_REBUY': 'info',
    'WAIT_REENTRY': 'warning',
    'TAKE_PROFIT': 'danger',
    'REBUY_COMPOUND': 'success'
  }
  return typeMap[event] || 'info'
}

function getEventLabel(event) {
  const labelMap = {
    'DEPOSIT_ALLOC': '分配充值',
    'BUY_OPEN': '开仓买入',
    'SELL_STEP': '分步卖出',
    'SELL_CYCLE': '周期卖出',
    'BUY_REBUY': '买入补仓',
    'WAIT_REENTRY': '等待重新入场',
    'TAKE_PROFIT': '止盈平仓',
    'REBUY_COMPOUND': '复利开仓'
  }
  return labelMap[event] || event
}

async function fetchEvents() {
  loading.value = true
  try {
    const allEvents = await compoundApi.getEventHistory(props.symbol, props.isSimulation, 500)
    
    const filtered = allEvents
      .filter(e => e.instanceId === props.instanceId)
      .reverse()
    
    const mapped = filtered.map((e, idx, arr) => {
      let buyPxRef = null
      let costRef = null
      let pnl = null
      let pnlPct = null
      
      if (e.event === 'BUY_OPEN' || e.event === 'BUY_REBUY' || e.event === 'REBUY_COMPOUND') {
        for (let j = idx + 1; j < arr.length; j++) {
          const nextEvent = arr[j]
          if (nextEvent.event === 'DEPOSIT_ALLOC') continue
          if (nextEvent.event === 'TAKE_PROFIT' || nextEvent.event === 'SELL_STEP' || nextEvent.event === 'SELL_CYCLE') {
            buyPxRef = e.price
            costRef = Number(e.quoteAmount)
            const sellQuote = Number(nextEvent.quoteAmount)
            const buyFee = costRef * 0.001
            const sellFee = sellQuote * 0.001
            pnl = sellQuote - costRef - buyFee - sellFee
            pnlPct = costRef > 0 ? ((pnl / costRef) * 100).toFixed(2) : '0.00'
            break
          }
          break
        }
      }
      
      return {
        ...e,
        buyPxRef: buyPxRef,
        costRef: costRef,
        pnl: pnl,
        pnlPct: pnlPct
      }
    }).reverse()
    
    events.value = mapped
  } catch (e) {
    console.error('Failed to fetch events:', e)
  } finally {
    loading.value = false
  }
}

watch(() => [props.symbol, props.instanceId, props.isSimulation], () => {
  fetchEvents()
}, { immediate: true })
</script>

<style scoped>
.instance-detail {
  width: 100%;
}

.positive {
  color: #0ecb81 !important;
}

.negative {
  color: #f6465d !important;
}
</style>
