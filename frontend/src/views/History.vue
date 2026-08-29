<template>
  <div class="page animate-fade-in">
    <h2>历史</h2>
    <p class="desc">事件 / 订单 / 资金费率报警</p>

    <el-tabs v-model="tab" class="pill-tabs">
      <el-tab-pane label="事件历史" name="events">
        <el-table :data="events" size="small" stripe>
          <el-table-column prop="created_at" label="时间" width="170" />
          <el-table-column prop="symbol" label="交易对" width="90" />
          <el-table-column prop="event" label="事件" width="110" />
          <el-table-column prop="price" label="价格" width="110" align="right" />
          <el-table-column prop="base_qty" label="数量" width="100" align="right" />
          <el-table-column prop="note" label="备注" min-width="120" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="订单历史" name="orders">
        <el-table :data="orders" size="small" stripe>
          <el-table-column prop="created_at" label="时间" width="170" />
          <el-table-column prop="symbol" label="交易对" width="90" />
          <el-table-column prop="side" label="方向" width="60" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="executed_qty" label="数量" width="100" align="right" />
          <el-table-column prop="avg_price" label="价格" width="100" align="right" />
          <el-table-column prop="order_id" label="订单 ID" min-width="180" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="资金费率" name="funding">
        <el-table :data="store.fundingAlerts" size="small" stripe>
          <el-table-column prop="symbol" label="交易对" width="90" />
          <el-table-column prop="alert_type" label="级别" width="110" />
          <el-table-column prop="funding_rate" label="费率" width="110" align="right" />
          <el-table-column prop="annualized_rate" label="年化" width="110" align="right" />
          <el-table-column prop="updated_at" label="更新时间" width="170" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { compoundApi } from '../api/compound'
import { useCompoundStore } from '../stores/compound'

const store = useCompoundStore()
const tab = ref('events')
const events = ref([])
const orders = ref([])
let timer = null

async function refresh() { try { events.value = await compoundApi.getEventHistory(null, 100); orders.value = await compoundApi.getOrderHistory(null, 100) } catch {} }

onMounted(() => { refresh(); timer = setInterval(refresh, 10000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.page { max-width: 1400px; }
h2 { margin: 0 0 4px; font-size: 18px; font-weight: 700; color: var(--text-primary); }
.desc { color: var(--text-secondary); font-size: 13px; margin: 0 0 20px; }
.pill-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.pill-tabs :deep(.el-tabs__item) { padding: 0 16px; height: 32px; line-height: 32px; border-radius: 16px; margin-right: 6px; font-size: 13px; }
.pill-tabs :deep(.el-tabs__item.is-active) { background: var(--bg-accent-dim); }
.pill-tabs :deep(.el-tabs__active-bar) { display: none; }
.pill-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
</style>
