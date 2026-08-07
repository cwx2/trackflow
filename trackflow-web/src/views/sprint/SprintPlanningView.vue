<template>
  <div class="sprint-planning-page">
    <!-- 顶部工具栏 -->
    <div class="planning-toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">Sprint 规划</h2>
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 200px"
          size="small"
          allow-search
          :loading="projectLoadState === 'loading'"
          @change="onProjectChange"
        >
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
      </div>
      <div class="toolbar-right">
        <a-button
          v-if="selectedProject && canCreateSprint"
          type="primary"
          size="small"
          @click="openSprintCreateModal"
        >
          + 新建迭代
        </a-button>
        <span v-if="selectedCount > 0" class="selection-indicator">
          已选择 {{ selectedCount }} 个工单
          <a-popover trigger="click" position="bottom" :content-style="{ padding: '4px 0' }" v-model:popup-visible="assignPopoverVisible">
            <a-button size="mini" type="primary">分配负责人</a-button>
            <template #content>
              <div class="assignee-popover-list">
                <div
                  class="assignee-popover-item"
                  @click="batchAssign(null)"
                >
                  <span class="assignee-popover-name">取消分配</span>
                </div>
                <div class="assignee-popover-divider"></div>
                <div
                  v-for="m in projectMembers"
                  :key="m.userId"
                  class="assignee-popover-item"
                  @click="batchAssign(m.userId)"
                >
                  <span class="assignee-popover-name">{{ m.displayName }}</span>
                </div>
              </div>
            </template>
          </a-popover>
          <a-button size="mini" type="text" @click="clearSelection">清除</a-button>
        </span>
      </div>
    </div>

    <!-- 主内容区 -->
    <div v-if="selectedProject" class="planning-content">
      <!-- 左侧 Backlog 面板 -->
      <div class="backlog-panel">
        <div class="panel-header">
          <div class="panel-header-left">
            <h3 class="panel-title">Backlog</h3>
            <span class="panel-count">{{ backlogIssues.length }}</span>
          </div>
          <div class="panel-header-right">
            <a-tooltip content="创建工单到 Backlog">
              <button class="panel-create-btn" @click="openCreateForBacklog">
                <icon-plus />
              </button>
            </a-tooltip>
          </div>
        </div>

        <!-- 筛选区（可折叠） -->
        <div class="backlog-filters-wrapper">
          <div class="backlog-filters-toggle" @click="backlogFiltersExpanded = !backlogFiltersExpanded">
            <icon-filter class="filter-toggle-icon" />
            <span class="filter-toggle-text">筛选</span>
            <span v-if="activeFilterCount > 0" class="filter-active-badge">{{ activeFilterCount }}</span>
            <icon-down v-if="!backlogFiltersExpanded" class="filter-toggle-arrow" />
            <icon-up v-else class="filter-toggle-arrow" />
          </div>
          <div v-show="backlogFiltersExpanded" class="backlog-filters">
            <a-input
              v-model="backlogSearch"
              placeholder="搜索..."
              size="mini"
              allow-clear
              style="margin-bottom: 6px"
              @input="onBacklogSearchInput"
              @clear="loadBacklog"
            >
              <template #prefix><icon-search /></template>
            </a-input>
            <div class="filter-row">
              <a-select v-model="backlogFilterType" placeholder="类型" size="mini" allow-clear style="flex:1" @change="loadBacklog">
                <a-option value="任务">任务</a-option>
                <a-option value="缺陷">缺陷</a-option>
                <a-option value="需求">需求</a-option>
                <a-option value="故事">故事</a-option>
              </a-select>
              <a-select v-model="backlogFilterPriority" placeholder="优先级" size="mini" allow-clear style="flex:1" @change="loadBacklog">
                <a-option value="紧急">紧急</a-option>
                <a-option value="高">高</a-option>
                <a-option value="普通">普通</a-option>
                <a-option value="低">低</a-option>
              </a-select>
              <a-select v-model="backlogFilterAssignee" placeholder="负责人" size="mini" allow-clear style="flex:1" @change="loadBacklog">
                <a-option value="unassigned">未分配</a-option>
                <a-option v-for="m in projectMembers" :key="m.userId" :value="m.userId">
                  {{ m.displayName }}
                </a-option>
              </a-select>
            </div>
          </div>
        </div>

        <!-- Backlog 工单列表 -->
        <div
          class="panel-body"
          @dragover.prevent="onBacklogDragOver"
          @dragleave="onBacklogDragLeave"
          @drop="onBacklogDrop"
          :class="{ 'drop-target': backlogDropHighlight }"
        >
          <a-spin :loading="backlogLoading" class="panel-spin">
            <div class="issue-list">
              <div
                v-for="issue in backlogIssues"
                :key="issue.id"
                class="planning-card"
                :class="{
                  'planning-card--selected': selectedIds.has(issue.id),
                  'planning-card--dragging': draggingIds.has(issue.id)
                }"
                draggable="true"
                @dragstart="onDragStart($event, issue, 'backlog')"
                @dragend="onDragEnd"
                @click="onCardClick($event, issue, 'backlog')"
                @contextmenu.prevent="onCardContextMenu($event, issue, 'backlog')"
              >
                <div class="card-top">
                  <span class="card-key">{{ issue.issueKey }}</span>
                  <span class="card-priority" :class="issue.priority?.toLowerCase()">
                    {{ priorityIcon(issue.priority) }}
                  </span>
                </div>
                <div class="card-title">{{ issue.title }}</div>
                <div class="card-meta">
                  <span class="card-type">{{ localizeIssueType(issue.issueType) }}</span>
                  <span v-if="issue.estimatedHours" class="card-estimation">⏱ {{ issue.estimatedHours }}h</span>
                  <span v-if="issue.assigneeName" class="card-assignee">{{ issue.assigneeName }}</span>
                </div>
              </div>

              <!-- 空状态 -->
              <div v-if="!backlogLoading && backlogIssues.length === 0" class="panel-empty">
                <div class="empty-icon">📋</div>
                <div class="empty-title">Backlog 为空</div>
                <div class="empty-desc">所有工单都已分配到 Sprint</div>
              </div>
            </div>

            <!-- Quick Add 内联输入框 -->
            <div class="quick-add-row">
              <input
                ref="backlogQuickAddRef"
                v-model="backlogQuickAddTitle"
                class="quick-add-input"
                placeholder="+ 快速创建工单..."
                @keydown.enter="quickCreateForBacklog"
                @focus="backlogQuickAddFocused = true"
                @blur="backlogQuickAddFocused = false"
              />
              <a-spin v-if="backlogQuickAddLoading" :size="14" class="quick-add-spinner" />
            </div>
          </a-spin>
        </div>
      </div>

      <!-- 右侧 Sprint 面板区域 -->
      <div class="sprint-panels">
        <div
          v-for="sprint in targetSprints"
          :key="sprint.id"
          class="sprint-column"
        >
          <!-- Sprint 面板头部 -->
          <div class="panel-header">
            <div class="panel-header-left">
              <span class="sprint-badge" :class="sprint.status">
                {{ sprint.status === 'active' ? '进行中' : '计划中' }}
              </span>
              <h3 class="panel-title">{{ sprint.name }}</h3>
              <span class="panel-count">{{ getSprintIssues(sprint.id).length }}</span>
              <span
                v-if="sprint.totalIssues > 0 && getSprintIssues(sprint.id).length < sprint.totalIssues"
                class="panel-count-total"
                :title="`共 ${sprint.totalIssues} 个工单，${sprint.totalIssues - getSprintIssues(sprint.id).length} 个已完成`"
              >/ {{ sprint.totalIssues }}</span>
            </div>
            <div class="panel-header-right">
              <span class="sprint-total-hours" v-if="getSprintTotalHours(sprint.id) > 0">⏱ {{ formatHours(getSprintTotalHours(sprint.id)) }}</span>
              <span v-if="sprint.startDate" class="sprint-dates">{{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}</span>
              <a-tooltip content="在工单列表中查看此 Sprint 的工单">
                <button class="panel-view-issues-btn" @click="viewSprintIssues(sprint)">
                  <icon-list />
                </button>
              </a-tooltip>
              <a-tooltip content="创建工单到此 Sprint">
                <button class="panel-create-btn" @click="openCreateForSprint(sprint.id)">
                  <icon-plus />
                </button>
              </a-tooltip>
            </div>
          </div>

          <!-- Sprint 工时统计条 -->
          <div class="sprint-hours-bar" v-if="getSprintTotalHours(sprint.id) > 0 || velocityData">
            <div class="sprint-hours-stats">
              <div class="hours-stat-item">
                <span class="hours-stat-label">已规划</span>
                <span class="hours-stat-value planned">{{ formatHours(getSprintTotalHours(sprint.id)) }}</span>
              </div>
              <div class="hours-stat-divider" v-if="velocityData && velocityData.averageVelocity > 0"></div>
              <div class="hours-stat-item" v-if="velocityData && velocityData.averageVelocity > 0">
                <span class="hours-stat-label">历史均速</span>
                <span class="hours-stat-value velocity">{{ formatHours(velocityData.averageVelocity) }}/Sprint</span>
              </div>
              <div class="hours-stat-item" v-if="velocityData && velocityData.sprintCount > 0">
                <a-tooltip :content="getVelocityTooltip()">
                  <span
                    class="hours-stat-badge"
                    :class="getLoadBadgeClass(sprint.id)"
                  >{{ getLoadLabel(sprint.id) }}</span>
                </a-tooltip>
              </div>
            </div>
            <!-- 工时进度条（仅有历史速率时才显示对比条） -->
            <div
              class="hours-progress-bar"
              v-if="velocityData && velocityData.averageVelocity > 0 && getSprintTotalHours(sprint.id) > 0"
            >
              <div
                class="hours-progress-fill"
                :class="getProgressBarClass(sprint.id)"
                :style="{ width: getProgressWidth(sprint.id) + '%' }"
              ></div>
              <div class="hours-progress-reference" title="历史平均速率">
                <!-- 参考线标记 -->
              </div>
            </div>
            <div
              class="hours-velocity-history"
              v-if="velocityData && velocityData.sprints.length > 0"
            >
              <span class="velocity-history-label">近期速率：</span>
              <span
                v-for="item in velocityData.sprints.slice(-3)"
                :key="item.id"
                class="velocity-history-chip"
                :title="item.name + ': ' + formatHours(item.completedHours)"
              >{{ formatHours(item.completedHours) }}</span>
            </div>
          </div>

          <!-- Sprint Goal 区域 -->
          <div class="sprint-goal-section">
            <template v-if="editingGoalSprintId === sprint.id">
              <textarea
                ref="goalTextareaRef"
                v-model="editingGoalValue"
                class="sprint-goal-textarea"
                placeholder="输入 Sprint 目标..."
                rows="2"
                @blur="saveGoal(sprint.id)"
                @keydown.enter.exact.prevent="saveGoal(sprint.id)"
                @keydown.escape="cancelGoalEdit"
              ></textarea>
            </template>
            <template v-else>
              <div
                class="sprint-goal-display"
                :class="{ 'sprint-goal-empty': !sprint.goal, 'sprint-goal-editable': canEditSprint }"
                @click="canEditSprint && startGoalEdit(sprint)"
              >
                <span class="sprint-goal-icon">🎯</span>
                <span class="sprint-goal-text">{{ sprint.goal || '设置 Sprint 目标...' }}</span>
                <span v-if="canEditSprint" class="sprint-goal-edit-hint">点击编辑</span>
              </div>
            </template>
          </div>

          <!-- Sprint 工单列表（drop target） -->
          <div
            class="panel-body"
            :class="{ 'drop-target': dropTargetSprintId === sprint.id }"
            @dragover.prevent="onSprintDragOver($event, sprint.id)"
            @dragleave="onSprintDragLeave"
            @drop="onSprintDrop($event, sprint.id)"
          >
            <div class="issue-list">
              <div
                v-for="issue in getSprintIssues(sprint.id)"
                :key="issue.id"
                class="planning-card"
                :class="{
                  'planning-card--selected': selectedIds.has(issue.id),
                  'planning-card--dragging': draggingIds.has(issue.id)
                }"
                draggable="true"
                @dragstart="onDragStart($event, issue, sprint.id)"
                @dragend="onDragEnd"
                @click="onCardClick($event, issue, sprint.id)"
                @contextmenu.prevent="onCardContextMenu($event, issue, sprint.id)"
              >
                <div class="card-top">
                  <span class="card-key">{{ issue.issueKey }}</span>
                  <span class="card-priority" :class="issue.priority?.toLowerCase()">
                    {{ priorityIcon(issue.priority) }}
                  </span>
                </div>
                <div class="card-title">{{ issue.title }}</div>
                <div class="card-meta">
                  <span class="card-type">{{ localizeIssueType(issue.issueType) }}</span>
                  <span v-if="issue.estimatedHours" class="card-estimation">⏱ {{ issue.estimatedHours }}h</span>
                  <span v-if="issue.assigneeName" class="card-assignee">{{ issue.assigneeName }}</span>
                </div>
              </div>

              <!-- Sprint 空状态 -->
              <div v-if="getSprintIssues(sprint.id).length === 0 && sprint.totalIssues > 0" class="panel-empty panel-empty--sprint panel-empty--all-done">
                <div class="empty-icon">✅</div>
                <div class="empty-title">所有工单已完成</div>
                <div class="empty-desc">此 Sprint 共 {{ sprint.totalIssues }} 个工单，全部已完成</div>
              </div>
              <div v-else-if="getSprintIssues(sprint.id).length === 0" class="panel-empty panel-empty--sprint">
                <div class="empty-icon">🎯</div>
                <div class="empty-title">暂无工单</div>
                <div class="empty-desc">从 Backlog 拖拽工单到此处，或使用下方输入框快速创建</div>
              </div>
            </div>

            <!-- Quick Add 内联输入框 -->
            <div class="quick-add-row">
              <input
                v-model="sprintQuickAddTitles[sprint.id]"
                class="quick-add-input"
                placeholder="+ 快速创建工单..."
                @keydown.enter="quickCreateForSprint(sprint.id)"
              />
              <a-spin v-if="sprintQuickAddLoadingId === sprint.id" :size="14" class="quick-add-spinner" />
            </div>
          </div>
        </div>

        <!-- 无 Sprint 空状态 -->
        <div v-if="targetSprints.length === 0 && !sprintsLoading" class="no-sprints-state">
          <div class="empty-icon">🏃</div>
          <h3 class="empty-title">暂无可规划的 Sprint</h3>
          <p class="empty-desc">请先创建一个 Sprint（计划中或进行中状态）</p>
          <div class="no-sprints-actions">
            <a-button v-if="canCreateSprint" type="primary" size="small" @click="openSprintCreateModal">+ 新建迭代</a-button>
            <a-button type="text" size="small" @click="goToSprintPage">前往迭代管理</a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 未选择项目 -->
    <div v-else class="empty-state">
      <div class="empty-icon">📁</div>
      <h3 class="empty-title">请选择项目</h3>
      <p class="empty-desc">从上方下拉框选择一个项目开始 Sprint 规划</p>
    </div>

    <!-- 右键上下文菜单 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="planning-context-menu"
        :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
        @click.stop
      >
        <div class="context-menu-item context-menu-item--submenu">
          <span>分配负责人</span>
          <span class="context-menu-arrow">›</span>
          <div class="context-submenu">
            <div class="context-menu-item" @click="contextAssign(null)">
              取消分配
            </div>
            <div class="context-menu-divider"></div>
            <div
              v-for="m in projectMembers"
              :key="m.userId"
              class="context-menu-item"
              @click="contextAssign(m.userId)"
            >
              {{ m.displayName }}
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 创建工单弹窗 -->
    <IssueCreatePanel
      v-model:visible="showCreatePanel"
      :project-id="selectedProject || undefined"
      :sprint-id="createPanelSprintId"
      :lock-sprint="createPanelLockSprint"
      @created="onIssueCreated"
      @expand-to-fullscreen="onCreatePanelExpand"
    />

    <!-- 新建 Sprint 弹窗 -->
    <a-modal v-model:visible="showSprintCreate" title="新建迭代" :width="480" @ok="handleSprintCreate" :ok-loading="sprintCreating">
      <a-form :model="sprintCreateForm" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model="sprintCreateForm.name" placeholder="如：Sprint 25" />
        </a-form-item>
        <a-form-item label="目标">
          <a-textarea v-model="sprintCreateForm.goal" placeholder="本迭代目标（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
        <a-form-item label="开始日期">
          <a-date-picker v-model="sprintCreateForm.startDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="结束日期">
          <a-date-picker v-model="sprintCreateForm.endDate" style="width: 100%" />
        </a-form-item>

        <!-- 可选操作区域 -->
        <div class="create-options-section" v-if="sprintCreationPreview">
          <div
            class="create-option-item"
            v-if="sprintCreationPreview.activeSprintId && sprintCreationPreview.unresolvedIssueCount > 0"
          >
            <a-checkbox v-model="sprintCreateForm.moveUnresolvedIssues">
              <span class="option-label">添加当前 Sprint 未完成工单</span>
            </a-checkbox>
            <span class="option-desc">
              将 <strong>{{ sprintCreationPreview.activeSprintName }}</strong> 中的
              {{ sprintCreationPreview.unresolvedIssueCount }} 个未完成工单移入新迭代
            </span>
          </div>
          <div class="create-option-item">
            <a-checkbox v-model="sprintCreateForm.setAsDefault">
              <span class="option-label">设为默认 Sprint</span>
            </a-checkbox>
            <span class="option-desc">
              <template v-if="sprintCreationPreview.hasDefaultSprint">
                当前默认为 <strong>{{ sprintCreationPreview.defaultSprintName }}</strong>，替换后新建工单将自动归属此迭代
              </template>
              <template v-else>
                启用后，该项目新创建的工单将自动分配到此迭代
              </template>
            </span>
          </div>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, reactive, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { IconSearch, IconPlus, IconList, IconFilter, IconDown, IconUp } from '@arco-design/web-vue/es/icon'
