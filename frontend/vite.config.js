import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理：/api 与 /web-common 转发到本地后端 8080
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 后端端口默认 8080，可用 BACKEND_PORT 环境变量覆盖（如本机 8080 被占时 SERVER_PORT=8081）
      '/api': `http://localhost:${process.env.BACKEND_PORT || 8080}`,
      '/web-common': `http://localhost:${process.env.BACKEND_PORT || 8080}`
    }
  }
})
