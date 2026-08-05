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

    <!-- Tab 切换：全部 / 我的报表 / 共享给我的 -->
    <div class="report-tabs">
      <a-radio-group v-model="viewMode" type="button" size="small">
        <a-radio value="all">全部报表</a-radio>
        <a-radio value="mine">我的报表</a-radio>
        <a-radio value="shared">共享给我的</a-radio>
      </a-radio-group>
    </div>

    <!-- 筛选栏 -->
    <div class="report-toolbar">
      <a-input-search
        v-model="searchKeyword"
        placeholder="搜索报表名称..."
        allow-clear
        size="small"
        style="width: 240px; margin-right: 12px"
      />
      <a-select
        v-model="selectedProjectId"
        placeholder="所有项目"
        allow-clear
        size="small"
        style="width: 200px; margin-right: 12px"
        @change="onProjectChange"
      >
        <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
      </a-select>
      <a-select
        v-model="selectedTypeFilter"
        placeholder="所有类型"
        allow-clear
        size="small"
        style="width: 180px"
      >
        <a-option value="issue_distribution">Issue 分布</a-option>
        <a-option value="timeline">时间线趋势</a-option>
        <a-option value="state_transition">状态转换</a-option>
        <a-option value="time_management">时间管理</a-option>
        <a-option value="other">其他</a-option>
      </a-select>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="report-loading">
      <a-skeleton :animation="true" v-for="i in 3" :key="i" style="margin-bottom: 16px">
        <a-skeleton-line :rows="3" :widths="['50%', '80%', '30%']" />
      </a-skeleton>
    </div>

    <!-- 空状态 -->
    <div v-else-if="filteredReports.length === 0 && reports.length > 0" class="report-empty">
      <div class="empty-icon">🔍</div>
      <h3 class="empty-title">未找到匹配的报表</h3>
      <p class="empty-desc">
        {{ searchKeyword ? `没有名称包含"${searchKeyword}"的报表。` : '' }}
        {{ viewMode === 'mine' ? '你还没有创建任何报表。' : '' }}
        {{ viewMode === 'shared' ? '还没有其他人共享报表给你。' : '' }}
        试试调整筛选条件或创建新报表。
      </p>
      <a-button v-if="canCreateReport" type="primary" size="small" @click="openCreateModal">
        创建报表
      </a-button>
    </div>
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
      <!-- 按类型分组展示时的分类标题 -->
      <template v-if="selectedTypeFilter && filteredReports.length > 0">
        <div class="report-category-header">
          <span class="category-label">{{ typeCategoryLabel(selectedTypeFilter) }}</span>
          <span class="category-count">{{ filteredReports.length }} 个报表</span>
        </div>
      </template>
      <template v-else-if="!selectedTypeFilter && filteredReports.length > 0">
        <div class="report-results-info">
          <span class="results-count">共 {{ filteredReports.length }} 个报表</span>
          <span v-if="searchKeyword" class="results-keyword">搜索: "{{ searchKeyword }}"</span>
        </div>
      </template>
      <div
        v-for="report in filteredReports"
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
        <h3 class="card-title">
          <router-link :to="{ name: 'ReportDetail', params: { id: report.id } }" class="card-title-link" @click.stop>
            {{ report.name }}
          </router-link>
        </h3>
        <div class="card-meta">
          <span v-if="report.shared" class="meta-shared">🔗 已共享</span>
          <span v-else-if="report.shareCount > 0" class="meta-shared">🔗 {{ report.shareCount }} 人</span>
          <span v-if="isOtherOwner(report)" class="meta-owner">👤 {{ report.ownerDisplayName }}</span>
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
          <ReportChart
            :data="reportData[report.id]"
            height="200px"
            :dimensions="allDimensions"
          />
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
              <a-option value="fixed_vs_reported">修复率 vs 报告率</a-option>
              <a-option value="verified_vs_reopened">验证率 vs 重开率</a-option>
              <a-option value="resolved_vs_new">解决率 vs 新增率</a-option>
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
        <a-form-item label="Issue 筛选">
          <a-textarea
            v-model="form.issueFilter"
            placeholder="输入筛选条件限定报表数据范围，如：type: Bug, priority: High&#10;支持字段：type / status / priority / assignee / sprint / tag / keyword"
            :auto-size="{ minRows: 2, maxRows: 4 }"
          />
          <span class="form-hint">使用与工单列表相同的筛选语法，留空则统计所有工单</span>
        </a-form-item>
        <!-- Average Issue Age 专用配置 -->
        <template v-if="form.type === 'average_issue_age'">
          <a-form-item label="追踪的状态" required>
            <a-select v-model="form.trackedStatuses" placeholder="选择要追踪停留时间的状态" multiple allow-clear>
              <a-option value="Open">Open</a-option>
              <a-option value="In Progress">In Progress</a-option>
              <a-option value="Code Review">Code Review</a-option>
              <a-option value="Testing">Testing</a-option>
              <a-option value="Done (Local Env)">Done (Local Env)</a-option>
              <a-option value="No Test">No Test</a-option>
              <a-option value="Pending Code Review">Pending Code Review</a-option>
              <a-option value="Pending Publish">Pending Publish</a-option>
              <a-option value="Reopened">Reopened</a-option>
              <a-option value="Todo">Todo</a-option>
              <a-option value="Pending Cancel">Pending Cancel</a-option>
              <a-option value="Pending Extension">Pending Extension</a-option>
            </a-select>
            <span class="form-hint">计算工单在所选状态中的平均停留时间</span>
          </a-form-item>
          <a-form-item label="时间粒度">
            <a-select v-model="form.granularity" placeholder="选择时间粒度">
              <a-option value="day">按天</a-option>
              <a-option value="week">按周</a-option>
              <a-option value="month">按月</a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="滑动窗口（天）">
            <a-input-number v-model="form.movingPeriod" :min="1" :max="90" :step="1" placeholder="7" />
            <span class="form-hint">Moving Average 计算窗口大小，默认 7 天</span>
          </a-form-item>
        </template>
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
import { reportApi } from '@/api/report'
import { projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import ShareReportModal from './ShareReportModal.vue'
import ReportChart from './ReportChart.vue'
import type { ReportDefinitionVO, ReportDataVO, UpdateReportParams } from '@/api/report'
import type { ProjectVO } from '@/api/types'

const authStore = useAuthStore()

const loading = ref(true)
const reports = ref<ReportDefinitionVO[]>([])
const projects = ref<ProjectVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const reportData = ref<Record<string, ReportDataVO>>({})
const executingId = ref<string | null>(null)
const refreshingId = ref<string | null>(null)
const autoRefreshTimers = ref<Record<string, ReturnType<typeof setInterval>>>({})
const searchKeyword = ref('')
const viewMode = ref<'all' | 'mine' | 'shared'>('all')
const selectedTypeFilter = ref<string | undefined>(undefined)

/** 报表类型 → 分类映射 */
const typeCategories: Record<string, string> = {
  issue_count: 'issue_distribution',
  by_status: 'issue_distribution',
  by_assignee: 'issue_distribution',
  by_priority: 'issue_distribution',
  by_type: 'issue_distribution',
  burndown: 'timeline',
  burndown_chart: 'timeline',
  cumulative_flow: 'timeline',
  resolution_time: 'timeline',
  average_issue_age: 'timeline',
  fixed_vs_reported: 'timeline',
  verified_vs_reopened: 'timeline',
  resolved_vs_new: 'timeline',
  state_transition: 'state_transition',
  time_report: 'time_management',
  estimation_report: 'time_management',
  custom: 'other'
}

/** 分类标签 */
function typeCategoryLabel(category: string): string {
  const map: Record<string, string> = {
    issue_distribution: 'Issue 分布报表',
    timeline: '时间线趋势报表',
    state_transition: '状态转换报表',
    time_management: '时间管理报表',
    other: '其他报表'
  }
  return map[category] || category
}

/** 经过搜索、视图模式、类型筛选后的报表列表 */
const filteredReports = computed(() => {
  let result = reports.value

  // 1. 按视图模式筛选
  if (viewMode.value === 'mine') {
    const currentUserId = authStore.user?.id
    result = result.filter(r => r.createdBy === currentUserId)
  } else if (viewMode.value === 'shared') {
    const currentUserId = authStore.user?.id
    result = result.filter(r => r.createdBy !== currentUserId)
  }

  // 2. 按搜索关键词筛选
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.trim().toLowerCase()
    result = result.filter(r => r.name?.toLowerCase().includes(keyword))
  }

  // 3. 按类型分类筛选
  if (selectedTypeFilter.value) {
    result = result.filter(r => typeCategories[r.type] === selectedTypeFilter.value)
  }

  return result
})

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
  refreshInterval: 0,
  issueFilter: '',
  trackedStatuses: [] as string[],
  granularity: 'day',
  movingPeriod: 7
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

