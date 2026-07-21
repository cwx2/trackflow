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
        @change="onProjectChange"
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
            <button
              class="favorite-btn"
              :class="{ 'is-favorited': report.favorited }"
              :title="report.favorited ? '取消收藏' : '收藏'"
              @click.stop="toggleFavorite(report)"
            >
              {{ report.favorited ? '★' : '☆' }}
            </button>
            <span v-if="report.isSystem" class="system-badge">系统</span>
            <a-dropdown v-if="canShowMenu(report)" trigger="click" @click.stop>
              <span class="card-menu-btn" @click.stop>⋯</span>
              <template #content>
                <a-doption v-if="canEditReport(report)" @click="startEdit(report)">
                  <span class="menu-item"><span class="menu-icon">✏️</span>编辑</span>
                </a-doption>
                <a-doption v-if="canShareReport(report)" @click="openShareModal(report)">
                  <span class="menu-item"><span class="menu-icon">🔗</span>共享设置</span>
                </a-doption>
                <a-doption @click="cloneReport(report)">
                  <span class="menu-item"><span class="menu-icon">📋</span>克隆</span>
                </a-doption>
                <a-doption @click="exportReport(report, 'csv')">
                  <span class="menu-item"><span class="menu-icon">📥</span>导出 CSV</span>
                </a-doption>
                <a-doption @click="exportReport(report, 'xlsx')">
                  <span class="menu-item"><span class="menu-icon">📊</span>导出 Excel</span>
                </a-doption>
                <a-doption @click="printReportCard(report)">
                  <span class="menu-item"><span class="menu-icon">🖨️</span>打印</span>
                </a-doption>
                <a-doption v-if="canDeleteReport(report)" @click="confirmDelete(report)">
                  <span class="menu-item menu-danger"><span class="menu-icon">🗑️</span>删除</span>
                </a-doption>
              </template>
            </a-dropdown>
          </div>
        </div>
        <h3 class="card-title">{{ report.name }}</h3>
        <div class="card-meta">
          <span v-if="report.shared" class="meta-shared">🔗 已共享</span>
          <span v-else-if="report.shareCount > 0" class="meta-shared">🔗 {{ report.shareCount }} 人</span>
          <span v-if="reportData[report.id]?.calculatedAt" class="meta-calculated">
            ⏱ {{ formatRelativeTime(reportData[report.id].calculatedAt) }}
          </span>
          <span v-else class="meta-time">{{ formatTime(report.createdAt) }}</span>
          <button
            v-if="reportData[report.id]"
            class="card-refresh-btn"
            title="手动刷新数据"
            :class="{ 'is-refreshing': refreshingId === report.id }"
            @click.stop="refreshReport(report)"
          >
            🔄
          </button>
        </div>

        <!-- 报表数据（展开后）— ECharts 图表 -->
        <div v-if="reportData[report.id]" class="card-chart" @click.stop>
          <div class="chart-summary">
            <span class="chart-total">共 {{ reportData[report.id].total }} 个工单</span>
            <span class="chart-group" v-if="!reportData[report.id].secondGroupBy">
              按 {{ groupByLabel(reportData[report.id].groupBy) }} 分组
            </span>
            <span class="chart-group" v-else>
              {{ groupByLabel(reportData[report.id].groupBy) }} × {{ groupByLabel(reportData[report.id].secondGroupBy!) }}
            </span>
          </div>
          <!-- 单维度图表 -->
          <div v-if="!reportData[report.id].secondGroupBy" class="chart-container">
            <v-chart
              :option="buildChartOption(reportData[report.id])"
              autoresize
              class="report-chart-instance"
            />
          </div>
          <!-- 双维度：堆叠条形图 + 矩阵表格 -->
          <div v-else class="cross-report-container">
            <div class="chart-container">
              <v-chart
                :option="buildCrossChartOption(reportData[report.id])"
                autoresize
                class="report-chart-instance"
              />
            </div>
            <div class="matrix-table-wrapper">
              <table class="matrix-table">
                <thead>
                  <tr>
                    <th class="matrix-corner">
                      {{ groupByLabel(reportData[report.id].groupBy) }} ＼ {{ groupByLabel(reportData[report.id].secondGroupBy!) }}
                    </th>
                    <th
                      v-for="col in reportData[report.id].secondLabels"
                      :key="col"
                      class="matrix-col-header"
                    >{{ localizeLabel(col, reportData[report.id].secondGroupBy!) }}</th>
                    <th class="matrix-col-header matrix-row-total">合计</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(row, rowIdx) in reportData[report.id].matrix"
                    :key="rowIdx"
                  >
                    <td class="matrix-row-header">{{ localizeLabel(reportData[report.id].labels[rowIdx], reportData[report.id].groupBy) }}</td>
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
            <a-option-group v-if="builtinDimensions.length > 0" label="内置维度">
              <a-option
                v-for="dim in builtinDimensions"
                :key="dim.value"
                :value="dim.value"
              >{{ dim.label }}</a-option>
            </a-option-group>
            <a-option-group v-if="customFieldDimensions.length > 0" label="自定义字段">
              <a-option
                v-for="dim in customFieldDimensions"
                :key="dim.value"
                :value="dim.value"
              >{{ dim.label }}</a-option>
            </a-option-group>
          </a-select>
          <span v-if="isGroupByLocked" class="form-hint">已根据报表类型自动设置</span>
        </a-form-item>
        <a-form-item label="第二分组维度（交叉分析）">
          <a-select v-model="form.secondGroupBy" placeholder="不使用（单维度）" allow-clear>
            <a-option
              v-for="dim in availableSecondDimensions"
              :key="dim.value"
              :value="dim.value"
            >{{ dim.label }}</a-option>
          </a-select>
          <span class="form-hint">选择后将生成双维度交叉矩阵（如"状态 × 负责人"）</span>
        </a-form-item>
        <a-form-item label="共享">
          <a-switch v-model="form.shared" />
          <span class="form-hint">共享后项目其他成员也可查看此报表</span>
        </a-form-item>
        <a-form-item label="自动刷新周期">
          <a-select v-model="form.refreshInterval" placeholder="选择刷新频率">
            <a-option :value="0">手动刷新（不自动）</a-option>
            <a-option :value="600">每 10 分钟</a-option>
            <a-option :value="1800">每 30 分钟</a-option>
            <a-option :value="3600">每小时</a-option>
            <a-option :value="86400">每天</a-option>
          </a-select>
          <span class="form-hint">设置报表数据自动重新计算的频率</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 共享设置弹窗 -->
    <ShareReportModal
      v-model:visible="showShareModal"
      :report-id="shareReportId"
      @saved="onShareSaved"
    />
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
import ShareReportModal from './ShareReportModal.vue'
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
const refreshingId = ref<string | null>(null)
const autoRefreshTimers = ref<Record<string, ReturnType<typeof setInterval>>>({})

