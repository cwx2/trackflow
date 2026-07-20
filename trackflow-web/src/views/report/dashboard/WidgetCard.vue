<template>
  <div class="widget-card" v-if="widget">
    <!-- Widget 头部 -->
    <div class="widget-header">
      <div class="widget-header-left">
        <span class="widget-type-icon">{{ widgetIcon }}</span>
        <span class="widget-title">{{ widget.title || widgetTypeLabel }}</span>
      </div>
      <div class="widget-header-right">
        <button
          v-if="dataLoaded && !loading"
          class="widget-action-btn"
          title="刷新数据"
          @click.stop="refreshData"
        >
          <icon-refresh :spin="refreshing" :size="14" />
        </button>
        <a-dropdown v-if="isOwner" trigger="click" :popup-max-height="false">
          <button class="widget-menu-btn" @click.stop>
            <icon-more />
          </button>
          <template #content>
            <a-doption @click="$emit('edit', widget)">
              <template #icon><icon-edit /></template>
              编辑配置
            </a-doption>
            <a-doption class="danger-option" @click="$emit('delete', widget)">
              <template #icon><icon-delete /></template>
              删除微件
            </a-doption>
          </template>
        </a-dropdown>
      </div>
    </div>

    <!-- Widget 内容区 -->
    <div class="widget-body">
      <!-- 加载状态 -->
      <div v-if="loading" class="widget-loading">
        <a-spin dot />
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="widget-error">
        <icon-exclamation-circle-fill :size="24" class="error-icon" />
        <span class="error-text">{{ error }}</span>
        <a-button size="mini" type="text" @click="refreshData">
          <template #icon><icon-refresh /></template>
          重试
        </a-button>
      </div>

      <!-- 笔记微件 -->
      <template v-else-if="widget.widgetType === 'note'">
        <div class="widget-note">
          <p class="note-placeholder" v-if="!parsedConfig.content">
            点击编辑添加笔记内容...
          </p>
          <div v-else class="note-content" v-html="parsedConfig.content"></div>
        </div>
      </template>

      <!-- 数字卡片微件 -->
      <template v-else-if="widget.widgetType === 'number_card'">
        <div class="widget-number-card">
          <div class="number-value" :class="{ 'has-color': numberColor }">
            <span :style="numberColor ? { color: numberColor } : {}">{{ displayNumber }}</span>
          </div>
          <div class="number-label">{{ displayLabel }}</div>
          <div v-if="numberSubtext" class="number-subtext">{{ numberSubtext }}</div>
        </div>
      </template>

      <!-- 报表分布图微件 -->
      <template v-else-if="widget.widgetType === 'report_distribution'">
        <div v-if="chartOption" class="widget-chart-container">
          <v-chart :option="chartOption" autoresize class="widget-chart-instance" />
        </div>
        <div v-else class="widget-configure-hint">
          <icon-bar-chart :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」选择报表数据源</span>
        </div>
      </template>

      <!-- 报表图表微件 -->
      <template v-else-if="widget.widgetType === 'report'">
        <div v-if="chartOption" class="widget-chart-container">
          <v-chart :option="chartOption" autoresize class="widget-chart-instance" />
        </div>
        <div v-else class="widget-configure-hint">
          <icon-bar-chart :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」关联报表定义</span>
        </div>
      </template>

      <!-- Issue 列表微件 -->
      <template v-else-if="widget.widgetType === 'issue_list'">
        <div v-if="issueListData.length > 0" class="widget-issue-list">
          <div v-for="issue in issueListData" :key="issue.id" class="issue-item">
            <span class="issue-key">{{ issue.issueKey }}</span>
            <span class="issue-title">{{ issue.title }}</span>
          </div>
        </div>
        <div v-else class="widget-configure-hint">
          <icon-list :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」设置查询条件</span>
        </div>
      </template>

      <!-- 活动流微件 -->
      <template v-else-if="widget.widgetType === 'activity_feed'">
        <div class="widget-configure-hint">
          <icon-notification :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」设置数据源</span>
        </div>
      </template>

      <!-- Sprint 进度 -->
      <template v-else-if="widget.widgetType === 'sprint_progress'">
        <div class="widget-configure-hint">
          <icon-thunderbolt :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」选择 Sprint</span>
        </div>
      </template>

      <!-- 日历微件 -->
      <template v-else-if="widget.widgetType === 'calendar'">
        <div class="widget-configure-hint">
          <icon-calendar :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」选择项目</span>
        </div>
      </template>

      <!-- 未知类型 -->
      <template v-else>
        <div class="widget-configure-hint">
          <icon-question-circle :size="32" class="hint-icon" />
          <span class="hint-text">未知微件类型: {{ widget.widgetType }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import {
  IconMore, IconEdit, IconDelete, IconRefresh,
  IconExclamationCircleFill, IconBarChart,
  IconList, IconNotification, IconThunderbolt, IconCalendar, IconQuestionCircle
} from '@arco-design/web-vue/es/icon'
import { reportApi } from '@/api/report'
import { reportStatisticsApi } from '@/api/reportStatistics'
import type { ReportDataVO } from '@/api/report'
import type { DashboardWidgetVO } from '@/api/customDashboard'
import type { OverviewData } from '@/api/reportStatistics'

// 注册 ECharts 组件
use([CanvasRenderer, PieChart, BarChart, TooltipComponent, LegendComponent, GridComponent])

const props = defineProps<{
  widget: DashboardWidgetVO | undefined
  isOwner: boolean
}>()

defineEmits<{
  edit: [widget: DashboardWidgetVO]
  delete: [widget: DashboardWidgetVO]
}>()

// ─── 状态 ─────────────────────────────────────────────

const loading = ref(false)
const refreshing = ref(false)
const error = ref<string | null>(null)
const dataLoaded = ref(false)

// number_card 数据
const numberValue = ref<number | null>(null)
const overviewData = ref<OverviewData | null>(null)

// chart 数据
const reportDataResult = ref<ReportDataVO | null>(null)

// issue_list 数据
const issueListData = ref<Array<{ id: string; issueKey: string; title: string }>>([])

// ─── 微件类型映射 ─────────────────────────────────────────

const widgetTypeMap: Record<string, { icon: string; label: string }> = {
  note: { icon: '📝', label: '快捷笔记' },
  number_card: { icon: '🔢', label: '数字卡片' },
  report_distribution: { icon: '📊', label: '分布图表' },
  issue_list: { icon: '📋', label: 'Issue 列表' },
  activity_feed: { icon: '🔔', label: '活动流' },
  report: { icon: '📈', label: '报表图表' },
  sprint_progress: { icon: '🏃', label: 'Sprint 进度' },
  calendar: { icon: '📅', label: '到期日历' }
}

const widgetIcon = computed(() => {
  if (!props.widget) return '❓'
  return widgetTypeMap[props.widget.widgetType]?.icon || '❓'
})

const widgetTypeLabel = computed(() => {
  if (!props.widget) return ''
  return widgetTypeMap[props.widget.widgetType]?.label || props.widget.widgetType
})

const parsedConfig = computed(() => {
  if (!props.widget?.config) return {} as Record<string, any>
  try {
    return JSON.parse(props.widget.config) as Record<string, any>
  } catch {
    return {} as Record<string, any>
  }
})

// ─── Number Card 逻辑 ─────────────────────────────────────

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

const displayNumber = computed(() => {
  const config = parsedConfig.value
  // If hardcoded value exists in config, use it
  if (config.value != null) return config.value

  // If queryType is set, use fetched data
  if (config.queryType && overviewData.value) {
    const key = config.queryType as QueryType
    const val = overviewData.value[key as keyof OverviewData]
    if (key === 'completion_rate') return `${val}%`
    return val ?? '—'
  }

  return numberValue.value ?? '—'
})

const displayLabel = computed(() => {
  const config = parsedConfig.value
  if (config.label) return config.label
  if (config.queryType) return queryTypeLabels[config.queryType as QueryType] || '统计数值'
  return '统计数值'
})

const numberColor = computed(() => {
  const config = parsedConfig.value
  if (config.color) return config.color
  if (config.queryType) return queryTypeColors[config.queryType as QueryType] || null
  return null
})

const numberSubtext = computed(() => {
  const config = parsedConfig.value
  return config.subtext || null
})

// ─── Chart 逻辑 ──────────────────────────────────────────

const palette = ['#58a6ff', '#3fb950', '#f0883e', '#a371f7', '#d29922', '#f85149', '#79c0ff', '#56d364', '#ffa657', '#d2a8ff']

const statusColors: Record<string, string> = {
  'Open': '#58a6ff', 'In Progress': '#f0883e', 'Code Review': '#a371f7',
  'Testing': '#d29922', 'Done': '#3fb950', 'Cancelled': '#6b7280',
  'Reopened': '#f85149', 'Todo': '#58a6ff', 'Closed': '#3fb950',
  'Solved': '#3fb950', 'Online': '#3fb950'
}

const priorityColors: Record<string, string> = {
  'Critical': '#f85149', 'High': '#f0883e', 'Normal': '#58a6ff', 'Low': '#3fb950',
  'critical': '#f85149', 'high': '#f0883e', 'normal': '#58a6ff', 'low': '#3fb950'
}

function getItemColor(label: string, groupBy: string, idx: number): string {
  if (groupBy === 'status') return statusColors[label] || palette[idx % palette.length]
  if (groupBy === 'priority') return priorityColors[label] || palette[idx % palette.length]
  return palette[idx % palette.length]
}

const chartOption = computed(() => {
  if (!reportDataResult.value) return null
  return buildChartOption(reportDataResult.value)
})

function buildChartOption(data: ReportDataVO): Record<string, any> {
  const chartType = data.chartType || inferChartType(data.groupBy, data.type)

  if (chartType === 'pie') {
    return buildPieOption(data)
  } else if (chartType === 'bar_horizontal') {
    return buildBarHorizontalOption(data)
  } else {
    return buildBarVerticalOption(data)
  }
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
  const colors = data.labels.map((l, i) => getItemColor(l, data.groupBy, i))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      textStyle: { fontSize: 11 }
    },
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
  const colors = data.labels.map((l, i) => getItemColor(l, data.groupBy, i))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      textStyle: { fontSize: 11 }
    },
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

