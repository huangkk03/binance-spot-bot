import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { compoundApi } from '../api/compound'

export const useCompoundStore = defineStore('compound', () => {
  const isSimulation = ref(true)

  let ws = null
  let wsReconnectTimer = null

  function initWebSocket() {
    if (ws) return
    
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/frontend`
    
    ws = new WebSocket(wsUrl)
    
    ws.onopen = () => {
      console.log('Frontend WebSocket connected')
      if (wsReconnectTimer) {
        clearTimeout(wsReconnectTimer)
        wsReconnectTimer = null
      }
    }
    
    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        if (msg.type === 'PRICE_UPDATE') {
          prices.value[msg.data.symbol] = msg.data.price
        }
      } catch (e) {
        console.error('Failed to parse WS message', e)
      }
    }
    
    ws.onclose = () => {
      console.log('Frontend WebSocket disconnected, reconnecting...')
      ws = null
      wsReconnectTimer = setTimeout(initWebSocket, 5000)
    }
    
    ws.onerror = (error) => {
      console.error('Frontend WebSocket error', error)
      ws.close()
    }
  }

  async function loadModeFromServer() {
    try {
      const result = await compoundApi.getCurrentMode()
      isSimulation.value = result.isSimulation
    } catch (e) {
      console.error('Failed to load mode from server:', e)
    }
  }
  const instances = ref([])
  const prices = ref({})
  const klines = ref({})
  const accounts = ref({})
  const alerts = ref({})
  const config = ref({
    stepPct: '0.01',
    cyclePct: '0.05',
    quoteReserve: '10',
    maxOrdersPerTick: '5'
  })
  const isLoading = ref(false)
  const lastError = ref(null)

  const openInstances = computed(() => instances.value.filter(i => i.isOpen))
  const totalPnL = computed(() => {
    let total = 0
    for (const inst of instances.value) {
      if (inst.isOpen && inst.cycleStartPrice && Number(inst.cycleStartPrice) > 0) {
        const currentPrice = prices.value[inst.symbol] || Number(inst.lastActionPrice) || 0
        const pnl = (currentPrice - Number(inst.cycleStartPrice)) * Number(inst.baseQty)
        total += pnl
      }
    }
    return total
  })

  async function setSimulationMode(sim) {
    isSimulation.value = sim
    try {
      await compoundApi.setCurrentMode(sim)
    } catch (e) {
      console.error('Failed to save mode to server:', e)
    }
    fetchInstances()
    fetchAccounts()
    fetchConfig()
  }

  async function fetchInstances(symbol = null) {
    try {
      isLoading.value = true
      instances.value = await compoundApi.getInstances(symbol, isSimulation.value)
    } catch (e) {
      lastError.value = e.message
      console.error('fetchInstances error:', e)
    } finally {
      isLoading.value = false
    }
  }

  async function fetchPrices() {
    try {
      prices.value = await compoundApi.getPrices()
    } catch (e) {
      lastError.value = e.message
      console.error('fetchPrices error:', e)
    }
  }

  async function fetchKLine(symbol) {
    try {
      const data = await compoundApi.getKLine(symbol)
      if (data && data.success) {
        klines.value[symbol] = data
      }
    } catch (e) {
      console.error('fetchKLine error:', e)
    }
  }

  async function fetchKLines(symbols) {
    for (const symbol of symbols) {
      await fetchKLine(symbol)
    }
  }

  async function deposit(asset, amount) {
    try {
      isLoading.value = true
      await compoundApi.deposit(asset, amount, isSimulation.value)
      await fetchAccounts()
    } catch (e) {
      lastError.value = e.message
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function executeTick(symbols) {
    try {
      isLoading.value = true
      const result = await compoundApi.executeTick(symbols, isSimulation.value)
      await fetchInstances()
      await fetchPrices()
      await fetchAccounts()
      return result
    } catch (e) {
      lastError.value = e.message
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function fetchAccounts() {
    const assets = ['USDT', 'BTC', 'ETH', 'BNB', 'ADA', 'DOGE', 'SOL']
    for (const asset of assets) {
      try {
        const account = await compoundApi.getAccount(asset, isSimulation.value)
        accounts.value[asset] = account
      } catch (e) {
        accounts.value[asset] = { asset, freeBalance: '0', lockedBalance: '0', isSimulation: isSimulation.value }
      }
    }
  }

  async function subscribePrices(symbols) {
    for (const symbol of symbols) {
      try {
        await compoundApi.subscribePrice(symbol)
      } catch (e) {
        console.error('subscribePrice error:', e)
      }
    }
  }

  async function updateConfig(key, value) {
    try {
      await compoundApi.updateConfig(key, value, isSimulation.value)
      config.value[key] = value
    } catch (e) {
      lastError.value = e.message
      throw e
    }
  }

  async function fetchConfig() {
    try {
      const configs = await compoundApi.getConfig(isSimulation.value)
      config.value = {}
      for (const c of configs) {
        config.value[c.configKey] = c.configValue
      }
    } catch (e) {
      console.error('fetchConfig error:', e)
    }
  }

  async function fetchAlerts() {
    try {
      const allAlerts = await compoundApi.getAlerts()
      const alertsMap = {}
      for (const alert of allAlerts) {
        const key = `${alert.symbol}_${alert.interval}`
        if (!alertsMap[key] || alert.tdCount > alertsMap[key].tdCount) {
          alertsMap[key] = alert
        }
      }
      alerts.value = alertsMap
    } catch (e) {
      console.error('fetchAlerts error:', e)
    }
  }

  async function triggerAlertScan() {
    try {
      await compoundApi.triggerAlertScan()
      await fetchAlerts()
    } catch (e) {
      console.error('triggerAlertScan error:', e)
    }
  }

  async function fetchApiConfig(key) {
    try {
      return await compoundApi.getApiConfig(key)
    } catch (e) {
      console.error('fetchApiConfig error:', e)
      return { hasValue: false }
    }
  }

  async function saveApiConfig(key, value) {
    try {
      return await compoundApi.saveApiConfig(key, value)
    } catch (e) {
      console.error('saveApiConfig error:', e)
      throw e
    }
  }

  async function testApiConfig(apiKey, apiSecret, testnet, proxyUrl) {
    try {
      return await compoundApi.testApiConfig(apiKey, apiSecret, testnet, proxyUrl)
    } catch (e) {
      console.error('testApiConfig error:', e)
      throw e
    }
  }

  async function fetchAllApiAccounts() {
    try {
      return await compoundApi.getAllApiAccounts()
    } catch (e) {
      console.error('fetchAllApiAccounts error:', e)
      return []
    }
  }

  async function fetchActiveApiAccount() {
    try {
      return await compoundApi.getActiveApiAccount()
    } catch (e) {
      console.error('fetchActiveApiAccount error:', e)
      return { hasActive: false }
    }
  }

  async function createApiAccount(accountData) {
    try {
      return await compoundApi.createApiAccount(accountData)
    } catch (e) {
      console.error('createApiAccount error:', e)
      throw e
    }
  }

  async function updateApiAccount(id, accountData) {
    try {
      return await compoundApi.updateApiAccount(id, accountData)
    } catch (e) {
      console.error('updateApiAccount error:', e)
      throw e
    }
  }

  async function deleteApiAccount(id) {
    try {
      return await compoundApi.deleteApiAccount(id)
    } catch (e) {
      console.error('deleteApiAccount error:', e)
      throw e
    }
  }

  async function activateApiAccount(id) {
    try {
      return await compoundApi.activateApiAccount(id)
    } catch (e) {
      console.error('activateApiAccount error:', e)
      throw e
    }
  }

  async function fetchApiAccountBalances(id) {
    try {
      return await compoundApi.getApiAccountBalances(id)
    } catch (e) {
      console.error('fetchApiAccountBalances error:', e)
      throw e
    }
  }

  async function testApiAccount(accountData) {
    try {
      return await compoundApi.testApiAccount(accountData)
    } catch (e) {
      console.error('testApiAccount error:', e)
      throw e
    }
  }

  return {
    isSimulation,
    instances,
    prices,
    klines,
    accounts,
    alerts,
    config,
    isLoading,
    lastError,
    openInstances,
    totalPnL,
    setSimulationMode,
    fetchInstances,
    fetchPrices,
    fetchKLine,
    fetchKLines,
    deposit,
    executeTick,
    fetchAccounts,
    subscribePrices,
    updateConfig,
    fetchConfig,
    fetchAlerts,
    triggerAlertScan,
    fetchApiConfig,
    saveApiConfig,
    testApiConfig,
    fetchAllApiAccounts,
    fetchActiveApiAccount,
    createApiAccount,
    updateApiAccount,
    deleteApiAccount,
    activateApiAccount,
    fetchApiAccountBalances,
    testApiAccount,
    loadModeFromServer,
    initWebSocket
  }
})
