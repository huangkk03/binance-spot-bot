<template>
  <div>
    <div class="config-grid">
      <el-form label-width="80px">
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
          <el-button type="primary" @click="save" round>保存配置</el-button>
          <el-button @click="testConnection" round>测试连接</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const form = ref({ url: 'https://api.openai.com/v1', key: '', model: 'gpt-3.5-turbo' })

onMounted(async () => {
  try {
    form.value.url = (await compoundApi.getConfigValue('AI_API_URL')).value || form.value.url
    form.value.key = (await compoundApi.getConfigValue('AI_API_KEY')).value || ''
    form.value.model = (await compoundApi.getConfigValue('AI_API_MODEL')).value || form.value.model
  } catch {}
})

async function save() {
  await compoundApi.setConfigValue('AI_API_URL', form.value.url)
  await compoundApi.setConfigValue('AI_API_KEY', form.value.key)
  await compoundApi.setConfigValue('AI_API_MODEL', form.value.model)
  ElMessage.success('已保存')
}

async function testConnection() {
  try {
    const r = await compoundApi.testAi(form.value.url, form.value.key, form.value.model)
    ElMessage({ message: r.success ? '连接成功' : '失败: ' + (r.error || r.status), type: r.success ? 'success' : 'error' })
  } catch (e) { ElMessage.error('测试失败') }
}
</script>

<style scoped>
.config-grid { max-width: 560px; }
</style>
