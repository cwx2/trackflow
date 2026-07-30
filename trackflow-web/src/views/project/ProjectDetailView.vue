<template>
  <div class="project-detail-page">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <a-spin :size="28" />
    </div>

    <!-- 项目详情 -->
    <template v-else-if="project">
      <!-- 归档状态提示 -->
      <div v-if="isArchived" class="archived-banner">
        <icon-lock class="archived-icon" />
        <div class="archived-info">
          <span class="archived-title">此项目已归档</span>
          <span class="archived-desc">归档项目为只读状态，无法创建或修改工单、迭代和成员</span>
        </div>
        <a-button v-if="canEditProject" size="small" type="outline" @click="handleRestore">
          恢复项目
        </a-button>
      </div>

      <!-- 顶部面包屑 + 操作 -->
      <div class="page-header">
        <div class="breadcrumb">
          <a class="breadcrumb-link" @click="$router.push('/projects')">项目</a>
          <span class="breadcrumb-sep">/</span>
          <span class="breadcrumb-current">{{ project.name }}</span>
        </div>
        <div class="header-actions">
          <a-button v-if="canEditProject && !isArchived" size="small" @click="goToSettings">
            <template #icon><icon-settings /></template>
            项目设置
          </a-button>
          <a-dropdown v-if="canDeleteProject" trigger="click">
            <a-button type="text" size="small">
              <icon-more />
            </a-button>
            <template #content>
              <a-doption class="danger-option" @click="confirmDeleteProject">删除项目</a-doption>
            </template>
          </a-dropdown>
        </div>
      </div>

      <!-- 项目信息区域 -->
      <div class="project-hero">
        <div class="project-icon" :style="{ background: getProjectColor() }">
          <span class="icon-text">{{ project.key?.substring(0, 3) }}</span>
        </div>
        <div class="project-main-info">
          <h1 class="project-title">{{ project.name }}</h1>
          <div class="project-meta">
            <span class="meta-item">
              <icon-code class="meta-icon" />
              {{ project.key }}
            </span>
            <span v-if="project.myRoleNames && project.myRoleNames.length > 0" class="meta-item role-badge">
              <icon-user class="meta-icon" />
              {{ project.myRoleNames.join(' / ') }}
            </span>
            <span v-else-if="project.myRoleName" class="meta-item role-badge">
              <icon-user class="meta-icon" />
              {{ project.myRoleName }}
            </span>
            <span class="meta-item visibility-badge" :class="'visibility-' + project.visibility">
              <icon-eye v-if="project.visibility !== 'private'" class="meta-icon" />
              <icon-eye-invisible v-else class="meta-icon" />
              {{ visibilityLabel }}
            </span>
            <span v-if="project.memberCount" class="meta-item">
              <icon-user-group class="meta-icon" />
              {{ project.memberCount }} 名成员
            </span>
            <span v-if="project.leadName" class="meta-item lead-item" :class="{ editable: canEditProject && !isArchived, 'lead-disabled': project.leadStatus === 'disabled' }" @click="canEditProject && !isArchived && goToSettings()">
              <icon-star class="meta-icon" />
              负责人：{{ project.leadName }}
              <span v-if="project.leadStatus === 'disabled'" class="lead-disabled-badge">已禁用</span>
              <icon-edit v-if="canEditProject && !isArchived" class="edit-hint-icon" />
            </span>
            <span v-else-if="canEditProject && !isArchived" class="meta-item lead-item editable" @click="goToSettings()">
              <icon-star class="meta-icon" />
              设置负责人
              <icon-edit class="edit-hint-icon" />
            </span>
          </div>
        </div>
      </div>

      <!-- 项目说明 -->
      <div class="section description-section">
        <h2 class="section-title">项目说明</h2>
        <div v-if="project.description" v-html="projectDescriptionHtml" class="description-rendered"></div>
        <p v-else class="description-empty">暂无项目说明</p>
      </div>

      <!-- 项目统计卡片 -->
      <div v-if="statistics" class="section statistics-section">
        <h2 class="section-title">项目统计</h2>
        <div class="stats-cards">
          <div class="stat-card">
            <span class="stat-value">{{ statistics.totalIssues }}</span>
            <span class="stat-label">工单总数</span>
          </div>
          <div class="stat-card">
            <span class="stat-value stat-open">{{ statistics.openIssues }}</span>
            <span class="stat-label">未解决</span>
          </div>
          <div class="stat-card">
            <span class="stat-value stat-done">{{ statistics.closedIssues }}</span>
            <span class="stat-label">已关闭</span>
          </div>
          <div class="stat-card">
            <span class="stat-value stat-rate">{{ statistics.completionRate }}%</span>
            <span class="stat-label">完成率</span>
          </div>
          <div class="stat-card">
            <span class="stat-value stat-new">+{{ statistics.createdThisWeek }}</span>
            <span class="stat-label">本周新建</span>
          </div>
          <div class="stat-card">
            <span class="stat-value stat-closed-week">+{{ statistics.closedThisWeek }}</span>
            <span class="stat-label">本周关闭</span>
          </div>
        </div>
      </div>

      <!-- 工单状态分布 -->
      <div v-if="statistics && statistics.statusDistribution.length > 0" class="section">
        <h2 class="section-title">状态分布</h2>
        <!-- 堆叠条形图 -->
        <div class="status-bar-container">
          <div class="status-bar">
            <div
              v-for="item in statistics.statusDistribution"
              :key="item.statusId"
              class="status-bar-segment"
              :style="{ width: getStatusPercent(item.count) + '%', background: item.statusColor || '#6b7280' }"
              :title="`${localizeStatusName(item.statusName)}: ${item.count} (${getStatusPercent(item.count).toFixed(1)}%)`"
            ></div>
          </div>
          <div class="status-legend">
            <div v-for="item in statistics.statusDistribution" :key="item.statusId" class="legend-item">
              <span class="legend-dot" :style="{ background: item.statusColor || '#6b7280' }"></span>
              <span class="legend-name">{{ localizeStatusName(item.statusName) }}</span>
              <span class="legend-count">{{ item.count }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 当前 Sprint 进度 -->
      <div v-if="statistics?.activeSprint" class="section">
        <h2 class="section-title">当前 Sprint</h2>
        <div class="sprint-card">
          <div class="sprint-header">
            <span class="sprint-name">{{ statistics.activeSprint.name }}</span>
            <span class="sprint-remaining">
              {{ statistics.activeSprint.remainingDays > 0 ? `剩余 ${statistics.activeSprint.remainingDays} 天` : '已到期' }}
            </span>
          </div>
          <div class="sprint-progress">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: sprintProgressPercent + '%' }"></div>
            </div>
            <span class="progress-text">
              {{ statistics.activeSprint.completedIssues }}/{{ statistics.activeSprint.totalIssues }} 已完成
              ({{ sprintProgressPercent.toFixed(0) }}%)
            </span>
          </div>
          <div class="sprint-dates">
            <span>{{ formatSprintDate(statistics.activeSprint.startDate) }}</span>
            <span>→</span>
            <span>{{ formatSprintDate(statistics.activeSprint.endDate) }}</span>
          </div>
        </div>
      </div>

      <!-- Widget 区域（项目概览仪表盘） -->
      <div class="section widget-overview-section">
        <div class="section-header-row">
          <h2 class="section-title">监控视图</h2>
          <a-button
            v-if="canEditProject && !isArchived"
            size="small"
            type="outline"
            @click="showAddWidgetModal = true"
          >
            <template #icon><icon-plus /></template>
            添加 Widget
          </a-button>
        </div>

        <!-- Widget 加载中 -->
        <div v-if="widgetLoading" class="widget-loading-state">
          <a-spin :size="20" />
          <span class="widget-loading-text">加载 Widget...</span>
        </div>

        <!-- Widget Grid -->
        <template v-else-if="overviewDashboard && overviewDashboard.widgets && overviewDashboard.widgets.length > 0">
          <GridLayout
            v-model:layout="widgetGridLayout"
            :col-num="12"
            :row-height="80"
            :margin="[12, 12]"
            :is-draggable="canEditProject && !isArchived"
            :is-resizable="canEditProject && !isArchived"
            @layout-updated="onWidgetLayoutUpdated"
          >
            <GridItem
              v-for="item in widgetGridLayout"
              :key="item.i"
              :x="item.x"
              :y="item.y"
              :w="item.w"
              :h="item.h"
              :i="item.i"
              class="overview-widget-grid-item"
            >
              <WidgetCard
                :widget="getOverviewWidgetById(item.i)"
                :is-owner="canEditProject && !isArchived"
                @edit="editOverviewWidget"
                @delete="deleteOverviewWidget"
                @move="() => {}"
              />
            </GridItem>
          </GridLayout>
        </template>

        <!-- 空状态 -->
        <div v-else-if="!widgetLoading" class="widget-empty-state">
          <template v-if="canEditProject && !isArchived">
            <div class="widget-empty-icon">📊</div>
            <p class="widget-empty-title">尚未配置监控视图</p>
            <p class="widget-empty-desc">点击「添加 Widget」开始配置项目监控视图，跟踪工单进度、Sprint 状态等数据</p>
            <a-button type="primary" size="small" @click="showAddWidgetModal = true">
              <template #icon><icon-plus /></template>
              添加 Widget
            </a-button>
          </template>
          <template v-else>
            <div class="widget-empty-icon">📊</div>
            <p class="widget-empty-title">暂无监控视图</p>
          </template>
        </div>
      </div>

      <!-- 近期活动 -->
      <div class="section">
        <h2 class="section-title">近期活动</h2>
        <div v-if="activitiesLoading" class="activities-loading">
          <a-spin :size="20" />
        </div>
        <div v-else-if="recentActivities.length > 0" class="activities-list">
          <div v-for="activity in recentActivities" :key="activity.id" class="activity-item">
            <div class="activity-dot"></div>
            <div class="activity-content">
              <div class="activity-main">
                <span class="activity-user">{{ activity.userName }}</span>
                <span class="activity-action">{{ formatActivityAction(activity) }}</span>
              </div>
              <span class="activity-time">{{ formatRelativeTime(activity.createdAt) }}</span>
            </div>
          </div>
        </div>
        <div v-else class="activities-empty">
          <icon-history class="empty-icon" />
          <p class="empty-title">暂无活动记录</p>
          <p class="empty-desc">项目成员的操作记录将在此展示</p>
        </div>
      </div>

      <!-- 功能入口 -->
      <div class="section">
        <h2 class="section-title">功能入口</h2>
        <div class="nav-grid">
          <div class="nav-card" @click="goToIssues">
            <div class="nav-card-icon issues-icon">
              <icon-list />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">问题列表</span>
              <span class="nav-card-desc">查看和管理项目工单</span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>

          <div class="nav-card" @click="goToBoard">
            <div class="nav-card-icon board-icon">
              <icon-apps />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">看板</span>
              <span class="nav-card-desc">可视化任务流转状态</span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>

          <div v-if="canViewSprints" class="nav-card" @click="goToSprints">
            <div class="nav-card-icon sprint-icon">
              <icon-thunderbolt />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">迭代</span>
              <span class="nav-card-desc">查看 Sprint 计划和进度</span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>

          <div v-if="canManageMembers && !isArchived" class="nav-card" @click="goToMembers">
            <div class="nav-card-icon members-icon">
              <icon-user-group />
            </div>
            <div class="nav-card-info">
              <span class="nav-card-title">成员管理</span>
              <span class="nav-card-desc">管理项目成员和角色</span>
            </div>
            <icon-right class="nav-card-arrow" />
          </div>
        </div>
      </div>

      <!-- 项目成员列表 -->
      <div class="section">
        <h2 class="section-title">项目成员</h2>
        <div v-if="membersLoading" class="members-loading">
          <a-spin :size="20" />
        </div>
        <div v-else-if="members.length > 0" class="members-list">
          <div v-for="member in members" :key="member.id" class="member-row">
            <a-avatar :size="28" :style="{ backgroundColor: getMemberColor(member.userId) }">
              {{ (member.displayName || member.username || '?').charAt(0) }}
            </a-avatar>
            <div class="member-info">
              <span class="member-name">{{ member.displayName || member.username }}</span>
              <span class="member-email">{{ member.email }}</span>
            </div>
            <span class="member-role">{{ getMemberRoleNames(member) }}</span>
          </div>
        </div>
        <div v-else class="members-empty">
          <p>暂无成员信息</p>
        </div>
      </div>

      <!-- 添加 Widget 弹窗 -->
      <a-modal
        v-model:visible="showAddWidgetModal"
        title="添加 Widget"
        :width="640"
        :footer="false"
      >
        <div class="widget-type-grid">
          <div
            v-for="wt in widgetTypeList"
            :key="wt.type"
            class="widget-type-card"
            @click="addOverviewWidget(wt.type, wt.defaultTitle)"
          >
            <div class="wt-icon">{{ wt.icon }}</div>
            <div class="wt-info">
              <div class="wt-name">{{ wt.label }}</div>
              <div class="wt-desc">{{ wt.description }}</div>
            </div>
          </div>
        </div>
      </a-modal>

      <!-- Widget 配置弹窗 -->
      <a-modal
        v-model:visible="showWidgetConfigModal"
        title="编辑 Widget 配置"
        :width="520"
        @ok="handleWidgetConfigSave"
        :ok-loading="savingWidgetConfig"
        ok-text="保存"
        cancel-text="取消"
      >
        <a-form :model="widgetConfigForm" layout="vertical">
          <a-form-item label="标题">
            <a-input v-model="widgetConfigForm.title" placeholder="Widget 标题" :max-length="100" />
          </a-form-item>
          <!-- number_card 配置 -->
          <template v-if="editingWidgetType === 'number_card'">
            <a-form-item label="数据来源">
              <a-select v-model="widgetConfigForm.queryType" placeholder="选择统计指标" allow-clear>
                <a-option value="total">工单总数</a-option>
                <a-option value="open">待处理工单数</a-option>
                <a-option value="closed">已完成工单数</a-option>
                <a-option value="unassigned">未分配工单数</a-option>
                <a-option value="overdue">已逾期工单数</a-option>
                <a-option value="completion_rate">完成率 (%)</a-option>
              </a-select>
            </a-form-item>
          </template>
          <!-- note 配置 -->
          <template v-if="editingWidgetType === 'note'">
            <a-form-item label="内容">
              <a-textarea v-model="widgetConfigForm.noteContent" placeholder="笔记内容" :auto-size="{ minRows: 3, maxRows: 8 }" />
            </a-form-item>
          </template>
          <!-- agile_chart 配置 -->
          <template v-if="editingWidgetType === 'agile_chart'">
            <a-form-item label="图表类型">
              <a-select v-model="widgetConfigForm.chartType" placeholder="选择图表类型">
                <a-option value="burndown">燃尽图</a-option>
                <a-option value="cumulative_flow">累积流图</a-option>
              </a-select>
            </a-form-item>
          </template>
        </a-form>
      </a-modal>
    </template>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-state">
      <icon-close-circle class="error-icon" />
      <h3 class="error-title">加载失败</h3>
      <p class="error-desc">{{ error }}</p>
      <a-button type="primary" @click="loadProject">重试</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  IconSettings,
  IconCode,
  IconUser,
  IconUserGroup,
  IconStar,
  IconList,
  IconApps,
  IconThunderbolt,
  IconRight,
  IconCloseCircle,
  IconEdit,
  IconLock,
  IconMore,
  IconEye,
  IconEyeInvisible,
  IconHistory,
  IconPlus
} from '@arco-design/web-vue/es/icon'
import { GridLayout, GridItem } from 'grid-layout-plus'
import { projectApi, workflowApi } from '@/api'
import { customDashboardApi } from '@/api/customDashboard'
import { useAuthStore } from '@/stores/auth'
import { loadProjectPermissions } from '@/composables/usePermission'
import { localizeStatusName } from '@/utils/fieldLabels'
import { renderMarkdown } from '@/utils/markdown'
import type { ProjectDetailVO, ProjectMemberVO, ProjectStatisticsVO, ProjectActivityVO } from '@/api/types'
import type { DashboardDetailVO, DashboardWidgetVO } from '@/api/customDashboard'
import { Message, Modal } from '@arco-design/web-vue'
import WidgetCard from '@/views/report/dashboard/WidgetCard.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const project = ref<ProjectDetailVO | null>(null)
const members = ref<ProjectMemberVO[]>([])
const loading = ref(true)
const membersLoading = ref(false)
const error = ref('')

