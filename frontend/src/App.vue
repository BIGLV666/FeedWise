<template>
  <el-container v-if="auth.token" class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">FeedWise</div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item index="/dashboard"><el-icon><DataBoard /></el-icon>工作台</el-menu-item>
        <el-menu-item index="/feedbacks"><el-icon><ChatLineSquare /></el-icon>用户反馈</el-menu-item>
        <el-menu-item index="/issues"><el-icon><Collection /></el-icon>候选问题</el-menu-item>
        <el-menu-item index="/drafts"><el-icon><Document /></el-icon>需求草稿</el-menu-item>
        <el-menu-item index="/tasks"><el-icon><List /></el-icon>改进任务</el-menu-item>
        <el-menu-item index="/history"><el-icon><Clock /></el-icon>操作历史</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ route.meta.title }}</span>
        <span class="spacer" />
        <el-tag type="info" effect="plain">{{ roleName }}</el-tag>
        <span class="user">{{ auth.user ? auth.user.displayName : '' }}</span>
        <el-button link type="primary" @click="doLogout">退出</el-button>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
  <router-view v-else />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, ChatLineSquare, Collection, Document, List, Clock } from '@element-plus/icons-vue'
import { auth, logout } from './store/auth'
import { ROLE_NAMES } from './constants'

const route = useRoute()
const router = useRouter()
const roleName = computed(() => ROLE_NAMES[auth.role] || auth.role)

async function doLogout() {
  await logout()
  router.push('/login')
}
</script>

<style>
body { margin: 0; background: #f5f7fa; }
.layout { height: 100vh; }
.aside { background: #fff; border-right: 1px solid #e4e7ed; }
.logo { font-size: 20px; font-weight: 700; padding: 18px 20px; color: #409eff; }
.menu { border-right: none; }
.header { background: #fff; border-bottom: 1px solid #e4e7ed; display: flex; align-items: center; gap: 12px; }
.title { font-weight: 600; }
.spacer { flex: 1; }
.user { color: #606266; }
.page-card { margin-bottom: 16px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
