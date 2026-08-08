<template>
  <div class="burndown-chart-container">
    <div class="burndown-header">
      <div class="burndown-title">
        <span class="chart-icon">📉</span>
        <span class="chart-label">燃尽图</span>
        <a-radio-group
          v-model="currentMode"
          size="mini"
          type="button"
          class="mode-switch"
          @change="onModeChange"
        >
          <a-radio value="issue_count">工单数</a-radio>
          <a-radio value="estimation">估算工时</a-radio>
          <a-radio value="work_items">实际工时</a-radio>
        </a-radio-group>
      </div>
      <div class="burndown-meta" v-if="burndownData">
        <span class="meta-item" v-if="burndownData.velocity > 0">
          <span class="meta-label">日均速率</span>
          <span class="meta-value">{{ burndownData.velocity }} {{ modeUnit }}/天</span>
        </span>
        <span class="meta-item" v-if="hasScopeChange">
          <span class="meta-label">起始/当前</span>
          <span class="meta-value scope-change" v-if="currentMode === 'issue_count'">{{ burndownData.startScopeIssues }} → {{ burndownData.totalIssues }}</span>
          <span class="meta-value scope-change" v-else>{{ formatHours(burndownData.startScopeHours) }}{{ currentMode === 'work_items' ? 'min' : 'h' }}</span>
        </span>
        <span class="meta-item forecast" v-if="burndownData.forecastDate && !isCompleted">
          <span class="meta-label">预计完成</span>
          <span class="meta-value" :class="{ overdue: isForecastLate }">{{ formatForecastDate(burndownData.forecastDate) }}</span>
        </span>
      </div>
    </div>
    <div class="chart-wrapper" v-if="burndownData && burndownData.dates.length > 0">
      <v-chart :option="chartOption" autoresize class="chart-instance" />
    </div>
    <div class="chart-empty" v-else-if="burndownData && burndownData.dates.length === 0">
      <span class="empty-text">Sprint 未设置日期范围，无法生成燃尽图</span>
    </div>
    <div class="chart-loading" v-else-if="loading">
      <a-spin :size="24" />
    </div>
    <div class="chart-error" v-else-if="error">
      <span class="error-text">加载失败</span>
      <a-link @click="loadData">重试</a-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
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
import { sprintApi } from '@/api'
import type { SprintBurndownVO } from '@/api/types'

// 注册 ECharts 组件
use([CanvasRenderer, LineChart, TooltipComponent, LegendComponent, GridComponent, MarkLineComponent])

const props = defineProps<{
  sprintId: string
  sprintEndDate?: string
  isCompleted?: boolean
}>()

const burndownData = ref<SprintBurndownVO | null>(null)
const loading = ref(false)
const error = ref(false)
const currentMode = ref<'issue_count' | 'estimation' | 'work_items'>('issue_count')

const modeUnit = computed(() => {
  if (currentMode.value === 'estimation') return '小时'
  if (currentMode.value === 'work_items') return '分钟'
  return '工单'
})

const isForecastLate = computed(() => {
  if (!burndownData.value?.forecastDate || !props.sprintEndDate) return false
  return burndownData.value.forecastDate > props.sprintEndDate
})

const hasScopeChange = computed(() => {
  if (!burndownData.value) return false
  if (currentMode.value === 'estimation' || currentMode.value === 'work_items') {
    return burndownData.value.startScopeHours != null && burndownData.value.startScopeHours > 0
  }
  return burndownData.value.startScopeIssues !== burndownData.value.totalIssues
})

function formatHours(hours?: number): string {
  if (hours == null) return '0'
  return hours % 1 === 0 ? String(hours) : hours.toFixed(1)
}

function formatForecastDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function onModeChange() {
  loadData()
}