// ─── 数据加载 ──────────────────────────────────────────

async function loadData() {
  if (!props.widget) return

  const { widgetType } = props.widget
  const config = parsedConfig.value

  // note 不需要加载数据
  if (widgetType === 'note') {
    dataLoaded.value = true
    return
  }

  // number_card: 如果有硬编码 value，不需要请求
  if (widgetType === 'number_card' && config.value != null && !config.queryType) {
    dataLoaded.value = true
    return
  }

  // number_card with queryType: 从 dashboard statistics 获取
  if (widgetType === 'number_card' && config.queryType) {
    loading.value = true
    error.value = null
    try {
      const params: Record<string, string> = {}
      if (config.projectId) params.projectId = config.projectId
      const res = await reportStatisticsApi.dashboard(params)
      overviewData.value = res.data?.overview || null
      dataLoaded.value = true
    } catch (e: any) {
      error.value = e.response?.data?.message || '加载统计数据失败'
    } finally {
      loading.value = false
    }
    return
  }

  // number_card without config: show dash
  if (widgetType === 'number_card') {
    dataLoaded.value = true
    return
  }

  // report_distribution / report: 通过 reportId 获取报表数据
  if (widgetType === 'report_distribution' || widgetType === 'report') {
    const reportId = props.widget.reportId
    if (!reportId) {
      // No report linked, show configure hint
      dataLoaded.value = true
      return
    }
    loading.value = true
    error.value = null
    try {
      const res = await reportApi.execute(reportId)
      reportDataResult.value = res.data || null
      dataLoaded.value = true
    } catch (e: any) {
      const status = e.response?.status
      if (status === 404) {
        error.value = '关联的报表已被删除'
      } else if (status === 403) {
        error.value = '无权限查看关联报表'
      } else {
        error.value = e.response?.data?.message || '加载报表数据失败'
      }
    } finally {
      loading.value = false
    }
    return
  }

  // Other types: just mark as loaded (placeholder state)
  dataLoaded.value = true
}

