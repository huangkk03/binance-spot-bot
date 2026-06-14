import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { compoundApi } from '../api/compound'

export const useCompoundStore = defineStore('compound', () => {
    const prices = ref({})
    const instances = ref([])
    const accounts = ref({})
    const alerts = ref({})
    const config = ref([])
    const fundingAlerts = ref([])

    const isLoading = ref(false)
    const lastError = ref(null)

    let ws = null
    let wsReconnectTimer = null

    function initWebSocket() {
        if (ws) return
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        const wsUrl = `${protocol}//${window.location.host}/ws/frontend`

        try {
            ws = new WebSocket(wsUrl)
        } catch (e) {
            console.error('WebSocket init failed:', e)
            scheduleReconnect()
            return
        }

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
                console.error('WS message parse error:', e)
            }
        }

        ws.onclose = () => {
            console.log('Frontend WebSocket disconnected')
            ws = null
            scheduleReconnect()
        }

        ws.onerror = (e) => {
            console.error('WebSocket error:', e)
        }
    }

    function scheduleReconnect() {
        if (wsReconnectTimer) return
        wsReconnectTimer = setTimeout(initWebSocket, 5000)
    }

    async function fetchInstances(symbol = null) {
        try {
            instances.value = await compoundApi.listInstances(symbol)
        } catch (e) {
            console.error('fetchInstances error:', e)
        }
    }

    async function fetchPrices() {
        try {
            prices.value = await compoundApi.getPrices()
        } catch (e) {
            console.error('fetchPrices error:', e)
        }
    }

    async function fetchAccounts() {
        try {
            const data = await compoundApi.getBalance()
            accounts.value = data.balances || []
        } catch (e) {
            console.error('fetchAccounts error:', e)
        }
    }

    async function fetchAlerts() {
        try {
            const triggered = await compoundApi.getTriggeredAlerts()
            alerts.value = {}
            for (const a of triggered) {
                const key = `${a.symbol}_${a.kline_interval}`
                alerts.value[key] = a
            }
        } catch (e) {
            console.error('fetchAlerts error:', e)
        }
    }

    async function fetchConfig() {
        try {
            config.value = await compoundApi.getConfig()
        } catch (e) {
            console.error('fetchConfig error:', e)
        }
    }

    async function fetchFundingAlerts() {
        try {
            fundingAlerts.value = await compoundApi.getFundingRateAlerts()
        } catch (e) {
            console.error('fetchFundingAlerts error:', e)
        }
    }

    return {
        prices, instances, accounts, alerts, config, fundingAlerts,
        isLoading, lastError,
        initWebSocket,
        fetchInstances, fetchPrices, fetchAccounts, fetchAlerts, fetchConfig, fetchFundingAlerts,
    }
})
