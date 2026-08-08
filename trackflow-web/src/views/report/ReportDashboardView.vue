<template>
  <div class="report-dashboard">
    <!-- 概览内容 -->
    <div class="overview-content">
      <!-- 仪表盘筛选栏 -->
      <div class="dashboard-filters">
        <a-select
          v-model="selectedProjectId"
          placeholder="选择项目"
          size="small"
          style="width: 180px"
          @change="handleProjectChange"
        >
          <a-option value="__all__">全部项目</a-option>
          <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
        </a-select>
        <a-select
          v-if="selectedProjectId !== '__all__'"
          v-model="selectedSprintId"
          placeholder="全部 Sprint"
          allow-clear
          size="small"
          style="width: 200px"
          @change="loadDashboard"
        >
          <a-option v-for="s in sprints" :key="s.id" :value="s.id">
            {{ s.name }}
            <span v-if="s.status === 'active'" class="sprint-active-badge">进行中</span>
          </a-option>
        </a-select>
        <a-range-picker
          v-model="dateRange"
          size="small"
          style="width: 240px"
          :shortcuts="dateShortcuts"
          @change="loadDashboard"
        />
        <a-dropdown v-if="dashboardData" trigger="click">
          <a-button size="small" type="outline">
            <template #icon><icon-download /></template>
            导出
          </a-button>
          <template #content>
            <a-doption @click="exportCSV">
              <template #icon><icon-file /></template>
              导出 CSV（统计数据）
            </a-doption>
            <a-doption @click="printReport">
              <template #icon><icon-printer /></template>
              打印报表
            </a-doption>
          </template>
        </a-dropdown>
      </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="dashboard-loading">
      <div class="overview-skeleton">
        <a-skeleton :animation="true" v-for="i in 4" :key="i">
          <a-skeleton-line :rows="2" :widths="['60%', '40%']" />
        </a-skeleton>
      </div>
      <div class="chart-skeleton">
        <a-skeleton :animation="true" v-for="i in 4" :key="i">
          <a-skeleton-shape shape="square" :style="{ width: '100%', height: '240px' }" />
        </a-skeleton>
      </div>
    </div>

    <!-- 空状态（数据为空） -->
    <div v-else-if="!dashboardData" class="dashboard-empty">
      <div class="empty-icon">📊</div>
      <h3 class="empty-title">暂无报表数据</h3>
      <p class="empty-desc">当前选择范围没有工单数据。请尝试切换项目或日期范围。</p>
    </div>

    <!-- 仪表盘主体 -->
    <template v-else-if="dashboardData">
      <!-- 概览卡片 -->
      <div class="overview-cards">
        <a-tooltip content="选定范围内的所有工单" position="bottom">
          <div class="stat-card stat-card-clickable" @click="drillDownOverview('total')">
            <div class="stat-value">{{ dashboardData.overview.total }}</div>
            <div class="stat-label">工单总数</div>
          </div>
        </a-tooltip>
        <a-tooltip position="bottom">
          <template #content>
            <div class="stat-tooltip">
              <div class="stat-tooltip-title">包含所有未关闭状态的工单：</div>
              <div class="stat-tooltip-list">待处理、进行中、代码审查、测试中、重新打开、待办等</div>
            </div>
          </template>
          <div class="stat-card stat-open stat-card-clickable" @click="drillDownOverview('open')">
            <div class="stat-value">{{ dashboardData.overview.open }}</div>
            <div class="stat-label">
              未完成
              <icon-info-circle class="stat-info-icon" />
            </div>
          </div>
        </a-tooltip>
        <a-tooltip position="bottom">
          <template #content>
            <div class="stat-tooltip">
              <div class="stat-tooltip-title">包含所有已关闭状态的工单：</div>
              <div class="stat-tooltip-list">已完成、已上线、已解决、已关闭、已取消</div>
            </div>
          </template>
          <div class="stat-card stat-done stat-card-clickable" @click="drillDownOverview('closed')">
            <div class="stat-value">{{ dashboardData.overview.closed }}</div>
            <div class="stat-label">
              已关闭
              <icon-info-circle class="stat-info-icon" />
            </div>
          </div>
        </a-tooltip>
        <a-tooltip content="已关闭工单数 ÷ 工单总数" position="bottom">
          <div class="stat-card stat-rate">
            <div class="stat-value">{{ dashboardData.overview.completionRate }}%</div>
            <div class="stat-label">完成率</div>
          </div>
        </a-tooltip>
        <a-tooltip content="截止日期已过但未关闭的工单" position="bottom" v-if="dashboardData.overview.overdue > 0">
          <div class="stat-card stat-overdue stat-card-clickable" @click="drillDownOverview('overdue')">
            <div class="stat-value">{{ dashboardData.overview.overdue }}</div>
            <div class="stat-label">已逾期</div>
          </div>
        </a-tooltip>
      </div>

      <!-- 图表网格 -->
      <div class="chart-grid">
        <!-- 工单状态分布 — 环形图 -->
        <div class="chart-card">
          <div class="chart-card-header">
            <h3 class="chart-title">状态分布</h3>
            <span class="chart-subtitle">各状态工单占比</span>
            <a-button
              class="chart-download-btn"
              type="text"
              size="mini"
              title="保存为图片"
              @click="downloadChart(statusChartRef, '状态分布')"
            >
              <template #icon><icon-download /></template>
            </a-button>
          </div>
          <div class="chart-body">
            <v-chart ref="statusChartRef" :option="statusChartOption" autoresize class="chart-instance chart-clickable" @click="handleStatusChartClick" />
          </div>
        </div>

        <!-- 优先级分布 — 柱状图 -->
        <div class="chart-card">
          <div class="chart-card-header">
            <h3 class="chart-title">优先级分布</h3>
            <span class="chart-subtitle">各优先级工单数量</span>
            <a-button
              class="chart-download-btn"
              type="text"
              size="mini"
              title="保存为图片"
              @click="downloadChart(priorityChartRef, '优先级分布')"
            >
              <template #icon><icon-download /></template>
            </a-button>
          </div>
          <div class="chart-body">
            <v-chart ref="priorityChartRef" :option="priorityChartOption" autoresize class="chart-instance chart-clickable" @click="handlePriorityChartClick" />
          </div>
        </div>

        <!-- 工单类型分布 — 饼图 -->
        <div class="chart-card">
          <div class="chart-card-header">
            <h3 class="chart-title">类型分布</h3>
            <span class="chart-subtitle">Bug / Task / Feature 占比</span>
            <a-button
              class="chart-download-btn"
              type="text"
              size="mini"
              title="保存为图片"
              @click="downloadChart(typeChartRef, '类型分布')"
            >
              <template #icon><icon-download /></template>
            </a-button>
          </div>
          <div class="chart-body">
            <v-chart ref="typeChartRef" :option="typeChartOption" autoresize class="chart-instance chart-clickable" @click="handleTypeChartClick" />
          </div>
        </div>

        <!-- 团队工作负载 — 横向柱状图 -->
        <div class="chart-card">
          <div class="chart-card-header">
            <h3 class="chart-title">团队负载</h3>
            <span class="chart-subtitle">按负责人统计工单数</span>
            <a-button
              class="chart-download-btn"
              type="text"
              size="mini"
              title="保存为图片"
              @click="downloadChart(workloadChartRef, '团队负载')"
            >
              <template #icon><icon-download /></template>
            </a-button>
          </div>
          <div class="chart-body">
            <v-chart ref="workloadChartRef" :option="workloadChartOption" autoresize class="chart-instance chart-clickable" @click="handleWorkloadChartClick" />
          </div>
        </div>

        <!-- 工单趋势 — 折线图（占两列） -->
        <div class="chart-card chart-card-wide">
          <div class="chart-card-header">
            <h3 class="chart-title">工单趋势</h3>
            <span class="chart-subtitle">每日新建 / 关闭工单数</span>
            <a-button
              class="chart-download-btn"
              type="text"
              size="mini"
              title="保存为图片"
              @click="downloadChart(trendChartRef, '工单趋势')"
            >
              <template #icon><icon-download /></template>
            </a-button>
          </div>
          <div class="chart-body">
            <v-chart ref="trendChartRef" :option="trendChartOption" autoresize class="chart-instance" />
          </div>
        </div>

        <!-- Sprint 燃尽图（仅选择了 Sprint 时显示） -->
        <div v-if="dashboardData.burndown" class="chart-card chart-card-wide">
          <div class="chart-card-header">
            <h3 class="chart-title">Sprint 燃尽图</h3>
            <span class="chart-subtitle">{{ dashboardData.burndown.sprintName }} — 理想 vs 实际进度</span>
            <a-button
              class="chart-download-btn"
              type="text"
              size="mini"
              title="保存为图片"
              @click="downloadChart(burndownChartRef, 'Sprint燃尽图')"
            >
              <template #icon><icon-download /></template>
            </a-button>
          </div>
          <div class="chart-body">
            <v-chart ref="burndownChartRef" :option="burndownChartOption" autoresize class="chart-instance" />
          </div>
        </div>

        <!-- 项目对比（仅"全部项目"模式且有多个项目时显示） -->
        <div v-if="dashboardData.projectComparison && dashboardData.projectComparison.items.length > 1" class="chart-card chart-card-wide">
          <div class="chart-card-header">
            <h3 class="chart-title">项目对比</h3>
            <span class="chart-subtitle">各项目工单数量与完成率对比</span>
            <a-button
              class="chart-download-btn"
              type="text"
              size="mini"
              title="保存为图片"
              @click="downloadChart(projectComparisonChartRef, '项目对比')"
            >
              <template #icon><icon-download /></template>
            </a-button>
          </div>
          <div class="chart-body">
            <v-chart ref="projectComparisonChartRef" :option="projectComparisonChartOption" autoresize class="chart-instance" />
          </div>
        </div>

        <!-- 累积流图（Cumulative Flow Diagram） -->
        <div v-if="dashboardData.cumulativeFlow && dashboardData.cumulativeFlow.series.length > 0" class="chart-card chart-card-wide">
          <div class="chart-card-header">
            <h3 class="chart-title">累积流图</h3>
            <span class="chart-subtitle">各状态工单数量随时间变化（面积越宽 = 积压越多）</span>
            <a-button
              class="chart-download-btn"
              type="text"
              size="mini"
              title="保存为图片"
              @click="downloadChart(cumulativeFlowChartRef, '累积流图')"
            >
              <template #icon><icon-download /></template>
            </a-button>
          </div>
          <div class="chart-body">
            <v-chart ref="cumulativeFlowChartRef" :option="cumulativeFlowChartOption" autoresize class="chart-instance" />
          </div>
        </div>

        <!-- 解决时间分析 -->
        <div v-if="dashboardData.resolutionTime && dashboardData.resolutionTime.dates.length > 0" class="chart-card chart-card-wide">
          <div class="chart-card-header">
            <div class="chart-header-left">
              <h3 class="chart-title">解决时间分析</h3>
              <span class="chart-subtitle">工单从创建到关闭的耗时趋势</span>
            </div>
            <div class="chart-header-right">
              <a-select
                v-model="resolutionTimeGroupBy"
                placeholder="分组查看"
                allow-clear
                size="mini"
                style="width: 100px"
                @change="loadResolutionTimeGrouped"
              >
                <a-option value="type">按类型</a-option>
                <a-option value="priority">按优先级</a-option>
                <a-option value="assignee">按负责人</a-option>
              </a-select>
              <a-button
                class="chart-download-btn"
                type="text"
                size="mini"
                title="保存为图片"
                @click="downloadChart(resolutionTimeChartRef, '解决时间分析')"
              >
                <template #icon><icon-download /></template>
              </a-button>
            </div>
          </div>
          <div class="chart-body">
            <v-chart ref="resolutionTimeChartRef" :option="resolutionTimeChartOption" autoresize class="chart-instance" />
          </div>
          <!-- 分组明细表格 -->
          <div v-if="resolutionTimeGroupDetails.length > 0" class="resolution-group-details">
            <div class="group-detail-header">
              <span class="group-detail-title">分组明细</span>
            </div>
            <div class="group-detail-table">
              <div class="group-detail-row group-detail-row-header">
                <span class="gd-name">分组</span>
                <span class="gd-value">平均耗时</span>
                <span class="gd-value">中位耗时</span>
                <span class="gd-value">工单数</span>
              </div>
              <div v-for="item in resolutionTimeGroupDetails" :key="item.name" class="group-detail-row">
                <span class="gd-name">{{ item.name }}</span>
                <span class="gd-value">{{ formatHours(item.avgHours) }}</span>
                <span class="gd-value">{{ formatHours(item.medianHours) }}</span>
                <span class="gd-value">{{ item.count }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
    </div><!-- end overview-content -->
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { IconDownload, IconFile, IconPrinter, IconInfoCircle } from '@arco-design/web-vue/es/icon'
import { reportStatisticsApi } from '@/api/reportStatistics'
import { projectApi, sprintApi } from '@/api'
import type { DashboardData, ProjectComparisonData, CumulativeFlowData, ResolutionTimeData } from '@/api/reportStatistics'
import type { ProjectVO } from '@/api/types'
import { localizeStatusName, priorityLabelMap } from '@/utils/fieldLabels'
import { useChartColors, SERIES_ACCENT, SERIES_SUCCESS, SERIES_DANGER, SERIES_TERTIARY, areaGradient, getChartDownloadBgColor } from '@/utils/chartColors'

// 注册 ECharts 组件
use([CanvasRenderer, PieChart, BarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const router = useRouter()

// ─── 状态 ─────────────────────────────────────────────

const loading = ref(false)
const projects = ref<ProjectVO[]>([])
const sprints = ref<{ id: string; name: string; status: string }[]>([])
const selectedProjectId = ref<string>('__all__')
const selectedSprintId = ref<string | undefined>(undefined)
const dateRange = ref<string[] | undefined>(undefined)
const dashboardData = ref<DashboardData | null>(null)

const dateShortcuts = [
  { label: '近 7 天', value: () => [daysAgo(6), today()] },
  { label: '近 14 天', value: () => [daysAgo(13), today()] },
  { label: '近 30 天', value: () => [daysAgo(29), today()] },
  { label: '近 90 天', value: () => [daysAgo(89), today()] }
]

function today() { return new Date() }
function daysAgo(n: number) { const d = new Date(); d.setDate(d.getDate() - n); return d }

// ─── 主题色（动态读取 CSS 变量，适配亮色/暗色/护眼主题） ─────────

const { chartColors } = useChartColors()

const chartBgColor = 'transparent'

// ─── 图表下载（替代 ECharts 内置 toolbox） ────────────────────

/** 图表模板引用 */
const statusChartRef = ref<InstanceType<typeof VChart> | null>(null)
const priorityChartRef = ref<InstanceType<typeof VChart> | null>(null)
const typeChartRef = ref<InstanceType<typeof VChart> | null>(null)
const workloadChartRef = ref<InstanceType<typeof VChart> | null>(null)
const trendChartRef = ref<InstanceType<typeof VChart> | null>(null)
const burndownChartRef = ref<InstanceType<typeof VChart> | null>(null)
const projectComparisonChartRef = ref<InstanceType<typeof VChart> | null>(null)
const cumulativeFlowChartRef = ref<InstanceType<typeof VChart> | null>(null)
const resolutionTimeChartRef = ref<InstanceType<typeof VChart> | null>(null)

/** 下载图表为 PNG */
function downloadChart(chartComp: InstanceType<typeof VChart> | null, title: string) {
  if (!chartComp) {
    Message.warning('图表尚未加载完成')
    return
  }
  // vue-echarts proxies ECharts methods (getDataURL) directly on the component instance
  const url = chartComp.getDataURL({
    type: 'png',
    pixelRatio: 2,
    backgroundColor: getChartDownloadBgColor()
  })
  const a = document.createElement('a')
  a.href = url
  a.download = `TrackFlow_${title}_${new Date().toISOString().substring(0, 10)}.png`
  a.click()
}

// ─── 图表 Options ─────────────────────────────────────────

const statusChartOption = computed(() => {
  if (!dashboardData.value) return {}
  const items = dashboardData.value.statusDistribution.items
  const c = chartColors.value
  return {
    backgroundColor: chartBgColor,
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText }
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
      radius: ['42%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 4, borderColor: c.cardBorder, borderWidth: 2 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 13, fontWeight: 500, color: c.tooltipText },
        itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.3)' }
      },
      data: items.map(item => ({
        name: localizeStatusName(item.name),
        value: item.value,
        itemStyle: { color: item.color }
      }))
    }]
  }
})

