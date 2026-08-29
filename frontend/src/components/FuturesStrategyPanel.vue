<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="info-card">
      合约策略与现货独立。修改后 <strong>保存</strong> 即可生效。
    </el-alert>

    <div class="config-grid">
      <div class="config-item">
        <div class="config-label">默认杠杆倍数</div>
        <div class="config-input">
          <el-input v-model="cfg.leverage" placeholder="100" size="small" />
          <el-button size="small" type="primary" @click="save('FUTURES_DEFAULT_LEVERAGE', cfg.leverage)" round>保存</el-button>
        </div>
      </div>
      <div class="config-item">
        <div class="config-label">止盈点 (%)</div>
        <div class="config-input">
          <el-input v-model="cfg.tp" placeholder="0.015" size="small" />
          <el-button size="small" type="primary" @click="save('FUTURES_TAKE_PROFIT_PCT', cfg.tp)" round>保存</el-button>
        </div>
      </div>
      <div class="config-item">
        <div class="config-label">止损点 (%)</div>
        <div class="config-input">
          <el-input v-model="cfg.sl" placeholder="0.10" size="small" />
          <el-button size="small" type="primary" @click="save('FUTURES_STOP_LOSS_PCT', cfg.sl)" round>保存</el-button>
        </div>
      </div>
      <div class="config-item">
        <div class="config-label">最大实例数</div>
        <div class="config-input">
          <el-input v-model="cfg.maxInst" placeholder="5" size="small" />
          <el-button size="small" type="primary" @click="save('FUTURES_MAX_INSTANCES', cfg.maxInst)" round>保存</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const cfg = ref({ leverage: '100', tp: '0.015', sl: '0.10', maxInst: '5' })

async function load() {
  const keys = ['FUTURES_DEFAULT_LEVERAGE', 'FUTURES_TAKE_PROFIT_PCT', 'FUTURES_STOP_LOSS_PCT', 'FUTURES_MAX_INSTANCES']
  for (const k of keys) {
    try {
      const r = await compoundApi.getStrategyEffectiveValue(k, 'BTCUSDT')
      const v = r.value
      if (v) {
        if (k === 'FUTURES_DEFAULT_LEVERAGE') cfg.value.leverage = v
        else if (k === 'FUTURES_TAKE_PROFIT_PCT') cfg.value.tp = v
        else if (k === 'FUTURES_STOP_LOSS_PCT') cfg.value.sl = v
        else if (k === 'FUTURES_MAX_INSTANCES') cfg.value.maxInst = v
      }
    } catch {}
  }
}

async function save(key, value) {
  try {
    await compoundApi.upsertStrategyConfig(key, value, 'BTCUSDT')
    ElMessage.success('已保存')
  } catch { ElMessage.error('保存失败') }
}

onMounted(load)
</script>

<style scoped>
.info-card { margin-bottom: 20px; }
.config-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.config-item { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); padding: 16px; }
.config-label { font-size: 13px; font-weight: 500; color: var(--text-secondary); margin-bottom: 8px; }
.config-input { display: flex; gap: 8px; }
.config-input > :first-child { flex: 1; }
</style>
