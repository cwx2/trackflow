<template>
  <div class="time-report">
    <!-- 筛选栏 -->
    <div class="report-filters">
      <a-select
        v-model="selectedProjectId"
        placeholder="全部项目"
        allow-clear
        size="small"
        style="width: 180px"
        @change="loadData"
      >
        <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
      </a-select>
      <a-range-picker
        v-model="dateRange"
        size="small"
        style="width: 240px"
        :shortcuts="dateShortcuts"
        @change="loadData"
      />
      <a-button size="small" type="outline" @click="exportCSV" :disabled="!reportData">
        <template #icon><icon-download /></template>
        导出 CSV
      </a-button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="report-loading">
      <a-skeleton :animation="true" v-for="i in 4" :key="i" style="margin-bottom: 16px">
        <a-skeleton-shape shape="square" :style="{ width: '100%', height: '200px' }" />
      </a-skeleton>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!reportData || reportData.totalMinutes === 0" class="report-empty">
      <div class="empty-icon">⏱️</div>
      <h3 class="empty-title">暂无工时数据</h3>
      <p class="empty-desc">在选定的时间范围内没有找到工时记录。请尝试调整筛选条件。</p>
    </div>

    <!-- 报表主体 -->
    <template v-else>
      <!-- 总工时概览 -->
      <div class="overview-cards">
        <div class="stat-card">
          <div class="stat-value">{{ formatDuration(reportData.totalMinutes) }}</div>
          <div class="stat-label">总工时</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ reportData.byUser.length }}</div>
          <div class="stat-label">参与人员</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ reportData.byProject.length }}</div>
          <div class="stat-label">涉及项目</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ reportData.byWorkType.length }}</div>
          <div class="stat-label">工作类型</div>
        </div>
      </div>

      <!-- 图表网格 -->
      <div class="chart-grid">
        <!-- 每日工时趋势 -->
        <div class="chart-card chart-card-wide">
          <div class="chart-card-header">
            <h3 class="chart-title">每日工时趋势</h3>
            <span class="chart-subtitle">日期范围内每天的总工时</span>
          </div>
          <div class="chart-body">
            <v-chart :option="trendChartOption" autoresize class="chart-instance" />
          </div>
        </div>

        <!-- 按人员分组 -->
        <div class="chart-card">
          <div class="chart-card-header">
            <h3 class="chart-title">按人员分布</h3>
            <span class="chart-subtitle">各成员工时占比</span>
          </div>
          <div class="chart-body">
            <v-chart :option="userChartOption" autoresize class="chart-instance" />
          </div>
        </div>

        <!-- 按项目分组 -->
        <div class="chart-card">
          <div class="chart-card-header">
            <h3 class="chart-title">按项目分布</h3>
            <span class="chart-subtitle">各项目工时占比</span>
          </div>
          <div class="chart-body">
            <v-chart :option="projectChartOption" autoresize class="chart-instance" />
          </div>
        </div>

        <!-- 按工作类型分组 -->
        <div class="chart-card">
          <div class="chart-card-header">
            <h3 class="chart-title">按工作类型</h3>
            <span class="chart-subtitle">各类工作的工时分布</span>
          </div>
          <div class="chart-body">
            <v-chart :option="workTypeChartOption" autoresize class="chart-instance" />
          </div>
        </div>

        <!-- 交叉维度表格：项目 × 人员 -->
        <div class="chart-card chart-card-wide" v-if="reportData.crossProjectUser.length > 0">
          <div class="chart-card-header">
            <h3 class="chart-title">项目 × 人员工时明细</h3>
            <span class="chart-subtitle">每个项目中每位成员的工时投入</span>
          </div>
          <div class="cross-table">
            <div class="cross-row cross-header">
              <span class="cross-cell cross-name">项目</span>
              <span class="cross-cell cross-name">成员</span>
              <span class="cross-cell cross-value">工时</span>
              <span class="cross-cell cross-bar-cell">占比</span>
            </div>
            <div
              v-for="(item, idx) in reportData.crossProjectUser"
              :key="idx"
              class="cross-row"
            >
              <span class="cross-cell cross-name">{{ item.projectName }}</span>
              <span class="cross-cell cross-name">{{ item.userName }}</span>
              <span class="cross-cell cross-value">{{ formatDuration(item.minutes) }}</span>
              <span class="cross-cell cross-bar-cell">
                <span
                  class="cross-bar"
                  :style="{ width: crossBarWidth(item.minutes) }"
                ></span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import {
  TooltipComponent,
  GridComponent,
  LegendComponent,
  ToolboxComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { IconDownload } from '@arco-design/web-vue/es/icon'
import { reportStatisticsApi } from '@/api/reportStatistics'
import type { TimeReportData } from '@/api/reportStatistics'
import type { ProjectVO } from '@/api/types'

use([CanvasRenderer, PieChart, BarChart, LineChart, TooltipComponent, GridComponent, LegendComponent, ToolboxComponent])

const props = defineProps<{
  projects: ProjectVO[]
}>()

const loading = ref(false)
const reportData = ref<TimeReportData | null>(null)
const selectedProjectId = ref<string | undefined>(undefined)
const dateRange = ref<string[] | undefined>(undefined)

const projects = computed(() => props.projects)

const dateShortcuts = [
  { label: '本周', value: () => [getWeekStart(), today()] },
  { label: '上周', value: () => [getLastWeekStart(), getLastWeekEnd()] },
  { label: '近 7 天', value: () => [daysAgo(6), today()] },
  { label: '近 30 天', value: () => [daysAgo(29), today()] },
  { label: '本月', value: () => [getMonthStart(), today()] }
]

function today() { return new Date() }
function daysAgo(n: number) { const d = new Date(); d.setDate(d.getDate() - n); return d }
function getWeekStart() { const d = new Date(); d.setDate(d.getDate() - d.getDay() + 1); return d }
function getLastWeekStart() { const d = getWeekStart(); d.setDate(d.getDate() - 7); return d }
function getLastWeekEnd() { const d = getWeekStart(); d.setDate(d.getDate() - 1); return d }
function getMonthStart() { const d = new Date(); d.setDate(1); return d }

// ─── 主题色 ─────────────────────────────────────

const chartColors = computed(() => {
  const style = getComputedStyle(document.documentElement)
  return {
    textColor: style.getPropertyValue('--tf-text-secondary').trim() || '#9ca3af',
    axisColor: style.getPropertyValue('--tf-border').trim() || '#30363d',
    tooltipBg: style.getPropertyValue('--tf-bg-elevated').trim() || '#22252a',
    tooltipBorder: style.getPropertyValue('--tf-border').trim() || '#30363d',
    tooltipText: style.getPropertyValue('--tf-text-primary').trim() || '#e6edf3',
    cardBorder: style.getPropertyValue('--tf-bg-elevated').trim() || '#2a2d33'
  }
})

const palette = ['#58a6ff', '#3fb950', '#d29922', '#f85149', '#a371f7', '#79c0ff', '#56d364', '#e3b341', '#ff7b72', '#bc8cff']

// ─── 图表 Options ─────────────────────────────

const trendChartOption = computed(() => {
  if (!reportData.value) return {}
  const { trendDates, trendMinutes } = reportData.value
  const c = chartColors.value
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText },
      formatter: (params: any) => {
        const p = params[0]
        return `${p.axisValue}<br/>工时: <strong>${formatDuration(p.value)}</strong>`
      }
    },
    grid: { left: 50, right: 20, top: 16, bottom: 30 },
    xAxis: {
      type: 'category',
      data: trendDates.map(d => d.substring(5)),
      boundaryGap: false,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 10, interval: 'auto' },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11, formatter: (v: number) => formatDurationShort(v) },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      data: trendMinutes,
      barWidth: '60%',
      itemStyle: { color: '#58a6ff', borderRadius: [3, 3, 0, 0] }
    }]
  }
})

