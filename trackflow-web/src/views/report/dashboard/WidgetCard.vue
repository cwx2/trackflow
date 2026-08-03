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
            <a-doption @click="handleCopyLink">
              <template #icon><icon-link /></template>
              复制链接
            </a-doption>
            <a-doption @click="$emit('move', widget)">
              <template #icon><icon-swap /></template>
              移动到其他仪表盘
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
          <div v-for="issue in issueListData" :key="issue.id" class="issue-item" @click="navigateToIssue(issue.id)">
            <span class="issue-key">{{ issue.issueKey }}</span>
            <span class="issue-title">{{ issue.title }}</span>
          </div>
        </div>
        <div v-else-if="parsedConfig.queryType && dataLoaded && !loading" class="widget-configure-hint">
          <icon-check-circle :size="32" class="hint-icon" style="color: var(--tf-text-quaternary)" />
          <span class="hint-text" style="color: var(--tf-text-tertiary)">暂无匹配的工单</span>
        </div>
        <div v-else-if="!parsedConfig.queryType && !loading" class="widget-configure-hint">
          <icon-list :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」设置查询条件</span>
        </div>
      </template>

      <!-- 活动流微件 -->
      <template v-else-if="widget.widgetType === 'activity_feed'">
        <div v-if="activityFeedData.length > 0" class="widget-activity-feed">
          <div v-for="item in activityFeedData" :key="item.id" class="activity-item">
            <div class="activity-header">
              <span class="activity-user">{{ item.userName || '系统' }}</span>
              <span class="activity-time">{{ formatActivityTime(item.createdAt) }}</span>
            </div>
            <div class="activity-body">
              <span class="activity-action">{{ formatActivityAction(item.action, item.fieldName) }}</span>
              <span class="activity-issue">{{ item.issueKey }}</span>
              <span v-if="item.issueTitle" class="activity-issue-title">{{ item.issueTitle }}</span>
            </div>
            <div v-if="item.oldValue || item.newValue" class="activity-change">
              <span v-if="item.oldValue" class="change-old">{{ item.oldValue }}</span>
              <span v-if="item.oldValue && item.newValue" class="change-arrow">→</span>
              <span v-if="item.newValue" class="change-new">{{ item.newValue }}</span>
            </div>
          </div>
        </div>
        <div v-else-if="!loading" class="widget-configure-hint">
          <icon-notification :size="32" class="hint-icon" />
          <span class="hint-text">暂无活动记录，点击「编辑配置」设置筛选条件</span>
        </div>
      </template>

      <!-- Sprint 进度 -->
      <template v-else-if="widget.widgetType === 'sprint_progress'">
        <div class="widget-configure-hint">
          <icon-thunderbolt :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」选择 Sprint</span>
        </div>
      </template>

      <!-- 敏捷图表微件（Burndown / Cumulative Flow） -->
      <template v-else-if="widget.widgetType === 'agile_chart'">
        <div v-if="agileChartOption" class="widget-chart-container">
          <v-chart :option="agileChartOption" autoresize class="widget-chart-instance" />
        </div>
        <div v-else-if="!loading" class="widget-configure-hint">
          <icon-bar-chart :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」选择 Sprint 和图表类型</span>
        </div>
      </template>

      <!-- 看板状态微件（堆叠条形图） -->
      <template v-else-if="widget.widgetType === 'agile_board_status'">
        <div v-if="boardStatusOption" class="widget-chart-container">
          <v-chart :option="boardStatusOption" autoresize class="widget-chart-instance" />
        </div>
        <div v-else-if="!loading" class="widget-configure-hint">
          <icon-bar-chart :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」选择 Sprint</span>
        </div>
      </template>

      <!-- 日历微件 -->
      <template v-else-if="widget.widgetType === 'calendar'">
        <!-- 未配置项目时显示引导 -->
        <div v-if="!parsedConfig.projectId" class="widget-configure-hint">
          <icon-calendar :size="32" class="hint-icon" />
          <span class="hint-text">点击「编辑配置」选择项目</span>
        </div>
        <!-- 已配置项目，渲染月历 -->
        <div v-else class="widget-calendar">
          <!-- 月份导航 -->
          <div class="calendar-nav">
            <button class="calendar-nav-btn" @click.stop="prevMonth" title="上一月">
              <icon-left :size="12" />
            </button>
            <span class="calendar-title">{{ calendarTitle }}</span>
            <button class="calendar-nav-btn" @click.stop="nextMonth" title="下一月">
              <icon-right :size="12" />
            </button>
          </div>
          <!-- 星期表头 -->
          <div class="calendar-weekdays">
            <span v-for="wd in ['日','一','二','三','四','五','六']" :key="wd" class="calendar-wd">{{ wd }}</span>
          </div>
          <!-- 日期格子 -->
          <div class="calendar-grid">
            <div
              v-for="cell in calendarCells"
              :key="cell.date"
              class="calendar-cell"
              :class="{
                'other-month': !cell.isCurrentMonth,
                'today': cell.isToday,
                'has-issues': cell.issues.length > 0
              }"
            >
              <span class="cell-day">{{ cell.day }}</span>
              <!-- 最多显示2个工单，其余显示+N -->
              <template v-if="cell.isCurrentMonth">
                <div
                  v-for="issue in cell.issues.slice(0, 2)"
                  :key="issue.id"
                  class="cell-issue"
                  :class="{ overdue: cell.isOverdue && !cell.isToday }"
                  :title="issue.issueKey + ' ' + issue.title"
                  @click.stop="navigateToIssue(issue.id)"
                >
                  <span class="cell-issue-key">{{ issue.issueKey }}</span>
                  <span class="cell-issue-title">{{ issue.title }}</span>
                </div>
                <div v-if="cell.issues.length > 2" class="cell-issue-more">
                  +{{ cell.issues.length - 2 }} 更多
                </div>
              </template>
            </div>
          </div>
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
import { Message } from '@arco-design/web-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent, MarkLineComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import {
  IconMore, IconEdit, IconDelete, IconRefresh, IconLink, IconSwap,
  IconExclamationCircleFill, IconBarChart,
  IconList, IconNotification, IconThunderbolt, IconCalendar, IconQuestionCircle,
  IconCheckCircle, IconLeft, IconRight
} from '@arco-design/web-vue/es/icon'
import { reportApi } from '@/api/report'
import { reportStatisticsApi } from '@/api/reportStatistics'
import { sprintApi } from '@/api/sprint'
import { dashboardApi } from '@/api/dashboard'
import { issueApi } from '@/api/issue'
import { useRouter } from 'vue-router'
import type { ReportDataVO } from '@/api/report'
import type { DashboardWidgetVO } from '@/api/customDashboard'
import type { OverviewData } from '@/api/reportStatistics'
import type { SprintBurndownVO, SprintVO, IssueVO } from '@/api/types'