import { issueApi, sprintApi, projectApi } from '@/api'
import { ERROR_CODES } from '@/api/error-codes'
import { useProjectStore } from '@/stores/project'
import { useProjectList } from '@/composables/useProjectList'
import { usePermission } from '@/composables/usePermission'
import { localizeIssueType, localizePriority } from '@/utils/fieldLabels'
import IssueCreatePanel from '@/views/issue/IssueCreatePanel.vue'
import { useDrafts } from '@/views/issue/composables/useDrafts'
import type { IssueVO, SprintVO, ProjectMemberVO, SprintVelocityVO, CreationPreviewVO, SprintOverlapWarning } from '@/api/types'

const router = useRouter()
const projectStore = useProjectStore()
const { saveDraft: saveIssueDraft } = useDrafts()

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})

const { projects, projectLoadState, loadProjects } = useProjectList()
const { canCreateSprint, canEditSprint } = usePermission(() => selectedProject.value)

// ===== Data =====
const backlogIssues = ref<IssueVO[]>([])
const sprintIssuesMap = ref<Map<string, IssueVO[]>>(new Map())
const sprints = ref<SprintVO[]>([])
const projectMembers = ref<ProjectMemberVO[]>([])
const backlogLoading = ref(false)
const sprintsLoading = ref(false)

