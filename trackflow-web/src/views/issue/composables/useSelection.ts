import { ref, computed, watch, type Ref } from 'vue'
import type { IssueVO } from '@/api/types'

export function useSelection(issues: Ref<IssueVO[]>) {
  const selectedIds = ref<Set<string>>(new Set())

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
   * 切换单行选中状态
   */
  function toggle(id: string) {
    const newSet = new Set(selectedIds.value)
    if (newSet.has(id)) {
      newSet.delete(id)
    } else {
      newSet.add(id)
    }
    selectedIds.value = newSet
  }

  /**
   * 全选/取消全选
   */
  function toggleAll() {
    if (isAllSelected.value) {
      // 已全选 → 取消全选
      selectedIds.value = new Set()
    } else {
      // 未全选 → 全选当前页
      selectedIds.value = new Set(issues.value.map(i => i.id))
    }
  }

  /**
   * 清空选择
   */
  function clearSelection() {
    selectedIds.value = new Set()
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
    toggleAll,
    clearSelection
  }
}
