<template>
  <div>
    <el-alert
      v-if="proxyStatus && !proxyStatus.configured"
      type="warning"
      :closable="false"
      show-icon
      class="alert-card"
    >
      <template #title><strong>未配置 Binance 代理</strong></template>
      国内服务器需配置 <code>BINANCE_PROXY_URL</code> 环境变量才能连接 Binance API。
      当前值: <code>{{ proxyStatus.value || '(空)' }}</code>
    </el-alert>

    <div class="actions-bar">
      <el-button type="primary" @click="openDialog()" round>
        <el-icon :size="16"><Plus /></el-icon>
        添加 API 账户
      </el-button>
      <el-button @click="refreshList" round>刷新</el-button>
    </div>

    <el-table :data="accounts" size="small" stripe>
      <el-table-column prop="id" label="#" width="48" align="center" />
      <el-table-column prop="account_name" label="账户名" min-width="120" />
      <el-table-column label="API Key" min-width="180">
        <template #default="{ row }">
          <code class="key-text">{{ row.api_key?.slice(0, 12) }}···{{ row.api_key?.slice(-4) }}</code>
        </template>
      </el-table-column>
      <el-table-column label="Secret" width="140">
        <template #default="{ row }">
          <code class="key-text">{{ row.api_secret_masked }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="testnet" label="网络" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.testnet ? 'warning' : 'success'" size="small" round>
            {{ row.testnet ? 'Testnet' : 'Mainnet' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="is_active" label="激活" width="72" align="center">
        <template #default="{ row }">
          <span class="status-dot" :class="{ active: row.is_active }" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)" text>编辑</el-button>
          <el-button v-if="!row.is_active" size="small" type="primary" @click="activateAccount(row)" text>激活</el-button>
          <el-button size="small" type="danger" @click="deleteAccount(row)" text>删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑账户' : '添加账户'" width="460px">
      <el-form :model="form" label-width="80px" label-position="top">
        <el-form-item label="账户名">
          <el-input v-model="form.account_name" placeholder="如：主账户" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.api_key" :disabled="!!form.id" placeholder="输入 Binance API Key" />
        </el-form-item>
        <el-form-item label="API Secret">
          <el-input v-model="form.api_secret" type="password" show-password :placeholder="form.id ? '留空则不修改' : '输入 API Secret'" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="网络">
              <el-switch v-model="form.testnet" active-text="Testnet" inactive-text="Mainnet" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="使用代理">
              <el-switch v-model="form.use_proxy" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="form.use_proxy" label="代理 URL">
          <el-input v-model="form.proxy_url" placeholder="http://127.0.0.1:7890" />
        </el-form-item>
        <el-form-item label="立即激活">
          <el-switch v-model="form.is_active" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false" round>取消</el-button>
        <el-button @click="testConnection" round :loading="testing">测试连接</el-button>
        <el-button type="primary" @click="save" round :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage, ElMessageBox } from 'element-plus'

const accounts = ref([])
const dialogVisible = ref(false)
const form = ref({})
const proxyStatus = ref(null)
const saving = ref(false)
const testing = ref(false)

async function refreshList() {
  accounts.value = await compoundApi.listAccounts()
}

async function loadProxyStatus() {
  try { proxyStatus.value = await compoundApi.getProxyStatus() } catch {}
}

function openDialog(row) {
  form.value = row ? { ...row, api_secret: '' } : { account_name: '', api_key: '', api_secret: '', testnet: true, use_proxy: false, proxy_url: '', is_active: false }
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (form.value.id) {
      const data = { ...form.value }
      if (!data.api_secret) delete data.api_secret
      await compoundApi.updateAccount(form.value.id, data)
    } else {
      await compoundApi.createAccount(form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await refreshList()
  } catch (e) {
    let msg = ''
    if (e.response) {
      const d = e.response.data
      msg = 'HTTP ' + e.response.status + ': ' + (typeof d === 'string' ? d : Object.entries(d || {}).map(([k, v]) => k + ': ' + (Array.isArray(v) ? v.join(', ') : v)).join('; '))
    } else {
      msg = e.message
    }
    ElMessage.error('保存失败: ' + msg)
  } finally { saving.value = false }
}

async function testConnection() {
  if (!form.value.api_key || !form.value.api_secret) { ElMessage.warning('请填写 API Key 和 Secret'); return }
  testing.value = true
  try {
    const result = await compoundApi.testAccount({
      api_key: form.value.api_key, api_secret: form.value.api_secret,
      testnet: form.value.testnet, use_proxy: form.value.use_proxy, proxy_url: form.value.proxy_url
    })
    ElMessage({ message: result.success ? '连接成功' : '连接失败: ' + result.message, type: result.success ? 'success' : 'error' })
  } catch (e) {
    ElMessage.error('测试异常: ' + e.message)
  } finally { testing.value = false }
}

async function activateAccount(row) {
  await compoundApi.activateAccount(row.id)
  ElMessage.success('已激活')
  await refreshList()
}

async function deleteAccount(row) {
  await ElMessageBox.confirm('确定删除账户 "' + row.account_name + '"？', '确认删除')
  await compoundApi.deleteAccount(row.id)
  ElMessage.success('已删除')
  await refreshList()
}

onMounted(() => { refreshList(); loadProxyStatus() })
</script>

<style scoped>
.alert-card { margin-bottom: 16px; }

.actions-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.key-text {
  background: var(--bg-accent-dim);
  color: var(--text-secondary);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
}

.status-dot {
  display: inline-block;
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
}
.status-dot.active { background: var(--positive); box-shadow: 0 0 8px var(--positive); }

:deep(.el-dialog__body) { padding-top: 8px; }
:deep(.el-form-item) { margin-bottom: 14px; }
:deep(.el-form-item__label) { padding-bottom: 2px; font-size: 12px; color: var(--text-secondary); }
</style>
