<template>
  <transition name="chart-slide">
    <div v-if="visible" class="board-chart-panel">
      <div class="chart-panel-header">
        <div class="chart-panel-title">
          <span class="chart-icon">{{ chartTypeIcon }}</span>
          <span class="chart-label">{{ chartTypeLabel }}</span>
        </div>
        <div class="chart-panel-meta" v-if="chartData">
          <template v-if="chartType === 'burndown' && burndownMeta">
            <span class="meta-item" v-if="burndownMeta.velocity > 0">
              <span class="meta-label">日均速率</span>
              <span class="meta-value">
                {{ burndownMeta.velocity.toFixed(1) }}
                <template v-if="burndownMeta.mode === 'issue_count'">工单/天</template>
                <template v-else>h/天</template>
              </span>
            </span>
            <span class="meta-item">
              <span class="meta-label">总工单</span>
              <span class="meta-value">{{ burndownMeta.totalIssues }}</span>
            </span>
          </template>
        </div>
        <div class="chart-panel-actions">
          <a-button size="mini" type="text" @click="loadChartData" :loading="chartLoading" title="刷新">
            <template #icon><icon-refresh /></template>
          </a-button>
          <a-button size="mini" type="text" @click="$emit('close')" title="收起图表">
            <template #icon><icon-up /></template>
          </a-button>
        </div>
      </div>
      <div class="chart-panel-body">
        <!-- Loading State -->
        <div v-if="chartLoading" class="chart-state chart-state--loading">
          <a-spin :size="20" />
          <span>加载图表数据...</span>
        </div>
        <!-- Error State -->
        <div v-else-if="chartError" class="chart-state chart-state--error">
          <span class="state-text">{{ chartError }}</span>
          <a-link @click="loadChartData">重试</a-link>
        </div>
        <!-- Empty State -->
        <div v-else-if="chartEmpty" class="chart-state chart-state--empty">
          <span class="state-icon">📊</span>
          <span class="state-text">{{ emptyMessage }}</span>
        </div>
        <!-- Chart -->
        <div v-else-if="chartData" class="chart-wrapper">
          <v-chart :option="chartOption" autoresize class="chart-instance" />
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  TooltipComponent,
  LegendComponent,
  GridComponent,
  MarkLineComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { reportStatisticsApi } from '@/api'
import type { BurndownData, CumulativeFlowData } from '@/api/reportStatistics'

// 注册 ECharts 组件
use([CanvasRenderer, LineChart, TooltipComponent, LegendComponent, GridComponent, MarkLineComponent])

const props = defineProps<{
  visible: boolean
  projectId: string
  sprintId?: string
  chartType: string // 'burndown' | 'cumulative_flow'
  burndownCalculation?: string // 'issue_count' | 'estimation' | 'work_items'
  estimationFieldId?: string | null // REQ-486: custom estimation field ID for estimation mode
}>()

const emit = defineEmits<{
  close: []
}>()

// ===== Chart Data =====
const chartLoading = ref(false)
const chartError = ref<string | null>(null)
const chartData = ref<BurndownData | CumulativeFlowData | null>(null)

const chartTypeIcon = computed(() => props.chartType === 'burndown' ? '📉' : '📊')
const chartTypeLabel = computed(() => props.chartType === 'burndown' ? '燃尽图' : '累积流图')

const burndownMeta = computed(() => {
  if (props.chartType !== 'burndown' || !chartData.value) return null
  const data = chartData.value as BurndownData
  // Calculate velocity from actual data
  const actual = data.actual || []
  if (actual.length < 2) return { velocity: 0, totalIssues: data.totalIssues || 0, mode: data.mode || 'issue_count' }
  const start = actual[0]
  const end = actual[actual.length - 1]
  const days = actual.length - 1
  const velocity = days > 0 ? (start - end) / days : 0
  return { velocity, totalIssues: data.totalIssues || 0, mode: data.mode || 'issue_count' }
})

const chartEmpty = computed(() => {
  if (!chartData.value) return false
  if (props.chartType === 'burndown') {
    const data = chartData.value as BurndownData
    return !data.dates || data.dates.length === 0
  } else {
    const data = chartData.value as CumulativeFlowData
    return !data.dates || data.dates.length === 0
  }
})

const emptyMessage = computed(() => {
  if (props.chartType === 'burndown') {
    if (!props.sprintId) return '请选择一个迭代以查看燃尽图'
    return 'Sprint 未设置日期范围，无法生成燃尽图'
  }
  return '暂无累积流数据'
})

