// 前端配置
const env = import.meta.env

export const APP_CONFIG = {
  // 简单登录密码（生产环境必须修改）
  AUTH_PASSWORD: env.VITE_AUTH_PASSWORD || 'admin123',

  // API base URL
  API_BASE: '/api/v1',

  // WebSocket base URL
  WS_BASE: '/ws/frontend',
}
