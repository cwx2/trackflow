import { ref, computed, watch } from 'vue'
import { issueApi } from '@/api'
import { DEFAULT_BADGE_COLOR } from '@/utils/issueColors'

/**
 * 优先级选项管理 - 从后端自定义字段系统动态加载优先级选项
 *
 * 颜色完全来自后端 API（自定义字段 option.color），不再使用硬编码回退色。
 * 当 API 不可用或颜色未配置时，返回 null（UI 层决定是否显示灰色或不显示）。
 */

export interface PriorityOption {
  value: string
  label: string
  color: string | null
  description: string | null
  isDefault: boolean
}

/** 全局缓存：projectId → options（避免同一页面多组件重复请求） */
const cache = new Map<string, { options: PriorityOption[]; timestamp: number }>()
const CACHE_TTL = 5 * 60 * 1000 // 5 分钟缓存

/** 中文标签映射（回退用，兼容历史英文值） */
const FALLBACK_LABELS: Record<string, string> = {
  'Show-stopper': '阻塞',
  'Critical': '紧急',
  'High': '高',
  'Medium': '普通',
  'Normal': '普通',
  'Low': '低',
}

/** 默认优先级颜色回退值（仅在缓存中无颜色且确实需要显示时使用） */
export const DEFAULT_PRIORITY_COLOR = DEFAULT_BADGE_COLOR

/** 默认优先级选项（仅在 API 请求进行中作为 loading 占位，不作为最终展示值） */
export const DEFAULT_PRIORITY_OPTIONS: PriorityOption[] = [
  { value: '阻塞', label: '阻塞', color: null, description: null, isDefault: false },
  { value: '紧急', label: '紧急', color: null, description: null, isDefault: false },
  { value: '高', label: '高', color: null, description: null, isDefault: false },
  { value: '普通', label: '普通', color: null, description: null, isDefault: true },
  { value: '低', label: '低', color: null, description: null, isDefault: false },
]

/**
 * 加载指定项目的优先级选项（projectId 为空时加载全局选项）
 */
export async function loadPriorityOptions(projectId?: string): Promise<PriorityOption[]> {
  const cacheKey = projectId || '__global__'
  // 检查缓存
  const cached = cache.get(cacheKey)
  if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
    return cached.options
  }

  try {
    const res = await issueApi.getPriorityOptions(projectId)
    if (res.code === 0 && res.data) {
      const options: PriorityOption[] = res.data.map(opt => ({
        value: opt.value,
        label: FALLBACK_LABELS[opt.value] || opt.value,
        color: opt.color,
        description: opt.description,
        isDefault: opt.isDefault,
      }))
      cache.set(cacheKey, { options, timestamp: Date.now() })
      return options
    }
  } catch (e) {
    console.warn('[usePriorityOptions] 加载优先级选项失败，使用默认值', e)
  }

  return DEFAULT_PRIORITY_OPTIONS
}

/**
 * 清除优先级选项缓存（项目设置变更后调用）
 */
export function clearPriorityOptionsCache(projectId?: string) {
  if (projectId) {
    cache.delete(projectId)
  } else {
    cache.clear()
  }
}

/**
 * 根据优先级值获取颜色（从缓存获取，无缓存时返回 DEFAULT_PRIORITY_COLOR）
 *
 * 注意：大多数场景应优先使用 API 返回的 issue.priorityColor 字段，
 * 本函数仅用于报表图表等无法直接获取 issue 对象颜色字段的场景。
 */
export function getPriorityColor(priority: string | null | undefined, projectId?: string): string {
  if (!priority) return DEFAULT_PRIORITY_COLOR

  // 尝试从缓存取
  if (projectId) {
    const cached = cache.get(projectId)
    if (cached) {
      const opt = cached.options.find(o => o.value === priority)
      if (opt?.color) return opt.color
    }
  }

  // 无缓存时遍历所有已缓存的项目查找
  for (const [, entry] of cache) {
    const opt = entry.options.find(o => o.value === priority)
    if (opt?.color) return opt.color
  }

  return DEFAULT_PRIORITY_COLOR
}

/**
 * 组合式函数：响应式的优先级选项
 */
export function usePriorityOptions(projectIdRef: { value: string | null | undefined }) {
  const options = ref<PriorityOption[]>(DEFAULT_PRIORITY_OPTIONS)
  const loading = ref(false)

  async function refresh() {
    const projectId = projectIdRef.value
    loading.value = true
    try {
      // projectId 为空时加载全局选项（而非使用硬编码静态列表）
      options.value = await loadPriorityOptions(projectId || undefined)
    } finally {
      loading.value = false
    }
  }

  // 项目切换时重新加载
  watch(() => projectIdRef.value, () => {
    refresh()
  }, { immediate: true })

  const selectOptions = computed(() =>
    options.value.map(o => ({ value: o.value, label: o.label, color: o.color }))
  )

  return {
    options,
    selectOptions,
    loading,
    refresh,
  }
}
