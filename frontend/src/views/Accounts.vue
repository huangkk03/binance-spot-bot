<template>
  <div class="page animate-fade-in">
    <h2>账户余额</h2>
    <p class="desc">现货资产实时查询（需激活 API 账户）</p>
    <el-table :data="store.accounts" size="small" stripe>
      <el-table-column prop="asset" label="币种" width="100" />
      <el-table-column prop="free" label="可用" align="right">
        <template #default="{ row }">{{ fmt(row.free) }}</template>
      </el-table-column>
      <el-table-column prop="locked" label="锁定" align="right">
        <template #default="{ row }">{{ fmt(row.locked) }}</template>
      </el-table-column>
      <el-table-column prop="total" label="总计" align="right">
        <template #default="{ row }">
          <strong>{{ fmt(row.total) }}</strong>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { useCompoundStore } from '../stores/compound'
const store = useCompoundStore()
function fmt(v) { return v ? Number(v).toFixed(8) : '0' }
</script>

<style scoped>
.page { max-width: 800px; }
h2 { margin: 0 0 4px; font-size: 18px; font-weight: 700; color: var(--text-primary); }
.desc { color: var(--text-secondary); font-size: 13px; margin: 0 0 20px; }
</style>
