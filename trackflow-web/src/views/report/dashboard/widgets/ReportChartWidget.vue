<template>
  <div v-if="chartOption" class="widget-chart-container">
    <v-chart :option="chartOption" autoresize class="widget-chart-instance" />
  </div>
  <div v-else class="widget-configure-hint">
    <icon-bar-chart :size="32" class="hint-icon" />
    <span class="hint-text">{{ hintText }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { IconBarChart } from '@arco-design/web-vue/es/icon'
import { reportApi } from '@/api/report'
import { getPriorityColor } from '@/composables/usePriorityOptions'
import { CHART_PALETTE, STATUS_COLORS, getChartItemColor } from '@/utils/chartColors'
import type { ReportDataVO } from '@/api/report'

use([CanvasRenderer, PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent])

const props = defineProps<{
  config: Record<string, any>
  reportId?: string | null
  widgetType: 'report' | 'report_distribution'
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
}>()

const reportDataResult = ref<ReportDataVO | null>(null)

const hintText = computed(() => {
  if (props.widgetType === 'report_distribution') {
    return '点击「编辑配置」选择报表数据源'
  }
  return '点击「编辑配置」关联报表定义'
})

function getItemColor(label: string, groupBy: string, idx: number): string {
  if (groupBy === 'status') return STATUS_COLORS[label] || CHART_PALETTE[idx % CHART_PALETTE.length]
  if (groupBy === 'priority') return getPriorityColor(label) || CHART_PALETTE[idx % CHART_PALETTE.length]
  return CHART_PALETTE[idx % CHART_PALETTE.length]
}

// ─── Chart Option Building ────────────────────────────

const chartOption = computed(() => {
  if (!reportDataResult.value) return null
  return buildChartOption(reportDataResult.value)
})

function buildChartOption(data: ReportDataVO): Record<string, any> {
  // 双维度交叉分析：使用堆叠条形图
  if (data.secondGroupBy && data.matrix && data.secondLabels) {
    return buildStackedBarOption(data)
  }

  const chartType = data.chartType || inferChartType(data.groupBy, data.type)

  if (chartType === 'pie') return buildPieOption(data)
  if (chartType === 'bar_horizontal') return buildBarHorizontalOption(data)
  return buildBarVerticalOption(data)
}

function inferChartType(groupBy: string, type: string): string {
  if (groupBy === 'assignee' || type === 'by_assignee') return 'bar_horizontal'
  if (groupBy === 'status' || groupBy === 'priority' || groupBy === 'type'
    || type === 'by_status' || type === 'by_priority' || type === 'by_type') return 'pie'
  return 'bar_vertical'
}

function buildPieOption(data: ReportDataVO): Record<string, any> {
  const items = data.labels.map((label, idx) => ({
    name: label,
    value: data.data[idx],
    itemStyle: { color: getItemColor(label, data.groupBy, idx) }
  }))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: 'var(--tf-bg-elevated, #22252a)',
      borderColor: 'var(--tf-border, #30363d)',
      textStyle: { color: 'var(--tf-text-primary, #e6edf3)', fontSize: 11 }
    },
    legend: {
      orient: 'vertical',
      right: 4,
      top: 'center',
      textStyle: { color: 'var(--tf-text-secondary, #9ca3af)', fontSize: 10 },
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 6
    },
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 3, borderWidth: 1 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 11, fontWeight: 500 },
        itemStyle: { shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.3)' }
      },
      data: items
    }]
  }
}

function buildBarHorizontalOption(data: ReportDataVO): Record<string, any> {
  const labels = data.labels
  const colors = labels.map((l, i) => getItemColor(l, data.groupBy, i))

  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', textStyle: { fontSize: 11 } },
    grid: { left: 70, right: 16, top: 4, bottom: 12 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { fontSize: 10 },
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.3 } }
    },
    yAxis: {
      type: 'category',
      data: labels,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, width: 60, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      data: data.data.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
      barMaxWidth: 18,
      itemStyle: { borderRadius: [0, 3, 3, 0] }
    }]
  }
}

function buildBarVerticalOption(data: ReportDataVO): Record<string, any> {
  const labels = data.labels
  const colors = labels.map((l, i) => getItemColor(l, data.groupBy, i))

  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', textStyle: { fontSize: 11 } },
    grid: { left: 32, right: 8, top: 8, bottom: 24 },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, rotate: labels.length > 5 ? 30 : 0 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { fontSize: 10 },
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.3 } }
    },
    series: [{
      type: 'bar',
      data: data.data.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
      barMaxWidth: 24,
      itemStyle: { borderRadius: [3, 3, 0, 0] }
    }]
  }
}

function buildStackedBarOption(data: ReportDataVO): Record<string, any> {
  const primaryLabels = data.labels
  const secondaryLabels = data.secondLabels || []
  const matrix = data.matrix || []

  const series = secondaryLabels.map((secLabel, colIdx) => ({
    name: secLabel,
    type: 'bar',
    stack: 'total',
    barMaxWidth: 24,
    itemStyle: { color: CHART_PALETTE[colIdx % CHART_PALETTE.length] },
    data: primaryLabels.map((_, rowIdx) => matrix[rowIdx]?.[colIdx] || 0)
  }))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      textStyle: { fontSize: 11 },
      axisPointer: { type: 'shadow' }
    },
    legend: {
      bottom: 0,
      textStyle: { color: 'var(--tf-text-secondary, #9ca3af)', fontSize: 10 },
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 8
    },
    grid: { left: 32, right: 8, top: 8, bottom: 28 },
    xAxis: {
      type: 'category',
      data: primaryLabels,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, rotate: primaryLabels.length > 5 ? 30 : 0 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { fontSize: 10 },
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.3 } }
    },
    series
  }
}

// ─── Data Loading ──────────────────────────────────────

const REQUEST_TIMEOUT_MS = 8000
const ABORT_REASON_TIMEOUT = 'timeout'
let currentController: AbortController | null = null

async function loadData(force = false) {
  const reportId = props.reportId
  if (!reportId) {
    emit('loaded')
    return
  }

  // 取消上一次未完成的请求（被新请求取代，非超时）
  if (currentController) {
    currentController.abort('superseded')
  }
  const controller = new AbortController()
  currentController = controller
  const timeoutId = setTimeout(() => controller.abort(ABORT_REASON_TIMEOUT), REQUEST_TIMEOUT_MS)

  try {
    const res = await reportApi.execute(reportId, force || undefined, controller.signal)
    reportDataResult.value = res.data || null
    emit('loaded')
  } catch (e: any) {
    if (e.name === 'AbortError' || e.code === 'ERR_CANCELED') {
      // 区分超时 abort 和被新请求取代的 abort
      const reason = controller.signal.reason
      if (reason === ABORT_REASON_TIMEOUT) {
        emit('error', '请求超时，请点击重试')
      }
      // 被新请求取代时静默返回，不 emit error
      return
    }
    const status = e.response?.status
    if (status === 404) {
      emit('error', '关联的报表已被删除，请重新编辑配置')
    } else if (status === 403) {
      emit('error', '无权限查看此报表')
    } else {
      emit('error', e.response?.data?.message || '加载报表数据失败')
    }
  } finally {
    clearTimeout(timeoutId)
    if (currentController === controller) {
      currentController = null
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