/** 判断报表是否由他人创建（用于显示创建者名称） */
function isOtherOwner(report: ReportDefinitionVO): boolean {
  if (!report.ownerDisplayName) return false
  if (report.isSystem) return false
  return report.createdBy !== authStore.user?.id
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

/** 时间线类和状态转换类报表不需要 groupBy */
const timelineTypes = new Set(['burndown_chart', 'cumulative_flow', 'resolution_time', 'average_issue_age', 'fixed_vs_reported', 'verified_vs_reopened', 'resolved_vs_new', 'state_transition'])

/** 当 type 有固定的 groupBy 映射或为时间线类型时，禁用 groupBy 选择 */
const isGroupByLocked = computed(() => form.type in typeToGroupByMap || timelineTypes.has(form.type))

/** time_report 专用分组维度（与 YouTrack Time Report Group By 对齐） */
const timeReportDimensions = [
  { value: 'assignee', label: '按负责人', category: 'builtin' },
  { value: 'project', label: '按项目', category: 'builtin' },
  { value: 'work_type', label: '按工作类型', category: 'builtin' },
  { value: 'issue', label: '按工单', category: 'builtin' }
]

/** 可选的分组维度列表（从 API 动态加载） */
const allDimensions = ref<{ value: string; label: string; category?: string }[]>([
  { value: 'status', label: '状态', category: 'builtin' },
  { value: 'assignee', label: '负责人', category: 'builtin' },
  { value: 'priority', label: '优先级', category: 'builtin' },
  { value: 'type', label: '工单类型', category: 'builtin' },
  { value: 'project', label: '项目', category: 'builtin' }
])
const builtinDimensions = computed(() => {
  // time_report 类型使用专用维度列表
  if (form.type === 'time_report') {
    return timeReportDimensions.filter(d => d.category === 'builtin' || !d.category)
  }
  return allDimensions.value.filter(d => d.category === 'builtin' || !d.category)
})
const customFieldDimensions = computed(() => {
  // time_report 不支持自定义字段分组
  if (form.type === 'time_report') return []
  return allDimensions.value.filter(d => d.category === 'custom_field')
})
const availableSecondDimensions = computed(() => {
  return allDimensions.value.filter(d => d.value !== form.groupBy)
})

// type 变化时自动锁定 groupBy，time_report 默认分组为 assignee
watch(() => form.type, (newType) => {
  if (newType in typeToGroupByMap) {
    form.groupBy = typeToGroupByMap[newType]
  } else if (newType === 'time_report') {
    form.groupBy = 'assignee'
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
    const res = await reportApi.execute(report.id, true)
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

/**
 * 将用户友好的筛选文本（如 "type: Bug, priority: High"）转为 QueryExecutor JSON 格式。
 * 如果输入已经是 JSON 数组，直接返回。留空返回空字符串。
 */
function parseIssueFilterToJson(text: string): string {
  if (!text || !text.trim()) return ''
  const trimmed = text.trim()
  // 如果已经是 JSON 数组格式，直接返回
  if (trimmed.startsWith('[')) {
    try {
      JSON.parse(trimmed)
      return trimmed
    } catch { /* JSON 解析容错，降级为文本格式解析 */ }
  }
  // 解析 "field: value" 文本格式
  const filters: object[] = []
  const parts = trimmed.split(/[,;]\s*|\s*\n\s*/)
  for (const part of parts) {
    const match = part.match(/^\s*(\w+)\s*:\s*(.+?)\s*$/)
    if (match) {
      const [, field, value] = match
      const fieldMap: Record<string, string> = {
        type: 'type', assignee: 'assignee', priority: 'priority',
        status: 'status', reporter: 'reporter', sprint: 'sprint',
        keyword: 'keyword', tag: 'tag'
      }
      const mappedField = fieldMap[field.toLowerCase()] || field
      if (mappedField === 'status' && (value === 'open' || value === 'closed')) {
        filters.push({ field: mappedField, operator: value, value: [value] })
      } else {
        filters.push({ field: mappedField, operator: 'eq', value: [value] })
      }
    } else if (part.trim()) {
      filters.push({ field: 'keyword', operator: 'contains', value: [part.trim()] })
    }
  }
  return filters.length > 0 ? JSON.stringify(filters) : ''
}

/**
 * 将 JSON 格式的 issueFilter 转为用户友好的文本格式（用于编辑时显示）
 */
function issueFilterJsonToText(json: string): string {
  if (!json || !json.trim()) return ''
  try {
    const filters = JSON.parse(json) as Array<{ field: string; operator: string; value: string[] }>
    return filters.map(f => {
      const value = f.value?.join(', ') || ''
      return `${f.field}: ${value}`
    }).join(', ')
  } catch {
    return json // 无法解析时直接返回原始文本
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
    if (form.issueFilter.trim()) {
      const parsedFilter = parseIssueFilterToJson(form.issueFilter)
      if (parsedFilter) {
        configObj.issueFilter = parsedFilter
      }
    }
    // Average Issue Age 专用配置
    if (form.type === 'average_issue_age') {
      if (form.trackedStatuses.length > 0) {
        configObj.trackedStatuses = form.trackedStatuses
      }
      if (form.granularity) {
        configObj.granularity = form.granularity
      }
      if (form.movingPeriod && form.movingPeriod > 0) {
        configObj.movingPeriod = form.movingPeriod
      }
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
    form.issueFilter = config.issueFilter ? issueFilterJsonToText(config.issueFilter) : ''
    // Average Issue Age 专用字段
    form.trackedStatuses = config.trackedStatuses || []
    form.granularity = config.granularity || 'day'
    form.movingPeriod = config.movingPeriod || 7
  } catch {
    form.groupBy = 'status'
    form.secondGroupBy = ''
    form.refreshInterval = 0
    form.issueFilter = ''
    form.trackedStatuses = []
    form.granularity = 'day'
    form.movingPeriod = 7
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
  form.issueFilter = ''
  form.trackedStatuses = []
  form.granularity = 'day'
  form.movingPeriod = 7
}

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
    fixed_vs_reported: '修复率 vs 报告率',
    verified_vs_reopened: '验证率 vs 重开率',
    resolved_vs_new: '解决率 vs 新增率',
    state_transition: '状态转换',
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

onMounted(async () => {
  await Promise.all([loadReports(), loadProjects(), loadGroupByOptions()])
})

onBeforeUnmount(() => {
  // 清理所有自动刷新定时器
  Object.keys(autoRefreshTimers.value).forEach(clearAutoRefresh)
})

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

.report-tabs {
  margin-bottom: 16px;
}

.report-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.report-category-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--tf-bg-surface, #22252a);
  border-radius: 6px;
  border: 1px solid var(--tf-border, #30363d);
  grid-column: 1 / -1;
}

.category-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary, #e6edf3);
}

.category-count {
  font-size: 12px;
  color: var(--tf-text-tertiary, #6b7280);
}

.report-results-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 6px 12px;
  grid-column: 1 / -1;
}

.results-count {
  font-size: 13px;
  color: var(--tf-text-secondary, #9ca3af);
}

.results-keyword {
  font-size: 12px;
  color: var(--tf-text-tertiary, #6b7280);
  font-style: italic;
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

.card-title-link {
  color: var(--tf-text-primary);
  text-decoration: none;
  transition: color 0.15s;
}

.card-title-link:hover {
  color: var(--tf-accent);
  text-decoration: underline;
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

.meta-owner {
  color: var(--tf-text-secondary);
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
