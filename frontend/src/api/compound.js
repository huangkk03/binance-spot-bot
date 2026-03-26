import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
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
  }
}
