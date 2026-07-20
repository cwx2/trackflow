/**
 * Due date status utilities.
 * Shared by IssueListView, KanbanBoardView, IssuePreviewDrawer, etc.
 */

export type DueDateStatus = 'overdue' | 'due-soon' | 'normal'

export interface DueDateInfo {
  /** Status classification */
  status: DueDateStatus
  /** Days difference: negative = overdue, 0 = today, positive = days remaining */
  diffDays: number
  /** Human-readable tooltip text */
  tooltip: string
}

/**
 * Calculate due date status and metadata.
 *
 * @param dueDate - ISO date string (yyyy-MM-dd or full ISO)
 * @param isClosed - Whether the issue is in a closed/resolved state (suppresses warnings)
 * @param soonThreshold - Days threshold for "due-soon" warning (default: 3)
 */
export function getDueDateInfo(
  dueDate: string | undefined | null,
  isClosed = false,
  soonThreshold = 3
): DueDateInfo {
  if (!dueDate) {
    return { status: 'normal', diffDays: Infinity, tooltip: '' }
  }

  // Closed/resolved issues never show warning colors
  if (isClosed) {
    return { status: 'normal', diffDays: 0, tooltip: '' }
  }

  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const due = new Date(dueDate)
  due.setHours(0, 0, 0, 0)
  const diffDays = Math.floor((due.getTime() - today.getTime()) / 86400000)

  let status: DueDateStatus = 'normal'
  let tooltip = ''

  if (diffDays < 0) {
    status = 'overdue'
    tooltip = `已逾期 ${Math.abs(diffDays)} 天`
  } else if (diffDays === 0) {
    status = 'due-soon'
    tooltip = '今天到期'
  } else if (diffDays === 1) {
    status = 'due-soon'
    tooltip = '明天到期'
  } else if (diffDays <= soonThreshold) {
    status = 'due-soon'
    tooltip = `${diffDays} 天后到期`
  } else {
    tooltip = `${diffDays} 天后到期`
  }

  return { status, diffDays, tooltip }
}

/**
 * Format due date for compact display (card/badge).
 * Returns short format like "7/12" or "逾期8天".
 */
export function formatDueDateShort(dueDate: string): string {
  if (!dueDate) return ''
  const d = new Date(dueDate)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