// 创建/编辑相关
const showFormModal = ref(false)
const submitting = ref(false)
const editingReport = ref<ReportDefinitionVO | null>(null)
const form = reactive({
  name: '',
  projectId: '' as string,
  type: 'by_status',
  groupBy: 'status',
  secondGroupBy: '' as string,
  shared: false,
  refreshInterval: 0
})

/** 是否有创建报表权限（system:admin 或 nav:report_create） */
const canCreateReport = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return authStore.hasGlobalPermission('nav:report_create')
})

// 共享弹窗状态
const showShareModal = ref(false)
const shareReportId = ref('')

/** 是否可管理指定报表（显示编辑/删除菜单） */
function canManageReport(report: ReportDefinitionVO): boolean {
  // 系统报表只有系统管理员可编辑
  if (report.isSystem) {
    return authStore.hasGlobalPermission('system:admin')
  }
  return canCreateReport.value
}

/** 是否显示「⋯」菜单（所有已认证用户都能看到克隆/导出） */
function canShowMenu(_report: ReportDefinitionVO): boolean {
  return true
}

/** 是否可编辑报表（系统报表仅管理员） */
function canEditReport(report: ReportDefinitionVO): boolean {
  if (report.isSystem) {
    return authStore.hasGlobalPermission('system:admin')
  }
  return canCreateReport.value
}

/** 是否可删除报表（系统报表不可删除） */
function canDeleteReport(report: ReportDefinitionVO): boolean {
  if (report.isSystem) return false
  return canCreateReport.value
}

/** 是否可管理共享（仅创建者） */
function canShareReport(report: ReportDefinitionVO): boolean {
  if (report.isSystem) return false
  // 只有报表创建者可以管理共享
  return report.createdBy === authStore.user?.id
}

/** 打开共享设置弹窗 */
function openShareModal(report: ReportDefinitionVO) {
  shareReportId.value = report.id
  showShareModal.value = true
}