const priorityChartOption = computed(() => {
  if (!dashboardData.value) return {}
  const { labels, data, colors } = dashboardData.value.priorityDistribution
  const c = chartColors.value
  return {
    backgroundColor: chartBgColor,
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText }
    },
    grid: { left: 40, right: 20, top: 16, bottom: 30 },
    xAxis: {
      type: 'category',
      data: labels.map(l => priorityLabelMap[l] || l),
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
      data: data.map((val, idx) => ({
        value: val,
        itemStyle: { color: colors[idx], borderRadius: [3, 3, 0, 0] }
      }))
    }]
  }
})

const typeChartOption = computed(() => {
  if (!dashboardData.value) return {}
  const items = dashboardData.value.typeDistribution.items
  const c = chartColors.value
  return {
    backgroundColor: chartBgColor,
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText }
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
      radius: ['0%', '70%'],
      center: ['35%', '50%'],
      roseType: 'radius',
      itemStyle: { borderRadius: 4, borderColor: c.cardBorder, borderWidth: 2 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 13, fontWeight: 500, color: c.tooltipText }
      },
      data: items.map(item => ({
        name: item.name,
        value: item.value,
        itemStyle: { color: item.color }
      }))
    }]
  }
})

const workloadChartOption = computed(() => {
  if (!dashboardData.value) return {}
  const items = dashboardData.value.workload.items
  const c = chartColors.value
  return {
    backgroundColor: chartBgColor,
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText },
      formatter: (params: any) => {
        const item = items[params[0]?.dataIndex]
        if (!item) return ''
        return `${item.name}<br/>总计: ${item.value}<br/>已完成: ${item.done}<br/>进行中: ${item.inProgress}`
      }
    },
    grid: { left: 80, right: 30, top: 8, bottom: 20 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: items.map(i => i.name),
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 11, width: 70, overflow: 'truncate' },
      axisTick: { show: false }
    },
    series: [
      {
        name: '已完成',
        type: 'bar',
        stack: 'total',
        barWidth: '60%',
        data: items.map(i => i.done),
        itemStyle: { color: SERIES_SUCCESS, borderRadius: [0, 0, 0, 0] }
      },
      {
        name: '进行中',
        type: 'bar',
        stack: 'total',
        barWidth: '60%',
        data: items.map(i => i.inProgress),
        itemStyle: { color: SERIES_ACCENT, borderRadius: [0, 3, 3, 0] }
      }
    ]
  }
})

