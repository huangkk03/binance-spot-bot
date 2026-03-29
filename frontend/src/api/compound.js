import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

export const compoundApi = {
  async deposit(asset, amount, isSimulation = true) {
    const response = await api.post('/deposit', { asset, amount, isSimulation })
    return response.data
  },

  async getAccount(asset, isSimulation = true) {
    const response = await api.get(`/accounts/${asset}`, { params: { isSimulation } })
    return response.data
  },

  async getInstances(symbol = null, isSimulation = true) {
    const params = { isSimulation }
    if (symbol) params.symbol = symbol
    const response = await api.get('/instances', { params })
    return response.data
  },

  async executeTick(symbols, isSimulation = true) {
    const response = await api.post('/tick', symbols, { params: { isSimulation } })
    return response.data
  },

  async executeRealTick(symbols, quoteAmount = null) {
    const params = quoteAmount ? { quoteAmount } : {}
    const response = await api.post('/real-tick', symbols, { params })
    return response.data
  },

  async getPrices() {
    const response = await api.get('/prices')
    return response.data
  },

  async getPrice(symbol) {
    const response = await api.get(`/prices/${symbol}`)
    return response.data
  },

  async getKLine(symbol) {
    const response = await api.get(`/kline/${symbol}`)
    return response.data
  },

  async subscribePrice(symbol) {
    const response = await api.post(`/prices/subscribe/${symbol}`)
    return response.data
  },

  async getConfig(isSimulation = true) {
    const response = await api.get('/config', { params: { isSimulation } })
    return response.data
  },

  async updateConfig(key, value, isSimulation = true) {
    const response = await api.put(`/config/${key}`, { configValue: value, isSimulation })
    return response.data
  },

  async health() {
    const response = await api.get('/health')
    return response.data
  },

  async getEventHistory(symbol = null, isSimulation = true, limit = 100) {
    const params = { isSimulation, limit }
    if (symbol) params.symbol = symbol
    const response = await api.get('/history/events', { params })
    return response.data
  },

  async getOrderHistory(symbol = null, isSimulation = true, limit = 100) {
    const params = { isSimulation, limit }
    if (symbol) params.symbol = symbol
    const response = await api.get('/history/orders', { params })
    return response.data
  },

  async getAlerts(symbol = null, interval = null) {
    const params = {}
    if (symbol) params.symbol = symbol
    if (interval) params.interval = interval
    const response = await api.get('/alerts', { params })
    return response.data
  },

  async getTriggeredAlerts() {
    const response = await api.get('/alerts/triggered')
    return response.data
  },

  async triggerAlertScan() {
    const response = await api.post('/alerts/scan')
    return response.data
  },

  async clearSimulationData() {
    const response = await api.post('/simulation/clear')
    return response.data
  },

  async getApiConfig(key) {
    const response = await api.get(`/api-config/${key}`)
    return response.data
  },

  async saveApiConfig(key, value) {
    const response = await api.put(`/api-config/${key}`, { value })
    return response.data
  },

  async deleteApiConfig(key) {
    const response = await api.delete(`/api-config/${key}`)
    return response.data
  },

  async testApiConfig(apiKey, apiSecret, testnet, proxyUrl) {
    const response = await api.post('/api-config/test', { apiKey, apiSecret, testnet, proxyUrl })
    return response.data
  },

  async getAllApiAccounts() {
    const response = await api.get('/api-accounts')
    return response.data
  },

  async getActiveApiAccount() {
    const response = await api.get('/api-accounts/active')
    return response.data
  },

  async createApiAccount(accountData) {
    const response = await api.post('/api-accounts', accountData)
    return response.data
  },

  async updateApiAccount(id, accountData) {
    const response = await api.put(`/api-accounts/${id}`, accountData)
    return response.data
  },

  async deleteApiAccount(id) {
    const response = await api.delete(`/api-accounts/${id}`)
    return response.data
  },

  async activateApiAccount(id) {
    const response = await api.post(`/api-accounts/${id}/activate`)
    return response.data
  },

  async getApiAccountBalances(id) {
    const response = await api.get(`/api-accounts/${id}/balances`)
    return response.data
  },

  async testApiAccount(accountData) {
    const response = await api.post('/api-accounts/test', accountData)
    return response.data
  },

  async getCurrentMode() {
    const response = await api.get('/mode')
    return response.data
  },

  async setCurrentMode(isSimulation) {
    const response = await api.put('/mode', { isSimulation })
    return response.data
  }
}