// ===== Velocity Data =====
const velocityData = ref<SprintVelocityVO | null>(null)

// ===== Filters =====
const backlogFiltersExpanded = ref(false)
const backlogSearch = ref('')
const backlogFilterType = ref<string | undefined>(undefined)
const backlogFilterPriority = ref<string | undefined>(undefined)
const backlogFilterAssignee = ref<string | undefined>(undefined)

// ===== Selection =====
const selectedIds = ref<Set<string>>(new Set())
const lastClickedId = ref<string | null>(null)
const lastClickedSource = ref<string | null>(null)

// ===== Drag & Drop =====
const draggingIds = ref<Set<string>>(new Set())
const dragSourcePanel = ref<string | null>(null)
const dropTargetSprintId = ref<string | null>(null)
const backlogDropHighlight = ref(false)

// ===== Assign Popover =====
const assignPopoverVisible = ref(false)

// ===== Quick Add =====
const backlogQuickAddRef = ref<HTMLInputElement | null>(null)
const backlogQuickAddTitle = ref('')
const backlogQuickAddFocused = ref(false)
const backlogQuickAddLoading = ref(false)
const sprintQuickAddTitles = ref<Record<string, string>>({})
const sprintQuickAddLoadingId = ref<string | null>(null)

// ===== Create Panel =====
const showCreatePanel = ref(false)
const createPanelSprintId = ref<string | null>(null)
const createPanelLockSprint = ref(false)

