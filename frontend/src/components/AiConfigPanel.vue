<template>
  <div>
    <h3>AI 配置</h3>
    <el-form :model="form" label-width="120px">
      <el-form-item label="API URL">
        <el-input v-model="form.url" placeholder="https://api.openai.com/v1" />
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="form.key" type="password" show-password />
      </el-form-item>
      <el-form-item label="Model">
        <el-input v-model="form.model" placeholder="gpt-3.5-turbo" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save">保存配置</el-button>
        <el-button @click="testConnection">测试连接</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const form = ref({ url: '', key: '', model: '' })

onMounted(async () => {
  form.value.url = await compoundApi.getConfigValue('AI_API_URL').then(r => r.value || 'https://api.openai.com/v1')
  form.value.key = await compoundApi.getConfigValue('AI_API_KEY').then(r => r.value || '')
  form.value.model = await compoundApi.getConfigValue('AI_API_MODEL').then(r => r.value || 'gpt-3.5-turbo')
})

async function save() {
  await compoundApi.setConfigValue('AI_API_URL', form.value.url)
  await compoundApi.setConfigValue('AI_API_KEY', form.value.key)
  await compoundApi.setConfigValue('AI_API_MODEL', form.value.model)
  ElMessage.success('AI 配置已保存')
}

async function testConnection() {
  try {
    const result = await compoundApi.testAi(form.value.url, form.value.key, form.value.model)
    if (result.success) {
      ElMessage.success('AI 服务连接成功')
    } else {
      ElMessage.error('连接失败: ' + (result.error || result.status))
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + e.message)
  }
}
</script>
