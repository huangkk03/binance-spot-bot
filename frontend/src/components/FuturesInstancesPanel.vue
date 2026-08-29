<template>
  <div>
    <el-table :data="instances" size="small" stripe>
      <el-table-column label="交易对" width="110">
        <template #default="{ row }">{{ row.symbol }}<span class="dim">#{{ row.instance_id }}</span></template>
      </el-table-column>
      <el-table-column label="方向" width="72" align="center">
        <template #default="{ row }">
          <el-tag :type="row.direction === 'LONG' ? 'success' : 'danger'" size="small" round>
            {{ row.direction === 'LONG' ? '多' : '空' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="杠杆" width="56" align="center">
        <template #default="{ row }">{{ row.leverage }}x</template>
      </el-table-column>
      <el-table-column label="入场价" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.cycle_start_price) }}</template>
      </el-table-column>
      <el-table-column label="保证金" width="90" align="right">
        <template #default="{ row }">{{ fmt(row.margin) }}</template>
      </el-table-column>
      <el-table-column label="名义仓位" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.notional) }}</template>
      </el-table-column>
      <el-table-column label="盈亏" width="100" align="right">
        <template #default="{ row }">
          <span :class="Number(row.cumulative_profit) >= 0 ? 'positive' : 'negative'">
            {{ Number(row.cumulative_profit) >= 0 ? '+' : '' }}{{ Number(row.cumulative_profit).toFixed(2) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="72" align="center">
        <template #default="{ row }">
          <span class="dot" :class="row.is_open ? 'open' : 'closed'" />
          {{ row.is_open ? '持仓' : '已平' }}
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { compoundApi } from '../api/compound'

const instances = ref([])
let timer = null

async function load() { try { instances.value = await compoundApi.futuresInstances() } catch {} }
function fmt(v) { return v ? Number(v).toFixed(2) : '-' }

onMounted(() => { load(); timer = setInterval(load, 10000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.dim { color: var(--text-muted); }
.dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; margin-right: 4px; }
.dot.open { background: var(--positive); box-shadow: 0 0 6px var(--positive); }
.dot.closed { background: var(--text-muted); }
.positive { color: var(--positive); font-weight: 600; }
.negative { color: var(--negative); font-weight: 600; }
</style>
