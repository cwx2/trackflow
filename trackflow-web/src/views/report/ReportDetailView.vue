<template>
  <div class="report-detail">
    <!-- 面包屑导航 -->
    <div class="detail-breadcrumb">
      <router-link to="/reports/list" class="breadcrumb-link">
        <span class="breadcrumb-icon">←</span>
        报表列表
      </router-link>
      <span class="breadcrumb-sep">/</span>
      <span class="breadcrumb-current">{{ report?.name || '加载中...' }}</span>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="detail-loading">
      <a-skeleton :animation="true">
        <a-skeleton-line :rows="1" :widths="['40%']" />
      </a-skeleton>
      <a-skeleton :animation="true" style="margin-top: 24px">
        <a-skeleton-line :rows="4" :widths="['100%', '80%', '60%', '90%']" />
      </a-skeleton>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="errorMsg" class="detail-error">
      <div class="error-icon">⚠️</div>
      <h3 class="error-title">加载报表失败</h3>
      <p class="error-desc">{{ errorMsg }}</p>
      <a-button type="primary" size="small" @click="loadReport">重试</a-button>
    </div>

    <!-- 报表内容 -->
    <template v-else-if="report">
      <!-- 标题区 -->
      <div class="detail-title-area">
        <div class="title-left">
          <div class="title-row">
            <h1 class="report-title">{{ report.name }}</h1>
            <span class="type-badge" :class="'type-' + report.type">{{ reportTypeLabel(report.type) }}</span>
            <button
              class="favorite-btn"
              :class="{ 'is-favorited': report.favorited }"
              :title="report.favorited ? '取消收藏' : '收藏'"
              @click="toggleFavorite"
            >
              {{ report.favorited ? '★' : '☆' }}
            </button>
          </div>
          <div class="title-meta">
            <span v-if="report.ownerDisplayName" class="meta-item">
              <span class="meta-icon">👤</span>
              {{ report.ownerDisplayName }}
            </span>
            <span v-if="report.shared" class="meta-item meta-shared">
              <span class="meta-icon">🔗</span>
              已共享{{ report.shareCount ? ` (${report.shareCount} 人)` : '' }}
            </span>
            <span v-if="report.isSystem" class="meta-item meta-system">
              <span class="meta-icon">🏢</span>
              系统报表
            </span>
            <span v-if="reportData?.calculatedAt" class="meta-item meta-time">
              <span class="meta-icon">⏱</span>
              {{ formatRelativeTime(reportData.calculatedAt) }}
            </span>
          </div>
        </div>
      </div>

      <!-- 工具栏 -->
      <div class="detail-toolbar">
        <div class="toolbar-left">
          <!-- 报表切换器 -->
          <a-select
            v-if="favoriteReports.length > 1"
            :model-value="report.id"
            size="small"
            style="width: 220px"
            placeholder="切换报表"
            @change="switchReport"
          >
            <a-option v-for="r in favoriteReports" :key="r.id" :value="r.id">
              {{ r.name }}
            </a-option>
          </a-select>
        </div>
        <div class="toolbar-right">
          <a-button v-if="canEdit" size="small" @click="handleEdit">
            <template #icon><span class="toolbar-icon">✏️</span></template>
            编辑设置
          </a-button>
          <a-button size="small" :loading="recalculating" @click="handleRecalculate">
            <template #icon><span class="toolbar-icon">🔄</span></template>
            重新计算
          </a-button>
          <a-dropdown trigger="click">
            <a-button size="small">
              <template #icon><span class="toolbar-icon">📥</span></template>
              导出
              <span class="dropdown-arrow">▾</span>
            </a-button>
            <template #content>
              <a-doption @click="handleExport('csv')">
                <span class="menu-item"><span class="menu-icon">📄</span>导出 CSV</span>
              </a-doption>
              <a-doption @click="handleExport('xlsx')">
                <span class="menu-item"><span class="menu-icon">📊</span>导出 Excel</span>
              </a-doption>
            </template>
          </a-dropdown>
          <a-button size="small" @click="handlePrint">
            <template #icon><span class="toolbar-icon">🖨️</span></template>
            打印
          </a-button>
          <a-dropdown trigger="click">
            <a-button size="small">
              <span class="toolbar-icon">⋯</span>
            </a-button>
            <template #content>
              <a-doption @click="handleClone">
                <span class="menu-item"><span class="menu-icon">📋</span>克隆</span>
              </a-doption>
              <a-doption v-if="canShare" @click="handleShare">
                <span class="menu-item"><span class="menu-icon">🔗</span>共享设置</span>
              </a-doption>
              <a-doption v-if="canDelete" @click="handleDelete">
                <span class="menu-item menu-danger"><span class="menu-icon">🗑️</span>删除</span>
              </a-doption>
            </template>
          </a-dropdown>
        </div>
      </div>

      <!-- 图表区域 -->
      <div class="detail-chart-area">
        <!-- 图表加载中 -->
        <div v-if="chartLoading" class="chart-loading">
          <a-spin :size="24" />
          <span>正在计算报表数据...</span>
        </div>

        <!-- 图表内容 -->
        <template v-else-if="reportData">
          <div class="chart-summary">
            <span class="chart-total" v-if="reportData.category === 'timeline'">
              {{ reportData.summary?.sprintName || '' }}
              {{ reportData.summary?.totalResolved ? `共解决 ${reportData.summary.totalResolved} 个工单` : '' }}
            </span>
            <span class="chart-total" v-else-if="reportData.category === 'time_management'">
              {{ reportData.summary?.totalHours ? `共 ${reportData.summary.totalHours} 小时` : '' }}
              {{ reportData.summary?.dateRange ? `(${reportData.summary.dateRange})` : '' }}
            </span>
            <span class="chart-total" v-else>共 {{ reportData.total }} 个工单</span>
            <span class="chart-group" v-if="reportData.category === 'timeline'">时间线趋势</span>
            <span class="chart-group" v-else-if="reportData.category === 'state_transition'">状态转换统计</span>
            <span class="chart-group" v-else-if="reportData.category === 'time_management'">时间管理</span>
            <span class="chart-group" v-else-if="!reportData.secondGroupBy">
              按 {{ groupByLabel(reportData.groupBy) }} 分组
            </span>
            <span class="chart-group" v-else>
              {{ groupByLabel(reportData.groupBy) }} × {{ groupByLabel(reportData.secondGroupBy!) }}
            </span>
          </div>

          <!-- 时间线图表（line/area） -->
          <div v-if="isTimeSeriesChart" class="chart-container chart-full">
            <v-chart
              :option="buildTimeSeriesChartOption(reportData)"
              autoresize
              class="report-chart-instance"
            />
          </div>
          <!-- 单维度图表 -->
          <div v-else-if="!reportData.secondGroupBy" class="chart-container chart-full">
            <v-chart
              :option="buildChartOption(reportData)"
              autoresize
              class="report-chart-instance"
            />
          </div>
          <!-- 双维度：堆叠条形图 + 矩阵表格 -->
          <div v-else class="cross-report-container">
            <div class="chart-container chart-full">
              <v-chart
                :option="buildCrossChartOption(reportData)"
                autoresize
                class="report-chart-instance"
              />
            </div>
            <div class="matrix-table-wrapper">
              <table class="matrix-table">
                <thead>
                  <tr>
                    <th class="matrix-corner">
                      {{ groupByLabel(reportData.groupBy) }} ＼ {{ groupByLabel(reportData.secondGroupBy!) }}
                    </th>
                    <th
                      v-for="col in reportData.secondLabels"
                      :key="col"
                      class="matrix-col-header"
                    >{{ localizeLabel(col, reportData.secondGroupBy!) }}</th>
                    <th class="matrix-col-header matrix-row-total">合计</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rowIdx) in reportData.matrix" :key="rowIdx">
                    <td class="matrix-row-header">{{ localizeLabel(reportData.labels[rowIdx], reportData.groupBy) }}</td>
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
        </template>

        <!-- 无数据 -->
        <div v-else class="chart-empty">
          <div class="empty-icon">📊</div>
          <p class="empty-desc">点击"重新计算"获取最新数据</p>
          <a-button type="primary" size="small" @click="handleRecalculate">重新计算</a-button>
        </div>
      </div>
    </template>

    <!-- 编辑报表弹窗 -->
    <a-modal
      v-model:visible="showEditModal"
      title="编辑报表设置"
      ok-text="保存修改"
      cancel-text="取消"
      :ok-loading="saving"
      @ok="handleSaveEdit"
      @cancel="showEditModal = false"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="报表名称" required>
          <a-input v-model="editForm.name" placeholder="报表名称" :max-length="200" />
        </a-form-item>
        <a-form-item label="报表类型" required>
          <a-select v-model="editForm.type" placeholder="选择类型">
            <a-option-group label="Issue 分布">
              <a-option value="issue_count">工单数量统计</a-option>
              <a-option value="by_status">按状态分布</a-option>
              <a-option value="by_assignee">按负责人分布</a-option>
              <a-option value="by_priority">按优先级分布</a-option>
            </a-option-group>
            <a-option-group label="时间线趋势">
              <a-option value="burndown_chart">燃尽图</a-option>
              <a-option value="cumulative_flow">累积流图</a-option>
              <a-option value="resolution_time">解决时间分析</a-option>
              <a-option value="average_issue_age">平均工单年龄</a-option>
            </a-option-group>
            <a-option-group label="状态转换">
              <a-option value="state_transition">状态转换统计</a-option>
            </a-option-group>
            <a-option-group label="时间管理">
              <a-option value="time_report">时间报表</a-option>
              <a-option value="estimation_report">预估对比</a-option>
            </a-option-group>
          </a-select>
        </a-form-item>
        <a-form-item label="分组依据">
          <a-select v-model="editForm.groupBy" placeholder="选择分组">
            <a-option value="status">状态</a-option>
            <a-option value="assignee">负责人</a-option>
            <a-option value="priority">优先级</a-option>
            <a-option value="type">工单类型</a-option>
            <a-option value="project">项目</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="共享">
          <a-switch v-model="editForm.shared" />
          <span class="form-hint">共享后项目其他成员可查看此报表</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 共享设置弹窗 -->
    <ShareReportModal
      v-model:visible="showShareModal"
      :report-id="report?.id || ''"
      @saved="onShareSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { reportApi } from '@/api/report'
