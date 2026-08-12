// @ts-nocheck
// Kanban Board Implementation — full business logic orchestration.
// Public API is in useKanbanBoard.ts; types are in types.ts.
import { ref, shallowRef, computed, watch, onMounted, onUnmounted, h, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Message, Modal, Notification } from '@arco-design/web-vue'
import { issueApi, sprintApi, boardApi } from '@/api'
import type { IssueVO, IssueStatusVO, SprintVO, BoardColumnVO, BoardCardConfigVO, BoardColumnMergeGroupVO, BoardCardVO, R, TransitStatusResultVO, DeletionPreviewVO } from '@/api/types'
import { ERROR_CODES } from '@/api/error-codes'
import { useProjectStore } from '@/stores/project'
import { useAuthStore } from '@/stores/auth'
import { usePermission } from '@/composables/usePermission'
import { useNavBadge } from '@/composables/useNavBadge'
import { useSelection } from '@/composables/useSelection'
import { useBatchOps } from '@/composables/useBatchOps'
import { useBoardFullscreen } from './useBoardFullscreen'
import { useBoardKeyboard } from './useBoardKeyboard'
import { useBoardFilter } from './useBoardFilter'
import { useBoardDrag, UNDO_TIMEOUT } from './useBoardDrag'
import type { UndoEntry } from './useBoardDrag'
import { useBoardData } from './useBoardData'
import type { BoardIssue, SwimlaneGroupBy, CardSize, EffectiveColumn, SwimlaneRow } from './types'
import { useManualOrder } from '@/composables/useManualOrder'
import { extractVersion, showActionFeedback } from '@/utils/transition'
import { getDueDateInfo } from '@/utils/dueDate'
import { useIssueProjectSubscription } from '@/composables/useWebSocket'
import type { IssueRealtimeEvent } from '@/composables/useWebSocket'

export function useKanbanBoardImpl() {

const router = useRouter()
const route = useRoute()
const projectStore = useProjectStore()

// ===== Card Size 控制 =====
const CARD_SIZE_KEY = 'tf_kanban_card_size'
const cardSize = ref<CardSize>((localStorage.getItem(CARD_SIZE_KEY) as CardSize) || 'M')
const cardSizeOptions = [
  { value: 'S' as const, label: 'S' },
  { value: 'M' as const, label: 'M' },
  { value: 'L' as const, label: 'L' },
  { value: 'XL' as const, label: 'XL' }
]

function setCardSize(size: CardSize) {
  cardSize.value = size
  localStorage.setItem(CARD_SIZE_KEY, size)
}

// ===== TV 模式（全屏大屏显示） =====
const { isTvMode, toggleTvMode } = useBoardFullscreen(cardSize, CARD_SIZE_KEY)

// ===== 未解决/未匹配列工单（Orphan Issues） =====
const showOrphanPanel = ref(false)

/** 不属于看板任何可见列的工单 */
const orphanIssues = computed(() => {
  if (!selectedProject.value || visibleStatuses.value.length === 0) return []
  const visibleStatusIds = new Set(visibleStatuses.value.map(s => s.id))
  return issues.value.filter(i => {
    if (boardColumnField.value === 'priority') {
      return !visibleStatusIds.has(i.priority || 'Normal')
    }
    return !visibleStatusIds.has(i.statusId)
  })
})

/** 未匹配列的工单数量 */
const orphanIssueCount = computed(() => orphanIssues.value.length)

/** 构建「在工单列表中打开」的 URL（含项目和状态过滤参数）
 * 支持普通列（单状态）和合并列（多状态，逗号分隔）。
 * 使用 router.resolve 保证路径与当前前端路由配置一致。
 */
function buildOpenInListUrl(col: EffectiveColumn): string {
  const query: Record<string, string> = {}
  // 添加项目过滤
  if (currentProjectKey.value) {
    query.project = currentProjectKey.value
  }
  // 添加状态过滤：普通列用 statusId，合并列用逗号分隔的多个 statusId
  if (boardColumnField.value === 'status') {
    // 状态模式：用 statusId 参数（IssueListView 支持逗号分隔多值）
    query.statusId = col.statusIds.join(',')
  } else if (boardColumnField.value === 'priority') {
    // 优先级模式：col.id 实际是优先级值（Critical/High/Normal/Low）
    query.priority = col.id
  }
  const resolved = router.resolve({ name: 'Issues', query })
  return resolved.href
}

/** 获取负责人姓名首字母/缩写 */
function getInitials(name: string): string {
  if (!name) return '?'
  // CJK: return last 1-2 characters (family name typically)
  const isCJK = /[\u4e00-\u9fff\u3400-\u4dbf]/.test(name)
  if (isCJK) {
    return name.length <= 2 ? name : name.slice(0, 2)
  }
  // Latin: first letter of first + last words
  const parts = name.trim().split(/\s+/)
  if (parts.length === 1) return parts[0][0].toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

/**
 * 卡片上"Set assignee"按钮的点击处理：乐观更新 + API 保存。
 * 阻止事件冒泡，避免打开工单预览面板。
 */
async function onCardSetAssignee(userId: string, issue: BoardIssue) {
  if (!userId) return
  const member = projectMembers.value.find(m => m.userId === userId)
  if (!member) return

  // 乐观更新：patchIssue 替换数组引用触发 shallowRef 视图更新
  const oldAssigneeId = issue.assigneeId
  const oldAssigneeName = issue.assigneeName
  patchIssue(issue.id, { assigneeId: userId, assigneeName: member.displayName })

  try {
    await issueApi.update(issue.id, { assigneeId: userId })
    patchIssue(issue.id, { version: (issue.version || 0) + 1 })
    Message.success(`${issue.issueKey} 负责人已设置为「${member.displayName}」`)
  } catch (e: any) {
    // 回滚
    patchIssue(issue.id, { assigneeId: oldAssigneeId, assigneeName: oldAssigneeName })
    Message.error(e.response?.data?.message || '设置负责人失败')
  }
}

/** 检查卡片是否有可见的自定义字段（仅考虑在 visibleFields 中配置的 cf.{id} 字段） */
function hasVisibleCustomFields(issue: BoardIssue): boolean {
  if (!issue.customFieldDetails || issue.customFieldDetails.length === 0) return false
  // 检查 visibleFields 中是否有任何 cf.{id} 配置
  const hasCfConfig = cardConfig.value.visibleFields.some(f => f.startsWith('cf.'))
  if (!hasCfConfig) return false
  // 检查此 issue 是否有匹配的自定义字段值
  return getVisibleCustomFieldDetails(issue).length > 0
}

/** 获取卡片可见的自定义字段详情（仅展示在 visibleFields 中配置的 cf.{id} 字段） */
function getVisibleCustomFieldDetails(issue: BoardIssue) {
  if (!issue.customFieldDetails) return []

  // 从 visibleFields 中提取已选的自定义字段 ID
  const selectedCfIds = new Set(
    cardConfig.value.visibleFields
      .filter(f => f.startsWith('cf.'))
      .map(f => f.substring(3))
  )

  // 如果没有选择任何自定义字段，不展示
  if (selectedCfIds.size === 0) return []

  // 只返回管理员在卡片设置中选择的自定义字段
  const maxFields = cardSize.value === 'L' ? 4 : 2
  return issue.customFieldDetails
    .filter(d => selectedCfIds.has(d.customFieldId))
    .slice(0, maxFields)
}

/** 获取卡片可见的标签列表（根据卡片尺寸限制显示数量） */
function getVisibleTags(issue: BoardIssue): Array<{ id: string; name: string; color?: string }> {
  const tags = issue.tags
  if (!tags || tags.length === 0) return []
  // M 尺寸最多显示 2 个标签，L/XL 最多 4 个
  const maxTags = cardSize.value === 'M' ? 2 : 4
  return tags.slice(0, maxTags)
}

/** 判断卡片上是否应展示某个字段（基于 cardConfig） */
function isCardFieldVisible(field: string): boolean {
  return cardConfig.value.visibleFields.includes(field)
}

/** 获取字段在卡片上的显示模式（full_name 或 initial） */
function getCardFieldDisplayMode(field: string): 'full_name' | 'initial' {
  const modes = cardConfig.value.fieldDisplayModes
  return (modes && modes[field]) || 'full_name'
}

/** 预定义项目颜色调色板（用于"按项目着色"方案） */
const PROJECT_COLOR_PALETTE = [
  '#0ea5e9', // 天蓝
  '#10b981', // 翠绿
  '#f59e0b', // 琥珀
  '#8b5cf6', // 紫色
  '#ef4444', // 红色
  '#f97316', // 橙色
  '#14b8a6', // 青色
  '#ec4899', // 粉色
  '#6366f1', // 靛蓝
  '#84cc16', // 黄绿
]

/**
 * 根据 projectId 哈希选取调色板中的颜色，确保同一项目始终显示同一颜色。
 * 使用简单字符串哈希（djb2 变体）取模颜色数量。
 */
function getProjectColor(projectId: string): string {
  if (!projectId) return PROJECT_COLOR_PALETTE[0]
  let hash = 5381
  for (let i = 0; i < projectId.length; i++) {
    hash = ((hash << 5) + hash) + projectId.charCodeAt(i)
    hash = hash & hash // 转为 32 位整数
  }
  const index = Math.abs(hash) % PROJECT_COLOR_PALETTE.length
  return PROJECT_COLOR_PALETTE[index]
}

/** 获取卡片的颜色方案 CSS class */
function getCardColorClass(issue: BoardIssue): string {
  const scheme = cardConfig.value.colorScheme
  if (scheme === 'none') return ''
  if (scheme === 'priority') {
    const p = (issue.priority || 'Normal').toLowerCase()
    return `kanban-card--color-priority-${p}`
  }
  if (scheme === 'type') {
    const t = (issue.issueType || 'task').toLowerCase()
    return `kanban-card--color-type-${t}`
  }
  if (scheme === 'project') {
    return `kanban-card--color-project`
  }
  return ''
}

/**
 * 获取卡片"按项目着色"时的内联样式（动态颜色，无法用静态 CSS class 实现）。
 * 仅当 colorScheme === 'project' 时返回有效样式，其他情况返回空对象。
 */
function getCardProjectColorStyle(issue: BoardIssue): Record<string, string> {
  if (cardConfig.value.colorScheme !== 'project') return {}
  const color = getProjectColor(issue.projectId || '')
  return { borderLeftColor: color }
}

/** 获取卡片截止日期的状态 class */
function getCardDueDateClass(issue: BoardIssue): string {
  if (!issue.dueDate) return ''
  const isClosed = isIssueResolved(issue.statusId)
  const info = getDueDateInfo(issue.dueDate, isClosed)
  if (info.status === 'overdue') return 'card-meta-tag--overdue'
  if (info.status === 'due-soon') return 'card-meta-tag--due-soon'
  return ''
}

/** 获取卡片截止日期的 tooltip */
function getCardDueDateTooltip(issue: BoardIssue): string {
  if (!issue.dueDate) return ''
  const isClosed = isIssueResolved(issue.statusId)
  const info = getDueDateInfo(issue.dueDate, isClosed)
  return info.tooltip
}

/** 判断工单是否已关闭（用于截止日期颜色判断——已关闭的工单不显示逾期警告） */
function isIssueResolved(statusId: string): boolean {
  const config = allColumnConfigs.value.find(c => c.statusId === statusId)
  if (config) return config.statusCategory === 'done' || config.statusCategory === 'cancelled'
  const status = statuses.value.find(s => s.id === statusId)
  return status?.isClosed === true
}

/** 根据 sprintId 获取 Sprint 名称 */
function getSprintName(sprintId: string): string {
  const sprint = sprints.value.find(s => s.id === sprintId)
  return sprint?.name || ''
}

// ===== Swimlane 类型（提前声明供 URL 状态恢复使用） =====
const SWIMLANE_STORAGE_KEY = 'tf_kanban_swimlane'

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})

// 权限控制
const { canChangeStatus, canCreateIssue, canDeleteIssue, canEditSprint, canDeleteSprint } = usePermission(() => selectedProject.value)
const selectedSprint = ref<string | undefined>(undefined)
/** 用户是否手动清除了 Sprint 选择（区分"未选择"和"显式选全部"） */
let userExplicitlySelectedAll = false
const keyword = ref('')
const loading = ref(false)
// issues 用 shallowRef：看板数据每次 API 返回整体替换，无需递归代理每张卡片的字段
// 注意：原地字段修改（乐观更新）必须通过 patchIssue() 来替换数组引用触发视图更新
const issues = shallowRef<BoardIssue[]>([])

/**
 * 乐观更新单张卡片字段（适用于已知 patch 对象的简单场景）。
 * 替换数组引用触发 shallowRef 视图更新。
 */
function patchIssue(issueId: string, patch: Partial<BoardIssue>): BoardIssue | null {
  const idx = issues.value.findIndex(i => i.id === issueId)
  if (idx === -1) return null
  const updated = [...issues.value]
  updated[idx] = { ...updated[idx], ...patch }
  issues.value = updated
  return updated[idx]
}

/**
 * 通过回调函数对单张卡片做原地修改，修改完成后替换数组引用触发 shallowRef 视图更新。
 * 适用于复杂的乐观更新 + 回滚场景（drag 状态机、WIP 确认流程）。
 */
function mutateIssue(issueId: string, mutateFn: (issue: BoardIssue) => void): BoardIssue | null {
  const idx = issues.value.findIndex(i => i.id === issueId)
  if (idx === -1) return null
  const copy = { ...issues.value[idx] }
  mutateFn(copy)
  const updated = [...issues.value]
  updated[idx] = copy
  issues.value = updated
  return copy
}

/**
 * 在对 draggingIssue 做多次原地字段修改后，调用此函数替换数组引用触发 shallowRef 视图更新。
 * 用于 drag 状态机（onDrop/handleBacklogDrop）中多步修改完成后的一次性刷新。
 */
function flushIssues() {
  issues.value = [...issues.value]
}
// projects/loadProjects/projectLoadState are now provided by useBoardData (see below)

// ===== 负责人筛选 =====
const authStore = useAuthStore()
const ASSIGNEE_FILTER_KEY = 'tf_kanban_assignee_filter'
const assigneeFilter = ref<string | undefined>(localStorage.getItem(ASSIGNEE_FILTER_KEY) || undefined)
const projectMembers = ref<Array<{ userId: string; displayName: string }>>([])

/** 当前生效的 assigneeId（me → 当前用户数据库 ID，其他 → 原值） */
const effectiveAssigneeId = computed(() => {
  if (!assigneeFilter.value) return undefined
  if (assigneeFilter.value === 'me') return authStore.user?.userId || undefined
  return assigneeFilter.value
})

/** 切换"仅显示我的"按钮 */
function toggleMyIssues() {
  if (assigneeFilter.value === 'me') {
    assigneeFilter.value = undefined
    localStorage.removeItem(ASSIGNEE_FILTER_KEY)
  } else {
    // 确保 userId 可用（首次登录时可能尚未从 /me 接口加载）
    if (!authStore.user?.userId) {
      Message.warning('正在加载用户信息，请稍候...')
      return
    }
    assigneeFilter.value = 'me'
    localStorage.setItem(ASSIGNEE_FILTER_KEY, 'me')
  }
  syncUrlState()
  loadIssuesWithLoading()
}

/** 负责人下拉变化 */
function onAssigneeFilterChange(val: any) {
  assigneeFilter.value = val || undefined
  if (val) {
    localStorage.setItem(ASSIGNEE_FILTER_KEY, val)
  } else {
    localStorage.removeItem(ASSIGNEE_FILTER_KEY)
  }
  syncUrlState()
  loadIssuesWithLoading()
}

// loadProjectMembers is now provided by useBoardData (see below)

const currentProjectName = computed(() => {
  if (!selectedProject.value) return ''
  const p = projects.value.find(proj => proj.id === selectedProject.value)
  return p?.name || ''
})

/** 获取当前选中项目的 key（用于 URL 可读性） */
const currentProjectKey = computed(() => {
  if (!selectedProject.value) return undefined
  const p = projects.value.find(proj => proj.id === selectedProject.value)
  return p?.key
})

// ===== URL 状态同步 =====
// URL query params: ?project=DE4&sprint=<id>&group=assignee
// 用 suppressUrlSync 标志避免从 URL 恢复状态时触发 URL 写入（循环）
let suppressUrlSync = false

/**
 * 将当前看板状态同步到 URL query params。
 * 使用 router.replace 以不产生多余的浏览器历史条目。
 */
