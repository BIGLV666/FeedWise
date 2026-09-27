<template>
  <div>
    <el-card class="page-card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="load">
          <el-option v-for="(label, key) in DRAFT_STATUS" :key="key" :label="label" :value="key" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="issueId" label="来源问题" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StateTag :value="row.status" :dict="DRAFT_STATUS" /></template>
        </el-table-column>
        <el-table-column label="AI 生成" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.aiGenerated" type="warning" size="small" effect="plain">AI</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">手工</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button size="small" @click="open(row)">查看/编辑</el-button>
            <el-button v-if="auth.isPM && row.status === 'DRAFT'" size="small" type="success" @click="confirm(row)">确认</el-button>
            <el-button v-if="auth.isPM && row.status === 'CONFIRMED'" size="small" type="primary" @click="openConvert(row)">转任务</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination layout="prev, pager, next, total" :total="total" :page-size="query.size"
        :current-page="query.current" @current-change="changePage" style="margin-top: 12px" />
    </el-card>

    <el-dialog v-model="editVisible" title="需求草稿（AI 产物必须经 PM 确认）" width="680">
      <el-form label-width="90">
        <el-form-item label="标题"><el-input v-model="form.title" :disabled="form.status !== 'DRAFT'" /></el-form-item>
        <el-form-item label="背景"><el-input v-model="form.background" type="textarea" :rows="2" :disabled="form.status !== 'DRAFT'" /></el-form-item>
        <el-form-item label="需求说明"><el-input v-model="form.description" type="textarea" :rows="4" :disabled="form.status !== 'DRAFT'" /></el-form-item>
        <el-form-item label="验收条件">
          <div style="width: 100%">
            <div v-for="(item, i) in form.acceptance" :key="i" class="acc-row">
              <el-input v-model="form.acceptance[i]" :disabled="form.status !== 'DRAFT'" />
              <el-button link type="danger" :disabled="form.status !== 'DRAFT'" @click="form.acceptance.splice(i, 1)">删除</el-button>
            </div>
            <el-button size="small" :disabled="form.status !== 'DRAFT'" @click="form.acceptance.push('')">+ 添加验收条件</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button v-if="form.status === 'DRAFT'" type="primary" :loading="saving" @click="save">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="convertVisible" title="建立改进任务" width="480">
      <el-form label-width="90">
        <el-form-item label="指派给">
          <el-select v-model="convertForm.assigneeId" clearable style="width: 100%">
            <el-option v-for="u in devUsers" :key="u.id" :label="u.displayName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="convertForm.priority" style="width: 100%">
            <el-option label="P1 紧急" value="P1" />
            <el-option label="P2 常规" value="P2" />
            <el-option label="P3 低" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务说明"><el-input v-model="convertForm.detail" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="convert">建立任务</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { auth } from '../store/auth'
import { DRAFT_STATUS } from '../constants'
import StateTag from '../components/StateTag.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, status: '' })

const editVisible = ref(false)
const convertVisible = ref(false)
const saving = ref(false)
const form = reactive({ id: 0, title: '', background: '', description: '', acceptance: [], status: 'DRAFT' })
const convertForm = reactive({ draftId: 0, assigneeId: null, priority: 'P2', detail: '' })
const devUsers = ref([])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await request.get('/api/drafts', { params: query })
    records.value = page.records || []
    total.value = Number(page.total || 0)
  } catch (ignored) {
  } finally {
    loading.value = false
  }
}

function changePage(current) {
  query.current = current
  load()
}

async function open(row) {
  const detail = await request.get(`/api/drafts/${row.id}`)
  Object.assign(form, {
    id: detail.draft.id,
    title: detail.draft.title,
    background: detail.draft.background,
    description: detail.draft.description,
    acceptance: detail.acceptance || [],
    status: detail.draft.status
  })
  editVisible.value = true
}

async function save() {
  saving.value = true
  try {
    await request.put(`/api/drafts/${form.id}`, {
      title: form.title, background: form.background, description: form.description, acceptance: form.acceptance
    })
    ElMessage.success('草稿已更新')
    editVisible.value = false
    load()
  } catch (ignored) {
  } finally {
    saving.value = false
  }
}

async function confirm(row) {
  await request.post(`/api/drafts/${row.id}/confirm`)
  ElMessage.success('草稿已确认，可建立改进任务')
  load()
}

async function openConvert(row) {
  convertForm.draftId = row.id
  try {
    devUsers.value = await request.get('/api/drafts/dev-users')
  } catch (ignored) {
    devUsers.value = []
  }
  convertVisible.value = true
}

async function convert() {
  saving.value = true
  try {
    const taskId = await request.post(`/api/drafts/${convertForm.draftId}/convert`, {
      assigneeId: convertForm.assigneeId, priority: convertForm.priority, detail: convertForm.detail
    })
    ElMessage.success(`改进任务已建立 #${taskId}（草稿状态转为 CONVERTED）`)
    convertVisible.value = false
    load()
  } catch (ignored) {
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.acc-row { display: flex; gap: 8px; margin-bottom: 6px; }
</style>
