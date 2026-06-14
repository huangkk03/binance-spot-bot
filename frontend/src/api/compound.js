import axios from 'axios'

const api = axios.create({
    baseURL: '/api/v1',
    timeout: 30000
})

export const compoundApi = {
    // ===== 账户 =====
    async listAccounts() {
        const r = await api.get('/accounts/')
        return r.data
    },
    async createAccount(data) {
        const r = await api.post('/accounts/', data)
        return r.data
    },
    async updateAccount(id, data) {
        const r = await api.put(`/accounts/${id}`, data)
        return r.data
    },
    async deleteAccount(id) {
        await api.delete(`/accounts/${id}`)
    },
    async activateAccount(id) {
        const r = await api.post(`/accounts/${id}/activate`)
        return r.data
    },
    async testAccount(data) {
        const r = await api.post('/accounts/test', data)
        return r.data
    },
    async getBalance() {
        const r = await api.get('/accounts/balance')
        return r.data
    },
    async getBalanceByAsset(asset) {
        const r = await api.get(`/accounts/balance/${asset}`)
        return r.data
    },
    async getAccountBalances(id) {
        const r = await api.get(`/accounts/${id}/balances`)
        return r.data
    },
    async getProxyStatus() {
        const r = await api.get('/accounts/proxy-status')
        return r.data
    },

    // ===== 交易 =====
    async executeTick(symbols) {
        const r = await api.post('/trading/tick', symbols)
        return r.data
    },
    async manualOpen(symbol, quoteAmount) {
        const r = await api.post('/trading/real-trade/open', { symbol, quote_amount: quoteAmount })
        return r.data
    },
    async listInstances(symbol) {
        const r = await api.get('/trading/instances', { params: symbol ? { symbol } : {} })
        return r.data
    },
    async getEventHistory(symbol, limit = 100) {
        const r = await api.get('/trading/history/events', { params: { symbol, limit } })
        return r.data
    },
    async getOrderHistory(symbol, limit = 100) {
        const r = await api.get('/trading/history/orders', { params: { symbol, limit } })
        return r.data
    },

    // ===== 行情 =====
    async getPrices() {
        const r = await api.get('/market/prices')
        return r.data
    },
    async getPrice(symbol) {
        const r = await api.get(`/market/prices/${symbol}`)
        return r.data
    },

    // ===== 扫描器 =====
    async getAlerts(symbol, interval) {
        const r = await api.get('/scanners/alerts', { params: { symbol, interval } })
        return r.data
    },
    async getTriggeredAlerts() {
        const r = await api.get('/scanners/alerts/triggered')
        return r.data
    },
    async triggerScan() {
        const r = await api.post('/scanners/alerts/scan')
        return r.data
    },
    async getFundingRateAlerts() {
        const r = await api.get('/scanners/funding-rates')
        return r.data
    },

    // ===== 策略配置 =====
    async getStrategyConfigList(symbol) {
        const r = await api.get('/strategy/config', { params: symbol ? { symbol } : {} })
        return r.data
    },
    async getStrategyEffective(symbol) {
        const r = await api.get('/strategy/effective', { params: symbol ? { symbol } : {} })
        return r.data
    },
    async getStrategyEffectiveValue(key, symbol) {
        const r = await api.get(`/strategy/config/${key}/effective`, { params: symbol ? { symbol } : {} })
        return r.data
    },
    async upsertStrategyConfig(key, value, symbol = null, description = '') {
        const params = symbol ? { symbol } : {}
        const r = await api.put(`/strategy/config/${key}`, {
            config_value: value,
            description,
        }, { params })
        return r.data
    },
    async deleteStrategyConfig(key, symbol = null) {
        const params = symbol ? { symbol } : {}
        const r = await api.delete(`/strategy/config/${key}`, { params })
        return r.data
    },

    // ===== 通知 =====
    async getConfig() {
        const r = await api.get('/notifications/config')
        return r.data
    },
    async getConfigValue(key) {
        const r = await api.get(`/notifications/config/${key}`)
        return r.data
    },
    async setConfigValue(key, value) {
        const r = await api.put(`/notifications/config/${key}`, { value })
        return r.data
    },
    async testNotification(title, content) {
        const r = await api.post('/notifications/test-notification', { title, content })
        return r.data
    },
    async testAi(url, key, model) {
        const r = await api.post('/notifications/test-ai', { url, key, model })
        return r.data
    },

    // ===== 报告 =====
    async getBtcPredictionPdfUrl() {
        return '/api/v1/reports/btc-prediction/pdf'
    },
    async getBtcPredictionText() {
        const r = await api.get('/reports/btc-prediction/text')
        return r.data
    },

    // ===== AI =====
    async aiChat(system, user) {
        const r = await api.post('/ai/chat', { system, user })
        return r.data
    },
}