// 统计和活动数据
const statistics = ref<ProjectStatisticsVO | null>(null)
const recentActivities = ref<ProjectActivityVO[]>([])
const activitiesLoading = ref(false)

// 权限
const projectPerms = ref<Set<string>>(new Set())

const canEditProject = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:edit')
})

const canManageMembers = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:manage_members')
})

const canDeleteProject = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('project:delete')
})

const canViewSprints = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return projectPerms.value.has('sprint:view')
})

const isArchived = computed(() => project.value?.status === 'archived')

// ─── Widget 区域（项目概览仪表盘） ─────────────────────────────────────

const overviewDashboard = ref<DashboardDetailVO | null>(null)
const widgetLoading = ref(false)
const showAddWidgetModal = ref(false)
const showWidgetConfigModal = ref(false)
const editingWidget = ref<DashboardWidgetVO | null>(null)
const editingWidgetType = ref<string>('')
const savingWidgetConfig = ref(false)
const widgetGridLayout = ref<Array<{ i: string; x: number; y: number; w: number; h: number }>>([])

const widgetConfigForm = ref<{
  title: string
  queryType?: string
  noteContent?: string
  chartType?: string
}>({
  title: '',
  queryType: undefined,
  noteContent: '',
  chartType: 'burndown'
})

const widgetTypeList = [
  { type: 'note', label: '快捷笔记', icon: '📝', description: '自由编辑内容', defaultTitle: '笔记' },
  { type: 'number_card', label: '数字卡片', icon: '🔢', description: '单数字大卡片统计', defaultTitle: '统计' },
  { type: 'report_distribution', label: '分布图表', icon: '📊', description: '按字段分组的图表', defaultTitle: '分布报表' },
  { type: 'issue_list', label: 'Issue 列表', icon: '📋', description: '按条件展示工单列表', defaultTitle: 'Issue 列表' },
  { type: 'activity_feed', label: '活动流', icon: '🔔', description: '最近的工单活动', defaultTitle: '最近活动' },
  { type: 'sprint_progress', label: 'Sprint 进度', icon: '🏃', description: 'Sprint 完成进度', defaultTitle: 'Sprint 进度' },
  { type: 'agile_chart', label: '敏捷图表', icon: '📉', description: '燃尽图或累积流图', defaultTitle: '敏捷图表' },
  { type: 'agile_board_status', label: '看板状态', icon: '📊', description: 'Sprint 工单状态分布', defaultTitle: '看板状态' },
  { type: 'calendar', label: '到期日历', icon: '📅', description: 'Issue 到期日历视图', defaultTitle: '到期日历' }
]

