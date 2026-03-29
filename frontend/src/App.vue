<template>
  <div class="app-container">
    <header class="app-header">
      <h1>Binance Compound Pro</h1>
      <nav>
        <button @click="currentView = 'dashboard'" :class="{ active: currentView === 'dashboard' }">行情监控</button>
        <button @click="currentView = 'console'" :class="{ active: currentView === 'console' }">模拟控制台</button>
        <button @click="currentView = 'history'" :class="{ active: currentView === 'history' }">历史记录</button>
      </nav>
    </header>
    
    <main class="app-main">
      <Dashboard v-if="currentView === 'dashboard'" />
      <SimulationConsole v-else-if="currentView === 'console'" />
      <History v-else-if="currentView === 'history'" />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Dashboard from './views/Dashboard.vue'
import SimulationConsole from './views/SimulationConsole.vue'
import History from './views/History.vue'
import { useCompoundStore } from './stores/compound'

const store = useCompoundStore()
const currentView = ref('dashboard')

onMounted(async () => {
  await store.loadModeFromServer()
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
  background: #0a0e17;
  color: #e0e6ed;
}

.app-container {
  min-height: 100vh;
}

.app-header {
  background: #1a1f2e;
  padding: 1rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #2a3042;
}

.app-header h1 {
  font-size: 1.5rem;
  color: #f0b90b;
}

.app-header nav button {
  background: transparent;
  border: 1px solid #2a3042;
  color: #848e9c;
  padding: 0.5rem 1rem;
  margin-left: 0.5rem;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.app-header nav button:hover,
.app-header nav button.active {
  background: #f0b90b;
  color: #000;
  border-color: #f0b90b;
}

.app-main {
  padding: 2rem;
}
</style>
