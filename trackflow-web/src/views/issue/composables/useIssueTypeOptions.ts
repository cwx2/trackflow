import { ref, computed, watch } from 'vue'
import { issueApi } from '@/api'
import { type MaybeRefOrGetter, toValue } from 'vue'

/**
 * 工单类型选项管理 - 从后端自定义字段系统动态加载工单类型选项
 *
 * 替代原有硬编码的 issueTypeLabelMap，支持：
 * - 项目级别的独立选项集
 * - 动态颜色配置
 * - 值的增删改（通过项目设置→自定义字段管理）
 */

export interface IssueTypeOption {
  value: string
  label: string
  color: string | null
  description: string | null
  isDefault: boolean
}

/** 全局缓存：projectId → options（避免同一页面多组件重复请求） */
const cache = new Map<string, { options: IssueTypeOption[]; timestamp: number }>()
const CACHE_TTL = 5 * 60 * 1000 // 5 分钟缓存

/** 中文标签映射（回退用，同时也用于本地化展示） */
const FALLBACK_LABELS: Record<string, string> = {
  'Bug': '缺陷',
  'Task': '任务',
  'Feature': '需求',
  'Epic': '史诗',
  'Story': '故事',
}

/** 回退颜色映射（API 不可用时） — 引用 issueColors 单一来源 */
import { ISSUE_TYPE_COLORS as FALLBACK_COLORS } from '@/utils/issueColors'

/** 默认工单类型颜色回退值 */
export const DEFAULT_ISSUE_TYPE_COLOR = FALLBACK_COLORS['Task'] || '#6366f1'

/** 默认工单类型选项（API 不可用时的回退，也作为初始值） */
export const DEFAULT_ISSUE_TYPE_OPTIONS: IssueTypeOption[] = [
  { value: 'Bug', label: '缺陷', color: FALLBACK_COLORS['Bug'], description: '软件缺陷，需要修复', isDefault: false },
  { value: 'Task', label: '任务', color: FALLBACK_COLORS['Task'], description: '常规任务', isDefault: true },
  { value: 'Feature', label: '需求', color: FALLBACK_COLORS['Feature'], description: '新功能需求', isDefault: false },
  { value: 'Epic', label: '史诗', color: FALLBACK_COLORS['Epic'], description: '大型功能集合', isDefault: false },
  { value: 'Story', label: '故事', color: FALLBACK_COLORS['Story'], description: '用户故事', isDefault: false },
]

/**
 * 加载指定项目的工单类型选项
 */
export async function loadIssueTypeOptions(projectId: string): Promise<IssueTypeOption[]> {
  // 检查缓存
  const cached = cache.get(projectId)
  if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
    return cached.options
  }

  try {
    const res = await issueApi.getIssueTypeOptions(projectId)
    if (res.code === 0 && res.data) {
      const options: IssueTypeOption[] = res.data.map(opt => ({
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
    console.warn('[useIssueTypeOptions] 加载工单类型选项失败，使用默认值', e)
  }

  return DEFAULT_ISSUE_TYPE_OPTIONS
}

/**
 * 清除工单类型选项缓存（项目设置变更后调用）
 */
export function clearIssueTypeOptionsCache(projectId?: string) {
  if (projectId) {
    cache.delete(projectId)
  } else {
    cache.clear()
  }
}

/**
 * 根据工单类型值获取颜色（从缓存或回退值）
 */
export function getIssueTypeColor(issueType: string | null | undefined, projectId?: string): string {
  if (!issueType) return FALLBACK_COLORS['Task']

  // 尝试从缓存取
  if (projectId) {
    const cached = cache.get(projectId)
    if (cached) {
      const opt = cached.options.find(o => o.value === issueType)
      if (opt?.color) return opt.color
    }
  }

  return FALLBACK_COLORS[issueType] || FALLBACK_COLORS['Task']
}

/**
 * 根据工单类型值获取本地化标签
 */
export function getIssueTypeLabel(issueType: string | null | undefined, projectId?: string): string {
  if (!issueType) return '未知'

  // 尝试从缓存取
  if (projectId) {
    const cached = cache.get(projectId)
    if (cached) {
      const opt = cached.options.find(o => o.value === issueType)
      if (opt) return opt.label
    }
  }

  return FALLBACK_LABELS[issueType] || issueType
}

/**
 * 组合式函数：响应式的工单类型选项
 *
 * 参数 projectId 支持三种形式（MaybeRefOrGetter）：
 * - 普通值：useIssueTypeOptions('proj-1')
 * - Ref：useIssueTypeOptions(projectIdRef)
 * - Getter：useIssueTypeOptions(() => props.projectId)
 */
export function useIssueTypeOptions(projectId: MaybeRefOrGetter<string | null | undefined>) {
  const options = ref<IssueTypeOption[]>(DEFAULT_ISSUE_TYPE_OPTIONS)
  const loading = ref(false)

  async function refresh() {
    const id = toValue(projectId)
    if (!id) {
      options.value = DEFAULT_ISSUE_TYPE_OPTIONS
      return
    }
    loading.value = true
    try {
      options.value = await loadIssueTypeOptions(id)
    } finally {
      loading.value = false
    }
  }

  // 项目切换时重新加载
  watch(() => toValue(projectId), () => {
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
