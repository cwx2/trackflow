<template>
  <div class="report-page">
    <div class="report-header">
      <div class="header-right" v-if="canCreateReport">
        <a-button type="primary" size="small" @click="openCreateModal">
          <template #icon><span class="btn-icon">➕</span></template>
          创建报表
        </a-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="report-toolbar">
      <a-select
        v-model="selectedProjectId"
        placeholder="所有项目"
        allow-clear
        size="small"
        style="width: 200px"
        @change="loadReports"
      >
        <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
      </a-select>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="report-loading">
      <a-skeleton :animation="true" v-for="i in 3" :key="i" style="margin-bottom: 16px">
        <a-skeleton-line :rows="3" :widths="['50%', '80%', '30%']" />
      </a-skeleton>
    </div>

    <!-- 空状态 -->
    <div v-else-if="reports.length === 0" class="report-empty">
      <div class="empty-icon">📊</div>
      <h3 class="empty-title">暂无报表</h3>
      <p class="empty-desc">
        {{ canCreateReport ? '创建第一个报表来跟踪项目进度和工作统计。' : '还没有可查看的报表，请联系项目管理员创建。' }}
      </p>
      <a-button v-if="canCreateReport" type="primary" size="small" @click="openCreateModal">
        创建报表
      </a-button>
    </div>

    <!-- 报表列表 -->
    <div v-else class="report-grid">
      <div
        v-for="report in reports"
        :key="report.id"
        class="report-card"
        :class="{ 'is-loading': executingId === report.id }"
        @click="executeReport(report)"
      >
        <div class="card-header">
          <span class="card-type-badge" :class="'type-' + report.type">{{ reportTypeLabel(report.type) }}</span>
          <div class="card-header-right">
            <span v-if="report.isSystem" class="system-badge">系统</span>
            <a-dropdown v-if="canManageReport(report)" trigger="click" @click.stop>
              <span class="card-menu-btn" @click.stop>⋯</span>
              <template #content>
                <a-doption @click="startEdit(report)">
                  编辑
                </a-doption>
                <a-doption v-if="!report.isSystem" @click="confirmDelete(report)">
                  <span style="color: var(--tf-danger)">删除</span>
                </a-doption>
              </template>
            </a-dropdown>
          </div>
        </div>
        <h3 class="card-title">{{ report.name }}</h3>
        <div class="card-meta">
          <span v-if="report.shared" class="meta-shared">🔗 已共享</span>
          <span class="meta-time">{{ formatTime(report.createdAt) }}</span>
        </div>

        <!-- 报表数据（展开后）— ECharts 图表 -->
        <div v-if="reportData[report.id]" class="card-chart" @click.stop>
          <div class="chart-summary">
            <span class="chart-total">共 {{ reportData[report.id].total }} 个工单</span>
            <span class="chart-group">按 {{ groupByLabel(reportData[report.id].groupBy) }} 分组</span>
          </div>
          <div class="chart-container">
            <v-chart
              :option="buildChartOption(reportData[report.id])"
              autoresize
              class="report-chart-instance"
            />
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="executingId === report.id" class="card-executing">
          <a-spin :size="16" />
          <span>加载数据...</span>
        </div>
      </div>
    </div>

    <!-- 创建/编辑报表弹窗 -->
    <a-modal
      v-model:visible="showFormModal"
      :title="editingReport ? '编辑报表' : '创建报表'"
      :ok-text="editingReport ? '保存修改' : '创建报表'"
      :cancel-text="'取消'"
      :ok-loading="submitting"
      @ok="handleSubmit"
      @cancel="resetForm"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="报表名称" required>
          <a-input v-model="form.name" placeholder="例如：本周 Bug 统计" :max-length="200" />
        </a-form-item>
        <a-form-item label="所属项目" required>
          <a-select v-model="form.projectId" placeholder="选择项目" :disabled="!!editingReport">
            <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
          </a-select>
          <span v-if="editingReport" class="form-hint">项目不可修改</span>
        </a-form-item>
        <a-form-item label="报表类型" required>
          <a-select v-model="form.type" placeholder="选择类型">
            <a-option value="issue_count">工单数量统计</a-option>
            <a-option value="by_status">按状态分布</a-option>
            <a-option value="by_assignee">按负责人分布</a-option>
            <a-option value="by_priority">按优先级分布</a-option>
            <a-option value="time_report">时间报表</a-option>
            <a-option value="estimation_report">预估对比</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分组依据">
          <a-select v-model="form.groupBy" placeholder="选择分组" :disabled="isGroupByLocked">
            <a-option value="status">状态</a-option>
            <a-option value="assignee">负责人</a-option>
            <a-option value="priority">优先级</a-option>
            <a-option value="type">工单类型</a-option>
          </a-select>
          <span v-if="isGroupByLocked" class="form-hint">已根据报表类型自动设置</span>
        </a-form-item>
        <a-form-item label="共享">
          <a-switch v-model="form.shared" />
          <span class="form-hint">共享后项目其他成员也可查看此报表</span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { reportApi } from '@/api/report'
import { projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { localizeStatusName, priorityLabelMap } from '@/utils/fieldLabels'
import type { ReportDefinitionVO, ReportDataVO, UpdateReportParams } from '@/api/report'
import type { ProjectVO } from '@/api/types'

// 注册 ECharts 组件（按需引入）
use([CanvasRenderer, PieChart, BarChart, TooltipComponent, LegendComponent, GridComponent])

const authStore = useAuthStore()

const loading = ref(true)
const reports = ref<ReportDefinitionVO[]>([])
const projects = ref<ProjectVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const reportData = ref<Record<string, ReportDataVO>>({})
const executingId = ref<string | null>(null)

// 创建/编辑相关
const showFormModal = ref(false)
const submitting = ref(false)
const editingReport = ref<ReportDefinitionVO | null>(null)
const form = reactive({
  name: '',
  projectId: '' as string,
  type: 'by_status',
  groupBy: 'status',
  shared: false
})

/** 是否有创建报表权限（system:admin 或 nav:report_create） */
const canCreateReport = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return authStore.hasGlobalPermission('nav:report_create')
})

/** 是否可管理指定报表（显示编辑/删除菜单） */
function canManageReport(report: ReportDefinitionVO): boolean {
  // 系统报表只有系统管理员可编辑
  if (report.isSystem) {
    return authStore.hasGlobalPermission('system:admin')
  }
  return canCreateReport.value
}

/** type → groupBy 自动映射 */
const typeToGroupByMap: Record<string, string> = {
  by_status: 'status',
  by_assignee: 'assignee',
  by_priority: 'priority'
}

/** 当 type 有固定的 groupBy 映射时，禁用 groupBy 选择 */
const isGroupByLocked = computed(() => form.type in typeToGroupByMap)

// type 变化时自动锁定 groupBy
watch(() => form.type, (newType) => {
  if (newType in typeToGroupByMap) {
    form.groupBy = typeToGroupByMap[newType]
  }
})

async function loadReports() {
  loading.value = true
  try {
    const res = await reportApi.list(selectedProjectId.value)
    reports.value = res.data || []
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载报表失败')
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    projects.value = res.data?.list || []
  } catch {
    // non-critical
  }
}

async function executeReport(report: ReportDefinitionVO) {
  // Toggle: 如果已有数据则折叠
  if (reportData.value[report.id]) {
    delete reportData.value[report.id]
    return
  }
  executingId.value = report.id
  try {
    const res = await reportApi.execute(report.id)
    reportData.value[report.id] = res.data
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行报表失败')
  } finally {
    executingId.value = null
  }
}

async function handleSubmit() {
  if (!form.name.trim()) {
    Message.warning('请输入报表名称')
    return
  }
  if (!editingReport.value && !form.projectId) {
    Message.warning('请选择所属项目')
    return
  }
  if (!form.type) {
    Message.warning('请选择报表类型')
    return
  }

  submitting.value = true
  try {
    const config = JSON.stringify({ groupBy: form.groupBy })

    if (editingReport.value) {
      // 编辑模式
      const updateData: UpdateReportParams = {
        name: form.name.trim(),
        type: form.type,
        config,
        shared: form.shared
      }
      const res = await reportApi.update(editingReport.value.id, updateData)
      Message.success('报表已更新')
      // 替换列表中的数据
      const idx = reports.value.findIndex(r => r.id === editingReport.value!.id)
      if (idx !== -1 && res.data) {
        reports.value[idx] = res.data
      }
    } else {
      // 创建模式
      await reportApi.create({
        name: form.name.trim(),
        projectId: form.projectId,
        type: form.type,
        config,
        shared: form.shared
      })
      Message.success('报表创建成功')
      await loadReports()
    }

    showFormModal.value = false
    resetForm()
  } catch (e: any) {
    Message.error(e.response?.data?.message || (editingReport.value ? '更新报表失败' : '创建报表失败'))
  } finally {
    submitting.value = false
  }
}

function openCreateModal() {
  editingReport.value = null
  resetForm()
  showFormModal.value = true
}

function startEdit(report: ReportDefinitionVO) {
  editingReport.value = report
  form.name = report.name
  form.projectId = report.projectId
  form.type = report.type
  form.shared = report.shared ?? false

  // 解析 config 中的 groupBy
  try {
    const config = report.config ? JSON.parse(report.config) : {}
    form.groupBy = config.groupBy || 'status'
  } catch {
    form.groupBy = 'status'
  }

  showFormModal.value = true
}