// ===== Sprint Creation Modal =====
const showSprintCreate = ref(false)
const sprintCreating = ref(false)
const sprintCreationPreview = ref<CreationPreviewVO | null>(null)
const sprintCreateForm = reactive({
  name: '',
  goal: '',
  startDate: '',
  endDate: '',
  moveUnresolvedIssues: false,
  setAsDefault: false
})

let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

// ===== Computed =====
const targetSprints = computed(() =>
  sprints.value.filter(s => s.status === 'active' || s.status === 'planned')
)

const selectedCount = computed(() => selectedIds.value.size)

// 激活的筛选条件数量（用于徽标显示）
const activeFilterCount = computed(() => {
  let count = 0
  if (backlogSearch.value) count++
  if (backlogFilterType.value) count++
  if (backlogFilterPriority.value) count++
  if (backlogFilterAssignee.value) count++
  return count
})

// ===== Methods =====

function getSprintIssues(sprintId: string): IssueVO[] {
  return sprintIssuesMap.value.get(sprintId) || []
}

function getSprintTotalHours(sprintId: string): number {
  const issues = getSprintIssues(sprintId)
  return issues.reduce((sum, issue) => sum + (issue.estimatedHours || 0), 0)
}

function formatHours(hours: number): string {
  if (hours === 0) return '0h'
  if (hours >= 1) return `${Math.round(hours * 10) / 10}h`
  return `${Math.round(hours * 60)}m`
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * 计算当前已规划工时占历史平均速率的百分比（用于进度条宽度）
 * 超过 120% 时固定显示 100%（避免进度条溢出容器）
 */
function getProgressWidth(sprintId: string): number {
  const totalHours = getSprintTotalHours(sprintId)
  const avgVelocity = velocityData.value?.averageVelocity || 0
  if (avgVelocity <= 0 || totalHours <= 0) return 0
  // 以 120% 的历史均速为 100% 宽度（留出超载红色区域）
  const ratio = totalHours / (avgVelocity * 1.2)
  return Math.min(Math.round(ratio * 100), 100)
}

/**
 * 根据已规划工时 vs 历史速率，返回进度条颜色类名
 * - 0% ~ 80%：蓝色（充足）
 * - 80% ~ 100%：橙色（合理）
 * - > 100%（即 > 历史均速 * 1.2）：红色（可能超载）
 */
function getProgressBarClass(sprintId: string): string {
  const totalHours = getSprintTotalHours(sprintId)
  const avgVelocity = velocityData.value?.averageVelocity || 0
  if (avgVelocity <= 0) return 'progress-normal'
  const ratio = totalHours / avgVelocity
  if (ratio > 1.2) return 'progress-overloaded'
  if (ratio > 0.8) return 'progress-healthy'
  return 'progress-normal'
}

/**
 * 根据工作量负荷返回 badge 类名和文本
 */
function getLoadBadgeClass(sprintId: string): string {
  const totalHours = getSprintTotalHours(sprintId)
  const avgVelocity = velocityData.value?.averageVelocity || 0
  if (avgVelocity <= 0) return 'load-badge-neutral'
  const ratio = totalHours / avgVelocity
  if (ratio > 1.2) return 'load-badge-overloaded'
  if (ratio > 0.8) return 'load-badge-healthy'
  if (totalHours === 0) return 'load-badge-neutral'
  return 'load-badge-light'
}

function getLoadLabel(sprintId: string): string {
  const totalHours = getSprintTotalHours(sprintId)
  const avgVelocity = velocityData.value?.averageVelocity || 0
  if (avgVelocity <= 0 || totalHours === 0) return '无参考'
  const ratio = totalHours / avgVelocity
  if (ratio > 1.2) return '超出建议'
  if (ratio > 0.8) return '负荷合理'
  return '负荷偏轻'
}

function getVelocityTooltip(): string {
  const avg = velocityData.value?.averageVelocity || 0
  const count = velocityData.value?.sprintCount || 0
  if (avg <= 0 || count === 0) return '暂无历史数据'
  return `基于最近 ${count} 个已完成 Sprint，平均完成 ${formatHours(avg)}/Sprint`
}

function priorityIcon(priority?: string): string {
  const map: Record<string, string> = { Critical: '🔴', High: '🟠', Normal: '🔵', Low: '⚪' }
  return map[priority || ''] || '🔵'
}

function clearSelection() {
  selectedIds.value = new Set()
  lastClickedId.value = null
  lastClickedSource.value = null
}

// ===== Data Loading =====

async function onProjectChange() {
  clearSelection()
  if (!selectedProject.value) return
  await Promise.all([loadBacklog(), loadSprints(), loadMembers(), loadVelocity()])
}

async function loadBacklog() {
  if (!selectedProject.value) return
  backlogLoading.value = true
  try {
    const params: Record<string, any> = {
      projectId: selectedProject.value,
      sprintId: 'none',
      hideResolved: 'true',
      page: 1,
      pageSize: 100
    }
    if (backlogSearch.value) params.keyword = backlogSearch.value
    if (backlogFilterType.value) params.issueType = backlogFilterType.value
    if (backlogFilterPriority.value) params.priority = backlogFilterPriority.value
    if (backlogFilterAssignee.value) {
      if (backlogFilterAssignee.value === 'unassigned') {
        params.assigneeId = 'none'
      } else {
        params.assigneeId = backlogFilterAssignee.value
      }
    }
    const res = await issueApi.list(params)
    backlogIssues.value = res.data?.list || []
  } catch {
    backlogIssues.value = []
    Message.error('加载 Backlog 失败')
  } finally {
    backlogLoading.value = false
  }
}

async function loadSprints() {
  if (!selectedProject.value) return
  sprintsLoading.value = true
  try {
    const res = await sprintApi.listByProject(selectedProject.value)
    sprints.value = res.data?.list || []
    // Load issues for each active/planned sprint
    await loadAllSprintIssues()
  } catch {
    sprints.value = []
  } finally {
    sprintsLoading.value = false
  }
}

async function loadAllSprintIssues() {
  const activePlanned = sprints.value.filter(s => s.status === 'active' || s.status === 'planned')
  const newMap = new Map<string, IssueVO[]>()

  await Promise.all(activePlanned.map(async (sprint) => {
    try {
      const res = await issueApi.list({
        projectId: selectedProject.value!,
        sprintId: sprint.id,
        hideResolved: 'true',
        page: 1,
        pageSize: 100
      })
      newMap.set(sprint.id, res.data?.list || [])
    } catch {
      newMap.set(sprint.id, [])
    }
  }))

  sprintIssuesMap.value = newMap
}

async function loadMembers() {
  if (!selectedProject.value) return
  try {
    const res = await projectApi.listMembers(selectedProject.value)
    projectMembers.value = res.data || []
  } catch {
    projectMembers.value = []
  }
}

async function loadVelocity() {
  if (!selectedProject.value) return
  try {
    const res = await sprintApi.velocity(selectedProject.value, 5)
    velocityData.value = res.data || null
  } catch {
    velocityData.value = null
  }
}

function onBacklogSearchInput() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => loadBacklog(), 350)
}