function buildWidgetGridLayout(widgets: DashboardWidgetVO[]) {
  widgetGridLayout.value = widgets.map(w => ({
    i: w.id,
    x: w.positionX,
    y: w.positionY,
    w: w.width,
    h: w.height
  }))
}

function getOverviewWidgetById(id: string): DashboardWidgetVO | undefined {
  return overviewDashboard.value?.widgets.find(w => w.id === id)
}

let widgetLayoutSaveTimer: ReturnType<typeof setTimeout> | null = null

function onWidgetLayoutUpdated(layout: Array<{ i: string; x: number; y: number; w: number; h: number }>) {
  if (widgetLayoutSaveTimer) clearTimeout(widgetLayoutSaveTimer)
  widgetLayoutSaveTimer = setTimeout(() => saveWidgetLayout(layout), 1000)
}

async function saveWidgetLayout(layout: Array<{ i: string; x: number; y: number; w: number; h: number }>) {
  if (!overviewDashboard.value || !canEditProject.value) return
  try {
    const items = layout.map(item => ({
      widgetId: item.i,
      positionX: item.x,
      positionY: item.y,
      width: item.w,
      height: item.h
    }))
    const version = overviewDashboard.value.layoutVersion ?? 0
    await customDashboardApi.updateLayout(overviewDashboard.value.id, items, version)
    overviewDashboard.value.layoutVersion = version + 1
  } catch {
    // 布局保存失败静默处理
  }
}