const trendChartOption = computed(() => {
  if (!dashboardData.value) return {}
  const { dates, created, resolved } = dashboardData.value.trend
  const shortDates = dates.map(d => d.substring(5))
  const c = chartColors.value
  return {
    backgroundColor: chartBgColor,
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText }
    },
    legend: {
      data: ['新建', '关闭'],
      right: 20,
      top: 0,
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 14,
      itemHeight: 3
    },
    grid: { left: 40, right: 20, top: 32, bottom: 30 },
    xAxis: {
      type: 'category',
      data: shortDates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 10, interval: 'auto' },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    series: [
      {
        name: '新建',
        type: 'line',
        data: created,
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { width: 2, color: SERIES_ACCENT },
        itemStyle: { color: SERIES_ACCENT },
        areaStyle: { color: areaGradient(SERIES_ACCENT, 0.2) }
      },
      {
        name: '关闭',
        type: 'line',
        data: resolved,
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { width: 2, color: SERIES_SUCCESS },
        itemStyle: { color: SERIES_SUCCESS },
        areaStyle: { color: areaGradient(SERIES_SUCCESS, 0.15) }
      }
    ]
  }
})

const burndownChartOption = computed(() => {
  if (!dashboardData.value?.burndown) return {}
  const { dates, ideal, actual, sprintName } = dashboardData.value.burndown
  const shortDates = dates.map(d => d.substring(5))
  const c = chartColors.value
  return {
    backgroundColor: chartBgColor,
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText }
    },
    legend: {
      data: ['理想进度', '实际剩余'],
      right: 20,
      top: 0,
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 14,
      itemHeight: 3
    },
    grid: { left: 40, right: 20, top: 32, bottom: 30 },
    xAxis: {
      type: 'category',
      data: shortDates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 10 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    series: [
      {
        name: '理想进度',
        type: 'line',
        data: ideal,
        lineStyle: { width: 2, color: SERIES_TERTIARY, type: 'dashed' },
        itemStyle: { color: SERIES_TERTIARY },
        symbol: 'none'
      },
      {
        name: '实际剩余',
        type: 'line',
        data: actual,
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2.5, color: SERIES_DANGER },
        itemStyle: { color: SERIES_DANGER },
        areaStyle: { color: areaGradient(SERIES_DANGER, 0.12) }
      }
    ]
  }
})