function syncUrlState() {
  if (suppressUrlSync) return
  const query: Record<string, string> = {}
  if (currentProjectKey.value) {
    query.project = currentProjectKey.value
  }
  if (selectedSprint.value) {
    query.sprint = selectedSprint.value
  }
  if (swimlaneGroupBy.value && swimlaneGroupBy.value !== 'none') {
    query.group = swimlaneGroupBy.value
  }
  if (assigneeFilter.value) {
    query.assignee = assigneeFilter.value
  }
  router.replace({ query })
}

/**
 * 从 URL query 恢复看板状态。
 * 优先级：URL params > projectStore (localStorage) > 无选择
 * @returns 是否成功从 URL 恢复了项目
 */
function restoreFromUrl(): boolean {
  const queryProject = route.query.project as string | undefined
  const querySprint = route.query.sprint as string | undefined
  const queryGroup = route.query.group as string | undefined
  const queryAssignee = route.query.assignee as string | undefined

  let restoredProject = false

  if (queryProject) {
    // 按 key 查找项目
    const project = projects.value.find(p => p.key === queryProject)
    if (project && project.id !== selectedProject.value) {
      suppressUrlSync = true
      selectedProject.value = project.id
      suppressUrlSync = false
      restoredProject = true
    } else if (project && project.id === selectedProject.value) {
      restoredProject = true
    }
  }

  if (querySprint) {
    selectedSprint.value = querySprint
  }

  if (queryGroup && ['none', 'assignee', 'priority', 'type', 'sprint', 'tag', 'parent', 'dueDate'].includes(queryGroup)) {
    swimlaneGroupBy.value = queryGroup as SwimlaneGroupBy
    localStorage.setItem(SWIMLANE_STORAGE_KEY, queryGroup)
  }

  // 恢复负责人筛选（URL 优先于 localStorage）
  if (queryAssignee) {
    assigneeFilter.value = queryAssignee
    localStorage.setItem(ASSIGNEE_FILTER_KEY, queryAssignee)
  }

  return restoredProject
}

// ===== Board Behavior 配置 =====
const boardFilterMode = ref<'all' | 'active_sprint' | 'query'>('all')
const boardFilterQuery = ref<string | null>(null)
const boardDoneRetentionDays = ref<number | null>(null)
const boardName = ref('')
/** 看板列标识字段：status=按状态分列, priority=按优先级分列 */
const boardColumnField = ref<'status' | 'priority'>('status')

/** 是否允许卡片分配到多个 Sprint（对应 Board Settings > Cards > Allow cards to be assigned to multiple sprints） */
const allowMultipleSprints = ref(false)

/** 当前用户是否有看板编辑权限（来自 board_general_config 动态计算） */
const canEditBoard = ref(false)

/** 关联项目 ID 列表（多项目看板时有值） */
const boardLinkedProjectIds = ref<string[]>([])

/** 是否为多项目看板 */
const isMultiProjectBoard = computed(() => boardLinkedProjectIds.value.length > 0)

// ===== Backlog 配置 =====
/** Backlog 视图模式（来自 Board Settings 配置）：list=平铺，tree=树形 */
const backlogViewMode = ref<'list' | 'tree'>('list')
/** 过滤 Backlog 工单的保存搜索 ID（null 时使用默认过滤） */
const backlogSavedQueryId = ref<string | null>(null)

/** 看板页面标题：优先使用管理员设置的名称，fallback 到"项目名 看板"或"看板" */
const displayBoardName = computed(() => {
  if (boardName.value) return boardName.value
  if (currentProjectName.value) return `${currentProjectName.value} 看板`
  return '看板'
})

/** 当前是否有 Board Behavior 过滤生效 */
const isBehaviorFilterActive = computed(() => {
  return boardFilterMode.value !== 'all' || boardDoneRetentionDays.value !== null || isSmartDefaultDoneRetentionActive.value
})

/** 智能默认 14 天保留是否在生效（无配置 + 无活跃 Sprint + 无手动选择 Sprint） */
const isSmartDefaultDoneRetentionActive = computed(() => {
  return boardDoneRetentionDays.value === null && !activeSprint.value && !selectedSprint.value
})

// ===== Backlog 面板 =====
const BACKLOG_VISIBLE_KEY = 'tf_kanban_backlog_visible'
const showBacklog = ref(localStorage.getItem(BACKLOG_VISIBLE_KEY) === 'true')
const backlogPanelRef = ref<InstanceType<typeof BacklogPanel> | null>(null)
const backlogDraggingIssue = ref<BoardIssue | null>(null)

// ===== 工单预览面板 =====
const previewVisible = ref(false)
const previewIssueId = ref<string | null>(null)

function openPreview(issue: BoardIssue) {
  previewIssueId.value = issue.id
  previewVisible.value = true
}

function closePreview() {
  previewVisible.value = false
}

function onPreviewGoDetail(issueId: string) {
  router.push({ name: 'IssueDetail', params: { id: issueId } })
}

/** Handle issue updates from preview panel (inline editing) */
function onPreviewIssueUpdated(issueId: string, changes: Partial<Pick<IssueVO, 'statusId' | 'statusName' | 'statusColor' | 'priority' | 'assigneeId' | 'assigneeName' | 'sprintId' | 'sprintName' | 'issueType' | 'title' | 'version'>>) {
  const issue = issues.value.find(i => i.id === issueId)
  if (!issue) return

  // patchIssue 替换数组引用，触发 shallowRef 视图更新
  const patch: Partial<BoardIssue> = {}
  if (changes.statusId) {
    patch.statusId = changes.statusId
    patch.version = (issue.version || 0) + 1
  }
  if (changes.priority) patch.priority = changes.priority
  if ('assigneeId' in changes) {
    patch.assigneeId = changes.assigneeId || undefined
    patch.assigneeName = changes.assigneeName || undefined
  }
  patchIssue(issueId, patch)
}

function toggleBacklog() {
  showBacklog.value = !showBacklog.value
  localStorage.setItem(BACKLOG_VISIBLE_KEY, String(showBacklog.value))
}

function onBacklogDragStart(issue: BoardIssue) {
  backlogDraggingIssue.value = issue
  // Fetch available transitions for the backlog issue's current status
  // For backlog items, all open statuses should be valid targets
  allowedTargetStatuses.value = new Set(statuses.value.map(s => s.id))
}

function onBacklogDragEnd() {
  backlogDraggingIssue.value = null
  allowedTargetStatuses.value.clear()
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
}

// ===== Swimlane 分组 =====
const COLLAPSED_SWIMLANES_KEY = 'tf_kanban_collapsed_swimlanes'

const swimlaneGroupBy = ref<SwimlaneGroupBy>(
  (localStorage.getItem(SWIMLANE_STORAGE_KEY) as SwimlaneGroupBy) || 'none'
)
const collapsedSwimlanes = ref<Set<string>>(
  new Set(JSON.parse(localStorage.getItem(COLLAPSED_SWIMLANES_KEY) || '[]'))
)

/** 泳道选中的值列表（null = 全选，向后兼容） */
const swimlaneSelectedValues = ref<string[] | null>(null)
/** 是否显示"未分类"泳道 */
const swimlaneShowUncategorized = ref<boolean>(true)
/** 未分类泳道位置 */
const swimlaneUncategorizedPosition = ref<'top' | 'bottom'>('bottom')
/** Issues 模式下作为泳道行的 Issue 类型 */
const swimlaneIssueType = ref<string | null>(null)

function onSwimlaneChange() {
  localStorage.setItem(SWIMLANE_STORAGE_KEY, swimlaneGroupBy.value)
  // 切换分组维度时清除折叠状态和已选值
  collapsedSwimlanes.value.clear()
  localStorage.removeItem(COLLAPSED_SWIMLANES_KEY)
  swimlaneSelectedValues.value = null
  swimlaneShowUncategorized.value = true
  swimlaneUncategorizedPosition.value = 'bottom'
  // 切换离开 parent 模式时清空 swimlaneIssueType
  if (swimlaneGroupBy.value !== 'parent') {
    swimlaneIssueType.value = null
  }
  // 清除该维度的自定义顺序（切换维度时重置排序）
  clearSwimlaneOrder()
  // 重新加载新维度的泳道排序
  loadSwimlaneOrder()
  // 同步到 URL
  syncUrlState()
  // 持久化到服务端（静默保存，不阻塞 UI）
  if (selectedProject.value) {
    boardApi.saveSwimlaneConfig(selectedProject.value, {
      groupByField: swimlaneGroupBy.value,
      selectedValues: null,
      showUncategorized: true,
      uncategorizedPosition: 'bottom',
      swimlaneIssueType: swimlaneGroupBy.value === 'parent' ? swimlaneIssueType.value : null
    }).catch((e) => { console.error('[KanbanBoard] 保存泳道配置失败:', e) })
  }
}

function toggleSwimlane(key: string) {
  if (collapsedSwimlanes.value.has(key)) {
    collapsedSwimlanes.value.delete(key)
  } else {
    collapsedSwimlanes.value.add(key)
  }
  localStorage.setItem(COLLAPSED_SWIMLANES_KEY, JSON.stringify([...collapsedSwimlanes.value]))
}

// ===== 泳道行拖拽排序 =====
const SWIMLANE_ORDER_KEY_PREFIX = 'tf_kanban_swimlane_order'

function getSwimlaneOrderKey(): string {
  return selectedProject.value
    ? `${SWIMLANE_ORDER_KEY_PREFIX}_${selectedProject.value}_${swimlaneGroupBy.value}`
    : `${SWIMLANE_ORDER_KEY_PREFIX}_${swimlaneGroupBy.value}`
}

/** 用户自定义泳道顺序（泳道 key 列表，null 表示使用默认顺序） */
const swimlaneCustomOrder = ref<string[] | null>(null)

/** 加载当前项目+分组维度的泳道排序 */
function loadSwimlaneOrder() {
  const key = getSwimlaneOrderKey()
  const stored = localStorage.getItem(key)
  swimlaneCustomOrder.value = stored ? JSON.parse(stored) : null
}

/** 保存泳道排序 */
function saveSwimlaneOrder(order: string[]) {
  const key = getSwimlaneOrderKey()
  localStorage.setItem(key, JSON.stringify(order))
  swimlaneCustomOrder.value = order
}

/** 清除泳道排序（切换分组时重置） */
function clearSwimlaneOrder() {
  if (selectedProject.value) {
    const key = getSwimlaneOrderKey()
    localStorage.removeItem(key)
  }
  swimlaneCustomOrder.value = null
}

/**
 * 按用户自定义顺序排列的泳道列表。
 * - 若无自定义顺序，使用 swimlanes 计算顺序（默认）
 * - 若有自定义顺序，按 key 排列，新增未知 key 追加到末尾
 */
const orderedSwimlanes = computed<SwimlaneRow[]>(() => {
  const base = swimlanes.value
  if (!swimlaneCustomOrder.value || swimlaneCustomOrder.value.length === 0) return base

  const orderMap = new Map<string, number>()
  swimlaneCustomOrder.value.forEach((key, idx) => orderMap.set(key, idx))

  const sorted = [...base].sort((a, b) => {
    const idxA = orderMap.has(a.key) ? orderMap.get(a.key)! : base.length
    const idxB = orderMap.has(b.key) ? orderMap.get(b.key)! : base.length
    return idxA - idxB
  })

  return sorted
})

/** 正在拖拽的泳道行 key */
const swimlaneDraggingKey = ref<string | null>(null)
/** 拖拽悬停的目标泳道行 key */
const swimlaneDragOverKey = ref<string | null>(null)

function onSwimlaneRowDragStart(event: DragEvent, laneKey: string) {
  // 不与卡片拖拽冲突：仅在无卡片拖拽时允许泳道行拖拽
  if (isDragging.value) {
    event.preventDefault()
    return
  }
  swimlaneDraggingKey.value = laneKey
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', `swimlane:${laneKey}`)
  }
}

function onSwimlaneRowDragEnd() {
  swimlaneDraggingKey.value = null
  swimlaneDragOverKey.value = null
}

