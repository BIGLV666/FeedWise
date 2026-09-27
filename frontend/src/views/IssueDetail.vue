<template>
  <div v-loading="loading">
    <el-card class="page-card">
      <template #header>
        <div style="display:flex; align-items:center; gap:10px">
          <span>候选问题 #{{ id }}</span>
          <StateTag :value="detail && detail.issue ? detail.issue.status : ''" :dict="ISSUE_STATUS" />
          <el-tag v-if="detail && detail.issue && detail.issue.aiGenerated" type="warning" size="small" effect="plain">AI 生成</el-tag>
          <span class="spacer" />
          <template v-if="auth.isPM && detail && detail.issue && detail.issue.status === 'PENDING_REVIEW'">
            <el-button type="success" @click="confirmIssue">确认成立</el-button>
            <el-button type="danger" plain @click="rejectVisible = true">驳回</el-button>
            <el-button type="warning" plain @click="mergeVisible = true">合并到其他卡</el-button>
          </template>
          <el-button v-if="auth.isPM && detail && detail.issue && detail.issue.status === 'CONFIRMED' && detail.activeDraftId < 0"
            type="primary" :loading="draftLoading" @click="aiDraft">AI 起草需求</el-button>
        </div>
      </template>
      <el-empty v-if="!loading && !detail" description="候选问题不存在" />
      <template v-else-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="标题">{{ detail.issue.title }}</el-descriptions-item>
          <el-descriptions-item label="模块">{{ MODULES[detail.issue.module] }}</el-descriptions-item>
          <el-descriptions-item label="问题现象" :span="2">{{ detail.issue.problem }}</el-descriptions-item>
          <el-descriptions-item label="用户诉求" :span="2">{{ detail.issue.demand || '-' }}</el-descriptions-item>
          <el-descriptions-item label="AI 相似提示" :span="2">{{ detail.issue.similarNote || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="detail.activeDraftId >= 0" type="info" :closable="false" style="margin-top: 12px"
          :title="`该问题已有需求草稿 #${detail.activeDraftId}，去「需求草稿」页处理`" />
      </template>
    </el-card>

    <el-card class="page-card">
      <template #header>原始反馈回查（每张候选卡片必须能回到原文）</template>
      <el-empty v-if="detail && (!detail.feedbacks || detail.feedbacks.length === 0)" description="无关联反馈（手工建卡）" :image-size="60" />
      <el-table v-else-if="detail" :data="detail.feedbacks" stripe size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="content" label="脱敏原文" min-width="400" show-overflow-tooltip />
        <el-table-column label="模块" width="110">
          <template #default="{ row }">{{ MODULES[row.module] }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">{{ SOURCES[row.source] }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <template #header>时间线</template>
      <TimelineList :items="detail ? detail.timeline : []" />
    </el-card>

    <el-dialog v-model="rejectVisible" title="驳回候选问题" width="420">
      <el-input v-model="note" type="textarea" :rows="3" placeholder="驳回原因（AI 误判时请说明）" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="rejectIssue">驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mergeVisible" title="合并候选问题" width="440">
      <el-form label-width="90">
        <el-form-item label="目标卡片ID"><el-input v-model="mergeTarget" placeholder="已确认或待确认的目标卡片 id" /></el-form-item>
        <el-form-item label="合并说明"><el-input v-model="note" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mergeVisible = false">取消</el-button>
        <el-button type="warning" @click="mergeIssue">合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { auth } from '../store/auth'
import { ISSUE_STATUS, MODULES, SOURCES } from '../constants'
import StateTag from '../components/StateTag.vue'
import TimelineList from '../components/TimelineList.vue'

const route = useRoute()
const id = computed(() => route.params.id)
const loading = ref(false)
const detail = ref(null)
const note = ref('')
const rejectVisible = ref(false)
const mergeVisible = ref(false)
const mergeTarget = ref('')
const draftLoading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    detail.value = await request.get(`/api/issues/${id.value}`)
  } catch (ignored) {
  } finally {
    loading.value = false
  }
}

async function confirmIssue() {
  await request.post(`/api/issues/${id.value}/confirm`, { note: note.value || '确认问题成立' })
  ElMessage.success('已确认，可发起 AI 起草需求')
  load()
}

async function rejectIssue() {
  await request.post(`/api/issues/${id.value}/reject`, { note: note.value || '驳回' })
  ElMessage.success('已驳回')
  rejectVisible.value = false
  load()
}

async function mergeIssue() {
  if (!mergeTarget.value) {
    ElMessage.warning('请输入目标卡片 id')
    return
  }
  await request.post(`/api/issues/${id.value}/merge`, { targetId: Number(mergeTarget.value), note: note.value })
  ElMessage.success('已合并')
  mergeVisible.value = false
  load()
}

async function aiDraft() {
  draftLoading.value = true
  try {
    const result = await request.post('/api/ai/draft', { issueId: Number(id.value) })
    ElMessage.success(`AI 需求草稿已生成 #${result.draftId}，请到「需求草稿」确认`)
    load()
  } catch (ignored) {
  } finally {
    draftLoading.value = false
  }
}
</script>