const userChartOption = computed(() => {
  if (!reportData.value) return {}
  const items = reportData.value.byUser
  const c = chartColors.value
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText },
      formatter: (params: any) => `${params.name}: ${formatDuration(params.value)} (${params.percent}%)`
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 10,
      itemHeight: 10
    },
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 4, borderColor: c.cardBorder, borderWidth: 2 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 12, fontWeight: 500, color: c.tooltipText }
      },
      data: items.map((item, idx) => ({
        name: item.name,
        value: item.minutes,
        itemStyle: { color: palette[idx % palette.length] }
      }))
    }]
  }
})

const projectChartOption = computed(() => {
  if (!reportData.value) return {}
  const items = reportData.value.byProject
  const c = chartColors.value
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText },
      formatter: (params: any) => `${params[0].name}: ${formatDuration(params[0].value)}`
    },
    grid: { left: 100, right: 30, top: 8, bottom: 20 },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11, formatter: (v: number) => formatDurationShort(v) },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: items.map(i => i.name),
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 11, width: 90, overflow: 'truncate' },
      axisTick: { show: false }
    },
    series: [{
      type: 'bar',
      barWidth: '55%',
      data: items.map((item, idx) => ({
        value: item.minutes,
        itemStyle: { color: palette[idx % palette.length], borderRadius: [0, 3, 3, 0] }
      }))
    }]
  }
})

const workTypeChartOption = computed(() => {
  if (!reportData.value) return {}
  const items = reportData.value.byWorkType
  const c = chartColors.value
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText },
      formatter: (params: any) => `${params.name}: ${formatDuration(params.value)} (${params.percent}%)`
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 10,
      itemHeight: 10
    },
    series: [{
      type: 'pie',
      radius: ['0%', '65%'],
      center: ['35%', '50%'],
      roseType: 'radius',
      itemStyle: { borderRadius: 4, borderColor: c.cardBorder, borderWidth: 2 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 12, fontWeight: 500, color: c.tooltipText }
      },
      data: items.map((item, idx) => ({
        name: item.name,
        value: item.minutes,
        itemStyle: { color: palette[idx % palette.length] }
      }))
    }]
  }
})

