<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-select v-model="objectType" style="width: 150px">
          <el-option label="用户反馈" value="FEEDBACK" />
          <el-option label="候选问题" value="ISSUE" />
          <el-option label="需求草稿" value="DRAFT" />
          <el-option label="改进任务" value="TASK" />
        </el-select>
        <el-input v-model="objectId" placeholder="对象 ID" style="width: 140px" @keyup.enter="load" />
        <el-button type="primary" :loading="loading" @click="load">查询时间线</el-button>
      </div>
      <TimelineList v-if="searched" :items="items" />
      <el-empty v-else description="按对象类型 + ID 查询完整时间线（操作日志 + 状态机流转历史）" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import request from '../api/request'
import TimelineList from '../components/TimelineList.vue'

const objectType = ref('FEEDBACK')
const objectId = ref('')
const loading = ref(false)
const items = ref([])
const searched = ref(false)

async function load() {
  if (!objectId.value) {
    return
  }
  loading.value = true
  try {
    items.value = await request.get('/api/history', {
      params: { objectType: objectType.value, objectId: objectId.value }
    })
    searched.value = true
  } catch (ignored) {
  } finally {
    loading.value = false
  }
}
</script>
