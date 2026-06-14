import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: () => import('../views/Dashboard.vue') },
    { path: '/trading', component: () => import('../views/TradingConsole.vue') },
    { path: '/strategy', component: () => import('../views/StrategyView.vue') },
    { path: '/accounts', component: () => import('../views/Accounts.vue') },
    { path: '/history', component: () => import('../views/History.vue') },
    { path: '/settings', component: () => import('../views/Settings.vue') },
]

const router = createRouter({
    history: createWebHashHistory(),
    routes,
})

export default router
