import { onMounted, onUnmounted } from 'vue'
import type { Ref } from 'vue'

interface UndoEntry {
  issueId: string
  issueKey: string
  oldStatusId: string
  newStatusId: string
  oldStatusName: string
  newStatusName: string
  timestamp: number
}

/**
 * 看板键盘快捷键处理。
 *
 * - Ctrl+Z：撤销最近的状态变更
 * - Escape：关闭预览面板 → 清空选择
 */
export function useBoardKeyboard(options: {
  undoStack: Ref<UndoEntry[]>
  undoTimeout: number
  undoTransition: (entry: UndoEntry) => void
  previewVisible: Ref<boolean>
  closePreview: () => void
  selectedCount: Ref<number>
  clearSelection: () => void
}) {
  const { undoStack, undoTimeout, undoTransition, previewVisible, closePreview, selectedCount, clearSelection } = options

  function handleKeydown(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
      const now = Date.now()
      const validEntries = undoStack.value.filter(entry => now - entry.timestamp < undoTimeout)
      if (validEntries.length > 0) {
        e.preventDefault()
        const lastEntry = validEntries[validEntries.length - 1]
        undoTransition(lastEntry)
      }
    }
    // Escape: 优先关闭预览面板 → 然后清空选择
    if (e.key === 'Escape') {
      if (previewVisible.value) {
        closePreview()
      } else if (selectedCount.value > 0) {
        clearSelection()
      }
    }
  }

  onMounted(() => {
    document.addEventListener('keydown', handleKeydown)
  })
  onUnmounted(() => {
    document.removeEventListener('keydown', handleKeydown)
  })

  return { handleKeydown }
}
