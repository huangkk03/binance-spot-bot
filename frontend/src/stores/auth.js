import { defineStore } from 'pinia'
import { ref } from 'vue'
import { APP_CONFIG } from '../config'

const STORAGE_KEY = 'binance_auth'
const TTL_DEFAULT = 24 * 3600         // 24 小时
const TTL_REMEMBER = 7 * 24 * 3600    // 7 天

export const useAuthStore = defineStore('auth', () => {
  const authed = ref(false)
  const loginTime = ref(0)
  const rememberMe = ref(false)
  const username = ref('')

  function _ttl() {
    return rememberMe.value ? TTL_REMEMBER : TTL_DEFAULT
  }

  function initFromStorage() {
    try {
      const data = localStorage.getItem(STORAGE_KEY)
      if (!data) return false
      const parsed = JSON.parse(data)
      const ttl = parsed.rememberMe ? TTL_REMEMBER : TTL_DEFAULT
      if (Date.now() - parsed.loginTime < ttl * 1000) {
        authed.value = true
        loginTime.value = parsed.loginTime
        rememberMe.value = parsed.rememberMe
        username.value = parsed.username || ''
        return true
      }
      localStorage.removeItem(STORAGE_KEY)
    } catch (e) {
      localStorage.removeItem(STORAGE_KEY)
    }
    return false
  }

  function login(password, remember = false) {
    if (password === APP_CONFIG.AUTH_PASSWORD) {
      authed.value = true
      loginTime.value = Date.now()
      rememberMe.value = remember
      username.value = 'admin'
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        loginTime: loginTime.value,
        rememberMe: remember,
        username: username.value,
      }))
      return true
    }
    return false
  }

  function logout() {
    authed.value = false
    loginTime.value = 0
    rememberMe.value = false
    username.value = ''
    localStorage.removeItem(STORAGE_KEY)
  }

  function checkExpiry() {
    if (!authed.value) return false
    const ttl = _ttl()
    if (Date.now() - loginTime.value > ttl * 1000) {
      logout()
      return false
    }
    return true
  }

  function remainingSeconds() {
    if (!authed.value || !loginTime.value) return 0
    const ttl = _ttl()
    const remaining = ttl - Math.floor((Date.now() - loginTime.value) / 1000)
    return Math.max(0, remaining)
  }

  initFromStorage()

  return {
    authed,
    loginTime,
    rememberMe,
    username,
    login,
    logout,
    checkExpiry,
    initFromStorage,
    remainingSeconds,
  }
})