// ===== Selection Logic =====

function onCardClick(event: MouseEvent, issue: IssueVO, source: string) {
  const ids = selectedIds.value
  if (event.ctrlKey || event.metaKey) {
    // Toggle individual selection
    const newSet = new Set(ids)
    if (newSet.has(issue.id)) {
      newSet.delete(issue.id)
    } else {
      newSet.add(issue.id)
    }
    selectedIds.value = newSet
    lastClickedId.value = issue.id
    lastClickedSource.value = source
  } else if (event.shiftKey && lastClickedId.value && lastClickedSource.value === source) {
    // Range selection within same panel
    const list = source === 'backlog' ? backlogIssues.value : getSprintIssues(source)
    const lastIdx = list.findIndex(i => i.id === lastClickedId.value)
    const curIdx = list.findIndex(i => i.id === issue.id)
    if (lastIdx >= 0 && curIdx >= 0) {
      const start = Math.min(lastIdx, curIdx)
      const end = Math.max(lastIdx, curIdx)
      const newSet = new Set(ids)
      for (let i = start; i <= end; i++) {
        newSet.add(list[i].id)
      }
      selectedIds.value = newSet
    }
  } else {
    // Single select (deselect all others)
    selectedIds.value = new Set([issue.id])
    lastClickedId.value = issue.id
    lastClickedSource.value = source
  }
}

// ===== Drag & Drop =====

function onDragStart(event: DragEvent, issue: IssueVO, source: string) {
  if (!event.dataTransfer) return

  // If dragging a selected item, drag all selected items
  let idsToMove: Set<string>
  if (selectedIds.value.has(issue.id) && selectedIds.value.size > 1) {
    idsToMove = new Set(selectedIds.value)
  } else {
    idsToMove = new Set([issue.id])
    selectedIds.value = new Set([issue.id])
  }

  draggingIds.value = idsToMove
  dragSourcePanel.value = source

  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', JSON.stringify({
    issueIds: Array.from(idsToMove),
    source
  }))

  // Custom drag image showing count
  if (idsToMove.size > 1) {
    const el = document.createElement('div')
    el.className = 'drag-ghost'
    el.textContent = `${idsToMove.size} 个工单`
    el.style.cssText = 'position:fixed;top:-100px;left:-100px;padding:6px 12px;background:var(--color-primary-light-2,#4080ff);color:#fff;border-radius:4px;font-size:12px;font-weight:500;z-index:9999;'
    document.body.appendChild(el)
    event.dataTransfer.setDragImage(el, 0, 0)
    setTimeout(() => document.body.removeChild(el), 0)
  }
}

function onDragEnd() {
  draggingIds.value = new Set()
  dragSourcePanel.value = null
  dropTargetSprintId.value = null
  backlogDropHighlight.value = false
}

function onSprintDragOver(event: DragEvent, sprintId: string) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dropTargetSprintId.value = sprintId
}

function onSprintDragLeave() {
  dropTargetSprintId.value = null
}

function onBacklogDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  backlogDropHighlight.value = true
}

function onBacklogDragLeave() {
  backlogDropHighlight.value = false
}

async function onSprintDrop(event: DragEvent, targetSprintId: string) {
  event.preventDefault()
  dropTargetSprintId.value = null

  const data = event.dataTransfer?.getData('text/plain')
  if (!data) return

  try {
    const { issueIds, source } = JSON.parse(data)
    if (source === targetSprintId) return // Dropped in same panel

    await moveIssuesToSprint(issueIds, targetSprintId)
  } catch (e: any) {
    Message.error(e.message || '移动失败')
  }
}

async function onBacklogDrop(event: DragEvent) {
  event.preventDefault()
  backlogDropHighlight.value = false

  const data = event.dataTransfer?.getData('text/plain')
  if (!data) return

  try {
    const { issueIds, source } = JSON.parse(data)
    if (source === 'backlog') return // Already in backlog

    await moveIssuesToBacklog(issueIds)
  } catch (e: any) {
    Message.error(e.message || '移动失败')
  }
}

async function moveIssuesToSprint(issueIds: string[], targetSprintId: string) {
  try {
    await issueApi.batch({
      operation: 'sprint',
      issueIds,
      sprintId: targetSprintId,
      silent: true
    })
    Message.success(`已将 ${issueIds.length} 个工单移入 Sprint`)
    clearSelection()
    // Refresh data
    await Promise.all([loadBacklog(), loadAllSprintIssues()])
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移动失败')
  }
}

async function moveIssuesToBacklog(issueIds: string[]) {
  try {
    await issueApi.batch({
      operation: 'sprint',
      issueIds,
      sprintId: '0', // 0 means remove sprint
      silent: true
    })
    Message.success(`已将 ${issueIds.length} 个工单移回 Backlog`)
    clearSelection()
    await Promise.all([loadBacklog(), loadAllSprintIssues()])
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移动失败')
  }
}

// ===== Context Menu =====
const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  targetIssueId: null as string | null
})

function onCardContextMenu(event: MouseEvent, issue: IssueVO, source: string) {
  // If the right-clicked card is not already selected, select only it
  if (!selectedIds.value.has(issue.id)) {
    selectedIds.value = new Set([issue.id])
    lastClickedId.value = issue.id
    lastClickedSource.value = source
  }

  contextMenu.targetIssueId = issue.id
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.visible = true

  // Close on next click anywhere
  const closeHandler = () => {
    contextMenu.visible = false
    document.removeEventListener('click', closeHandler)
    document.removeEventListener('contextmenu', closeHandler)
  }
  setTimeout(() => {
    document.addEventListener('click', closeHandler)
    document.addEventListener('contextmenu', closeHandler)
  }, 0)
}

function contextAssign(userId: string | null) {
  contextMenu.visible = false
  const ids = Array.from(selectedIds.value)
  if (ids.length === 0 && contextMenu.targetIssueId) {
    ids.push(contextMenu.targetIssueId)
  }
  if (ids.length > 0) {
    performBatchAssign(ids, userId)
  }
}

