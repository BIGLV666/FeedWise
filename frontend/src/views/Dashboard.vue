<template>
  <div v-loading="loading">
    <el-row :gutter="16">
      <el-col :span="8" v-for="card in cards" :key="card.title">
        <el-card class="page-card">
          <template #header>{{ card.title }}</template>
          <el-empty v-if="!card.items.length" description="暂无数据" :image-size="50" />
          <div v-for="item in card.items" :key="item.key" class="stat-row">
            <el-tag size="small" :type="item.type" effect="plain">{{ item.label }}</el-tag>
            <b class="stat-num">{{ item.count }}</b>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-card>
      <template #header>关于本系统</template>
      <p style="margin: 0 0 8px">
        主链路：客服录入/导入脱敏反馈 → AI（受限工具调用）生成候选问题卡片 → 产品经理确认/合并/驳回
        → AI 起草需求草稿 → PM 确认并建立改进任务 → 开发/测试推进状态并记录验证结果，全程留痕。
      </p>
      <el-text type="info" size="small">AI 只产出待确认草稿；优先级、合并、确认均由产品经理决定。</el-text>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../api/request'
import { FEEDBACK_STATUS, ISSUE_STATUS, TASK_STATUS, MODULES } from '../constants'

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

// 统计卡片：反馈状态 / 候选问题状态 / 任务状态（扩展功能：工作台概览）
const cards = computed(() => {
  const s = snapshot.value
  const toItems = (dict, map, colorFor) =>
    Object.keys(dict).map((key) => ({
      key,
      label: dict[key],
      count: (map && map[key]) || 0,
      type: colorFor(key)
    }))
  return [
    { title: '用户反馈（按状态）', items: toItems(FEEDBACK_STATUS, s && s.feedbackByStatus, k => (k === 'UNPROCESSED' ? 'warning' : 'success')) },
    { title: '候选问题（按状态）', items: toItems(ISSUE_STATUS, s && s.issueByStatus, k => (k === 'PENDING_REVIEW' ? 'warning' : 'primary')) },
    { title: '改进任务（按状态）', items: toItems(TASK_STATUS, s && s.taskByStatus, k => (k === 'TODO' ? 'warning' : k === 'DONE' ? 'success' : 'primary')) }
  ]
})
</script>

<style scoped>
.stat-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; }
.stat-num { font-size: 18px; }
</style>
