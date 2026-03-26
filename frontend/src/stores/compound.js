import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { compoundApi } from '../api/compound'

export const useCompoundStore = defineStore('compound', () => {
  const instances = ref([])
  const prices = ref({})
  const klines = ref({})
  const accounts = ref({})
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

  async function fetchInstances(symbol = null) {
    try {
      isLoading.value = true
      instances.value = await compoundApi.getInstances(symbol, true)
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
      await compoundApi.deposit(asset, amount, true)
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
      const result = await compoundApi.executeTick(symbols, true)
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
        const account = await compoundApi.getAccount(asset, true)
        accounts.value[asset] = account
      } catch (e) {
        accounts.value[asset] = { asset, freeBalance: '0', lockedBalance: '0' }
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
      await compoundApi.updateConfig(key, value, true)
      config.value[key] = value
    } catch (e) {
      lastError.value = e.message
      throw e
    }
  }

  async function fetchConfig() {
    try {
      const configs = await compoundApi.getConfig(true)
      for (const c of configs) {
        config.value[c.configKey] = c.configValue
      }
    } catch (e) {
      console.error('fetchConfig error:', e)
    }
  }

  return {
    instances,
    prices,
    klines,
    accounts,
    config,
    isLoading,
    lastError,
    openInstances,
    totalPnL,
    fetchInstances,
    fetchPrices,
    fetchKLine,
    fetchKLines,
    deposit,
    executeTick,
    fetchAccounts,
    subscribePrices,
    updateConfig,
    fetchConfig
  }
})
