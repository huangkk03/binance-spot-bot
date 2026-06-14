<template>
  <div class="history">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="事件历史" name="events">
        <el-table :data="events" stripe>
          <el-table-column prop="created_at" label="时间" width="180" />
          <el-table-column prop="symbol" label="交易对" width="100" />
          <el-table-column prop="event" label="事件" width="120" />
          <el-table-column prop="price" label="价格" width="120" />
          <el-table-column prop="base_qty" label="数量" width="120" />
          <el-table-column prop="note" label="备注" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="订单历史" name="orders">
        <el-table :data="orders" stripe>
          <el-table-column prop="created_at" label="时间" width="180" />
          <el-table-column prop="symbol" label="交易对" width="100" />
          <el-table-column prop="side" label="方向" width="80" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="executed_qty" label="数量" width="120" />
          <el-table-column prop="avg_price" label="价格" width="120" />
          <el-table-column prop="order_id" label="订单 ID" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="资金费率报警" name="funding">
        <el-table :data="store.fundingAlerts" stripe>
          <el-table-column prop="symbol" label="交易对" width="100" />
          <el-table-column prop="alert_type" label="级别" width="120" />
          <el-table-column prop="funding_rate" label="费率" width="120" />
          <el-table-column prop="annualized_rate" label="年化" width="120" />
          <el-table-column prop="updated_at" label="更新时间" width="180" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { compoundApi } from '../api/compound'
import { useCompoundStore } from '../stores/compound'

const store = useCompoundStore()
const activeTab = ref('events')
const events = ref([])
const orders = ref([])
let timer = null

async function refresh() {
  try {
    events.value = await compoundApi.getEventHistory(null, 100)
    orders.value = await compoundApi.getOrderHistory(null, 100)
  } catch (e) {
    console.error('refresh history error:', e)
  }
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, 10000)
})

onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.history { max-width: 1600px; margin: 0 auto; padding: 1rem; }
</style>