// ===== Batch Assign =====

function batchAssign(userId: string | null) {
  assignPopoverVisible.value = false
  const ids = Array.from(selectedIds.value)
  if (ids.length === 0) return
  performBatchAssign(ids, userId)
}

async function performBatchAssign(issueIds: string[], userId: string | null) {
  try {
    await issueApi.batch({
      operation: 'assign',
      issueIds,
      assigneeId: userId || '0', // '0' means unassign
      silent: true
    })
    const assigneeName = userId
      ? projectMembers.value.find(m => m.userId === userId)?.displayName || ''
      : ''
    const actionText = userId ? `分配给 ${assigneeName}` : '取消分配'
    Message.success(`已将 ${issueIds.length} 个工单${actionText}`)

    // Optimistically update assignee on cards
    updateAssigneeOnCards(issueIds, userId, assigneeName)
    clearSelection()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '分配失败')
  }
}

function updateAssigneeOnCards(issueIds: string[], userId: string | null, assigneeName: string) {
  const idSet = new Set(issueIds)

  // Update backlog issues
  backlogIssues.value = backlogIssues.value.map(issue => {
    if (idSet.has(issue.id)) {
      return { ...issue, assigneeId: userId || undefined, assigneeName: assigneeName || undefined }
    }
    return issue
  })

  // Update sprint issues
  const newMap = new Map<string, IssueVO[]>()
  for (const [sprintId, issues] of sprintIssuesMap.value.entries()) {
    newMap.set(sprintId, issues.map(issue => {
      if (idSet.has(issue.id)) {
        return { ...issue, assigneeId: userId || undefined, assigneeName: assigneeName || undefined }
      }
      return issue
    }))
  }
  sprintIssuesMap.value = newMap
}

function goToSprintPage() {
  router.push('/sprints')
}

// ===== Sprint Creation =====

async function openSprintCreateModal() {
  sprintCreateForm.name = ''
  sprintCreateForm.goal = ''
  sprintCreateForm.startDate = ''
  sprintCreateForm.endDate = ''
  sprintCreateForm.moveUnresolvedIssues = false
  sprintCreateForm.setAsDefault = false
  sprintCreationPreview.value = null
  showSprintCreate.value = true

  if (selectedProject.value) {
    try {
      const res = await sprintApi.creationPreview(selectedProject.value)
      sprintCreationPreview.value = res.data
    } catch {
      sprintCreationPreview.value = null
    }
  }
}

async function handleSprintCreate() {
  if (!sprintCreateForm.name.trim()) {
    Message.warning('请输入迭代名称')
    return
  }
  if (sprintCreateForm.startDate && sprintCreateForm.endDate && sprintCreateForm.startDate >= sprintCreateForm.endDate) {
    Message.warning('开始日期必须早于结束日期')
    return
  }
  await doSprintCreate(false)
}

async function doSprintCreate(confirmOverlap: boolean) {
  sprintCreating.value = true
  try {
    await sprintApi.create(selectedProject.value!, {
      name: sprintCreateForm.name.trim(),
      goal: sprintCreateForm.goal || undefined,
      startDate: sprintCreateForm.startDate || undefined,
      endDate: sprintCreateForm.endDate || undefined,
      moveUnresolvedIssues: sprintCreateForm.moveUnresolvedIssues || undefined,
      setAsDefault: sprintCreateForm.setAsDefault || undefined,
      confirmOverlap: confirmOverlap || undefined
    })
    Message.success('迭代创建成功')
    showSprintCreate.value = false
    // Refresh sprints so new one appears in the panel
    await loadSprints()
  } catch (e: any) {
    const code = e.response?.data?.code
    if (code === ERROR_CODES.SPRINT_DATE_OVERLAP) {
      const warning = e.response.data.data as SprintOverlapWarning
      const overlapNames = warning.overlappingSprints.map(s => s.name).join('、')
      Modal.warning({
        title: '日期重叠提醒',
        content: `新迭代的日期与以下迭代重叠：${overlapNames}。确定继续创建吗？`,
        okText: '继续创建',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => doSprintCreate(true)
      })
    } else {
      Message.error(e.response?.data?.message || '创建失败')
    }
  } finally {
    sprintCreating.value = false
  }
}

/**
 * 跳转到工单列表，自动应用当前 Sprint 过滤器
 */
function viewSprintIssues(sprint: SprintVO) {
  const currentProject = projects.value.find(p => p.id === selectedProject.value)
  const query: Record<string, string> = { sprint: sprint.id, label: sprint.name }
  if (currentProject) {
    query.project = currentProject.key
  }
  router.push({ path: '/issues', query })
}

// ===== Create Issue =====

function openCreateForBacklog() {
  createPanelSprintId.value = null
  createPanelLockSprint.value = true
  showCreatePanel.value = true
}

function openCreateForSprint(sprintId: string) {
  createPanelSprintId.value = sprintId
  createPanelLockSprint.value = true
  showCreatePanel.value = true
}

function onIssueCreated() {
  // Reload all data after issue creation
  loadBacklog()
  loadAllSprintIssues()
}

/**
 * 用户点击创建面板的「全屏」按钮，跳转到全屏创建页面
 */
function onCreatePanelExpand(formData: any) {
  showCreatePanel.value = false
  if (formData && (formData.title?.trim() || formData.description?.trim())) {
    const draftId = saveIssueDraft(formData)
    if (draftId) {
      router.push({ name: 'IssueCreate', query: { draftId } })
      return
    }
  }
  router.push({ name: 'IssueCreate' })
}

async function quickCreateForBacklog() {
  const title = backlogQuickAddTitle.value.trim()
  if (!title || !selectedProject.value) return

  backlogQuickAddLoading.value = true
  try {
    await issueApi.create({
      projectId: selectedProject.value,
      title,
      issueType: 'Task',
      priority: 'Normal'
    })
    Message.success('工单已创建到 Backlog')
    backlogQuickAddTitle.value = ''
    await loadBacklog()
  } catch (e: any) {
    const errorMsg = e.response?.data?.message || ''
    // If validation fails (required custom fields etc), fall back to full create panel
    if (e.response?.status === 400 && errorMsg.includes('字段')) {
      Message.warning('该项目有必填字段，已打开完整创建表单')
      createPanelSprintId.value = null
      createPanelLockSprint.value = true
      showCreatePanel.value = true
      // Keep the title so user doesn't have to retype
    } else {
      Message.error(errorMsg || '创建失败')
    }
  } finally {
    backlogQuickAddLoading.value = false
  }
}