// 注册 ECharts 组件
use([CanvasRenderer, PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent, MarkLineComponent])

const props = defineProps<{
  widget: DashboardWidgetVO | undefined
  isOwner: boolean
}>()

defineEmits<{
  edit: [widget: DashboardWidgetVO]
  delete: [widget: DashboardWidgetVO]
  move: [widget: DashboardWidgetVO]
}>()

const router = useRouter()

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

// agile_chart 数据
const agileChartData = ref<SprintBurndownVO | null>(null)
const cumulativeFlowData = ref<{ dates: string[]; series: Array<{ name: string; color: string; data: number[] }> } | null>(null)

// agile_board_status 数据
const boardStatusData = ref<{ totalIssues: number; doneIssues: number; inProgressIssues: number; todoIssues: number; sprintName: string } | null>(null)

// activity_feed 数据
const activityFeedData = ref<Array<{
  id: string; issueId: string; issueKey: string; issueTitle: string
  userId: string; userName: string; userAvatar?: string
  action: string; fieldName?: string; oldValue?: string; newValue?: string
  createdAt: string
}>>([])

// calendar 数据：当前展示的年月
const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth() + 1) // 1-12
// 按日期分组的工单（key: 'yyyy-MM-dd'）
const calendarIssueMap = ref<Map<string, IssueVO[]>>(new Map())

