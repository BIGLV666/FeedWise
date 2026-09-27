<template>
  <div v-loading="loading">
    <el-row :gutter="16">
      <el-col :span="8" v-for="card in cards" :key="card.title">
        <el-card class="page-card stat-card">
          <div class="stat-head">
            <el-icon :size="18" :color="card.color"><component :is="card.icon" /></el-icon>
            <span class="stat-title">{{ card.title }}</span>
          </div>
          <el-empty v-if="!card.items.length" description="暂无数据" :image-size="50" />
          <div class="stat-body">
            <div v-for="item in card.items" :key="item.key" class="stat-row">
              <span class="stat-label">{{ item.label }}</span>
              <b class="stat-num" :style="{ color: card.color }">{{ item.count }}</b>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card class="page-card">
          <template #header>
            <div class="card-head"><el-icon><DataLine /></el-icon><span>反馈来源分布</span></div>
          </template>
          <el-empty v-if="!sourceItems.length" description="暂无数据" :image-size="60" />
          <div v-for="item in sourceItems" :key="item.key" class="stat-row">
            <span class="dot" :style="{ background: item.color }"></span>
            <span class="stat-label">{{ item.label }}</span>
            <el-progress :percentage="item.pct" :stroke-width="10" :show-text="false"
              style="flex: 1; margin: 0 14px" />
            <b class="stat-num small">{{ item.count }}</b>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="page-card">
          <template #header>
            <div class="card-head"><el-icon><InfoFilled /></el-icon><span>这条链路怎么跑</span></div>
          </template>
          <el-steps direction="vertical" :space="34" :active="6" class="flow-steps">
            <el-step title="客服录入 / 批量导入脱敏反馈" description="事务 outbox 事件 → RabbitMQ → AI 消费者" />
            <el-step title="AI 生成候选问题卡片" description="受限工具调用：混合反馈自动拆分，产物待确认" />
            <el-step title="产品经理确认 / 合并 / 驳回" description="必须能回查原始反馈原文" />
            <el-step title="AI 起草需求 → PM 确认 → 建改进任务" description="草稿未经确认不能变成任务（禁跳 40900）" />
            <el-step title="开发 / 测试推进状态并记录验证结果" description="待开发→开发中→待验证→已完成，全程留痕" />
          </el-steps>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChatDotRound, Collection, List, DataLine, InfoFilled, ChatLineSquare, Tickets } from '@element-plus/icons-vue'
import request from '../api/request'
import { FEEDBACK_STATUS, ISSUE_STATUS, TASK_STATUS } from '../constants'

const loading = ref(false)
const snapshot = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    snapshot.value = await request.get('/api/dashboard')
  } catch (ignored) {
  } finally {
    loading.value = false
  }
})

const toItems = (dict, map) =>
  Object.keys(dict).map((key) => ({ key, label: dict[key], count: (map && map[key]) || 0 }))

const cards = computed(() => {
  const s = snapshot.value
  return [
    { title: '用户反馈', icon: ChatLineSquare, color: '#f5a623', items: toItems(FEEDBACK_STATUS, s && s.feedbackByStatus) },
    { title: '候选问题', icon: Collection, color: '#6d5df6', items: toItems(ISSUE_STATUS, s && s.issueByStatus) },
    { title: '改进任务', icon: List, color: '#2f6bff', items: toItems(TASK_STATUS, s && s.taskByStatus) }
  ]
})

const SOURCE_COLORS = { TICKET: '#2f6bff', SURVEY: '#34c38f', CALL: '#f5a623' }
const SOURCE_NAMES = { TICKET: '客服工单', SURVEY: '使用调查', CALL: '客户沟通' }

const sourceItems = computed(() => {
  const map = (snapshot.value && snapshot.value.feedbackByModule) || {}
  // 来源分布后端未聚合，这里按模块分布展示（模块即业务关注点）
  const names = { INVOICE_UPLOAD: '发票上传', REIMBURSE_FORM: '报销单填写', APPROVAL: '审批进度', RETURN_MODIFY: '退回修改', OTHER: '其他' }
  const entries = Object.keys(names).map((key) => ({ key, label: names[key], count: map[key] || 0 }))
  const total = entries.reduce((sum, e) => sum + e.count, 0) || 1
  return entries.map((e) => ({ ...e, pct: Math.round((e.count / total) * 100), color: SOURCE_COLORS[e.key] || '#98a2b3' }))
})
</script>

<style scoped>
.stat-card { padding-bottom: 6px; }
.stat-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.stat-title { font-weight: 700; color: #1f2d3d; font-size: 15px; }
.stat-body { max-height: 168px; }
.stat-row { display: flex; justify-content: space-between; align-items: center; padding: 7px 0; }
.stat-label { color: #4b5565; font-size: 13px; }
.stat-num { font-size: 20px; font-weight: 800; }
.stat-num.small { font-size: 15px; color: #1f2d3d; }
.card-head { display: flex; align-items: center; gap: 7px; font-weight: 600; }
.dot { width: 8px; height: 8px; border-radius: 50%; margin-right: 8px; }
.flow-steps :deep(.el-step__title) { font-size: 13px; font-weight: 600; }
.flow-steps :deep(.el-step__description) { font-size: 12px; }
</style>
