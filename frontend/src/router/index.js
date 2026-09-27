import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
  { path: '/feedbacks', component: () => import('../views/FeedbackList.vue'), meta: { title: '用户反馈' } },
  { path: '/feedbacks/:id', component: () => import('../views/FeedbackDetail.vue'), meta: { title: '反馈详情' } },
  { path: '/issues', component: () => import('../views/IssueList.vue'), meta: { title: '候选问题' } },
  { path: '/issues/:id', component: () => import('../views/IssueDetail.vue'), meta: { title: '候选问题详情' } },
  { path: '/drafts', component: () => import('../views/DraftList.vue'), meta: { title: '需求草稿' } },
  { path: '/tasks', component: () => import('../views/TaskList.vue'), meta: { title: '改进任务' } },
  { path: '/tasks/:id', component: () => import('../views/TaskDetail.vue'), meta: { title: '任务详情' } },
  { path: '/history', component: () => import('../views/History.vue'), meta: { title: '操作历史' } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  document.title = `${to.meta.title || ''} · FeedWise`
  if (to.path !== '/login' && !localStorage.getItem('feedwise.token')) {
    return '/login'
  }
  if (to.path === '/login' && localStorage.getItem('feedwise.token')) {
    return '/dashboard'
  }
  return true
})

export default router