async function loadOverviewDashboard(projectId: string) {
  widgetLoading.value = true
  try {
    const res = await projectApi.getOverviewDashboard(projectId)
    overviewDashboard.value = res.data || null
    if (overviewDashboard.value?.widgets) {
      buildWidgetGridLayout(overviewDashboard.value.widgets)
    }
  } catch {
    overviewDashboard.value = null
  } finally {
    widgetLoading.value = false
  }
}

async function addOverviewWidget(widgetType: string, defaultTitle: string) {
  if (!overviewDashboard.value) return
  showAddWidgetModal.value = false
  try {
    const res = await customDashboardApi.addWidget(overviewDashboard.value.id, {
      widgetType,
      title: defaultTitle,
      config: '{}'
    })
    if (res.data && overviewDashboard.value) {
      overviewDashboard.value.widgets = [...(overviewDashboard.value.widgets || []), res.data]
      buildWidgetGridLayout(overviewDashboard.value.widgets)
    }
    Message.success('Widget 已添加')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加 Widget 失败')
  }
}

function editOverviewWidget(widget: DashboardWidgetVO) {
  editingWidget.value = widget
  editingWidgetType.value = widget.widgetType
  const config = widget.config ? JSON.parse(widget.config) : {}
  widgetConfigForm.value = {
    title: widget.title || '',
    queryType: config.queryType,
    noteContent: config.content || '',
    chartType: config.chartType || 'burndown'
  }
  showWidgetConfigModal.value = true
}

