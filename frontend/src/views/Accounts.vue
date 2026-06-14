<template>
  <div>
    <h2>账户管理</h2>
    <p>请前往 <router-link to="/trading">交易控制台</router-link> 的 "API 账户" 标签页管理 API 账户。</p>
    <el-card>
      <template #header>当前激活账户余额</template>
      <el-table :data="store.accounts" stripe>
        <el-table-column prop="asset" label="币种" width="100" />
        <el-table-column prop="free" label="可用" width="200">
          <template #default="{ row }">{{ formatNumber(row.free) }}</template>
        </el-table-column>
        <el-table-column prop="locked" label="锁定" width="200">
          <template #default="{ row }">{{ formatNumber(row.locked) }}</template>
        </el-table-column>
        <el-table-column prop="total" label="总计" width="200">
          <template #default="{ row }">{{ formatNumber(row.total) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { useCompoundStore } from '../stores/compound'
const store = useCompoundStore()
function formatNumber(v) { return v ? Number(v).toFixed(8) : '0' }
</script>