function onSwimlaneRowDragOver(event: DragEvent, laneKey: string) {
  if (!swimlaneDraggingKey.value) return
  event.preventDefault()
  swimlaneDragOverKey.value = laneKey
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onSwimlaneRowDragLeave(event: DragEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null
  const currentTarget = event.currentTarget as HTMLElement
  if (relatedTarget && currentTarget.contains(relatedTarget)) return
  swimlaneDragOverKey.value = null
}

function onSwimlaneRowDrop(event: DragEvent, targetKey: string) {
  event.preventDefault()
  const fromKey = swimlaneDraggingKey.value
  swimlaneDraggingKey.value = null
  swimlaneDragOverKey.value = null

  if (!fromKey || fromKey === targetKey) return

  // Build new order by moving fromKey to targetKey's position
  const currentOrder = orderedSwimlanes.value.map(l => l.key)
  const fromIdx = currentOrder.indexOf(fromKey)
  const toIdx = currentOrder.indexOf(targetKey)
  if (fromIdx < 0 || toIdx < 0) return

  const newOrder = [...currentOrder]
  newOrder.splice(fromIdx, 1)
  // Determine drop side (above or below target)
  const insertIdx = fromIdx < toIdx ? toIdx : toIdx
  newOrder.splice(insertIdx, 0, fromKey)

  saveSwimlaneOrder(newOrder)
}

// Swimlane 数据结构
// (SwimlaneRow interface exported at top level)

// 类型映射（使用共享工具）
const TYPE_LABELS = { Task: '任务', Bug: '缺陷', Feature: '需求', Epic: '史诗', Story: '故事' } as Record<string, string>

const swimlanes = computed<SwimlaneRow[]>(() => {
  if (swimlaneGroupBy.value === 'none') return []

  const allIssues = issues.value

  let rows: SwimlaneRow[]
  switch (swimlaneGroupBy.value) {
    case 'assignee':
      rows = groupByAssignee(allIssues)
      break
    case 'priority':
      rows = groupByPriority(allIssues)
      break
    case 'type':
      rows = groupByType(allIssues)
      break
    case 'sprint':
      rows = groupBySprint(allIssues)
      break
    case 'tag':
      rows = groupByTag(allIssues)
      break
    case 'parent':
      rows = groupByParent(allIssues)
      break
    case 'dueDate':
      // 日期泳道不支持自定义 selectedValues 过滤，直接返回
      return groupByDueDate(allIssues)
    default:
      return []
  }

  // 如果有 selectedValues 配置，过滤泳道并生成"未分类"泳道
  const selected = swimlaneSelectedValues.value
  if (selected && selected.length > 0) {
    const selectedSet = new Set(selected)
    const filteredRows: SwimlaneRow[] = []
    const uncategorizedIssues: BoardIssue[] = []

    for (const row of rows) {
      if (selectedSet.has(row.key)) {
        filteredRows.push(row)
      } else {
        // 不在选中列表中的泳道，工单归入"未分类"
        uncategorizedIssues.push(...row.issues)
      }
    }

    // 按选中值顺序排列
    filteredRows.sort((a, b) => selected.indexOf(a.key) - selected.indexOf(b.key))

    // 添加"未分类"泳道
    if (swimlaneShowUncategorized.value && uncategorizedIssues.length > 0) {
      const uncategorizedRow: SwimlaneRow = {
        key: '__uncategorized__',
        label: '未分类',
        issues: uncategorizedIssues
      }
      if (swimlaneUncategorizedPosition.value === 'top') {
        filteredRows.unshift(uncategorizedRow)
      } else {
        filteredRows.push(uncategorizedRow)
      }
    }

    return filteredRows
  }

  return rows
})

function groupByAssignee(allIssues: BoardIssue[]): SwimlaneRow[] {
  const groups = new Map<string, BoardIssue[]>()
  const unassigned: BoardIssue[] = []

  for (const issue of allIssues) {
    if (!issue.assigneeId || !issue.assigneeName) {
      unassigned.push(issue)
    } else {
      const key = issue.assigneeId
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key)!.push(issue)
    }
  }

  // 按工单数量降序排列负责人
  const rows: SwimlaneRow[] = [...groups.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .map(([assigneeId, issues]) => ({
      key: assigneeId,
      label: issues[0].assigneeName || '未知',
      issues
    }))

  // "未分配" 放在最后
  if (unassigned.length > 0) {
    rows.push({ key: '__unassigned__', label: '未分配', issues: unassigned })
  }

  return rows
}

function groupByPriority(allIssues: BoardIssue[]): SwimlaneRow[] {
  const priorities = ['紧急', '高', '普通', '低']
  const groups = new Map<string, BoardIssue[]>()
  for (const p of priorities) groups.set(p, [])

  for (const issue of allIssues) {
    const p = issue.priority || 'Normal'
    if (!groups.has(p)) groups.set(p, [])
    groups.get(p)!.push(issue)
  }

  return priorities
    .filter(p => (groups.get(p)?.length ?? 0) > 0)
    .map(p => ({
      key: p,
      label: `${priorityIcon(p)} ${p}`,
      issues: groups.get(p)!
    }))
}

function groupByType(allIssues: BoardIssue[]): SwimlaneRow[] {
  const types = ['Bug', 'Task', 'Feature', 'Story']
  const groups = new Map<string, BoardIssue[]>()
  const other: BoardIssue[] = []

  for (const issue of allIssues) {
    const t = issue.issueType
    if (types.includes(t)) {
      if (!groups.has(t)) groups.set(t, [])
      groups.get(t)!.push(issue)
    } else {
      other.push(issue)
    }
  }

  const rows: SwimlaneRow[] = types
    .filter(t => (groups.get(t)?.length ?? 0) > 0)
    .map(t => ({
      key: t,
      label: TYPE_LABELS[t] || t,
      issues: groups.get(t)!
    }))

  if (other.length > 0) {
    rows.push({ key: '__other__', label: '其他', issues: other })
  }

  return rows
}

function groupBySprint(allIssues: BoardIssue[]): SwimlaneRow[] {
  const groups = new Map<string, BoardIssue[]>()
  const noSprint: BoardIssue[] = []

  for (const issue of allIssues) {
    if (!issue.sprintId) {
      noSprint.push(issue)
    } else {
      if (!groups.has(issue.sprintId)) groups.set(issue.sprintId, [])
      groups.get(issue.sprintId)!.push(issue)
    }
  }

  // 用 sprints 列表映射名称
  const sprintMap = new Map(sprints.value.map(s => [s.id, s.name]))

  const rows: SwimlaneRow[] = [...groups.entries()].map(([sprintId, issues]) => ({
    key: sprintId,
    label: sprintMap.get(sprintId) || `Sprint ${sprintId}`,
    issues
  }))

  if (noSprint.length > 0) {
    rows.push({ key: '__no_sprint__', label: '未规划', issues: noSprint })
  }

  return rows
}

function groupByTag(allIssues: BoardIssue[]): SwimlaneRow[] {
  const groups = new Map<string, BoardIssue[]>()
  const noTag: BoardIssue[] = []

  for (const issue of allIssues) {
    const tags = issue.tags
    if (!tags || tags.length === 0) {
      noTag.push(issue)
    } else {
      // 工单放入第一个标签对应的泳道（与 YouTrack 行为一致）
      const firstTag = tags[0]
      const key = firstTag.id || firstTag.name
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key)!.push(issue)
    }
  }

  const rows: SwimlaneRow[] = [...groups.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .map(([tagKey, issues]) => {
      const sampleIssue = issues[0]
      const tags = sampleIssue.tags
      const tagName = tags?.find(t => (t.id || t.name) === tagKey)?.name || tagKey
      return { key: tagKey, label: `🏷️ ${tagName}`, issues }
    })

  if (noTag.length > 0) {
    rows.push({ key: '__no_tag__', label: '无标签', issues: noTag })
  }

  return rows
}

/**
 * Issues 模式（YouTrack Swimlanes Issues 类型）：
 * 以 swimlaneIssueType 指定类型的工单作为泳道行标题，
 * 其子工单（parentId 指向该工单）排列在对应泳道中。
 * 没有父工单（或父工单类型不匹配）的工单归入"未分类"泳道。
 */
function groupByParent(allIssues: BoardIssue[]): SwimlaneRow[] {
  const targetType = swimlaneIssueType.value

  // 收集所有作为父工单的卡片（类型匹配且本身在看板上）
  // 及子工单映射：parentId → [子工单列表]
  const parentMap = new Map<string, BoardIssue>()
  const childrenMap = new Map<string, BoardIssue[]>()
  const uncategorized: BoardIssue[] = []

  // 第一遍：识别父工单（目标类型的工单）
  for (const issue of allIssues) {
    const issueType = issue.issueType
    if (targetType && issueType === targetType) {
      parentMap.set(issue.id, issue)
    }
  }

  // 第二遍：将子工单归入父工单或未分类
  for (const issue of allIssues) {
    // 跳过父工单本身（它们成为泳道行，不作为子工单出现）
    if (parentMap.has(issue.id)) continue

    const parentId = ('parentId' in issue ? issue.parentId : undefined) as string | undefined
    if (parentId && parentMap.has(parentId)) {
      if (!childrenMap.has(parentId)) childrenMap.set(parentId, [])
      childrenMap.get(parentId)!.push(issue)
    } else {
      // 无父工单或父工单不在看板上（类型不匹配）→ 未分类
      uncategorized.push(issue)
    }
  }

  // 构建泳道行
  const rows: SwimlaneRow[] = []
  for (const [parentId, parentIssue] of parentMap.entries()) {
    const children = childrenMap.get(parentId) || []
    rows.push({
      key: parentId,
      label: `${parentIssue.issueKey} ${parentIssue.title}`,
      issues: children
    })
  }

  // 按父工单 key 排序（字母数字）
  rows.sort((a, b) => {
    const keyA = parentMap.get(a.key)?.issueKey || a.key
    const keyB = parentMap.get(b.key)?.issueKey || b.key
    return keyA.localeCompare(keyB)
  })

  // 未分类泳道（未匹配父工单的工单），按 showUncategorized 配置决定是否显示
  if (swimlaneShowUncategorized.value && uncategorized.length > 0) {
    const uncategorizedRow: SwimlaneRow = {
      key: '__uncategorized__',
      label: '未分类',
      issues: uncategorized
    }
    if (swimlaneUncategorizedPosition.value === 'top') {
      rows.unshift(uncategorizedRow)
    } else {
      rows.push(uncategorizedRow)
    }
  }

  return rows
}

/**
 * 按截止日期分组 — YouTrack 风格相对日期范围泳道。
 * 分组顺序：已过期 → 今天 → 本周 → 下周 → 本月 → 更晚 → 无截止日期
 * 相对日期基于看板加载时的客户端本地时间动态计算。
 */
function groupByDueDate(allIssues: BoardIssue[]): SwimlaneRow[] {
  const now = new Date()
  const todayStr = now.toISOString().slice(0, 10)

  // 计算当周起止（周一~周日）
  const dayOfWeek = now.getDay() // 0=周日
  const daysToMonday = (dayOfWeek === 0 ? -6 : 1 - dayOfWeek)
  const monday = new Date(now)
  monday.setDate(now.getDate() + daysToMonday)
  monday.setHours(0, 0, 0, 0)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  sunday.setHours(23, 59, 59, 999)

  // 下周起止
  const nextMonday = new Date(monday)
  nextMonday.setDate(monday.getDate() + 7)
  const nextSunday = new Date(sunday)
  nextSunday.setDate(sunday.getDate() + 7)

  // 本月起止
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
  const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0)
  monthEnd.setHours(23, 59, 59, 999)

  type DueDateBucket = 'overdue' | 'today' | 'this_week' | 'next_week' | 'this_month' | 'later' | 'no_date'

  function getBucket(dueDate: string | undefined): DueDateBucket {
    if (!dueDate) return 'no_date'

    // dueDate 格式为 'YYYY-MM-DD'
    if (dueDate < todayStr) return 'overdue'
    if (dueDate === todayStr) return 'today'

    const d = new Date(dueDate + 'T00:00:00')
    if (d >= monday && d <= sunday) return 'this_week'
    if (d >= nextMonday && d <= nextSunday) return 'next_week'
    if (d >= monthStart && d <= monthEnd) return 'this_month'
    return 'later'
  }

  const bucketOrder: DueDateBucket[] = ['overdue', 'today', 'this_week', 'next_week', 'this_month', 'later', 'no_date']
  const bucketLabels: Record<DueDateBucket, string> = {
    overdue: '⚠️ 已过期',
    today: '📅 今天',
    this_week: '📅 本周',
    next_week: '📅 下周',
    this_month: '📅 本月',
    later: '📅 更晚',
    no_date: '— 无截止日期',
  }

  const groups: Record<DueDateBucket, BoardIssue[]> = {
    overdue: [],
    today: [],
    this_week: [],
    next_week: [],
    this_month: [],
    later: [],
    no_date: [],
  }

  for (const issue of allIssues) {
    const bucket = getBucket(issue.dueDate)
    groups[bucket].push(issue)
  }

  // 只返回有工单的泳道（保持 YouTrack 风格：空泳道不展示）
  return bucketOrder
    .filter(b => groups[b].length > 0)
    .map(b => ({
      key: b,
      label: bucketLabels[b],
      issues: groups[b],
    }))
}

/** 获取某泳道中某状态列的工单 */
function getSwimlaneColumnIssues(laneKey: string, statusId: string): BoardIssue[] {
  if (!lane) return []
  return lane.issues.filter(i => i.statusId === statusId)
}

// Swimlane 模式下的拖拽 hover 状态
const dragOverSwimlaneKey = ref<string | null>(null)

function onDragOverSwimlane(event: DragEvent, statusId: string, laneKey: string) {
  event.preventDefault()
  dragOverColumnId.value = statusId
  dragOverSwimlaneKey.value = laneKey
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = isDropAllowed(statusId) ? 'move' : 'none'
  }
}

function onDragLeaveSwimlane(event: DragEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null
  const currentTarget = event.currentTarget as HTMLElement
  if (relatedTarget && currentTarget.contains(relatedTarget)) return
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
}

// ===== Sprint 选择器辅助 =====

/** 当前选中的 Sprint 对象 */
const currentSelectedSprint = computed(() => {
  if (!selectedSprint.value) return null
  return sprints.value.find(s => s.id === selectedSprint.value) || null
})

/** 看板所有者（项目负责人 leadId 对应的显示名称） */
const boardOwnerName = computed(() => {
  if (!selectedProject.value) return ''
  const proj = projects.value.find(p => p.id === selectedProject.value)
  if (!proj || !proj.leadId) return ''
  const member = projectMembers.value.find(m => m.userId === proj.leadId)
  return member?.displayName || ''
})

/** 当前活跃 Sprint（status=active 或日期范围包含今天的 planned Sprint） */
const activeSprint = computed(() => {
  const active = sprints.value.find(s => s.status === 'active')
  if (active) return active
  // Fallback: planned sprint covering today
  const today = new Date().toISOString().split('T')[0]
  return sprints.value.find(s =>
    s.status === 'planned' && s.startDate && s.endDate &&
    s.startDate <= today && s.endDate >= today
  )
})

/** 当前 Sprint 目标文本 */
const currentSprintGoal = computed(() => {
  // 优先展示选中 Sprint 的目标，其次展示活跃 Sprint 的目标
  const sprint = currentSelectedSprint.value || activeSprint.value
  if (!sprint) return ''
  return sprint.goal || ''
})