// ===== ECharts Options =====
const chartOption = computed(() => {
  if (!chartData.value) return {}
  if (props.chartType === 'burndown') {
    return buildBurndownOption(chartData.value as BurndownData)
  }
  return buildCumulativeFlowOption(chartData.value as CumulativeFlowData)
})

function buildBurndownOption(data: BurndownData) {
  const { dates, ideal, actual } = data
  if (!dates || dates.length === 0) return {}

  const shortDates = dates.map(d => d.substring(5))
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
  const textColor = isDark ? '#9ca3af' : '#57606a'
  const axisColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)'
  const tooltipBg = isDark ? '#2a2d33' : '#fff'
  const tooltipBorder = isDark ? '#3d4048' : '#e5e7eb'
  const idealColor = isDark ? '#6b7280' : '#9ca3af'
  const actualColor = isDark ? '#58a6ff' : '#0969da'

  // Y-axis unit based on calculation mode
  const mode = data.mode || 'issue_count'
  const yUnit = mode === 'issue_count' ? '工单' : 'min'
  const yAxisName = mode === 'issue_count' ? '' : '(分钟)'

  // Find today index (actual line may end before the full dates range)
  const today = new Date().toISOString().slice(0, 10)
  const todayIndex = dates.indexOf(today)

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: textColor, fontSize: 12 },
      formatter: (params: any[]) => {
        if (!params || params.length === 0) return ''
        const dateIdx = params[0].dataIndex
        const fullDate = dates[dateIdx]
        let html = `<div style="font-weight:500;margin-bottom:4px">${fullDate}</div>`
        for (const p of params) {
          if (p.value !== undefined && p.value !== null) {
            const valStr = typeof p.value === 'number' ? p.value.toFixed(1) : p.value
            html += `<div style="display:flex;align-items:center;gap:6px;">
              <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
              <span>${p.seriesName}：<b>${valStr}</b>${mode !== 'issue_count' ? ' min' : ''}</span>
            </div>`
          }
        }
        return html
      }
    },
    legend: {
      data: ['理想进度', '实际剩余'],
      right: 0,
      top: 0,
      textStyle: { color: textColor, fontSize: 11 },
      itemWidth: 14,
      itemHeight: 3
    },
    grid: { left: yAxisName ? 52 : 36, right: 12, top: 28, bottom: 24 },
    xAxis: {
      type: 'category',
      data: shortDates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: axisColor } },
      axisLabel: {
        color: textColor,
        fontSize: 10,
        interval: shortDates.length > 14 ? Math.floor(shortDates.length / 7) - 1 : 0
      },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: yAxisName,
      nameTextStyle: { color: textColor, fontSize: 10 },
      minInterval: mode === 'issue_count' ? 1 : undefined,
      axisLine: { show: false },
      axisLabel: { color: textColor, fontSize: 10 },
      splitLine: { lineStyle: { color: axisColor, type: 'dashed' } }
    },
    series: [
      {
        name: '理想进度',
        type: 'line',
        data: ideal,
        lineStyle: { width: 2, color: idealColor, type: 'dashed' },
        itemStyle: { color: idealColor },
        symbol: 'none',
        z: 1
      },
      {
        name: '实际剩余',
        type: 'line',
        data: actual,
        smooth: 0.3,
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { width: 2.5, color: actualColor },
        itemStyle: { color: actualColor },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: isDark ? 'rgba(88, 166, 255, 0.1)' : 'rgba(9, 105, 218, 0.08)' },
              { offset: 1, color: 'rgba(88, 166, 255, 0)' }
            ]
          }
        },
        markLine: todayIndex >= 0 ? {
          silent: true,
          symbol: 'none',
          lineStyle: { color: isDark ? '#3fb950' : '#1a7f37', width: 1.5, type: 'solid' },
          label: {
            show: true,
            formatter: '今天',
            color: isDark ? '#3fb950' : '#1a7f37',
            fontSize: 10,
            position: 'start'
          },
          data: [{ xAxis: todayIndex }]
        } : undefined,
        z: 2
      }
    ]
  }
}

