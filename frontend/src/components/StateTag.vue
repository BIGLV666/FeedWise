<template>
  <el-tag :type="tagType" effect="light" size="small">{{ label }}</el-tag>
</template>
<script setup>
import { computed } from 'vue'

// 统一状态色：待处理=warning、进行/确认=primary、通过=success、驳回/合并=info
const props = defineProps({ value: String, dict: Object })
const label = computed(() => (props.dict && props.dict[props.value]) || props.value)
const tagType = computed(() => {
  const v = props.value
  if (['UNPROCESSED', 'PENDING_REVIEW', 'DRAFT', 'TODO', 'P1'].includes(v)) return 'warning'
  if (['IN_PROGRESS', 'CONFIRMED', 'PENDING_VERIFY'].includes(v)) return 'primary'
  if (['DONE', 'CONVERTED', 'PROCESSED'].includes(v)) return 'success'
  return 'info'
})
</script>