/** Sprint 剩余天数（选中的 Sprint 有 endDate 时显示——active 或已开始的 planned） */
const sprintRemainingDays = computed(() => {
  const sprint = currentSelectedSprint.value
  if (!sprint || !sprint.endDate) return null
  // Show countdown for active sprint or planned sprint that has started (date-range)
  if (sprint.status !== 'active' && sprint.status !== 'planned') return null
  // For planned sprints, only show if start date has passed or is today
  if (sprint.status === 'planned' && sprint.startDate) {
    const today = new Date().toISOString().split('T')[0]
    if (sprint.startDate > today) return null
  }
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const endDate = new Date(sprint.endDate + 'T00:00:00')
  const diff = Math.ceil((endDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
  return diff
})

/** 判断某 Sprint 是否为当前活跃 Sprint（用于下拉列表标记"当前"） */
function isActiveSprint(sprint: SprintVO): boolean {
  return sprint.id === activeSprint.value?.id
}

// ===== 看板 Footer：已归档 Sprint 操作 =====

/** 恢复归档 Sprint 的 loading 状态 */
const restoringArchivedSprint = ref(false)

/** 删除归档 Sprint 的 loading 状态 */
const deletingArchivedSprint = ref(false)

/** 是否显示删除归档 Sprint 的确认弹框 */
const showDeleteArchivedSprintModal = ref(false)

/** 删除预览信息 */
const deleteArchivedSprintPreview = ref<DeletionPreviewVO | null>(null)

/** 删除时的工单处理方式 */
const deleteArchivedSprintMoveOption = ref<string>('backlog')

/** 删除时的目标迭代 ID */
const deleteArchivedSprintTargetId = ref<string>('')

/** 从看板 Footer 恢复已归档 Sprint */
async function handleRestoreArchivedSprint() {
  if (!currentSelectedSprint.value) return
  restoringArchivedSprint.value = true
  try {
    await sprintApi.restore(currentSelectedSprint.value.id)
    Message.success('迭代已恢复为已完成状态')
    // 刷新 Sprint 列表
    if (selectedProject.value) {
      const res = await sprintApi.listByProject(selectedProject.value)
      sprints.value = res.data?.list || []
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '恢复失败')
  } finally {
    restoringArchivedSprint.value = false
  }
}

/** 从看板 Footer 删除已归档 Sprint（显示确认弹框） */
async function handleDeleteArchivedSprint() {
  if (!currentSelectedSprint.value) return
  deleteArchivedSprintPreview.value = null
  deleteArchivedSprintMoveOption.value = 'backlog'
  deleteArchivedSprintTargetId.value = ''
  showDeleteArchivedSprintModal.value = true

  try {
    const res = await sprintApi.deletionPreview(currentSelectedSprint.value.id)
    deleteArchivedSprintPreview.value = res.data
    if (res.data.totalIssues > 0 && res.data.targetSprints && res.data.targetSprints.length > 0) {
      deleteArchivedSprintTargetId.value = res.data.targetSprints[0].id
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取预览信息失败')
    showDeleteArchivedSprintModal.value = false
  }
}

/** 确认删除已归档 Sprint */
async function confirmDeleteArchivedSprint() {
  if (!currentSelectedSprint.value || !deleteArchivedSprintPreview.value) return

  const hasIssues = deleteArchivedSprintPreview.value.totalIssues > 0
  if (hasIssues && deleteArchivedSprintMoveOption.value === 'next_sprint' && !deleteArchivedSprintTargetId.value) {
    Message.warning('请选择目标迭代')
    return
  }

  deletingArchivedSprint.value = true
  try {
    const body = hasIssues
      ? { moveOption: deleteArchivedSprintMoveOption.value, targetSprintId: deleteArchivedSprintMoveOption.value === 'next_sprint' ? deleteArchivedSprintTargetId.value : undefined }
      : undefined

    await sprintApi.delete(currentSelectedSprint.value.id, body)
    Message.success('迭代已删除')
    showDeleteArchivedSprintModal.value = false
    // 清空选中的 Sprint，刷新列表
    selectedSprint.value = undefined
    if (selectedProject.value) {
      const res = await sprintApi.listByProject(selectedProject.value)
      sprints.value = res.data?.list || []
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  } finally {
    deletingArchivedSprint.value = false
  }
}

// ===== 无活跃 Sprint 引导 =====

/** 下一个计划中的 Sprint（最近的未来 planned Sprint） */
const nextPlannedSprint = computed(() => {
  if (activeSprint.value) return null
  const today = new Date().toISOString().split('T')[0]
  // 找到开始日期在今天之后的 planned Sprint，取最近的一个
  const futurePlanned = sprints.value
    .filter(s => s.status === 'planned' && s.startDate && s.startDate > today)
    .sort((a, b) => (a.startDate || '').localeCompare(b.startDate || ''))
  return futurePlanned.length > 0 ? futurePlanned[0] : null
})

/** 工具栏中显示的下一个 Sprint 提示文字 */
const nextPlannedSprintHint = computed(() => {
  const sprint = nextPlannedSprint.value
  if (!sprint || !sprint.startDate) return ''
  const startDate = new Date(sprint.startDate + 'T00:00:00')
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const daysUntil = Math.ceil((startDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
  if (daysUntil <= 0) return `${sprint.name} 今天开始`
  if (daysUntil === 1) return `${sprint.name} 明天开始`
  return `${sprint.name} ${daysUntil} 天后开始`
})

/** 引导横幅 dismissed 状态（localStorage 存储，基于项目+会话） */
const GUIDANCE_DISMISSED_KEY = 'tf_kanban_guidance_dismissed'
const guidanceDismissed = ref(false)

/** 是否显示无活跃 Sprint 引导横幅 */
const showNoActiveSprintGuidance = computed(() => {
  if (guidanceDismissed.value) return false
  if (!selectedProject.value) return false
  if (activeSprint.value) return false
  if (selectedSprint.value) return false // 用户已手动选了某个 Sprint
  if (boardFilterMode.value === 'active_sprint') return false // 已在 Sprint 模式（有对应空状态）
  if (sprints.value.length === 0) return false
  if (issues.value.length === 0) return false
  return true
})

/** 所有计划中的 Sprint（无论是否有日期） */
const plannedSprints = computed(() => {
  return sprints.value.filter(s => s.status === 'planned')
})

/** 关闭引导横幅 */
function dismissGuidance() {
  guidanceDismissed.value = true
  // 当前会话记忆（刷新页面时重新显示，切换项目时重置）
  sessionStorage.setItem(`${GUIDANCE_DISMISSED_KEY}_${selectedProject.value}`, 'true')
}

/** 选择下一个计划 Sprint */
function selectNextPlannedSprint() {
  if (nextPlannedSprint.value) {
    selectedSprint.value = nextPlannedSprint.value.id
    userExplicitlySelectedAll = false
    syncUrlState()
    loadIssuesWithLoading()
    guidanceDismissed.value = true
  }
}

/** 从引导横幅打开创建 Sprint 弹窗 */
function openCreateSprintFromGuidance() {
  newSprintForm.value = { name: '', goal: '', startDate: undefined, endDate: undefined }
  newSprintModalVisible.value = true
}

/** 激活指定的计划 Sprint 并刷新看板 */
const activatingSprintId = ref<string | null>(null)
async function activatePlannedSprint(sprint: SprintVO) {
  activatingSprintId.value = sprint.id
  try {
    const res = await sprintApi.activate(sprint.id)
    if (res.data) {
      // 更新本地 Sprint 列表中的状态
      const idx = sprints.value.findIndex(s => s.id === sprint.id)
      if (idx >= 0) sprints.value[idx] = res.data
      Message.success(`迭代「${res.data.name}」已激活`)
      // 自动选中新激活的 Sprint 并刷新看板
      selectedSprint.value = res.data.id
      userExplicitlySelectedAll = false
      syncUrlState()
      await loadIssuesWithLoading()
      guidanceDismissed.value = true
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '激活迭代失败')
  } finally {
    activatingSprintId.value = null
  }
}

/** 格式化 Sprint 日期（用于引导横幅） */
function formatSprintDate(dateStr: string | undefined): string {
  if (!dateStr) return ''
  const d = new Date(dateStr + 'T00:00:00')
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** 有效的完成工单保留天数（服务端配置 > 默认 14 天） */
const effectiveDoneRetentionDays = computed(() => {
  return boardDoneRetentionDays.value
})

// 搜索相关
// ===== 搜索过滤逻辑（委托到 useBoardFilter composable） =====
// loadIssues is provided by useBoardData (declared below after useBoardDrag);
// we pass a late-binding wrapper since useBoardFilter only invokes it at runtime.
let _loadIssuesFn: () => Promise<void> = async () => {}
const {
  isSearchActive, showNoSearchResults,
  onSearchInput, onSearchClear, clearSearch,
  loadIssuesWithLoading, clearDebounceTimer
} = useBoardFilter({
  keyword, selectedProject, issues, loading, loadIssues: () => _loadIssuesFn()
})

/** 是否显示 Sprint 模式无活跃迭代的空状态 */
const showSprintModeNoActiveState = computed(() =>
  selectedProject.value &&
  !loading.value &&
  boardFilterMode.value === 'active_sprint' &&
  !activeSprint.value &&
  issues.value.length === 0 &&
  !isSearchActive.value
)

/** 跳转到迭代管理页面 */
function goToSprints() {
  router.push({ name: 'Sprints' })
}

/** 跳转到迭代详情页（保持项目上下文） */
function goToSprintDetail() {
  const query: Record<string, string> = {}
  if (currentProjectKey.value) {
    query.project = currentProjectKey.value
  }
  router.push({ name: 'Sprints', query })
}

/** 格式化 Sprint 日期范围（如 "7/29 - 8/11"） */
function formatSprintDateRange(startDate?: string, endDate?: string): string {
  if (!startDate) return ''
  const formatShort = (dateStr: string) => {
    const d = new Date(dateStr + 'T00:00:00')
    return `${d.getMonth() + 1}/${d.getDate()}`
  }
  if (!endDate) return formatShort(startDate) + ' 开始'
  return `${formatShort(startDate)} - ${formatShort(endDate)}`
}

/** 打开看板设置到基本设置标签页 */
function openSettingsToGeneral() {
  showSettings.value = true
}

function onProjectChange() {
  keyword.value = ''
  selectedSprint.value = undefined
  showAllColumns.value = false  // 切换项目时重置临时显示
  userExplicitlySelectedAll = false  // Reset: allow auto-select for new project
  guidanceDismissed.value = false  // Reset guidance for new project
  resetBoardManualOrder()  // Reset manual order when switching projects
  // 切换项目时清除泳道自定义排序（不同项目的泳道数据不同）
  swimlaneCustomOrder.value = null
  // 切换项目时：保留 'me' 筛选，但清除指定用户 ID（因为不同项目的成员不同）
  if (assigneeFilter.value && assigneeFilter.value !== 'me') {
    assigneeFilter.value = undefined
    localStorage.removeItem(ASSIGNEE_FILTER_KEY)
  }
  clearDebounceTimer()
  syncUrlState()
  loadBoard()
}
function onSprintChange() {
  // Track if user explicitly cleared the sprint selection (chose "所有迭代")
  userExplicitlySelectedAll = !selectedSprint.value
  syncUrlState()
  // Sprint change only affects issue filtering — no need to reload board columns,
  // sprints list, card config, etc. This avoids re-fetching sprints which would
  // cause Arco Select to re-render options and potentially clear the v-model value.
  loadIssuesWithLoading()
}
const sprints = ref<SprintVO[]>([])
const statuses = ref<IssueStatusVO[]>([])

// ===== 卡片多选 =====
const {
  selectedIds, selectedCount, selectedIssues,
  toggle: toggleCardSelection, clearSelection
} = useSelection(issues)

const { batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority, batchTagAdd, batchTagRemove, batchAddLink, batchDelete } = useBatchOps()

// ===== 手动排序（列内拖拽） =====
const {
  isManualSorted: boardManualSorted,
  loadManualOrder: loadBoardManualOrder,
  applyManualOrder: applyBoardManualOrder,
  saveOrder: saveBoardManualOrder,
  reset: resetBoardManualOrder
} = useManualOrder()

/** 判断手动排序是否被禁用（Board Settings 的 filterQuery 包含 sort by 等排序指令） */
const isManualSortDisabled = computed(() => {
  if (!boardFilterQuery.value) return false
  // Check if filterQuery contains sort-related fields (simplified check)
  try {
    const filters = JSON.parse(boardFilterQuery.value)
    return Array.isArray(filters) && filters.some((f: { field?: string }) => f.field === 'sort' || f.field === 'orderBy')
  } catch {
    // string-based query: check for 'sort by' keyword
    return boardFilterQuery.value.toLowerCase().includes('sort by')
  }
})

// 看板列配置
const allColumnConfigs = ref<BoardColumnVO[]>([])
const showSettings = ref(false)

// ===== 克隆看板 =====
const showCloneModal = ref(false)

/** 当前看板显示名称（用于克隆弹窗） */
const currentBoardDisplayName = computed(() => displayBoardName.value || currentProjectName.value || '看板')

function openCloneModal() {
  showCloneModal.value = true
}

function onBoardCloned(newProjectId: string) {
  // 克隆成功后切换到新看板
  selectedProject.value = newProjectId
}

// 看板图表面板
const showChart = ref(false)
const boardChartType = ref<string>('burndown')
const boardBurndownCalculation = ref<string>('issue_count')

// Board truncation state (written by useBoardData)
const boardTotalCount = ref(0)
const boardTruncated = ref(false)

// 看板卡片配置（字段显示 + 颜色方案）
const cardConfig = ref<BoardCardConfigVO>({
  visibleFields: ['assignee', 'priority', 'type'],
  colorScheme: 'none',
  showCustomFieldColors: true
})

// 看板列合并配置
const columnMerges = ref<BoardColumnMergeGroupVO[]>([])

// 根据列配置过滤出可见的状态
const visibleStatuses = computed(() => {
  if (allColumnConfigs.value.length === 0) {
    if (boardColumnField.value === 'status') {
      return statuses.value
    }
    // For non-status modes with no config, return empty (will be populated by loadBoardColumns)
    return []
  }
  return allColumnConfigs.value
    .filter(c => c.visible || showAllColumns.value)
    .map(c => ({
      id: c.fieldValue || c.statusId,
      name: c.statusName,
      code: c.statusCode,
      color: c.statusColor,
      category: c.statusCategory,
      isDefault: false,
      isClosed: c.statusCategory === 'done' || c.statusCategory === 'cancelled',
      sortOrder: c.sortOrder
    } as IssueStatusVO))
})

// ===== 进度指示器：活跃状态 vs 终态 =====

/** 活跃状态列（排除已完成/已取消） — 用于进度指示器主体 */
const activeStatuses = computed(() => {
  return visibleStatuses.value.filter(s => !isClosedStatus(s))
})

/** 终态列（已完成/已取消） — 用于进度指示器弱化汇总 */
const closedStatuses = computed(() => {
  return visibleStatuses.value.filter(s => isClosedStatus(s))
})

/** 终态工单总数 */
const closedIssueCount = computed(() => {
  return closedStatuses.value.reduce((sum, s) => sum + getColumnIssues(s.id).length, 0)
})

/** 终态工单明细（用于 tooltip） */
const closedIssueDetail = computed(() => {
  return closedStatuses.value
    .filter(s => getColumnIssues(s.id).length > 0)
    .map(s => `${s.name} ${getColumnIssues(s.id).length}`)
    .join('、')
})

/** 进度指示器 aria-label */
const progressIndicatorAriaLabel = computed(() => {
  const activeCount = activeStatuses.value.reduce((sum, s) => sum + getColumnIssues(s.id).length, 0)
  return `活跃工单分布：${activeCount} 个活跃工单` + (closedIssueCount.value > 0 ? `，${closedIssueCount.value} 个已完成` : '')
})

// EffectiveColumn interface is imported from ./types

const effectiveColumns = computed<EffectiveColumn[]>(() => {
  const cols = visibleStatuses.value
  const merges = columnMerges.value

  if (merges.length === 0) {
    // 无合并：每个状态独占一列
    return cols.map(s => ({
      id: s.id,
      name: s.name,
      color: s.color || '',
      category: s.category || '',
      statusIds: [s.id],
      isMerged: false,
      sortOrder: s.sortOrder
    }))
  }

  // 构建 statusId → mergeGroup 的映射
  const statusToGroup = new Map<string, BoardColumnMergeGroupVO>()
  for (const group of merges) {
    for (const sid of group.statusIds) {
      statusToGroup.set(sid, group)
    }
  }

  // 遍历可见状态，生成有效列（合并组只出现一次）
  const result: EffectiveColumn[] = []
  const processedGroups = new Set<string>()

  for (const s of cols) {
    const group = statusToGroup.get(s.id)
    if (group) {
      if (!processedGroups.has(group.mergeGroupId)) {
        processedGroups.add(group.mergeGroupId)
        // 取合并组中第一个可见状态的颜色
        const firstVisibleStatus = cols.find(c => group.statusIds.includes(c.id))
        result.push({
          id: group.mergeGroupId,
          name: group.mergeTitle,
          color: firstVisibleStatus?.color || '',
          category: firstVisibleStatus?.category || '',
          statusIds: group.statusIds.filter(sid => cols.some(c => c.id === sid)),
          isMerged: true,
          sortOrder: s.sortOrder
        })
      }
      // 跳过合并组中的后续状态
    } else {
      // 普通列
      result.push({
        id: s.id,
        name: s.name,
        color: s.color || '',
        category: s.category || '',
        statusIds: [s.id],
        isMerged: false,
        sortOrder: s.sortOrder
      })
    }
  }

  return result
})

/** 获取有效列（考虑合并）中某列的所有工单 */
function getEffectiveColumnIssues(column: EffectiveColumn): BoardIssue[] {
  return issues.value.filter(i => column.statusIds.includes(i.statusId))
}

/** 获取 Swimlane 中某有效列的工单 */
function getSwimlaneEffectiveColumnIssues(laneKey: string, column: EffectiveColumn): BoardIssue[] {
  const lane = swimlanes.value.find(l => l.key === laneKey)
  if (!lane) return []
  return lane.issues.filter(i => column.statusIds.includes(i.statusId))
}

// Comma-separated visible board status IDs for Backlog exclusion
const boardStatusIdsForBacklog = computed(() => {
  return visibleStatuses.value.map(s => s.id).join(',')
})

// 隐藏列中有工单的列（用于提示 banner）
const hiddenIssueColumns = computed(() => {
  return allColumnConfigs.value.filter(c => !c.visible && c.hasHiddenIssues && (c.issueCount || 0) > 0)
})

// 临时显示全部列（不修改持久化配置）
const showAllColumns = ref(false)

// 当用户切换"显示全部列"时，重新加载工单数据（后端需要返回隐藏列的工单）
watch(showAllColumns, () => {
  if (selectedProject.value) {
    loadIssuesWithLoading()
  }
})

// 当 userId 变为可用时，如果"仅我的"过滤器已启用，重新加载看板数据
watch(() => authStore.user?.userId, (newUserId, oldUserId) => {
  if (newUserId && !oldUserId && assigneeFilter.value === 'me' && selectedProject.value) {
    loadIssuesWithLoading()
  }
})

// 隐藏工单总数
const hiddenIssueTotalCount = computed(() => {
  return hiddenIssueColumns.value.reduce((sum, c) => sum + (c.issueCount || 0), 0)
})

// 隐藏列详情文字（状态名:数量）
const hiddenIssueColumnsDetail = computed(() => {
  return hiddenIssueColumns.value
    .map(c => `${c.statusName} ${c.issueCount || 0}个`)
    .join('、')
})

/**
 * 隐藏状态标签点击处理（REQ-753）：
 * 临时显示全部列并滚动到被点击的状态列。
 */
function onHiddenStatusTagClick(col: BoardColumnVO) {
  if (!col.statusId && !col.fieldValue) return
  // 先展示全部列
  showAllColumns.value = true
  // 等待 DOM 更新后滚动到目标列
  const targetId = col.statusId || col.fieldValue
  nextTick(() => {
    setTimeout(() => {
      scrollToColumn(targetId)
    }, 100)
  })
}

// 被手动展开的空列集合
const expandedEmptyColumns = ref<Set<string>>(new Set())

// ===== 手动折叠的列（任何列，含有工单的也可以折叠） =====
const COLLAPSED_COLUMNS_KEY_PREFIX = 'tf_kanban_collapsed_columns'

function getCollapsedColumnsKey(): string {
  return selectedProject.value
    ? `${COLLAPSED_COLUMNS_KEY_PREFIX}_${selectedProject.value}`
    : COLLAPSED_COLUMNS_KEY_PREFIX
}

const collapsedColumns = ref<Set<string>>(new Set())

/** 从 localStorage 加载当前项目的折叠列状态 */
function loadCollapsedColumnsState() {
  const key = getCollapsedColumnsKey()
  const stored = localStorage.getItem(key)
  collapsedColumns.value = new Set(stored ? JSON.parse(stored) : [])
}

/** 保存折叠列状态到 localStorage */
function saveCollapsedColumnsState() {
  const key = getCollapsedColumnsKey()
  if (collapsedColumns.value.size === 0) {
    localStorage.removeItem(key)
  } else {
    localStorage.setItem(key, JSON.stringify([...collapsedColumns.value]))
  }
}

/** 判断列是否处于折叠状态（手动折叠或空列自动折叠）
 * columnId: 对于普通列为 statusId，对于合并列为 mergeGroupId
 */
function isColumnCollapsed(columnId: string): boolean {
  // 手动折叠优先级最高
  if (collapsedColumns.value.has(columnId)) return true
  // 空列且未手动展开 → 自动折叠
  const col = effectiveColumns.value.find(c => c.id === columnId)
  if (col) {
    if (getEffectiveColumnIssues(col).length === 0 && !expandedEmptyColumns.value.has(columnId)) return true
  } else {
    // fallback: 单状态列
    if (getColumnIssues(columnId).length === 0 && !expandedEmptyColumns.value.has(columnId)) return true
  }
  return false
}

/** 切换列的折叠/展开状态
 * columnId: 对于普通列为 statusId，对于合并列为 mergeGroupId
 */
function toggleColumnCollapse(columnId: string) {
  if (collapsedColumns.value.has(columnId)) {
    // 展开
    collapsedColumns.value.delete(columnId)
    expandedEmptyColumns.value.add(columnId) // 确保空列也被展开
  } else {
    // 折叠
    collapsedColumns.value.add(columnId)
    expandedEmptyColumns.value.delete(columnId)
  }
  saveCollapsedColumnsState()
}

/**
 * 获取有效列（合并列或普通列）对应的拖拽放置目标 statusId。
 * 对于合并列，使用组内第一个状态；对于普通列直接返回自身 ID。
 */
function getDropTargetStatusId(col: EffectiveColumn): string {
  return col.statusIds[0] ?? col.id
}

/**
 * 判断有效列是否为已关闭列（合并列：所有子状态均为终态才算终态）
 */
function isEffectiveColumnClosed(col: EffectiveColumn): boolean {
  if (!col.isMerged) {
    return col.category === 'done' || col.category === 'cancelled'
  }
  // 合并列：全部子状态都是终态才算终态
  return col.statusIds.every(sid => {
    const config = allColumnConfigs.value.find(c => c.statusId === sid)
    if (config) return config.statusCategory === 'done' || config.statusCategory === 'cancelled'
    return false
  })
}

/**
 * 获取有效列的预估工时（合并列：所有子状态之和）
 */
function getEffectiveColumnEstimation(col: EffectiveColumn): string {
  let total = 0
  for (const sid of col.statusIds) {
    const config = getColumnConfig(sid)
    if (config && config.totalEstimation) {
      total += Number(config.totalEstimation)
    }
  }
  if (total <= 0) return ''
  return total % 1 === 0 ? String(total) : total.toFixed(1)
}

/**
 * 获取有效列的 WIP Max（合并列：各子状态 WIP Max 之和；任一无限制则整列无限制）
 */
function getEffectiveColumnWipMax(col: EffectiveColumn): number | null {
  if (!col.isMerged) return getWipMax(col.id)
  let total = 0
  for (const sid of col.statusIds) {
    const max = getWipMax(sid)
    if (max === null) return null // 无限制
    total += max
  }
  return total || null
}

/**
 * 获取有效列的 WIP 警告状态（合并列：使用合并后的工单数和 WIP 限制计算）
 */
function getEffectiveColumnWipWarning(col: EffectiveColumn): 'wip-over' | 'wip-under' | null {
  if (!col.isMerged) return getWipWarning(col.id)
  const count = getEffectiveColumnIssues(col).length
  // 合并列：检查各子状态 WIP 限制之和
  let totalWipMin = 0
  let totalWipMax = 0
  let hasWipMax = false
  let hasWipMin = false
  for (const sid of col.statusIds) {
    const config = getColumnConfig(sid)
    if (config?.wipMax != null) { totalWipMax += config.wipMax; hasWipMax = true }
    if (config?.wipMin != null) { totalWipMin += config.wipMin; hasWipMin = true }
  }
  if (hasWipMax && count > totalWipMax) return 'wip-over'
  if (hasWipMin && count < totalWipMin) return 'wip-under'
  return null
}

/**
 * 获取有效列的 WIP CSS class
 */
function getEffectiveColumnWipClass(col: EffectiveColumn): string {
  if (!col.isMerged) return getWipClass(col.id)
  const warning = getEffectiveColumnWipWarning(col)
  if (warning === 'wip-over') return 'wip-over'
  if (warning === 'wip-under') return 'wip-under'
  return ''
}

/**
 * 判断有效列的拖拽是否允许放置
 * 合并列：只要任一子状态允许放置即可
 */
function isEffectiveColumnDropAllowed(col: EffectiveColumn): boolean {
  return col.statusIds.some(sid => isDropAllowed(sid))
}

// ===== Progress Indicator（各列卡片数 mini bar chart） =====

/** 计算进度条高度（相对活跃状态中最大列的比例） */
function getProgressBarHeight(statusId: string): string {
  const count = getColumnIssues(statusId).length
  if (count === 0) return '2px'
  const maxCount = Math.max(...activeStatuses.value.map(s => getColumnIssues(s.id).length), 1)
  const height = Math.max(4, Math.round((count / maxCount) * 24))
  return `${height}px`
}

/** 计算终态汇总柱高度（相对活跃状态按比例缩放，但上限为 16px 以弱化视觉） */
function getClosedProgressBarHeight(): string {
  if (closedIssueCount.value === 0) return '2px'
  const maxActiveCount = Math.max(...activeStatuses.value.map(s => getColumnIssues(s.id).length), 1)
  // 使用对数缩放 — 终态工单再多也不会压过活跃柱形
  const ratio = Math.min(closedIssueCount.value / maxActiveCount, 3)
  const height = Math.max(4, Math.min(16, Math.round(ratio * 8)))
  return `${height}px`
}

/** 滚动到指定列（支持普通列和合并列）
 * statusId: 可以是普通状态 ID 或合并列 ID（mergeGroupId）
 */
function scrollToColumn(statusId: string) {
  // 查找包含此 statusId 的有效列（可能是合并列）
  const col = effectiveColumns.value.find(c => c.statusIds.includes(statusId)) ||
              effectiveColumns.value.find(c => c.id === statusId)
  const targetId = col ? col.id : statusId

  // 如果列被折叠，先展开
  if (collapsedColumns.value.has(targetId)) {
    toggleColumnCollapse(targetId)
  }
  // 在下一帧滚动到目标列
  setTimeout(() => {
    const container = document.querySelector('.board-container') || document.querySelector('.swimlane-container')
    if (!container) return
    const columnEl = container.querySelector(`[data-column-id="${targetId}"]`)
    if (columnEl) {
      columnEl.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
    }
  }, 50)
}

// ===== 拖拽逻辑（委托到 useBoardDrag composable） =====
const {
  draggingIssue, dragOverColumnId, allowedTargetStatuses, requireCommentStatuses,
  transitioningIssueIds, transitionableSourceStatuses, isDragging, undoStack,
  isCardDraggable, onDragStart, onDragEnd, onDragOver, onDragLeave, isDropAllowed
} = useBoardDrag({
  statuses,
  boardColumnField,
  visibleStatuses,
  isManualSortDisabled,
  canChangeStatus,
  backlogDraggingIssue,
  dragOverSwimlaneKey
})

// ===== 撤销历史（state managed by useBoardDrag, business logic below） =====

function getColumnIssues(statusId: string): BoardIssue[] {
  let columnIssues: BoardIssue[]
  if (boardColumnField.value === 'priority') {
    // Priority mode: statusId parameter is actually the priority value (Critical/High/Normal/Low)
    columnIssues = issues.value.filter(i => (i.priority || 'Normal') === statusId)
  } else {
    // Default: status mode
    columnIssues = issues.value.filter(i => i.statusId === statusId)
  }

  // Apply manual order if available and not disabled
  if (boardManualSorted.value && !isManualSortDisabled.value) {
    const { sorted, rest } = applyBoardManualOrder(columnIssues)
    return [...sorted, ...rest]
  }

  return columnIssues
}

// ===== WIP 限制辅助函数 =====

function getColumnConfig(statusId: string): BoardColumnVO | undefined {
  if (boardColumnField.value === 'priority') {
    // 优先级模式：statusId 参数实际是 priority 值（Critical/High/Normal/Low）
    // 需按 fieldValue 查找，且这些列的 statusId 为 null
    return allColumnConfigs.value.find(c => c.fieldValue === statusId && c.statusId == null)
  }
  return allColumnConfigs.value.find(c => c.statusId === statusId)
}

function getWipMin(statusId: string): number | null {
  return getColumnConfig(statusId)?.wipMin ?? null
}

function getWipMax(statusId: string): number | null {
  return getColumnConfig(statusId)?.wipMax ?? null
}

/**
 * 判断列的 WIP 状态：'wip-over' | 'wip-under' | null
 */
function getWipWarning(statusId: string): 'wip-over' | 'wip-under' | null {
  const config = getColumnConfig(statusId)
  if (!config) return null
  const count = getColumnIssues(statusId).length
  if (config.wipMax != null && count > config.wipMax) return 'wip-over'
  if (config.wipMin != null && count < config.wipMin) return 'wip-under'
  return null
}

/**
 * 列计数的 CSS class（用于颜色变化）
 */
function getWipClass(statusId: string): string {
  const warning = getWipWarning(statusId)
  if (warning === 'wip-over') return 'column-count--over'
  if (warning === 'wip-under') return 'column-count--under'
  return ''
}

/**
 * WIP tooltip 文本
 */
function getWipTooltip(statusId: string): string {
  const config = getColumnConfig(statusId)
  if (!config) return ''
  const count = getColumnIssues(statusId).length
  const parts: string[] = []
  if (config.wipMin != null) parts.push(`最小: ${config.wipMin}`)
  if (config.wipMax != null) parts.push(`最大: ${config.wipMax}`)
  if (parts.length === 0) return `${count} 个工单`
  const warning = getWipWarning(statusId)
  let suffix = ''
  if (warning === 'wip-over') suffix = ' ⚠️ 超出限制'
  if (warning === 'wip-under') suffix = ' ⚠️ 低于最小值'
  return `${count} 个工单 (${parts.join(', ')})${suffix}`
}

/**
 * 获取列的预估工时总和（从列配置数据中读取）
 */
function getColumnEstimation(statusId: string): string {
  const config = getColumnConfig(statusId)
  if (!config || !config.totalEstimation) return ''
  // 格式化：去除尾部多余的零
  const val = Number(config.totalEstimation)
  if (val <= 0) return ''
  return val % 1 === 0 ? String(val) : val.toFixed(1)
}

/**
 * 获取看板总预估工时（所有可见列的 totalEstimation 总和）
 */
const boardTotalEstimation = computed(() => {
  if (!allColumnConfigs.value || allColumnConfigs.value.length === 0) return 0
  let total = 0
  for (const col of allColumnConfigs.value) {
    if (col.visible && col.totalEstimation) {
      total += Number(col.totalEstimation)
    }
  }
  return total
})

function expandColumn(statusId: string) {
  collapsedColumns.value.delete(statusId)
  expandedEmptyColumns.value.add(statusId)
  saveCollapsedColumnsState()
}

function collapseColumn(statusId: string) {
  collapsedColumns.value.add(statusId)
  expandedEmptyColumns.value.delete(statusId)
  saveCollapsedColumnsState()
}

function priorityIcon(priority: string): string {
  switch (priority) {
    case '阻塞': return '⛔'
    case '紧急': case 'Critical': return '🔴'
    case '高': case 'High': return '🟠'
    case '普通': case 'Normal': return '🔵'
    case '低': case 'Low': return '🟢'
    default: return '⚪'
  }
}

function typeLabel(type: string): string {
  return type
}

/**
 * 返回工单类型的首字母缩写（用于 Initial 显示模式）。
 */
function typeInitial(type: string): string {
  if (!type) return '?'
  return type.charAt(0).toUpperCase()
}

function openIssue(issue: BoardIssue) {
  if (draggingIssue.value) return
  openPreview(issue)
}

/**
 * 卡片单击处理：
 * - Ctrl/Meta + Click：切换选中状态（多选）
 * - 无修饰键：延迟 200ms 打开预览（被双击取消则不打开）
 */
let clickTimer: ReturnType<typeof setTimeout> | null = null

function onCardClick(event: MouseEvent | KeyboardEvent, issue: BoardIssue) {
  if (draggingIssue.value) return

  if (event instanceof MouseEvent && (event.ctrlKey || event.metaKey)) {
    // Ctrl+Click: toggle this card's selection (batch mode)
    toggleCardSelection(issue.id)
  } else if (selectedCount.value > 0) {
    // Already have selection: toggle this card (stay in batch mode)
    toggleCardSelection(issue.id)
  } else {
    // No modifier, no existing selection: delay to distinguish from double-click
    if (clickTimer) clearTimeout(clickTimer)
    clickTimer = setTimeout(() => {
      openPreview(issue)
      clickTimer = null
    }, 200)
  }
}

/**
 * 卡片双击：跳转详情页（取消单击的预览）
 */
function onCardDblClick(issue: BoardIssue) {
  if (draggingIssue.value) return
  // Cancel the pending single-click preview
  if (clickTimer) {
    clearTimeout(clickTimer)
    clickTimer = null
  }
  // Close preview if open
  previewVisible.value = false
  router.push({ name: 'IssueDetail', params: { id: issue.id } })
}

/**
 * 卡片 Enter 键：若有选中则切换选中状态，否则打开预览
 * Space 键：打开预览（保持与单击一致）
 */
function onCardKeydown(event: KeyboardEvent, issue: BoardIssue) {
  if (event.key === 'Enter') {
    if (selectedCount.value > 0 || event.ctrlKey || event.metaKey) {
      toggleCardSelection(issue.id)
    } else {
      openPreview(issue)
    }
  }
  if (event.key === ' ') {
    event.preventDefault()
    openPreview(issue)
  }
  // Escape 清空选择或关闭预览
  if (event.key === 'Escape') {
    if (selectedCount.value > 0) {
      clearSelection()
    } else if (previewVisible.value) {
      closePreview()
    }
  }
}

// ===== 拖拽业务逻辑（基础事件处理在 useBoardDrag composable，此处只保留 onDrop 业务逻辑） =====

async function onDrop(event: DragEvent, targetStatusId: string) {
  event.preventDefault()

  // ★ Save the target swimlane key BEFORE clearing (for cross-swimlane drop handling)
  const targetLaneKey = dragOverSwimlaneKey.value

  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null

  // Check if this is a backlog drop
  const backlogIssue = backlogDraggingIssue.value
  if (backlogIssue) {
    await handleBacklogDrop(backlogIssue, targetStatusId)
    return
  }

  const issue = draggingIssue.value
  if (!issue || !isDropAllowed(targetStatusId)) {
    onDragEnd()
    return
  }

  // Within-column drop: reorder card (manual sorting)
  const isWithinColumnDrop = (boardColumnField.value === 'priority')
    ? (issue.priority || 'Normal') === targetStatusId
    : issue.statusId === targetStatusId

  if (isWithinColumnDrop) {
    // ★ Even if same column, check for cross-swimlane movement (YouTrack behavior:
    //    dragging to different row same column → only update swimlane field)
    if (targetLaneKey && swimlaneGroupBy.value !== 'none') {
      // Determine current lane key for the issue
      let currentLaneKey: string | null = null
      switch (swimlaneGroupBy.value) {
        case 'assignee': currentLaneKey = issue.assigneeId || '__unassigned__'; break
        case 'priority': currentLaneKey = issue.priority || 'Normal'; break
        case 'type': currentLaneKey = issue.issueType; break
        case 'sprint': currentLaneKey = issue.sprintId || '__no_sprint__'; break
        case 'tag': currentLaneKey = null; break
        case 'parent': currentLaneKey = ('parentId' in issue ? issue.parentId : undefined) || '__uncategorized__'; break
      }
      if (currentLaneKey !== targetLaneKey) {
        // Cross-swimlane, same column → update swimlane field only
        draggingIssue.value = null
        allowedTargetStatuses.value.clear()
        await handleCrossSwimlaneUpdate(issue, targetLaneKey)
        return
      }
    }
    // True within-column drop (same column, same swimlane) → reorder
    if (!isManualSortDisabled.value) {
      await handleWithinColumnReorder(issue, targetStatusId, event)
      return
    }
  }

  // Priority mode: update priority field instead of status transition
  if (boardColumnField.value === 'priority') {
    const oldPriority = issue.priority || 'Normal'
    const targetPriority = targetStatusId // In priority mode, targetStatusId is the priority value
    if (oldPriority === targetPriority) {
      onDragEnd()
      return
    }

    // WIP 限制检查（本地）
    const targetColConfig = getColumnConfig(targetPriority)
    if (targetColConfig?.wipMax != null) {
      const currentCount = getColumnIssues(targetPriority).length
      if (currentCount >= targetColConfig.wipMax) {
        // WIP 超限：弹出确认框（与状态模式一致的交互）
        draggingIssue.value = null
        allowedTargetStatuses.value.clear()
        Modal.warning({
          title: 'WIP 限制',
          content: `目标优先级列「${targetPriority}」已达到 WIP 上限（${currentCount}/${targetColConfig.wipMax}），确定要继续移入吗？`,
          okText: '继续移入',
          cancelText: '取消',
          hideCancel: false,
          onOk: async () => {
            // 用户确认后执行（带 forceWip=true）
            issue.priority = targetPriority
            flushIssues()
            try {
              await issueApi.update(issue.id, { priority: targetPriority, forceWip: true })
              issue.version = (issue.version || 0) + 1
              flushIssues()
              Message.success(`${issue.issueKey} 优先级已变更为「${targetPriority}」`)
              await handleCrossSwimlaneUpdate(issue, targetLaneKey)
            } catch (e: any) {
              issue.priority = oldPriority
              flushIssues()
              Message.error(`优先级变更失败：${e.response?.data?.message || '未知错误'}`)
            }
          }
        })
        return
      }
    }

    // Optimistic update
    issue.priority = targetPriority
    flushIssues()
    draggingIssue.value = null
    allowedTargetStatuses.value.clear()
    try {
      await issueApi.update(issue.id, { priority: targetPriority })
      issue.version = (issue.version || 0) + 1
      flushIssues()
      Message.success(`${issue.issueKey} 优先级已变更为「${targetPriority}」`)
      // ★ Cross-swimlane field update in priority mode
      await handleCrossSwimlaneUpdate(issue, targetLaneKey)
    } catch (e: any) {
      if (e.response?.data?.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
        // 后端防御性 WIP 校验触发（前端遗漏的情况）
        issue.priority = oldPriority
        flushIssues()
        Modal.warning({
          title: 'WIP 限制',
          content: e.response.data.message,
          okText: '继续移入',
          cancelText: '取消',
          hideCancel: false,
          onOk: async () => {
            issue.priority = targetPriority
            flushIssues()
            try {
              await issueApi.update(issue.id, { priority: targetPriority, forceWip: true })
              issue.version = (issue.version || 0) + 1
              flushIssues()
              Message.success(`${issue.issueKey} 优先级已变更为「${targetPriority}」`)
              await handleCrossSwimlaneUpdate(issue, targetLaneKey)
            } catch (e2: any) {
              issue.priority = oldPriority
              flushIssues()
              Message.error(`优先级变更失败：${e2.response?.data?.message || '未知错误'}`)
            }
          }
        })
      } else {
        issue.priority = oldPriority
        flushIssues()
        Message.error(`优先级变更失败：${e.response?.data?.message || '未知错误'}`)
      }
    }
    return
  }

  const oldStatusId = issue.statusId
  const targetStatus = statuses.value.find(s => s.id === targetStatusId)

  // If the target status requires a comment, prompt the user before executing
  if (requireCommentStatuses.value.has(targetStatusId)) {
    draggingIssue.value = null
    allowedTargetStatuses.value.clear()
    requireCommentStatuses.value.clear()
    let commentText = ''
    Modal.confirm({
      title: '状态变更 — 请填写理由',
      content: () => h('div', { style: 'display:flex;flex-direction:column;gap:8px' }, [
        h('div', { style: 'display:flex;align-items:center;gap:6px' }, [
          h('span', { style: 'color:var(--color-text-3);font-size:13px' }, '目标状态：'),
          h('span', { style: `background:${targetStatus?.color || '#6b7280'};color:#fff;padding:2px 8px;border-radius:3px;font-size:12px` }, targetStatus?.name || '')
        ]),
        h('textarea', {
          placeholder: '请说明退回/变更的原因（必填）',
          style: 'width:100%;min-height:80px;margin-top:8px;padding:8px;border:1px solid var(--color-border-2);border-radius:4px;resize:vertical;font-size:13px;background:var(--color-bg-2);color:var(--color-text-1)',
          onInput: (e: Event) => { commentText = (e.target as HTMLTextAreaElement).value }
        })
      ]),
      okText: '确认变更',
      cancelText: '取消',
      width: 480,
      onBeforeOk: () => {
        if (!commentText.trim()) {
          Message.warning('请填写变更理由')
          return false
        }
        return true
      },
      onOk: async () => {
        issue.statusId = targetStatusId
        flushIssues()
        transitioningIssueIds.value.add(issue.id)
        try {
          const res = await issueApi.transitStatus(issue.id, targetStatusId, commentText.trim(), issue.version)
          if (res.code === 0) {
            const newVersion = extractVersion(res.data)
            if (newVersion != null) issue.version = newVersion
            else issue.version = (issue.version || 0) + 1
            showActionFeedback(res.data)
            useNavBadge().refresh() // 状态变更后刷新导航栏 badge
            pushUndoNotification(issue, oldStatusId, targetStatusId, targetStatus)
            // ★ Cross-swimlane field update after comment-required transition
            await handleCrossSwimlaneUpdate(issue, targetLaneKey)
          } else {
            issue.statusId = oldStatusId
            flushIssues()
            Message.error(res.message || '状态变更失败')
          }
        } catch (e: any) {
          issue.statusId = oldStatusId
          flushIssues()
          Message.error(e.response?.data?.message || '状态变更失败')
        } finally {
          transitioningIssueIds.value.delete(issue.id)
        }
      }
    })
    return
  }

  // 乐观更新
  issue.statusId = targetStatusId
  flushIssues()
  transitioningIssueIds.value.add(issue.id)

  draggingIssue.value = null
  allowedTargetStatuses.value.clear()

  try {
    const res = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version)

    if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
      // WIP 超限：回滚乐观更新，弹确认框
      issue.statusId = oldStatusId
      flushIssues()
      transitioningIssueIds.value.delete(issue.id)
      Modal.warning({
        title: 'WIP 限制',
        content: res.message,
        okText: '继续移入',
        cancelText: '取消',
        hideCancel: false,
        onOk: async () => {
          // 用户确认后重试（带 forceWip）
          issue.statusId = targetStatusId
          flushIssues()
          transitioningIssueIds.value.add(issue.id)
          try {
            const forceRes = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version, undefined, true)
            if (forceRes.code === 0) {
              const newVersion = extractVersion(forceRes.data)
              if (newVersion != null) issue.version = newVersion
              else issue.version = (issue.version || 0) + 1
              showActionFeedback(forceRes.data)
              useNavBadge().refresh() // 状态变更后刷新导航栏 badge
              pushUndoNotification(issue, oldStatusId, targetStatusId, targetStatus)
              // ★ Cross-swimlane field update after WIP force transition
              await handleCrossSwimlaneUpdate(issue, targetLaneKey)
            } else {
              issue.statusId = oldStatusId
              flushIssues()
              Message.error(forceRes.message || '状态变更失败')
            }
          } catch (e2: any) {
            issue.statusId = oldStatusId
            flushIssues()
            Message.error(e2.response?.data?.message || '状态变更失败')
          } finally {
            transitioningIssueIds.value.delete(issue.id)
          }
        }
      })
      return
    }

    if (res.code === ERROR_CODES.DESCRIPTION_EMPTY_WARNING) {
      // 描述为空警告：回滚乐观更新，弹确认框
      issue.statusId = oldStatusId
      flushIssues()
      transitioningIssueIds.value.delete(issue.id)
      Modal.warning({
        title: '工单描述为空',
        content: res.message,
        okText: '继续变更',
        cancelText: '取消',
        hideCancel: false,
        onOk: async () => {
          // 用户确认后重试（带 forceDescEmpty）
          issue.statusId = targetStatusId
          flushIssues()
          transitioningIssueIds.value.add(issue.id)
          try {
            const forceRes = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version, undefined, undefined, true)
            if (forceRes.code === 0) {
              const newVersion = extractVersion(forceRes.data)
              if (newVersion != null) issue.version = newVersion
              else issue.version = (issue.version || 0) + 1
              showActionFeedback(forceRes.data)
              useNavBadge().refresh() // 状态变更后刷新导航栏 badge
              pushUndoNotification(issue, oldStatusId, targetStatusId, targetStatus)
              // ★ Cross-swimlane field update after description empty force transition
              await handleCrossSwimlaneUpdate(issue, targetLaneKey)
            } else {
              issue.statusId = oldStatusId
              flushIssues()
              Message.error(forceRes.message || '状态变更失败')
            }
          } catch (e2: any) {
            issue.statusId = oldStatusId
            flushIssues()
            Message.error(e2.response?.data?.message || '状态变更失败')
          } finally {
            transitioningIssueIds.value.delete(issue.id)
          }
        }
      })
      return
    }

    if (res.code !== 0) {
      // 其他非成功响应
      issue.statusId = oldStatusId
      flushIssues()
      Message.error(res.message || '状态变更失败')
      transitioningIssueIds.value.delete(issue.id)
      return
    }

    // 检查是否为字段校验失败（状态转换被阻止）
    const actionResult = res.data?.actionResult
    if (actionResult?.outcome === 'FIELD_VALIDATION_FAILED') {
      // 回滚乐观更新
      issue.statusId = oldStatusId
      flushIssues()
      transitioningIssueIds.value.delete(issue.id)
      // 显示警告消息
      Modal.warning({
        title: '字段校验',
        content: actionResult.warningMessage || `请先填写「${actionResult.requiredFieldName}」字段`,
        okText: '知道了'
      })
      return
    }

    // 同步更新本地版本号（后端返回 TransitStatusResultVO）
    const newVersion = extractVersion(res.data)
    if (newVersion != null) {
      issue.version = newVersion
      flushIssues()
    } else {
      // fallback: 本地递增
      issue.version = (issue.version || 0) + 1
      flushIssues()
    }

    // 显示自动分配反馈
    showActionFeedback(res.data)
    useNavBadge().refresh() // 状态变更后刷新导航栏 badge

    pushUndoNotification(issue, oldStatusId, targetStatusId, targetStatus)

    // ★ Cross-swimlane field update (YouTrack behavior: drag to different row updates swimlane field)
    await handleCrossSwimlaneUpdate(issue, targetLaneKey)
  } catch (e: any) {
    issue.statusId = oldStatusId
    flushIssues()
    const errMsg = e.response?.data?.message || '状态变更失败'
    Message.error(`${issue.issueKey} 移动失败：${errMsg}`)
  } finally {
    transitioningIssueIds.value.delete(issue.id)
  }
}

/**
 * Handle cross-swimlane drop: update the swimlane field value.
 * Per YouTrack behavior:
 * - When swimlane is by field value (assignee/priority/type/sprint),
 *   dragging a card to another swimlane row updates that field.
 * - Returns true if a cross-swimlane update was performed.
 */
async function handleCrossSwimlaneUpdate(issue: BoardIssue, targetLaneKey: string | null): Promise<boolean> {
  // No swimlane mode or no target lane → nothing to do
  if (!targetLaneKey || swimlaneGroupBy.value === 'none') return false

  // Determine the issue's current swimlane key based on the swimlaneGroupBy dimension
  let currentLaneKey: string | null = null
  switch (swimlaneGroupBy.value) {
    case 'assignee':
      currentLaneKey = issue.assigneeId || '__unassigned__'
      break
    case 'priority':
      currentLaneKey = issue.priority || 'Normal'
      break
    case 'type':
      currentLaneKey = issue.issueType
      break
    case 'sprint':
      currentLaneKey = issue.sprintId || '__no_sprint__'
      break
    case 'tag':
      // Tag swimlane uses tag id as key; complex to handle — skip for now
      return false
    case 'parent':
      currentLaneKey = ('parentId' in issue ? issue.parentId : undefined) || '__uncategorized__'
      break
    case 'dueDate':
      // 日期泳道为只读：拖拽不更新截止日期（日期按相对范围分组，不是可选泳道值）
      return false
  }

  // If the issue is already in the target swimlane, nothing to do
  if (currentLaneKey === targetLaneKey) return false

  // Build the update payload based on swimlaneGroupBy
  const updateData: Partial<Pick<IssueVO, 'assigneeId' | 'priority' | 'issueType' | 'sprintId'> & { parentId: string | null; addToSprintId: string }> = {}
  switch (swimlaneGroupBy.value) {
    case 'assignee':
      // Special keys like '__unassigned__' mean set to null
      if (targetLaneKey === '__unassigned__' || targetLaneKey === '__uncategorized__') {
        updateData.assigneeId = undefined
      } else {
        updateData.assigneeId = targetLaneKey
      }
      break
    case 'priority':
      updateData.priority = targetLaneKey
      break
    case 'type':
      updateData.issueType = targetLaneKey
      break
    case 'sprint':
      if (targetLaneKey === '__no_sprint__' || targetLaneKey === '__uncategorized__') {
        updateData.sprintId = undefined
      } else if (allowMultipleSprints.value) {
        // 多 Sprint 模式：追加而非覆盖
        updateData.addToSprintId = targetLaneKey
      } else {
        updateData.sprintId = targetLaneKey
      }
      break
    case 'parent':
      // Moving to a parent swimlane sets the parentId; moving to uncategorized clears it
      if (targetLaneKey === '__uncategorized__') {
        updateData.parentId = null
      } else {
        updateData.parentId = targetLaneKey
      }
      break
    default:
      return false
  }

  // Perform the update (optimistic + API call)
  // Optimistic update - apply changes locally first
  const rollbackData: Record<string, string | undefined> = {}
  if ('assigneeId' in updateData) {
    rollbackData.assigneeId = issue.assigneeId
    rollbackData.assigneeName = issue.assigneeName
    issue.assigneeId = updateData.assigneeId || undefined
    // We don't know the assignee name for optimistic update;
    // it will be resolved after reload or from projectMembers
    if (updateData.assigneeId) {
      const member = projectMembers.value.find(m => m.userId === updateData.assigneeId)
      issue.assigneeName = member?.displayName || ''
    } else {
      issue.assigneeName = undefined
    }
  }
  if ('priority' in updateData) {
    rollbackData.priority = issue.priority
    issue.priority = updateData.priority!
  }
  if ('issueType' in updateData) {
    rollbackData.issueType = issue.issueType
    issue.issueType = updateData.issueType!
  }
  if ('sprintId' in updateData) {
    rollbackData.sprintId = issue.sprintId
    issue.sprintId = updateData.sprintId || undefined
  }
  if ('addToSprintId' in updateData) {
    // 多 Sprint 模式：乐观更新本地显示（让卡片移动到目标泳道）
    rollbackData.sprintId = issue.sprintId
    issue.sprintId = updateData.addToSprintId || undefined
  }
  if ('parentId' in updateData) {
    rollbackData.parentId = 'parentId' in issue ? (issue as BoardCardVO).parentId : undefined
    if ('parentId' in issue) {
      (issue as BoardCardVO).parentId = updateData.parentId || undefined
    }
  }

  try {
    const updateRes = await issueApi.update(issue.id, updateData as any)
    if (updateRes.warnings?.length) {
      updateRes.warnings.forEach((w: string) => Message.warning({ content: w, duration: 5000 }))
      // Rollback optimistic update for skipped fields
      if ('sprintId' in rollbackData) {
        issue.sprintId = rollbackData.sprintId
      }
      return false
    }
    issue.version = (issue.version || 0) + 1
    // Build a descriptive message for the swimlane change
    const fieldLabel = swimlaneGroupBy.value === 'assignee' ? '负责人'
      : swimlaneGroupBy.value === 'priority' ? '优先级'
      : swimlaneGroupBy.value === 'type' ? '类型'
      : swimlaneGroupBy.value === 'sprint' ? '迭代'
      : swimlaneGroupBy.value === 'parent' ? '父工单' : ''
    if (fieldLabel) {
      Message.success(`${issue.issueKey} ${fieldLabel}已更新`)
    }
    return true
  } catch (e: unknown) {
    // Rollback optimistic update
    if ('assigneeId' in rollbackData) {
      issue.assigneeId = rollbackData.assigneeId
      issue.assigneeName = rollbackData.assigneeName
    }
    if ('priority' in rollbackData) {
      issue.priority = rollbackData.priority!
    }
    if ('issueType' in rollbackData) {
      issue.issueType = rollbackData.issueType!
    }
    if ('sprintId' in rollbackData) {
      issue.sprintId = rollbackData.sprintId
    }
    if ('parentId' in rollbackData && 'parentId' in issue) {
      (issue as BoardCardVO).parentId = rollbackData.parentId
    }
    const err = e as { response?: { data?: { message?: string } } }
    const errMsg = err.response?.data?.message || '字段更新失败'
    Message.error(`${issue.issueKey} 跨泳道更新失败：${errMsg}`)
    return false
  }
}

/** Handle within-column drop: reorder card using manual order API */
async function handleWithinColumnReorder(issue: BoardIssue, columnId: string, event: DragEvent) {
  // Determine the drop target position within the column
  const columnIssues = getColumnIssues(columnId)
  const draggedIndex = columnIssues.findIndex(i => i.id === issue.id)

  // Get the drop target element to determine position
  const dropTarget = event.target as HTMLElement | null
  let targetIndex = columnIssues.length - 1 // default: drop at end

  if (dropTarget) {
    // Find the closest card element to determine insertion point
    const cardEl = dropTarget.closest('.kanban-card') as HTMLElement | null
    if (cardEl) {
      // Find which issue this card belongs to
      const cardIndex = columnIssues.findIndex(i => {
        // Use issue key to match (card-key contains it)
        const keyEl = cardEl.querySelector('.card-key')
        return keyEl && keyEl.textContent === i.issueKey
      })
      if (cardIndex >= 0 && cardIndex !== draggedIndex) {
        // Determine if dropped above or below the target card
        const rect = cardEl.getBoundingClientRect()
        const dropY = event.clientY
        const midY = rect.top + rect.height / 2
        targetIndex = dropY < midY ? cardIndex : cardIndex + 1
        if (targetIndex > draggedIndex) targetIndex-- // Adjust for removal
      }
    }
  }

  // Only reorder if position actually changed
  if (draggedIndex === targetIndex) {
    onDragEnd()
    return
  }

  // Build new order for all issues (not just this column)
  const allIssueIds = issues.value.map(i => i.id)

  // Reorder within the column: remove from old position, insert at new position
  const reorderedColumn = [...columnIssues]
  const [moved] = reorderedColumn.splice(draggedIndex, 1)
  reorderedColumn.splice(targetIndex, 0, moved)

  // Build the full issue order list (preserving order of other columns, updating this column)
  const columnIssueSet = new Set(columnIssues.map(i => i.id))
  const fullOrder: string[] = []
  let columnInserted = false

  for (const id of allIssueIds) {
    if (columnIssueSet.has(id)) {
      if (!columnInserted) {
        // Insert all reordered column issues at the position of the first column issue
        fullOrder.push(...reorderedColumn.map(i => i.id))
        columnInserted = true
      }
      // Skip individual column issues (they're already added in order)
    } else {
      fullOrder.push(id)
    }
  }

  // End drag state
  draggingIssue.value = null
  allowedTargetStatuses.value.clear()

  // Save the new order via API
  try {
    await saveBoardManualOrder(fullOrder)
    // Force re-render by updating the issues array order
    const orderMap = new Map<string, number>()
    fullOrder.forEach((id, idx) => orderMap.set(id, idx))
    issues.value.sort((a, b) => (orderMap.get(a.id) ?? 0) - (orderMap.get(b.id) ?? 0))
  } catch {
    // Order save failed - the composable already shows an error message
  }
}

/** Handle drop from Backlog panel: assign sprint + change status */
async function handleBacklogDrop(issue: BoardIssue, targetStatusId: string) {
  backlogDraggingIssue.value = null
  allowedTargetStatuses.value.clear()

  // Determine which sprint to assign
  const targetSprintId = selectedSprint.value || getActiveSprintId()
  if (!targetSprintId) {
    Message.warning('请先选择一个 Sprint 或确保项目有活跃的 Sprint')
    return
  }

  const targetStatus = statuses.value.find(s => s.id === targetStatusId)

  /**
   * 回滚：撤销已执行的 Sprint 分配，并展示错误信息（errorMessage 为 null 时静默回滚）
   */
  async function rollbackSprintAssignment(errorMessage: string | null) {
    try {
      // 多 Sprint 模式下移除刚追加的关联，否则清空主 Sprint
      const rollbackPayload = (allowMultipleSprints.value && issue.sprintId)
        ? { removeFromSprintId: targetSprintId }
        : { sprintId: null }
      await issueApi.update(issue.id, rollbackPayload)
    } catch {
      // best-effort rollback，忽略错误
    }
    if (errorMessage) {
      Message.error(`${issue.issueKey} 状态变更被拒绝：${errorMessage}`)
    }
  }

  /**
   * 成功路径：将工单从 Backlog 移入看板并更新本地状态
   */
  function finalizeBacklogDrop(res: R<TransitStatusResultVO>) {
    const extracted = extractVersion(res.data)
    const newVersion = extracted != null ? extracted : (issue.version || 0) + 2
    showActionFeedback(res.data)
    useNavBadge().refresh() // 状态变更后刷新导航栏 badge

    // Remove from backlog panel
    backlogPanelRef.value?.removeIssue(issue.id)

    // Add to board issues list with updated status/sprint/version
    const updatedIssue: BoardIssue = {
      ...issue,
      statusId: targetStatusId,
      sprintId: targetSprintId,
      version: newVersion,
    }
    issues.value.push(updatedIssue)

    Message.success(`${issue.issueKey} 已添加到看板「${targetStatus?.name}」`)
  }

  try {
    // Step 1: Assign sprint — use addToSprintId in multi-sprint mode (preserves existing associations)
    const sprintPayload = (allowMultipleSprints.value && issue.sprintId)
      ? { addToSprintId: targetSprintId }
      : { sprintId: targetSprintId }
    await issueApi.update(issue.id, sprintPayload)

    // Step 2: Transition status (if different from current)
    if (issue.statusId !== targetStatusId) {
      const res = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version)

      // ★ Handle WIP limit exceeded — same UX as column-to-column drag
      if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
        Modal.warning({
          title: 'WIP 限制',
          content: res.message,
          okText: '继续移入',
          cancelText: '取消',
          hideCancel: false,
          onOk: async () => {
            try {
              const forceRes = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version, undefined, true)
              if (forceRes.code === 0) {
                finalizeBacklogDrop(forceRes)
              } else {
                await rollbackSprintAssignment(forceRes.message || '状态变更失败')
              }
            } catch (e2: any) {
              await rollbackSprintAssignment(e2.response?.data?.message || '状态变更失败')
            }
          },
          onCancel: async () => {
            // User cancelled — rollback the sprint assignment (no error message needed)
            await rollbackSprintAssignment(null)
          },
        })
        return
      }

      // ★ Handle close confirmation required — prompt user to force close
      if (res.code === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) {
        Modal.warning({
          title: '确认关闭',
          content: res.message,
          okText: '强制关闭',
          cancelText: '取消',
          hideCancel: false,
          onOk: async () => {
            try {
              const forceRes = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version, true)
              if (forceRes.code === 0) {
                finalizeBacklogDrop(forceRes)
              } else {
                await rollbackSprintAssignment(forceRes.message || '状态变更失败')
              }
            } catch (e2: any) {
              await rollbackSprintAssignment(e2.response?.data?.message || '状态变更失败')
            }
          },
          onCancel: async () => {
            await rollbackSprintAssignment(null)
          },
        })
        return
      }

      // ★ Handle description empty warning — prompt user to confirm
      if (res.code === ERROR_CODES.DESCRIPTION_EMPTY_WARNING) {
        Modal.warning({
          title: '工单描述为空',
          content: res.message,
          okText: '继续变更',
          cancelText: '取消',
          hideCancel: false,
          onOk: async () => {
            try {
              const forceRes = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version, undefined, undefined, true)
              if (forceRes.code === 0) {
                finalizeBacklogDrop(forceRes)
              } else {
                await rollbackSprintAssignment(forceRes.message || '状态变更失败')
              }
            } catch (e2: any) {
              await rollbackSprintAssignment(e2.response?.data?.message || '状态变更失败')
            }
          },
          onCancel: async () => {
            await rollbackSprintAssignment(null)
          },
        })
        return
      }

      // ★ Any other non-success code: rollback sprint assignment
      if (res.code !== 0) {
        await rollbackSprintAssignment(res.message || '状态变更失败')
        return
      }

      // Success path
      finalizeBacklogDrop(res)
    } else {
      // Status unchanged, only sprint was updated — version +1
      const newVersion = (issue.version || 0) + 1

      // Remove from backlog panel
      backlogPanelRef.value?.removeIssue(issue.id)

      // Add to board issues list
      const updatedIssue: BoardIssue = {
        ...issue,
        sprintId: targetSprintId,
        version: newVersion,
      }
      issues.value.push(updatedIssue)

      Message.success(`${issue.issueKey} 已添加到看板「${targetStatus?.name}」`)
    }
  } catch (e: any) {
    const errMsg = e.response?.data?.message || '操作失败'
    Message.error(`${issue.issueKey} 移入看板失败：${errMsg}`)
  }
}

