<template>
  <el-container v-if="auth.token" class="layout">
    <el-aside width="216px" class="aside">
      <div class="brand">
        <div class="brand-mark">F</div>
        <div class="brand-text">
          <div class="brand-name">FeedWise</div>
          <div class="brand-sub">反馈 · 改进助手</div>
        </div>
      </div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon><span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/feedbacks">
          <el-icon><ChatLineSquare /></el-icon><span>用户反馈</span>
        </el-menu-item>
        <el-menu-item index="/issues">
          <el-icon><Collection /></el-icon><span>候选问题</span>
        </el-menu-item>
        <el-menu-item index="/drafts">
          <el-icon><Document /></el-icon><span>需求草稿</span>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <el-icon><List /></el-icon><span>改进任务</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><Clock /></el-icon><span>操作历史</span>
        </el-menu-item>
        <el-menu-item index="/capability">
          <el-icon><Grid /></el-icon><span>能力地图</span>
        </el-menu-item>
        <el-menu-item index="/machines">
          <el-icon><Share /></el-icon><span>状态机</span>
        </el-menu-item>
        <el-menu-item index="/dlq">
          <el-icon><Delete /></el-icon><span>死信管理</span>
        </el-menu-item>
        <el-menu-item index="/sessions">
          <el-icon><Monitor /></el-icon><span>在线会话</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">biglv666 全家桶演示</div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ route.meta.title }}</span>
        <span class="spacer" />
        <el-tag :type="roleTagType" effect="light" round>{{ roleName }}</el-tag>
        <el-avatar :size="30" class="avatar">{{ initials }}</el-avatar>
        <span class="user">{{ auth.user ? auth.user.displayName : '' }}</span>
        <el-divider direction="vertical" />
        <el-button link @click="doLogout">退出</el-button>
      </el-header>
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
  <router-view v-else />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, ChatLineSquare, Collection, Document, List, Clock, Grid, Share, Delete, Monitor } from '@element-plus/icons-vue'
import { auth, logout } from './store/auth'
import { ROLE_NAMES } from './constants'

const route = useRoute()
const router = useRouter()
const roleName = computed(() => ROLE_NAMES[auth.role] || auth.role)
const roleTagType = computed(() => ({ PM: 'warning', SUPPORT: 'success', DEV: 'primary' }[auth.role] || 'info'))
const initials = computed(() => (auth.user && auth.user.displayName ? auth.user.displayName.slice(0, 1) : '?'))

async function doLogout() {
  await logout()
  router.push('/login')
}
</script>

<style>
:root {
  --el-color-primary: #2f6bff;
  --el-border-radius-base: 8px;
}
body { margin: 0; background: #f4f6fa; font-family: 'Helvetica Neue', 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.layout { height: 100vh; }
.aside { background: #fff; border-right: 1px solid #e8ecf3; display: flex; flex-direction: column; }
.brand { display: flex; align-items: center; gap: 10px; padding: 18px 18px 14px; }
.brand-mark {
  width: 36px; height: 36px; border-radius: 10px; color: #fff; font-weight: 800; font-size: 20px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #2f6bff 0%, #6d5df6 100%);
  box-shadow: 0 4px 10px rgba(47, 107, 255, .35);
}
.brand-name { font-size: 17px; font-weight: 700; color: #1f2d3d; line-height: 1.2; }
.brand-sub { font-size: 11px; color: #98a2b3; }
.menu { border-right: none; padding: 4px 10px; flex: 1; }
.menu .el-menu-item { border-radius: 8px; margin: 3px 0; height: 42px; color: #4b5565; }
.menu .el-menu-item.is-active { background: #eef3ff; color: var(--el-color-primary); font-weight: 600; }
.menu .el-menu-item:hover { background: #f3f6fb; }
.aside-footer { padding: 14px 18px; font-size: 11px; color: #b6bfce; border-top: 1px solid #f0f2f7; }
.header {
  background: #fff; border-bottom: 1px solid #e8ecf3; display: flex; align-items: center; gap: 10px;
  height: 56px;
}
.header .title { font-weight: 700; font-size: 16px; color: #1f2d3d; }
.spacer { flex: 1; }
.avatar { background: linear-gradient(135deg, #2f6bff, #6d5df6); color: #fff; font-size: 13px; }
.user { color: #4b5565; font-size: 13px; }
.main { padding: 18px 22px; }
.page-card { margin-bottom: 16px; border-radius: 10px; border: 1px solid #e8ecf3; }
.page-card .el-card__header { padding: 12px 16px; font-weight: 600; color: #1f2d3d; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; align-items: center; }
.el-table { --el-table-header-bg-color: #f7f9fc; --el-table-header-text-color: #667085; }
.el-table th.el-table__cell { font-weight: 600; }
</style>