/** 共享保存后刷新列表 */
async function onShareSaved() {
  await loadReports()
}

/** type → groupBy 自动映射 */
const typeToGroupByMap: Record<string, string> = {
  by_status: 'status',
  by_assignee: 'assignee',
  by_priority: 'priority'
}

/** 当 type 有固定的 groupBy 映射时，禁用 groupBy 选择 */
const isGroupByLocked = computed(() => form.type in typeToGroupByMap)

/** 可选的分组维度列表（从 API 动态加载） */
const allDimensions = ref<{ value: string; label: string; category?: string }[]>([
  { value: 'status', label: '状态', category: 'builtin' },
  { value: 'assignee', label: '负责人', category: 'builtin' },
  { value: 'priority', label: '优先级', category: 'builtin' },
  { value: 'type', label: '工单类型', category: 'builtin' },
  { value: 'project', label: '项目', category: 'builtin' }
])
const builtinDimensions = computed(() => {
  return allDimensions.value.filter(d => d.category === 'builtin' || !d.category)
})
const customFieldDimensions = computed(() => {
  return allDimensions.value.filter(d => d.category === 'custom_field')
})
const availableSecondDimensions = computed(() => {
  return allDimensions.value.filter(d => d.value !== form.groupBy)
})

// type 变化时自动锁定 groupBy
watch(() => form.type, (newType) => {
  if (newType in typeToGroupByMap) {
    form.groupBy = typeToGroupByMap[newType]
  }
})

