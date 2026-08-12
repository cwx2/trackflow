// @ts-nocheck
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import { boardApi } from '@/api'
import type { SprintVO } from '@/api/types'
import type { BoardIssue, SwimlaneGroupBy, SwimlaneRow } from './useKanbanBoard'

const SWIMLANE_STORAGE_KEY = 'tf_kanban_swimlane'
const COLLAPSED_SWIMLANES_KEY = 'tf_kanban_collapsed_swimlanes'
const SWIMLANE_ORDER_KEY_PREFIX = 'tf_kanban_swimlane_order'

export interface SwimlaneDeps {
  issues: Ref<BoardIssue[]>
  sprints: Ref<SprintVO[]>
  selectedProject: ComputedRef<string | undefined>
  swimlaneGroupBy: Ref<SwimlaneGroupBy>
  swimlaneSelectedValues: Ref<string[] | null>
  swimlaneShowUncategorized: Ref<boolean>
  swimlaneUncategorizedPosition: Ref<'top' | 'bottom'>
  swimlaneIssueType: Ref<string | null>
  isDragging: ComputedRef<boolean>
  dragOverColumnId: Ref<string | null>
  isDropAllowed: (statusId: string) => boolean
  syncUrlState: () => void
}

export function useBoardSwimlane(deps: SwimlaneDeps) {
  const {
    issues, sprints, selectedProject,
    swimlaneGroupBy, swimlaneSelectedValues, swimlaneShowUncategorized,
    swimlaneUncategorizedPosition, swimlaneIssueType,
    isDragging, dragOverColumnId, isDropAllowed, syncUrlState
  } = deps

  // ===== Collapsed swimlanes =====
  const collapsedSwimlanes = ref<Set<string>>(
    new Set(JSON.parse(localStorage.getItem(COLLAPSED_SWIMLANES_KEY) || '[]'))
  )

  function onSwimlaneChange() {
    localStorage.setItem(SWIMLANE_STORAGE_KEY, swimlaneGroupBy.value)
    collapsedSwimlanes.value.clear()
    localStorage.removeItem(COLLAPSED_SWIMLANES_KEY)
    swimlaneSelectedValues.value = null
    swimlaneShowUncategorized.value = true
    swimlaneUncategorizedPosition.value = 'bottom'
    if (swimlaneGroupBy.value !== 'parent') {
      swimlaneIssueType.value = null
    }
    clearSwimlaneOrder()
    loadSwimlaneOrder()
    syncUrlState()
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

  // ===== Swimlane row drag ordering =====
  function getSwimlaneOrderKey(): string {
    return selectedProject.value
      ? `${SWIMLANE_ORDER_KEY_PREFIX}_${selectedProject.value}_${swimlaneGroupBy.value}`
      : `${SWIMLANE_ORDER_KEY_PREFIX}_${swimlaneGroupBy.value}`
  }

  const swimlaneCustomOrder = ref<string[] | null>(null)

  function loadSwimlaneOrder() {
    const key = getSwimlaneOrderKey()
    const stored = localStorage.getItem(key)
    swimlaneCustomOrder.value = stored ? JSON.parse(stored) : null
  }

  function saveSwimlaneOrder(order: string[]) {
    const key = getSwimlaneOrderKey()
    localStorage.setItem(key, JSON.stringify(order))
    swimlaneCustomOrder.value = order
  }

  function clearSwimlaneOrder() {
    if (selectedProject.value) {
      const key = getSwimlaneOrderKey()
      localStorage.removeItem(key)
    }
    swimlaneCustomOrder.value = null
  }

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

  const swimlaneDraggingKey = ref<string | null>(null)
  const swimlaneDragOverKey = ref<string | null>(null)

  function onSwimlaneRowDragStart(event: DragEvent, laneKey: string) {
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
    const currentOrder = orderedSwimlanes.value.map(l => l.key)
    const fromIdx = currentOrder.indexOf(fromKey)
    const toIdx = currentOrder.indexOf(targetKey)
    if (fromIdx < 0 || toIdx < 0) return
    const newOrder = [...currentOrder]
    newOrder.splice(fromIdx, 1)
    const insertIdx = fromIdx < toIdx ? toIdx : toIdx
    newOrder.splice(insertIdx, 0, fromKey)
    saveSwimlaneOrder(newOrder)
  }

  // ===== Swimlane drag over state =====
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

  // ===== Swimlane grouping logic =====
  const TYPE_LABELS = { Task: '任务', Bug: '缺陷', Feature: '需求', Epic: '史诗', Story: '故事' } as Record<string, string>

  const swimlanes = computed<SwimlaneRow[]>(() => {
    if (swimlaneGroupBy.value === 'none') return []
    const allIssues = issues.value
    let rows: SwimlaneRow[]
    switch (swimlaneGroupBy.value) {
      case 'assignee': rows = groupByAssignee(allIssues); break
      case 'priority': rows = groupByPriority(allIssues); break
      case 'type': rows = groupByType(allIssues); break
      case 'sprint': rows = groupBySprint(allIssues); break
      case 'tag': rows = groupByTag(allIssues); break
      case 'parent': rows = groupByParent(allIssues); break
      case 'dueDate': return groupByDueDate(allIssues)
      default: return []
    }
    const selected = swimlaneSelectedValues.value
    if (selected && selected.length > 0) {
      const selectedSet = new Set(selected)
      const filteredRows: SwimlaneRow[] = []
      const uncategorizedIssues: BoardIssue[] = []
      for (const row of rows) {
        if (selectedSet.has(row.key)) {
          filteredRows.push(row)
        } else {
          uncategorizedIssues.push(...row.issues)
        }
      }
      filteredRows.sort((a, b) => selected.indexOf(a.key) - selected.indexOf(b.key))
      if (swimlaneShowUncategorized.value && uncategorizedIssues.length > 0) {
        const uncategorizedRow: SwimlaneRow = { key: '__uncategorized__', label: '未分类', issues: uncategorizedIssues }
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
    const rows: SwimlaneRow[] = [...groups.entries()]
      .sort((a, b) => b[1].length - a[1].length)
      .map(([assigneeId, issues]) => ({ key: assigneeId, label: issues[0].assigneeName || '未知', issues }))
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
      .map(p => ({ key: p, label: p, issues: groups.get(p)! }))
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
      .map(t => ({ key: t, label: TYPE_LABELS[t] || t, issues: groups.get(t)! }))
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
    const sprintMap = new Map(sprints.value.map(s => [s.id, s.name]))
    const rows: SwimlaneRow[] = [...groups.entries()].map(([sprintId, issues]) => ({
      key: sprintId, label: sprintMap.get(sprintId) || `Sprint ${sprintId}`, issues
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

  function groupByParent(allIssues: BoardIssue[]): SwimlaneRow[] {
    const targetType = swimlaneIssueType.value
    const parentMap = new Map<string, BoardIssue>()
    const childrenMap = new Map<string, BoardIssue[]>()
    const uncategorized: BoardIssue[] = []
    for (const issue of allIssues) {
      if (targetType && issue.issueType === targetType) {
        parentMap.set(issue.id, issue)
      }
    }
    for (const issue of allIssues) {
      if (parentMap.has(issue.id)) continue
      const parentId = ('parentId' in issue ? issue.parentId : undefined) as string | undefined
      if (parentId && parentMap.has(parentId)) {
        if (!childrenMap.has(parentId)) childrenMap.set(parentId, [])
        childrenMap.get(parentId)!.push(issue)
      } else {
        uncategorized.push(issue)
      }
    }
    const rows: SwimlaneRow[] = []
    for (const [parentId, parentIssue] of parentMap.entries()) {
      const children = childrenMap.get(parentId) || []
      rows.push({ key: parentId, label: `${parentIssue.issueKey} ${parentIssue.title}`, issues: children })
    }
    rows.sort((a, b) => {
      const keyA = parentMap.get(a.key)?.issueKey || a.key
      const keyB = parentMap.get(b.key)?.issueKey || b.key
      return keyA.localeCompare(keyB)
    })
    if (swimlaneShowUncategorized.value && uncategorized.length > 0) {
      const uncategorizedRow: SwimlaneRow = { key: '__uncategorized__', label: '未分类', issues: uncategorized }
      if (swimlaneUncategorizedPosition.value === 'top') {
        rows.unshift(uncategorizedRow)
      } else {
        rows.push(uncategorizedRow)
      }
    }
    return rows
  }

  function groupByDueDate(allIssues: BoardIssue[]): SwimlaneRow[] {
    const now = new Date()
    const todayStr = now.toISOString().slice(0, 10)
    const dayOfWeek = now.getDay()
    const daysToMonday = (dayOfWeek === 0 ? -6 : 1 - dayOfWeek)
    const monday = new Date(now)
    monday.setDate(now.getDate() + daysToMonday)
    monday.setHours(0, 0, 0, 0)
    const sunday = new Date(monday)
    sunday.setDate(monday.getDate() + 6)
    sunday.setHours(23, 59, 59, 999)
    const nextMonday = new Date(monday)
    nextMonday.setDate(monday.getDate() + 7)
    const nextSunday = new Date(sunday)
    nextSunday.setDate(sunday.getDate() + 7)
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
    const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0)
    monthEnd.setHours(23, 59, 59, 999)

    type DueDateBucket = 'overdue' | 'today' | 'this_week' | 'next_week' | 'this_month' | 'later' | 'no_date'
    function getBucket(dueDate: string | undefined): DueDateBucket {
      if (!dueDate) return 'no_date'
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
      overdue: '⚠️ 已过期', today: '📅 今天', this_week: '📅 本周',
      next_week: '📅 下周', this_month: '📅 本月', later: '📅 更晚', no_date: '— 无截止日期',
    }
    const groups: Record<DueDateBucket, BoardIssue[]> = {
      overdue: [], today: [], this_week: [], next_week: [], this_month: [], later: [], no_date: [],
    }
    for (const issue of allIssues) {
      const bucket = getBucket(issue.dueDate)
      groups[bucket].push(issue)
    }
    return bucketOrder
      .filter(b => groups[b].length > 0)
      .map(b => ({ key: b, label: bucketLabels[b], issues: groups[b] }))
  }

  /** 获取某泳道中某状态列的工单 */
  function getSwimlaneColumnIssues(laneKey: string, statusId: string): BoardIssue[] {
    const lane = swimlanes.value.find(l => l.key === laneKey)
    if (!lane) return []
    return lane.issues.filter(i => i.statusId === statusId)
  }

  return {
    // State
    collapsedSwimlanes,
    swimlaneCustomOrder,
    swimlaneDraggingKey,
    swimlaneDragOverKey,
    dragOverSwimlaneKey,
    swimlanes,
    orderedSwimlanes,
    // Methods
    onSwimlaneChange,
    toggleSwimlane,
    loadSwimlaneOrder,
    clearSwimlaneOrder,
    onSwimlaneRowDragStart,
    onSwimlaneRowDragEnd,
    onSwimlaneRowDragOver,
    onSwimlaneRowDragLeave,
    onSwimlaneRowDrop,
    onDragOverSwimlane,
    onDragLeaveSwimlane,
    getSwimlaneColumnIssues,
  }
}
