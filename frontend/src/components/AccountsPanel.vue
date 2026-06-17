<template>
  <div>
    <el-alert
      v-if="proxyStatus && !proxyStatus.configured"
      type="warning"
      :closable="false"
      style="margin-bottom: 1rem;"
      show-icon
    >
      <template #title>
        <strong>未配置 Binance 代理</strong>
      </template>
      国内服务器需要配置代理才能连接 Binance API。
      请联系管理员设置 <code>BINANCE_PROXY_URL</code> 环境变量。
      <br/>当前值: <code>{{ proxyStatus.value || '(空)' }}</code>
    </el-alert>

    <div class="actions">
      <el-button type="primary" @click="openDialog()">添加 API 账户</el-button>
      <el-button @click="refreshList">刷新列表</el-button>
    </div>

    <el-table :data="accounts" stripe>
      <el-table-column prop="account_name" label="账户名" width="150" />
      <el-table-column prop="api_key" label="API Key" width="200" />
      <el-table-column prop="api_secret_masked" label="API Secret" width="180" />
      <el-table-column prop="testnet" label="网络" width="80">
        <template #default="{ row }">
          <el-tag :type="row.testnet ? 'warning' : 'success'" size="small">
            {{ row.testnet ? 'Testnet' : 'Mainnet' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="is_active" label="激活" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.is_active" type="success" size="small">激活</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button v-if="!row.is_active" size="small" type="primary" @click="activateAccount(row)">激活</el-button>
          <el-button size="small" type="danger" @click="deleteAccount(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑账户' : '添加账户'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="账户名">
          <el-input v-model="form.account_name" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.api_key" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="API Secret">
          <el-input v-model="form.api_secret" type="password" :placeholder="form.id ? '留空则不修改' : ''" />
        </el-form-item>
        <el-form-item label="网络">
          <el-switch v-model="form.testnet" active-text="Testnet" inactive-text="Mainnet" />
        </el-form-item>
        <el-form-item label="使用代理">
          <el-switch v-model="form.use_proxy" />
        </el-form-item>
        <el-form-item v-if="form.use_proxy" label="代理 URL">
          <el-input v-model="form.proxy_url" placeholder="http://127.0.0.1:7890" />
        </el-form-item>
        <el-form-item label="激活">
          <el-switch v-model="form.is_active" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button @click="testConnection">测试连接</el-button>
        <el-button type="primary" @click="save">保存</el-button>
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

async function refreshList() {
  accounts.value = await compoundApi.listAccounts()
}

async function loadProxyStatus() {
  try {
    proxyStatus.value = await compoundApi.getProxyStatus()
  } catch (e) {
    console.error('load proxy status failed:', e)
  }
}

function openDialog(row) {
  if (row) {
    form.value = { ...row, api_secret: '' }
  } else {
    form.value = {
      account_name: '',
      api_key: '',
      api_secret: '',
      testnet: true,
      use_proxy: false,
      proxy_url: '',
      is_active: false,
    }
  }
  dialogVisible.value = true
}

async function save() {
  try {
    if (form.value.id) {
      const data = { ...form.value }
      if (!data.api_secret) delete data.api_secret
      await compoundApi.updateAccount(form.value.id, data)
    } else {
      console.log('Saving new account:', { ...form.value, api_secret: '***' })
      await compoundApi.createAccount(form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await refreshList()
  } catch (e) {
    console.error('Save error:', e)
    let msg = ''
    if (e.response) {
      msg = 'HTTP ' + e.response.status + ': '
      const data = e.response.data
      if (typeof data === 'string') {
        msg += data
      } else if (data) {
        // 处理字段验证错误
        const errors = []
        for (const [field, msgs] of Object.entries(data)) {
          errors.push(field + ': ' + (Array.isArray(msgs) ? msgs.join(', ') : msgs))
        }
        msg += errors.join('; ')
      }
    } else {
      msg = e.message
    }
    ElMessage.error('保存失败: ' + msg)
  }
}

async function testConnection() {
  if (!form.value.api_key || !form.value.api_secret) {
    ElMessage.warning('请先填写 API Key 和 Secret')
    return
  }
  try {
    const result = await compoundApi.testAccount({
      api_key: form.value.api_key,
      api_secret: form.value.api_secret,
      testnet: form.value.testnet,
      use_proxy: form.value.use_proxy,
      proxy_url: form.value.proxy_url,
    })
    if (result.success) {
      ElMessage.success('连接成功: ' + result.message)
    } else {
      ElMessage.error('连接失败: ' + result.message)
    }
  } catch (e) {
    ElMessage.error('测试异常: ' + e.message)
  }
}

async function activateAccount(row) {
  await compoundApi.activateAccount(row.id)
  ElMessage.success('已激活')
  await refreshList()
}

async function deleteAccount(row) {
  await ElMessageBox.confirm(`确定删除账户 "${row.account_name}"?`, '确认删除')
  await compoundApi.deleteAccount(row.id)
  ElMessage.success('已删除')
  await refreshList()
}

onMounted(() => {
  refreshList()
  loadProxyStatus()
})
</script>

<style scoped>
.actions { margin-bottom: 1rem; }
</style>
