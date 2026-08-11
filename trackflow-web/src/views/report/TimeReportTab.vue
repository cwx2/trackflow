<template>
  <div class="time-report">
    <!-- 顶部工具栏 -->
    <div class="report-toolbar">
      <!-- 视图切换 -->
      <div class="view-switcher">
        <a-radio-group v-model="activeView" type="button" size="small" @change="onViewChange">
          <a-radio value="charts">
            <template #radio><icon-bar-chart />&nbsp;图表</template>
          </a-radio>
          <a-radio value="issue">
            <template #radio><icon-list />&nbsp;按工单</template>
          </a-radio>
          <a-radio value="user">
            <template #radio><icon-user />&nbsp;按用户</template>
          </a-radio>
          <a-radio value="work_item">
            <template #radio><icon-clock-circle />&nbsp;工时明细</template>
          </a-radio>
        </a-radio-group>
      </div>

      <!-- 筛选栏 -->
      <div class="report-filters">
        <a-select
          v-model="selectedProjectId"
          placeholder="全部项目"
          allow-clear
          size="small"
          style="width: 180px"
          @change="onFilterChange"
        >
          <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
        </a-select>
        <a-range-picker
          v-model="dateRange"
          size="small"
          style="width: 240px"
          :shortcuts="dateShortcuts"
          @change="onFilterChange"
        />
        <a-button
          v-if="activeView === 'charts'"
          size="small"
          type="outline"
          @click="exportCSV"
          :disabled="!chartData"
        >
          <template #icon><icon-download /></template>
          导出 CSV
        </a-button>
        <a-button
          v-if="activeView !== 'charts'"
          size="small"
          type="outline"
          @click="exportTableCSV"
        >
          <template #icon><icon-download /></template>
          导出 CSV
        </a-button>
      </div>
    </div>

    <!-- 图表视图 -->
    <template v-if="activeView === 'charts'">
      <!-- 加载状态 -->
      <div v-if="loading" class="report-loading">
        <a-skeleton :animation="true" v-for="i in 4" :key="i" style="margin-bottom: 16px">
          <a-skeleton-shape shape="square" :style="{ width: '100%', height: '200px' }" />
        </a-skeleton>
      </div>

      <!-- 空状态 -->
      <EmptyState
        v-else-if="!chartData || chartData.totalMinutes === 0"
        icon="clock-circle"
        title="暂无工时数据"
        description="在选定的时间范围内没有找到工时记录。请尝试调整筛选条件。"
      />

      <!-- 报表主体 -->
      <template v-else>
        <!-- 总工时概览 -->
        <div class="overview-cards">
          <div class="stat-card">
            <div class="stat-value">{{ formatDuration(chartData.totalMinutes) }}</div>
            <div class="stat-label">总工时</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ chartData.byUser.length }}</div>
            <div class="stat-label">参与人员</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ chartData.byProject.length }}</div>
            <div class="stat-label">涉及项目</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ chartData.byWorkType.length }}</div>
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
          <div class="chart-card chart-card-wide" v-if="chartData.crossProjectUser.length > 0">
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
                v-for="(item, idx) in chartData.crossProjectUser"
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
    </template>

    <!-- 表格视图（Per Issue / Per User / Per Work Item） -->
    <template v-else>
      <!-- 加载状态 -->
      <div v-if="tableLoading" class="report-loading">
        <a-skeleton :animation="true" v-for="i in 5" :key="i" style="margin-bottom: 8px">
          <a-skeleton-shape shape="square" :style="{ width: '100%', height: '40px' }" />
        </a-skeleton>
      </div>

      <!-- 空状态 -->
      <EmptyState
        v-else-if="!tableData || tableData.totalCount === 0"
        icon="clock-circle"
        title="暂无工时数据"
        description="在选定的时间范围内没有找到工时记录。请尝试调整筛选条件。"
      />

      <template v-else>
        <!-- 概览 -->
        <div class="table-overview">
          <span class="table-overview-item">
            <span class="table-overview-label">当前范围总工时：</span>
            <span class="table-overview-value">{{ formatDuration(tableData.totalMinutes) }}</span>
          </span>
          <span class="table-overview-item">
            <span class="table-overview-label">共 {{ tableData.totalCount }} 条记录</span>
          </span>
        </div>

        <!-- Per Issue 视图 -->
        <template v-if="activeView === 'issue'">
          <a-table
            :columns="issueColumns"
            :data="tableData.issueGroups || []"
            :pagination="false"
            :bordered="{ wrapper: false, cell: false }"
            size="small"
            row-key="issueId"
          >
            <template #issueKey="{ record }">
              <span class="issue-key">{{ record.issueKey }}</span>
            </template>
            <template #title="{ record }">
              <span class="issue-title">{{ record.title }}</span>
            </template>
            <template #totalMinutes="{ record }">
              <span class="time-value">{{ formatDuration(record.totalMinutes) }}</span>
            </template>
          </a-table>
        </template>

        <!-- Per User 视图 -->
        <template v-else-if="activeView === 'user'">
          <a-table
            :columns="userColumns"
            :data="tableData.userGroups || []"
            :pagination="false"
            :bordered="{ wrapper: false, cell: false }"
            size="small"
            row-key="userId"
          >
            <template #userName="{ record }">
              <span class="user-name">{{ record.userName }}</span>
            </template>
            <template #totalMinutes="{ record }">
              <span class="time-value">{{ formatDuration(record.totalMinutes) }}</span>
            </template>
            <template #byProject="{ record }">
              <div class="project-chips">
                <a-tag
                  v-for="pItem in record.byProject.slice(0, 3)"
                  :key="pItem.projectName"
                  size="small"
                >
                  {{ pItem.projectName }}: {{ formatDuration(pItem.minutes) }}
                </a-tag>
                <span v-if="record.byProject.length > 3" class="more-tag">+{{ record.byProject.length - 3 }}</span>
              </div>
            </template>
          </a-table>
        </template>

        <!-- Per Work Item 视图 -->
        <template v-else-if="activeView === 'work_item'">
          <a-table
            :columns="workItemColumns"
            :data="tableData.workItems || []"
            :pagination="false"
            :bordered="{ wrapper: false, cell: false }"
            size="small"
            row-key="entryId"
          >
            <template #minutes="{ record }">
              <span class="time-value">{{ formatDuration(record.minutes) }}</span>
            </template>
            <template #issueKey="{ record }">
              <span class="issue-key">{{ record.issueKey }}</span>
            </template>
          </a-table>
        </template>

        <!-- 分页 -->
        <div class="table-pagination">
          <a-pagination
            :total="Number(tableData.totalCount)"
            :current="tablePage"
            :page-size="tablePageSize"
            show-total
            show-page-size
            :page-size-options="[20, 50, 100]"
            @change="onTablePageChange"
            @page-size-change="onTablePageSizeChange"
          />
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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
import {
  IconDownload,
  IconBarChart,
  IconList,
  IconUser,
  IconClockCircle
} from '@arco-design/web-vue/es/icon'
import { reportStatisticsApi } from '@/api/reportStatistics'
import { EmptyState } from '@/components/base'
import { readChartThemeColors, CHART_PALETTE, SERIES_ACCENT } from '@/utils/chartColors'
import type { TimeReportData, TimeReportGroupedData } from '@/api/reportStatistics'
import type { ProjectVO } from '@/api/types'

