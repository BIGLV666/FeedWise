import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理：前端站内的接口入口统一走代理转发到后端，避免跨端口裸跳
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 后端端口默认 8080，可用 BACKEND_PORT 环境变量覆盖（如本机 8080 被占时 SERVER_PORT=8081）
      '/api': `http://localhost:${process.env.BACKEND_PORT || 8080}`,
      '/web-common': `http://localhost:${process.env.BACKEND_PORT || 8080}`,
      '/actuator': `http://localhost:${process.env.BACKEND_PORT || 8080}`,
      '/api-governance': `http://localhost:${process.env.BACKEND_PORT || 8080}`
    }
  }
})