// ─── 工具方法 ─────────────────────────────

function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes}m`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

function formatDurationShort(minutes: number): string {
  if (minutes < 60) return `${minutes}m`
  return `${(minutes / 60).toFixed(1)}h`
}

function crossBarWidth(minutes: number): string {
  if (!reportData.value || reportData.value.totalMinutes === 0) return '0%'
  return Math.max(4, (minutes / reportData.value.totalMinutes) * 100) + '%'
}

function formatDate(val: any): string {
  if (typeof val === 'string') return val.substring(0, 10)
  if (val instanceof Date) return val.toISOString().substring(0, 10)
  return ''
}

// ─── 数据加载 ─────────────────────────────

async function loadData() {
  loading.value = true
  reportData.value = null
  try {
    const params: any = {}
    if (selectedProjectId.value) params.projectId = selectedProjectId.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = formatDate(dateRange.value[0])
      params.endDate = formatDate(dateRange.value[1])
    }
    const res = await reportStatisticsApi.timeReport(params)
    reportData.value = res.data
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载时间报表失败')
  } finally {
    loading.value = false
  }
}

// ─── 导出 CSV ─────────────────────────────

function exportCSV() {
  if (!reportData.value) return

  const data = reportData.value
  const lines: string[] = []
  const BOM = '\uFEFF'

  lines.push('=== 时间报表 ===')
  lines.push(`总工时,${formatDuration(data.totalMinutes)}`)
  lines.push('')

  lines.push('=== 按人员 ===')
  lines.push('人员,工时(分钟),工时,占比')
  data.byUser.forEach(item => {
    lines.push(`${item.name},${item.minutes},${formatDuration(item.minutes)},${item.percentage}%`)
  })
  lines.push('')

  lines.push('=== 按项目 ===')
  lines.push('项目,工时(分钟),工时,占比')
  data.byProject.forEach(item => {
    lines.push(`${item.name},${item.minutes},${formatDuration(item.minutes)},${item.percentage}%`)
  })
  lines.push('')

  lines.push('=== 按工作类型 ===')
  lines.push('工作类型,工时(分钟),工时,占比')
  data.byWorkType.forEach(item => {
    lines.push(`${item.name},${item.minutes},${formatDuration(item.minutes)},${item.percentage}%`)
  })
  lines.push('')

  lines.push('=== 每日趋势 ===')
  lines.push('日期,工时(分钟),工时')
  data.trendDates.forEach((date, idx) => {
    lines.push(`${date},${data.trendMinutes[idx]},${formatDuration(data.trendMinutes[idx])}`)
  })
  lines.push('')

  if (data.crossProjectUser.length > 0) {
    lines.push('=== 项目×人员明细 ===')
    lines.push('项目,人员,工时(分钟),工时')
    data.crossProjectUser.forEach(item => {
      lines.push(`${item.projectName},${item.userName},${item.minutes},${formatDuration(item.minutes)}`)
    })
  }

  const csvContent = BOM + lines.join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `TrackFlow_时间报表_${new Date().toISOString().substring(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  Message.success('CSV 导出成功')
}

onMounted(() => {
  loadData()
})

// 公开方法供父组件调用
defineExpose({ loadData })
</script>

<style scoped>
.time-report {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.report-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.report-loading,
.report-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0;
  max-width: 320px;
  line-height: 1.5;
}

/* 概览卡片 */
.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.stat-card {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--tf-text-primary);
  line-height: 1.2;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* 图表网格 */
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.chart-card {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.chart-card-wide {
  grid-column: 1 / -1;
}

.chart-card-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}

.chart-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0;
}

.chart-subtitle {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.chart-body {
  flex: 1;
  min-height: 0;
}

.chart-instance {
  width: 100%;
  height: 240px;
}

.chart-card-wide .chart-instance {
  height: 260px;
}

/* 交叉表格 */
.cross-table {
  display: flex;
  flex-direction: column;
  max-height: 320px;
  overflow-y: auto;
}

.cross-row {
  display: grid;
  grid-template-columns: 1fr 1fr 100px 1fr;
  padding: 6px 8px;
  font-size: 12px;
  color: var(--tf-text-primary);
  border-radius: 4px;
  align-items: center;
}

.cross-row:hover:not(.cross-header) {
  background: var(--tf-bg-hover);
}

.cross-header {
  color: var(--tf-text-tertiary);
  font-weight: 500;
  font-size: 11px;
  border-bottom: 1px solid var(--tf-border-light);
  margin-bottom: 2px;
}

.cross-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cross-value {
  text-align: right;
  font-weight: 500;
}

.cross-bar-cell {
  padding-left: 12px;
}

.cross-bar {
  display: block;
  height: 6px;
  background: var(--tf-accent);
  border-radius: 3px;
  transition: width 0.2s ease;
}

@media (max-width: 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