const projectComparisonChartOption = computed(() => {
  if (!dashboardData.value?.projectComparison) return {}
  const items = dashboardData.value.projectComparison.items
  const c = chartColors.value
  const projectNames = items.map(i => `${i.name} (${i.key})`)
  return {
    backgroundColor: chartBgColor,
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText },
      formatter: (params: any) => {
        const idx = params[0]?.dataIndex
        if (idx == null) return ''
        const item = items[idx]
        return `<strong>${item.name}</strong> (${item.key})<br/>` +
          `工单总数: ${item.total}<br/>` +
          `已完成: ${item.closed}<br/>` +
          `进行中: ${item.open}<br/>` +
          `完成率: ${item.completionRate}%<br/>` +
          `已逾期: ${item.overdue}`
      }
    },
    legend: {
      data: ['已完成', '进行中', '已逾期'],
      right: 20,
      top: 0,
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 14,
      itemHeight: 10
    },
    grid: { left: 100, right: 30, top: 36, bottom: 20 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: projectNames,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 11, width: 90, overflow: 'truncate' },
      axisTick: { show: false }
    },
    series: [
      {
        name: '已完成',
        type: 'bar',
        stack: 'total',
        barWidth: '55%',
        data: items.map(i => i.closed),
        itemStyle: { color: '#3fb950', borderRadius: [0, 0, 0, 0] }
      },
      {
        name: '进行中',
        type: 'bar',
        stack: 'total',
        barWidth: '55%',
        data: items.map(i => i.open - i.overdue),
        itemStyle: { color: '#58a6ff' }
      },
      {
        name: '已逾期',
        type: 'bar',
        stack: 'total',
        barWidth: '55%',
        data: items.map(i => i.overdue),
        itemStyle: { color: '#f85149', borderRadius: [0, 3, 3, 0] }
      }
    ]
  }
})