use([CanvasRenderer, PieChart, BarChart, LineChart, TooltipComponent, GridComponent, LegendComponent, ToolboxComponent])

const props = defineProps<{
  projects: ProjectVO[]
}>()

// ─── 视图状态 ─────────────────────────────────────
const activeView = ref<'charts' | 'issue' | 'user' | 'work_item'>('charts')

// ─── 筛选状态 ─────────────────────────────────────
const loading = ref(false)
const tableLoading = ref(false)
const chartData = ref<TimeReportData | null>(null)
const tableData = ref<TimeReportGroupedData | null>(null)
const selectedProjectId = ref<string | undefined>(undefined)
const dateRange = ref<string[] | undefined>(undefined)

// ─── 分页状态 ─────────────────────────────────────
const tablePage = ref(1)
const tablePageSize = ref(50)

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

// ─── 表格列定义 ─────────────────────────────────────

const issueColumns = [
  { title: '工单编号', dataIndex: 'issueKey', slotName: 'issueKey', width: 110 },
  { title: '标题', dataIndex: 'title', slotName: 'title', ellipsis: true },
  { title: '项目', dataIndex: 'projectName', width: 160, ellipsis: true },
  { title: '状态', dataIndex: 'statusName', width: 110 },
  { title: '工时记录数', dataIndex: 'entryCount', width: 100, align: 'right' as const },
  { title: '总工时', dataIndex: 'totalMinutes', slotName: 'totalMinutes', width: 100, align: 'right' as const }
]

const userColumns = [
  { title: '用户', dataIndex: 'userName', slotName: 'userName', width: 160 },
  { title: '总工时', dataIndex: 'totalMinutes', slotName: 'totalMinutes', width: 110, align: 'right' as const },
  { title: '项目分布', dataIndex: 'byProject', slotName: 'byProject' }
]

