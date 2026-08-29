<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="info-card">
      优先级：<strong>交易对独立配置</strong> > 数据库全局 > settings.py 默认值
    </el-alert>

    <div class="scope-row">
      <el-radio-group v-model="form.scope" @change="loadEffective" size="small">
        <el-radio-button value="GLOBAL">全局默认</el-radio-button>
        <el-radio-button value="SYMBOL">交易对独立</el-radio-button>
      </el-radio-group>
      <el-select v-if="form.scope === 'SYMBOL'" v-model="form.symbol" @change="loadEffective" size="small" style="width: 150px; margin-left: 12px;">
        <el-option v-for="sym in availableSymbols" :key="sym" :value="sym" :label="sym.replace('USDT','') + '/USDT'" />
      </el-select>
    </div>

    <div class="config-grid">
      <div v-for="(item, key) in effective" :key="key" class="config-item">
        <div class="config-label">
          {{ item.label }}
          <span v-if="item.isOverride" class="override-badge">已覆盖</span>
        </div>
        <div class="config-input">
          <el-input v-model="item.value" :placeholder="item.placeholder" size="small" />
          <el-button size="small" type="primary" @click="saveConfig(key, item.value)" :loading="savingKey === key" round>保存</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const availableSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']
const form = reactive({ scope: 'GLOBAL', symbol: 'BTCUSDT' })
const effective = ref({})
const savingKey = ref(null)

const configMeta = {
  TAKE_PROFIT_PCT: { label: '止盈点 (%)', placeholder: '0.03' },
  STOP_LOSS_PCT: { label: '止损点 (%)，0=关闭', placeholder: '0.10' },
  QUOTE_RESERVE: { label: 'USDT 预留金额', placeholder: '10' },
  MAX_ORDERS_PER_TICK: { label: '每轮最大订单数', placeholder: '5' },
  MAX_INSTANCES_PER_SYMBOL: { label: '每币种最大实例数', placeholder: '3' },
  RSI_OVERBOUGHT: { label: 'RSI 超买阈值', placeholder: '80' },
  RSI_OVERSOLD: { label: 'RSI 超卖阈值', placeholder: '20' },
  RSI_PERIOD: { label: 'RSI 周期', placeholder: '14' },
}

async function loadEffective() {
  const sym = form.scope === 'SYMBOL' ? form.symbol : null
  try {
    const data = await compoundApi.getStrategyEffective(sym)
    const overrides = {}
    effective.value = {}
    for (const [key, meta] of Object.entries(configMeta)) {
      effective.value[key] = { label: meta.label, placeholder: meta.placeholder, value: data[key] || '', isOverride: false }
    }
  } catch (e) { ElMessage.error('加载配置失败') }
}

async function saveConfig(key, value) {
  if (!value) { ElMessage.warning('值不能为空'); return }
  savingKey.value = key
  try {
    const sym = form.scope === 'SYMBOL' ? form.symbol : null
    await compoundApi.upsertStrategyConfig(key, value, sym)
    ElMessage.success('已保存')
    await loadEffective()
  } catch (e) { ElMessage.error('保存失败') }
  finally { savingKey.value = null }
}

onMounted(loadEffective)
</script>

<style scoped>
.info-card { margin-bottom: 20px; }
.scope-row { display: flex; align-items: center; margin-bottom: 20px; }
.config-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.config-item {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  padding: 16px;
}
.config-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
.override-badge {
  padding: 1px 8px;
  background: linear-gradient(135deg, var(--accent), #fcd535);
  color: #1a1f2e;
  border-radius: 20px;
  font-size: 10px;
  font-weight: 700;
}
.config-input { display: flex; gap: 8px; }
.config-input > :first-child { flex: 1; }
</style>
