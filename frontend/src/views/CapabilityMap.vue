<template>
  <div v-loading="loading">
    <el-alert type="success" :closable="false" class="page-card"
      title="本系统由 biglv666 全家桶 8 个 Spring Boot 组件驱动，每张卡片对应一个组件在本项目中的真实使用点与演示入口" />
    <el-row :gutter="14">
      <el-col :span="12" v-for="card in cards" :key="card.name">
        <el-card class="page-card capability-card">
          <template #header>
            <div class="card-head">
              <span class="card-name">{{ card.name }}</span>
              <el-tag size="small" effect="plain" type="info">{{ card.role }}</el-tag>
              <span class="spacer" />
              <router-link v-if="card.demoEntry" :to="card.demoEntry" class="entry-link">演示入口 →</router-link>
            </div>
          </template>
          <p class="usage">{{ card.usage }}</p>
          <div v-if="card.metrics && Object.keys(card.metrics).length" class="metrics">
            <el-tag v-for="(v, k) in card.metrics" :key="k" size="small" effect="plain" type="success">
              {{ k }}：{{ v }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '../api/request'

const loading = ref(false)
const cards = ref([])

onMounted(async () => {
  loading.value = true
  try {
    cards.value = await request.get('/api/capability')
  } catch (ignored) {
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.capability-card { min-height: 170px; }
.card-head { display: flex; align-items: center; gap: 10px; }
.card-name { font-weight: 700; font-size: 15px; color: #1f2d3d; font-family: 'JetBrains Mono', monospace; }
.entry-link { color: #2f6bff; font-size: 12px; text-decoration: none; }
.usage { color: #4b5565; font-size: 13px; line-height: 1.7; margin: 0 0 10px; }
.metrics { display: flex; gap: 6px; flex-wrap: wrap; }
</style>