// ─── Calendar 辅助计算 ────────────────────────────────────

/** 当前月日历格子（包含补齐的前后月日期） */
interface CalendarCell {
  date: string        // 'yyyy-MM-dd'
  day: number         // 日（1-31）
  isCurrentMonth: boolean
  isToday: boolean
  isOverdue: boolean
  issues: IssueVO[]
}

const calendarCells = computed((): CalendarCell[] => {
  const today = new Date()
  const todayStr = formatDate(today)
  const year = calendarYear.value
  const month = calendarMonth.value
  const firstDay = new Date(year, month - 1, 1)
  const lastDay = new Date(year, month, 0)
  const cells: CalendarCell[] = []
  const startWeekday = firstDay.getDay() // 0=Sunday

  // 补齐上月末尾（以周一为起始，但使用自然周日=0）
  for (let i = 0; i < startWeekday; i++) {
    const d = new Date(year, month - 1, -startWeekday + i + 1)
    const dateStr = formatDate(d)
    cells.push({ date: dateStr, day: d.getDate(), isCurrentMonth: false, isToday: false, isOverdue: false, issues: [] })
  }

  // 本月各天
  for (let day = 1; day <= lastDay.getDate(); day++) {
    const d = new Date(year, month - 1, day)
    const dateStr = formatDate(d)
    const issues = calendarIssueMap.value.get(dateStr) || []
    const isOverdue = dateStr < todayStr
    cells.push({ date: dateStr, day, isCurrentMonth: true, isToday: dateStr === todayStr, isOverdue, issues })
  }

  // 补齐下月（凑满6行×7列）
  const remaining = 42 - cells.length
  for (let i = 1; i <= remaining; i++) {
    const d = new Date(year, month, i)
    const dateStr = formatDate(d)
    cells.push({ date: dateStr, day: i, isCurrentMonth: false, isToday: false, isOverdue: false, issues: [] })
  }

  return cells
})

const calendarTitle = computed(() => `${calendarYear.value} 年 ${calendarMonth.value} 月`)

function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function prevMonth() {
  if (calendarMonth.value === 1) {
    calendarYear.value--
    calendarMonth.value = 12
  } else {
    calendarMonth.value--
  }
  loadCalendarData()
}

function nextMonth() {
  if (calendarMonth.value === 12) {
    calendarYear.value++
    calendarMonth.value = 1
  } else {
    calendarMonth.value++
  }
  loadCalendarData()
}

function navigateToIssue(issueId: string) {
  router.push(`/issues/${issueId}`)
}

