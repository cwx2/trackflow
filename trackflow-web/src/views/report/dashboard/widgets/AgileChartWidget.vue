<template>
  <div v-if="chartOption" class="widget-chart-container">
    <v-chart :option="chartOption" autoresize class="widget-chart-instance" />
  </div>
  <div v-else class="widget-configure-hint">
    <icon-bar-chart :size="32" class="hint-icon" />
    <span class="hint-text">点击「编辑配置」选择 Sprint 和图表类型</span>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent, MarkLineComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { IconBarChart } from '@arco-design/web-vue/es/icon'
import { sprintApi } from '@/api/sprint'
import { reportStatisticsApi } from '@/api/reportStatistics'
import { SERIES_ACCENT, SERIES_TERTIARY, SERIES_WARNING, SERIES_PURPLE, SERIES_MARKER } from '@/utils/chartColors'
import type { SprintBurndownVO } from '@/api/types'

use([CanvasRenderer, LineChart, TooltipComponent, LegendComponent, GridComponent, MarkLineComponent])

const props = defineProps<{
  config: Record<string, any>
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
}>()

const agileChartData = ref<SprintBurndownVO | null>(null)
const cumulativeFlowData = ref<{ dates: string[]; series: Array<{ name: string; color: string; data: number[] }> } | null>(null)

const chartOption = computed(() => {
  const chartType = props.config.chartType || 'burndown'

  if (chartType === 'burndown' && agileChartData.value) {
    return buildBurndownOption(agileChartData.value)
  }
  if (chartType === 'cumulative_flow' && cumulativeFlowData.value) {
    return buildCumulativeFlowOption(cumulativeFlowData.value)
  }
  return null
})

function buildBurndownOption(data: SprintBurndownVO): Record<string, any> {
  const todayIdx = data.todayIndex >= 0 ? data.todayIndex : undefined

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'var(--tf-bg-elevated, #22252a)',
      borderColor: 'var(--tf-border)',
      textStyle: { color: 'var(--tf-text-primary)', fontSize: 11 }
    },
    legend: {
      data: ['理想线', '实际线', '范围线'],
      bottom: 0,
      textStyle: { color: 'var(--tf-text-secondary)', fontSize: 10 },
      itemWidth: 16,
      itemHeight: 3
    },
    grid: { left: 36, right: 12, top: 12, bottom: 32 },
    xAxis: {
      type: 'category',
      data: data.dates.map(d => d.substring(5)),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 9, interval: Math.max(0, Math.floor(data.dates.length / 8) - 1) }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { fontSize: 10 },
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.2 } }
    },
    series: [
      {
        name: '理想线',
        type: 'line',
        data: data.idealLine,
        lineStyle: { type: 'dashed', color: SERIES_TERTIARY, width: 1.5 },
        symbol: 'none',
        itemStyle: { color: SERIES_TERTIARY }
      },
      {
        name: '实际线',
        type: 'line',
        data: data.actualLine,
        lineStyle: { color: SERIES_ACCENT, width: 2 },
        symbol: 'circle',
        symbolSize: 4,
        itemStyle: { color: SERIES_ACCENT },
        ...(todayIdx !== undefined ? {
          markLine: {
            silent: true,
            data: [{ xAxis: todayIdx }],
            lineStyle: { type: 'solid', color: SERIES_MARKER, width: 1 },
            label: { show: false }
          }
        } : {})
      },
      {
        name: '范围线',
        type: 'line',
        data: data.scopeLine,
        lineStyle: { color: SERIES_PURPLE, width: 1.5, type: 'dotted' },
        symbol: 'none',
        itemStyle: { color: SERIES_PURPLE }
      }
    ]
  }
}

function buildCumulativeFlowOption(data: { dates: string[]; series: Array<{ name: string; color: string; data: number[] }> }): Record<string, any> {
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'var(--tf-bg-elevated, #22252a)',
      borderColor: 'var(--tf-border)',
      textStyle: { color: 'var(--tf-text-primary)', fontSize: 11 }
    },
    legend: {
      data: data.series.map(s => s.name),
      bottom: 0,
      textStyle: { color: 'var(--tf-text-secondary)', fontSize: 10 },
      itemWidth: 12,
      itemHeight: 8
    },
    grid: { left: 36, right: 12, top: 12, bottom: 32 },
    xAxis: {
      type: 'category',
      data: data.dates.map(d => d.substring(5)),
      boundaryGap: false,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 9, interval: Math.max(0, Math.floor(data.dates.length / 8) - 1) }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { fontSize: 10 },
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.2 } }
    },
    series: data.series.map(s => ({
      name: s.name,
      type: 'line',
      stack: 'Total',
      areaStyle: { opacity: 0.6 },
      lineStyle: { width: 1, color: s.color },
      itemStyle: { color: s.color },
      symbol: 'none',
      data: s.data
    }))
  }
}

async function loadData(_force = false) {
  const config = props.config
  const sprintId = config.sprintId
  const projectId = config.projectId
  const chartType = config.chartType || 'burndown'

  if (!sprintId && !projectId) {
    emit('loaded')
    return
  }

  try {
    if (chartType === 'burndown' && sprintId) {
      const res = await sprintApi.burndown(sprintId)
      agileChartData.value = res.data || null
    } else if (chartType === 'cumulative_flow' && projectId) {
      const res = await reportStatisticsApi.cumulativeFlow(projectId)
      cumulativeFlowData.value = res.data || null
    }
    emit('loaded')
  } catch (e: any) {
    emit('error', e.response?.data?.message || '加载敏捷图表数据失败')
  }
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData })
</script>

<style scoped>
.widget-chart-container {
  flex: 1;
  min-height: 0;
  display: flex;
}

.widget-chart-instance {
  width: 100%;
  height: 100%;
  min-height: 120px;
}

.widget-configure-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  padding: 12px;
}

.hint-icon {
  color: var(--tf-text-tertiary);
  opacity: 0.4;
}

.hint-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: center;
  line-height: 1.4;
}
</style>
