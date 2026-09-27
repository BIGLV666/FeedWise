<template>
  <div v-loading="loading">
    <el-card class="page-card">
      <template #header>
        <span>反馈原文 #{{ id }}（原文不可修改/删除）</span>
      </template>
      <el-empty v-if="!loading && !detail" description="反馈不存在或无权查看（40904）" />
      <template v-else-if="detail">
        <p class="content">{{ detail.feedback.content }}</p>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="来源">{{ SOURCES[detail.feedback.source] }}</el-descriptions-item>
          <el-descriptions-item label="模块">{{ MODULES[detail.feedback.module] }}</el-descriptions-item>
          <el-descriptions-item label="状态"><StateTag :value="detail.feedback.status" :dict="FEEDBACK_STATUS" /></el-descriptions-item>
          <el-descriptions-item label="客户标签">{{ detail.feedback.customerTag || '-' }}</el-descriptions-item>
          <el-descriptions-item label="录入人ID">{{ detail.feedback.createdBy }}</el-descriptions-item>
          <el-descriptions-item label="录入时间">{{ fmt(detail.feedback.createdAt) }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-card>
    <el-card>
      <template #header>时间线</template>
      <TimelineList :items="detail ? detail.timeline : []" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import request from '../api/request'
import { FEEDBACK_STATUS, MODULES, SOURCES } from '../constants'
import StateTag from '../components/StateTag.vue'
import TimelineList from '../components/TimelineList.vue'

const route = useRoute()
const id = computed(() => route.params.id)
const loading = ref(false)
const detail = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    detail.value = await request.get(`/api/feedbacks/${id.value}`)
  } catch (ignored) {
  } finally {
    loading.value = false
  }
})

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}
</script>

<style scoped>
.content { font-size: 15px; line-height: 1.7; background: #f8f9fb; padding: 12px; border-radius: 6px; }
</style>