import { useAuthStore } from '@/stores/auth'
import { localizeStatusName, priorityLabelMap } from '@/utils/fieldLabels'
import ShareReportModal from './ShareReportModal.vue'
import type { ReportDefinitionVO, ReportDataVO, UpdateReportParams } from '@/api/report'

// 注册 ECharts 组件
use([CanvasRenderer, PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent])

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// ─── 状态 ────────────────────────────────────────────────

const loading = ref(true)
const errorMsg = ref('')
const report = ref<ReportDefinitionVO | null>(null)
const reportData = ref<ReportDataVO | null>(null)
const chartLoading = ref(false)
const recalculating = ref(false)
const favoriteReports = ref<ReportDefinitionVO[]>([])

// 编辑相关
const showEditModal = ref(false)
const saving = ref(false)
const editForm = reactive({
  name: '',
  type: '',
  groupBy: 'status',
  shared: false
})

// 共享弹窗
const showShareModal = ref(false)

// ─── 计算属性 ─────────────────────────────────────────────

const reportId = computed(() => route.params.id as string)

const canEdit = computed(() => {
  if (!report.value) return false
  if (report.value.isSystem) return authStore.hasGlobalPermission('system:admin')
  return authStore.hasGlobalPermission('system:admin') || authStore.hasGlobalPermission('nav:report_create')
})

