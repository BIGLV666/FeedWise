<template>
  <div>
    <el-card class="page-card">
      <div class="toolbar">
        <el-select v-model="query.module" placeholder="功能模块" clearable style="width: 140px">
          <el-option v-for="(label, key) in MODULES" :key="key" :label="label" :value="key" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="未处理" value="UNPROCESSED" />
          <el-option label="已归档" value="PROCESSED" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="按内容搜索" clearable style="width: 220px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <span class="spacer" />
        <el-button v-if="auth.isSupport || auth.isPM" @click="createVisible = true">录入反馈</el-button>
        <el-button v-if="auth.isSupport || auth.isPM" type="success" @click="importVisible = true">批量导入</el-button>
        <el-button v-if="auth.isPM" type="warning" :loading="aiLoading" @click="runAi">AI 整理未处理反馈</el-button>
      </div>
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="content" label="反馈内容" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">
            <router-link :to="`/feedbacks/${row.id}`" class="link">{{ row.content }}</router-link>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">{{ SOURCES[row.source] || row.source }}</template>
        </el-table-column>
        <el-table-column label="模块" width="110">
          <template #default="{ row }">{{ MODULES[row.module] || row.module }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StateTag :value="row.status" :dict="FEEDBACK_STATUS" />
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="录入人ID" width="90" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination layout="prev, pager, next, total" :total="total" :page-size="query.size"
        :current-page="query.current" @current-change="changePage" style="margin-top: 12px" />
    </el-card>

    <!-- 单条录入 -->
    <el-dialog v-model="createVisible" title="录入脱敏反馈" width="520">
      <el-form label-width="90">
        <el-form-item label="反馈内容"><el-input v-model="form.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source" style="width: 100%">
            <el-option v-for="(label, key) in SOURCES" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="form.module" style="width: 100%">
            <el-option v-for="(label, key) in MODULES" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户标签"><el-input v-model="form.customerTag" placeholder="脱敏标签，如 客户A" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">提交</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入：每行一条 -->
    <el-dialog v-model="importVisible" title="批量导入（每行一条脱敏反馈）" width="640">
      <el-input v-model="importText" type="textarea" :rows="8"
        placeholder="上传发票一直失败（换行输入下一条）" />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { auth } from '../store/auth'
import { FEEDBACK_STATUS, MODULES, SOURCES } from '../constants'
import StateTag from '../components/StateTag.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, module: '', status: '', keyword: '' })

const createVisible = ref(false)
const importVisible = ref(false)
const saving = ref(false)
const aiLoading = ref(false)
const form = reactive({ content: '', source: 'TICKET', module: 'OTHER', customerTag: '' })
const importText = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await request.get('/api/feedbacks', { params: query })
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
  if (!form.content.trim()) {
    ElMessage.warning('反馈内容不能为空')
    return
  }
  saving.value = true
  try {
    await request.post('/api/feedbacks', { ...form })
    ElMessage.success('录入成功，已触发 AI 整理')
    createVisible.value = false
    form.content = ''
    load()
  } catch (ignored) {
  } finally {
    saving.value = false
  }
}

async function doImport() {
  const items = importText.value.split('\n').map((line) => line.trim()).filter(Boolean)
    .map((content) => ({ content }))
  if (!items.length) {
    ElMessage.warning('请输入至少一条反馈')
    return
  }
  saving.value = true
  try {
    const count = await request.post('/api/feedbacks/import', { items })
    ElMessage.success(`导入成功 ${count} 条，已触发 AI 整理`)
    importVisible.value = false
    importText.value = ''
    load()
  } catch (ignored) {
  } finally {
    saving.value = false
  }
}

async function runAi() {
  aiLoading.value = true
  try {
    const result = await request.post('/api/ai/extract', { feedbackIds: null })
    ElMessage.success(`AI 整理完成：生成 ${result.cardsCreated} 张候选问题卡片（其余未匹配的反馈请手工分类）`)
  } catch (ignored) {
  } finally {
    aiLoading.value = false
  }
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}
</script>

<style scoped>
.link { color: #409eff; text-decoration: none; }
</style>