async function handleWidgetConfigSave() {
  if (!editingWidget.value || !overviewDashboard.value) return
  savingWidgetConfig.value = true
  try {
    const config: Record<string, any> = {}
    if (editingWidgetType.value === 'number_card' && widgetConfigForm.value.queryType) {
      config.queryType = widgetConfigForm.value.queryType
    }
    if (editingWidgetType.value === 'note') {
      config.content = widgetConfigForm.value.noteContent
    }
    if (editingWidgetType.value === 'agile_chart') {
      config.chartType = widgetConfigForm.value.chartType
    }
    await customDashboardApi.updateWidget(
      overviewDashboard.value.id,
      editingWidget.value.id,
      {
        title: widgetConfigForm.value.title || undefined,
        config: JSON.stringify(config)
      }
    )
    showWidgetConfigModal.value = false
    // Reload dashboard to get fresh widget data
    const projectKey = project.value?.key || project.value?.id
    if (projectKey) await loadOverviewDashboard(projectKey)
    Message.success('Widget 配置已保存')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    savingWidgetConfig.value = false
  }
}

async function deleteOverviewWidget(widget: DashboardWidgetVO) {
  if (!overviewDashboard.value) return
  Modal.warning({
    title: '删除 Widget',
    content: `确定要删除「${widget.title || widget.widgetType}」Widget？`,
    okText: '删除',
    cancelText: '取消',
    onOk: async () => {
      try {
        await customDashboardApi.deleteWidget(overviewDashboard.value!.id, widget.id)
        if (overviewDashboard.value) {
          overviewDashboard.value.widgets = overviewDashboard.value.widgets.filter(w => w.id !== widget.id)
          buildWidgetGridLayout(overviewDashboard.value.widgets)
        }
        Message.success('Widget 已删除')
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

// 项目描述 Markdown 渲染
const projectDescriptionHtml = computed(() => renderMarkdown(project.value?.description || ''))

// 可见性
const visibilityLabel = computed(() => {
  const map: Record<string, string> = { private: '私有项目', internal: '内部项目', public: '公开项目' }
  return map[project.value?.visibility || 'private'] || '私有项目'
})

// 角色映射（动态加载）
const roleMap = ref<Record<string, string>>({})

async function loadRoles() {
  try {
    const res = await workflowApi.listProjectRoles()
    const roles = res.data || []
    const map: Record<string, string> = {}
    roles.forEach((r) => {
      map[String(r.id)] = r.name
    })
    roleMap.value = map
  } catch {
    roleMap.value = {}
  }
}

function getRoleName(roleId: string): string {
  return roleMap.value[roleId] || `角色 ${roleId}`
}

function getMemberRoleNames(member: any): string {
  // 优先使用后端返回的 roleNames
  if (member.roleNames && member.roleNames.length > 0) {
    return member.roleNames.join(', ')
  }
  // 兼容：使用 roleIds + roleMap
  if (member.roleIds && member.roleIds.length > 0) {
    return member.roleIds.map((rid: string) => getRoleName(rid)).join(', ')
  }
  // 最终 fallback
  return getRoleName(member.roleId)
}

// 颜色
const colorPool = [
  '#e91e63', '#9c27b0', '#673ab7', '#3f51b5', '#2196f3',
  '#00bcd4', '#009688', '#4caf50', '#ff9800', '#ff5722',
  '#795548', '#607d8b'
]

function getProjectColor() {
  const id = parseInt(project.value?.id || '0', 10)
  return colorPool[id % colorPool.length]
}

function getMemberColor(userId: string) {
  const id = parseInt(userId || '0', 10)
  return colorPool[id % colorPool.length]
}

// 加载数据
async function loadProject() {
  const projectKey = route.params.projectKey as string
  if (!projectKey) {
    error.value = '无效的项目标识'
    loading.value = false
    return
  }

  loading.value = true
  error.value = ''

  try {
    const res = await projectApi.getDetail(projectKey)
    project.value = res.data

    // 如果 URL 使用的是数字 ID，重定向到 key 格式（更可读）
    if (project.value!.key && projectKey !== project.value!.key) {
      router.replace({ path: `/projects/${project.value!.key}` })
    }

    // 使用项目 Key 调用后续 API（后端统一支持 Key/ID 标识符）
    const projectIdentifier = project.value!.key || project.value!.id
    // 并行加载权限、成员和角色
    await Promise.all([
      loadPerms(projectIdentifier),
      loadMembers(projectIdentifier),
      loadRoles(),
      loadStatistics(projectIdentifier),
      loadActivities(projectIdentifier),
      loadOverviewDashboard(projectIdentifier)
    ])
  } catch (e: any) {
    if (e.response?.status === 403) {
      error.value = '您没有权限查看此项目'
    } else if (e.response?.status === 404) {
      error.value = '项目不存在'
    } else {
      error.value = e.response?.data?.message || '加载项目信息失败'
    }
  } finally {
    loading.value = false
  }
}

async function loadPerms(projectId: string) {
  try {
    projectPerms.value = await loadProjectPermissions(projectId)
  } catch {
    projectPerms.value = new Set()
  }
}

async function loadMembers(projectId: string) {
  membersLoading.value = true
  try {
    const res = await projectApi.listMembers(projectId)
    members.value = res.data || []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

async function loadStatistics(projectId: string) {
  try {
    const res = await projectApi.getStatistics(projectId)
    statistics.value = res.data
  } catch {
    statistics.value = null
  }
}

async function loadActivities(projectId: string) {
  activitiesLoading.value = true
  try {
    const res = await projectApi.listActivities(projectId, { page: 1, pageSize: 10 })
    recentActivities.value = res.data?.list || []
  } catch {
    recentActivities.value = []
  } finally {
    activitiesLoading.value = false
  }
}

// 统计相关计算属性
const sprintProgressPercent = computed(() => {
  if (!statistics.value?.activeSprint) return 0
  const { totalIssues, completedIssues } = statistics.value.activeSprint
  if (totalIssues === 0) return 0
  return (completedIssues / totalIssues) * 100
})

function getStatusPercent(count: number): number {
  if (!statistics.value || statistics.value.totalIssues === 0) return 0
  return (count / statistics.value.totalIssues) * 100
}

function formatSprintDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function formatActivityAction(activity: ProjectActivityVO): string {
  const action = activity.action
  const detail = activity.detail ? (() => { try { return JSON.parse(activity.detail!) } catch { return {} } })() : {}

  switch (action) {
    case 'member_added':
    case 'add_member': {
      const roleName = detail.role_names || detail.role_name || ''
      return roleName
        ? `添加了成员 ${activity.targetUserName || ''}（角色：${roleName}）`
        : `添加了成员 ${activity.targetUserName || ''}`
    }
    case 'member_removed':
    case 'remove_member':
      return `移除了成员 ${activity.targetUserName || ''}`
    case 'member_role_changed':
    case 'change_role': {
      const oldRole = detail.old_role_names || detail.old_role_name || ''
      const newRole = detail.new_role_names || detail.new_role_name || ''
      if (oldRole && newRole) {
        return `将 ${activity.targetUserName || ''} 的角色从「${oldRole}」变更为「${newRole}」`
      }
      return `变更了 ${activity.targetUserName || ''} 的角色`
    }
    case 'project_created':
    case 'create_project':
      return '创建了项目'
    case 'project_updated':
    case 'update_project': {
      const field = detail.field
      if (field === 'name') {
        return `将项目名称从「${detail.old_value || ''}」变更为「${detail.new_value || ''}」`
      } else if (field === 'description') {
        return '更新了项目描述'
      }
      return `更新了项目${detail.fields ? '（' + detail.fields + '）' : '设置'}`
    }
    case 'project_archived':
    case 'archive_project':
      return '归档了项目'
    case 'project_restored':
    case 'restore_project':
      return '恢复了项目'
    case 'lead_changed':
    case 'change_lead':
      return `将负责人变更为 ${activity.targetUserName || ''}`
    case 'visibility_changed':
    case 'change_visibility': {
      const visibilityMap: Record<string, string> = { private: '私有', internal: '内部', public: '公开' }
      const newVis = visibilityMap[detail.new_value] || detail.visibility || detail.new_value || ''
      return `将项目可见性变更为「${newVis}」`
    }
    // Sprint 相关操作
    case 'create_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `创建了迭代「${sprintName}」` : '创建了迭代'
    }
    case 'activate_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `激活了迭代「${sprintName}」` : '激活了迭代'
    }
    case 'complete_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `完成了迭代「${sprintName}」` : '完成了迭代'
    }
    case 'update_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `更新了迭代「${sprintName}」` : '更新了迭代'
    }
    case 'archive_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `归档了迭代「${sprintName}」` : '归档了迭代'
    }
    case 'auto_complete_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `自动完成了迭代「${sprintName}」` : '自动完成了迭代'
    }
    default:
      return action.replace(/_/g, ' ')
  }
}

function formatRelativeTime(dateStr: string): string {
  const now = new Date()
  const date = new Date(dateStr)
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  if (diffHour < 24) return `${diffHour}小时前`
  if (diffDay < 7) return `${diffDay}天前`
  return `${date.getMonth() + 1}/${date.getDate()}`
}

// 导航
function goToIssues() {
  router.push({ path: '/issues', query: { project: project.value?.key } })
}

function goToBoard() {
  router.push({ path: '/boards', query: { project: project.value?.key } })
}

function goToSprints() {
  router.push({ path: '/sprints', query: { project: project.value?.key } })
}

function goToMembers() {
  router.push({ path: `/projects/${project.value?.key}/settings`, query: { tab: 'members' } })
}

function goToSettings() {
  if (!project.value) return
  router.push({ path: `/projects/${project.value.key}/settings` })
}

async function handleRestore() {
  if (!project.value) return

  Modal.warning({
    title: '恢复项目',
    content: `确定要将项目「${project.value.name}」恢复为活跃状态？恢复后项目将重新允许创建和修改工单。`,
    okText: '确认恢复',
    cancelText: '取消',
    onOk: async () => {
      try {
        await projectApi.restore(project.value!.key)
        Message.success('项目已恢复为活跃状态')
        await loadProject()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '恢复项目失败')
      }
    }
  })
}