const canDelete = computed(() => {
  if (!report.value) return false
  if (report.value.isSystem) return false
  return authStore.hasGlobalPermission('system:admin') || authStore.hasGlobalPermission('nav:report_create')
})

const canShare = computed(() => {
  if (!report.value) return false
  if (report.value.isSystem) return false
  return report.value.createdBy === authStore.user?.id
})

const isTimeSeriesChart = computed(() => {
  if (!reportData.value) return false
  return reportData.value.category === 'timeline'
    || reportData.value.category === 'state_transition'
    || reportData.value.category === 'time_management'
})

// ─── 生命周期 ─────────────────────────────────────────────

onMounted(async () => {
  readThemeColors()
  themeObserver = new MutationObserver(() => readThemeColors())
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class'] })
  await loadReport()
  loadFavoriteReports()
})

let themeObserver: MutationObserver | null = null
onBeforeUnmount(() => {
  themeObserver?.disconnect()
})

// 监听路由参数变化，支持报表切换
watch(() => route.params.id, (newId) => {
  if (newId && route.name === 'ReportDetail') {
    loadReport()
  }
})

// ─── 数据加载 ─────────────────────────────────────────────

async function loadReport() {
  loading.value = true
  errorMsg.value = ''
  reportData.value = null
  try {
    const res = await reportApi.getById(reportId.value)
    report.value = res.data
    // 自动加载报表数据
    await loadReportData(false)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '加载报表失败'
  } finally {
    loading.value = false
  }
}

