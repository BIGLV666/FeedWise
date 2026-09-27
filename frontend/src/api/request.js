import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * 统一请求封装：自动携带 token；按统一 Result 的 code 分流六类反馈形态：
 * 0 成功 / 40100 未登录跳登录 / 40300 无权限 / 40900 状态冲突 / 40906 重复提交 / 其他失败。
 */
const request = axios.create({ timeout: 30000 })

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('feedwise.token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body == null || typeof body.code !== 'number') {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    if (body.code === 40100) {
      ElMessage.warning('登录已失效，请重新登录')
      localStorage.removeItem('feedwise.token')
      localStorage.removeItem('feedwise.user')
      router.push('/login')
    } else if (body.code === 40300) {
      ElMessage.warning('无权限执行该操作（40300）')
    } else if (body.code === 40900) {
      ElMessage.error(`状态冲突（40900）：${body.message}`)
    } else if (body.code === 40906) {
      ElMessage.warning(body.message || '请勿重复提交（40906）')
    } else if (body.code === 50301) {
      ElMessage.error('AI 模型暂不可用，请改用手工分类（50301）')
    } else {
      ElMessage.error(body.message || `请求失败（${body.code}）`)
    }
    return Promise.reject(new Error(body.message || `code=${body.code}`))
  },
  (error) => {
    // HTTP 层错误（如 api-governance 限流 429）
    if (error.response && error.response.status === 429) {
      ElMessage.error('请求过于频繁，请稍后再试（429）')
    } else {
      ElMessage.error('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
