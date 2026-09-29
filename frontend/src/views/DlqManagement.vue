<template>
  <div>
    <el-card class="page-card">
      <template #header>
        <div style="display:flex; align-items:center; gap:10px">
          <span>死信台账（OutboxPro DLQ）</span>
          <span class="spacer" />
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px"
        title="消费失败经退避重试耗尽（或 @NonRetryable 直进）的消息落台账；修复根因后由产品经理手工重放（dlq:replay 权限位，操作留痕）" />
      <el-empty v-if="!loading && records.length === 0" description="没有死信记录（队列健康）" />
      <el-table v-else v-loading="loading" :data="records" stripe size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="事件" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.eventId }}</template>
        </el-table-column>
        <el-table-column prop="eventType" label="类型" width="180" />
        <el-table-column prop="consumerName" label="消费者" width="130" />
        <el-table-column label="原因" width="180">
          <template #default="{ row }">
            <el-tag size="small" type="danger" effect="plain">{{ reasonText(row.reason) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PENDING_REPLAY' ? 'warning' : 'success'" effect="light">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="replayCount" label="重放次数" width="90" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING_REPLAY'" size="small" type="primary"
              @click="replay(row)">重放</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination layout="prev, pager, next, total" :total="total" :page-size="query.size"
        :current-page="query.page" @current-change="changePage" style="margin-top: 12px" />
    </el-card>

    <el-dialog v-model="replayVisible" title="确认重放" width="440">
      <el-input v-model="reason" type="textarea" :rows="3" placeholder="重放原因（写入台账审计，如：模型恢复后重放）" />
      <template #footer>
        <el-button @click="replayVisible = false">取消</el-button>
        <el-button type="primary" :loading="replaying" @click="doReplay">确认重放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })
const replayVisible = ref(false)
const replaying = ref(false)
const reason = ref('')
const current = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await request.get('/api/dlq', { params: query })
    records.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (ignored) {
  } finally {
    loading.value = false
  }
}

function changePage(page) {
  query.page = page
  load()
}

function reasonText(reason) {
  const map = {
    NON_RETRYABLE_EXCEPTION: '不可重试异常',
    RETRY_EXHAUSTED: '重试耗尽',
    UNKNOWN_EVENT_TYPE: '未知事件类型',
    MALFORMED_MESSAGE: '消息格式错误',
    HANDLER_FAILURE: '处理器失败'
  }
  return map[reason] || reason
}

function replay(row) {
  current.value = row
  reason.value = ''
  replayVisible.value = true
}

async function doReplay() {
  replaying.value = true
  try {
    const result = await request.post(`/api/dlq/${current.value.eventId}/replay`, { reason: reason.value })
    ElMessage.success(`重放完成：成功 ${result.replayed} 条，失败 ${result.failed} 条`)
    replayVisible.value = false
    load()
  } catch (ignored) {
  } finally {
    replaying.value = false
  }
}
</script>
