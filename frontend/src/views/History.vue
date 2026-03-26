<template>
  <div class="history-view">
    <h2>历史记录</h2>
    
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="操作记录" name="events">
        <div class="filter-bar">
          <el-select v-model="eventSymbol" placeholder="全部交易对" clearable @change="fetchEventHistory">
            <el-option label="全部" value="" />
            <el-option v-for="sym in symbols" :key="sym" :label="sym" :value="sym" />
          </el-select>
        </div>
        <el-table :data="events" stripe style="width: 100%" :max-height="600">
          <el-table-column prop="createdAtUtc" label="时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.createdAtUtc) }}
            </template>
          </el-table-column>
          <el-table-column prop="symbol" label="交易对" width="120" />
          <el-table-column prop="instanceId" label="实例" width="80">
            <template #default="{ row }">#{{ row.instanceId }}</template>
          </el-table-column>
          <el-table-column prop="cycleId" label="周期" width="80" />
          <el-table-column prop="event" label="事件" width="120">
            <template #default="{ row }">
              <el-tag :type="getEventTagType(row.event)">{{ getEventLabel(row.event) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="price" label="价格" width="150">
            <template #default="{ row }">{{ formatPrice(row.price, row.symbol) }}</template>
          </el-table-column>
          <el-table-column prop="baseQty" label="数量" width="150">
            <template #default="{ row }">{{ formatQty(row.baseQty) }}</template>
          </el-table-column>
          <el-table-column prop="quoteAmount" label="金额(USDT)" width="150">
            <template #default="{ row }">{{ formatQty(row.quoteAmount) }}</template>
          </el-table-column>
          <el-table-column prop="note" label="备注" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="委托记录" name="orders">
        <div class="filter-bar">
          <el-select v-model="orderSymbol" placeholder="全部交易对" clearable @change="fetchOrderHistory">
            <el-option label="全部" value="" />
            <el-option v-for="sym in symbols" :key="sym" :label="sym" :value="sym" />
          </el-select>
        </div>
        <el-table :data="orders" stripe style="width: 100%" :max-height="600">
          <el-table-column prop="createdAtUtc" label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.createdAtUtc) }}
            </template>
          </el-table-column>
          <el-table-column prop="updatedAtUtc" label="更新时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.updatedAtUtc) }}
            </template>
          </el-table-column>
          <el-table-column prop="symbol" label="交易对" width="120" />
          <el-table-column prop="instanceId" label="实例" width="80">
            <template #default="{ row }">#{{ row.instanceId }}</template>
          </el-table-column>
          <el-table-column prop="cycleId" label="周期" width="80" />
          <el-table-column prop="isOpen" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isOpen ? 'success' : 'info'">{{ row.isOpen ? '持仓中' : '已平仓' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="baseQty" label="持仓数量" width="150">
            <template #default="{ row }">{{ formatQty(row.baseQty) }}</template>
          </el-table-column>
          <el-table-column prop="cycleStartPrice" label="周期起始价" width="150">
            <template #default="{ row }">{{ formatPrice(row.cycleStartPrice, row.symbol) }}</template>
          </el-table-column>
          <el-table-column prop="quoteAmount" label="委托金额" width="150">
            <template #default="{ row }">{{ formatQty(row.quoteAmount) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="logs">
        <div class="filter-bar">
          <el-button @click="clearLogs">清空日志</el-button>
        </div>
        <div class="log-container">
          <div v-for="(log, idx) in logs" :key="idx" class="log-item" :class="getLogClass(log)">
            <span class="log-time">{{ log.time }}</span>
            <span class="log-type">{{ log.type }}</span>
            <span class="log-msg">{{ log.message }}</span>
          </div>
          <div v-if="logs.length === 0" class="no-logs">暂无操作日志</div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const activeTab = ref('events')
const symbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']

const eventSymbol = ref('')
const orderSymbol = ref('')
const events = ref([])
const orders = ref([])
const logs = ref(JSON.parse(localStorage.getItem('compound_logs') || '[]'))

function formatTime(time) {
  if (!time) return '-'
  if (Array.isArray(time)) {
    return new Date(time[0], time[1] - 1, time[2], time[3], time[4], time[5]).toLocaleString('zh-CN')
  }
  return new Date(time).toLocaleString('zh-CN')
}

function getDecimalPlaces(symbol) {
  const highPriceSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT']
  if (highPriceSymbols.includes(symbol)) return 2
  const midPriceSymbols = ['ADAUSDT']
  if (midPriceSymbols.includes(symbol)) return 4
  return 8
}

function formatPrice(value, symbol) {
  if (!value || value === '0') return '-'
  const decimals = getDecimalPlaces(symbol)
  return Number(value).toFixed(decimals)
}

function formatQty(value) {
  if (!value) return '-'
  return Number(value).toFixed(6)
}

function getEventTagType(event) {
  const typeMap = {
    'DEPOSIT_ALLOC': 'primary',
    'BUY_OPEN': 'success',
    'SELL_STEP': 'warning',
    'SELL_CYCLE': 'danger',
    'BUY_REBUY': 'info',
    'WAIT_REENTRY': 'warning'
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
    'WAIT_REENTRY': '等待重新入场'
  }
  return labelMap[event] || event
}

function getLogClass(log) {
  if (log.type.includes('成功')) return 'log-success'
  if (log.type.includes('失败') || log.type.includes('错误')) return 'log-error'
  if (log.type.includes('充值')) return 'log-deposit'
  return ''
}

async function fetchEventHistory() {
  try {
    events.value = await compoundApi.getEventHistory(eventSymbol.value || null, true, 200)
  } catch (e) {
    ElMessage.error('获取操作记录失败: ' + e.message)
  }
}

async function fetchOrderHistory() {
  try {
    orders.value = await compoundApi.getOrderHistory(orderSymbol.value || null, true, 200)
  } catch (e) {
    ElMessage.error('获取委托记录失败: ' + e.message)
  }
}

function clearLogs() {
  logs.value = []
  localStorage.removeItem('compound_logs')
  ElMessage.success('日志已清空')
}

onMounted(() => {
  fetchEventHistory()
  fetchOrderHistory()
})
</script>

<style scoped>
.history-view {
  max-width: 1400px;
  margin: 0 auto;
}

h2 {
  color: #e0e6ed;
  margin-bottom: 1rem;
}

.filter-bar {
  margin-bottom: 1rem;
  display: flex;
  gap: 1rem;
}

.log-container {
  background: #0a0e17;
  border-radius: 8px;
  padding: 1rem;
  max-height: 500px;
  overflow-y: auto;
}

.log-item {
  display: flex;
  gap: 1rem;
  padding: 0.5rem;
  border-radius: 4px;
  margin-bottom: 0.5rem;
  font-family: monospace;
  font-size: 0.85rem;
}

.log-item.log-success {
  background: rgba(14, 203, 129, 0.1);
  color: #0ecb81;
}

.log-item.log-error {
  background: rgba(246, 70, 93, 0.1);
  color: #f6465d;
}

.log-item.log-deposit {
  background: rgba(240, 185, 11, 0.1);
  color: #f0b90b;
}

.log-time {
  color: #848e9c;
  flex-shrink: 0;
}

.log-type {
  color: #f0b90b;
  flex-shrink: 0;
  min-width: 80px;
}

.log-msg {
  color: #e0e6ed;
}

.no-logs {
  color: #848e9c;
  text-align: center;
  padding: 2rem;
}
</style>
