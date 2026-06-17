<template>
  <div class="login-page">
    <!-- 背景动效球 -->
    <div class="bg-balls">
      <div class="ball ball-1"></div>
      <div class="ball ball-2"></div>
      <div class="ball ball-3"></div>
    </div>

    <div class="overlay"></div>

    <div class="login-card">
      <div class="card-inner">
        <div class="brand">
          <div class="brand-logo">B</div>
          <h1>Binance Spot Bot</h1>
          <p class="subtitle">币安现货复利交易系统</p>
        </div>

        <div class="form">
          <div class="input-group">
            <el-icon :size="18" class="input-icon"><Lock /></el-icon>
            <input
              v-model="password"
              type="password"
              placeholder="请输入访问密码"
              @keyup.enter="onLogin"
              autofocus
            />
          </div>

          <label class="checkbox-label">
            <input type="checkbox" v-model="remember" />
            <span>记住我（7 天免登录）</span>
          </label>

          <button class="login-btn" :class="{ loading }" @click="onLogin" :disabled="loading">
            {{ loading ? '验证中...' : '登 录' }}
          </button>

          <div v-if="error" class="error-msg">
            <el-icon :size="14"><WarningFilled /></el-icon>
            {{ error }}
          </div>
        </div>

        <div class="hint">
          默认密码: <code>admin123</code>
        </div>
      </div>
    </div>
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
const remember = ref(true)
const loading = ref(false)
const error = ref('')

function onLogin() {
  if (!password.value) { error.value = '请输入密码'; return }
  loading.value = true
  error.value = ''
  setTimeout(() => {
    const ok = auth.login(password.value, remember.value)
    if (ok) {
      ElMessage.success('登录成功')
      router.push(route.query.redirect || '/trading')
    } else {
      error.value = '密码错误'
    }
    loading.value = false
  }, 300)
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
  position: relative;
  overflow: hidden;
}

/* ========== 背景动效球 ========== */
.bg-balls {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.ball {
  position: absolute;
  border-radius: 50%;
  opacity: 0.12;
  filter: blur(80px);
}

.ball-1 {
  width: 400px;
  height: 400px;
  background: var(--accent);
  top: -100px;
  right: -100px;
  animation: float1 12s ease-in-out infinite;
}

.ball-2 {
  width: 300px;
  height: 300px;
  background: var(--positive);
  bottom: -80px;
  left: -60px;
  animation: float2 15s ease-in-out infinite;
}

.ball-3 {
  width: 200px;
  height: 200px;
  background: var(--accent);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: float3 10s ease-in-out infinite;
}

@keyframes float1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(-40px, 30px) scale(1.1); }
  66% { transform: translate(20px, -20px) scale(0.9); }
}

@keyframes float2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -20px) scale(1.15); }
  66% { transform: translate(-20px, 10px) scale(0.85); }
}

@keyframes float3 {
  0%, 100% { transform: translate(-50%, -50%) scale(1); }
  50% { transform: translate(-50%, -50%) scale(1.3); }
}

.overlay {
  position: absolute;
  inset: 0;
  backdrop-filter: blur(100px);
  z-index: 1;
}

/* ========== 卡片 ========== */
.login-card {
  position: relative;
  z-index: 2;
  width: 380px;
  animation: fadeIn 0.6s ease-out;
}

.card-inner {
  background: rgba(21, 25, 34, 0.85);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 40px 32px;
  box-shadow: var(--shadow-hover);
}

/* ========== 品牌 ========== */
.brand {
  text-align: center;
  margin-bottom: 32px;
}

.brand-logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 16px;
  background: linear-gradient(135deg, var(--accent) 0%, #fcd535 100%);
  color: #1a1f2e;
  font-size: 24px;
  font-weight: 700;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 20px rgba(240, 185, 11, 0.25);
}

.brand h1 {
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 6px;
}

.subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  margin: 0;
}

/* ========== 表单 ========== */
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-group {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  color: var(--text-muted);
  pointer-events: none;
  z-index: 1;
}

.input-group input {
  width: 100%;
  padding: 12px 14px 12px 42px;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
  transition: border-color var(--transition);
}

.input-group input:focus {
  border-color: var(--accent);
}

.input-group input::placeholder {
  color: var(--text-muted);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  accent-color: var(--accent);
  width: 16px;
  height: 16px;
}

.login-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, var(--accent) 0%, #fcd535 100%);
  color: #1a1f2e;
  border: none;
  border-radius: var(--radius);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all var(--transition);
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(240, 185, 11, 0.3);
}

.login-btn.loading {
  opacity: 0.7;
  cursor: not-allowed;
}

.error-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: var(--negative-bg);
  border: 1px solid rgba(246, 70, 93, 0.2);
  border-radius: var(--radius-sm);
  color: var(--negative);
  font-size: 13px;
}

.hint {
  margin-top: 24px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}

.hint code {
  background: var(--bg-accent-dim);
  color: var(--accent);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
}
</style>