async function refreshData() {
  refreshing.value = true
  error.value = null
  reportDataResult.value = null
  overviewData.value = null
  numberValue.value = null
  dataLoaded.value = false
  await loadData()
  refreshing.value = false
}

// ─── Lifecycle ────────────────────────────────────────

let autoRefreshTimer: ReturnType<typeof setInterval> | null = null

function setupAutoRefreshTimer() {
  clearAutoRefreshTimer()
  const config = parsedConfig.value
  const interval = config.refreshInterval
  if (!interval || interval <= 0) return
  autoRefreshTimer = setInterval(() => {
    refreshData()
  }, interval * 1000)
}

function clearAutoRefreshTimer() {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

onMounted(() => {
  loadData().then(() => {
    setupAutoRefreshTimer()
  })
})

// Watch widget changes (e.g., after config edit)
watch(() => props.widget?.config, () => {
  refreshData().then(() => {
    setupAutoRefreshTimer()
  })
}, { deep: true })

watch(() => props.widget?.reportId, () => {
  refreshData()
})

onBeforeUnmount(() => {
  clearAutoRefreshTimer()
})
</script>

<style scoped>
.widget-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  overflow: hidden;
}

.widget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--tf-border-light);
  flex-shrink: 0;
}

.widget-header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.widget-type-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.widget-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.widget-header-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

.widget-action-btn,
.widget-menu-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  color: var(--tf-text-tertiary);
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.widget-action-btn:hover,
.widget-menu-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.widget-body {
  flex: 1;
  padding: 12px;
  overflow: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 加载状态 */
.widget-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

/* 错误状态 */
.widget-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  text-align: center;
}

.error-icon {
  color: var(--tf-danger, #f85149);
  opacity: 0.7;
}

.error-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  line-height: 1.4;
  max-width: 160px;
}

/* 笔记微件 */
.note-placeholder {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  font-style: italic;
  margin: 0;
}

.note-content {
  font-size: 13px;
  color: var(--tf-text-primary);
  line-height: 1.5;
}

/* 数字卡片 */
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

.number-value.has-color span {
  /* Color applied inline */
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

/* 图表容器 */
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

/* 配置引导 */
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

/* Issue 列表 */
.widget-issue-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  border-radius: 4px;
  transition: background 0.15s;
}

.issue-item:hover {
  background: var(--tf-bg-hover);
}

.issue-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent);
  white-space: nowrap;
  flex-shrink: 0;
}

.issue-title {
  font-size: 12px;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.danger-option {
  color: var(--tf-danger) !important;
}
</style>