// groupBy 变化时，如果与 secondGroupBy 相同则清除
watch(() => form.groupBy, (newGroupBy) => {
  if (form.secondGroupBy === newGroupBy) {
    form.secondGroupBy = ''
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

async function loadGroupByOptions() {
  try {
    const res = await reportApi.getGroupByOptions(selectedProjectId.value || undefined)
    if (res.data && res.data.length > 0) {
      allDimensions.value = res.data.map(opt => ({
        value: opt.value,
        label: opt.label,
        category: opt.category
      }))
    }
  } catch {
    // non-critical, fallback to initial built-in dimensions
  }
}

async function onProjectChange() {
  await Promise.all([loadReports(), loadGroupByOptions()])
}

async function executeReport(report: ReportDefinitionVO) {
  // Toggle: 如果已有数据则折叠
  if (reportData.value[report.id]) {
    delete reportData.value[report.id]
    clearAutoRefresh(report.id)
    return
  }
  executingId.value = report.id
  try {
    const res = await reportApi.execute(report.id)
    reportData.value[report.id] = res.data
    // 设置自动刷新定时器
    setupAutoRefresh(report.id, res.data?.refreshInterval)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行报表失败')
  } finally {
    executingId.value = null
  }
}

/** 手动刷新报表数据 */
async function refreshReport(report: ReportDefinitionVO) {
  refreshingId.value = report.id
  try {
    const res = await reportApi.execute(report.id)
    reportData.value[report.id] = res.data
    // 重置自动刷新定时器
    setupAutoRefresh(report.id, res.data?.refreshInterval)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '刷新报表失败')
  } finally {
    refreshingId.value = null
  }
}

/** 设置自动刷新定时器 */
function setupAutoRefresh(reportId: string, interval?: number | null) {
  clearAutoRefresh(reportId)
  if (!interval || interval <= 0) return
  autoRefreshTimers.value[reportId] = setInterval(async () => {
    try {
      const res = await reportApi.execute(reportId)
      reportData.value[reportId] = res.data
    } catch {
      // 自动刷新失败不打扰用户
    }
  }, interval * 1000)
}

/** 清除自动刷新定时器 */
function clearAutoRefresh(reportId: string) {
  if (autoRefreshTimers.value[reportId]) {
    clearInterval(autoRefreshTimers.value[reportId])
    delete autoRefreshTimers.value[reportId]
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
    const configObj: Record<string, any> = { groupBy: form.groupBy }
    if (form.secondGroupBy) {
      configObj.secondGroupBy = form.secondGroupBy
    }
    if (form.refreshInterval > 0) {
      configObj.refreshInterval = form.refreshInterval
    }
    const config = JSON.stringify(configObj)

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
    form.secondGroupBy = config.secondGroupBy || ''
    form.refreshInterval = config.refreshInterval || 0
  } catch {
    form.groupBy = 'status'
    form.secondGroupBy = ''
    form.refreshInterval = 0
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

async function cloneReport(report: ReportDefinitionVO) {
  try {
    const res = await reportApi.clone(report.id)
    Message.success(`已克隆为「${res.data?.name || report.name + ' (副本)'}」`)
    await loadReports()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '克隆失败')
  }
}

async function toggleFavorite(report: ReportDefinitionVO) {
  try {
    const res = await reportApi.toggleFavorite(report.id)
    report.favorited = res.data
    // 重新排序列表：收藏的在前
    reports.value.sort((a, b) => {
      const aFav = a.favorited ? 1 : 0
      const bFav = b.favorited ? 1 : 0
      if (aFav !== bFav) return bFav - aFav
      return (a.name || '').localeCompare(b.name || '')
    })
    Message.success(res.data ? '已收藏' : '已取消收藏')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function exportReport(report: ReportDefinitionVO, format: 'csv' | 'xlsx' = 'csv') {
  try {
    const blob = format === 'xlsx'
      ? await reportApi.exportExcel(report.id)
      : await reportApi.exportCsv(report.id)
    // 触发浏览器下载
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${report.name}.${format}`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    Message.success(format === 'xlsx' ? 'Excel 已导出' : '报表已导出')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '导出失败')
  }
}

function printReportCard(report: ReportDefinitionVO) {
  // 先确保报表数据已加载
  if (!reportData[report.id]) {
    Message.info('请先点击报表加载数据，然后再打印')
    return
  }
  window.print()
}

function resetForm() {
  editingReport.value = null
  form.name = ''
  form.projectId = ''
  form.type = 'by_status'
  form.groupBy = 'status'
  form.secondGroupBy = ''
  form.shared = false
  form.refreshInterval = 0
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
  // First check dynamic dimensions from API
  const dim = allDimensions.value.find(d => d.value === groupBy)
  if (dim) return dim.label
  // Fallback for built-in
  const map: Record<string, string> = {
    status: '状态',
    assignee: '负责人',
    priority: '优先级',
    type: '工单类型',
    project: '项目'
  }
  return map[groupBy] || groupBy
}

function formatTime(time: string) {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 格式化相对时间（"刚刚"/"X分钟前"/"X小时前"等） */
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
  await Promise.all([loadReports(), loadProjects(), loadGroupByOptions()])
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
  // 清理所有自动刷新定时器
  Object.keys(autoRefreshTimers.value).forEach(clearAutoRefresh)
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

/**
 * 双维度交叉模式：堆叠条形图
 * X 轴 = 主维度（labels），每个堆叠系列 = 第二维度（secondLabels）
 */
function buildCrossChartOption(data: ReportDataVO): Record<string, any> {
  const c = chartColors.value
  const primaryLabels = data.labels.map(l => localizeLabel(l, data.groupBy))
  const secondaryLabels = data.secondLabels || []

  // 每个第二维度值对应一个 series
  const series = secondaryLabels.map((secLabel, secIdx) => ({
    name: localizeLabel(secLabel, data.secondGroupBy || ''),
    type: 'bar',
    stack: 'cross',
    barWidth: '55%',
    emphasis: { focus: 'series' },
    itemStyle: { color: palette[secIdx % palette.length], borderRadius: secIdx === secondaryLabels.length - 1 ? [3, 3, 0, 0] : [0, 0, 0, 0] },
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

.favorite-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
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

.meta-calculated {
  color: var(--tf-text-tertiary);
  font-size: 11px;
}

.card-refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s;
  margin-left: auto;
}

.report-card:hover .card-refresh-btn {
  opacity: 1;
}

.card-refresh-btn:hover {
  background: var(--tf-bg-hover);
}

.card-refresh-btn.is-refreshing {
  opacity: 1;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
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

/* 打印样式 */
@media print {
  .report-page {
    padding: 0;
    overflow: visible;
  }

  .report-header,
  .report-toolbar {
    display: none;
  }

  .report-grid {
    display: block;
  }

  .report-card {
    background: #fff !important;
    border: 1px solid #ddd !important;
    break-inside: avoid;
    page-break-inside: avoid;
    margin-bottom: 16px;
    box-shadow: none !important;
  }

  .card-menu-btn,
  .favorite-btn,
  .card-refresh-btn {
    display: none !important;
  }

  .card-title {
    color: #000 !important;
  }

  .card-meta {
    color: #555 !important;
  }

  .chart-summary {
    color: #333 !important;
  }

  .report-chart-instance {
    height: 200px !important;
  }

  .matrix-table {
    border-collapse: collapse;
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