async function confirmDeleteProject() {
  if (!project.value) return
  // Navigate to settings page danger zone
  router.push({ path: `/projects/${project.value.key}/settings`, query: { tab: 'general' } })
}

onMounted(() => {
  loadProject()
})
</script>

<style scoped>
.project-detail-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px;
  max-width: 960px;
  margin: 0 auto;
}

/* 加载状态 */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

/* 面包屑 + 头部 */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

.breadcrumb-link {
  color: var(--tf-accent);
  cursor: pointer;
  transition: opacity 0.15s;
}
.breadcrumb-link:hover {
  opacity: 0.8;
}

.breadcrumb-sep {
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
}

.breadcrumb-current {
  color: var(--tf-text-secondary);
}

/* 项目英雄区 */
.project-hero {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 32px;
}

.project-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-text {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  text-transform: uppercase;
}

.project-main-info {
  flex: 1;
  min-width: 0;
}

.project-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
  letter-spacing: -0.3px;
  line-height: 1.2;
}

.project-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.meta-icon {
  font-size: 14px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
}

.role-badge {
  background: var(--tf-accent-bg, rgba(88, 166, 255, 0.1));
  color: var(--tf-accent);
  padding: 2px 8px;
  border-radius: 3px;
  font-weight: 500;
}

.lead-item {
  position: relative;
}

