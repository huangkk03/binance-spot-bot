<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="info-card">
      选择做多或做空，输入<strong>名义仓位金额</strong>，系统自动计算保证金（名义仓位 ÷ 杠杆）。
    </el-alert>

    <div class="form-row">
      <el-select v-model="form.symbol" placeholder="交易对" size="large" style="width: 140px;">
        <el-option v-for="s in symbols" :key="s" :value="s" :label="s.replace('USDT','')+'/USDT'" />
      </el-select>
      <el-select v-model="form.direction" placeholder="方向" size="large" style="width: 120px;">
        <el-option value="LONG" label="📈 做多" />
        <el-option value="SHORT" label="📉 做空" />
      </el-select>
      <el-input-number v-model="form.notional" :min="100" :max="1000000" :step="100" size="large" style="flex:1;" placeholder="名义仓位 USDT" />
      <el-input-number v-model="form.leverage" :min="1" :max="125" :step="1" size="large" style="width: 120px;" placeholder="杠杆" />
      <div class="margin-preview">
        保证金: <strong>{{ (form.notional / form.leverage).toFixed(2) }}</strong> USDT
      </div>
      <el-button type="primary" :loading="loading" @click="doOpen" size="large" round>
        确认开仓
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const symbols = ['BTCUSDT', 'ETHUSDT']
const form = ref({ symbol: 'BTCUSDT', direction: 'LONG', notional: 5000, leverage: 100 })
const loading = ref(false)

async function doOpen() {
  if (!form.value.symbol) return
  loading.value = true
  try {
    const r = await compoundApi.futuresOpen({
      symbol: form.value.symbol, direction: form.value.direction,
      notional: form.value.notional, leverage: form.value.leverage
    })
    ElMessage({ message: r.success ? '开仓成功' : '失败: ' + (r.errors || []).join('; '), type: r.success ? 'success' : 'error' })
  } catch (e) { ElMessage.error('请求失败: ' + e.message) }
  finally { loading.value = false }
}
</script>

<style scoped>
.info-card { margin-bottom: 20px; }
.form-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.margin-preview { color: var(--accent); font-size: 14px; white-space: nowrap; padding: 0 8px; }
</style>