async function quickCreateForSprint(sprintId: string) {
  const title = (sprintQuickAddTitles.value[sprintId] || '').trim()
  if (!title || !selectedProject.value) return

  sprintQuickAddLoadingId.value = sprintId
  try {
    await issueApi.create({
      projectId: selectedProject.value,
      title,
      issueType: 'Task',
      priority: 'Normal',
      sprintId
    })
    Message.success('工单已创建到 Sprint')
    sprintQuickAddTitles.value[sprintId] = ''
    await loadAllSprintIssues()
  } catch (e: any) {
    const errorMsg = e.response?.data?.message || ''
    if (e.response?.status === 400 && errorMsg.includes('字段')) {
      Message.warning('该项目有必填字段，已打开完整创建表单')
      createPanelSprintId.value = sprintId
      createPanelLockSprint.value = true
      showCreatePanel.value = true
    } else {
      Message.error(errorMsg || '创建失败')
    }
  } finally {
    sprintQuickAddLoadingId.value = null
  }
}

// ===== Sprint Goal Inline Edit =====
const editingGoalSprintId = ref<string | null>(null)
const editingGoalValue = ref('')
const goalTextareaRef = ref<HTMLTextAreaElement | null>(null)
const savingGoalId = ref<string | null>(null)

function startGoalEdit(sprint: SprintVO) {
  editingGoalSprintId.value = sprint.id
  editingGoalValue.value = sprint.goal || ''
  nextTick(() => {
    goalTextareaRef.value?.focus()
    goalTextareaRef.value?.select()
  })
}

function cancelGoalEdit() {
  editingGoalSprintId.value = null
  editingGoalValue.value = ''
}

async function saveGoal(sprintId: string) {
  if (savingGoalId.value === sprintId) return // prevent duplicate saves on blur+enter
  const sprint = sprints.value.find(s => s.id === sprintId)
  if (!sprint) { cancelGoalEdit(); return }

  const newGoal = editingGoalValue.value.trim()
  // No change — just cancel
  if (newGoal === (sprint.goal || '')) {
    cancelGoalEdit()
    return
  }

  savingGoalId.value = sprintId
  cancelGoalEdit()

  try {
    await sprintApi.update(sprintId, { goal: newGoal })
    // Update local sprint goal
    const idx = sprints.value.findIndex(s => s.id === sprintId)
    if (idx >= 0) {
      sprints.value[idx] = { ...sprints.value[idx], goal: newGoal || undefined }
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存目标失败')
    // Restore edit mode on failure
    const s = sprints.value.find(s => s.id === sprintId)
    if (s) startGoalEdit(s)
  } finally {
    savingGoalId.value = null
  }
}


watch(selectedProject, (val) => {
  if (val) onProjectChange()
}, { immediate: false })

// On mount, load projects and auto-select if available
import { onMounted } from 'vue'
onMounted(async () => {
  await loadProjects()
  if (selectedProject.value) {
    onProjectChange()
  }
})
</script>

<style scoped>
.sprint-planning-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== Toolbar ===== */
.planning-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0;
}
.selection-indicator {
  font-size: 12px;
  color: rgb(var(--primary-6));
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(var(--primary-6), 0.08);
  border-radius: 4px;
}

/* ===== Main Content ===== */
.planning-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== Backlog Panel ===== */
.backlog-panel {
  width: 300px;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-1);
  overflow: hidden;
}

/* ===== Backlog Filters Toggle ===== */
.backlog-filters-wrapper {
  flex-shrink: 0;
  border-bottom: 1px solid var(--color-border);
}
.backlog-filters-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  cursor: pointer;
  user-select: none;
  color: var(--tf-text-secondary);
  font-size: 12px;
  transition: background 0.15s;
}
.backlog-filters-toggle:hover {
  background: var(--tf-bg-hover);
}
.filter-toggle-icon {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}
.filter-toggle-text {
  flex: 1;
}
.filter-active-badge {
  background: var(--color-primary-light-1);
  color: var(--color-primary-6);
  font-size: 10px;
  font-weight: 600;
  padding: 1px 5px;
  border-radius: 8px;
  min-width: 16px;
  text-align: center;
}
.filter-toggle-arrow {
  font-size: 10px;
  color: var(--tf-text-tertiary);
  transition: transform 0.15s;
}
.backlog-filters {
  padding: 8px 12px;
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}
.filter-row {
  display: flex;
  gap: 4px;
}

/* ===== Sprint Panels Area ===== */
.sprint-panels {
  flex: 1;
  display: flex;
  overflow-x: auto;
  padding: 0;
  gap: 0;
}
.sprint-column {
  min-width: 300px;
  max-width: 400px;
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  overflow: hidden;
}
.sprint-column:last-child {
  border-right: none;
}

/* ===== Shared Panel Styles ===== */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  flex-shrink: 0;
}
.panel-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel-header-right {
  display: flex;
  align-items: center;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0;
}
.panel-count {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 2px 6px;
  border-radius: 3px;
}
.panel-count-total {
  font-size: 11px;
  color: var(--color-text-4);
  margin-left: 2px;
  cursor: help;
}
.sprint-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 500;
}
.sprint-badge.active {
  background: rgba(var(--primary-6), 0.1);
  color: rgb(var(--primary-6));
}
.sprint-badge.planned {
  background: var(--color-fill-2);
  color: var(--color-text-3);
}
.sprint-dates {
  font-size: 11px;
  color: var(--color-text-3);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 8px;
  transition: background 0.15s;
  border: 2px solid transparent;
  border-radius: 4px;
  margin: 0 4px 4px;
}
.panel-body.drop-target {
  background: rgba(var(--primary-6), 0.04);
  border-color: rgba(var(--primary-6), 0.3);
}

.panel-spin {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.issue-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* ===== Planning Card ===== */
.planning-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 8px 10px;
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s;
  user-select: none;
}
.planning-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.planning-card:active {
  cursor: grabbing;
}
.planning-card--selected {
  border-color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.04);
  box-shadow: 0 0 0 1px rgba(var(--primary-6), 0.3);
}
.planning-card--dragging {
  opacity: 0.4;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.card-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
  font-family: monospace;
}
.card-priority {
  font-size: 10px;
}
.card-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-1);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}
.card-type {
  font-size: 10px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 1px 5px;
  border-radius: 3px;
}
.card-assignee {
  font-size: 10px;
  color: var(--color-text-2);
}
.card-estimation {
  font-size: 10px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 1px 4px;
  border-radius: 3px;
}
.sprint-total-hours {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  padding: 2px 6px;
  border-radius: 3px;
  margin-right: 8px;
}

/* ===== Sprint 工时统计条 ===== */
.sprint-hours-bar {
  padding: 0 12px 8px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 4px;
}

.sprint-hours-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.hours-stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.hours-stat-label {
  font-size: 10px;
  color: var(--color-text-3);
}

.hours-stat-value {
  font-size: 11px;
  font-weight: 500;
}

.hours-stat-value.planned {
  color: rgb(var(--primary-6));
}

.hours-stat-value.velocity {
  color: var(--color-text-2);
}

