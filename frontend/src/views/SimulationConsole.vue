<template>
  <div class="simulation-console">
    <div class="console-header">
      <h2>模拟控制台</h2>
      <div class="mode-indicator">
        <span class="mode-badge simulation">模拟模式</span>
      </div>
    </div>

    <div class="console-grid">
      <div class="panel deposit-panel">
        <h3>模拟充值</h3>
        <div class="form-group">
          <label>资产</label>
          <select v-model="depositAsset">
            <option value="USDT">USDT</option>
            <option value="BTC">BTC</option>
            <option value="ETH">ETH</option>
          </select>
        </div>
        <div class="form-group">
          <label>金额</label>
          <input type="number" v-model="depositAmount" placeholder="0.00" />
        </div>
        <button @click="handleDeposit" :disabled="store.isLoading" class="btn-primary">
          {{ store.isLoading ? '处理中...' : '充值' }}
        </button>
      </div>

      <div class="panel config-panel">
        <h3>策略参数</h3>
        <div class="form-group">
          <label>STEP_PCT (1% 步进)</label>
          <input type="text" v-model="store.config.stepPct" @change="updateConfig('STEP_PCT', store.config.stepPct)" />
        </div>
        <div class="form-group">
          <label>CYCLE_PCT (5% 周期)</label>
          <input type="text" v-model="store.config.cyclePct" @change="updateConfig('CYCLE_PCT', store.config.cyclePct)" />
        </div>
        <div class="form-group">
          <label>QUOTE_RESERVE (预留金额)</label>
          <input type="text" v-model="store.config.quoteReserve" @change="updateConfig('QUOTE_RESERVE', store.config.quoteReserve)" />
        </div>
        <div class="form-group">
          <label>MAX_ORDERS_PER_TICK (每轮最大订单)</label>
          <input type="text" v-model="store.config.maxOrdersPerTick" @change="updateConfig('MAX_ORDERS_PER_TICK', store.config.maxOrdersPerTick)" />
        </div>
      </div>

      <div class="panel execution-panel">
        <h3>手动执行 Tick</h3>
        <div class="symbols-select">
          <label>选择交易对:</label>
          <div class="checkbox-group">
            <label v-for="sym in availableSymbols" :key="sym">
              <input type="checkbox" v-model="selectedSymbols" :value="sym" />
              {{ sym }}
            </label>
          </div>
        </div>
        <button @click="handleTick" :disabled="store.isLoading || selectedSymbols.length === 0" class="btn-execute">
          {{ store.isLoading ? '执行中...' : '执行 Tick' }}
        </button>
      </div>

      <div class="panel actions-log-panel">
        <h3>最近操作</h3>
        <div class="actions-log">
          <div v-for="(action, idx) in recentActions" :key="idx" class="action-item">
            {{ action }}
          </div>
          <div v-if="recentActions.length === 0" class="no-actions">
            暂无操作记录
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useCompoundStore } from '../stores/compound'
import { ElMessage } from 'element-plus'

const store = useCompoundStore()

const availableSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']
const selectedSymbols = ref(['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT'])
const depositAsset = ref('USDT')
const depositAmount = ref(100)
const localLogs = ref([])

const recentActions = computed(() => {
  return localLogs.value.map(log => `[${log.time}] [${log.type}] ${log.message}`)
})

function loadLogs() {
  localLogs.value = JSON.parse(localStorage.getItem('compound_logs') || '[]')
}

function saveLog(type, message) {
  const logs = JSON.parse(localStorage.getItem('compound_logs') || '[]')
  logs.unshift({
    time: new Date().toLocaleString('zh-CN'),
    type,
    message
  })
  if (logs.length > 500) logs.pop()
  localStorage.setItem('compound_logs', JSON.stringify(logs))
  localLogs.value = logs
}

async function handleDeposit() {
  if (!depositAmount.value || depositAmount.value <= 0) {
    ElMessage.warning('请输入有效金额')
    return
  }
  
  try {
    await store.deposit(depositAsset.value, depositAmount.value)
    saveLog('充值', `${depositAsset.value} 充值 ${depositAmount.value} 成功`)
    ElMessage.success('充值成功')
  } catch (e) {
    saveLog('充值失败', e.message)
    ElMessage.error('充值失败: ' + e.message)
  }
}

async function handleTick() {
  if (selectedSymbols.value.length === 0) {
    ElMessage.warning('请至少选择一个交易对')
    return
  }
  
  try {
    const result = await store.executeTick(selectedSymbols.value)
    if (result && result.actions) {
      for (const action of result.actions) {
        saveLog('Tick执行', action)
      }
    }
    ElMessage.success('Tick 执行成功')
  } catch (e) {
    saveLog('Tick失败', e.message)
    ElMessage.error('Tick 执行失败: ' + e.message)
  }
}

async function updateConfig(key, value) {
  try {
    await store.updateConfig(key, value)
    ElMessage.success('配置已更新')
  } catch (e) {
    ElMessage.error('配置更新失败')
  }
}

function handleLogsUpdate() {
  loadLogs()
}

onMounted(async () => {
  loadLogs()
  await store.fetchInstances()
  await store.fetchAccounts()
  window.addEventListener('logsUpdated', handleLogsUpdate)
})

onUnmounted(() => {
  window.removeEventListener('logsUpdated', handleLogsUpdate)
})
</script>

<style scoped>
.simulation-console {
  max-width: 1400px;
  margin: 0 auto;
}

.console-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.console-header h2 {
  color: #e0e6ed;
  font-size: 1.5rem;
}

.mode-badge {
  padding: 0.5rem 1rem;
  border-radius: 4px;
  font-weight: 600;
  font-size: 0.875rem;
}

.mode-badge.simulation {
  background: rgba(240, 185, 11, 0.1);
  color: #f0b90b;
  border: 1px solid #f0b90b;
}

.console-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.5rem;
}

.panel {
  background: #1a1f2e;
  border: 1px solid #2a3042;
  border-radius: 8px;
  padding: 1.5rem;
}

.panel h3 {
  color: #e0e6ed;
  font-size: 1rem;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid #2a3042;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  color: #848e9c;
  font-size: 0.875rem;
  margin-bottom: 0.5rem;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 0.75rem;
  background: #0a0e17;
  border: 1px solid #2a3042;
  border-radius: 4px;
  color: #e0e6ed;
  font-size: 0.875rem;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #f0b90b;
}

.btn-primary,
.btn-execute {
  width: 100%;
  padding: 0.875rem;
  background: #f0b90b;
  color: #000;
  border: none;
  border-radius: 4px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover,
.btn-execute:hover {
  background: #dfa90c;
}

.btn-primary:disabled,
.btn-execute:disabled {
  background: #848e9c;
  cursor: not-allowed;
}

.symbols-select {
  margin-bottom: 1rem;
}

.symbols-select label {
  display: block;
  color: #848e9c;
  font-size: 0.875rem;
  margin-bottom: 0.5rem;
}

.checkbox-group {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}

.checkbox-group label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #e0e6ed;
  font-size: 0.875rem;
  cursor: pointer;
}

.checkbox-group input {
  accent-color: #f0b90b;
}

.actions-log {
  max-height: 250px;
  overflow-y: auto;
}

.action-item {
  padding: 0.5rem;
  background: #0a0e17;
  border-radius: 4px;
  margin-bottom: 0.5rem;
  font-size: 0.8rem;
  color: #0ecb81;
  font-family: monospace;
}

.no-actions {
  color: #848e9c;
  font-size: 0.875rem;
  text-align: center;
  padding: 2rem;
}
</style>
