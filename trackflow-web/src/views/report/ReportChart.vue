<template>
  <div class="report-chart-wrapper">
    <!-- 摘要信息 -->
    <div class="chart-summary">
      <span class="chart-total" v-if="data.category === 'timeline'">
        {{ data.summary?.sprintName || '' }}
        {{ data.summary?.totalResolved ? `共解决 ${data.summary.totalResolved} 个工单` : '' }}
      </span>
      <span class="chart-total" v-else-if="data.category === 'time_management'">
        {{ data.summary?.totalHours ? `共 ${data.summary.totalHours} 小时` : '' }}
        {{ data.summary?.dateRange ? `(${data.summary.dateRange})` : '' }}
      </span>
      <span class="chart-total" v-else>共 {{ data.total }} 个工单</span>
      <span class="chart-group" v-if="data.category === 'timeline'">时间线趋势</span>
      <span class="chart-group" v-else-if="data.category === 'state_transition'">状态转换统计</span>
      <span class="chart-group" v-else-if="data.category === 'time_management'">时间管理</span>
      <span class="chart-group" v-else-if="!data.secondGroupBy">
        按 {{ groupByLabel(data.groupBy) }} 分组
      </span>
      <span class="chart-group" v-else>
        {{ groupByLabel(data.groupBy) }} × {{ groupByLabel(data.secondGroupBy) }}
      </span>
    </div>

    <!-- 时间序列图表（timeline / state_transition / time_management） -->
    <div v-if="isTimeSeriesChart" class="chart-container" :style="{ height: chartHeight }">
      <v-chart
        :option="buildTimeSeriesChartOption(data)"
        autoresize
        class="report-chart-instance"
      />
    </div>

    <!-- 单维度图表 -->
    <div v-else-if="!data.secondGroupBy" class="chart-container" :style="{ height: chartHeight }">
      <v-chart
        :option="buildChartOption(data)"
        autoresize
        class="report-chart-instance"
      />
    </div>

    <!-- 双维度：堆叠条形图 + 矩阵表格 -->
    <div v-else class="cross-report-container">
      <div class="chart-container" :style="{ height: chartHeight }">
        <v-chart
          :option="buildCrossChartOption(data)"
          autoresize
          class="report-chart-instance"
        />
      </div>
      <div class="matrix-table-wrapper">
        <table class="matrix-table">
          <thead>
            <tr>
              <th class="matrix-corner">
                {{ groupByLabel(data.groupBy) }} ＼ {{ groupByLabel(data.secondGroupBy!) }}
              </th>
              <th
                v-for="col in data.secondLabels"
                :key="col"
                class="matrix-col-header"
              >{{ localizeLabel(col, data.secondGroupBy!) }}</th>
              <th class="matrix-col-header matrix-row-total">合计</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIdx) in data.matrix" :key="rowIdx">
              <td class="matrix-row-header">{{ localizeLabel(data.labels[rowIdx], data.groupBy) }}</td>
              <td
                v-for="(cell, colIdx) in row"
                :key="colIdx"
                class="matrix-cell"
                :class="{ 'has-value': cell > 0 }"
              >{{ cell || '—' }}</td>
              <td class="matrix-cell matrix-row-total">{{ row.reduce((a: number, b: number) => a + b, 0) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { localizeStatusName } from '@/utils/fieldLabels'
import { getPriorityColor } from '@/composables/usePriorityOptions'
import { useChartColors, STATUS_COLORS, CHART_PALETTE } from '@/utils/chartColors'
import type { ReportDataVO } from '@/api/report'

// 注册 ECharts 组件（按需引入）
use([CanvasRenderer, PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent])

// ─── Props ────────────────────────────────────────────────

const props = withDefaults(defineProps<{
  /** 报表数据 */
  data: ReportDataVO
  /** 图表高度，列表页用 200px，详情页用 420px */
  height?: string
  /** 分组维度标签映射（列表页可能有动态自定义字段维度） */
  dimensions?: { value: string; label: string }[]
}>(), {
  height: '200px',
  dimensions: () => []
})

// ─── 计算属性 ─────────────────────────────────────────────

const chartHeight = computed(() => props.height)

const isTimeSeriesChart = computed(() => {
  return props.data.category === 'timeline'
    || props.data.category === 'state_transition'
    || props.data.category === 'time_management'
})

// ─── ECharts 主题色（动态读取 CSS 变量，适配亮色/暗色主题） ─────────

const { chartColors } = useChartColors()

// ─── 图表预设色板 ──────────────────────────────────────────

// ─── 辅助函数 ─────────────────────────────────────────────

function getItemColor(label: string, groupBy: string, idx: number): string {
  if (groupBy === 'status') return STATUS_COLORS[label] || CHART_PALETTE[idx % CHART_PALETTE.length]
  if (groupBy === 'priority') return getPriorityColor(label) || CHART_PALETTE[idx % CHART_PALETTE.length]
  return CHART_PALETTE[idx % CHART_PALETTE.length]
}

function localizeLabel(label: string, groupBy: string): string {
  if (groupBy === 'status') return localizeStatusName(label)
  if (groupBy === 'priority') return label
  return label
}

function groupByLabel(groupBy: string): string {
  // 优先从外部传入的动态维度列表查找（含自定义字段）
  if (props.dimensions.length > 0) {
    const dim = props.dimensions.find(d => d.value === groupBy)
    if (dim) return dim.label
  }
  // 兜底内置维度
  const map: Record<string, string> = {
    status: '状态',
    assignee: '负责人',
    priority: '优先级',
    type: '工单类型',
    project: '项目',
    work_type: '工作类型',
    issue: '工单'
  }
  return map[groupBy] || groupBy
}

/** 根据 groupBy/type 推断图表类型（当后端没有返回 chartType 时的 fallback） */
function inferChartType(groupBy: string, type: string): string {
  if (groupBy === 'assignee' || type === 'by_assignee') return 'bar_horizontal'
  if (groupBy === 'status' || groupBy === 'priority' || groupBy === 'type'
    || type === 'by_status' || type === 'by_priority' || type === 'by_type') return 'pie'
  return 'bar_vertical'
}

// ─── 构建 ECharts Option ──────────────────────────────────

function buildChartOption(data: ReportDataVO): Record<string, any> {
  const chartType = data.chartType || inferChartType(data.groupBy, data.type)
  const c = chartColors.value

  if (chartType === 'pie') {
    return buildPieOption(data, c)
  } else if (chartType === 'bar_horizontal') {
    return buildBarHorizontalOption(data, c)
  } else {
    return buildBarVerticalOption(data, c)
  }
}

function buildPieOption(data: ReportDataVO, c: typeof chartColors.value): Record<string, any> {
  const items = data.labels.map((label, idx) => ({
    name: localizeLabel(label, data.groupBy),
    value: data.data[idx],
    itemStyle: { color: getItemColor(label, data.groupBy, idx) }
  }))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText, fontSize: 12 }
    },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'center',
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 10,
      itemHeight: 10
    },
    series: [{
      type: 'pie',
      radius: ['38%', '68%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 3, borderColor: c.cardBorder, borderWidth: 2 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 12, fontWeight: 500, color: c.tooltipText },
        itemStyle: { shadowBlur: 8, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.3)' }
      },
      data: items
    }]
  }
}