// ─── 数据加载 ─────────────────────────────────────────

// ─── 图表下钻（点击跳转到工单列表） ─────────────────────

/** 构建跳转到工单列表的查询参数 */
function buildDrillDownQuery(params: Record<string, string>): Record<string, string> {
  const query: Record<string, string> = { ...params }
  // 如果选择了具体项目，带上 projectId
  if (selectedProjectId.value && selectedProjectId.value !== '__all__') {
    query.projectId = selectedProjectId.value
  }
  return query
}

/** 跳转到工单列表 */
function navigateToDrillDown(params: Record<string, string>) {
  router.push({ path: '/issues', query: buildDrillDownQuery(params) })
}

/** 概览卡片点击下钻 */
function drillDownOverview(type: 'total' | 'open' | 'closed' | 'overdue') {
  switch (type) {
    case 'total':
      navigateToDrillDown({ label: '全部工单' })
      break
    case 'open':
      navigateToDrillDown({ statusCategory: 'in_progress', label: '进行中' })
      break
    case 'closed':
      navigateToDrillDown({ statusCategory: 'done', label: '已完成' })
      break
    case 'overdue':
      navigateToDrillDown({ overdue: 'true', label: '已逾期' })
      break
  }
}

/** 状态分布图点击 — 按具体状态筛选 */
function handleStatusChartClick(params: any) {
  if (!params || !params.data) return
  const item = dashboardData.value?.statusDistribution.items.find(
    i => localizeStatusName(i.name) === params.name || i.name === params.name
  )
  if (item) {
    // Pass statusName which will be resolved to status ID in IssueListView
    navigateToDrillDown({
      statusName: item.name,
      label: localizeStatusName(item.name)
    })
  }
}

/** 优先级分布图点击 — 按优先级筛选 */
function handlePriorityChartClick(params: any) {
  if (!params || params.dataIndex == null) return
  const labels = dashboardData.value?.priorityDistribution.labels
  if (!labels) return
  const priorityName = labels[params.dataIndex]
  if (priorityName) {
    navigateToDrillDown({
      priority: priorityName,
      label: priorityLabelMap[priorityName] || priorityName
    })
  }
}

