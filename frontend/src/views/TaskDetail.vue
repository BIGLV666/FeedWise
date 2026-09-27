<template>
  <div v-loading="loading">
    <el-card class="page-card">
      <template #header>
        <div style="display:flex; align-items:center; gap:10px">
          <span>改进任务 #{{ id }}</span>
          <StateTag :value="detail && detail.task ? detail.task.status : ''" :dict="TASK_STATUS" />
          <el-tag size="small" :type="detail && detail.task && detail.task.priority === 'P1' ? 'danger' : 'info'">
            {{ detail && detail.task ? detail.task.priority : '' }}
          </el-tag>
          <span class="spacer" />
          <template v-if="(auth.isDev || auth.isPM) && detail && detail.task && detail.task.status !== 'DONE'">
            <el-button v-for="ev in allowedEvents" :key="ev.event" size="small" :type="ev.event === 'PASS' ? 'success' : ev.event === 'REJECT' ? 'danger' : 'primary'"
              plain @click="fireDialog(ev)">
              {{ ev.label }}
            </el-button>
          </template>
        </div>
      </template>
      <el-empty v-if="!loading && !detail" description="任务不存在或未指派给你（40904）" />
      <template v-else-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="标题" :span="2">{{ detail.task.title }}</el-descriptions-item>
          <el-descriptions-item label="任务说明" :span="2">{{ detail.task.detail || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源草稿">#{{ detail.task.draftId }}</el-descriptions-item>
          <el-descriptions-item label="来源候选问题">#{{ detail.task.issueId }}</el-descriptions-item>
          <el-descriptions-item label="指派给">用户#{{ detail.task.assigneeId }}</el-descriptions-item>
          <el-descriptions-item label="验证结果">{{ detail.task.verifyResult || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-card>

    <el-card>
      <template #header>时间线（操作日志 + 状态机流转历史）</template>
      <TimelineList :items="detail ? detail.timeline : []" />
    </el-card>

    <el-dialog v-model="fireVisible" :title="`确认操作：${currentEvent && currentEvent.label}`" width="440">
      <el-input v-model="fireNote" type="textarea" :rows="3"
        :placeholder="currentEvent && (currentEvent.event === 'PASS' || currentEvent.event === 'REJECT') ? '验证结果（必填）' : '处理说明（可选）'" />
      <template #footer>
        <el-button @click="fireVisible = false">取消</el-button>
        <el-button type="primary" @click="fire">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { auth } from '../store/auth'
import { TASK_EVENTS, TASK_STATUS } from '../constants'
import StateTag from '../components/StateTag.vue'
import TimelineList from '../components/TimelineList.vue'

const route = useRoute()
const id = computed(() => route.params.id)
const loading = ref(false)
const detail = ref(null)
const fireVisible = ref(false)
const fireNote = ref('')
const currentEvent = ref(null)

onMounted(load)

// 只展示当前状态允许的事件，禁跳由后端状态机兜底
const allowedEvents = computed(() => {
  if (!detail.value || !detail.value.task) return []
  return TASK_EVENTS.filter((ev) => ev.from === detail.value.task.status)
})

async function load() {
  loading.value = true
  try {
    detail.value = await request.get(`/api/tasks/${id.value}`)
  } catch (ignored) {
  } finally {
    loading.value = false
  }
}

function fireDialog(ev) {
  currentEvent.value = ev
  fireNote.value = ''
  fireVisible.value = true
}

async function fire() {
  if ((currentEvent.value.event === 'PASS' || currentEvent.value.event === 'REJECT') && !fireNote.value.trim()) {
    ElMessage.warning('验证通过/不通过必须填写验证结果')
    return
  }
  try {
    await request.post(`/api/tasks/${id.value}/fire`, { event: currentEvent.value.event, note: fireNote.value })
    ElMessage.success(`已执行：${currentEvent.value.label}`)
    fireVisible.value = false
    load()
  } catch (ignored) {
  }
}
</script>