const chartOption = computed(() => {
  if (!burndownData.value || burndownData.value.dates.length === 0) return {}

  const { dates, idealLine, actualLine, scopeLine, todayIndex } = burndownData.value
  // 短日期显示 (MM-DD)
  const shortDates = dates.map(d => d.substring(5))

  // 主题色（使用 CSS 变量兼容暗色/亮色主题）
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
  const textColor = isDark ? '#9ca3af' : '#57606a'
  const axisColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)'
  const tooltipBg = isDark ? '#2a2d33' : '#fff'
  const tooltipBorder = isDark ? '#3d4048' : '#e5e7eb'
  // YouTrack 风格配色：理想线=蓝色，实际线=红色
  const idealColor = isDark ? '#58a6ff' : '#0969da'  // 蓝色
  const actualColor = isDark ? '#f85149' : '#cf222e' // 红色
  const scopeColor = isDark ? '#d29922' : '#9a6700'
  const todayLineColor = isDark ? '#3fb950' : '#1a7f37'

  // 判断是否有范围变化（scope line 不是一条直线）
  const showScopeLine = scopeLine.length > 0 && hasScopeChange.value

  const legendData = showScopeLine
    ? ['理想进度', '实际剩余', '范围']
    : ['理想进度', '实际剩余']

  const series: any[] = [
    {
      name: '理想进度',
      type: 'line',
      data: idealLine,
      lineStyle: { width: 2, color: idealColor, type: 'solid' },  // YouTrack 风格：实线
      itemStyle: { color: idealColor },
      symbol: 'none',
      z: 1
    },
    {
      name: '实际剩余',
      type: 'line',
      data: actualLine,
      smooth: 0.3,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { width: 2.5, color: actualColor },
      itemStyle: { color: actualColor },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: isDark ? 'rgba(248, 81, 73, 0.15)' : 'rgba(207, 34, 46, 0.1)' },
            { offset: 1, color: 'rgba(248, 81, 73, 0)' }
          ]
        }
      },
      markLine: todayIndex >= 0 ? {
        silent: true,
        symbol: 'none',
        lineStyle: { color: todayLineColor, width: 1.5, type: 'solid' },
        label: {
          show: true,
          formatter: '今天',
          color: todayLineColor,
          fontSize: 10,
          position: 'start'
        },
        data: [{ xAxis: todayIndex }]
      } : undefined,
      z: 2
    }
  ]

  // 范围线：仅在有 scope change 时显示
  if (showScopeLine) {
    series.push({
      name: '范围',
      type: 'line',
      data: scopeLine,
      lineStyle: { width: 1.5, color: scopeColor, type: 'dotted' },
      itemStyle: { color: scopeColor },
      symbol: 'none',
      z: 1
    })
  }

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
        const unit = currentMode.value === 'estimation' ? '小时' : currentMode.value === 'work_items' ? '分钟' : '工单'
        let html = `<div style="font-weight:500;margin-bottom:4px">${fullDate}</div>`
        for (const p of params) {
          if (p.value !== undefined) {
            const seriesUnit = p.seriesName === '范围' ? `${unit}（总范围）` : unit
            html += `<div style="display:flex;align-items:center;gap:6px;">
              <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
              <span>${p.seriesName}：<b>${p.value}</b> ${seriesUnit}</span>
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
    series
  }
})

async function loadData() {
  loading.value = true
  error.value = false
  try {
    const res = await sprintApi.burndown(props.sprintId, currentMode.value)
    burndownData.value = res.data
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(() => props.sprintId, () => {
  loadData()
})

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.burndown-chart-container {
  margin-top: 12px;
  border-top: 1px solid var(--color-border);
  padding-top: 12px;
}

.burndown-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.burndown-title {
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

.mode-switch {
  margin-left: 12px;
}

.mode-switch :deep(.arco-radio-button) {
  font-size: 11px;
  padding: 0 8px;
  height: 22px;
  line-height: 22px;
}

.burndown-meta {
  display: flex;
  align-items: center;
  gap: 16px;
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

.meta-value.overdue {
  color: rgb(var(--danger-6));
}

.meta-value.scope-change {
  color: var(--color-text-2);
  font-size: 11px;
}

.chart-wrapper {
  height: 200px;
}

.chart-instance {
  width: 100%;
  height: 100%;
}

.chart-empty,
.chart-loading,
.chart-error {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.empty-text,
.error-text {
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
