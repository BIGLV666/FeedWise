<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex; align-items:center; gap:10px">
          <span>在线会话（auth-kit Redis 会话存储）</span>
          <span class="spacer" />
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>
      <el-empty v-if="!loading && records.length === 0" description="当前没有在线会话" />
      <el-table v-else v-loading="loading" :data="records" stripe>
        <el-table-column prop="displayName" label="用户" width="130" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ ROLE_NAMES[row.role] || row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="device" label="设备" width="90" />
        <el-table-column label="token" min-width="150">
          <template #default="{ row }"><code class="tk">{{ row.tokenMasked }}</code></template>
        </el-table-column>
        <el-table-column label="登录时间" width="170">
          <template #default="{ row }">{{ fmt(row.loginTime) }}</template>
        </el-table-column>
        <el-table-column label="最后活跃" width="170">
          <template #default="{ row }">{{ fmt(row.lastActiveTime) }}</template>
        </el-table-column>
        <el-table-column label="记住我" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.rememberMe" size="small" type="success" effect="plain">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button size="small" type="danger" plain @click="kickout(row)">强制下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
import { ROLE_NAMES } from '../constants'

const loading = ref(false)
const records = ref([])

onMounted(load)

async function load() {
  loading.value = true
  try {
    records.value = await request.get('/api/sessions')
  } catch (ignored) {
  } finally {
    loading.value = false
  }
}

async function kickout(row) {
  await ElMessageBox.confirm(`强制 ${row.displayName}（全部设备）下线？`, '踢人下线', { type: 'warning' })
  await request.delete(`/api/sessions/${row.userId}`)
  ElMessage.success('已强制下线（旧 token 收到 KICKED_OUT 语义）')
  load()
}

function fmt(ts) {
  return ts ? new Date(ts).toLocaleString('zh-CN', { hour12: false }) : '-'
}
</script>

<style scoped>
.tk { color: #6d5df6; font-size: 12px; }
</style>
