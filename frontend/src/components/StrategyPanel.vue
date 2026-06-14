<template>
  <div>
    <h3>策略参数配置</h3>
    <el-alert type="info" :closable="false" style="margin-bottom: 1rem;">
      三层优先级：交易对独立配置 → 全局数据库配置 → settings.py 默认值
    </el-alert>

    <el-form :model="form" label-width="180px" style="max-width: 700px;">
      <el-form-item label="配置范围">
        <el-radio-group v-model="form.scope" @change="onScopeChange">
          <el-radio value="GLOBAL">全局默认</el-radio>
          <el-radio value="SYMBOL">交易对独立</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.scope === 'SYMBOL'" label="选择交易对">
        <el-select v-model="form.symbol" @change="loadEffective" style="width: 100%;">
          <el-option v-for="sym in availableSymbols" :key="sym" :value="sym" :label="sym" />
        </el-select>
      </el-form-item>

      <el-divider>当前生效配置</el-divider>

      <el-form-item v-for="(item, key) in effective" :key="key" :label="item.label">
        <el-input v-model="item.value" :placeholder="item.placeholder" style="width: 200px;" />
        <el-button size="small" type="primary" @click="saveConfig(key, item.value)" :loading="savingKey === key" style="margin-left: 8px;">
          保存
        </el-button>
        <span v-if="item.isOverride" class="override-tag">已覆盖</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const availableSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']

const form = reactive({
  scope: 'GLOBAL',
  symbol: 'BTCUSDT',
})

const effective = ref({})
const savingKey = ref(null)
const overrides = ref(new Set())

const configMeta = {
  TAKE_PROFIT_PCT: { label: '止盈点 (%)', placeholder: '0.03' },
  STOP_LOSS_PCT: { label: '止损点 (%)，0=关闭', placeholder: '0.10' },
  QUOTE_RESERVE: { label: 'USDT 预留金额', placeholder: '10' },
  MAX_ORDERS_PER_TICK: { label: '每轮最大订单数', placeholder: '5' },
  MAX_INSTANCES_PER_SYMBOL: { label: '每交易对最大实例数', placeholder: '3' },
  RSI_OVERBOUGHT: { label: 'RSI 超买阈值', placeholder: '80' },
  RSI_OVERSOLD: { label: 'RSI 超卖阈值', placeholder: '20' },
  RSI_PERIOD: { label: 'RSI 周期', placeholder: '14' },
}

async function loadEffective() {
  const symbol = form.scope === 'SYMBOL' ? form.symbol : null
  try {
    const data = await compoundApi.getStrategyEffective(symbol)
    overrides.value.clear()
    effective.value = {}
    for (const [key, meta] of Object.entries(configMeta)) {
      effective.value[key] = {
        label: meta.label,
        placeholder: meta.placeholder,
        value: data[key] || '',
        isOverride: symbol ? await checkOverride(key, symbol) : false,
      }
    }
  } catch (e) {
    ElMessage.error('加载配置失败: ' + e.message)
  }
}

async function checkOverride(key, symbol) {
  try {
    const r = await compoundApi.getStrategyConfigList(symbol)
    return r.some(item => item.config_key === key && item.symbol === symbol)
  } catch {
    return false
  }
}

function onScopeChange() {
  loadEffective()
}

async function saveConfig(key, value) {
  if (!value) {
    ElMessage.warning('值不能为空')
    return
  }
  savingKey.value = key
  try {
    const symbol = form.scope === 'SYMBOL' ? form.symbol : null
    await compoundApi.upsertStrategyConfig(key, value, symbol)
    ElMessage.success('已保存')
    await loadEffective()
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    savingKey.value = null
  }
}

onMounted(() => {
  loadEffective()
})
</script>

<style scoped>
h3 { color: #e0e6ed; }
.override-tag {
  margin-left: 8px;
  padding: 2px 8px;
  background: #f0b90b;
  color: #1a1f2e;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
</style>
