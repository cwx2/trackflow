// @ts-nocheck
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import { Message } from '@arco-design/web-vue'
import { issueApi } from '@/api'
import type { IssueStatusVO } from '@/api/types'
import type { BoardIssue } from './useKanbanBoard'

/**
 * 看板拖拽核心逻辑 — 管理拖拽状态和事件处理。
 *
 * 负责：
 * - 拖拽状态管理（draggingIssue, dragOverColumnId, allowedTargets）
 * - 拖拽事件（dragstart, dragover, dragleave, dragend）
 * - 拖放验证（isDropAllowed, isCardDraggable）
 * - 撤销状态管理（undoStack）
 *
 * 不负责（由主 composable 处理）：
 * - onDrop 的业务逻辑（状态转换、WIP 限制弹窗）
 * - 跨泳道更新逻辑
 * - Backlog 拖放处理
 */
export interface DragDeps {
  statuses: Ref<IssueStatusVO[]>
  boardColumnField: Ref<'status' | 'priority'>
  visibleStatuses: ComputedRef<IssueStatusVO[]>
  isManualSortDisabled: Ref<boolean>
  canChangeStatus: ComputedRef<boolean>
  backlogDraggingIssue: Ref<BoardIssue | null>
  dragOverSwimlaneKey: Ref<string | null>
}

export interface UndoEntry {
  issueId: string
  issueKey: string
  oldStatusId: string
  newStatusId: string
  oldStatusName: string
  newStatusName: string
  timestamp: number
}

export const UNDO_TIMEOUT = 10000

export function useBoardDrag(deps: DragDeps) {
  const {
    statuses, boardColumnField, visibleStatuses, isManualSortDisabled,
    canChangeStatus, backlogDraggingIssue, dragOverSwimlaneKey
  } = deps

  // ===== Drag state =====
  const draggingIssue = ref<BoardIssue | null>(null)
  const dragOverColumnId = ref<string | null>(null)
  const allowedTargetStatuses = ref<Set<string>>(new Set())
  const requireCommentStatuses = ref<Set<string>>(new Set())
  const transitioningIssueIds = ref<Set<string>>(new Set())
  const transitionableSourceStatuses = ref<Set<string>>(new Set())

  // Combined dragging state (from board card or backlog)
  const isDragging = computed(() => !!draggingIssue.value || !!backlogDraggingIssue.value)

  // ===== Undo stack =====
  const undoStack = ref<UndoEntry[]>([])

  // ===== Drag event handlers =====

  function isCardDraggable(issue: BoardIssue): boolean {
    // Priority mode: always draggable (no workflow constraint, just needs edit permission)
    if (boardColumnField.value === 'priority') return true
    if (!canChangeStatus.value) return false
    if (transitionableSourceStatuses.value.size === 0) return true
    return transitionableSourceStatuses.value.has(issue.statusId)
  }

  function onDragStart(event: DragEvent, issue: BoardIssue) {
    if (!isCardDraggable(issue)) {
      event.preventDefault()
      Message.warning('该工单当前状态不允许变更')
      return
    }

    draggingIssue.value = issue

    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move'
      event.dataTransfer.setData('text/plain', issue.id)
    }

    // Priority mode: all columns are valid targets (no workflow constraint)
    if (boardColumnField.value === 'priority') {
      allowedTargetStatuses.value = new Set(visibleStatuses.value.map(s => s.id))
      return
    }

    // Optimistic: allow all statuses immediately so drag feedback works instantly.
    allowedTargetStatuses.value = new Set(statuses.value.map(s => s.id))
    requireCommentStatuses.value = new Set()

    // Fetch actual allowed transitions asynchronously (non-blocking).
    issueApi.getAvailableTransitions(issue.id).then(res => {
      if (draggingIssue.value?.id === issue.id) {
        const allowed = res.data || []
        allowedTargetStatuses.value = new Set(allowed.map(s => s.id))
        requireCommentStatuses.value = new Set(allowed.filter(s => s.requireComment).map(s => s.id))
      }
    }).catch(() => {
      if (draggingIssue.value?.id === issue.id) {
        allowedTargetStatuses.value = new Set(statuses.value.map(s => s.id))
        requireCommentStatuses.value = new Set()
      }
    })
  }

  function onDragEnd() {
    draggingIssue.value = null
    dragOverColumnId.value = null
    dragOverSwimlaneKey.value = null
    allowedTargetStatuses.value.clear()
    requireCommentStatuses.value.clear()
  }

  function onDragOver(event: DragEvent, statusId: string) {
    event.preventDefault()
    dragOverColumnId.value = statusId
    dragOverSwimlaneKey.value = null

    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = isDropAllowed(statusId) ? 'move' : 'none'
    }
  }

  function onDragLeave(event: DragEvent) {
    const relatedTarget = event.relatedTarget as HTMLElement | null
    const currentTarget = event.currentTarget as HTMLElement
    if (relatedTarget && currentTarget.contains(relatedTarget)) return
    dragOverColumnId.value = null
    dragOverSwimlaneKey.value = null
  }

  function isDropAllowed(targetStatusId: string): boolean {
    // Priority mode: always allow (no workflow restriction, just check not same column)
    if (boardColumnField.value === 'priority') {
      if (!draggingIssue.value) return false
      if ((draggingIssue.value.priority || 'Normal') === targetStatusId) {
        return !isManualSortDisabled.value
      }
      return true
    }
    // Allow drop from backlog panel
    if (backlogDraggingIssue.value) {
      return allowedTargetStatuses.value.has(targetStatusId)
    }
    if (!draggingIssue.value) return false
    // Allow within-column drop for reordering
    if (draggingIssue.value.statusId === targetStatusId) {
      return !isManualSortDisabled.value
    }
    return allowedTargetStatuses.value.has(targetStatusId)
  }

  return {
    // State
    draggingIssue,
    dragOverColumnId,
    allowedTargetStatuses,
    requireCommentStatuses,
    transitioningIssueIds,
    transitionableSourceStatuses,
    isDragging,
    undoStack,

    // Event handlers
    isCardDraggable,
    onDragStart,
    onDragEnd,
    onDragOver,
    onDragLeave,
    isDropAllowed,

    // Constants
    UNDO_TIMEOUT
  }
}
