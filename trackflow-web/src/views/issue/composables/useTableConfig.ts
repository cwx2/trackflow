import { ref, reactive, computed, type Ref } from 'vue'
import type { IssueVO, IssueStatusVO } from '@/api/types'
import type { TableData } from '@arco-design/web-vue'
import { localizeStatusName } from '@/utils/fieldLabels'

export interface TableConfigOptions {
  visibleColumns: Ref<Array<{ key: string; label: string; fixed?: boolean; sortable?: boolean }>>
  canBatchOps: Ref<boolean>
  statusCache: Ref<IssueStatusVO[]>
  sortState: Ref<{ field: string | null; direction: 'asc' | 'desc' | null }>
  previewMode: Ref<'sidebar' | 'off'>
  previewVisible: Ref<boolean>
  previewIssueId: Ref<string | null>
  focusedIssueId: Ref<string | null>
  sprintOptionsCache: Record<string, any[]>
  toggleColumn: (key: string) => void
  reorderColumn: (fromKey: string, toKey: string) => void
}

const COLUMN_WIDTH_STORAGE_KEY = 'trackflow:issue-column-widths'
const DEFAULT_COLUMN_WIDTHS: Record<string, number> = {
  issueKey: 130,
  title: 300,
  project: 120,
  assignee: 110,
  status: 120,
  sprint: 160,
  priority: 100,
  updatedAt: 100,
  issueType: 80,
  reporter: 110,
  createdAt: 110,
  dueDate: 100,
  estimatedHours: 100,
  spentHours: 100,
  remaining: 100
}

