<template>
  <div class="simulation-console">
    <div class="console-header">
      <h2>交易控制台</h2>
      <div class="mode-indicator">
        <span class="mode-badge" :class="store.isSimulation ? 'simulation' : 'real'">
          {{ store.isSimulation ? '模拟模式' : '真实模式' }}
        </span>
        <el-switch 
          v-model="isSimMode" 
          active-text="模拟" 
          inactive-text="真实"
          @change="handleModeChange"
          style="margin-left: 1rem;"
        />
      </div>
    </div>

    <div class="console-grid">
      <template v-if="store.isSimulation">
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
            <label>选择配置对象</label>
            <select v-model="selectedConfigSymbol" @change="handleConfigSymbolChange">
              <option value="GLOBAL">全局默认 (Global)</option>
              <option v-for="sym in availableSymbols" :key="sym" :value="sym">{{ sym }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>TAKE_PROFIT_PCT (固定止盈点)</label>
            <input type="text" v-model="localConfig.TAKE_PROFIT_PCT" :placeholder="selectedConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>QUOTE_RESERVE (预留金额)</label>
            <input type="text" v-model="localConfig.QUOTE_RESERVE" :placeholder="selectedConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>MAX_ORDERS_PER_TICK (每轮最大订单)</label>
            <input type="text" v-model="localConfig.MAX_ORDERS_PER_TICK" :placeholder="selectedConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>RSI_OVERBOUGHT (RSI超买阈值)</label>
            <input type="text" v-model="localConfig.RSI_OVERBOUGHT" :placeholder="selectedConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>RSI_OVERSOLD (RSI超卖阈值)</label>
            <input type="text" v-model="localConfig.RSI_OVERSOLD" :placeholder="selectedConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>RSI_PERIOD (RSI 报警周期)</label>
            <input type="text" v-model="localConfig.RSI_PERIOD" :placeholder="selectedConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <button @click="handleSaveConfig" class="btn-save">保存配置</button>
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
          <button @click="handleClearData" :disabled="store.isLoading" class="btn-danger">
            清空模拟数据
          </button>
        </div>
      </template>

      <template v-else>
        <div class="panel api-account-panel">
          <h3>API 账户管理</h3>
          <div class="form-group">
            <label>选择账户</label>
            <select v-model="selectedAccountId" @change="handleAccountChange">
              <option value="">-- 新建账户 --</option>
              <option v-for="acc in apiAccounts" :key="acc.id" :value="acc.id">
                {{ acc.accountName }} {{ acc.isActive ? '(当前激活)' : '' }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label>账户名称</label>
            <input type="text" v-model="accountForm.accountName" placeholder="如：我的主账户" />
          </div>
          <div class="form-group">
            <label>API Key</label>
            <input type="password" v-model="accountForm.apiKey" placeholder="输入 API Key" />
          </div>
          <div class="form-group">
            <label>API Secret</label>
            <input type="password" v-model="accountForm.apiSecret" placeholder="输入 API Secret" />
          </div>
          <div class="form-group">
            <label>使用代理</label>
            <el-switch v-model="accountForm.useProxy" active-text="是" inactive-text="否" />
          </div>
          <div class="form-group" v-if="accountForm.useProxy">
            <label>代理地址</label>
            <input type="text" v-model="accountForm.proxyUrl" placeholder="如 http://127.0.0.1:7890" />
          </div>
          <div class="form-group">
            <label>测试网络</label>
            <el-switch v-model="accountForm.testnet" active-text="是" inactive-text="否" />
          </div>
          <div class="btn-group">
            <button @click="handleSaveAccount" class="btn-save">{{ selectedAccountId ? '更新账户' : '创建账户' }}</button>
            <button @click="handleTestAccount" class="btn-test" :disabled="testing">
              {{ testing ? '测试中...' : '测试连接' }}
            </button>
            <button v-if="selectedAccountId" @click="handleDeleteAccount" class="btn-danger">删除</button>
          </div>
          <div v-if="testResult" class="test-result" :class="testResult.success ? 'success' : 'error'">
            <div v-if="testResult.success" class="test-success">
              <div class="test-title">连接成功!</div>
            </div>
            <div v-else class="test-error">
              <div class="test-title">连接失败</div>
              <div v-for="(err, idx) in testResult.errors" :key="idx" class="error-item">{{ err }}</div>
            </div>
          </div>
        </div>

        <div class="panel config-panel">
          <h3>AI 模型配置</h3>
          <div class="form-group">
            <label>API URL</label>
            <input type="text" v-model="aiConfigForm.url" placeholder="例如: https://api.openai.com/v1/chat/completions" />
          </div>
          <div class="form-group">
            <label>API Key</label>
            <input type="password" v-model="aiConfigForm.key" placeholder="输入 AI API Key" />
          </div>
          <div class="form-group">
            <label>模型名称</label>
            <input type="text" v-model="aiConfigForm.model" placeholder="例如: gpt-3.5-turbo" />
          </div>
          <div class="btn-group">
            <button @click="handleSaveAiConfig" class="btn-save">保存 AI 配置</button>
            <button @click="handleTestAiConfig" class="btn-test" :disabled="testingAi">
              {{ testingAi ? '测试中...' : '测试连接' }}
            </button>
          </div>
        </div>

        <div class="panel config-panel">
          <h3>通知配置（微信/邮件）</h3>
          <div class="form-group">
            <label>微信 Webhook URL</label>
            <input type="text" v-model="notifyConfigForm.wechatWebhookUrl" placeholder="例如: https://sctapi.ftqq.com/xxx.send" />
          </div>
          <div class="form-group">
            <label>邮件接收人</label>
            <input type="text" v-model="notifyConfigForm.emailTo" placeholder="例如: user@example.com" />
          </div>
          <div class="form-group">
            <label>SMTP Host</label>
            <input type="text" v-model="notifyConfigForm.smtpHost" placeholder="例如: smtp.qq.com" />
          </div>
          <div class="form-group">
            <label>SMTP Port</label>
            <input type="text" v-model="notifyConfigForm.smtpPort" placeholder="例如: 465 或 587" />
          </div>
          <div class="form-group">
            <label>SMTP 用户名</label>
            <input type="text" v-model="notifyConfigForm.smtpUsername" placeholder="邮箱账号" />
          </div>
          <div class="form-group">
            <label>SMTP 密码/授权码</label>
            <input type="password" v-model="notifyConfigForm.smtpPassword" placeholder="邮箱授权码" />
          </div>
          <div class="btn-group">
            <button @click="handleSaveNotifyConfig" class="btn-save">保存通知配置</button>
            <button @click="handleTestNotifyConfig" class="btn-test" :disabled="testingNotify">
              {{ testingNotify ? '测试中...' : '测试通知' }}
            </button>
          </div>
        </div>

        <div class="panel config-panel">
          <h3>真实交易策略参数</h3>
          <div class="form-group">
            <label>选择配置对象</label>
            <select v-model="selectedRealConfigSymbol" @change="handleRealConfigSymbolChange">
              <option value="GLOBAL">全局默认 (Global)</option>
              <option v-for="sym in availableSymbols" :key="sym" :value="sym">{{ sym }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>TAKE_PROFIT_PCT (固定止盈点)</label>
            <input type="text" v-model="localRealConfig.TAKE_PROFIT_PCT" :placeholder="selectedRealConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>QUOTE_RESERVE (预留金额)</label>
            <input type="text" v-model="localRealConfig.QUOTE_RESERVE" :placeholder="selectedRealConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>MAX_ORDERS_PER_TICK (每轮最大订单)</label>
            <input type="text" v-model="localRealConfig.MAX_ORDERS_PER_TICK" :placeholder="selectedRealConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>RSI_OVERBOUGHT (RSI超买阈值)</label>
            <input type="text" v-model="localRealConfig.RSI_OVERBOUGHT" :placeholder="selectedRealConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>RSI_OVERSOLD (RSI超卖阈值)</label>
            <input type="text" v-model="localRealConfig.RSI_OVERSOLD" :placeholder="selectedRealConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <div class="form-group">
            <label>RSI_PERIOD (RSI 报警周期)</label>
            <input type="text" v-model="localRealConfig.RSI_PERIOD" :placeholder="selectedRealConfigSymbol !== 'GLOBAL' ? '留空则使用全局默认' : ''" />
          </div>
          <button @click="handleSaveRealConfig" class="btn-save">保存配置</button>
        </div>

        <div class="panel balances-panel">
          <h3>现货账户资产</h3>
          <div class="balances-info" v-if="activeAccount">
            <div class="account-name">账户: {{ activeAccount.accountName }}</div>
            <div class="network-type">{{ activeAccount.testnet ? '测试网络' : '主网' }}</div>
          </div>
          <div class="balances-info" v-else>
            <div class="no-account">请先创建或选择一个API账户</div>
          </div>
          <div class="balances-list" v-if="balances.length > 0">
            <div v-for="bal in balances" :key="bal.asset" class="balance-item">
              <span class="asset-name">{{ bal.asset }}</span>
              <span class="balance-values">
                可用: <span class="free">{{ formatNumber(bal.free) }}</span>
                锁定: <span class="locked">{{ formatNumber(bal.locked) }}</span>
                合计: <span class="total">{{ formatNumber(bal.total) }}</span>
              </span>
            </div>
          </div>
          <div class="no-balances" v-else-if="activeAccount">
            <div>暂无资产</div>
          </div>
          <button @click="handleRefreshBalances" class="btn-refresh" :disabled="loadingBalances">
            {{ loadingBalances ? '加载中...' : '刷新余额' }}
          </button>
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
          <div class="form-group">
            <label>报价金额 (USDT)</label>
            <input type="number" v-model="quoteAmount" placeholder="默认使用配置值" />
          </div>
          <button @click="handleRealTick" :disabled="store.isLoading || !activeAccount || selectedSymbols.length === 0" class="btn-execute">
            {{ store.isLoading ? '执行中...' : '执行 Tick' }}
          </button>
        </div>
      </template>

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
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const store = useCompoundStore()

const availableSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']
const selectedSymbols = ref(['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT'])
const depositAsset = ref('USDT')
const depositAmount = ref(100)
const localLogs = ref([])
const selectedConfigSymbol = ref('GLOBAL')
const selectedRealConfigSymbol = ref('GLOBAL')
const localConfig = ref({
  TAKE_PROFIT_PCT: '0.03',
  QUOTE_RESERVE: '10',
  MAX_ORDERS_PER_TICK: '5',
  RSI_OVERBOUGHT: '80',
  RSI_OVERSOLD: '20',
  RSI_PERIOD: '14'
})
const localRealConfig = ref({
  TAKE_PROFIT_PCT: '0.03',
  QUOTE_RESERVE: '10',
  MAX_ORDERS_PER_TICK: '5',
  RSI_OVERBOUGHT: '80',
  RSI_OVERSOLD: '20',
  RSI_PERIOD: '14'
})
const fullRealConfig = ref({})
const isSimMode = ref(store.isSimulation)

const apiAccounts = ref([])
const selectedAccountId = ref('')
const activeAccount = ref(null)
const accountForm = ref({
  accountName: '',
  apiKey: '',
  apiSecret: '',
  useProxy: false,
  proxyUrl: '',
  testnet: true
})
const aiConfigForm = ref({
  url: '',
  key: '',
  model: ''
})
const notifyConfigForm = ref({
  wechatWebhookUrl: '',
  emailTo: '',
  smtpHost: '',
  smtpPort: '465',
  smtpUsername: '',
  smtpPassword: ''
})
const testing = ref(false)
const testingAi = ref(false)
const testingNotify = ref(false)
const testResult = ref(null)
const balances = ref([])
const loadingBalances = ref(false)
const quoteAmount = ref(null)

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

async function handleRealTick() {
  if (!activeAccount.value) {
    ElMessage.warning('请先选择并激活一个API账户')
    return
  }
  if (selectedSymbols.value.length === 0) {
    ElMessage.warning('请至少选择一个交易对')
    return
  }
  
  try {
    store.isLoading = true
    const result = await compoundApi.executeRealTick(selectedSymbols.value, quoteAmount.value)
    if (result.success) {
      saveLog('真实Tick', `执行成功: ${result.message}`)
      ElMessage.success(result.message)
    } else {
      saveLog('真实Tick', `执行失败: ${result.message}`)
      ElMessage.error(result.message)
    }
  } catch (e) {
    saveLog('真实Tick失败', e.message)
    ElMessage.error('执行失败: ' + e.message)
  } finally {
    store.isLoading = false
  }
}

async function handleClearData() {
  try {
    await compoundApi.clearSimulationData()
    localStorage.removeItem('compound_logs')
    localLogs.value = []
    await store.fetchInstances()
    await store.fetchAccounts()
    ElMessage.success('模拟数据已清空')
    saveLog('清空数据', '所有模拟数据已清空')
  } catch (e) {
    ElMessage.error('清空数据失败: ' + e.message)
  }
}

async function handleSaveConfig() {
  try {
    for (const [baseKey, value] of Object.entries(localConfig.value)) {
      let key;
      if (baseKey.startsWith('RSI_')) {
          key = selectedConfigSymbol.value === 'GLOBAL' ? `${baseKey}_DEFAULT` : `${baseKey}_${selectedConfigSymbol.value}`
      } else {
          key = selectedConfigSymbol.value === 'GLOBAL' ? baseKey : `${baseKey}_${selectedConfigSymbol.value}`
      }
      await store.updateConfig(key, value)
    }
    ElMessage.success('配置已保存')
    saveLog('配置保存', JSON.stringify(localConfig.value))
  } catch (e) {
    ElMessage.error('配置保存失败: ' + e.message)
  }
}

async function handleSaveRealConfig() {
  try {
    for (const [baseKey, value] of Object.entries(localRealConfig.value)) {
      let key;
      if (baseKey.startsWith('RSI_')) {
          key = selectedRealConfigSymbol.value === 'GLOBAL' ? `${baseKey}_DEFAULT` : `${baseKey}_${selectedRealConfigSymbol.value}`
      } else {
          key = selectedRealConfigSymbol.value === 'GLOBAL' ? baseKey : `${baseKey}_${selectedRealConfigSymbol.value}`
      }
      await compoundApi.updateConfig(key, value, false)
      fullRealConfig.value[key] = value
    }
    ElMessage.success('真实交易配置已保存')
    saveLog('真实配置保存', JSON.stringify(localRealConfig.value))
  } catch (e) {
    ElMessage.error('配置保存失败: ' + e.message)
  }
}

function handleConfigSymbolChange() {
  const cfg = store.config
  const suffix = selectedConfigSymbol.value === 'GLOBAL' ? '' : `_${selectedConfigSymbol.value}`
  const rsiSuffix = selectedConfigSymbol.value === 'GLOBAL' ? '_DEFAULT' : `_${selectedConfigSymbol.value}`
  localConfig.value.TAKE_PROFIT_PCT = cfg[`TAKE_PROFIT_PCT${suffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '0.03' : '')
  localConfig.value.QUOTE_RESERVE = cfg[`QUOTE_RESERVE${suffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '10' : '')
  localConfig.value.MAX_ORDERS_PER_TICK = cfg[`MAX_ORDERS_PER_TICK${suffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '5' : '')
  localConfig.value.RSI_OVERBOUGHT = cfg[`RSI_OVERBOUGHT${rsiSuffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '80' : '')
  localConfig.value.RSI_OVERSOLD = cfg[`RSI_OVERSOLD${rsiSuffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '20' : '')
  localConfig.value.RSI_PERIOD = cfg[`RSI_PERIOD${rsiSuffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '14' : '')
}

function handleRealConfigSymbolChange() {
  const suffix = selectedRealConfigSymbol.value === 'GLOBAL' ? '' : `_${selectedRealConfigSymbol.value}`
  const rsiSuffix = selectedRealConfigSymbol.value === 'GLOBAL' ? '_DEFAULT' : `_${selectedRealConfigSymbol.value}`
  localRealConfig.value.TAKE_PROFIT_PCT = fullRealConfig.value[`TAKE_PROFIT_PCT${suffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '0.03' : '')
  localRealConfig.value.QUOTE_RESERVE = fullRealConfig.value[`QUOTE_RESERVE${suffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '10' : '')
  localRealConfig.value.MAX_ORDERS_PER_TICK = fullRealConfig.value[`MAX_ORDERS_PER_TICK${suffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '5' : '')
  localRealConfig.value.RSI_OVERBOUGHT = fullRealConfig.value[`RSI_OVERBOUGHT${rsiSuffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '80' : '')
  localRealConfig.value.RSI_OVERSOLD = fullRealConfig.value[`RSI_OVERSOLD${rsiSuffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '20' : '')
  localRealConfig.value.RSI_PERIOD = fullRealConfig.value[`RSI_PERIOD${rsiSuffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '14' : '')
}

async function loadAiConfig() {
  try {
    const urlRes = await compoundApi.getApiConfig('AI_API_URL')
    const keyRes = await compoundApi.getApiConfig('AI_API_KEY')
    const modelRes = await compoundApi.getApiConfig('AI_API_MODEL')
    
    aiConfigForm.value.url = urlRes.hasValue ? urlRes.value : ''
    aiConfigForm.value.key = keyRes.hasValue ? keyRes.value : ''
    aiConfigForm.value.model = modelRes.hasValue ? modelRes.value : ''
  } catch (e) {
    console.error('Failed to load AI config:', e)
  }
}

async function loadNotifyConfig() {
  try {
    const wechatRes = await compoundApi.getApiConfig('WECHAT_WEBHOOK_URL')
    const emailToRes = await compoundApi.getApiConfig('EMAIL_TO')
    const smtpHostRes = await compoundApi.getApiConfig('EMAIL_SMTP_HOST')
    const smtpPortRes = await compoundApi.getApiConfig('EMAIL_SMTP_PORT')
    const smtpUsernameRes = await compoundApi.getApiConfig('EMAIL_SMTP_USERNAME')
    const smtpPasswordRes = await compoundApi.getApiConfig('EMAIL_SMTP_PASSWORD')

    notifyConfigForm.value.wechatWebhookUrl = wechatRes.hasValue ? wechatRes.value : ''
    notifyConfigForm.value.emailTo = emailToRes.hasValue ? emailToRes.value : ''
    notifyConfigForm.value.smtpHost = smtpHostRes.hasValue ? smtpHostRes.value : ''
    notifyConfigForm.value.smtpPort = smtpPortRes.hasValue ? smtpPortRes.value : '465'
    notifyConfigForm.value.smtpUsername = smtpUsernameRes.hasValue ? smtpUsernameRes.value : ''
    notifyConfigForm.value.smtpPassword = smtpPasswordRes.hasValue ? smtpPasswordRes.value : ''
  } catch (e) {
    console.error('Failed to load notify config:', e)
  }
}

async function handleSaveAiConfig() {
  try {
    await compoundApi.saveApiConfig('AI_API_URL', aiConfigForm.value.url)
    await compoundApi.saveApiConfig('AI_API_KEY', aiConfigForm.value.key)
    await compoundApi.saveApiConfig('AI_API_MODEL', aiConfigForm.value.model)
    ElMessage.success('AI 配置已保存')
    saveLog('修改配置', '保存 AI 配置成功')
  } catch (e) {
    ElMessage.error('保存 AI 配置失败: ' + e.message)
  }
}

async function handleTestAiConfig() {
  if (!aiConfigForm.value.key) {
    ElMessage.warning('请输入 AI API Key')
    return
  }
  
  testingAi.value = true
  try {
    const result = await compoundApi.testAiConfig(
      aiConfigForm.value.url,
      aiConfigForm.value.key,
      aiConfigForm.value.model
    )
    
    if (result.success) {
      ElMessage.success(result.message || 'AI 接口连接成功')
      saveLog('AI测试', 'AI 接口连接成功')
    } else {
      ElMessage.error(result.error || 'AI 接口连接失败')
      saveLog('AI测试', 'AI 接口连接失败: ' + result.error)
    }
  } catch (e) {
    ElMessage.error('测试异常: ' + e.message)
  } finally {
    testingAi.value = false
  }
}

async function handleSaveNotifyConfig() {
  try {
    await compoundApi.saveApiConfig('WECHAT_WEBHOOK_URL', notifyConfigForm.value.wechatWebhookUrl)
    await compoundApi.saveApiConfig('EMAIL_TO', notifyConfigForm.value.emailTo)
    await compoundApi.saveApiConfig('EMAIL_SMTP_HOST', notifyConfigForm.value.smtpHost)
    await compoundApi.saveApiConfig('EMAIL_SMTP_PORT', notifyConfigForm.value.smtpPort)
    await compoundApi.saveApiConfig('EMAIL_SMTP_USERNAME', notifyConfigForm.value.smtpUsername)
    await compoundApi.saveApiConfig('EMAIL_SMTP_PASSWORD', notifyConfigForm.value.smtpPassword)
    ElMessage.success('通知配置已保存')
    saveLog('通知配置', '保存通知配置成功')
  } catch (e) {
    ElMessage.error('保存通知配置失败: ' + e.message)
  }
}

async function handleTestNotifyConfig() {
  testingNotify.value = true
  try {
    const result = await compoundApi.testNotification(
      '交易通知测试',
      '这是一条测试通知，用于验证微信和邮件配置是否可用。'
    )
    if (result.success) {
      ElMessage.success(result.message || '测试通知已发送')
      saveLog('通知测试', '测试通知已发送')
    } else {
      ElMessage.error(result.error || '测试通知失败')
      saveLog('通知测试', '测试通知失败: ' + (result.error || '未知错误'))
    }
  } catch (e) {
    ElMessage.error('测试通知失败: ' + e.message)
  } finally {
    testingNotify.value = false
  }
}

async function handleModeChange(val) {
  isSimMode.value = val
  await store.setSimulationMode(val)
  ElMessage.success(val ? '已切换到模拟模式' : '已切换到真实模式')
  loadLogs()
  if (!val) {
    await loadApiAccounts()
    const cfg = fullRealConfig.value
    const suffix = selectedRealConfigSymbol.value === 'GLOBAL' ? '' : `_${selectedRealConfigSymbol.value}`
    const rsiSuffix = selectedRealConfigSymbol.value === 'GLOBAL' ? '_DEFAULT' : `_${selectedRealConfigSymbol.value}`
    localRealConfig.value.TAKE_PROFIT_PCT = cfg[`TAKE_PROFIT_PCT${suffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '0.03' : '')
    localRealConfig.value.QUOTE_RESERVE = cfg[`QUOTE_RESERVE${suffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '10' : '')
    localRealConfig.value.MAX_ORDERS_PER_TICK = cfg[`MAX_ORDERS_PER_TICK${suffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '5' : '')
    localRealConfig.value.RSI_OVERBOUGHT = cfg[`RSI_OVERBOUGHT${rsiSuffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '80' : '')
    localRealConfig.value.RSI_OVERSOLD = cfg[`RSI_OVERSOLD${rsiSuffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '20' : '')
    localRealConfig.value.RSI_PERIOD = cfg[`RSI_PERIOD${rsiSuffix}`] || (selectedRealConfigSymbol.value === 'GLOBAL' ? '14' : '')
  } else {
    const cfg = store.config
    const suffix = selectedConfigSymbol.value === 'GLOBAL' ? '' : `_${selectedConfigSymbol.value}`
    const rsiSuffix = selectedConfigSymbol.value === 'GLOBAL' ? '_DEFAULT' : `_${selectedConfigSymbol.value}`
    localConfig.value.TAKE_PROFIT_PCT = cfg[`TAKE_PROFIT_PCT${suffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '0.03' : '')
    localConfig.value.QUOTE_RESERVE = cfg[`QUOTE_RESERVE${suffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '10' : '')
    localConfig.value.MAX_ORDERS_PER_TICK = cfg[`MAX_ORDERS_PER_TICK${suffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '5' : '')
    localConfig.value.RSI_OVERBOUGHT = cfg[`RSI_OVERBOUGHT${rsiSuffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '80' : '')
    localConfig.value.RSI_OVERSOLD = cfg[`RSI_OVERSOLD${rsiSuffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '20' : '')
    localConfig.value.RSI_PERIOD = cfg[`RSI_PERIOD${rsiSuffix}`] || (selectedConfigSymbol.value === 'GLOBAL' ? '14' : '')
  }
}

async function loadApiAccounts() {
  try {
    apiAccounts.value = await store.fetchAllApiAccounts()
    const activeResult = await store.fetchActiveApiAccount()
    if (activeResult.hasActive) {
      activeAccount.value = activeResult.account
      selectedAccountId.value = activeAccount.value.id
      accountForm.value = {
        accountName: activeAccount.value.accountName,
        apiKey: activeAccount.value.maskedApiKey,
        apiSecret: activeAccount.value.maskedApiSecret,
        useProxy: activeAccount.value.useProxy,
        proxyUrl: activeAccount.value.proxyUrl,
        testnet: activeAccount.value.testnet
      }
      await loadBalances(activeAccount.value.id)
    } else {
      activeAccount.value = null
      selectedAccountId.value = ''
      resetAccountForm()
    }
  } catch (e) {
    console.error('Failed to load API accounts:', e)
  }
}

function resetAccountForm() {
  accountForm.value = {
    accountName: '',
    apiKey: '',
    apiSecret: '',
    useProxy: false,
    proxyUrl: '',
    testnet: true
  }
}

function handleAccountChange() {
  if (selectedAccountId.value) {
    const acc = apiAccounts.value.find(a => a.id === selectedAccountId.value)
    if (acc) {
      accountForm.value = {
        accountName: acc.accountName,
        apiKey: acc.maskedApiKey,
        apiSecret: acc.maskedApiSecret,
        useProxy: acc.useProxy,
        proxyUrl: acc.proxyUrl,
        testnet: acc.testnet
      }
    }
  } else {
    resetAccountForm()
  }
}

async function handleSaveAccount() {
  if (!accountForm.value.accountName) {
    ElMessage.warning('请输入账户名称')
    return
  }
  if (!accountForm.value.apiKey || !accountForm.value.apiSecret) {
    ElMessage.warning('请输入 API Key 和 Secret')
    return
  }
  
  try {
    if (selectedAccountId.value) {
      await store.updateApiAccount(selectedAccountId.value, accountForm.value)
      saveLog('账户更新', `更新账户: ${accountForm.value.accountName}`)
      ElMessage.success('账户已更新')
    } else {
      const result = await store.createApiAccount(accountForm.value)
      saveLog('账户创建', `创建账户: ${accountForm.value.accountName}`)
      ElMessage.success('账户已创建')
      await store.activateApiAccount(result.id)
      saveLog('账户激活', `激活账户: ${accountForm.value.accountName}`)
      await loadApiAccounts()
    }
    await loadApiAccounts()
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message)
  }
}

async function handleTestAccount() {
  if (!accountForm.value.apiKey || !accountForm.value.apiSecret) {
    ElMessage.warning('请输入 API Key 和 Secret')
    return
  }
  
  testing.value = true
  testResult.value = null
  
  try {
    const result = await store.testApiAccount({
      apiKey: accountForm.value.apiKey,
      apiSecret: accountForm.value.apiSecret,
      testnet: accountForm.value.testnet,
      proxyUrl: accountForm.value.useProxy ? accountForm.value.proxyUrl : ''
    })
    testResult.value = result
    
    if (result.success) {
      ElMessage.success('API 连接测试成功')
      saveLog('API测试', 'API 连接成功')
    } else {
      ElMessage.error('API 连接失败')
      saveLog('API测试', 'API 连接失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + e.message)
    testResult.value = { success: false, errors: [e.message] }
  } finally {
    testing.value = false
  }
}

async function handleDeleteAccount() {
  if (!selectedAccountId.value) return
  
  try {
    await store.deleteApiAccount(selectedAccountId.value)
    saveLog('账户删除', `删除账户 ID: ${selectedAccountId.value}`)
    ElMessage.success('账户已删除')
    selectedAccountId.value = ''
    activeAccount.value = null
    resetAccountForm()
    await loadApiAccounts()
  } catch (e) {
    ElMessage.error('删除失败: ' + e.message)
  }
}

async function loadBalances(accountId) {
  if (!accountId) return
  
  loadingBalances.value = true
  try {
    const result = await store.fetchApiAccountBalances(accountId)
    if (result.success && result.account && result.account.balances) {
      balances.value = result.account.balances
    } else {
      balances.value = []
    }
  } catch (e) {
    console.error('Failed to load balances:', e)
    balances.value = []
  } finally {
    loadingBalances.value = false
  }
}

async function handleRefreshBalances() {
  if (activeAccount.value) {
    await loadBalances(activeAccount.value.id)
    saveLog('刷新余额', `刷新账户 ${activeAccount.value.accountName} 余额`)
  }
}

function formatNumber(num) {
  if (!num) return '0'
  const n = parseFloat(num)
  if (isNaN(n)) return '0'
  return n.toFixed(8).replace(/\.?0+$/, '')
}

function handleLogsUpdate() {
  loadLogs()
}

onMounted(async () => {
  loadLogs()
  await store.fetchInstances()
  await store.fetchAccounts()
  await store.fetchConfig()
  const cfg = store.config
  localConfig.value.TAKE_PROFIT_PCT = cfg.TAKE_PROFIT_PCT || '0.03'
  localConfig.value.QUOTE_RESERVE = cfg.QUOTE_RESERVE || '10'
  localConfig.value.MAX_ORDERS_PER_TICK = cfg.MAX_ORDERS_PER_TICK || '5'
  localConfig.value.RSI_OVERBOUGHT = cfg.RSI_OVERBOUGHT_DEFAULT || '80'
  localConfig.value.RSI_OVERSOLD = cfg.RSI_OVERSOLD_DEFAULT || '20'
  localConfig.value.RSI_PERIOD = cfg.RSI_PERIOD_DEFAULT || '14'
  await loadApiAccounts()
  await loadAiConfig()
  await loadNotifyConfig()
  const realResult = await compoundApi.getConfig(false)
  const realCfg = {}
  for (const c of realResult) {
    realCfg[c.configKey] = c.configValue
  }
  fullRealConfig.value = realCfg
  localRealConfig.value.TAKE_PROFIT_PCT = realCfg.TAKE_PROFIT_PCT || '0.03'
  localRealConfig.value.QUOTE_RESERVE = realCfg.QUOTE_RESERVE || '10'
  localRealConfig.value.MAX_ORDERS_PER_TICK = realCfg.MAX_ORDERS_PER_TICK || '5'
  localRealConfig.value.RSI_OVERBOUGHT = realCfg.RSI_OVERBOUGHT_DEFAULT || '80'
  localRealConfig.value.RSI_OVERSOLD = realCfg.RSI_OVERSOLD_DEFAULT || '20'
  localRealConfig.value.RSI_PERIOD = realCfg.RSI_PERIOD_DEFAULT || '14'
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

.mode-badge.real {
  background: rgba(14, 203, 129, 0.1);
  color: #0ecb81;
  border: 1px solid #0ecb81;
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

.btn-save {
  background: #0ecb81;
  color: #000;
}

.btn-save:hover {
  background: #0db573;
}

.btn-test {
  background: #f0b90b;
  color: #000;
}

.btn-test:hover {
  background: #dfa90c;
}

.btn-test:disabled {
  background: #848e9c;
}

.btn-save,
.btn-test,
.btn-danger,
.btn-refresh {
  padding: 0.75rem 1rem;
  border: none;
  border-radius: 4px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 0.5rem;
  margin-right: 0.5rem;
}

.btn-danger {
  background: #f6465d;
  color: #fff;
}

.btn-danger:hover {
  background: #d63d55;
}

.btn-refresh {
  background: #2a3042;
  color: #e0e6ed;
}

.btn-refresh:hover {
  background: #3a4052;
}

.btn-refresh:disabled {
  background: #1a2030;
  cursor: not-allowed;
}

.btn-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1rem;
}

.test-result {
  margin-top: 1rem;
  padding: 1rem;
  border-radius: 4px;
}

.test-result.success {
  background: rgba(14, 203, 129, 0.1);
  border: 1px solid #0ecb81;
}

.test-result.error {
  background: rgba(246, 70, 93, 0.1);
  border: 1px solid #f6465d;
}

.test-title {
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.test-success .test-title {
  color: #0ecb81;
}

.test-error .test-title {
  color: #f6465d;
}

.error-item {
  font-size: 0.875rem;
  color: #f6465d;
}

.balances-info {
  margin-bottom: 1rem;
}

.account-name {
  color: #e0e6ed;
  font-weight: 600;
}

.network-type {
  color: #848e9c;
  font-size: 0.875rem;
  margin-top: 0.25rem;
}

.no-account,
.no-balances {
  color: #848e9c;
  text-align: center;
  padding: 1rem;
}

.balances-list {
  max-height: 300px;
  overflow-y: auto;
}

.balance-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem;
  background: #0a0e17;
  border-radius: 4px;
  margin-bottom: 0.5rem;
}

.asset-name {
  font-weight: 600;
  color: #f0b90b;
}

.balance-values {
  font-size: 0.875rem;
  color: #848e9c;
  font-family: monospace;
}

.balance-values .free {
  color: #0ecb81;
}

.balance-values .locked {
  color: #f6465d;
}

.balance-values .total {
  color: #e0e6ed;
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
