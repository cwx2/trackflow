<template>
  <div class="widget-number-card">
    <div class="number-value" :class="{ 'has-color': numberColor }">
      <span :style="numberColor ? { color: numberColor } : {}">{{ displayNumber }}</span>
    </div>
    <div class="number-label">{{ displayLabel }}</div>
    <div v-if="numberSubtext" class="number-subtext">{{ numberSubtext }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { reportStatisticsApi } from '@/api/reportStatistics'
import type { OverviewData } from '@/api/reportStatistics'

const props = defineProps<{
  config: Record<string, any>
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
}>()

type QueryType = 'total' | 'open' | 'closed' | 'unassigned' | 'overdue' | 'completion_rate'

const queryTypeLabels: Record<QueryType, string> = {
  total: '工单总数',
  open: '待处理',
  closed: '已完成',
  unassigned: '未分配',
  overdue: '已逾期',
  completion_rate: '完成率'
}

const queryTypeColors: Record<QueryType, string> = {
  total: '#58a6ff',
  open: '#f0883e',
  closed: '#3fb950',
  unassigned: '#d29922',
  overdue: '#f85149',
  completion_rate: '#a371f7'
}

const overviewData = ref<OverviewData | null>(null)

const displayNumber = computed(() => {
  const config = props.config
  if (config.value != null) return config.value

  if (config.queryType && overviewData.value) {
    const key = config.queryType as QueryType
    const val = overviewData.value[key as keyof OverviewData]
    if (key === 'completion_rate') return `${val}%`
    return val ?? '—'
  }

  return '—'
})

const displayLabel = computed(() => {
  const config = props.config
  if (config.label) return config.label
  if (config.queryType) return queryTypeLabels[config.queryType as QueryType] || '统计数值'
  return '统计数值'
})

const numberColor = computed(() => {
  const config = props.config
  if (config.color) return config.color
  if (config.queryType) return queryTypeColors[config.queryType as QueryType] || null
  return null
})

const numberSubtext = computed(() => props.config.subtext || null)

async function loadData(force = false) {
  const config = props.config

  // Hardcoded value, no fetch needed
  if (config.value != null && !config.queryType) {
    emit('loaded')
    return
  }

  // Query type: fetch from dashboard statistics
  if (config.queryType) {
    try {
      const params: Record<string, string> = {}
      if (config.projectId) params.projectId = config.projectId
      const res = await reportStatisticsApi.dashboard(params)
      overviewData.value = res.data?.overview || null
      emit('loaded')
    } catch (e: any) {
      emit('error', e.response?.data?.message || '加载统计数据失败')
    }
    return
  }

  emit('loaded')
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData })
</script>

<style scoped>
.widget-number-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 4px;
}

.number-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--tf-text-primary);
  line-height: 1.1;
}

.number-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.number-subtext {
  font-size: 11px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  margin-top: 2px;
}
</style>
