import { ref, computed, watch } from 'vue'
import { issueApi } from '@/api'

/**
 * 优先级选项管理 - 从后端自定义字段系统动态加载优先级选项
 *
 * 替代原有硬编码的 priorityOptions 数组，支持：
 * - 项目级别的独立选项集
 * - 动态颜色配置
 * - 值的增删改（通过项目设置→自定义字段管理）
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
  '阻塞': '阻塞',
  '紧急': '紧急',
  '高': '高',
  '普通': '普通',
  '低': '低',
}

/** 回退颜色映射（API 不可用时） */
const FALLBACK_COLORS: Record<string, string> = {
  '阻塞': '#b91c1c',
  '紧急': '#ef4444',
  '高': '#f59e0b',
  '普通': '#6366f1',
  '低': '#64748b',
  // 兼容历史英文值
  'Show-stopper': '#b91c1c',
  'Critical': '#ef4444',
  'High': '#f59e0b',
  'Medium': '#6366f1',
  'Normal': '#6366f1',
  'Low': '#64748b',
}

/** 默认优先级选项（API 不可用时的回退） */
const FALLBACK_OPTIONS: PriorityOption[] = [
  { value: '阻塞', label: '阻塞', color: '#b91c1c', description: '阻塞性问题，必须立即解决', isDefault: false },
  { value: '紧急', label: '紧急', color: '#ef4444', description: '严重问题，影响核心功能', isDefault: false },
  { value: '高', label: '高', color: '#f59e0b', description: '高优先级，需要尽快处理', isDefault: false },
  { value: '普通', label: '普通', color: '#6366f1', description: '普通优先级，按计划处理', isDefault: true },
  { value: '低', label: '低', color: '#64748b', description: '低优先级，有空再处理', isDefault: false },
]

/**
 * 加载指定项目的优先级选项
 */
export async function loadPriorityOptions(projectId: string): Promise<PriorityOption[]> {
  // 检查缓存
  const cached = cache.get(projectId)
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
      cache.set(projectId, { options, timestamp: Date.now() })
      return options
    }
  } catch (e) {
    console.warn('[usePriorityOptions] 加载优先级选项失败，使用默认值', e)
  }

  return FALLBACK_OPTIONS
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
 * 根据优先级值获取颜色（从缓存或回退值）
 */
export function getPriorityColor(priority: string | null | undefined, projectId?: string): string {
  if (!priority) return FALLBACK_COLORS['Normal']

  // 尝试从缓存取
  if (projectId) {
    const cached = cache.get(projectId)
    if (cached) {
      const opt = cached.options.find(o => o.value === priority)
      if (opt?.color) return opt.color
    }
  }

  return FALLBACK_COLORS[priority] || FALLBACK_COLORS['Normal']
}

/**
 * 组合式函数：响应式的优先级选项
 */
export function usePriorityOptions(projectIdRef: { value: string | null | undefined }) {
  const options = ref<PriorityOption[]>(FALLBACK_OPTIONS)
  const loading = ref(false)

  async function refresh() {
    const projectId = projectIdRef.value
    if (!projectId) {
      options.value = FALLBACK_OPTIONS
      return
    }
    loading.value = true
    try {
      options.value = await loadPriorityOptions(projectId)
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