const workItemColumns = [
  { title: '工作日期', dataIndex: 'workDate', width: 110 },
  { title: '用户', dataIndex: 'userName', width: 120 },
  { title: '工单编号', dataIndex: 'issueKey', slotName: 'issueKey', width: 110 },
  { title: '工单标题', dataIndex: 'issueTitle', ellipsis: true },
  { title: '项目', dataIndex: 'projectName', width: 160, ellipsis: true },
  { title: '工作类型', dataIndex: 'workType', width: 110 },
  { title: '工时', dataIndex: 'minutes', slotName: 'minutes', width: 90, align: 'right' as const },
  { title: '描述', dataIndex: 'description', ellipsis: true }
]

// ─── 主题色 ─────────────────────────────────────

const chartColors = computed(() => readChartThemeColors())

const palette = CHART_PALETTE

// ─── 图表 Options ─────────────────────────────

const trendChartOption = computed(() => {
  if (!chartData.value) return {}
  const { trendDates, trendMinutes } = chartData.value
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
      data: trendDates.map((d: string) => d.substring(5)),
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
      itemStyle: { color: SERIES_ACCENT, borderRadius: [3, 3, 0, 0] }
    }]
  }
})

const userChartOption = computed(() => {
  if (!chartData.value) return {}
  const items = chartData.value.byUser
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
      data: items.map((item: any, idx: number) => ({
        name: item.name,
        value: item.minutes,
        itemStyle: { color: palette[idx % palette.length] }
      }))
    }]
  }
})

const projectChartOption = computed(() => {
  if (!chartData.value) return {}
  const items = chartData.value.byProject
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
      data: items.map((i: any) => i.name),
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 11, width: 90, overflow: 'truncate' },
      axisTick: { show: false }
    },
    series: [{
      type: 'bar',
      barWidth: '55%',
      data: items.map((item: any, idx: number) => ({
        value: item.minutes,
        itemStyle: { color: palette[idx % palette.length], borderRadius: [0, 3, 3, 0] }
      }))
    }]
  }
})

const workTypeChartOption = computed(() => {
  if (!chartData.value) return {}
  const items = chartData.value.byWorkType
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
      data: items.map((item: any, idx: number) => ({
        name: item.name,
        value: item.minutes,
        itemStyle: { color: palette[idx % palette.length] }
      }))
    }]
  }
})

// ─── 工具方法 ─────────────────────────────

function formatDuration(minutes: number): string {
  if (!minutes || minutes <= 0) return '0m'
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
  if (!chartData.value || chartData.value.totalMinutes === 0) return '0%'
  return Math.max(4, (minutes / chartData.value.totalMinutes) * 100) + '%'
}

function formatDate(val: any): string {
  if (typeof val === 'string') return val.substring(0, 10)
  if (val instanceof Date) return val.toISOString().substring(0, 10)
  return ''
}

function buildParams() {
  const params: any = {}
  if (selectedProjectId.value) params.projectId = selectedProjectId.value
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = formatDate(dateRange.value[0])
    params.endDate = formatDate(dateRange.value[1])
  }
  return params
}

// ─── 数据加载 ─────────────────────────────

async function loadChartData() {
  loading.value = true
  chartData.value = null
  try {
    const res = await reportStatisticsApi.timeReport(buildParams())
    chartData.value = res.data
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载时间报表失败')
  } finally {
    loading.value = false
  }
}

async function loadTableData() {
  if (activeView.value === 'charts') return
  tableLoading.value = true
  tableData.value = null
  try {
    const res = await reportStatisticsApi.timeReportGrouped({
      ...buildParams(),
      viewType: activeView.value as 'issue' | 'user' | 'work_item',
      page: tablePage.value,
      pageSize: tablePageSize.value
    })
    tableData.value = res.data
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载时间报表失败')
  } finally {
    tableLoading.value = false
  }
}

function onViewChange() {
  tablePage.value = 1
  if (activeView.value === 'charts') {
    loadChartData()
  } else {
    loadTableData()
  }
}

function onFilterChange() {
  tablePage.value = 1
  if (activeView.value === 'charts') {
    loadChartData()
  } else {
    loadTableData()
  }
}

function onTablePageChange(page: number) {
  tablePage.value = page
  loadTableData()
}

function onTablePageSizeChange(pageSize: number) {
  tablePageSize.value = pageSize
  tablePage.value = 1
  loadTableData()
}

// ─── 导出 CSV ─────────────────────────────

