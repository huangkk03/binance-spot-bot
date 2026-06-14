<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <div class="logo">B</div>
        <h1>Binance Spot Bot</h1>
        <p class="subtitle">币安现货复利交易系统</p>
      </div>

      <el-form @submit.prevent="onLogin">
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            placeholder="请输入访问密码"
            size="large"
            show-password
            autofocus
            @keyup.enter="onLogin"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="remember" size="large">
            记住我（7 天免登录）
          </el-checkbox>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          @click="onLogin"
          style="width: 100%;"
        >
          登 录
        </el-button>

        <el-alert
          v-if="error"
          :title="error"
          type="error"
          :closable="false"
          show-icon
          style="margin-top: 1rem;"
        />
      </el-form>

      <div class="hint">
        <p>默认密码: <code>admin123</code></p>
        <p class="small">⚠️ 局域网私用版本，生产环境请修改 VITE_AUTH_PASSWORD</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const password = ref('')
const remember = ref(true)  // 默认勾选
const loading = ref(false)
const error = ref('')

function onLogin() {
  if (!password.value) {
    error.value = '请输入密码'
    return
  }
  loading.value = true
  error.value = ''
  setTimeout(() => {
    const ok = auth.login(password.value, remember.value)
    if (ok) {
      ElMessage.success('登录成功')
      const redirect = route.query.redirect || '/trading'
      router.push(redirect)
    } else {
      error.value = '密码错误'
    }
    loading.value = false
  }, 300)
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f1218 0%, #1a1f2e 50%, #0f1218 100%);
}

.login-card {
  width: 420px;
  padding: 1rem;
  background: #1a1f2e;
  border: 1px solid #2a3042;
  border-radius: 12px;
}

.brand {
  text-align: center;
  margin-bottom: 2rem;
}

.logo {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #f0b90b 0%, #fcd535 100%);
  color: #1a1f2e;
  font-size: 2.5rem;
  font-weight: bold;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 1rem;
  box-shadow: 0 4px 20px rgba(240, 185, 11, 0.3);
}

.brand h1 {
  color: #f0b90b;
  font-size: 1.5rem;
  margin: 0.5rem 0 0.25rem;
}

.subtitle {
  color: #848e9c;
  font-size: 0.875rem;
  margin: 0;
}

.hint {
  margin-top: 1.5rem;
  text-align: center;
  color: #848e9c;
  font-size: 0.75rem;
}

.hint p {
  margin: 0.25rem 0;
}

.hint code {
  background: #2a3042;
  color: #f0b90b;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: monospace;
}

.hint .small {
  color: #5e6772;
  font-size: 0.7rem;
}

:deep(.el-input__wrapper) {
  background: #0f1218;
  box-shadow: 0 0 0 1px #2a3042 inset;
}

:deep(.el-input__inner) {
  color: #e0e6ed;
}

:deep(.el-checkbox__label) {
  color: #e0e6ed;
}
</style>