/** 类型分布图点击 — 按工单类型筛选 */
function handleTypeChartClick(params: any) {
  if (!params || !params.data) return
  const typeName = params.data.name || params.name
  if (typeName) {
    navigateToDrillDown({
      issueType: typeName,
      label: typeName
    })
  }
}

/** 团队负载图点击 — 按负责人筛选 */
function handleWorkloadChartClick(params: any) {
  if (!params || params.dataIndex == null) return
  const items = dashboardData.value?.workload.items
  if (!items) return
  const item = items[params.dataIndex]
  if (item && item.name) {
    navigateToDrillDown({
      assigneeName: item.name,
      label: item.name
    })
  }
}

// Resolution time group-by state
const resolutionTimeGroupBy = ref<string | undefined>(undefined)
const resolutionTimeGroupDetails = ref<{ name: string; avgHours: number; medianHours: number; count: number }[]>([])

// ─── 累积流图 Option ─────────────────────────────────────

const cumulativeFlowChartOption = computed(() => {
  if (!dashboardData.value?.cumulativeFlow) return {}
  const { dates, series } = dashboardData.value.cumulativeFlow
  if (!series.length) return {}
  const shortDates = dates.map(d => d.substring(5))
  const c = chartColors.value
  return {
    backgroundColor: chartBgColor,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText, fontSize: 12 },
      formatter: (params: any) => {
        if (!params.length) return ''
        let html = `<strong>${params[0].axisValue}</strong><br/>`
        let total = 0
        for (const p of params) {
          total += p.value || 0
          html += `<span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:${p.color};margin-right:4px;"></span>${p.seriesName}: <strong>${p.value}</strong><br/>`
        }
        html += `<br/>总计: <strong>${total}</strong>`
        return html
      }
    },
    legend: {
      data: series.map(s => localizeStatusName(s.name)),
      bottom: 0,
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 12,
      itemHeight: 10,
      type: 'scroll'
    },
    grid: { left: 40, right: 20, top: 16, bottom: 40 },
    xAxis: {
      type: 'category',
      data: shortDates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 10, interval: 'auto' },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    series: series.map(s => ({
      name: localizeStatusName(s.name),
      type: 'line',
      stack: 'total',
      areaStyle: { opacity: 0.7 },
      emphasis: { focus: 'series' },
      symbol: 'none',
      lineStyle: { width: 1, color: s.color },
      itemStyle: { color: s.color },
      data: s.data
    }))
  }
})

// ─── 解决时间分析 Option ─────────────────────────────────

const resolutionTimeChartOption = computed(() => {
  if (!dashboardData.value?.resolutionTime) return {}
  const { dates, avgHours, medianHours, p90Hours, resolvedCount } = dashboardData.value.resolutionTime
  if (!dates.length) return {}
  const shortDates = dates.map(d => d.substring(5))
  const c = chartColors.value
  return {
    backgroundColor: chartBgColor,
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText, fontSize: 12 },
      formatter: (params: any) => {
        if (!params.length) return ''
        let html = `<strong>${params[0].axisValue}</strong><br/>`
        for (const p of params) {
          if (p.value == null) continue
          const unit = p.seriesName === '解决工单数' ? '' : 'h'
          html += `<span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:${p.color};margin-right:4px;"></span>${p.seriesName}: <strong>${p.value}${unit}</strong><br/>`
        }
        return html
      }
    },
    legend: {
      data: ['平均', '中位', 'P90', '解决工单数'],
      right: 20,
      top: 0,
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 14,
      itemHeight: 3
    },
    grid: { left: 50, right: 50, top: 32, bottom: 30 },
    xAxis: {
      type: 'category',
      data: shortDates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 10, interval: 'auto' },
      axisTick: { show: false }
    },
    yAxis: [
      {
        type: 'value',
        name: '小时',
        nameTextStyle: { color: c.textColor, fontSize: 11 },
        axisLine: { show: false },
        axisLabel: { color: c.textColor, fontSize: 11 },
        splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
      },
      {
        type: 'value',
        name: '工单数',
        nameTextStyle: { color: c.textColor, fontSize: 11 },
        axisLine: { show: false },
        axisLabel: { color: c.textColor, fontSize: 11 },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '平均',
        type: 'line',
        yAxisIndex: 0,
        data: avgHours,
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        connectNulls: true,
        lineStyle: { width: 2, color: '#58a6ff' },
        itemStyle: { color: '#58a6ff' }
      },
      {
        name: '中位',
        type: 'line',
        yAxisIndex: 0,
        data: medianHours,
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        connectNulls: true,
        lineStyle: { width: 2, color: '#3fb950' },
        itemStyle: { color: '#3fb950' }
      },
      {
        name: 'P90',
        type: 'line',
        yAxisIndex: 0,
        data: p90Hours,
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        connectNulls: true,
        lineStyle: { width: 2, color: '#f85149', type: 'dashed' },
        itemStyle: { color: '#f85149' }
      },
      {
        name: '解决工单数',
        type: 'bar',
        yAxisIndex: 1,
        data: resolvedCount,
        barWidth: '40%',
        itemStyle: { color: 'rgba(88, 166, 255, 0.2)', borderRadius: [3, 3, 0, 0] }
      }
    ]
  }
})

