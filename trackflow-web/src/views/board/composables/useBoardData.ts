// @ts-nocheck
import { ref } from 'vue'
import type { Ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { issueApi, boardApi, sprintApi, projectApi } from '@/api'
import type { IssueStatusVO, SprintVO, BoardColumnVO, BoardCardConfigVO } from '@/api/types'
import type { BoardIssue, SwimlaneGroupBy } from './useKanbanBoard'
import { useProjectList } from '@/composables/useProjectList'

/**
 * 看板数据加载逻辑。
 *
 * 负责：
 * - 加载项目列表、状态列表、Sprint 列表
 * - 加载看板列配置、卡片配置、泳道配置
 * - 加载看板工单数据（聚合 API + Legacy fallback）
 * - loadBoard 组合加载
 */
export interface DataDeps {
  selectedProject: Ref<string | undefined>
  selectedSprint: Ref<string | undefined>
  keyword: Ref<string>
  issues: Ref<BoardIssue[]>
  statuses: Ref<IssueStatusVO[]>
  sprints: Ref<SprintVO[]>
  allColumnConfigs: Ref<BoardColumnVO[]>
  cardConfig: Ref<BoardCardConfigVO>
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
  loading: Ref<boolean>
  // Functions
  effectiveAssigneeId: Ref<string | undefined>
  visibleStatuses: { value: IssueStatusVO[] }
  collapsedColumns: Ref<Set<string>>
}

export function useBoardData(deps: DataDeps) {
  const {
    selectedProject, selectedSprint, keyword, issues, statuses, sprints,
    allColumnConfigs, cardConfig, swimlaneGroupBy,
    swimlaneSelectedValues, swimlaneShowUncategorized, swimlaneUncategorizedPosition, swimlaneIssueType,
    boardFilterMode, boardFilterQuery, boardDoneRetentionDays, boardName, boardColumnField,
    allowMultipleSprints, canEditBoard, boardLinkedProjectIds, backlogViewMode, backlogSavedQueryId,
    showAllColumns, boardTotalCount, boardTruncated, loading,
    effectiveAssigneeId, visibleStatuses, collapsedColumns
  } = deps

  // Project list composable
  const { projects, loadState: projectLoadState, load: loadProjects } = useProjectList()

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
        localStorage.setItem('tf_kanban_swimlane', res.data.groupByField)
        swimlaneSelectedValues.value = res.data.selectedValues || null
        swimlaneShowUncategorized.value = res.data.showUncategorized !== false
        swimlaneUncategorizedPosition.value = res.data.uncategorizedPosition || 'bottom'
        swimlaneIssueType.value = res.data.swimlaneIssueType ?? null
      }
    } catch {
      // 保持当前 localStorage 中的值
    }
  }

  async function loadSprints() {
    if (!selectedProject.value) {
      sprints.value = []
      return
    }
    try {
      const res = await sprintApi.list(selectedProject.value)
      sprints.value = res.data || []
    } catch {
      sprints.value = []
    }
  }

  async function loadBoardBehavior() {
    if (!selectedProject.value) return
    try {
      const res = await boardApi.getGeneralConfig(selectedProject.value)
      if (res.data) {
        boardFilterMode.value = res.data.filterMode || 'all'
        boardFilterQuery.value = res.data.filterQuery || null
        boardDoneRetentionDays.value = res.data.doneRetentionDays ?? null
        boardName.value = res.data.name || ''
        boardColumnField.value = res.data.columnField || 'status'
        allowMultipleSprints.value = res.data.allowMultipleSprints ?? false
        canEditBoard.value = res.data.canEdit ?? false
        boardLinkedProjectIds.value = res.data.linkedProjectIds || []
        backlogViewMode.value = res.data.backlogViewMode || 'list'
        backlogSavedQueryId.value = res.data.backlogSavedQueryId || null
      }
    } catch {
      // 保持默认值
    }
  }

  return {
    projects,
    projectLoadState,
    loadProjects,
    loadStatuses,
    loadBoardColumns,
    loadCardConfig,
    loadSwimlaneConfig,
    loadSprints,
    loadBoardBehavior,
  }
}