/** Get the active sprint ID for the current project */
function getActiveSprintId(): string | undefined {
  return activeSprint.value?.id
}

// ===== 撤销逻辑 =====

/** 推送撤销通知（复用于正常拖拽和强制 WIP 确认后的成功路径） */
function pushUndoNotification(issue: BoardIssue, oldStatusId: string, newStatusId: string, targetStatus: IssueStatusVO | undefined) {
  const undoEntry: UndoEntry = {
    issueId: issue.id,
    issueKey: issue.issueKey,
    oldStatusId: oldStatusId,
    newStatusId: newStatusId,
    oldStatusName: statuses.value.find(s => s.id === oldStatusId)?.name || '',
    newStatusName: targetStatus?.name || '',
    timestamp: Date.now()
  }
  undoStack.value.push(undoEntry)

  const notifId = `undo-${issue.id}-${Date.now()}`
  Notification.success({
    id: notifId,
    title: '状态变更成功',
    content: `${issue.issueKey} 已移至「${targetStatus?.name}」`,
    duration: UNDO_TIMEOUT,
    closable: true,
    footer: () => h('button', {
      class: 'undo-btn',
      onClick: () => {
        undoTransition(undoEntry)
        Notification.remove(notifId)
      }
    }, '↩ 撤销 (Ctrl+Z)')
  })
}

