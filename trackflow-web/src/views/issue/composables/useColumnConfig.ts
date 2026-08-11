import { ref, computed, watch, type MaybeRefOrGetter, toValue } from 'vue'
import { customFieldApi } from '@/api'
import type { AvailableColumnVO } from '@/api/types'

/**
 * 列定义（前端使用）
 */
export interface ColumnDef {
  key: string
  label: string
  group: 'standard' | 'custom'
  fieldFormat?: string
  sortable: boolean
  fixed?: boolean  // checkbox/issueKey/title 固定不可移除
}

/** 固定列（始终存在） */
const FIXED_KEYS = ['checkbox', 'issueKey', 'title']

/** 默认显示的列 */
const DEFAULT_VISIBLE_KEYS = [
  'checkbox', 'issueKey', 'issueType', 'title', 'project', 'assignee', 'status', 'sprint', 'priority', 'dueDate', 'updatedAt'
]

const STORAGE_KEY = 'trackflow:issue-list-columns'

/**
 * 动态列配置 composable
 * 从后端 API 加载可用列（固定属性 + 自定义字段），支持保存到 savedQuery 或 localStorage
 */
export function useColumnConfig(projectId?: MaybeRefOrGetter<string | undefined>) {
  const allColumns = ref<ColumnDef[]>([])
  const visibleKeys = ref<string[]>(loadFromStorage())
  const columnsLoading = ref(false)

  /**
   * 从后端加载可用列
   */
  async function fetchAvailableColumns() {
    columnsLoading.value = true
    try {
      const res = await customFieldApi.availableColumns(toValue(projectId) || undefined)
      const apiColumns: AvailableColumnVO[] = res.data || []

      const defs: ColumnDef[] = [
        { key: 'checkbox', label: '', group: 'standard', sortable: false, fixed: true }
      ]
      for (const col of apiColumns) {
        defs.push({
          key: col.key,
          label: col.label,
          group: col.group,
          fieldFormat: col.fieldFormat,
          sortable: col.sortable,
          fixed: !col.removable
        })
      }
      allColumns.value = defs
    } catch {
      allColumns.value = []
    } finally {
      columnsLoading.value = false
    }
  }

  /**
   * 当前可见的列定义
   */
  const visibleColumns = computed(() => {
    return visibleKeys.value
      .map(key => allColumns.value.find(c => c.key === key))
      .filter((c): c is ColumnDef => c !== undefined)
  })

  /**
   * 可选列（排除 fixed）
   */
  const configurableColumns = computed(() => {
    return allColumns.value.filter(c => !c.fixed)
  })

  /**
   * 标准列分组
   */
  const standardColumns = computed(() => {
    return configurableColumns.value.filter(c => c.group === 'standard')
  })

  /**
   * 自定义字段列分组
   */
  const customFieldColumns = computed(() => {
    return configurableColumns.value.filter(c => c.group === 'custom')
  })

  function isVisible(key: string): boolean {
    return visibleKeys.value.includes(key)
  }

  function toggleColumn(key: string) {
    const col = allColumns.value.find(c => c.key === key)
    if (col?.fixed) return

    const idx = visibleKeys.value.indexOf(key)
    if (idx >= 0) {
      visibleKeys.value = visibleKeys.value.filter((_, i) => i !== idx)
    } else {
      visibleKeys.value = [...visibleKeys.value, key]
    }
  }

  function resetToDefault() {
    visibleKeys.value = [...DEFAULT_VISIBLE_KEYS]
  }

  /**
   * 拖拽调整列顺序：将 fromKey 移动到 toKey 的位置
   */
  function reorderColumn(fromKey: string, toKey: string) {
    const keys = [...visibleKeys.value]
    const fromIdx = keys.indexOf(fromKey)
    const toIdx = keys.indexOf(toKey)
    if (fromIdx < 0 || toIdx < 0 || fromIdx === toIdx) return
    keys.splice(fromIdx, 1)
    keys.splice(toIdx, 0, fromKey)
    visibleKeys.value = keys
  }

  /**
   * 从 savedQuery 的 columns 数组设置可见列
   */
  function setFromQuery(columns: string[] | null) {
    if (columns && columns.length > 0) {
      // 确保 fixed 列存在
      const merged = [...new Set([...FIXED_KEYS, ...columns])]
      visibleKeys.value = merged
    } else {
      visibleKeys.value = [...DEFAULT_VISIBLE_KEYS]
    }
  }

  /**
   * 获取当前列选择（用于保存到 savedQuery）
   */
  function getColumnsForSave(): string[] {
    return visibleKeys.value.filter(k => !FIXED_KEYS.includes(k))
  }

  // 保存到 localStorage（作为 fallback）
  watch(visibleKeys, (keys) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(keys))
  }, { deep: true })

  // projectId 变化时重新加载；初始化时也加载一次
  if (projectId !== undefined) {
    watch(() => toValue(projectId), () => {
      fetchAvailableColumns()
    }, { immediate: true })
  } else {
    fetchAvailableColumns()
  }

  return {
    allColumns,
    visibleKeys,
    visibleColumns,
    configurableColumns,
    standardColumns,
    customFieldColumns,
    columnsLoading,
    isVisible,
    toggleColumn,
    reorderColumn,
    resetToDefault,
    setFromQuery,
    getColumnsForSave,
    fetchAvailableColumns
  }
}

function loadFromStorage(): string[] {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const keys = JSON.parse(stored) as string[]
      const merged = [...new Set([...FIXED_KEYS, ...keys])]
      return merged
    }
  } catch { /* JSON 解析容错，降级为默认列配置 */ }
  return [...DEFAULT_VISIBLE_KEYS]
}
