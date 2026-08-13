<template>
  <div v-if="chartOption" class="widget-chart-container">
    <v-chart :option="chartOption" autoresize class="widget-chart-instance" />
  </div>
  <div v-else class="widget-configure-hint">
    <icon-bar-chart :size="32" class="hint-icon" />
    <span class="hint-text">点击「编辑配置」选择 Sprint</span>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { IconBarChart } from '@arco-design/web-vue/es/icon'
import { sprintApi } from '@/api/sprint'
import { SERIES_ACCENT, SERIES_SUCCESS, SERIES_MARKER } from '@/utils/chartColors'

use([CanvasRenderer, BarChart, TooltipComponent, LegendComponent, GridComponent])

const props = defineProps<{
  config: Record<string, any>
  /** 仪表盘所属项目 ID（来自 project_overview 仪表盘） */
  dashboardProjectId?: string
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
  'permission-denied': []
}>()

interface BoardStatusData {
  totalIssues: number
  doneIssues: number
  inProgressIssues: number
  todoIssues: number
  sprintName: string
}

const boardStatusData = ref<BoardStatusData | null>(null)

const chartOption = computed(() => {
  if (!boardStatusData.value) return null
  return buildBoardStatusOption(boardStatusData.value)
})

function buildBoardStatusOption(data: BoardStatusData): Record<string, any> {
  const completionRate = data.totalIssues > 0 ? Math.round((data.doneIssues / data.totalIssues) * 100) : 0

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'var(--tf-bg-elevated, #22252a)',
      borderColor: 'var(--tf-border)',
      textStyle: { color: 'var(--tf-text-primary)', fontSize: 11 },
      formatter: (params: any[]) => {
        let html = `<div style="font-weight:500;margin-bottom:4px">${data.sprintName}</div>`
        params.forEach(p => {
          html += `<div>${p.marker} ${p.seriesName}: ${p.value}</div>`
        })
        html += `<div style="margin-top:4px;color:#9ca3af">完成率: ${completionRate}% (${data.doneIssues}/${data.totalIssues})</div>`
        return html
      }
    },
    legend: {
      data: ['待处理', '进行中', '已完成'],
      bottom: 0,
      textStyle: { color: 'var(--tf-text-secondary)', fontSize: 10 },
      itemWidth: 12,
      itemHeight: 8
    },
    grid: { left: 12, right: 12, top: 24, bottom: 32 },
    xAxis: {
      type: 'value',
      max: data.totalIssues || undefined,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'category',
      data: [data.sprintName],
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false }
    },
    series: [
      {
        name: '待处理',
        type: 'bar',
        stack: 'sprint',
        data: [data.todoIssues],
        barWidth: '60%',
        itemStyle: { color: SERIES_ACCENT, borderRadius: [3, 0, 0, 3] }
      },
      {
        name: '进行中',
        type: 'bar',
        stack: 'sprint',
        data: [data.inProgressIssues],
        barWidth: '60%',
        itemStyle: { color: SERIES_MARKER }
      },
      {
        name: '已完成',
        type: 'bar',
        stack: 'sprint',
        data: [data.doneIssues],
        barWidth: '60%',
        itemStyle: { color: SERIES_SUCCESS, borderRadius: [0, 3, 3, 0] }
      }
    ]
  }
}

async function loadData(_force = false) {
  const sprintId = props.config.sprintId
  if (!sprintId) {
    emit('loaded')
    return
  }

  try {
    const res = await sprintApi.getById(sprintId, { _silent403: true })
    const sprint = res.data
    if (sprint) {
      boardStatusData.value = {
        totalIssues: sprint.totalIssues,
        doneIssues: sprint.doneIssues,
        inProgressIssues: sprint.inProgressIssues,
        todoIssues: sprint.todoIssues,
        sprintName: sprint.name
      }
    }
    emit('loaded')
  } catch (e: any) {
    const status = e.response?.status
    if (status === 403) {
      emit('permission-denied')
    } else {
      emit('error', e.response?.data?.message || '加载看板状态数据失败')
    }
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
