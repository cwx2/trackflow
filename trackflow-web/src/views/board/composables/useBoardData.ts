// @ts-nocheck
import { ref } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import { Message } from '@arco-design/web-vue'
import { issueApi, boardApi, sprintApi, projectApi, workflowApi } from '@/api'
import type { IssueStatusVO, SprintVO, BoardColumnVO, BoardCardConfigVO, BoardColumnMergeGroupVO, BoardCardVO } from '@/api/types'
import type { BoardIssue, SwimlaneGroupBy, EffectiveColumn } from './useKanbanBoard'
import { useProjectList } from '@/composables/useProjectList'

/**
 * 看板数据加载逻辑。
 *
 * 负责所有数据 IO：项目列表、状态列表、Sprint 列表、
 * 列配置、卡片配置、泳道配置、看板行为、图表配置、
 * 工单数据加载（聚合 API + Legacy fallback）、项目成员加载。
 *
 * 不负责（由主 composable 或其他 composable 处理）：
 * - UI 交互逻辑（拖拽/筛选/键盘/全屏）
 * - 状态转换业务逻辑
 * - URL 状态同步
 */
export interface DataDeps {
  selectedProject: Ref<string | undefined> | ComputedRef<string | undefined>
  selectedSprint: Ref<string | undefined>
  keyword: Ref<string>
  issues: Ref<BoardIssue[]>
  statuses: Ref<IssueStatusVO[]>
  sprints: Ref<SprintVO[]>
  allColumnConfigs: Ref<BoardColumnVO[]>
  cardConfig: Ref<BoardCardConfigVO>
  columnMerges: Ref<BoardColumnMergeGroupVO[]>
  swimlaneGroupBy: Ref<SwimlaneGroupBy>
  swimlaneSelectedValues: Ref<string[] | null>
  swimlaneShowUncategorized: Ref<boolean>
  swimlaneUncategorizedPosition: Ref<'top' | 'bottom'>
  swimlaneIssueType: Ref<string | null>
  boardFilterMode: Ref<'all' | 'active_sprint' | 'query'>
  boardFilterQuery: Ref<string | null>
  boardDoneRetentionDays: Ref<number | null>
  boardName: Ref<string>
  boardColumnField: Ref<'status' | 'priority'>
  allowMultipleSprints: Ref<boolean>
  canEditBoard: Ref<boolean>
  boardLinkedProjectIds: Ref<string[]>
  backlogViewMode: Ref<'list' | 'tree'>
  backlogSavedQueryId: Ref<string | null>
  showAllColumns: Ref<boolean>
  boardTotalCount: Ref<number>
  boardTruncated: Ref<boolean>
  boardChartType: Ref<string>
  boardBurndownCalculation: Ref<string>
  loading: Ref<boolean>
  projectMembers: Ref<Array<{ userId: string; displayName: string }>>
  // Computed/Ref dependencies for loadIssues
  effectiveAssigneeId: ComputedRef<string | undefined>
  visibleStatuses: ComputedRef<IssueStatusVO[]>
  effectiveColumns: ComputedRef<EffectiveColumn[]>
  collapsedColumns: Ref<Set<string>>
  expandedEmptyColumns: Ref<Set<string>>
  activeSprint: ComputedRef<SprintVO | undefined>
  // Drag state (for loadTransitionableStatuses)
  transitionableSourceStatuses: Ref<Set<string>>
  canChangeStatus: ComputedRef<boolean>
  // Guidance/UI state used by loadBoard
  guidanceDismissed: Ref<boolean>
  guidanceDismissedKey: string
  // External functions needed by loadBoard
  syncUrlState: () => void
  loadCollapsedColumnsState: () => void
  loadSwimlaneOrder: () => void
  loadBoardManualOrder: (scope: { type: string; id: string }) => Promise<void>
  // Sprint auto-select flag
  userExplicitlySelectedAll: () => boolean
}

/** 看板安全上限：超过此数量的工单将截断并提示用户 */
const BOARD_MAX_ISSUES = 2000

const SWIMLANE_STORAGE_KEY = 'tf_kanban_swimlane'

