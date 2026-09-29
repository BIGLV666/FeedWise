<template>
  <div v-loading="loading">
    <el-alert type="info" :closable="false" class="page-card"
      title="三台状态机由 state-kit 0.3.0 的 Exporter 从 yml 声明自动生成 Mermaid 图；切换上方页签查看各机器的流转定义" />
    <el-tabs v-model="active" @tab-change="render">
      <el-tab-pane v-for="name in ['task', 'issue', 'draft']" :key="name" :label="labelOf(name)" :name="name" />
    </el-tabs>
    <el-card class="page-card">
      <div ref="mermaidRef" class="mermaid-box"></div>
    </el-card>
    <el-card>
      <template #header>当前可操作事件（availableActions 联动演示）</template>
      <el-form inline @submit.prevent>
        <el-form-item label="任务 ID">
          <el-input v-model="taskId" style="width: 120px" @keyup.enter="loadActions" />
        </el-form-item>
        <el-button type="primary" @click="loadActions">查询</el-button>
      </el-form>
      <el-empty v-if="searched && actions.length === 0" description="终态或任务不存在：无可操作事件" :image-size="60" />
      <el-table v-else-if="searched" :data="actions" stripe size="small">
        <el-table-column prop="event" label="事件" width="120" />
        <el-table-column prop="to" label="目标状态" width="140" />
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column label="守卫" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.guarded ? 'warning' : 'info'" effect="plain">{{ row.guarded ? '有' : '无' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="期望参数" min-width="140">
          <template #default="{ row }">{{ (row.requiredParams || []).join(', ') || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import mermaid from 'mermaid'
import request from '../api/request'

const loading = ref(false)
const diagrams = ref({})
const active = ref('task')
const mermaidRef = ref(null)
const taskId = ref('1')
const actions = ref([])
const searched = ref(false)

mermaid.initialize({ startOnLoad: false, theme: 'neutral' })

const NAMES = { task: '改进任务（task）', issue: '候选问题（issue）', draft: '需求草稿（draft）' }
const labelOf = (n) => NAMES[n] || n

onMounted(async () => {
  loading.value = true
  try {
    diagrams.value = await request.get('/api/machines/diagrams')
    await render()
  } catch (ignored) {
  } finally {
    loading.value = false
  }
})

async function render() {
  await nextTick()
  const source = diagrams.value[active.value] && diagrams.value[active.value].mermaid
  if (!source || !mermaidRef.value) {
    return
  }
  const { svg } = await mermaid.render('machine-diagram', source)
  mermaidRef.value.innerHTML = svg
}

async function loadActions() {
  if (!taskId.value) {
    return
  }
  actions.value = await request.get(`/api/machines/task/${taskId.value}/actions`)
  searched.value = true
}
</script>

<style scoped>
.mermaid-box { display: flex; justify-content: center; padding: 8px 0; }
.mermaid-box :deep(svg) { max-width: 100%; }
</style>
