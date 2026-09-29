import { reactive } from 'vue'
import request from '../api/request'

/**
 * 登录态（token + 用户信息），localStorage 持久化。
 */
export const auth = reactive({
  token: localStorage.getItem('feedwise.token') || '',
  user: JSON.parse(localStorage.getItem('feedwise.user') || 'null'),
  get role() {
    return this.user ? this.user.role : ''
  },
  get isPM() {
    return this.role === 'PM'
  },
  get isSupport() {
    return this.role === 'SUPPORT'
  },
  get isDev() {
    return this.role === 'DEV'
  }
})

export async function login(username, password, rememberMe = false) {
  const data = await request.post('/api/auth/login', { username, password, rememberMe })
  auth.token = data.token
  auth.user = data.user
  localStorage.setItem('feedwise.token', data.token)
  localStorage.setItem('feedwise.user', JSON.stringify(data.user))
  return data.user
}

export async function logout() {
  try {
    await request.post('/api/auth/logout')
  } catch (ignored) {
    // token 已失效也照常清理本地
  }
  auth.token = ''
  auth.user = null
  localStorage.removeItem('feedwise.token')
  localStorage.removeItem('feedwise.user')
}