async function undoTransition(entry: UndoEntry) {
  const issue = issues.value.find(i => i.id === entry.issueId)
  if (!issue) {
    Message.warning('工单已不在当前视图中，无法撤销')
    return
  }

  const currentStatusId = issue.statusId
  issue.statusId = entry.oldStatusId
  transitioningIssueIds.value.add(issue.id)

  try {
    const res = await issueApi.undoTransitStatus(issue.id, entry.oldStatusId)
    // 同步更新版本号
    const newVersion = extractVersion(res.data)
    if (newVersion != null) {
      issue.version = newVersion
    } else {
      issue.version = (issue.version || 0) + 1
    }
    Message.success(`${entry.issueKey} 已撤销回「${entry.oldStatusName}」`)
    undoStack.value = undoStack.value.filter(e => e !== entry)
  } catch (e: any) {
    issue.statusId = currentStatusId
    const errMsg = e.response?.data?.message || '撤销失败'
    Message.error(`撤销失败：${errMsg}`)
  } finally {
    transitioningIssueIds.value.delete(issue.id)
  }
}

// ===== 键盘快捷键（委托到 useBoardKeyboard composable） =====
const { handleKeydown } = useBoardKeyboard({
  undoStack,
  undoTimeout: UNDO_TIMEOUT,
  undoTransition,
  previewVisible,
  closePreview,
  selectedCount,
  clearSelection
})

