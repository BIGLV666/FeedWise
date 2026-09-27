<template>
  <div>
    <el-card class="page-card">
      <div class="toolbar">
        <el-radio-group v-model="query.status" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="(label, key) in TASK_STATUS" :key="key" :value="key">{{ label }}</el-radio-button>
        </el-radio-group>
      </div>
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="任务" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <router-link :to="`/tasks/${row.id}`" class="link">{{ row.title }}</router-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StateTag :value="row.status" :dict="TASK_STATUS" /></template>
        </el-table-column>
        <el-table-column label="优先级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.priority === 'P1' ? 'danger' : row.priority === 'P2' ? 'warning' : 'info'" size="small">
              {{ row.priority }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="指派给" width="100">
          <template #default="{ row }">{{ row.assigneeName || (row.assigneeId ? '用户#' + row.assigneeId : '-') }}</template>
        </el-table-column>
        <el-table-column label="验证结果" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.verifyResult || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-pagination layout="prev, pager, next, total" :total="total" :page-size="query.size"
        :current-page="query.current" @current-change="changePage" style="margin-top: 12px" />
    </el-card>
    <el-alert type="info" :closable="false"
      title="状态机约束：TODO → 开发中 → 待验证 → 已完成；验证不通过可退回开发中。跳步（如待开发直接完成）会被后端以 40900 拒绝。" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import request from '../api/request'
import { TASK_STATUS } from '../constants'
import StateTag from '../components/StateTag.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, status: '' })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await request.get('/api/tasks', { params: query })
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
</script>

<style scoped>
.link { color: #409eff; text-decoration: none; }
</style>