async function loadCalendarData() {
  const config = parsedConfig.value
  if (!config.projectId) {
    calendarIssueMap.value = new Map()
    return
  }
  loading.value = true
  error.value = null
  try {
    const year = calendarYear.value
    const month = calendarMonth.value
    const dueAfter = `${year}-${String(month).padStart(2, '0')}-01`
    const lastDay = new Date(year, month, 0).getDate()
    const dueBefore = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`

    const res = await issueApi.list({
      projectId: config.projectId,
      dueAfter,
      dueBefore,
      pageSize: 100,
      page: 1
    })

    const map = new Map<string, IssueVO[]>()
    const issues = res.data?.list || []
    for (const issue of issues) {
      if (!issue.dueDate) continue
      // dueDate may be 'yyyy-MM-dd' or ISO string
      const dateKey = issue.dueDate.substring(0, 10)
      if (!map.has(dateKey)) map.set(dateKey, [])
      map.get(dateKey)!.push(issue)
    }
    calendarIssueMap.value = map
    dataLoaded.value = true
  } catch (e: any) {
    error.value = e.response?.data?.message || '加载日历数据失败'
  } finally {
    loading.value = false
  }
}

// ─── 微件类型映射 ─────────────────────────────────────────
const widgetTypeMap: Record<string, { icon: string; label: string }> = {
  note: { icon: '📝', label: '快捷笔记' },
  number_card: { icon: '🔢', label: '数字卡片' },
  report_distribution: { icon: '📊', label: '分布图表' },
  issue_list: { icon: '📋', label: 'Issue 列表' },
  activity_feed: { icon: '🔔', label: '活动流' },
  report: { icon: '📈', label: '报表图表' },
  sprint_progress: { icon: '🏃', label: 'Sprint 进度' },
  calendar: { icon: '📅', label: '到期日历' },
  agile_chart: { icon: '📉', label: '敏捷图表' },
  agile_board_status: { icon: '📊', label: '看板状态' }
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

// ─── Agile Chart 逻辑 ──────────────────────────────────────

const agileChartOption = computed(() => {
  const config = parsedConfig.value
  const chartType = config.chartType || 'burndown'

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
      borderColor: 'var(--tf-border, #30363d)',
      textStyle: { color: 'var(--tf-text-primary, #e6edf3)', fontSize: 11 }
    },
    legend: {
      data: ['理想线', '实际线', '范围线'],
      bottom: 0,
      textStyle: { color: 'var(--tf-text-secondary, #9ca3af)', fontSize: 10 },
      itemWidth: 16,
      itemHeight: 3
    },
    grid: { left: 36, right: 12, top: 12, bottom: 32 },
    xAxis: {
      type: 'category',
      data: data.dates.map(d => d.substring(5)), // MM-DD
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
        lineStyle: { type: 'dashed', color: '#6b7280', width: 1.5 },
        symbol: 'none',
        itemStyle: { color: '#6b7280' }
      },
      {
        name: '实际线',
        type: 'line',
        data: data.actualLine,
        lineStyle: { color: '#58a6ff', width: 2 },
        symbol: 'circle',
        symbolSize: 4,
        itemStyle: { color: '#58a6ff' },
        ...(todayIdx !== undefined ? {
          markLine: {
            silent: true,
            data: [{ xAxis: todayIdx }],
            lineStyle: { type: 'solid', color: '#f0883e', width: 1 },
            label: { show: false }
          }
        } : {})
      },
      {
        name: '范围线',
        type: 'line',
        data: data.scopeLine,
        lineStyle: { color: '#a371f7', width: 1.5, type: 'dotted' },
        symbol: 'none',
        itemStyle: { color: '#a371f7' }
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
      borderColor: 'var(--tf-border, #30363d)',
      textStyle: { color: 'var(--tf-text-primary, #e6edf3)', fontSize: 11 }
    },
    legend: {
      data: data.series.map(s => s.name),
      bottom: 0,
      textStyle: { color: 'var(--tf-text-secondary, #9ca3af)', fontSize: 10 },
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

// ─── Agile Board Status 逻辑 ──────────────────────────────

const boardStatusOption = computed(() => {
  if (!boardStatusData.value) return null
  return buildBoardStatusOption(boardStatusData.value)
})

function buildBoardStatusOption(data: { totalIssues: number; doneIssues: number; inProgressIssues: number; todoIssues: number; sprintName: string }): Record<string, any> {
  const completionRate = data.totalIssues > 0 ? Math.round((data.doneIssues / data.totalIssues) * 100) : 0

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'var(--tf-bg-elevated, #22252a)',
      borderColor: 'var(--tf-border, #30363d)',
      textStyle: { color: 'var(--tf-text-primary, #e6edf3)', fontSize: 11 },
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
      textStyle: { color: 'var(--tf-text-secondary, #9ca3af)', fontSize: 10 },
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
        itemStyle: { color: '#58a6ff', borderRadius: [3, 0, 0, 3] }
      },
      {
        name: '进行中',
        type: 'bar',
        stack: 'sprint',
        data: [data.inProgressIssues],
        barWidth: '60%',
        itemStyle: { color: '#f0883e' }
      },
      {
        name: '已完成',
        type: 'bar',
        stack: 'sprint',
        data: [data.doneIssues],
        barWidth: '60%',
        itemStyle: { color: '#3fb950', borderRadius: [0, 3, 3, 0] }
      }
    ]
  }
}

// ─── 活动流格式化 ──────────────────────────────────────────

const activityActionLabels: Record<string, string> = {
  created: '创建了',
  commented: '评论了',
  status_changed: '变更了状态',
  status_reverted: '撤销了状态变更',
  field_change: '修改了',
  update: '更新了',
  updated: '更新了',
  assigned: '分配了',
  auto_assigned: '自动分配了',
  attachment_added: '添加了附件',
  link_added: '添加了关联',
  link_removed: '移除了关联',
  tag_added: '修改了标签',
  time_logged: '记录了工时',
  time_updated: '更新了工时',
  time_removed: '删除了工时',
  deleted: '删除了',
  restored: '恢复了',
  moved_to_project: '移动了项目',
  // Sprint 相关操作
  create_sprint: '创建了迭代',
  activate_sprint: '激活了迭代',
  complete_sprint: '完成了迭代',
  update_sprint: '更新了迭代',
  archive_sprint: '归档了迭代',
  auto_complete_sprint: '自动完成了迭代'
}

function formatActivityAction(action: string, fieldName?: string): string {
  const label = activityActionLabels[action] || action
  if (action === 'field_change' && fieldName) {
    return `修改了 ${fieldName}`
  }
  return label
}

function formatActivityTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 7) return `${diffDay} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// ─── 数据加载 ──────────────────────────────────────────

async function loadData(force = false) {
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
      const res = await reportApi.execute(reportId, force || undefined)
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

  // agile_chart: 加载燃尽图或累积流图数据
  if (widgetType === 'agile_chart') {
    const sprintId = config.sprintId
    const projectId = config.projectId
    const chartType = config.chartType || 'burndown'

    if (!sprintId && !projectId) {
      dataLoaded.value = true
      return
    }

    loading.value = true
    error.value = null
    try {
      if (chartType === 'burndown' && sprintId) {
        const res = await sprintApi.burndown(sprintId)
        agileChartData.value = res.data || null
      } else if (chartType === 'cumulative_flow' && projectId) {
        const res = await reportStatisticsApi.cumulativeFlow(projectId)
        cumulativeFlowData.value = res.data || null
      }
      dataLoaded.value = true
    } catch (e: any) {
      error.value = e.response?.data?.message || '加载敏捷图表数据失败'
    } finally {
      loading.value = false
    }
    return
  }

  // agile_board_status: 加载 Sprint 状态分布
  if (widgetType === 'agile_board_status') {
    const sprintId = config.sprintId
    if (!sprintId) {
      dataLoaded.value = true
      return
    }

    loading.value = true
    error.value = null
    try {
      const res = await sprintApi.getById(sprintId)
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
      dataLoaded.value = true
    } catch (e: any) {
      error.value = e.response?.data?.message || '加载看板状态数据失败'
    } finally {
      loading.value = false
    }
    return
  }

  // activity_feed: 加载活动流数据
  if (widgetType === 'activity_feed') {
    loading.value = true
    error.value = null
    try {
      const params: Record<string, any> = {}
      if (config.projectIds && config.projectIds.length > 0) params.projectIds = config.projectIds
      if (config.actions && config.actions.length > 0) params.actions = config.actions
      if (config.userIds && config.userIds.length > 0) params.userIds = config.userIds
      params.limit = config.limit || 10
      const res = await dashboardApi.activityFeed(params)
      activityFeedData.value = res.data || []
      dataLoaded.value = true
    } catch (e: any) {
      error.value = e.response?.data?.message || '加载活动数据失败'
    } finally {
      loading.value = false
    }
    return
  }

  // issue_list: 加载工单列表数据
  if (widgetType === 'issue_list') {
    loading.value = true
    error.value = null
    try {
      const params: Record<string, any> = { pageSize: config.pageSize || 10, page: 1 }
      // 按 queryType 决定筛选条件
      const queryType = config.queryType as string | undefined
      if (queryType === 'open') {
        params.hideResolved = 'true'
      } else if (queryType === 'closed') {
        params.onlyResolved = 'true'
      } else if (queryType === 'my_open') {
        params.hideResolved = 'true'
        params.assigneeId = 'me'
      }
      // 可选项目筛选
      if (config.projectId) params.projectId = config.projectId
      // 排序：默认按更新时间倒序
      params.sort = config.sort || '-updatedAt'

      const res = await issueApi.list(params)
      const issues = res.data?.list || []
      issueListData.value = issues.map(item => ({
        id: item.id,
        issueKey: item.issueKey || '',
        title: item.title || ''
      }))
      dataLoaded.value = true
    } catch (e: any) {
      error.value = e.response?.data?.message || '加载工单列表失败'
    } finally {
      loading.value = false
    }
    return
  }

  // calendar: 加载月历数据
  if (widgetType === 'calendar') {
    if (!config.projectId) {
      // 未配置项目，显示引导提示
      dataLoaded.value = true
      return
    }
    await loadCalendarData()
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
  agileChartData.value = null
  cumulativeFlowData.value = null
  boardStatusData.value = null
  activityFeedData.value = []
  calendarIssueMap.value = new Map()
  dataLoaded.value = false
  await loadData(true)
  refreshing.value = false
}

async function handleCopyLink() {
  if (!props.widget) return
  const baseUrl = window.location.origin
  const link = `${baseUrl}/dashboard?id=${props.widget.dashboardId}&widget=${props.widget.id}`
  try {
    await navigator.clipboard.writeText(link)
    Message.success('链接已复制到剪贴板')
  } catch {
    // Fallback for non-HTTPS environments
    const textarea = document.createElement('textarea')
    textarea.value = link
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    Message.success('链接已复制到剪贴板')
  }
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
  cursor: pointer;
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

/* Activity Feed Widget */
.widget-activity-feed {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  max-height: 100%;
}

.activity-item {
  padding: 6px 8px;
  border-radius: 4px;
  transition: background 0.15s;
}

.activity-item:hover {
  background: var(--tf-bg-hover);
}

.activity-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.activity-user {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.activity-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.activity-body {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.activity-action {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

.activity-issue {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent);
  white-space: nowrap;
}

.activity-issue-title {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.activity-change {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 11px;
}

.change-old {
  color: var(--tf-text-tertiary);
  text-decoration: line-through;
}

.change-arrow {
  color: var(--tf-text-quaternary);
}

.change-new {
  color: var(--tf-text-secondary);
  font-weight: 500;
}

/* Calendar Widget */
.widget-calendar {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 4px;
  overflow: hidden;
}

.calendar-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px;
  flex-shrink: 0;
}

.calendar-nav-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  color: var(--tf-text-secondary);
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.calendar-nav-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.calendar-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 1px;
  flex-shrink: 0;
}

.calendar-wd {
  text-align: center;
  font-size: 10px;
  color: var(--tf-text-tertiary);
  padding: 2px 0;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 1px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.calendar-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-height: 28px;
  padding: 2px 3px;
  border-radius: 3px;
  border: 1px solid transparent;
  transition: background 0.1s;
}

.calendar-cell.has-issues {
  background: var(--tf-bg-hover);
}

.calendar-cell.today {
  border-color: var(--tf-accent, #58a6ff);
}

.calendar-cell.today .cell-day {
  color: var(--tf-accent, #58a6ff);
  font-weight: 700;
}

.calendar-cell.other-month {
  opacity: 0.35;
}

.cell-day {
  font-size: 10px;
  color: var(--tf-text-secondary);
  line-height: 1.2;
  font-weight: 500;
}

.cell-issue {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 1px 3px;
  background: rgba(88, 166, 255, 0.15);
  border-radius: 2px;
  cursor: pointer;
  transition: background 0.1s;
  overflow: hidden;
}

.cell-issue:hover {
  background: rgba(88, 166, 255, 0.3);
}

.cell-issue.overdue {
  background: rgba(248, 81, 73, 0.15);
}

.cell-issue.overdue:hover {
  background: rgba(248, 81, 73, 0.3);
}

.cell-issue-key {
  font-size: 9px;
  font-weight: 600;
  color: var(--tf-accent, #58a6ff);
  white-space: nowrap;
  flex-shrink: 0;
}

.cell-issue.overdue .cell-issue-key {
  color: var(--tf-danger, #f85149);
}

.cell-issue-title {
  font-size: 9px;
  color: var(--tf-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-issue-more {
  font-size: 9px;
  color: var(--tf-text-tertiary);
  padding: 0 2px;
  cursor: default;
}
</style>
