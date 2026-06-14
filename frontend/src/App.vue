<template>
  <el-container class="app-container">
    <!-- 左侧侧边栏 -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="app-aside">
      <div class="logo" :class="{ collapsed: isCollapsed }">
        <div class="logo-icon">B</div>
        <span v-if="!isCollapsed" class="logo-text">Binance Bot</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        class="app-menu"
        background-color="transparent"
        text-color="#b8c0cc"
        active-text-color="#f0b90b"
        router
      >
        <el-menu-item index="/trading">
          <el-icon><Setting /></el-icon>
          <template #title>交易控制台</template>
        </el-menu-item>
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <template #title>Dashboard</template>
        </el-menu-item>
        <el-menu-item index="/strategy">
          <el-icon><Histogram /></el-icon>
          <template #title>策略配置</template>
        </el-menu-item>
        <el-menu-item index="/accounts">
          <el-icon><Wallet /></el-icon>
          <template #title>账户余额</template>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><Document /></el-icon>
          <template #title>历史</template>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Tools /></el-icon>
          <template #title>设置</template>
        </el-menu-item>
      </el-menu>

      <div class="collapse-btn" @click="isCollapsed = !isCollapsed">
        <el-icon><Fold v-if="!isCollapsed" /><Expand v-else /></el-icon>
      </div>
    </el-aside>

    <!-- 右侧主区域 -->
    <el-container>
      <!-- 顶栏 -->
      <el-header class="app-header">
        <div class="header-left">
          <span class="page-title">{{ pageTitle }}</span>
          <el-tag v-if="pageSubtitle" size="small" type="info" class="page-subtitle">
            {{ pageSubtitle }}
          </el-tag>
        </div>
        <div class="header-right">
          <el-tag v-if="auth.authed" type="success" size="small" class="user-tag">
            <el-icon><User /></el-icon>
            <span style="margin-left: 4px;">{{ auth.username }}</span>
          </el-tag>
          <el-tag v-if="store.accounts && store.accounts.length > 0" type="warning" size="small">
            {{ store.accounts.length }} 币种
          </el-tag>
          <el-button size="small" @click="refreshAll" :loading="refreshing">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button size="small" type="danger" plain @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            登出
          </el-button>
        </div>
      </el-header>

      <!-- 主内容 -->
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from './stores/auth'
import { useCompoundStore } from './stores/compound'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const store = useCompoundStore()

const isCollapsed = ref(false)
const refreshing = ref(false)
let expiryTimer = null

const pageMap = {
  '/trading': { title: '交易控制台', subtitle: '账户 / 策略 / Tick / AI / 通知' },
  '/dashboard': { title: 'Dashboard', subtitle: '实时行情 + 交易实例' },
  '/strategy': { title: '策略配置', subtitle: '全局 + 交易对独立覆盖' },
  '/accounts': { title: '账户余额', subtitle: '现货资产实时查询' },
  '/history': { title: '历史', subtitle: '事件 / 订单 / 报警' },
  '/settings': { title: '设置', subtitle: '系统信息' },
}

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => pageMap[route.path]?.title || 'Binance Bot')
const pageSubtitle = computed(() => pageMap[route.path]?.subtitle || '')

async function refreshAll() {
  refreshing.value = true
  try {
    await Promise.all([
      store.fetchInstances(),
      store.fetchAccounts(),
      store.fetchAlerts(),
    ])
    ElMessage.success('已刷新')
  } catch (e) {
    ElMessage.error('刷新失败: ' + e.message)
  } finally {
    refreshing.value = false
  }
}

function handleLogout() {
  ElMessageBox.confirm('确定要登出吗？', '确认登出', {
    type: 'warning',
  }).then(() => {
    auth.logout()
    ElMessage.success('已登出')
    router.push('/login')
  }).catch(() => {})
}

onMounted(() => {
  store.initWebSocket()
  // 启动时拉取一次
  store.fetchInstances().catch(() => {})
  store.fetchAccounts().catch(() => {})

  // 定期检查会话过期
  expiryTimer = setInterval(() => {
    if (!auth.checkExpiry()) {
      ElMessage.warning('会话已过期，请重新登录')
      router.push('/login')
    }
  }, 60 * 1000)  // 每分钟检查一次
})

onUnmounted(() => {
  if (expiryTimer) clearInterval(expiryTimer)
})
</script>

<style scoped>
.app-container {
  height: 100vh;
}

.app-aside {
  background: #1a1f2e;
  border-right: 1px solid #2a3042;
  transition: width 0.2s;
  position: relative;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid #2a3042;
  gap: 12px;
  overflow: hidden;
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #f0b90b 0%, #fcd535 100%);
  color: #1a1f2e;
  font-size: 1.25rem;
  font-weight: bold;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  color: #f0b90b;
  font-size: 1rem;
  font-weight: 600;
  white-space: nowrap;
}

.app-menu {
  border: none !important;
  flex: 1;
  overflow-y: auto;
}

.collapse-btn {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #848e9c;
  cursor: pointer;
  border-top: 1px solid #2a3042;
  transition: color 0.2s;
}

.collapse-btn:hover {
  color: #f0b90b;
  background: #252a3d;
}

.app-header {
  background: #1a1f2e;
  border-bottom: 1px solid #2a3042;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.5rem;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.page-title {
  color: #e0e6ed;
  font-size: 1.125rem;
  font-weight: 600;
}

.page-subtitle {
  margin-left: 0.5rem;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.user-tag {
  margin-right: 0.25rem;
}

.app-main {
  background: #0f1218;
  padding: 1.5rem;
  overflow-y: auto;
}

/* 路由过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Element Plus 菜单覆盖 */
:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item) {
  border-radius: 0;
  margin: 0 8px;
}

:deep(.el-menu-item:hover) {
  background: #252a3d !important;
  color: #f0b90b !important;
}

:deep(.el-menu-item.is-active) {
  background: rgba(240, 185, 11, 0.1) !important;
  border-right: 2px solid #f0b90b;
}

:deep(.el-menu--collapse) .el-menu-item {
  margin: 0 4px;
}
</style>
