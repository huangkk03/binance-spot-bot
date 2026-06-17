<template>
  <div>
    <div class="config-grid">
      <el-form label-width="120px">
        <el-form-item label="Webhook URL">
          <el-input v-model="form.url" placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=..." />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="save" round>保存</el-button>
          <el-button @click="test" round>发送测试通知</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { compoundApi } from '../api/compound'
import { ElMessage } from 'element-plus'

const form = ref({ url: '' })

onMounted(async () => {
  try { form.value.url = (await compoundApi.getConfigValue('WECHAT_WEBHOOK_URL')).value || '' } catch {}
})

async function save() {
  await compoundApi.setConfigValue('WECHAT_WEBHOOK_URL', form.value.url)
  ElMessage.success('已保存')
}

async function test() {
  if (!form.value.url) { ElMessage.warning('请先填写 URL'); return }
  await compoundApi.setConfigValue('WECHAT_WEBHOOK_URL', form.value.url)
  await compoundApi.testNotification('测试', '这是一条来自 Binance Spot Bot 的测试通知。')
  ElMessage.success('已发送')
}
</script>

<style scoped>
.config-grid { max-width: 560px; }
</style>
