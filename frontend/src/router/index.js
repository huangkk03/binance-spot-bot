import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
    { path: '/', redirect: '/trading' },
    { path: '/login', component: () => import('../views/Login.vue'), meta: { hideLayout: true } },
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

// 路由守卫：未登录跳 /login
router.beforeEach((to, from, next) => {
    const auth = useAuthStore()

    // /login 始终放行
    if (to.path === '/login') {
        next()
        return
    }

    // 未登录跳 /login
    if (!auth.authed) {
        // 再次尝试从 storage 恢复
        const restored = auth.initFromStorage()
        if (!restored) {
            next({ path: '/login', query: { redirect: to.fullPath } })
            return
        }
    }

    // 检查过期（24h/7d）
    if (!auth.checkExpiry()) {
        next({ path: '/login', query: { redirect: to.fullPath } })
        return
    }

    next()
})

export default router