function buildCumulativeFlowOption(data: CumulativeFlowData) {
  const { dates, series } = data
  if (!dates || dates.length === 0 || !series || series.length === 0) return {}

  const shortDates = dates.map(d => d.substring(5))
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
  const textColor = isDark ? '#9ca3af' : '#57606a'
  const axisColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)'
  const tooltipBg = isDark ? '#2a2d33' : '#fff'
  const tooltipBorder = isDark ? '#3d4048' : '#e5e7eb'

  const legendData = series.map(s => s.name)
  const chartSeries = series.map(s => ({
    name: s.name,
    type: 'line',
    stack: 'total',
    smooth: 0.2,
    symbol: 'none',
    lineStyle: { width: 1.5, color: s.color || undefined },
    itemStyle: { color: s.color || undefined },
    areaStyle: { opacity: 0.6 },
    data: s.data
  }))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: textColor, fontSize: 12 },
      formatter: (params: any[]) => {
        if (!params || params.length === 0) return ''
        const dateIdx = params[0].dataIndex
        const fullDate = dates[dateIdx]
        let html = `<div style="font-weight:500;margin-bottom:4px">${fullDate}</div>`
        // Show in reverse order (bottom to top in stack)
        for (let i = params.length - 1; i >= 0; i--) {
          const p = params[i]
          if (p.value !== undefined && p.value !== null) {
            html += `<div style="display:flex;align-items:center;gap:6px;">
              <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
              <span>${p.seriesName}：<b>${p.value}</b></span>
            </div>`
          }
        }
        return html
      }
    },
    legend: {
      data: legendData,
      right: 0,
      top: 0,
      textStyle: { color: textColor, fontSize: 11 },
      itemWidth: 14,
      itemHeight: 3
    },
    grid: { left: 36, right: 12, top: 28, bottom: 24 },
    xAxis: {
      type: 'category',
      data: shortDates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: axisColor } },
      axisLabel: {
        color: textColor,
        fontSize: 10,
        interval: shortDates.length > 14 ? Math.floor(shortDates.length / 7) - 1 : 0
      },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: textColor, fontSize: 10 },
      splitLine: { lineStyle: { color: axisColor, type: 'dashed' } }
    },
    series: chartSeries
  }
}

// ===== Data Loading =====
async function loadChartData() {
  if (!props.projectId) return
  if (props.chartType === 'burndown' && !props.sprintId) {
    chartData.value = null
    chartError.value = null
    return
  }

  chartLoading.value = true
  chartError.value = null
  try {
    if (props.chartType === 'burndown') {
      const calculation = (props.burndownCalculation as 'issue_count' | 'estimation' | 'work_items' | undefined) || 'issue_count'
      // REQ-486: pass estimationFieldId when calculation=estimation
      const fieldId = calculation === 'estimation' ? props.estimationFieldId : undefined
      const res = await reportStatisticsApi.burndown(
        props.projectId,
        props.sprintId!,
        calculation,
        fieldId
      )
      chartData.value = res.data
    } else {
      // Cumulative flow: default last 30 days
      const endDate = new Date().toISOString().slice(0, 10)
      const startDate = new Date(Date.now() - 29 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
      const res = await reportStatisticsApi.cumulativeFlow(props.projectId, startDate, endDate)
      chartData.value = res.data
    }
  } catch (e: any) {
    chartError.value = e?.response?.data?.message || '加载图表数据失败'
  } finally {
    chartLoading.value = false
  }
}

// Watch for prop changes to reload
watch(
  () => [props.visible, props.projectId, props.sprintId, props.chartType, props.burndownCalculation, props.estimationFieldId],
  ([visible]) => {
    if (visible && props.projectId) {
      loadChartData()
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.board-chart-panel {
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-2);
  padding: 12px 16px;
  overflow: hidden;
}

.chart-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.chart-panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.chart-icon {
  font-size: 14px;
}

.chart-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.chart-panel-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  margin-left: 24px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-label {
  font-size: 11px;
  color: var(--color-text-3);
}

.meta-value {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-1);
}

.chart-panel-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.chart-panel-body {
  position: relative;
}

.chart-wrapper {
  height: 180px;
}

.chart-instance {
  width: 100%;
  height: 100%;
}

.chart-state {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.chart-state--loading .state-text,
.chart-state--error .state-text,
.chart-state--empty .state-text {
  font-size: 12px;
  color: var(--color-text-3);
}

.state-icon {
  font-size: 20px;
}

/* Slide transition */
.chart-slide-enter-active,
.chart-slide-leave-active {
  transition: all 0.25s ease;
  max-height: 260px;
}

.chart-slide-enter-from,
.chart-slide-leave-to {
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  margin: 0;
  opacity: 0;
  overflow: hidden;
}
</style>