function confirmDelete(report: ReportDefinitionVO) {
  Modal.warning({
    title: '确认删除',
    content: `确定要删除报表「${report.name}」吗？此操作不可恢复。`,
    okText: '删除报表',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      try {
        await reportApi.delete(report.id)
        Message.success('报表已删除')
        reports.value = reports.value.filter(r => r.id !== report.id)
        delete reportData.value[report.id]
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

function resetForm() {
  editingReport.value = null
  form.name = ''
  form.projectId = ''
  form.type = 'by_status'
  form.groupBy = 'status'
  form.shared = false
}

function reportTypeLabel(type: string) {
  const map: Record<string, string> = {
    issue_count: '数量统计',
    by_status: '状态分布',
    by_assignee: '负责人分布',
    by_priority: '优先级分布',
    by_type: '类型分布',
    burndown: '燃尽图',
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
    type: '工单类型'
  }
  return map[groupBy] || groupBy
}

function formatTime(time: string) {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// ─── ECharts 主题色（动态读取 CSS 变量，适配亮色/暗色主题） ─────────

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

// 监听主题切换
let themeObserver: MutationObserver | null = null

onMounted(async () => {
  readThemeColors()
  themeObserver = new MutationObserver(() => readThemeColors())
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class'] })
  await Promise.all([loadReports(), loadProjects()])
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
})

// ─── 图表预设色板 ─────────────────────────────────────

/** 状态色（按分类） */
const statusColors: Record<string, string> = {
  'Open': '#58a6ff',
  'In Progress': '#f0883e',
  'Code Review': '#a371f7',
  'Testing': '#d29922',
  'Done': '#3fb950',
  'Cancelled': '#6b7280',
  'Reopened': '#f85149',
  'Todo': '#58a6ff',
  'Closed': '#3fb950',
  'Solved': '#3fb950',
  'Online': '#3fb950'
}

/** 优先级色 */
const priorityColors: Record<string, string> = {
  'Critical': '#f85149',
  'High': '#f0883e',
  'Normal': '#58a6ff',
  'Low': '#3fb950',
  'critical': '#f85149',
  'high': '#f0883e',
  'normal': '#58a6ff',
  'low': '#3fb950'
}

/** 通用色板（轮循） */
const palette = ['#58a6ff', '#3fb950', '#f0883e', '#a371f7', '#d29922', '#f85149', '#79c0ff', '#56d364', '#ffa657', '#d2a8ff']

function getItemColor(label: string, groupBy: string, idx: number): string {
  if (groupBy === 'status') return statusColors[label] || palette[idx % palette.length]
  if (groupBy === 'priority') return priorityColors[label] || palette[idx % palette.length]
  return palette[idx % palette.length]
}

function localizeLabel(label: string, groupBy: string): string {
  if (groupBy === 'status') return localizeStatusName(label)
  if (groupBy === 'priority') return priorityLabelMap[label] || label
  return label
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
    // bar_vertical (default)
    return buildBarVerticalOption(data, c)
  }
}

/** 根据 groupBy/type 推断图表类型（当后端没有返回 chartType 时的 fallback） */
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
</script>

<style scoped>
.report-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.report-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 20px;
}

.btn-icon {
  font-size: 12px;
  margin-right: 2px;
}

.report-toolbar {
  margin-bottom: 20px;
}

/* 加载状态 */
.report-loading {
  padding: 16px 0;
}

/* 空状态 */
.report-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
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
  margin: 0 0 24px;
  max-width: 320px;
  line-height: 1.5;
}

/* 报表网格 */
.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.report-card {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.report-card:hover {
  border-color: var(--tf-accent);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.report-card.is-loading {
  opacity: 0.7;
  pointer-events: none;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.card-header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.system-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
  background: rgba(88, 166, 255, 0.1);
  color: var(--tf-accent);
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.card-type-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  font-weight: 500;
  background: var(--tf-bg-surface);
  color: var(--tf-text-secondary);
}

.card-type-badge.type-by_status { color: var(--tf-accent); background: rgba(88, 166, 255, 0.1); }
.card-type-badge.type-by_assignee { color: var(--tf-purple, #a371f7); background: rgba(163, 113, 247, 0.1); }
.card-type-badge.type-by_priority { color: var(--tf-warning); background: rgba(210, 153, 34, 0.1); }
.card-type-badge.type-by_type { color: var(--tf-success); background: rgba(63, 185, 80, 0.1); }
.card-type-badge.type-issue_count { color: var(--tf-success); background: rgba(63, 185, 80, 0.1); }

.card-menu-btn {
  font-size: 16px;
  color: var(--tf-text-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.card-menu-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
  line-height: 1.3;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.meta-shared {
  color: var(--tf-accent);
}

/* 图表区域 */
.card-chart {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--tf-border-light);
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
  height: 200px;
}

.report-chart-instance {
  width: 100%;
  height: 100%;
}

.card-executing {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* 表单提示 */
.form-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-left: 8px;
}
</style>
