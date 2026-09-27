<template>
  <div>
    <el-card class="page-card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 160px" @change="load">
          <el-option v-for="(label, key) in ISSUE_STATUS" :key="key" :label="label" :value="key" />
        </el-select>
        <el-select v-model="query.module" placeholder="模块" clearable style="width: 140px" @change="load">
          <el-option v-for="(label, key) in MODULES" :key="key" :label="label" :value="key" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <span class="spacer" />
        <el-button v-if="auth.isPM || auth.isSupport" @click="manualVisible = true">手工建卡（AI 不可用兜底）</el-button>
      </div>
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="标题" min-width="220">
          <template #default="{ row }">
            <router-link :to="`/issues/${row.id}`" class="link">{{ row.title }}</router-link>
          </template>
        </el-table-column>
        <el-table-column label="模块" width="110">
          <template #default="{ row }">{{ MODULES[row.module] || row.module }}</template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }"><StateTag :value="row.status" :dict="ISSUE_STATUS" /></template>
        </el-table-column>
        <el-table-column label="AI 生成" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.aiGenerated" type="warning" size="small" effect="plain">AI</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">手工</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="similarNote" label="AI 相似提示" min-width="200" show-overflow-tooltip />
      </el-table>
      <el-pagination layout="prev, pager, next, total" :total="total" :page-size="query.size"
        :current-page="query.current" @current-change="changePage" style="margin-top: 12px" />
    </el-card>

    <el-dialog v-model="manualVisible" title="手工创建候选问题" width="560">
      <el-form label-width="90">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="模块">
          <el-select v-model="form.module" style="width: 100%">
            <el-option v-for="(label, key) in MODULES" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题现象"><el-input v-model="form.problem" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="用户诉求"><el-input v-model="form.demand" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="关联反馈ID">
          <el-input v-model="feedbackIdsText" placeholder="逗号分隔的反馈ID，可留空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { auth } from '../store/auth'
import { ISSUE_STATUS, MODULES } from '../constants'
import StateTag from '../components/StateTag.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, status: '', module: '' })

const manualVisible = ref(false)
const saving = ref(false)
const form = reactive({ title: '', module: 'OTHER', problem: '', demand: '' })
const feedbackIdsText = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await request.get('/api/issues', { params: query })
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

async function save() {
  const feedbackIds = feedbackIdsText.value.split(/[,，\s]+/).filter(Boolean).map(Number)
  saving.value = true
  try {
    await request.post('/api/issues/manual', { ...form, feedbackIds })
    ElMessage.success('候选问题已创建（待产品经理确认）')
    manualVisible.value = false
    load()
  } catch (ignored) {
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.link { color: #409eff; text-decoration: none; }
</style>