function exportCSV() {
  if (!chartData.value) return
  const data = chartData.value
  const lines: string[] = []
  const BOM = '\uFEFF'

  lines.push('=== 时间报表 ===')
  lines.push(`总工时,${formatDuration(data.totalMinutes)}`)
  lines.push('')

  lines.push('=== 按人员 ===')
  lines.push('人员,工时(分钟),工时,占比')
  data.byUser.forEach((item: any) => {
    lines.push(`${item.name},${item.minutes},${formatDuration(item.minutes)},${item.percentage}%`)
  })
  lines.push('')

  lines.push('=== 按项目 ===')
  lines.push('项目,工时(分钟),工时,占比')
  data.byProject.forEach((item: any) => {
    lines.push(`${item.name},${item.minutes},${formatDuration(item.minutes)},${item.percentage}%`)
  })
  lines.push('')

  lines.push('=== 按工作类型 ===')
  lines.push('工作类型,工时(分钟),工时,占比')
  data.byWorkType.forEach((item: any) => {
    lines.push(`${item.name},${item.minutes},${formatDuration(item.minutes)},${item.percentage}%`)
  })
  lines.push('')

  lines.push('=== 每日趋势 ===')
  lines.push('日期,工时(分钟),工时')
  data.trendDates.forEach((date: string, idx: number) => {
    lines.push(`${date},${data.trendMinutes[idx]},${formatDuration(data.trendMinutes[idx])}`)
  })
  lines.push('')

  if (data.crossProjectUser.length > 0) {
    lines.push('=== 项目×人员明细 ===')
    lines.push('项目,人员,工时(分钟),工时')
    data.crossProjectUser.forEach((item: any) => {
      lines.push(`${item.projectName},${item.userName},${item.minutes},${formatDuration(item.minutes)}`)
    })
  }

  downloadCSV(lines.join('\n'), BOM, 'TrackFlow_时间报表_图表')
}

function exportTableCSV() {
  if (!tableData.value) return
  const data = tableData.value
  const lines: string[] = []
  const BOM = '\uFEFF'
  let filename = 'TrackFlow_时间报表'

  if (activeView.value === 'issue') {
    filename += '_按工单'
    lines.push('工单编号,标题,项目,状态,工时记录数,总工时(分钟),总工时')
    ;(data.issueGroups || []).forEach(item => {
      lines.push(`${item.issueKey},"${item.title}","${item.projectName}","${item.statusName}",${item.entryCount},${item.totalMinutes},${formatDuration(item.totalMinutes)}`)
    })
  } else if (activeView.value === 'user') {
    filename += '_按用户'
    lines.push('用户,总工时(分钟),总工时,项目分布')
    ;(data.userGroups || []).forEach(item => {
      const projectSummary = item.byProject.map(p => `${p.projectName}:${formatDuration(p.minutes)}`).join(';')
      lines.push(`"${item.userName}",${item.totalMinutes},${formatDuration(item.totalMinutes)},"${projectSummary}"`)
    })
  } else {
    filename += '_工时明细'
    lines.push('工作日期,用户,工单编号,工单标题,项目,工作类型,工时(分钟),工时,描述')
    ;(data.workItems || []).forEach(item => {
      lines.push(`${item.workDate},"${item.userName}","${item.issueKey}","${item.issueTitle}","${item.projectName}","${item.workType}",${item.minutes},${formatDuration(item.minutes)},"${item.description || ''}"`)
    })
  }

  downloadCSV(lines.join('\n'), BOM, filename)
}

function downloadCSV(content: string, bom: string, name: string) {
  const csvContent = bom + content
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${name}_${new Date().toISOString().substring(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  Message.success('CSV 导出成功')
}

onMounted(() => {
  loadChartData()
})

// 公开方法供父组件调用
defineExpose({ loadData: loadChartData })
</script>

<style scoped>
.time-report {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.view-switcher {
  flex-shrink: 0;
}

.report-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.report-loading,
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

/* 表格视图 */
.table-overview {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 12px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 6px;
  font-size: 13px;
}

.table-overview-label {
  color: var(--tf-text-secondary);
}

.table-overview-value {
  color: var(--tf-text-primary);
  font-weight: 600;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}

.issue-key {
  font-family: 'SFMono-Regular', 'Consolas', monospace;
  font-size: 12px;
  color: var(--tf-accent);
  font-weight: 500;
}

.issue-title {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.user-name {
  font-weight: 500;
  color: var(--tf-text-primary);
}

.time-value {
  font-weight: 600;
  color: var(--tf-text-primary);
}

.project-chips {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.more-tag {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

@media (max-width: 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .report-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