function buildBarHorizontalOption(data: ReportDataVO, c: typeof chartColors.value): Record<string, any> {
  const labels = data.labels.map(l => localizeLabel(l, data.groupBy))
  const colors = data.labels.map((l, i) => getItemColor(l, data.groupBy, i))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText, fontSize: 12 }
    },
    grid: { left: 80, right: 24, top: 8, bottom: 16 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 11, width: 70, overflow: 'truncate' },
      axisTick: { show: false }
    },
    series: [{
      type: 'bar',
      barWidth: '55%',
      data: data.data.map((val, idx) => ({
        value: val,
        itemStyle: { color: colors[idx], borderRadius: [0, 3, 3, 0] }
      }))
    }]
  }
}

function buildBarVerticalOption(data: ReportDataVO, c: typeof chartColors.value): Record<string, any> {
  const labels = data.labels.map(l => localizeLabel(l, data.groupBy))
  const colors = data.labels.map((l, i) => getItemColor(l, data.groupBy, i))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText, fontSize: 12 }
    },
    grid: { left: 36, right: 16, top: 8, bottom: 28 },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 11 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      barWidth: '50%',
      data: data.data.map((val, idx) => ({
        value: val,
        itemStyle: { color: colors[idx], borderRadius: [3, 3, 0, 0] }
      }))
    }]
  }
}

