<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="logo-row">
        <div class="logo-mark">F</div>
        <span class="logo-name">FeedWise</span>
      </div>
      <p class="login-sub">企业报销软件 · 用户反馈与功能改进助手</p>
      <el-form @submit.prevent>
        <el-form-item>
          <el-input v-model="username" size="large" placeholder="用户名" @keyup.enter="doLogin" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" size="large" type="password" placeholder="密码" show-password @keyup.enter="doLogin" />
        </el-form-item>
        <el-form-item style="margin-bottom: 14px">
          <el-checkbox v-model="rememberMe">7 天免登录（auth-kit 记住我）</el-checkbox>
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="doLogin">登 录</el-button>
      </el-form>
      <el-divider><span class="divider-text">演示账号（密码 123456）</span></el-divider>
      <div class="demo-accounts">
        <button v-for="acc in accounts" :key="acc.u" class="acc-chip" type="button" @click="fill(acc.u)">
          <span class="acc-role" :data-role="acc.u.startsWith('support') ? 'S' : acc.u.startsWith('lead') ? 'L' : acc.u === 'pm' ? 'P' : 'D'"></span>
          {{ acc.label }}<span class="acc-id">{{ acc.u }}</span>
        </button>
      </div>
    </div>
    <p class="login-foot">AI 只做第一轮整理 · 决策权始终在产品经理</p>
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
const rememberMe = ref(false)

const accounts = [
  { u: 'support1', label: '客服小王' },
  { u: 'support2', label: '客服小李' },
  { u: 'lead1', label: '客服主管' },
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
    const user = await login(username.value, password.value, rememberMe.value)
    ElMessage.success(`欢迎，${user.displayName}`)
    router.push('/dashboard')
  } catch (ignored) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center;
  background:
    radial-gradient(1000px 500px at 20% -10%, rgba(109, 93, 246, .35), transparent 60%),
    radial-gradient(900px 500px at 90% 110%, rgba(47, 107, 255, .35), transparent 60%),
    linear-gradient(160deg, #101a33 0%, #1b2b52 55%, #23386b 100%);
}
.login-card {
  width: 400px; background: #fff; border-radius: 16px; padding: 34px 36px 26px;
  box-shadow: 0 20px 60px rgba(9, 20, 48, .45);
}
.logo-row { display: flex; align-items: center; justify-content: center; gap: 10px; }
.logo-mark {
  width: 40px; height: 40px; border-radius: 12px; color: #fff; font-weight: 800; font-size: 22px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #2f6bff 0%, #6d5df6 100%);
  box-shadow: 0 6px 14px rgba(47, 107, 255, .4);
}
.logo-name { font-size: 26px; font-weight: 800; color: #1f2d3d; letter-spacing: .5px; }
.login-sub { text-align: center; color: #8a94a6; font-size: 13px; margin: 8px 0 22px; }
.login-btn { width: 100%; font-weight: 600; letter-spacing: 4px; }
.divider-text { color: #a2abc0; font-size: 12px; }
.demo-accounts { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.acc-chip {
  border: 1px solid #e4e9f2; background: #f8fafd; border-radius: 10px; padding: 8px 10px;
  display: flex; align-items: center; gap: 7px; cursor: pointer; font-size: 13px; color: #3c4a5e;
  transition: all .15s ease;
}
.acc-chip:hover { border-color: #2f6bff; color: #2f6bff; background: #f0f5ff; }
.acc-id { margin-left: auto; color: #b0b9c9; font-size: 11px; }
.acc-role { width: 8px; height: 8px; border-radius: 50%; }
.acc-role[data-role="S"] { background: #34c38f; }
.acc-role[data-role="L"] { background: #13c2c2; }
.acc-role[data-role="P"] { background: #f5a623; }
.acc-role[data-role="D"] { background: #2f6bff; }
.login-foot { color: rgba(255, 255, 255, .55); font-size: 12px; margin-top: 22px; }
</style>