// ===== 批量操作处理 =====

async function onBatchState(statusId: string) {
  const result = await batchTransitStatus(selectedIssues.value, statusId)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        issue.statusId = statusId
        // 批量操作不返回单个版本号，本地递增
        issue.version = (issue.version || 0) + 1
      }
    })
  }
  clearSelection()
}

async function onBatchAssign(assigneeId: string | null) {
  await batchAssign(selectedIssues.value, assigneeId || '')
  // 需要刷新看板数据以获取更新后的 assigneeName
  await loadIssues()
  clearSelection()
}

async function onBatchSprint(sprintId: string | null) {
  const result = await batchUpdateSprint(selectedIssues.value, sprintId)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        issue.sprintId = sprintId || undefined
      }
    })
  }
  clearSelection()
}

async function onBatchPriority(priority: string) {
  const result = await batchUpdatePriority(selectedIssues.value, priority)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        issue.priority = priority
      }
    })
  }
  clearSelection()
}

async function onBatchTagAdd(tagId: string) {
  const result = await batchTagAdd(selectedIssues.value, tagId)
  if (result.succeeded > 0) {
    await loadIssues()
  }
  clearSelection()
}

async function onBatchTagRemove(tagId: string) {
  const result = await batchTagRemove(selectedIssues.value, tagId)
  if (result.succeeded > 0) {
    await loadIssues()
  }
  clearSelection()
}

async function onBatchLink(linkType: string, targetIssueId: string) {
  const result = await batchAddLink(selectedIssues.value, linkType, targetIssueId)
  if (result.succeeded > 0) {
    await loadIssues()
  }
  clearSelection()
}

async function onBatchDelete() {
  const result = await batchDelete(selectedIssues.value)
  if (result.succeeded > 0) {
    // 从视图中移除已删除的工单
    const deletedIds = new Set(
      selectedIssues.value
        .map(i => i.id)
        .filter(id => !result.failures.some(f => f.issueId === id))
    )
    issues.value = issues.value.filter(i => !deletedIds.has(i.id))
  }
  clearSelection()
}

// ===== 数据加载（委托到 useBoardData composable） =====
const {
  projects, projectLoadState, loadProjects,
  loadStatuses, loadBoardColumns, loadCardConfig, loadSwimlaneConfig,
  loadColumnMerges, loadTransitionableStatuses, loadSprints,
  loadBoardBehavior, loadChartConfig, loadProjectMembers,
  loadBoard, loadIssues, onSettingsSaved
} = useBoardData({
  selectedProject, selectedSprint, keyword, issues, statuses, sprints,
  allColumnConfigs, cardConfig, columnMerges,
  swimlaneGroupBy, swimlaneSelectedValues, swimlaneShowUncategorized,
  swimlaneUncategorizedPosition, swimlaneIssueType,
  boardFilterMode, boardFilterQuery, boardDoneRetentionDays, boardName, boardColumnField,
  allowMultipleSprints, canEditBoard, boardLinkedProjectIds,
  backlogViewMode, backlogSavedQueryId,
  showAllColumns, boardTotalCount, boardTruncated,
  boardChartType, boardBurndownCalculation,
  loading, projectMembers,
  effectiveAssigneeId, visibleStatuses, effectiveColumns, collapsedColumns,
  expandedEmptyColumns, activeSprint,
  transitionableSourceStatuses, canChangeStatus,
  guidanceDismissed,
  guidanceDismissedKey: GUIDANCE_DISMISSED_KEY,
  syncUrlState,
  loadCollapsedColumnsState,
  loadSwimlaneOrder,
  loadBoardManualOrder,
  userExplicitlySelectedAll: () => userExplicitlySelectedAll
})
// Wire up the late-binding function reference for useBoardFilter
_loadIssuesFn = loadIssues



// ===== 内联快速创建卡片 =====
const addingCardColumnId = ref<string | null>(null)
const addingCardSwimlaneKey = ref<string | null>(null)
const addCardTitle = ref('')
const addCardType = ref<string>('Task')
const addCardSubmitting = ref(false)

/** 存储 input refs，用于自动聚焦 */
function setAddCardInputRef(el: HTMLElement | null, _statusId: string, _laneKey: string) {
  if (el) {
    nextTick(() => (el as HTMLInputElement).focus())
  }
}

/** 判断某状态是否为终态（已完成/已取消），终态列不允许直接创建卡片 */
function isClosedStatus(status: { category?: string; isClosed?: boolean }): boolean {
  if (status.isClosed) return true
  const cat = status.category?.toLowerCase()
  return cat === 'done' || cat === 'cancelled'
}

/** 开始添加卡片：展开内联表单 */
function startAddCard(statusId: string, swimlaneKey?: string) {
  addingCardColumnId.value = statusId
  addingCardSwimlaneKey.value = swimlaneKey || null
  addCardTitle.value = ''
  addCardType.value = 'Task'
}

/** 取消添加卡片 */
function cancelAddCard() {
  addingCardColumnId.value = null
  addingCardSwimlaneKey.value = null
  addCardTitle.value = ''
  keepFormOpen = false
}

/** blur 时如果标题为空则取消——但提交后保持打开 */
let keepFormOpen = false