.lead-item.lead-disabled {
  color: var(--tf-text-tertiary);
}

.lead-disabled-badge {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  line-height: 1;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--color-warning-light-2, rgba(209, 153, 34, 0.15));
  color: var(--color-warning-6, #d29922);
  margin-left: 4px;
  font-weight: 500;
}

.lead-item.editable {
  cursor: pointer;
  border-radius: 3px;
  padding: 2px 6px;
  margin: -2px -6px;
  transition: background 0.15s;
}

.lead-item.editable:hover {
  background: var(--tf-bg-hover);
}

.edit-hint-icon {
  font-size: 11px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  opacity: 0;
  transition: opacity 0.15s;
  margin-left: 2px;
}

.lead-item.editable:hover .edit-hint-icon {
  opacity: 1;
}

/* Lead change modal */
.lead-change-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.lead-change-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
  line-height: 1.5;
}

/* Section */
.section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 12px;
}

/* 描述 */
.description-rendered {
  font-size: 13px;
  color: var(--tf-text-secondary);
  line-height: 1.6;
}

.description-rendered :deep(p) {
  margin: 0 0 8px;
}

.description-rendered :deep(h1),
.description-rendered :deep(h2),
.description-rendered :deep(h3) {
  color: var(--tf-text-primary);
  font-weight: 600;
  margin: 12px 0 6px;
}

.description-rendered :deep(h1) { font-size: 16px; }
.description-rendered :deep(h2) { font-size: 14px; }
.description-rendered :deep(h3) { font-size: 13px; }

.description-rendered :deep(strong) {
  color: var(--tf-text-primary);
  font-weight: 600;
}

.description-rendered :deep(code) {
  background: var(--tf-bg-code, var(--tf-bg-surface));
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
}

.description-rendered :deep(pre) {
  background: var(--tf-bg-code, var(--tf-bg-surface));
  padding: 10px 12px;
  border-radius: 4px;
  overflow-x: auto;
  border: 1px solid var(--tf-border);
  margin: 8px 0;
}

.description-rendered :deep(pre code) {
  background: none;
  padding: 0;
}

.description-rendered :deep(blockquote) {
  border-left: 3px solid var(--tf-accent);
  padding-left: 10px;
  color: var(--tf-text-tertiary);
  margin: 6px 0;
}

.description-rendered :deep(a) {
  color: var(--tf-accent);
  text-decoration: none;
}

.description-rendered :deep(a:hover) {
  text-decoration: underline;
}

.description-rendered :deep(ul),
.description-rendered :deep(ol) {
  padding-left: 20px;
  margin: 4px 0 8px;
}

.description-rendered :deep(li) {
  margin-bottom: 2px;
}

.description-text {
  font-size: 13px;
  color: var(--tf-text-secondary);
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
}

.description-empty {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
  font-style: italic;
}

/* 功能入口网格 */
.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.nav-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.nav-card:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-accent);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.nav-card-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
  color: #fff;
}

