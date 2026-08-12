import { ref, computed, watch } from 'vue'
import { issueApi } from '@/api'
import { type MaybeRefOrGetter, toValue } from 'vue'
import { DEFAULT_BADGE_COLOR } from '@/utils/issueColors'

/**
 * 工单类型选项管理 - 从后端自定义字段系统动态加载工单类型选项
 *
 * 颜色完全来自后端 API（自定义字段 option.color），不再使用硬编码回退色。
 * 当 API 不可用或颜色未配置时，返回 null（UI 层决定是否显示灰色或不显示）。
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

/** 中文标签映射（回退用，兼容历史英文值） */
const FALLBACK_LABELS: Record<string, string> = {
  'Bug': '缺陷',
  'Task': '任务',
  'Feature': '需求',
  'Epic': '史诗',
  'Story': '故事',
}

/** 默认工单类型颜色回退值 */
export const DEFAULT_ISSUE_TYPE_COLOR = DEFAULT_BADGE_COLOR

/** 默认工单类型选项（API 不可用时的回退，value 使用中文，与数据库存储一致） */
export const DEFAULT_ISSUE_TYPE_OPTIONS: IssueTypeOption[] = [
  { value: '缺陷', label: '缺陷', color: null, description: '软件缺陷，需要修复', isDefault: false },
  { value: '任务', label: '任务', color: null, description: '常规任务', isDefault: true },
  { value: '需求', label: '需求', color: null, description: '新功能需求', isDefault: false },
  { value: '史诗', label: '史诗', color: null, description: '大型功能集合', isDefault: false },
  { value: '故事', label: '故事', color: null, description: '用户故事', isDefault: false },
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
 * 根据工单类型值获取颜色（从缓存获取，无缓存时返回 DEFAULT_ISSUE_TYPE_COLOR）
 *
 * 注意：大多数场景应优先使用 API 返回的 issue.issueTypeColor 字段，
 * 本函数仅用于报表图表等无法直接获取 issue 对象颜色字段的场景。
 */
export function getIssueTypeColor(issueType: string | null | undefined, projectId?: string): string {
  if (!issueType) return DEFAULT_ISSUE_TYPE_COLOR

  // 尝试从缓存取
  if (projectId) {
    const cached = cache.get(projectId)
    if (cached) {
      const opt = cached.options.find(o => o.value === issueType)
      if (opt?.color) return opt.color
    }
  }

  // 无缓存时遍历所有已缓存的项目查找
  for (const [, entry] of cache) {
    const opt = entry.options.find(o => o.value === issueType)
    if (opt?.color) return opt.color
  }

  return DEFAULT_ISSUE_TYPE_COLOR
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
