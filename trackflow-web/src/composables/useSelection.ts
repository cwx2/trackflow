import { ref, computed, watch, onMounted, onUnmounted, type Ref } from 'vue'

/** 最小化的工单接口 — 只要有 id 就能参与选择 */
interface Selectable {
  id: string
}

export function useSelection<T extends Selectable>(issues: Ref<T[]>) {
  const selectedIds = ref<Set<string>>(new Set())
  /** 记录最后一次点击选中的 issue id，用于 Shift+点击范围选择 */
  const lastSelectedId = ref<string | null>(null)
  /** 跟踪 Shift 键是否按下 */
  const shiftHeld = ref(false)

  function onKeyDown(e: KeyboardEvent) { if (e.key === 'Shift') shiftHeld.value = true }
  function onKeyUp(e: KeyboardEvent) { if (e.key === 'Shift') shiftHeld.value = false }

  onMounted(() => {
    document.addEventListener('keydown', onKeyDown)
    document.addEventListener('keyup', onKeyUp)
  })
  onUnmounted(() => {
    document.removeEventListener('keydown', onKeyDown)
    document.removeEventListener('keyup', onKeyUp)
  })

  const isAllSelected = computed(() =>
    issues.value.length > 0 && issues.value.every(i => selectedIds.value.has(i.id))
  )

  const isIndeterminate = computed(() =>
    selectedIds.value.size > 0 && !isAllSelected.value
  )

  const selectedCount = computed(() => selectedIds.value.size)

  const selectedIssues = computed(() =>
    issues.value.filter(i => selectedIds.value.has(i.id))
  )

  /**
   * 切换单行选中状态。
   * 如果 Shift 键按下且有锚点，执行范围选择。
   */
  function toggle(id: string) {
    if (shiftHeld.value && lastSelectedId.value && lastSelectedId.value !== id) {
      shiftSelect(id)
      return
    }

    const newSet = new Set(selectedIds.value)
    if (newSet.has(id)) {
      newSet.delete(id)
    } else {
      newSet.add(id)
    }
    selectedIds.value = newSet
    lastSelectedId.value = id
  }

  /**
   * Shift+点击范围选择：从 lastSelectedId 到目标 id 之间的所有工单全部选中
   */
  function shiftSelect(id: string) {
    if (!lastSelectedId.value) {
      toggle(id)
      return
    }

    const list = issues.value
    const anchorIndex = list.findIndex(i => i.id === lastSelectedId.value)
    const targetIndex = list.findIndex(i => i.id === id)

    if (anchorIndex === -1 || targetIndex === -1) {
      toggle(id)
      return
    }

    const start = Math.min(anchorIndex, targetIndex)
    const end = Math.max(anchorIndex, targetIndex)

    const newSet = new Set(selectedIds.value)
    for (let idx = start; idx <= end; idx++) {
      newSet.add(list[idx].id)
    }
    selectedIds.value = newSet
    // 不更新 lastSelectedId — 保留锚点以便连续 Shift+点击扩展范围
  }

  /**
   * 全选/取消全选
   */
  function toggleAll() {
    if (isAllSelected.value) {
      selectedIds.value = new Set()
    } else {
      selectedIds.value = new Set(issues.value.map(i => i.id))
    }
    lastSelectedId.value = null
  }

  /**
   * 清空选择
   */
  function clearSelection() {
    selectedIds.value = new Set()
    lastSelectedId.value = null
  }

  // issues 变化时（翻页、排序、筛选）清空选中
  watch(issues, () => {
    clearSelection()
  })

  return {
    selectedIds,
    isAllSelected,
    isIndeterminate,
    selectedCount,
    selectedIssues,
    toggle,
    shiftSelect,
    toggleAll,
    clearSelection
  }
}