function formatHours(hours: number): string {
  if (hours < 1) return `${Math.round(hours * 60)}分钟`
  if (hours < 24) return `${hours.toFixed(1)}小时`
  const days = hours / 24
  return `${days.toFixed(1)}天`
}

async function loadResolutionTimeGrouped() {
  if (!selectedProjectId.value || selectedProjectId.value === '__all__') {
    resolutionTimeGroupDetails.value = []
    return
  }
  if (!resolutionTimeGroupBy.value) {
    resolutionTimeGroupDetails.value = []
    // Reload without groupBy
    return
  }
  try {
    const params: any = { projectId: selectedProjectId.value, groupBy: resolutionTimeGroupBy.value }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = formatDate(dateRange.value[0])
      params.endDate = formatDate(dateRange.value[1])
    }
    const res = await reportStatisticsApi.resolutionTime(
      params.projectId, params.startDate, params.endDate, params.groupBy
    )
    resolutionTimeGroupDetails.value = res.data?.groupDetails || []
  } catch {
    resolutionTimeGroupDetails.value = []
  }
}

onMounted(async () => {
  await loadProjects()
  // 默认选择"全部项目"，直接加载聚合数据
  await loadDashboard()
})

onBeforeUnmount(() => {
  // cleanup handled by useChartColors composable
})

watch(selectedProjectId, async () => {
  selectedSprintId.value = undefined
  if (selectedProjectId.value && selectedProjectId.value !== '__all__') {
    await loadSprints()
  } else {
    sprints.value = []
  }
})

async function handleProjectChange() {
  selectedSprintId.value = undefined
  if (selectedProjectId.value && selectedProjectId.value !== '__all__') {
    await loadSprints()
  } else {
    sprints.value = []
  }
  await loadDashboard()
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    projects.value = res.data?.list || []
  } catch {
    // non-critical
  }
}

async function loadSprints() {
  if (!selectedProjectId.value) return
  try {
    const res = await sprintApi.listByProject(selectedProjectId.value)
    sprints.value = (res.data?.list || []).map((s: any) => ({
      id: s.id,
      name: s.name,
      status: s.status
    }))
  } catch {
    sprints.value = []
  }
}

async function loadDashboard() {
  if (!selectedProjectId.value) return
  loading.value = true
  dashboardData.value = null
  try {
    const params: any = {}
    // 只有选择了具体项目才传 projectId，"全部项目"时不传
    if (selectedProjectId.value !== '__all__') {
      params.projectId = selectedProjectId.value
    }
    if (selectedSprintId.value) params.sprintId = selectedSprintId.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = formatDate(dateRange.value[0])
      params.endDate = formatDate(dateRange.value[1])
    }
    // Issue filter - removed (REQ-402)
    const res = await reportStatisticsApi.dashboard(params)
    dashboardData.value = res.data
    // Reset group details on dashboard reload
    resolutionTimeGroupDetails.value = res.data?.resolutionTime?.groupDetails || []
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载报表数据失败')
  } finally {
    loading.value = false
  }
}

function formatDate(val: any): string {
  if (typeof val === 'string') return val.substring(0, 10)
  if (val instanceof Date) return val.toISOString().substring(0, 10)
  return ''
}

// ─── 导出功能 ─────────────────────────────────────────

