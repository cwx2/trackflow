import { ref, computed, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import type { IssueVO } from '@/api/types'

export interface KeyboardNavOptions {
  issues: Ref<IssueVO[]>
  canCreateIssueGlobal: boolean | Ref<boolean>
  canBatchOps: Ref<boolean>
  previewMode: Ref<'sidebar' | 'off'>
  previewVisible: Ref<boolean>
  previewIssueId: Ref<string | null>
  activeIssueIndex: Ref<number>
  showCommandDialog: Ref<boolean>
  showCreatePanel: Ref<boolean>
  showShortcutsHelp: Ref<boolean>
  selectedCount: Ref<number>
  toggle: (id: string) => void
  toggleAll: () => void
  openPreview: (issue: IssueVO, index: number) => void
  closePreview: () => void
  onPreviewGoDetail: (issueId: string) => void
}

export function useKeyboardNav(options: KeyboardNavOptions) {
  const router = useRouter()

  const {
    issues, canCreateIssueGlobal, canBatchOps,
    previewMode, previewVisible, previewIssueId, activeIssueIndex,
    showCommandDialog, showCreatePanel, showShortcutsHelp, selectedCount,
    toggle, toggleAll, openPreview, closePreview, onPreviewGoDetail
  } = options

  /** Index of the keyboard-focused row in the current issues list (-1 = no focus) */
  const focusedIndex = ref<number>(-1)

  /** ID of the keyboard-focused issue (derived from focusedIndex) */
  const focusedIssueId = computed<string | null>(() => {
    if (focusedIndex.value >= 0 && focusedIndex.value < issues.value.length) {
      return issues.value[focusedIndex.value].id
    }
    return null
  })

  /** Scroll the focused row into view if needed */
  function scrollFocusedIntoView() {
    if (focusedIndex.value < 0) return
    const issue = issues.value[focusedIndex.value]
    if (!issue) return
    const el = (
      document.querySelector(`[data-id="${issue.id}"]`) ||
      document.querySelector(`[data-row-key="${issue.id}"]`)
    ) as HTMLElement | null
    el?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }

  function navigateIssue(direction: number) {
    const newIndex = activeIssueIndex.value + direction
    if (newIndex >= 0 && newIndex < issues.value.length) {
      activeIssueIndex.value = newIndex
      const issue = issues.value[newIndex]
      previewIssueId.value = issue.id
    }
  }

  function handleKeyboardNav(e: KeyboardEvent) {
    // Apply Command dialog: Ctrl+Alt+J
    if (e.key === 'j' && e.ctrlKey && e.altKey && selectedCount.value > 0) {
      e.preventDefault()
      showCommandDialog.value = true
      return
    }

    // Don't intercept when focus is in an input field (typing)
    const tag = (e.target as HTMLElement)?.tagName?.toLowerCase()
    const isEditing = tag === 'input' || tag === 'textarea' || tag === 'select'
    const isContentEditable = (e.target as HTMLElement)?.isContentEditable

    // ===== Preview mode sidebar: ArrowUp/Down navigate preview =====
    if (previewMode.value === 'sidebar' && previewVisible.value && !isEditing && !isContentEditable) {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        navigateIssue(1)
        return
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        navigateIssue(-1)
        return
      } else if (e.key === 'Enter') {
        e.preventDefault()
        if (previewIssueId.value) {
          onPreviewGoDetail(previewIssueId.value)
        }
        return
      } else if (e.key === 'Escape') {
        e.preventDefault()
        closePreview()
        return
      }
    }

    // ===== Global shortcuts (not in input) =====
    if (isEditing || isContentEditable) return

    // Escape: close any open panel/modal
    if (e.key === 'Escape') {
      if (showShortcutsHelp.value) {
        showShortcutsHelp.value = false
      } else if (previewVisible.value) {
        closePreview()
      } else if (showCommandDialog.value) {
        showCommandDialog.value = false
      } else if (focusedIndex.value >= 0) {
        focusedIndex.value = -1
      }
      return
    }

    // ? — toggle shortcuts help panel
    if (e.key === '?' || (e.key === '/' && e.shiftKey)) {
      e.preventDefault()
      showShortcutsHelp.value = !showShortcutsHelp.value
      return
    }

    // Don't trigger list shortcuts when any modal/dialog is open
    if (showCommandDialog.value || showCreatePanel.value || showShortcutsHelp.value) return

    // N — create new issue
    if (e.key === 'n' || e.key === 'N') {
      const canCreate = typeof canCreateIssueGlobal === 'boolean'
        ? canCreateIssueGlobal
        : canCreateIssueGlobal.value
      if (canCreate) {
        e.preventDefault()
        showCreatePanel.value = true
      }
      return
    }

    // J / ArrowDown — move focus down
    if (e.key === 'j' || e.key === 'J' || e.key === 'ArrowDown') {
      e.preventDefault()
      if (issues.value.length === 0) return
      if (focusedIndex.value < issues.value.length - 1) {
        focusedIndex.value++
      } else {
        focusedIndex.value = 0 // wrap to top
      }
      scrollFocusedIntoView()
      return
    }

    // K / ArrowUp — move focus up
    if (e.key === 'k' || e.key === 'K' || e.key === 'ArrowUp') {
      e.preventDefault()
      if (issues.value.length === 0) return
      if (focusedIndex.value > 0) {
        focusedIndex.value--
      } else {
        focusedIndex.value = issues.value.length - 1 // wrap to bottom
      }
      scrollFocusedIntoView()
      return
    }

    // Enter — open focused issue
    if (e.key === 'Enter' && focusedIndex.value >= 0) {
      e.preventDefault()
      const issue = issues.value[focusedIndex.value]
      if (issue) {
        if (previewMode.value === 'sidebar') {
          openPreview(issue, focusedIndex.value)
        } else {
          router.push(`/issues/${issue.issueKey}`)
        }
      }
      return
    }

    // Space — select/deselect focused issue
    if (e.key === ' ' && focusedIndex.value >= 0 && canBatchOps.value) {
      e.preventDefault()
      const issue = issues.value[focusedIndex.value]
      if (issue) {
        toggle(issue.id)
      }
      return
    }

    // Ctrl+A — select all
    if ((e.ctrlKey || e.metaKey) && (e.key === 'a' || e.key === 'A') && canBatchOps.value) {
      e.preventDefault()
      toggleAll()
      return
    }
  }

  return {
    focusedIndex,
    focusedIssueId,
    scrollFocusedIntoView,
    handleKeyboardNav,
    navigateIssue
  }
}