export function useBoardData(deps: DataDeps) {
  const {
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
    guidanceDismissed, guidanceDismissedKey,
    syncUrlState, loadCollapsedColumnsState, loadSwimlaneOrder, loadBoardManualOrder,
    userExplicitlySelectedAll
  } = deps

  // ===== Project list =====
  const { projects, loadState: projectLoadState, load: loadProjects } = useProjectList()

  // ===== Data loading functions =====

  async function loadStatuses() {
    try {
      const res = await issueApi.listStatuses()
      statuses.value = res.data || []
    } catch {
      statuses.value = []
      Message.error('加载状态列表失败')
    }
  }

  async function loadBoardColumns() {
    if (!selectedProject.value) {
      allColumnConfigs.value = []
      return
    }
    try {
      const res = await boardApi.getColumns(selectedProject.value)
      allColumnConfigs.value = res.data || []
    } catch {
      allColumnConfigs.value = []
    }
  }

  async function loadCardConfig() {
    if (!selectedProject.value) {
      cardConfig.value = { visibleFields: ['assignee', 'priority', 'type'], colorScheme: 'none' }
      return
    }
    try {
      const res = await boardApi.getCardConfig(selectedProject.value)
      if (res.data) {
        cardConfig.value = res.data
      }
    } catch {
      cardConfig.value = { visibleFields: ['assignee', 'priority', 'type'], colorScheme: 'none' }
    }
  }

  async function loadSwimlaneConfig() {
    if (!selectedProject.value) {
      swimlaneGroupBy.value = 'none'
      swimlaneSelectedValues.value = null
      swimlaneShowUncategorized.value = true
      swimlaneUncategorizedPosition.value = 'bottom'
      swimlaneIssueType.value = null
      return
    }
    try {
      const res = await boardApi.getSwimlaneConfig(selectedProject.value)
      if (res.data && res.data.groupByField) {
        swimlaneGroupBy.value = res.data.groupByField as SwimlaneGroupBy
        // 同步到 localStorage（兼容本地快速切换）
        localStorage.setItem(SWIMLANE_STORAGE_KEY, res.data.groupByField)
        // 加载值选择配置
        swimlaneSelectedValues.value = res.data.selectedValues || null
        swimlaneShowUncategorized.value = res.data.showUncategorized !== false
        swimlaneUncategorizedPosition.value = res.data.uncategorizedPosition || 'bottom'
        swimlaneIssueType.value = res.data.swimlaneIssueType ?? null
      }
    } catch {
      // 保持当前 localStorage 中的值
    }
  }

  async function loadColumnMerges() {
    if (!selectedProject.value) {
      columnMerges.value = []
      return
    }
    try {
      const res = await boardApi.getColumnMerges(selectedProject.value)
      columnMerges.value = res.data || []
    } catch {
      columnMerges.value = []
    }
  }

  async function loadTransitionableStatuses() {
    if (!selectedProject.value || !canChangeStatus.value) {
      transitionableSourceStatuses.value = new Set()
      return
    }
    try {
      const res = await workflowApi.getTransitionableStatuses(selectedProject.value)
      transitionableSourceStatuses.value = new Set(res.data || [])
    } catch {
      transitionableSourceStatuses.value = new Set()
    }
  }

  async function loadSprints() {
    if (!selectedProject.value) { sprints.value = []; return }
    try {
      const res = await sprintApi.listByProject(selectedProject.value)
      const rawSprints = res.data?.list || []

      // Sort sprints: active first, then planned, then completed; within group by startDate desc
      const statusOrder: Record<string, number> = { active: 0, planned: 1, completed: 2 }
      rawSprints.sort((a, b) => {
        const orderA = statusOrder[a.status] ?? 9
        const orderB = statusOrder[b.status] ?? 9
        if (orderA !== orderB) return orderA - orderB
        const dateA = a.startDate || ''
        const dateB = b.startDate || ''
        return dateB.localeCompare(dateA)
      })
      sprints.value = rawSprints

      // Auto-select active sprint if no explicit user/URL selection
      if (!selectedSprint.value && !userExplicitlySelectedAll()) {
        const activeSprintItem = rawSprints.find(s => s.status === 'active')
        if (activeSprintItem) {
          selectedSprint.value = activeSprintItem.id
          syncUrlState()
        } else {
          // Fallback: find a planned sprint whose date range contains today
          const today = new Date().toISOString().split('T')[0]
          const currentDateSprint = rawSprints.find(s =>
            s.status === 'planned' && s.startDate && s.endDate &&
            s.startDate <= today && s.endDate >= today
          )
          if (currentDateSprint) {
            selectedSprint.value = currentDateSprint.id
            syncUrlState()
          }
        }
      }
    } catch {
      sprints.value = []
      Message.error('加载迭代列表失败')
    }
  }

  async function loadBoardBehavior() {
    if (!selectedProject.value) return
    try {
      const res = await boardApi.getGeneralConfig(selectedProject.value)
      if (res.data) {
        boardFilterMode.value = (res.data.filterMode as 'all' | 'active_sprint' | 'query') || 'all'
        boardFilterQuery.value = res.data.filterQuery ?? null
        boardDoneRetentionDays.value = res.data.doneRetentionDays ?? null
        canEditBoard.value = res.data.currentUserCanEdit ?? false
        boardName.value = res.data.name || ''
        boardColumnField.value = (res.data.columnField as 'status' | 'priority') || 'status'
        allowMultipleSprints.value = res.data.allowMultipleSprints ?? false
        backlogViewMode.value = (res.data.backlogViewMode as 'list' | 'tree') || 'list'
        backlogSavedQueryId.value = res.data.backlogSavedQueryId ?? null
        boardLinkedProjectIds.value = res.data.linkedProjectIds || []
      }
    } catch {
      boardFilterMode.value = 'all'
      boardFilterQuery.value = null
      boardDoneRetentionDays.value = null
      canEditBoard.value = false
      boardName.value = ''
      allowMultipleSprints.value = false
      backlogViewMode.value = 'list'
      backlogSavedQueryId.value = null
      boardLinkedProjectIds.value = []
    }
  }

  async function loadChartConfig() {
    if (!selectedProject.value) return
    try {
      const res = await boardApi.getChartConfig(selectedProject.value)
      if (res.data) {
        boardChartType.value = res.data.chartType || 'burndown'
        boardBurndownCalculation.value = res.data.burndownCalculation || 'issue_count'
      }
    } catch {
      boardChartType.value = 'burndown'
      boardBurndownCalculation.value = 'issue_count'
    }
  }

  async function loadProjectMembers() {
    if (!selectedProject.value) {
      projectMembers.value = []
      return
    }
    try {
      const res = await projectApi.listAssignableMembers(selectedProject.value)
      projectMembers.value = (res.data || []).map(m => ({
        userId: m.userId,
        displayName: m.displayName
      }))
    } catch {
      projectMembers.value = []
    }
  }

  async function loadBoard() {
    if (!selectedProject.value) { issues.value = []; return }
    expandedEmptyColumns.value.clear()
    loadCollapsedColumnsState()
    // Restore guidance dismissed state from sessionStorage
    guidanceDismissed.value = sessionStorage.getItem(`${guidanceDismissedKey}_${selectedProject.value}`) === 'true'
    loading.value = true
    try {
      await Promise.all([
        loadSprints(), loadBoardColumns(), loadCardConfig(), loadSwimlaneConfig(),
        loadColumnMerges(), loadTransitionableStatuses(), loadBoardBehavior(),
        loadProjectMembers(), loadChartConfig()
      ])
      // 加载泳道自定义排序（需在 loadSwimlaneConfig 之后，确保 swimlaneGroupBy 已恢复）
      loadSwimlaneOrder()
      await loadIssues()
      // Load manual order for board card sorting
      if (selectedProject.value) {
        await loadBoardManualOrder({ type: 'project', id: selectedProject.value })
      }
    } catch (e: any) {
      const isSessionExpired = e?.message === '会话已过期' || e?.code === 'ERR_CANCELED'
      if (isSessionExpired) return
      issues.value = []
      Message.error('加载看板数据失败')
    } finally {
      loading.value = false
    }
  }

  async function loadIssues() {
    if (!selectedProject.value) { issues.value = []; boardTruncated.value = false; return }

    const effectiveSprintId = selectedSprint.value || undefined

    const DEFAULT_DONE_RETENTION_DAYS = 14
    let excludeDoneBefore: string | undefined
    if (boardDoneRetentionDays.value === null && !activeSprint.value && !selectedSprint.value) {
      const cutoffDate = new Date(Date.now() - DEFAULT_DONE_RETENTION_DAYS * 24 * 60 * 60 * 1000)
      excludeDoneBefore = cutoffDate.toISOString().split('T')[0]
    }

    // 收集已折叠列的状态 ID
    const collapsedStatusIdSet = new Set<string>()
    for (const colId of collapsedColumns.value) {
      const mergedCol = effectiveColumns.value.find(c => c.isMerged && c.id === colId)
      if (mergedCol) {
        for (const sid of mergedCol.statusIds) {
          collapsedStatusIdSet.add(sid)
        }
      } else {
        collapsedStatusIdSet.add(colId)
      }
    }
    const collapsedIds = [...collapsedStatusIdSet].join(',')

    // 泳道服务端过滤
    let swimlaneFieldParam: string | undefined
    let swimlaneValuesParam: string | undefined
    if (
      swimlaneGroupBy.value !== 'none' &&
      swimlaneSelectedValues.value && swimlaneSelectedValues.value.length > 0 &&
      swimlaneShowUncategorized.value === false
    ) {
      swimlaneFieldParam = swimlaneGroupBy.value
      swimlaneValuesParam = swimlaneSelectedValues.value.join(',')
    }

    try {
      const res = await boardApi.getBoardData({
        projectId: selectedProject.value,
        sprintId: effectiveSprintId,
        assigneeId: effectiveAssigneeId.value || undefined,
        keyword: keyword.value || undefined,
        excludeDoneBefore,
        collapsedStatusIds: collapsedIds || undefined,
        swimlaneField: swimlaneFieldParam,
        swimlaneValues: swimlaneValuesParam,
        showAllColumns: showAllColumns.value || undefined
      })

      const boardData = res.data
      if (!boardData) {
        issues.value = []
        boardTotalCount.value = 0
        boardTruncated.value = false
        return
      }

      // 从聚合数据中同步列配置
      if (boardData.columnConfigs && boardData.columnConfigs.length > 0) {
        allColumnConfigs.value = boardData.columnConfigs
      }

      // 从聚合数据中提取所有工单
      const allIssues: BoardCardVO[] = []
      for (const col of boardData.columns) {
        if (col.issues && col.issues.length > 0) {
          allIssues.push(...col.issues)
        }
      }

      boardTotalCount.value = boardData.totalIssueCount
      boardTruncated.value = boardData.truncated
      issues.value = allIssues
    } catch (e: any) {
      const status = e.response?.status
      if (status === 404 || status === 405) {
        await loadIssuesLegacy(effectiveSprintId, excludeDoneBefore)
      } else {
        throw e
      }
    }
  }

  async function loadIssuesLegacy(effectiveSprintId: string | undefined, excludeDoneBefore: string | undefined) {
    const PAGE_SIZE = 100
    let page = 1
    let allIssues: BoardIssue[] = []
    let total = 0

    const visibleStatusIds = visibleStatuses.value.map(s => s.id).join(',')

    while (true) {
      const res = await issueApi.list({
        projectId: selectedProject.value!,
        statusId: visibleStatusIds || undefined,
        sprintId: effectiveSprintId,
        assigneeId: effectiveAssigneeId.value || undefined,
        keyword: keyword.value || undefined,
        excludeDoneBefore,
        page,
        pageSize: PAGE_SIZE
      })
      const list = res.data?.list || []
      total = res.data?.pagination?.total || 0
      allIssues = allIssues.concat(list)

      if (allIssues.length >= total || allIssues.length >= BOARD_MAX_ISSUES || list.length < PAGE_SIZE) {
        break
      }
      page++
    }

    boardTotalCount.value = total
    boardTruncated.value = allIssues.length < total
    issues.value = allIssues
  }

  /** Reload board sub-configs after settings change */
  function onSettingsSaved() {
    loadBoardColumns()
    loadCardConfig()
    loadSwimlaneConfig()
    loadColumnMerges()
    loadBoardBehavior().then(() => loadIssues())
  }

  return {
    // Project list
    projects,
    projectLoadState,
    loadProjects,
    // Individual loaders
    loadStatuses,
    loadBoardColumns,
    loadCardConfig,
    loadSwimlaneConfig,
    loadColumnMerges,
    loadTransitionableStatuses,
    loadSprints,
    loadBoardBehavior,
    loadChartConfig,
    loadProjectMembers,
    // Composite loaders
    loadBoard,
    loadIssues,
    loadIssuesLegacy,
    // Settings callback
    onSettingsSaved,
    // Constants
    BOARD_MAX_ISSUES
  }
}