async function loadReportData(force: boolean) {
  chartLoading.value = true
  try {
    const res = await reportApi.execute(reportId.value, force)
    reportData.value = res.data
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取报表数据失败')
  } finally {
    chartLoading.value = false
  }
}

async function loadFavoriteReports() {
  try {
    const res = await reportApi.list()
    // 只保留收藏的报表用于切换器
    favoriteReports.value = (res.data || []).filter(r => r.favorited)
  } catch {
    // non-critical
  }
}

// ─── 工具栏操作 ───────────────────────────────────────────

async function handleRecalculate() {
  recalculating.value = true
  try {
    await loadReportData(true)
    Message.success('报表已重新计算')
  } finally {
    recalculating.value = false
  }
}

function handleEdit() {
  if (!report.value) return
  editForm.name = report.value.name
  editForm.type = report.value.type
  editForm.shared = report.value.shared ?? false
  try {
    const config = report.value.config ? JSON.parse(report.value.config) : {}
    editForm.groupBy = config.groupBy || 'status'
  } catch {
    editForm.groupBy = 'status'
  }
  showEditModal.value = true
}

async function handleSaveEdit() {
  if (!editForm.name.trim()) {
    Message.warning('请输入报表名称')
    return
  }
  saving.value = true
  try {
    const configObj: Record<string, any> = { groupBy: editForm.groupBy }
    const updateData: UpdateReportParams = {
      name: editForm.name.trim(),
      type: editForm.type,
      config: JSON.stringify(configObj),
      shared: editForm.shared
    }
    const res = await reportApi.update(reportId.value, updateData)
    report.value = res.data
    showEditModal.value = false
    Message.success('报表设置已保存')
    // 重新计算
    await loadReportData(true)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleExport(format: 'csv' | 'xlsx') {
  try {
    const blob = format === 'xlsx'
      ? await reportApi.exportExcel(reportId.value)
      : await reportApi.exportCsv(reportId.value)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${report.value?.name || 'report'}.${format}`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    Message.success(format === 'xlsx' ? 'Excel 已导出' : '报表已导出')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '导出失败')
  }
}

function handlePrint() {
  if (!reportData.value) {
    Message.info('请先等待报表数据加载完成')
    return
  }
  window.print()
}

async function handleClone() {
  try {
    const res = await reportApi.clone(reportId.value)
    Message.success(`已克隆为「${res.data?.name || '副本'}」`)
    // 导航到克隆的报表
    if (res.data?.id) {
      router.push({ name: 'ReportDetail', params: { id: res.data.id } })
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '克隆失败')
  }
}

function handleShare() {
  showShareModal.value = true
}

async function onShareSaved() {
  // 刷新报表信息
  await loadReport()
}

function handleDelete() {
  if (!report.value) return
  Modal.warning({
    title: '确认删除',
    content: `确定要删除报表「${report.value.name}」吗？此操作不可恢复。`,
    okText: '删除报表',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      try {
        await reportApi.delete(reportId.value)
        Message.success('报表已删除')
        router.push({ name: 'ReportList' })
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

async function toggleFavorite() {
  if (!report.value) return
  try {
    const res = await reportApi.toggleFavorite(reportId.value)
    report.value.favorited = res.data
    Message.success(res.data ? '已收藏' : '已取消收藏')
    loadFavoriteReports()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

function switchReport(newId: string | number | boolean | Record<string, any> | (string | number | boolean | Record<string, any>)[]) {
  if (typeof newId === 'string' && newId !== reportId.value) {
    router.push({ name: 'ReportDetail', params: { id: newId } })
  }
}

// ─── 辅助函数 ─────────────────────────────────────────────

function reportTypeLabel(type: string) {
  const map: Record<string, string> = {
    issue_count: '数量统计',
    by_status: '状态分布',
    by_assignee: '负责人分布',
    by_priority: '优先级分布',
    by_type: '类型分布',
    burndown: '燃尽图',
    burndown_chart: '燃尽图',
    cumulative_flow: '累积流图',
    resolution_time: '解决时间',
    average_issue_age: '平均工单年龄',
    state_transition: '状态转换',
    time_report: '时间报表',
    estimation_report: '预估对比',
    custom: '自定义'
  }
  return map[type] || type
}

function groupByLabel(groupBy: string) {
  const map: Record<string, string> = {
    status: '状态',
    assignee: '负责人',
    priority: '优先级',
    type: '工单类型',
    project: '项目'
  }
  return map[groupBy] || groupBy
}

function formatRelativeTime(time?: string) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 60) return '刚刚计算'
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin} 分钟前计算`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前计算`
  const diffDay = Math.floor(diffHour / 24)
  return `${diffDay} 天前计算`
}

function localizeLabel(label: string, groupBy: string): string {
  if (groupBy === 'status') return localizeStatusName(label)
  if (groupBy === 'priority') return priorityLabelMap[label] || label
  return label
}

// ─── ECharts 主题色 ──────────────────────────────────────

const chartColors = ref({
  textColor: '#9ca3af',
  axisColor: '#30363d',
  tooltipBg: '#22252a',
  tooltipBorder: '#30363d',
  tooltipText: '#e6edf3',
  cardBorder: '#2a2d33'
})

function readThemeColors() {
  const style = getComputedStyle(document.documentElement)
  chartColors.value = {
    textColor: style.getPropertyValue('--tf-text-secondary').trim() || '#9ca3af',
    axisColor: style.getPropertyValue('--tf-border').trim() || '#30363d',
    tooltipBg: style.getPropertyValue('--tf-bg-elevated').trim() || '#22252a',
    tooltipBorder: style.getPropertyValue('--tf-border').trim() || '#30363d',
    tooltipText: style.getPropertyValue('--tf-text-primary').trim() || '#e6edf3',
    cardBorder: style.getPropertyValue('--tf-bg-elevated').trim() || '#2a2d33'
  }
}

// ─── 图表色板 ────────────────────────────────────────────

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

const palette = ['#58a6ff', '#3fb950', '#f0883e', '#a371f7', '#d29922', '#f85149', '#79c0ff', '#56d364', '#ffa657', '#d2a8ff']

function getItemColor(label: string, groupBy: string, idx: number): string {
  if (groupBy === 'status') return statusColors[label] || palette[idx % palette.length]
  if (groupBy === 'priority') return priorityColors[label] || palette[idx % palette.length]
  return palette[idx % palette.length]
}

// ─── 构建 ECharts Option ─────────────────────────────────

function buildChartOption(data: ReportDataVO): Record<string, any> {
  const chartType = data.chartType || inferChartType(data.groupBy, data.type)
  const c = chartColors.value
  if (chartType === 'pie') return buildPieOption(data, c)
  if (chartType === 'bar_horizontal') return buildBarHorizontalOption(data, c)
  return buildBarVerticalOption(data, c)
}

function inferChartType(groupBy: string, type: string): string {
  if (groupBy === 'assignee' || type === 'by_assignee') return 'bar_horizontal'
  if (groupBy === 'status' || groupBy === 'priority' || groupBy === 'type'
    || type === 'by_status' || type === 'by_priority' || type === 'by_type') return 'pie'
  return 'bar_vertical'
}

function buildPieOption(data: ReportDataVO, c: typeof chartColors.value): Record<string, any> {
  const items = data.labels.map((label, idx) => ({
    name: localizeLabel(label, data.groupBy),
    value: data.data[idx],
    itemStyle: { color: getItemColor(label, data.groupBy, idx) }
  }))
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)', backgroundColor: c.tooltipBg, borderColor: c.tooltipBorder, textStyle: { color: c.tooltipText, fontSize: 12 } },
    legend: { orient: 'vertical', right: 24, top: 'center', textStyle: { color: c.textColor, fontSize: 12 }, itemWidth: 12, itemHeight: 12 },
    series: [{ type: 'pie', radius: ['35%', '65%'], center: ['40%', '50%'], avoidLabelOverlap: true, itemStyle: { borderRadius: 4, borderColor: c.cardBorder, borderWidth: 2 }, label: { show: false }, emphasis: { label: { show: true, fontSize: 13, fontWeight: 500, color: c.tooltipText }, itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.3)' } }, data: items }]
  }
}

function buildBarHorizontalOption(data: ReportDataVO, c: typeof chartColors.value): Record<string, any> {
  const labels = data.labels.map(l => localizeLabel(l, data.groupBy))
  const colors = data.labels.map((l, i) => getItemColor(l, data.groupBy, i))
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', backgroundColor: c.tooltipBg, borderColor: c.tooltipBorder, textStyle: { color: c.tooltipText, fontSize: 12 } },
    grid: { left: 100, right: 32, top: 16, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisLabel: { color: c.textColor, fontSize: 12 }, splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } } },
    yAxis: { type: 'category', data: labels, axisLine: { lineStyle: { color: c.axisColor } }, axisLabel: { color: c.textColor, fontSize: 12, width: 80, overflow: 'truncate' }, axisTick: { show: false } },
    series: [{ type: 'bar', barWidth: '55%', data: data.data.map((val, idx) => ({ value: val, itemStyle: { color: colors[idx], borderRadius: [0, 4, 4, 0] } })) }]
  }
}

function buildBarVerticalOption(data: ReportDataVO, c: typeof chartColors.value): Record<string, any> {
  const labels = data.labels.map(l => localizeLabel(l, data.groupBy))
  const colors = data.labels.map((l, i) => getItemColor(l, data.groupBy, i))
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', backgroundColor: c.tooltipBg, borderColor: c.tooltipBorder, textStyle: { color: c.tooltipText, fontSize: 12 } },
    grid: { left: 48, right: 24, top: 16, bottom: 36 },
    xAxis: { type: 'category', data: labels, axisLine: { lineStyle: { color: c.axisColor } }, axisLabel: { color: c.textColor, fontSize: 12 }, axisTick: { show: false } },
    yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisLabel: { color: c.textColor, fontSize: 12 }, splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } } },
    series: [{ type: 'bar', barWidth: '50%', data: data.data.map((val, idx) => ({ value: val, itemStyle: { color: colors[idx], borderRadius: [4, 4, 0, 0] } })) }]
  }
}

function buildTimeSeriesChartOption(data: ReportDataVO): Record<string, any> {
  const c = chartColors.value
  if (data.category === 'state_transition' && data.labels && data.data) {
    return buildBarHorizontalOption(data, c)
  }
  const dates = data.dates || []
  const series = data.series || []
  const echartseries = series.map(s => ({
    name: s.name, type: 'line', smooth: true, symbol: 'circle', symbolSize: 5,
    lineStyle: { width: 2.5, color: s.color }, itemStyle: { color: s.color },
    areaStyle: s.seriesType === 'area' ? { opacity: 0.25, color: s.color } : undefined,
    stack: data.chartType === 'stacked_area' ? 'total' : undefined,
    data: s.data
  }))
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', backgroundColor: c.tooltipBg, borderColor: c.tooltipBorder, textStyle: { color: c.tooltipText, fontSize: 12 } },
    legend: { bottom: 0, textStyle: { color: c.textColor, fontSize: 12 }, itemWidth: 14, itemHeight: 10 },
    grid: { left: 56, right: 24, top: 24, bottom: 48 },
    xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: c.axisColor } }, axisLabel: { color: c.textColor, fontSize: 11, rotate: dates.length > 14 ? 30 : 0, formatter: (val: string) => val.length >= 10 ? val.substring(5) : val }, axisTick: { show: false } },
    yAxis: { type: 'value', axisLine: { show: false }, axisLabel: { color: c.textColor, fontSize: 12 }, splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } } },
    series: echartseries
  }
}

function buildCrossChartOption(data: ReportDataVO): Record<string, any> {
  const c = chartColors.value
  const primaryLabels = data.labels.map(l => localizeLabel(l, data.groupBy))
  const secondaryLabels = data.secondLabels || []
  const series = secondaryLabels.map((secLabel, secIdx) => ({
    name: localizeLabel(secLabel, data.secondGroupBy || ''), type: 'bar', stack: 'cross', barWidth: '55%',
    emphasis: { focus: 'series' },
    itemStyle: { color: palette[secIdx % palette.length], borderRadius: secIdx === secondaryLabels.length - 1 ? [4, 4, 0, 0] : [0, 0, 0, 0] },
    data: data.matrix ? data.matrix.map(row => row[secIdx] || 0) : []
  }))
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: c.tooltipBg, borderColor: c.tooltipBorder, textStyle: { color: c.tooltipText, fontSize: 12 } },
    legend: { bottom: 0, textStyle: { color: c.textColor, fontSize: 12 }, itemWidth: 12, itemHeight: 10 },
    grid: { left: 100, right: 24, top: 16, bottom: 48 },
    xAxis: { type: 'category', data: primaryLabels, axisLine: { lineStyle: { color: c.axisColor } }, axisLabel: { color: c.textColor, fontSize: 12, width: 80, overflow: 'truncate' }, axisTick: { show: false } },
    yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisLabel: { color: c.textColor, fontSize: 12 }, splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } } },
    series
  }
}
</script>

<style scoped>
.report-detail {
  height: 100%;
  overflow-y: auto;
  padding: 20px 32px 48px;
}

/* 面包屑 */
.detail-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  font-size: 13px;
}

.breadcrumb-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--tf-text-secondary);
  text-decoration: none;
  transition: color 0.15s;
}

.breadcrumb-link:hover {
  color: var(--tf-accent);
}

.breadcrumb-icon {
  font-size: 14px;
}

.breadcrumb-sep {
  color: var(--tf-text-tertiary);
}

.breadcrumb-current {
  color: var(--tf-text-primary);
  font-weight: 500;
}

/* 标题区 */
.detail-title-area {
  margin-bottom: 16px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.report-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
  letter-spacing: -0.3px;
}

.type-badge {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 3px;
  font-weight: 500;
  background: var(--tf-bg-surface);
  color: var(--tf-text-secondary);
}

.type-badge.type-by_status { color: var(--tf-accent); background: rgba(88, 166, 255, 0.1); }
.type-badge.type-by_assignee { color: var(--tf-purple, #a371f7); background: rgba(163, 113, 247, 0.1); }
.type-badge.type-by_priority { color: var(--tf-warning); background: rgba(210, 153, 34, 0.1); }
.type-badge.type-by_type { color: var(--tf-success); background: rgba(63, 185, 80, 0.1); }
.type-badge.type-issue_count { color: var(--tf-success); background: rgba(63, 185, 80, 0.1); }

.favorite-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
  color: var(--tf-text-tertiary);
  transition: color 0.15s, transform 0.15s;
}

.favorite-btn:hover {
  color: var(--tf-warning, #d29922);
  transform: scale(1.15);
}

.favorite-btn.is-favorited {
  color: var(--tf-warning, #d29922);
}

.title-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-icon {
  font-size: 12px;
}

.meta-shared {
  color: var(--tf-accent);
}

.meta-system {
  color: var(--tf-text-secondary);
}

.meta-time {
  color: var(--tf-text-tertiary);
}

/* 工具栏 */
.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 24px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-icon {
  font-size: 12px;
  margin-right: 2px;
}

.dropdown-arrow {
  font-size: 10px;
  margin-left: 2px;
  opacity: 0.6;
}

/* 图表区域 */
.detail-chart-area {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  padding: 24px;
  min-height: 400px;
}

.chart-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 12px;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}

.chart-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

.chart-total {
  font-weight: 500;
  color: var(--tf-text-secondary);
  font-size: 14px;
}

.chart-container {
  width: 100%;
}

.chart-full {
  height: 420px;
}

.report-chart-instance {
  width: 100%;
  height: 100%;
}

.chart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 12px;
}

.empty-icon {
  font-size: 48px;
}

.empty-desc {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0;
}

/* 双维度 */
.cross-report-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.matrix-table-wrapper {
  overflow-x: auto;
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
}

.matrix-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  white-space: nowrap;
}

.matrix-table th,
.matrix-table td {
  padding: 8px 12px;
  text-align: center;
  border-bottom: 1px solid var(--tf-border-light);
}

.matrix-table th {
  background: var(--tf-bg-surface);
  color: var(--tf-text-secondary);
  font-weight: 500;
  font-size: 12px;
}

.matrix-corner {
  text-align: left !important;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  min-width: 120px;
}

.matrix-row-header {
  text-align: left !important;
  font-weight: 500;
  color: var(--tf-text-primary);
  background: var(--tf-bg-surface);
}

.matrix-cell {
  color: var(--tf-text-tertiary);
  min-width: 56px;
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

/* 加载/错误状态 */
.detail-loading {
  padding: 24px 0;
}

.detail-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
  text-align: center;
}

.error-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.error-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.error-desc {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0 0 24px;
}

/* 菜单项 */
.menu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.menu-icon {
  font-size: 12px;
  width: 16px;
  text-align: center;
}

.menu-danger {
  color: var(--tf-danger);
}

/* 表单提示 */
.form-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-left: 8px;
}

/* 打印样式 */
@media print {
  .report-detail {
    padding: 0;
    overflow: visible;
  }

  .detail-breadcrumb,
  .detail-toolbar {
    display: none !important;
  }

  .detail-chart-area {
    border: none;
    padding: 0;
    background: #fff !important;
  }

  .report-title {
    color: #000 !important;
  }

  .chart-summary {
    color: #333 !important;
  }

  .report-chart-instance {
    height: 400px !important;
  }

  .matrix-table th,
  .matrix-table td {
    border: 1px solid #ddd !important;
    background: #fff !important;
    color: #000 !important;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .matrix-table th {
    background: #f5f5f5 !important;
  }
}
</style>