.hours-stat-divider {
  width: 1px;
  height: 12px;
  background: var(--color-border);
}

/* 负荷状态 badge */
.hours-stat-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 10px;
  font-weight: 500;
  cursor: default;
}

.load-badge-neutral {
  background: var(--color-fill-2);
  color: var(--color-text-3);
}

.load-badge-light {
  background: rgba(var(--blue-4), 0.1);
  color: rgb(var(--blue-6));
}

.load-badge-healthy {
  background: rgba(var(--green-4), 0.15);
  color: rgb(var(--green-6));
}

.load-badge-overloaded {
  background: rgba(var(--red-4), 0.15);
  color: rgb(var(--red-6));
}

/* 工时进度条 */
.hours-progress-bar {
  height: 4px;
  background: var(--color-fill-3);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 6px;
  position: relative;
}

.hours-progress-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.hours-progress-fill.progress-normal {
  background: rgb(var(--primary-6));
}

.hours-progress-fill.progress-healthy {
  background: rgb(var(--green-6));
}

.hours-progress-fill.progress-overloaded {
  background: rgb(var(--red-5));
}

/* 历史速率芯片 */
.hours-velocity-history {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.velocity-history-label {
  font-size: 10px;
  color: var(--color-text-3);
}

.velocity-history-chip {
  font-size: 10px;
  padding: 1px 6px;
  background: var(--color-fill-2);
  border-radius: 3px;
  color: var(--color-text-2);
  cursor: default;
  border: 1px solid var(--color-border);
}

/* ===== Empty States ===== */
.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  text-align: center;
}
.panel-empty--sprint {
  padding: 48px 16px;
  min-height: 200px;
}
.panel-empty--all-done {
  opacity: 0.7;
}
.panel-empty--all-done .empty-icon {
  font-size: 28px;
}
.panel-empty--all-done .empty-title {
  color: var(--color-text-2);
}
.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}
.empty-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-2);
  margin-bottom: 4px;
}
.empty-desc {
  font-size: 12px;
  color: var(--color-text-3);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: 64px 24px;
}

.no-sprints-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  text-align: center;
  padding: 64px 24px;
}

/* ===== Assignee Popover ===== */
.assignee-popover-list {
  max-height: 240px;
  overflow-y: auto;
  min-width: 140px;
}
.assignee-popover-item {
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-1);
  transition: background 0.1s;
  border-radius: 3px;
}
.assignee-popover-item:hover {
  background: var(--color-fill-2);
}
.assignee-popover-divider {
  height: 1px;
  background: var(--color-border);
  margin: 4px 0;
}

/* ===== Context Menu ===== */
.planning-context-menu {
  position: fixed;
  z-index: 9999;
  background: var(--color-bg-popup, var(--color-bg-2));
  border: 1px solid var(--color-border);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  min-width: 160px;
}
.context-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--color-text-1);
  cursor: pointer;
  position: relative;
  transition: background 0.1s;
}
.context-menu-item:hover {
  background: var(--color-fill-2);
}
.context-menu-item--submenu {
  position: relative;
}
.context-menu-arrow {
  font-size: 14px;
  color: var(--color-text-3);
}
.context-submenu {
  display: none;
  position: absolute;
  left: 100%;
  top: -4px;
  background: var(--color-bg-popup, var(--color-bg-2));
  border: 1px solid var(--color-border);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  min-width: 140px;
  max-height: 280px;
  overflow-y: auto;
}
.context-menu-item--submenu:hover > .context-submenu {
  display: block;
}
.context-menu-divider {
  height: 1px;
  background: var(--color-border);
  margin: 4px 0;
}

/* ===== Sprint Goal Section ===== */
.sprint-goal-section {
  padding: 0 12px 8px;
  flex-shrink: 0;
}

.sprint-goal-display {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-2);
  background: var(--color-fill-1);
  border: 1px solid transparent;
  transition: border-color 0.15s, background 0.15s;
  min-height: 32px;
}

.sprint-goal-display.sprint-goal-editable {
  cursor: pointer;
}

.sprint-goal-display.sprint-goal-editable:hover {
  border-color: var(--color-border);
  background: var(--color-fill-2);
}

.sprint-goal-display.sprint-goal-empty {
  color: var(--color-text-4);
  font-style: italic;
}

.sprint-goal-icon {
  font-size: 11px;
  flex-shrink: 0;
  margin-top: 1px;
}

.sprint-goal-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  white-space: pre-wrap;
  word-break: break-word;
}

.sprint-goal-edit-hint {
  font-size: 10px;
  color: var(--color-text-4);
  opacity: 0;
  flex-shrink: 0;
  transition: opacity 0.15s;
  white-space: nowrap;
}

.sprint-goal-display.sprint-goal-editable:hover .sprint-goal-edit-hint {
  opacity: 1;
}

.sprint-goal-textarea {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid rgb(var(--primary-6));
  border-radius: 4px;
  background: var(--color-bg-2);
  color: var(--color-text-1);
  font-size: 12px;
  line-height: 1.5;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
  box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.15);
  transition: border-color 0.15s;
}

.sprint-goal-textarea::placeholder {
  color: var(--color-text-4);
}

/* ===== Create Button ===== */
.panel-create-btn,
.panel-view-issues-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  transition: all 0.15s;
  font-size: 14px;
}
.panel-create-btn:hover,
.panel-view-issues-btn:hover {
  background: var(--color-fill-3);
  color: rgb(var(--primary-6));
}
.panel-create-btn:active,
.panel-view-issues-btn:active {
  background: var(--color-fill-4);
}
.panel-header-right {
  gap: 4px;
}

/* ===== Quick Add ===== */
.quick-add-row {
  display: flex;
  align-items: center;
  padding: 6px 4px 4px;
  flex-shrink: 0;
  position: relative;
}
.quick-add-input {
  flex: 1;
  height: 30px;
  padding: 0 8px;
  border: 1px dashed var(--color-border);
  border-radius: 4px;
  background: transparent;
  font-size: 12px;
  color: var(--color-text-1);
  outline: none;
  transition: border-color 0.15s, background 0.15s;
}
.quick-add-input::placeholder {
  color: var(--color-text-4);
}
.quick-add-input:focus {
  border-color: rgb(var(--primary-6));
  border-style: solid;
  background: var(--color-bg-2);
}
.quick-add-spinner {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
}

/* ===== No Sprints Actions ===== */
.no-sprints-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

/* ===== Sprint Create Modal Options ===== */
.create-options-section {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
.create-option-item {
  margin-bottom: 12px;
}
.create-option-item .option-label {
  font-size: 13px;
  font-weight: 500;
}
.create-option-item .option-desc {
  display: block;
  font-size: 12px;
  color: var(--color-text-3);
  margin-top: 2px;
  padding-left: 22px;
}
</style>
