<template>
  <el-empty v-if="!items || items.length === 0" description="暂无操作记录" :image-size="60" />
  <el-timeline v-else>
    <el-timeline-item v-for="(item, i) in items" :key="i" :timestamp="formatTime(item.time)" placement="top">
      <div class="tl-head">
        <b>{{ item.action }}</b>
        <el-tag size="small" :type="item.source === 'STATE_MACHINE' ? 'success' : 'info'" effect="plain">
          {{ item.source === 'STATE_MACHINE' ? '状态机' : '操作日志' }}
        </el-tag>
        <span class="tl-operator">{{ item.operator }}</span>
      </div>
      <div v-if="item.detail" class="tl-detail">{{ item.detail }}</div>
    </el-timeline-item>
  </el-timeline>
</template>
<script setup>
defineProps({ items: Array })
function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}
</script>
<style scoped>
.tl-head { display: flex; gap: 8px; align-items: center; }
.tl-operator { color: #909399; font-size: 12px; }
.tl-detail { color: #606266; font-size: 13px; margin-top: 2px; }
</style>