function buildTimeSeriesChartOption(data: ReportDataVO): Record<string, any> {
  const c = chartColors.value

  // 状态转换报表使用横向条形图
  if (data.category === 'state_transition' && data.labels && data.data) {
    return buildBarHorizontalOption(data, c)
  }

  // 时间线报表：折线图/面积图
  const dates = data.dates || []
  const series = data.series || []

  const echartseries = series.map(s => ({
    name: s.name,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 4,
    lineStyle: { width: 2, color: s.color },
    itemStyle: { color: s.color },
    areaStyle: s.seriesType === 'area' ? { opacity: 0.3, color: s.color } : undefined,
    stack: data.chartType === 'stacked_area' ? 'total' : undefined,
    data: s.data
  }))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText, fontSize: 12 }
    },
    legend: {
      bottom: 0,
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 12,
      itemHeight: 8
    },
    grid: { left: 48, right: 16, top: 16, bottom: 36 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: {
        color: c.textColor,
        fontSize: 10,
        rotate: dates.length > 14 ? 30 : 0,
        formatter: (val: string) => {
          if (val.length >= 10) return val.substring(5) // 去掉年份
          return val
        }
      },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    series: echartseries
  }
}

function buildCrossChartOption(data: ReportDataVO): Record<string, any> {
  const c = chartColors.value
  const primaryLabels = data.labels.map(l => localizeLabel(l, data.groupBy))
  const secondaryLabels = data.secondLabels || []

  const series = secondaryLabels.map((secLabel, secIdx) => ({
    name: localizeLabel(secLabel, data.secondGroupBy || ''),
    type: 'bar',
    stack: 'cross',
    barWidth: '55%',
    emphasis: { focus: 'series' },
    itemStyle: {
      color: CHART_PALETTE[secIdx % CHART_PALETTE.length],
      borderRadius: secIdx === secondaryLabels.length - 1 ? [3, 3, 0, 0] : [0, 0, 0, 0]
    },
    data: data.matrix ? data.matrix.map(row => row[secIdx] || 0) : []
  }))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText, fontSize: 12 }
    },
    legend: {
      bottom: 0,
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 10,
      itemHeight: 10
    },
    grid: { left: 80, right: 16, top: 8, bottom: 36 },
    xAxis: {
      type: 'category',
      data: primaryLabels,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 11, width: 70, overflow: 'truncate' },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    series
  }
}
</script>

<style scoped>
.report-chart-wrapper {
  width: 100%;
}

.chart-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.chart-total {
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.chart-container {
  width: 100%;
}

.report-chart-instance {
  width: 100%;
  height: 100%;
}

/* 双维度交叉报表 */
.cross-report-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.matrix-table-wrapper {
  overflow-x: auto;
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
}

.matrix-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  white-space: nowrap;
}

.matrix-table th,
.matrix-table td {
  padding: 6px 10px;
  text-align: center;
  border-bottom: 1px solid var(--tf-border-light);
}

.matrix-table th {
  background: var(--tf-bg-surface);
  color: var(--tf-text-secondary);
  font-weight: 500;
  font-size: 11px;
}

.matrix-corner {
  text-align: left !important;
  font-size: 10px;
  color: var(--tf-text-tertiary);
  min-width: 100px;
}

.matrix-row-header {
  text-align: left !important;
  font-weight: 500;
  color: var(--tf-text-primary);
  background: var(--tf-bg-surface);
}

.matrix-cell {
  color: var(--tf-text-tertiary);
  min-width: 48px;
}

.matrix-cell.has-value {
  color: var(--tf-text-primary);
  font-weight: 500;
}

.matrix-row-total {
  font-weight: 600;
  color: var(--tf-accent) !important;
  border-left: 1px solid var(--tf-border-light);
}
</style>
