<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="login-title">FeedWise</h2>
      <p class="login-sub">企业报销软件 · 用户反馈与功能改进助手</p>
      <el-form @submit.prevent>
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" @keyup.enter="doLogin" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" show-password @keyup.enter="doLogin" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="doLogin">登录</el-button>
      </el-form>
      <el-divider>演示账号（密码 123456）</el-divider>
      <div class="demo-accounts">
        <el-button v-for="acc in accounts" :key="acc.u" size="small" @click="fill(acc.u)">
          {{ acc.label }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../store/auth'

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)

const accounts = [
  { u: 'support1', label: '客服小王' },
  { u: 'support2', label: '客服小李' },
  { u: 'pm', label: '产品经理' },
  { u: 'dev1', label: '开发张工' }
]

function fill(u) {
  username.value = u
  password.value = '123456'
}

async function doLogin() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const user = await login(username.value, password.value)
    ElMessage.success(`欢迎，${user.displayName}（${user.role}）`)
    router.push('/dashboard')
  } catch (ignored) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f0f2f5; }
.login-card { width: 380px; }
.login-title { text-align: center; color: #409eff; margin-bottom: 4px; }
.login-sub { text-align: center; color: #909399; font-size: 13px; margin-top: 0; }
.demo-accounts { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
</style>