.issues-icon { background: #3f51b5; }
.board-icon { background: #009688; }
.sprint-icon { background: #ff9800; }
.members-icon { background: #9c27b0; }

.nav-card-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-card-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.nav-card-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.nav-card-arrow {
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  font-size: 14px;
  flex-shrink: 0;
}

/* 成员列表 */
.members-loading {
  padding: 16px;
  text-align: center;
}

.members-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  transition: background 0.15s;
}
.member-row:hover {
  background: var(--tf-bg-hover);
}

.member-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.member-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.member-email {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-role {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-surface);
  padding: 2px 8px;
  border-radius: 3px;
  white-space: nowrap;
}

.members-empty {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  padding: 16px 0;
}
.members-empty p {
  margin: 0;
}

/* 错误状态 */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  text-align: center;
}

.error-icon {
  font-size: 48px;
  color: var(--tf-danger, #f85149);
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
  color: var(--tf-text-tertiary);
  margin: 0 0 24px;
}

/* 操作按钮 */
.header-actions {
  display: flex;
  gap: 8px;
}

/* 归档状态横幅 */
.archived-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(210, 153, 34, 0.08);
  border: 1px solid rgba(210, 153, 34, 0.25);
  border-radius: 8px;
  margin-bottom: 20px;
}

.archived-icon {
  font-size: 20px;
  color: #d29922;
  flex-shrink: 0;
}

.archived-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.archived-title {
  font-size: 13px;
  font-weight: 600;
  color: #d29922;
}

.archived-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* 删除项目确认弹窗 */
.delete-confirm-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.delete-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(248, 81, 73, 0.08);
  border: 1px solid rgba(248, 81, 73, 0.2);
  border-radius: var(--tf-radius-md);
  font-size: 13px;
  color: var(--tf-danger);
}

.delete-warning .warning-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.delete-impact {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.impact-title {
  margin: 0 0 8px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.impact-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.impact-list li {
  font-size: 13px;
}

.impact-warn {
  color: var(--tf-danger);
  font-weight: 500;
}

.delete-confirm-input {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.delete-confirm-input p {
  margin: 0 0 8px;
}

.delete-confirm-input strong {
  color: var(--tf-text-primary);
  font-weight: 600;
}

:deep(.arco-dropdown-option.danger-option) {
  color: var(--tf-danger);
}

/* 可见性设置 */
.visibility-section {
  margin-bottom: 32px;
}

.visibility-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.visibility-option {
  padding: 14px 16px;
  border: 1px solid var(--tf-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.visibility-option:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-text-tertiary);
}

.visibility-option.active {
  border-color: var(--tf-accent);
  background: rgba(88, 166, 255, 0.06);
}

.visibility-option-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.visibility-option-icon {
  font-size: 16px;
  color: var(--tf-text-tertiary);
}

.visibility-option.active .visibility-option-icon {
  color: var(--tf-accent);
}

.visibility-option-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.visibility-option-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin: 0;
  line-height: 1.4;
}

/* 可见性 Badge */
.visibility-badge {
  padding: 2px 8px;
  border-radius: 3px;
  font-weight: 500;
}

.visibility-private {
  background: var(--tf-bg-surface);
  color: var(--tf-text-tertiary);
}

.visibility-internal {
  background: rgba(88, 166, 255, 0.1);
  color: var(--tf-accent);
}

.visibility-public {
  background: rgba(63, 185, 80, 0.1);
  color: #3fb950;
}

/* ========== 统计卡片 ========== */
.statistics-section {
  margin-bottom: 32px;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 12px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
  transition: background 0.15s;
}

.stat-card:hover {
  background: var(--tf-bg-hover);
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--tf-text-primary);
  line-height: 1.2;
  letter-spacing: -0.3px;
}

.stat-value.stat-open { color: var(--tf-accent, #58a6ff); }
.stat-value.stat-done { color: #3fb950; }
.stat-value.stat-rate { color: #d29922; }
.stat-value.stat-new { color: var(--tf-accent, #58a6ff); }
.stat-value.stat-closed-week { color: #3fb950; }

.stat-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  font-weight: 500;
}

/* ========== 状态分布 ========== */
.status-bar-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-bar {
  display: flex;
  height: 10px;
  border-radius: 5px;
  overflow: hidden;
  background: var(--tf-bg-surface);
}

.status-bar-segment {
  transition: width 0.3s ease;
  min-width: 3px;
}

.status-bar-segment:first-child {
  border-radius: 5px 0 0 5px;
}

.status-bar-segment:last-child {
  border-radius: 0 5px 5px 0;
}

.status-bar-segment:only-child {
  border-radius: 5px;
}

.status-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}

.legend-name {
  color: var(--tf-text-secondary);
}

.legend-count {
  color: var(--tf-text-tertiary);
  font-weight: 500;
}

/* ========== Sprint 进度 ========== */
.sprint-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
}

.sprint-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sprint-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.sprint-remaining {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 2px 8px;
  border-radius: 3px;
}

.sprint-progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.progress-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--tf-bg-hover);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
  background: #3fb950;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

.sprint-dates {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* ========== 近期活动 ========== */
.activities-loading {
  padding: 16px;
  text-align: center;
}

.activities-list {
  display: flex;
  flex-direction: column;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 0;
  position: relative;
}

.activity-item:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 4px;
  top: 24px;
  bottom: -4px;
  width: 1px;
  background: var(--tf-border, rgba(255, 255, 255, 0.06));
}

.activity-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--tf-accent, #58a6ff);
  flex-shrink: 0;
  margin-top: 5px;
  opacity: 0.7;
}

.activity-content {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.activity-main {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 13px;
  min-width: 0;
}

.activity-user {
  font-weight: 500;
  color: var(--tf-text-primary);
  white-space: nowrap;
}

.activity-action {
  color: var(--tf-text-secondary);
}

.activity-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  white-space: nowrap;
  flex-shrink: 0;
}

.activities-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 16px;
  text-align: center;
}

.activities-empty .empty-icon {
  font-size: 32px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  margin-bottom: 12px;
  opacity: 0.5;
}

.activities-empty .empty-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  margin: 0 0 4px;
}

.activities-empty .empty-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

/* ========== Widget 概览区域 ========== */
.widget-overview-section {
  margin-bottom: 32px;
}

.section-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-header-row .section-title {
  margin-bottom: 0;
}

.widget-loading-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}

.widget-loading-text {
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

.widget-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 16px;
  text-align: center;
  background: var(--tf-bg-surface);
  border: 1px dashed var(--tf-border, rgba(255, 255, 255, 0.1));
  border-radius: 8px;
}

.widget-empty-icon {
  font-size: 32px;
  margin-bottom: 12px;
}

.widget-empty-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.widget-empty-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0 0 16px;
  max-width: 360px;
  line-height: 1.6;
}

.overview-widget-grid-item {
  /* grid-layout-plus sets position/size; we just ensure card fills the cell */
}

/* Add Widget Type Picker */
.widget-type-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 4px 0;
}

.widget-type-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border, rgba(255, 255, 255, 0.06));
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.widget-type-card:hover {
  border-color: var(--tf-accent);
  background: var(--tf-bg-hover);
}

.wt-icon {
  font-size: 20px;
  flex-shrink: 0;
  line-height: 1;
  margin-top: 1px;
}

.wt-info {
  flex: 1;
  min-width: 0;
}

.wt-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 2px;
}

.wt-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  line-height: 1.4;
}
</style>
