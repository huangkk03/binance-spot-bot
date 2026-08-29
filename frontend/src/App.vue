<template>
  <div class="app-layout">
    <!-- 左侧侧边栏 (hover 展开 + 固定按钮) -->
    <aside class="sidebar" :class="{ expanded: sidebarExpanded || pinned }" @mouseenter="sidebarExpanded = true" @mouseleave="sidebarExpanded = pinned">
      <div class="sidebar-brand">
        <div class="brand-icon">B</div>
        <span class="brand-text" v-show="sidebarExpanded || pinned">Binance Bot</span>
        <button class="pin-btn" :class="{ active: pinned }" @click="pinned = !pinned" :title="pinned ? '取消固定' : '固定展开'">
          <el-icon :size="14"><component :is="pinned ? 'Lock' : 'Unlock'" /></el-icon>
        </button>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/trading" class="nav-item" :class="{ active: $route.path === '/trading' }">
          <el-icon :size="20"><Setting /></el-icon>
          <span class="nav-label" v-show="sidebarExpanded || pinned">交易控制台</span>
        </router-link>
        <router-link to="/futures" class="nav-item" :class="{ active: $route.path === '/futures' }">
          <el-icon :size="20"><TrendCharts /></el-icon>
          <span class="nav-label" v-show="sidebarExpanded || pinned">合约交易</span>
        </router-link>
        <router-link to="/dashboard" class="nav-item" :class="{ active: $route.path === '/dashboard' }">
          <el-icon :size="20"><DataLine /></el-icon>
          <span class="nav-label" v-show="sidebarExpanded || pinned">Dashboard</span>
        </router-link>
        <router-link to="/strategy" class="nav-item" :class="{ active: $route.path === '/strategy' }">
          <el-icon :size="20"><Histogram /></el-icon>
          <span class="nav-label" v-show="sidebarExpanded || pinned">策略配置</span>
        </router-link>
        <router-link to="/accounts" class="nav-item" :class="{ active: $route.path === '/accounts' }">
          <el-icon :size="20"><Wallet /></el-icon>
          <span class="nav-label" v-show="sidebarExpanded || pinned">账户余额</span>
        </router-link>
        <router-link to="/history" class="nav-item" :class="{ active: $route.path === '/history' }">
          <el-icon :size="20"><Document /></el-icon>
          <span class="nav-label" v-show="sidebarExpanded || pinned">历史</span>
        </router-link>
        <router-link to="/settings" class="nav-item" :class="{ active: $route.path === '/settings' }">
          <el-icon :size="20"><Tools /></el-icon>
          <span class="nav-label" v-show="sidebarExpanded || pinned">设置</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <button class="logout-btn" @click="handleLogout" :title="'登出 — ' + auth.username">
          <el-icon :size="18"><SwitchButton /></el-icon>
          <span class="nav-label" v-show="sidebarExpanded || pinned">登出</span>
        </button>
      </div>
    </aside>

    <!-- 右侧主区域 -->
    <div class="main-area">
      <!-- 顶栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="topbar-right">
          <el-tag v-if="store.accounts && store.accounts.length" size="small" type="warning" round>
            {{ store.accounts.length }} 币种
          </el-tag>
          <button class="refresh-btn" @click="refreshAll" :disabled="refreshing" title="刷新">
            <el-icon :size="16" :class="{ spinning: refreshing }"><Refresh /></el-icon>
          </button>
        </div>
      </header>

      <!-- 主内容 -->
      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from './stores/auth'
import { useCompoundStore } from './stores/compound'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const store = useCompoundStore()

const sidebarExpanded = ref(false)
const pinned = ref(false)
const refreshing = ref(false)
let expiryTimer = null

const pageTitle = computed(() => {
  const m = {
  '/trading': '交易控制台',
  '/futures': '合约交易',
  '/dashboard': 'Dashboard',
    '/strategy': '策略配置',
    '/accounts': '账户余额',
    '/history': '历史',
    '/settings': '设置',
  }
  return m[route.path] || 'Binance Bot'
})

async function refreshAll() {
  refreshing.value = true
  try {
    await Promise.all([store.fetchInstances(), store.fetchAccounts(), store.fetchAlerts()])
    ElMessage.success('已刷新')
  } catch (e) {
    // 静默失败
  } finally {
    refreshing.value = false
  }
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}

onMounted(() => {
  store.initWebSocket()
  store.fetchInstances().catch(() => {})
  store.fetchAccounts().catch(() => {})

  expiryTimer = setInterval(() => {
    if (!auth.checkExpiry()) {
      ElMessage.warning('会话已过期，请重新登录')
      router.push('/login')
    }
  }, 60000)
})

onUnmounted(() => {
  if (expiryTimer) clearInterval(expiryTimer)
})
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ========== 侧边栏 ========== */
.sidebar {
  width: var(--sidebar-collapsed);
  background: var(--bg-card);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-slow);
  z-index: 100;
  flex-shrink: 0;
}

.sidebar.expanded {
  width: var(--sidebar-width);
}

.sidebar-brand {
  height: var(--header-height);
  display: flex;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid var(--border-light);
  gap: 10px;
  overflow: hidden;
  flex-shrink: 0;
}

.brand-icon {
  width: 28px;
  height: 28px;
  background: var(--accent);
  color: #1a1f2e;
  font-size: 14px;
  font-weight: 700;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-text {
  color: var(--accent);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  flex: 1;
}

.pin-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  background: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0;
  flex-shrink: 0;
  transition: all var(--transition);
}

.sidebar.expanded .pin-btn {
  display: flex;
}

.pin-btn:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.pin-btn.active {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--bg-accent-dim);
}

/* ========== 导航 ========== */
.sidebar-nav {
  flex: 1;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  overflow-x: hidden;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all var(--transition);
  white-space: nowrap;
  position: relative;
}

.nav-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 0;
  background: var(--accent);
  border-radius: 1px;
  transition: height var(--transition);
}

.nav-item:hover {
  color: var(--text-primary);
  background: var(--bg-card-hover);
}

.nav-item.active {
  color: var(--accent);
  background: var(--bg-accent-dim);
}

.nav-item.active::before {
  height: 20px;
}

.nav-label {
  transition: opacity 0.3s;
}

.sidebar.expanded .nav-label {
  opacity: 1;
}

/* ========== 底部登出 ========== */
.sidebar-footer {
  padding: 8px;
  border-top: 1px solid var(--border-light);
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  border: none;
  background: none;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition);
  white-space: nowrap;
}

.logout-btn:hover {
  color: var(--negative);
  background: var(--negative-bg);
}

/* ========== 主区域 ========== */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ========== 顶栏 ========== */
.topbar {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.page-title {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  background: var(--bg-card-hover);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition);
}

.refresh-btn:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ========== 内容区 ========== */
.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: var(--bg-primary);
}

/* ========== 路由过渡 ========== */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
