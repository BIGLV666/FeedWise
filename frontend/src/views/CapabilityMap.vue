<template>
  <div v-loading="loading">
    <el-alert type="success" :closable="false" class="page-card"
      title="本系统由 biglv666 全家桶 8 个 Spring Boot 组件驱动；卡片里的「页面」可跳转演示，「接口」在弹窗内实时拉取后端响应（无需离开本站）" />
    <el-row :gutter="14">
      <el-col :span="12" v-for="card in cards" :key="card.name">
        <el-card class="page-card capability-card">
          <template #header>
            <div class="card-head">
              <span class="card-name">{{ card.name }}</span>
              <el-tag size="small" effect="plain" type="info">{{ card.role }}</el-tag>
              <span class="spacer" />
              <router-link v-if="card.pageEntry" :to="card.pageEntry" class="entry-link">页面 →</router-link>
            </div>
          </template>
          <p class="usage">{{ card.usage }}</p>
          <div v-if="card.apiEntries && card.apiEntries.length" class="api-list">
            <el-button v-for="api in card.apiEntries" :key="api.path" size="small" plain
              @click="openApi(api)">接口：{{ api.label }}</el-button>
          </div>
          <div v-if="card.metrics && Object.keys(card.metrics).length" class="metrics">
            <el-tag v-for="(v, k) in card.metrics" :key="k" size="small" effect="plain" type="success">
              {{ k }}：{{ v }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="apiVisible" :title="`接口演示：${current && current.label}`" width="720">
      <el-alert type="info" :closable="false" style="margin-bottom: 10px"
        :title="`GET ${current && current.path}`" />
      <pre class="api-result">{{ apiResult }}</pre>
      <template #footer>
        <el-button @click="apiVisible = false">关闭</el-button>
        <el-button type="primary" :loading="apiLoading" @click="reloadApi">重新请求</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '../api/request'

const loading = ref(false)
const cards = ref([])
const apiVisible = ref(false)
const apiLoading = ref(false)
const current = ref(null)
const apiResult = ref('')

onMounted(async () => {
  loading.value = true
  try {
    cards.value = await request.get('/api/capability')
  } catch (ignored) {
  } finally {
    loading.value = false
  }
})

function openApi(api) {
  current.value = api
  apiVisible.value = true
  apiResult.value = ''
  reloadApi()
}

/** 站内代理 fetch：/web-common、/actuator、/api-governance 均已由 vite 代理到后端。 */
async function reloadApi() {
  if (!current.value) {
    return
  }
  apiLoading.value = true
  try {
    const res = await fetch(current.value.path, {
      headers: { Accept: 'application/json' }
    })
    const text = await res.text()
    try {
      apiResult.value = JSON.stringify(JSON.parse(text), null, 2)
    } catch (e) {
      apiResult.value = text
    }
    if (!res.ok) {
      apiResult.value = `HTTP ${res.status}\n${apiResult.value}`
    }
  } catch (e) {
    apiResult.value = `请求失败：${e.message}`
  } finally {
    apiLoading.value = false
  }
}
</script>

<style scoped>
.capability-card { min-height: 170px; }
.card-head { display: flex; align-items: center; gap: 10px; }
.card-name { font-weight: 700; font-size: 15px; color: #1f2d3d; font-family: 'JetBrains Mono', monospace; }
.entry-link { color: #2f6bff; font-size: 12px; text-decoration: none; }
.usage { color: #4b5565; font-size: 13px; line-height: 1.7; margin: 0 0 10px; }
.api-list { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px; }
.metrics { display: flex; gap: 6px; flex-wrap: wrap; }
.api-result {
  background: #0f172a; color: #d6e2ff; border-radius: 8px; padding: 12px;
  max-height: 420px; overflow: auto; font-size: 12px; line-height: 1.6;
}
</style>