export function useTableConfig(options: TableConfigOptions) {
  const {
    visibleColumns, canBatchOps, statusCache, sortState,
    previewMode, previewVisible, previewIssueId, focusedIssueId,
    sprintOptionsCache, toggleColumn, reorderColumn
  } = options

  const columnWidths = reactive<Record<string, number>>(loadColumnWidths())

  function loadColumnWidths(): Record<string, number> {
    try {
      const stored = localStorage.getItem(COLUMN_WIDTH_STORAGE_KEY)
      if (stored) return { ...DEFAULT_COLUMN_WIDTHS, ...JSON.parse(stored) }
    } catch { /* JSON parse fallback */ }
    return { ...DEFAULT_COLUMN_WIDTHS }
  }

  const tableMinWidth = computed(() => {
    const sum = visibleColumns.value
      .filter(c => c.key !== 'checkbox')
      .reduce((acc, col) => {
        return acc + (columnWidths[col.key] || DEFAULT_COLUMN_WIDTHS[col.key] || 100)
      }, 0)
    return sum + (canBatchOps.value ? 60 : 0)
  })

  const tableColumns = computed(() => {
    return visibleColumns.value
      .filter(c => c.key !== 'checkbox')
      .map(col => ({
        title: col.label,
        dataIndex: col.key,
        slotName: col.key === 'title' ? 'title-cell' : col.key.startsWith('cf_') ? 'customFieldCell' : col.key,
        titleSlotName: 'column-header',
        width: columnWidths[col.key] || DEFAULT_COLUMN_WIDTHS[col.key] || 100,
        ellipsis: true,
        tooltip: col.key === 'title'
      }))
  })

  const rowSelection = computed(() => {
    if (!canBatchOps.value) return undefined
    return {
      type: 'checkbox' as const,
      showCheckedAll: true
    }
  })

  function onColumnResize(dataIndex: string, width: number) {
    columnWidths[dataIndex] = width
    localStorage.setItem(COLUMN_WIDTH_STORAGE_KEY, JSON.stringify(columnWidths))
  }

  function onHeaderSort(key: string) {
    if (sortState.value.field !== key) {
      sortState.value = { field: key, direction: 'asc' }
    } else if (sortState.value.direction === 'asc') {
      sortState.value = { field: key, direction: 'desc' }
    } else {
      sortState.value = { field: null, direction: null }
    }
  }

  function onHeaderRemove(key: string) {
    toggleColumn(key)
  }

  function onHeaderDragDrop(fromKey: string, toKey: string) {
    reorderColumn(fromKey, toKey)
  }

  function getColumnSortDir(key: string): 'asc' | 'desc' | null {
    if (sortState.value.field === key) return sortState.value.direction
    return null
  }

  function isColumnFixed(key: string): boolean {
    const col = visibleColumns.value.find(c => c.key === key)
    return col?.fixed === true
  }

  function isColumnSortable(key: string): boolean {
    const col = visibleColumns.value.find(c => c.key === key)
    return col?.sortable === true
  }

  // Status helpers
  function isResolved(statusId: string): boolean {
    const s = statusCache.value.find(st => st.id === statusId)
    return s?.isClosed === true
  }

  function getStatusName(id: string, inlineName?: string) {
    if (inlineName) return localizeStatusName(inlineName)
    const s = statusCache.value.find(st => st.id === id)
    return localizeStatusName(s?.name)
  }

  function getStatusColor(id: string, inlineColor?: string) {
    if (inlineColor) return inlineColor
    const s = statusCache.value.find(st => st.id === id)
    return s?.color || 'var(--tf-text-tertiary)'
  }

  function getSprintName(sprintId?: string, inlineName?: string) {
    if (inlineName) return inlineName
    if (!sprintId) return ''
    for (const sprints of Object.values(sprintOptionsCache)) {
      const found = (sprints as any[]).find(s => s.id === sprintId)
      if (found) return found.name
    }
    return ''
  }

  // Due date helpers
  function getDueDateStatus(record: TableData): 'overdue' | 'due-soon' | 'normal' {
    if (!record.dueDate) return 'normal'
    if (isResolved(record.statusId as string)) return 'normal'
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const due = new Date(record.dueDate as string)
    due.setHours(0, 0, 0, 0)
    const diffDays = Math.floor((due.getTime() - today.getTime()) / 86400000)
    if (diffDays < 0) return 'overdue'
    if (diffDays <= 3) return 'due-soon'
    return 'normal'
  }

  function getDueDateTooltip(record: TableData): string {
    if (!record.dueDate) return ''
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const due = new Date(record.dueDate as string)
    due.setHours(0, 0, 0, 0)
    const diffDays = Math.floor((due.getTime() - today.getTime()) / 86400000)
    if (diffDays < 0) return `已逾期 ${Math.abs(diffDays)} 天`
    if (diffDays === 0) return '今天到期'
    if (diffDays === 1) return '明天到期'
    return `${diffDays} 天后到期`
  }

  function getRowClass(record: TableData): string {
    const classes: string[] = []
    if (isResolved(record.statusId as string)) classes.push('issue-resolved')
    if (previewMode.value === 'sidebar' && previewVisible.value && record.id === previewIssueId.value) {
      classes.push('issue-previewing')
    }
    if (focusedIssueId.value && record.id === focusedIssueId.value) {
      classes.push('issue-keyboard-focused')
    }
    return classes.join(' ')
  }

  // Time formatting
  function formatHoursCell(hours: number | null | undefined): string {
    if (hours == null || hours <= 0) return '\u2014'
    const totalMins = Math.round(hours * 60)
    const h = Math.floor(totalMins / 60)
    const m = totalMins % 60
    if (h === 0) return `${m}m`
    if (m === 0) return `${h}h`
    return `${h}h ${m}m`
  }

  function formatRemainingCell(record: any): string {
    const estimated = record.estimatedHours ?? 0
    if (estimated <= 0) return '\u2014'
    const spent = record.spentHours ?? 0
    const remaining = estimated - spent
    if (remaining <= 0) return '0h'
    const totalMins = Math.round(remaining * 60)
    const h = Math.floor(totalMins / 60)
    const m = totalMins % 60
    if (h === 0) return `${m}m`
    if (m === 0) return `${h}h`
    return `${h}h ${m}m`
  }

  function getSpentHoursClass(record: any): string {
    const estimated = record.estimatedHours ?? 0
    const spent = record.spentHours ?? 0
    if (estimated > 0 && spent > estimated) return 'time-over-budget'
    return ''
  }

  function getRemainingClass(record: any): string {
    const estimated = record.estimatedHours ?? 0
    const spent = record.spentHours ?? 0
    if (estimated > 0 && spent > estimated) return 'time-over-budget'
    return ''
  }

  return {
    columnWidths,
    tableMinWidth,
    tableColumns,
    rowSelection,
    onColumnResize,
    onHeaderSort,
    onHeaderRemove,
    onHeaderDragDrop,
    getColumnSortDir,
    isColumnFixed,
    isColumnSortable,
    isResolved,
    getStatusName,
    getStatusColor,
    getSprintName,
    getDueDateStatus,
    getDueDateTooltip,
    getRowClass,
    formatHoursCell,
    formatRemainingCell,
    getSpentHoursClass,
    getRemainingClass
  }
}
