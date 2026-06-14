<template>
  <div>
    <h3>通知配置（企业微信 / 钉钉）</h3>
    <el-form :model="form" label-width="160px">
      <el-form-item label="Webhook URL">
        <el-input v-model="form.url" placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=..." />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save">保存</el-button>
        <el-button @click="test">发送测试通知</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const form = ref({ url: '' })

onMounted(async () => {
  const r = await compoundApi.getConfigValue('WECHAT_WEBHOOK_URL').catch(() => null)
  form.value.url = r?.value || ''
})

async function save() {
  await compoundApi.setConfigValue('WECHAT_WEBHOOK_URL', form.value.url)
  ElMessage.success('已保存')
}

async function test() {
  if (!form.value.url) {
    ElMessage.warning('请先填写 Webhook URL')
    return
  }
  await compoundApi.setConfigValue('WECHAT_WEBHOOK_URL', form.value.url)
  await compoundApi.testNotification('测试', '这是一条来自 Binance Spot Bot 的测试通知。')
  ElMessage.success('测试通知已发送')
}
</script>