function onAddCardBlur() {
  if (keepFormOpen) return
  // 使用 setTimeout 避免点击"创建"按钮时提前关闭
  setTimeout(() => {
    if (!addCardTitle.value.trim() && !addCardSubmitting.value && !keepFormOpen) {
      cancelAddCard()
    }
  }, 200)
}

/** 提交创建卡片 */
async function submitAddCard(statusId: string, swimlaneKey?: string) {
  const title = addCardTitle.value.trim()
  if (!title || !selectedProject.value) return

  addCardSubmitting.value = true
  keepFormOpen = true
  try {
    // 确定 Sprint
    const sprintId = selectedSprint.value || getActiveSprintId()

    // 确定负责人（按负责人分组时预填）
    let assigneeId: string | undefined
    if (swimlaneGroupBy.value === 'assignee' && swimlaneKey && swimlaneKey !== '__unassigned__') {
      assigneeId = swimlaneKey
    }

    const createData = {
      projectId: selectedProject.value!,
      title,
      issueType: addCardType.value,
      sprintId: sprintId || undefined,
      assigneeId: assigneeId || undefined
    }

    const res = await issueApi.create(createData)
    const newIssue = res.data

    if (newIssue) {
      // 如果创建的 Issue 状态不是目标列的状态，需要做状态转换
      // （初始状态通常是 default/待处理，但用户在其他列创建需要转换）
      const defaultStatus = statuses.value.find(s => s.isDefault)
      if (defaultStatus && defaultStatus.id !== statusId) {
        try {
          const transitRes = await issueApi.transitStatus(newIssue.id, statusId, undefined, newIssue.version)
          // 更新本地状态和版本号
          newIssue.statusId = statusId
          if (transitRes.data != null) {
            newIssue.version = transitRes.data
          } else {
            newIssue.version = (newIssue.version || 0) + 1
          }
        } catch {
          // 转换失败不影响创建，卡片将出现在默认状态列
          Message.warning(`工单已创建，但无法自动转换到「${localizeStatusName(visibleStatuses.value.find(s => s.id === statusId)?.name)}」状态`)
        }
      }

      // 构建本地 BoardIssue 添加到看板
      const issueVO: BoardIssue = {
        id: newIssue.id,
        issueKey: newIssue.issueKey,
        title: newIssue.title,
        issueType: newIssue.issueType || addCardType.value,
        priority: newIssue.priority || 'Normal',
        statusId: newIssue.statusId || statusId,
        projectId: selectedProject.value!,
        sprintId: sprintId || undefined,
        assigneeId: newIssue.assigneeId || assigneeId,
        assigneeName: newIssue.assigneeName || '',
      }
      issues.value.push(issueVO)

      Message.success(`${newIssue.issueKey} 创建成功`)

      // 清空标题但保持表单打开，方便连续创建
      addCardTitle.value = ''
      // 保持 keepFormOpen 直到下一帧，防止 blur 关闭表单
      nextTick(() => {
        // 延迟重置 keepFormOpen，让 blur 有时间判断
        setTimeout(() => { keepFormOpen = false }, 250)
      })
    }
  } catch (e: any) {
    const errMsg = e.response?.data?.message || '创建失败'
    Message.error(`创建工单失败：${errMsg}`)
    keepFormOpen = false
  } finally {
    addCardSubmitting.value = false
  }
}

// ===== 头部"新建..."按钮相关 =====
const newCardModalVisible = ref(false)
/** 新建卡片时预填的 Sprint ID（来自当前看板 Sprint 选择或活跃 Sprint） */
const newCardPrefilledSprintId = ref<string | null>(null)

const newSprintModalVisible = ref(false)
const newSprintSubmitting = ref(false)
const newSprintForm = ref({
  name: '',
  goal: '',
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined
})

/** "新建..."按钮下拉菜单选择处理 */
function onNewMenuSelect(value: string | number | Record<string, unknown> | undefined) {
  if (value === 'card') {
    // 打开完整创建面板，预填当前 Sprint
    newCardPrefilledSprintId.value = selectedSprint.value || getActiveSprintId() || null
    newCardModalVisible.value = true
  } else if (value === 'sprint') {
    newSprintForm.value = { name: '', goal: '', startDate: undefined, endDate: undefined }
    newSprintModalVisible.value = true
  }
}

/** IssueCreatePanel 创建成功后刷新看板 */
async function onNewCardCreated() {
  newCardModalVisible.value = false
  // 重新加载看板工单以包含新创建的工单
  await loadIssues()
}

/** IssueCreatePanel 全屏展开：跳转到创建页面 */
function onNewCardExpandFullscreen(formData: { projectId?: string } | undefined) {
  newCardModalVisible.value = false
  router.push({ name: 'IssueCreate', query: formData?.projectId ? { projectId: formData.projectId } : undefined })
}

/** 提交新建 Sprint 对话框 */
async function submitNewSprintModal() {
  const name = newSprintForm.value.name.trim()
  if (!name || !selectedProject.value) {
    Message.warning('请输入 Sprint 名称')
    return
  }

  newSprintSubmitting.value = true
  try {
    const res = await sprintApi.create(selectedProject.value, {
      name,
      goal: newSprintForm.value.goal || undefined,
      startDate: newSprintForm.value.startDate || undefined,
      endDate: newSprintForm.value.endDate || undefined
    })
    if (res.data) {
      sprints.value.push(res.data)
      Message.success(`Sprint「${res.data.name}」创建成功`)
      newSprintModalVisible.value = false
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建 Sprint 失败')
  } finally {
    newSprintSubmitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadStatuses()])
  // Note: keyboard listener registration is handled by useBoardKeyboard composable

  // URL 状态恢复优先级：URL params > projectStore (localStorage) > 首个项目
  restoreFromUrl()

  if (selectedProject.value) {
    // 如果恢复了状态或已有选择，同步到 URL 并加载看板
    syncUrlState()
    loadBoard()
  } else {
    // 尝试自动选择：首个项目（收藏项目已排在前面）
    if (projects.value.length > 0) {
      selectedProject.value = projects.value[0].id
      syncUrlState()
      loadBoard()
    }
  }
})

// 监听路由 query 变化（浏览器前进/后退按钮）
watch(() => route.query, (newQuery, oldQuery) => {
  // 避免自身 replace 触发的变化导致循环
  if (JSON.stringify(newQuery) === JSON.stringify(oldQuery)) return

  // 仅当仍在看板路由时才响应 query 变化（导航离开时忽略，避免清空 store）
  if (route.name !== 'Boards') return

  const queryProject = newQuery.project as string | undefined
  const querySprint = newQuery.sprint as string | undefined
  const queryGroup = newQuery.group as string | undefined
  const queryAssignee = newQuery.assignee as string | undefined

  suppressUrlSync = true

  // Track whether any state actually changed (to avoid redundant loadBoard calls)
  let stateChanged = false

  // 恢复项目
  if (queryProject) {
    const project = projects.value.find(p => p.key === queryProject)
    if (project && project.id !== selectedProject.value) {
      selectedProject.value = project.id
      selectedSprint.value = querySprint || undefined
      swimlaneGroupBy.value = (queryGroup as SwimlaneGroupBy) || 'none'
      localStorage.setItem(SWIMLANE_STORAGE_KEY, swimlaneGroupBy.value)
      suppressUrlSync = false
      loadBoard()
      return
    }
  } else if (selectedProject.value) {
    // URL 无项目参数但仍有选中项目（浏览器后退到无参数的 /boards）
    // 不清除 store 中的选中状态——重新同步 URL 并保持看板显示
    suppressUrlSync = false
    syncUrlState()
    return
  }

  // 恢复 Sprint
  if (querySprint !== selectedSprint.value) {
    selectedSprint.value = querySprint || undefined
    stateChanged = true
  }

  // 恢复分组
  if (queryGroup && queryGroup !== swimlaneGroupBy.value) {
    swimlaneGroupBy.value = queryGroup as SwimlaneGroupBy
    localStorage.setItem(SWIMLANE_STORAGE_KEY, swimlaneGroupBy.value)
    stateChanged = true
  } else if (!queryGroup && swimlaneGroupBy.value !== 'none') {
    swimlaneGroupBy.value = 'none'
    localStorage.setItem(SWIMLANE_STORAGE_KEY, 'none')
    stateChanged = true
  }  // 恢复负责人筛选
  if (queryAssignee !== assigneeFilter.value) {
    assigneeFilter.value = queryAssignee || undefined
    if (queryAssignee) {
      localStorage.setItem(ASSIGNEE_FILTER_KEY, queryAssignee)
    } else {
      localStorage.removeItem(ASSIGNEE_FILTER_KEY)
    }
    stateChanged = true
  }

  suppressUrlSync = false

  // 仅在实际状态发生变化时重新加载（避免与 onSprintChange/onProjectChange 的 loadBoard 重复调用）
  if (stateChanged && selectedProject.value) {
    loadBoard()
  }
})

// Note: keyboard listener cleanup is handled by useBoardKeyboard composable
// Note: debounce timer cleanup is handled by useBoardFilter composable

// ===== WebSocket 实时订阅 =====
// 订阅当前项目的 Issue 变更，刷新看板数据
useIssueProjectSubscription(
  () => selectedProject.value ?? null,
  (event: IssueRealtimeEvent) => {
    // 过滤自己的操作
    const myUserId = authStore.user?.userId
    if (myUserId && String(event.operatorId) === String(myUserId)) return

    if (event.action === 'FIELD_UPDATED') {
      // 字段变更（状态/优先级/负责人/Sprint）：局部更新卡片，避免全量刷新
      const issue = issues.value.find(i => String(i.id) === String(event.issueId))
      if (issue) {
        for (const [key, value] of Object.entries(event.changes)) {
          ;(issue as any)[key] = value
        }
      }
    } else if (
      event.action === 'CREATED' ||
      event.action === 'DELETED' ||
      event.action === 'TAG_CHANGED'
    ) {
      // 新建/删除/标签变更：重新加载看板（影响卡片显示）
      if (selectedProject.value) loadBoard()
    }
  }
)

  // Return all state and methods needed by the template
  return {
    // Core state
    selectedProject, selectedSprint, keyword, loading, issues, statuses, sprints,
    projects, projectLoadState,
    // Card size
    cardSize, cardSizeOptions, setCardSize,
    // TV mode
    isTvMode, toggleTvMode,
    // Orphan issues
    showOrphanPanel, orphanIssues, orphanIssueCount,
    // Column config
    allColumnConfigs, showSettings, effectiveColumns, visibleStatuses,
    hiddenIssueColumns, showAllColumns,
    boardColumnField, isMultiProjectBoard,
    getColumnIssues, getEffectiveColumnIssues,
    getSwimlaneEffectiveColumnIssues, isEffectiveColumnClosed,
    getEffectiveColumnWipClass, getEffectiveColumnWipMax, getEffectiveColumnWipWarning,
    getEffectiveColumnEstimation,
    getWipTooltip, getColumnConfig, getWipWarning,
    isColumnCollapsed, toggleColumnCollapse, expandedEmptyColumns,
    collapsedColumns,
    // Board behavior
    boardFilterMode, boardFilterQuery, boardDoneRetentionDays, boardName,
    canEditBoard, displayBoardName,
    isBehaviorFilterActive, isSmartDefaultDoneRetentionActive,
    allowMultipleSprints, boardLinkedProjectIds,
    // Backlog
    showBacklog, toggleBacklog, backlogPanelRef, backlogDraggingIssue, backlogViewMode, backlogSavedQueryId,
    boardStatusIdsForBacklog,
    onBacklogDragStart, onBacklogDragEnd,
    // Preview
    previewVisible, previewIssueId, openPreview, closePreview,
    onPreviewGoDetail, onPreviewIssueUpdated,
    // Swimlane
    swimlaneGroupBy, onSwimlaneChange, swimlanes, orderedSwimlanes,
    collapsedSwimlanes, toggleSwimlane,
    swimlaneSelectedValues, swimlaneShowUncategorized, swimlaneUncategorizedPosition, swimlaneIssueType,
    getSwimlaneColumnIssues,
    dragOverSwimlaneKey, onDragOverSwimlane, onDragLeaveSwimlane,
    // Swimlane row drag
    swimlaneDraggingKey, swimlaneDragOverKey,
    onSwimlaneRowDragStart, onSwimlaneRowDragEnd, onSwimlaneRowDragOver,
    onSwimlaneRowDragLeave, onSwimlaneRowDrop,
    // Sprint
    currentSelectedSprint, activeSprint, currentSprintGoal, sprintRemainingDays,
    isActiveSprint, formatSprintDateRange, boardOwnerName,
    // Sprint archived footer
    restoringArchivedSprint, deletingArchivedSprint,
    showDeleteArchivedSprintModal, deleteArchivedSprintPreview,
    deleteArchivedSprintMoveOption, deleteArchivedSprintTargetId,
    handleRestoreArchivedSprint, handleDeleteArchivedSprint, confirmDeleteArchivedSprint,
    // No active sprint guidance
    guidanceDismissed, showSprintModeNoActiveState,
    nextPlannedSprint, plannedSprints, dismissGuidance,
    openCreateSprintFromGuidance, activatePlannedSprint, activatingSprintId,
    // Card config
    cardConfig, isCardFieldVisible, getCardFieldDisplayMode,
    getCardColorClass, getCardProjectColorStyle, getCardDueDateClass, getCardDueDateTooltip,
    hasVisibleCustomFields, getVisibleCustomFieldDetails, getVisibleTags,
    // Card interactions
    onCardClick, onCardDblClick, onCardKeydown, onCardSetAssignee,
    // Drag
    draggingIssue, dragOverColumnId, isDragging,
    allowedTargetStatuses, transitioningIssueIds,
    isCardDraggable, onDragStart, onDragEnd, onDragOver, onDragLeave, onDrop,
    isDropAllowed, isEffectiveColumnDropAllowed, getDropTargetStatusId,
    // Selection
    selectedIds, selectedCount, selectedIssues, toggleCardSelection, clearSelection,
    // Manual order
    boardManualSorted, isManualSortDisabled,
    // Batch operations
    batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority,
    batchTagAdd, batchTagRemove, batchAddLink, batchDelete,
    // Search
    isSearchActive, onSearchInput, onSearchClear, clearSearch,
    showNoSearchResults,
    // Assignee filter
    assigneeFilter, effectiveAssigneeId, projectMembers,
    toggleMyIssues, onAssigneeFilterChange,
    // Permission
    canChangeStatus, canCreateIssue, canDeleteIssue, canEditSprint, canDeleteSprint,
    // Project helpers
    currentProjectName, currentProjectKey, onProjectChange, onSprintChange,
    // Navigation
    openIssue, goToSprintDetail, buildOpenInListUrl,
    // No active sprint guidance (extra names)
    showNoActiveSprintGuidance, selectNextPlannedSprint, formatSprintDate,
    effectiveDoneRetentionDays, hiddenIssueTotalCount, openCloneModal,
    // New card/sprint
    newCardModalVisible, newCardPrefilledSprintId, onNewCardCreated, onNewCardExpandFullscreen,
    newSprintModalVisible, newSprintSubmitting, newSprintForm, onNewMenuSelect, submitNewSprintModal,
    // Inline add card
    addingCardColumnId, addingCardSwimlaneKey, addCardTitle, addCardType, addCardSubmitting,
    startAddCard, cancelAddCard, onAddCardBlur, submitAddCard, setAddCardInputRef,
    isClosedStatus,
    // Board chart
    showChart, boardChartType, boardBurndownCalculation,
    // Clone board
    showCloneModal,
    // Undo
    undoStack,
    // Progress
    activeStatuses, closedIssueCount, closedIssueDetail,
    progressIndicatorAriaLabel, getProgressBarHeight, getClosedProgressBarHeight,
    scrollToColumn,
    // Hidden columns
    onHiddenStatusTagClick,
    // Settings saved
    onSettingsSaved,
    // Board truncated
    boardTruncated, boardTotalCount, boardTotalEstimation,
    // Helper functions
    localizeStatusName,
    getInitials, getSprintName,
    priorityIcon, typeLabel, typeInitial, getActiveSprintId,
    // onBatch handlers
    onBatchTransit: onBatchState, onBatchAssign, onBatchSprint, onBatchPriority,
    onBatchTagAdd, onBatchTagRemove, onBatchLink, onBatchDelete,
    // Keyboard
    handleKeydown,
    // Load
    loadBoard, loadIssuesWithLoading, loadProjects,
    nextPlannedSprintHint,
  }
}

