// @ts-nocheck
import { ref, computed, watch, nextTick } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import type { IssueStatusVO, BoardColumnVO, BoardColumnMergeGroupVO, BoardCardConfigVO } from '@/api/types'
import type { BoardIssue, EffectiveColumn, CardSize } from './useKanbanBoard'
import { localizeStatusName } from '@/utils/fieldLabels'
import { getDueDateInfo } from '@/utils/dueDate'
import { useManualOrder } from '@/composables/useManualOrder'

export interface ColumnDeps {
  issues: Ref<BoardIssue[]>
  statuses: Ref<IssueStatusVO[]>
  selectedProject: ComputedRef<string | undefined>
  boardColumnField: Ref<'status' | 'priority'>
  boardFilterQuery: Ref<string | null>
  allColumnConfigs: Ref<BoardColumnVO[]>
  columnMerges: Ref<BoardColumnMergeGroupVO[]>
  cardConfig: Ref<BoardCardConfigVO>
  showAllColumns: Ref<boolean>
  cardSize: Ref<CardSize>
  loadIssuesWithLoading: () => Promise<void>
}

export function useBoardColumns(deps: ColumnDeps) {
  const {
    issues, statuses, selectedProject, boardColumnField, boardFilterQuery,
    allColumnConfigs, columnMerges, cardConfig, showAllColumns, cardSize,
    loadIssuesWithLoading
  } = deps

  // ===== Manual order =====
  const {
    isManualSorted: boardManualSorted,
    loadManualOrder: loadBoardManualOrder,
    applyManualOrder: applyBoardManualOrder,
    saveOrder: saveBoardManualOrder,
    reset: resetBoardManualOrder
  } = useManualOrder()

  const isManualSortDisabled = computed(() => {
    if (!boardFilterQuery.value) return false
    try {
      const filters = JSON.parse(boardFilterQuery.value)
      return Array.isArray(filters) && filters.some((f: { field?: string }) => f.field === 'sort' || f.field === 'orderBy')
    } catch {
      return boardFilterQuery.value.toLowerCase().includes('sort by')
    }
  })

  // ===== Visible statuses =====
  const visibleStatuses = computed(() => {
    if (allColumnConfigs.value.length === 0) {
      if (boardColumnField.value === 'status') return statuses.value
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

  // ===== Effective columns (merge groups) =====
  const effectiveColumns = computed<EffectiveColumn[]>(() => {
    const cols = visibleStatuses.value
    const merges = columnMerges.value
    if (merges.length === 0) {
      return cols.map(s => ({
        id: s.id, name: localizeStatusName(s.name), color: s.color || '',
        category: s.category || '', statusIds: [s.id], isMerged: false, sortOrder: s.sortOrder
      }))
    }
    const statusToGroup = new Map<string, BoardColumnMergeGroupVO>()
    for (const group of merges) {
      for (const sid of group.statusIds) statusToGroup.set(sid, group)
    }
    const result: EffectiveColumn[] = []
    const processedGroups = new Set<string>()
    for (const s of cols) {
      const group = statusToGroup.get(s.id)
      if (group) {
        if (!processedGroups.has(group.mergeGroupId)) {
          processedGroups.add(group.mergeGroupId)
          const firstVisibleStatus = cols.find(c => group.statusIds.includes(c.id))
          result.push({
            id: group.mergeGroupId, name: group.mergeTitle,
            color: firstVisibleStatus?.color || '', category: firstVisibleStatus?.category || '',
            statusIds: group.statusIds.filter(sid => cols.some(c => c.id === sid)),
            isMerged: true, sortOrder: s.sortOrder
          })
        }
      } else {
        result.push({
          id: s.id, name: localizeStatusName(s.name), color: s.color || '',
          category: s.category || '', statusIds: [s.id], isMerged: false, sortOrder: s.sortOrder
        })
      }
    }
    return result
  })

  // ===== Column collapse =====
  const COLLAPSED_COLUMNS_KEY_PREFIX = 'tf_kanban_collapsed_columns'
  const collapsedColumns = ref<Set<string>>(new Set())
  const expandedEmptyColumns = ref<Set<string>>(new Set())

  function getCollapsedColumnsKey(): string {
    return selectedProject.value
      ? `${COLLAPSED_COLUMNS_KEY_PREFIX}_${selectedProject.value}`
      : COLLAPSED_COLUMNS_KEY_PREFIX
  }

  function loadCollapsedColumnsState() {
    const key = getCollapsedColumnsKey()
    const stored = localStorage.getItem(key)
    collapsedColumns.value = new Set(stored ? JSON.parse(stored) : [])
  }

  function saveCollapsedColumnsState() {
    const key = getCollapsedColumnsKey()
    if (collapsedColumns.value.size === 0) {
      localStorage.removeItem(key)
    } else {
      localStorage.setItem(key, JSON.stringify([...collapsedColumns.value]))
    }
  }

  function isColumnCollapsed(columnId: string): boolean {
    if (collapsedColumns.value.has(columnId)) return true
    const col = effectiveColumns.value.find(c => c.id === columnId)
    if (col) {
      if (getEffectiveColumnIssues(col).length === 0 && !expandedEmptyColumns.value.has(columnId)) return true
    } else {
      if (getColumnIssues(columnId).length === 0 && !expandedEmptyColumns.value.has(columnId)) return true
    }
    return false
  }

  function toggleColumnCollapse(columnId: string) {
    if (collapsedColumns.value.has(columnId)) {
      collapsedColumns.value.delete(columnId)
      expandedEmptyColumns.value.add(columnId)
    } else {
      collapsedColumns.value.add(columnId)
      expandedEmptyColumns.value.delete(columnId)
    }
    saveCollapsedColumnsState()
  }

  // ===== Column helpers =====
  function getColumnIssues(statusId: string): BoardIssue[] {
    let columnIssues: BoardIssue[]
    if (boardColumnField.value === 'priority') {
      columnIssues = issues.value.filter(i => (i.priority || 'Normal') === statusId)
    } else {
      columnIssues = issues.value.filter(i => i.statusId === statusId)
    }
    if (boardManualSorted.value && !isManualSortDisabled.value) {
      const { sorted, rest } = applyBoardManualOrder(columnIssues)
      return [...sorted, ...rest]
    }
    return columnIssues
  }

  function getEffectiveColumnIssues(column: EffectiveColumn): BoardIssue[] {
    return issues.value.filter(i => column.statusIds.includes(i.statusId))
  }

  function getSwimlaneEffectiveColumnIssues(laneKey: string, column: EffectiveColumn, swimlanes: { value: { key: string; issues: BoardIssue[] }[] }): BoardIssue[] {
    const lane = swimlanes.value.find(l => l.key === laneKey)
    if (!lane) return []
    return lane.issues.filter(i => column.statusIds.includes(i.statusId))
  }

  function getDropTargetStatusId(col: EffectiveColumn): string {
    return col.statusIds[0] ?? col.id
  }

  function isEffectiveColumnClosed(col: EffectiveColumn): boolean {
    if (!col.isMerged) return col.category === 'done' || col.category === 'cancelled'
    return col.statusIds.every(sid => {
      const config = allColumnConfigs.value.find(c => c.statusId === sid)
      if (config) return config.statusCategory === 'done' || config.statusCategory === 'cancelled'
      return false
    })
  }

  // ===== WIP =====
  function getColumnConfig(statusId: string): BoardColumnVO | undefined {
    if (boardColumnField.value === 'priority') {
      return allColumnConfigs.value.find(c => c.fieldValue === statusId && c.statusId == null)
    }
    return allColumnConfigs.value.find(c => c.statusId === statusId)
  }

  function getWipMin(statusId: string): number | null { return getColumnConfig(statusId)?.wipMin ?? null }
  function getWipMax(statusId: string): number | null { return getColumnConfig(statusId)?.wipMax ?? null }

  function getWipWarning(statusId: string): 'wip-over' | 'wip-under' | null {
    const config = getColumnConfig(statusId)
    if (!config) return null
    const count = getColumnIssues(statusId).length
    if (config.wipMax != null && count > config.wipMax) return 'wip-over'
    if (config.wipMin != null && count < config.wipMin) return 'wip-under'
    return null
  }

  function getWipClass(statusId: string): string {
    const w = getWipWarning(statusId)
    if (w === 'wip-over') return 'column-count--over'
    if (w === 'wip-under') return 'column-count--under'
    return ''
  }

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

  function getEffectiveColumnEstimation(col: EffectiveColumn): string {
    let total = 0
    for (const sid of col.statusIds) {
      const config = getColumnConfig(sid)
      if (config && config.totalEstimation) total += Number(config.totalEstimation)
    }
    if (total <= 0) return ''
    return total % 1 === 0 ? String(total) : total.toFixed(1)
  }

  function getEffectiveColumnWipMax(col: EffectiveColumn): number | null {
    if (!col.isMerged) return getWipMax(col.id)
    let total = 0
    for (const sid of col.statusIds) {
      const max = getWipMax(sid)
      if (max === null) return null
      total += max
    }
    return total || null
  }

  function getEffectiveColumnWipWarning(col: EffectiveColumn): 'wip-over' | 'wip-under' | null {
    if (!col.isMerged) return getWipWarning(col.id)
    const count = getEffectiveColumnIssues(col).length
    let totalWipMin = 0, totalWipMax = 0, hasWipMax = false, hasWipMin = false
    for (const sid of col.statusIds) {
      const config = getColumnConfig(sid)
      if (config?.wipMax != null) { totalWipMax += config.wipMax; hasWipMax = true }
      if (config?.wipMin != null) { totalWipMin += config.wipMin; hasWipMin = true }
    }
    if (hasWipMax && count > totalWipMax) return 'wip-over'
    if (hasWipMin && count < totalWipMin) return 'wip-under'
    return null
  }

  function getEffectiveColumnWipClass(col: EffectiveColumn): string {
    if (!col.isMerged) return getWipClass(col.id)
    const warning = getEffectiveColumnWipWarning(col)
    if (warning === 'wip-over') return 'wip-over'
    if (warning === 'wip-under') return 'wip-under'
    return ''
  }

  function isEffectiveColumnDropAllowed(col: EffectiveColumn, isDropAllowed: (s: string) => boolean): boolean {
    return col.statusIds.some(sid => isDropAllowed(sid))
  }

  // ===== Progress indicator =====
  function isClosedStatus(status: { category?: string; isClosed?: boolean }): boolean {
    if (status.isClosed) return true
    const cat = status.category?.toLowerCase()
    return cat === 'done' || cat === 'cancelled'
  }

  const activeStatuses = computed(() => visibleStatuses.value.filter(s => !isClosedStatus(s)))
  const closedStatuses = computed(() => visibleStatuses.value.filter(s => isClosedStatus(s)))
  const closedIssueCount = computed(() => closedStatuses.value.reduce((sum, s) => sum + getColumnIssues(s.id).length, 0))
  const closedIssueDetail = computed(() =>
    closedStatuses.value.filter(s => getColumnIssues(s.id).length > 0)
      .map(s => `${localizeStatusName(s.name)} ${getColumnIssues(s.id).length}`).join('、')
  )
  const progressIndicatorAriaLabel = computed(() => {
    const activeCount = activeStatuses.value.reduce((sum, s) => sum + getColumnIssues(s.id).length, 0)
    return `活跃工单分布：${activeCount} 个活跃工单` + (closedIssueCount.value > 0 ? `，${closedIssueCount.value} 个已完成` : '')
  })

  function getProgressBarHeight(statusId: string): string {
    const count = getColumnIssues(statusId).length
    if (count === 0) return '2px'
    const maxCount = Math.max(...activeStatuses.value.map(s => getColumnIssues(s.id).length), 1)
    const height = Math.max(4, Math.round((count / maxCount) * 24))
    return `${height}px`
  }

  function getClosedProgressBarHeight(): string {
    if (closedIssueCount.value === 0) return '2px'
    const maxActiveCount = Math.max(...activeStatuses.value.map(s => getColumnIssues(s.id).length), 1)
    const ratio = Math.min(closedIssueCount.value / maxActiveCount, 3)
    const height = Math.max(4, Math.min(16, Math.round(ratio * 8)))
    return `${height}px`
  }

  function scrollToColumn(statusId: string) {
    const col = effectiveColumns.value.find(c => c.statusIds.includes(statusId)) ||
                effectiveColumns.value.find(c => c.id === statusId)
    const targetId = col ? col.id : statusId
    if (collapsedColumns.value.has(targetId)) toggleColumnCollapse(targetId)
    setTimeout(() => {
      const container = document.querySelector('.board-container') || document.querySelector('.swimlane-container')
      if (!container) return
      const columnEl = container.querySelector(`[data-column-id="${targetId}"]`)
      if (columnEl) columnEl.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
    }, 50)
  }

  // ===== Hidden columns =====
  const hiddenIssueColumns = computed(() => allColumnConfigs.value.filter(c => !c.visible && c.hasHiddenIssues && (c.issueCount || 0) > 0))
  const hiddenIssueTotalCount = computed(() => hiddenIssueColumns.value.reduce((sum, c) => sum + (c.issueCount || 0), 0))

  function onHiddenStatusTagClick(col: BoardColumnVO) {
    if (!col.statusId && !col.fieldValue) return
    showAllColumns.value = true
    const targetId = col.statusId || col.fieldValue
    nextTick(() => { setTimeout(() => { scrollToColumn(targetId) }, 100) })
  }

  // ===== Board total estimation =====
  const boardTotalEstimation = computed(() => {
    if (!allColumnConfigs.value || allColumnConfigs.value.length === 0) return 0
    let total = 0
    for (const col of allColumnConfigs.value) {
      if (col.visible && col.totalEstimation) total += Number(col.totalEstimation)
    }
    return total
  })

  const boardStatusIdsForBacklog = computed(() => visibleStatuses.value.map(s => s.id).join(','))

  const isMultiProjectBoard = computed(() => false) // placeholder, set from deps

  // Watch showAllColumns to reload
  watch(showAllColumns, () => { if (selectedProject.value) loadIssuesWithLoading() })

  // ===== Card field helpers =====
  function isCardFieldVisible(field: string): boolean { return cardConfig.value.visibleFields.includes(field) }
  function getCardFieldDisplayMode(field: string): 'full_name' | 'initial' {
    const modes = cardConfig.value.fieldDisplayModes
    return (modes && modes[field]) || 'full_name'
  }

  const PROJECT_COLOR_PALETTE = ['#0ea5e9','#10b981','#f59e0b','#8b5cf6','#ef4444','#f97316','#14b8a6','#ec4899','#6366f1','#84cc16']
  function getProjectColor(projectId: string): string {
    if (!projectId) return PROJECT_COLOR_PALETTE[0]
    let hash = 5381
    for (let i = 0; i < projectId.length; i++) { hash = ((hash << 5) + hash) + projectId.charCodeAt(i); hash = hash & hash }
    return PROJECT_COLOR_PALETTE[Math.abs(hash) % PROJECT_COLOR_PALETTE.length]
  }

  function getCardColorClass(issue: BoardIssue): string {
    const scheme = cardConfig.value.colorScheme
    if (scheme === 'none') return ''
    if (scheme === 'priority') return `kanban-card--color-priority-${(issue.priority || 'Normal').toLowerCase()}`
    if (scheme === 'type') return `kanban-card--color-type-${(issue.issueType || 'task').toLowerCase()}`
    if (scheme === 'project') return `kanban-card--color-project`
    return ''
  }

  function getCardProjectColorStyle(issue: BoardIssue): Record<string, string> {
    if (cardConfig.value.colorScheme !== 'project') return {}
    return { borderLeftColor: getProjectColor(issue.projectId || '') }
  }

  function isIssueResolved(statusId: string): boolean {
    const config = allColumnConfigs.value.find(c => c.statusId === statusId)
    if (config) return config.statusCategory === 'done' || config.statusCategory === 'cancelled'
    const status = statuses.value.find(s => s.id === statusId)
    return status?.isClosed === true
  }

  function getCardDueDateClass(issue: BoardIssue): string {
    if (!issue.dueDate) return ''
    const info = getDueDateInfo(issue.dueDate, isIssueResolved(issue.statusId))
    if (info.status === 'overdue') return 'card-meta-tag--overdue'
    if (info.status === 'due-soon') return 'card-meta-tag--due-soon'
    return ''
  }

  function getCardDueDateTooltip(issue: BoardIssue): string {
    if (!issue.dueDate) return ''
    return getDueDateInfo(issue.dueDate, isIssueResolved(issue.statusId)).tooltip
  }

  function hasVisibleCustomFields(issue: BoardIssue): boolean {
    if (!issue.customFieldDetails || issue.customFieldDetails.length === 0) return false
    const hasCfConfig = cardConfig.value.visibleFields.some(f => f.startsWith('cf.'))
    if (!hasCfConfig) return false
    return getVisibleCustomFieldDetails(issue).length > 0
  }

  function getVisibleCustomFieldDetails(issue: BoardIssue) {
    if (!issue.customFieldDetails) return []
    const selectedCfIds = new Set(cardConfig.value.visibleFields.filter(f => f.startsWith('cf.')).map(f => f.substring(3)))
    if (selectedCfIds.size === 0) return []
    const maxFields = cardSize.value === 'L' ? 4 : 2
    return issue.customFieldDetails.filter(d => selectedCfIds.has(d.customFieldId)).slice(0, maxFields)
  }

  function getVisibleTags(issue: BoardIssue): Array<{ id: string; name: string; color?: string }> {
    const tags = issue.tags
    if (!tags || tags.length === 0) return []
    const maxTags = cardSize.value === 'M' ? 2 : 4
    return tags.slice(0, maxTags)
  }

  return {
    // Manual order
    boardManualSorted, isManualSortDisabled,
    loadBoardManualOrder, applyBoardManualOrder, saveBoardManualOrder, resetBoardManualOrder,
    // Visible statuses & effective columns
    visibleStatuses, effectiveColumns,
    // Collapse
    collapsedColumns, expandedEmptyColumns,
    loadCollapsedColumnsState, isColumnCollapsed, toggleColumnCollapse,
    // Column helpers
    getColumnIssues, getEffectiveColumnIssues, getSwimlaneEffectiveColumnIssues,
    getDropTargetStatusId, isEffectiveColumnClosed,
    getColumnConfig, getWipWarning, getWipTooltip,
    getEffectiveColumnEstimation, getEffectiveColumnWipMax,
    getEffectiveColumnWipWarning, getEffectiveColumnWipClass,
    isEffectiveColumnDropAllowed,
    // Progress
    activeStatuses, closedIssueCount, closedIssueDetail,
    progressIndicatorAriaLabel, getProgressBarHeight, getClosedProgressBarHeight,
    scrollToColumn,
    // Hidden columns
    hiddenIssueColumns, hiddenIssueTotalCount, onHiddenStatusTagClick,
    // Board misc
    boardTotalEstimation, boardStatusIdsForBacklog, isMultiProjectBoard,
    isClosedStatus,
    // Card field helpers
    isCardFieldVisible, getCardFieldDisplayMode,
    getCardColorClass, getCardProjectColorStyle,
    getCardDueDateClass, getCardDueDateTooltip,
    hasVisibleCustomFields, getVisibleCustomFieldDetails, getVisibleTags,
  }
}