/** 导出 CSV 统计数据 */
function exportCSV() {
  if (!dashboardData.value) return

  const data = dashboardData.value
  const lines: string[] = []
  const BOM = '\uFEFF' // UTF-8 BOM for Excel compatibility

  // 概览
  lines.push('=== 概览 ===')
  lines.push('指标,数值')
  lines.push(`工单总数,${data.overview.total}`)
  lines.push(`进行中,${data.overview.open}`)
  lines.push(`已完成,${data.overview.closed}`)
  lines.push(`完成率,${data.overview.completionRate}%`)
  lines.push(`未分配,${data.overview.unassigned}`)
  lines.push(`已逾期,${data.overview.overdue}`)
  lines.push('')

  // 状态分布
  lines.push('=== 状态分布 ===')
  lines.push('状态,数量')
  data.statusDistribution.items.forEach(item => {
    lines.push(`${localizeStatusName(item.name)},${item.value}`)
  })
  lines.push('')

  // 优先级分布
  lines.push('=== 优先级分布 ===')
  lines.push('优先级,数量')
  data.priorityDistribution.labels.forEach((label, idx) => {
    lines.push(`${priorityLabelMap[label] || label},${data.priorityDistribution.data[idx]}`)
  })
  lines.push('')

  // 类型分布
  lines.push('=== 类型分布 ===')
  lines.push('类型,数量')
  data.typeDistribution.items.forEach(item => {
    lines.push(`${item.name},${item.value}`)
  })
  lines.push('')

  // 团队负载
  lines.push('=== 团队负载 ===')
  lines.push('负责人,总计,已完成,进行中')
  data.workload.items.forEach(item => {
    lines.push(`${item.name},${item.value},${item.done},${item.inProgress}`)
  })
  lines.push('')

  // 工单趋势
  lines.push('=== 工单趋势 ===')
  lines.push('日期,新建,关闭')
  data.trend.dates.forEach((date, idx) => {
    lines.push(`${date},${data.trend.created[idx]},${data.trend.resolved[idx]}`)
  })
  lines.push('')

  // 燃尽图
  if (data.burndown) {
    lines.push(`=== Sprint 燃尽图 (${data.burndown.sprintName}) ===`)
    lines.push('日期,理想进度,实际剩余')
    data.burndown.dates.forEach((date, idx) => {
      lines.push(`${date},${data.burndown!.ideal[idx]},${data.burndown!.actual[idx]}`)
    })
    lines.push('')
  }

  // 项目对比
  if (data.projectComparison && data.projectComparison.items.length > 1) {
    lines.push('=== 项目对比 ===')
    lines.push('项目名称,项目Key,工单总数,已完成,进行中,完成率,已逾期')
    data.projectComparison.items.forEach(item => {
      lines.push(`${item.name},${item.key},${item.total},${item.closed},${item.open},${item.completionRate}%,${item.overdue}`)
    })
  }

  const csvContent = BOM + lines.join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  const projectName = selectedProjectId.value === '__all__'
    ? '全部项目'
    : projects.value.find(p => p.id === selectedProjectId.value)?.name || '报表'
  link.download = `TrackFlow_报表_${projectName}_${new Date().toISOString().substring(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  Message.success('CSV 导出成功')
}

/** 打印报表 */
function printReport() {
  window.print()
}
</script>

<style scoped>
.report-dashboard {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.overview-content {
  min-height: 0;
}

.dashboard-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.sprint-active-badge {
  font-size: 10px;
  color: var(--tf-success);
  margin-left: 4px;
}

/* 概览卡片 */
.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.stat-card-clickable {
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}

.stat-card-clickable:hover {
  border-color: var(--tf-accent);
  background: var(--tf-bg-hover);
  transform: translateY(-1px);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--tf-text-primary);
  line-height: 1.2;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  font-weight: 400;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.stat-info-icon {
  font-size: 12px;
  opacity: 0.6;
}

.stat-card:hover .stat-info-icon {
  opacity: 1;
}

/* 统计卡片 tooltip 内容 */
.stat-tooltip {
  font-size: 12px;
  line-height: 1.5;
}

.stat-tooltip-title {
  font-weight: 500;
  margin-bottom: 4px;
}

.stat-tooltip-list {
  color: var(--color-text-3);
}

.stat-open .stat-value { color: var(--tf-accent); }
.stat-done .stat-value { color: var(--tf-success); }
.stat-rate .stat-value { color: var(--tf-purple, #a371f7); }
.stat-overdue .stat-value { color: var(--tf-danger); }

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

.chart-download-btn {
  opacity: 0;
  transition: opacity 0.15s;
  margin-left: auto;
  color: var(--tf-text-tertiary);
  flex-shrink: 0;
}

.chart-download-btn:hover {
  color: var(--tf-text-primary);
}

.chart-card:hover .chart-download-btn {
  opacity: 1;
}

.chart-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex: 1;
}

.chart-header-right {
  display: flex;
  align-items: center;
  gap: 6px;
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

.chart-clickable {
  cursor: pointer;
}

.chart-card-wide .chart-instance {
  height: 280px;
}

/* 加载状态 */
.dashboard-loading {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.overview-skeleton {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.chart-skeleton {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

/* 空状态 */
.dashboard-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 56px;
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

/* 解决时间分组明细 */
.resolution-group-details {
  margin-top: 12px;
  border-top: 1px solid var(--tf-border-light);
  padding-top: 12px;
}

.group-detail-header {
  margin-bottom: 8px;
}

.group-detail-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.group-detail-table {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.group-detail-row {
  display: grid;
  grid-template-columns: 1fr 100px 100px 80px;
  padding: 6px 8px;
  font-size: 12px;
  color: var(--tf-text-primary);
  border-radius: 4px;
}

.group-detail-row:hover:not(.group-detail-row-header) {
  background: var(--tf-bg-hover);
}

.group-detail-row-header {
  color: var(--tf-text-tertiary);
  font-weight: 500;
  font-size: 11px;
  border-bottom: 1px solid var(--tf-border-light);
  margin-bottom: 2px;
}

.gd-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gd-value {
  text-align: right;
}

/* 响应式 */
@media (max-width: 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
  .overview-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 打印样式 */
@media print {
  .report-dashboard {
    padding: 0;
    overflow: visible;
  }

  .dashboard-filters {
    display: none;
  }

  .overview-cards {
    grid-template-columns: repeat(4, 1fr);
  }

  .stat-card {
    background: #fff !important;
    border: 1px solid #ddd !important;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .stat-value {
    color: #000 !important;
  }

  .stat-label {
    color: #555 !important;
  }

  .chart-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-card {
    background: #fff !important;
    border: 1px solid #ddd !important;
    break-inside: avoid;
    page-break-inside: avoid;
  }

  .chart-card-wide {
    grid-column: 1 / -1;
  }

  .chart-title {
    color: #000 !important;
  }

  .chart-subtitle {
    color: #555 !important;
  }

  .chart-instance {
    height: 200px !important;
  }

  .chart-card-wide .chart-instance {
    height: 240px !important;
  }
}
</style>
