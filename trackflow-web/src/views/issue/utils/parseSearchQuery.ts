/**
 * parseSearchQuery — 解析 QueryInput 中的结构化搜索文本为 FilterCondition[]
 *
 * QueryInput 使用中文字段名格式（queryKey），例如：
 *   "状态: 未关闭 负责人: 我 规则引擎"
 *
 * 解析规则：
 * - 识别已知的"字段名:"模式，提取字段值（支持逗号分隔多值）
 * - 将用户可见的标签（如"未关闭"、"进行中"）翻译为后端期望的内部代码
 * - 剩余不属于任何字段的自由文本作为关键词搜索条件
 * - 返回 FilterCondition[] 供 QueryExecutor 使用
 *
 * 这是修复 REQ-808 的核心：确保搜索栏中输入的结构化查询与列表结果一致。
 * 之前整个文本被当作 keyword 发送到后端，导致匹配失败。
 */

import type { FilterCondition } from '@/components/base'

/**
 * 已知的查询字段映射表：queryKey（用户可见）→ filterField（QueryExecutor 使用的 field 名）
 * 注意：保持与 QueryInput.vue 中 getBuiltinFields() 的 queryKey/key 一致
 */
const FIELD_MAP: Record<string, string> = {
  '状态': 'status',
  '优先级': 'priority',
  '负责人': 'assignee',
  '报告人': 'reporter',
  '类型': 'type',
  'Sprint': 'sprint',
  '项目': 'project',
  '截止日期': 'dueDate',
  '创建日期': 'createdAt',
  '更新日期': 'updatedAt',
  '解决日期': 'resolvedAt',
  '关键词': 'keyword',
}

/** 所有已知的 queryKey 列表（按长度降序排列，优先匹配长的） */
const QUERY_KEYS = Object.keys(FIELD_MAP).sort((a, b) => b.length - a.length)

export interface ParsedQuery {
  /** 解析出的结构化筛选条件 */
  filters: FilterCondition[]
  /** 是否包含结构化字段（即是否为结构化查询） */
  hasStructuredFields: boolean
}

/**
 * 字段值上下文：提供标签→内部值的映射
 * 由调用方（FilterBar）根据当前 statusList/projectList 等动态构建
 */
export interface FieldValueContext {
  /** 状态选项：label（显示名/英文名）→ code（后端 QueryExecutor 期望的值） */
  statusOptions?: Array<{ label: string; code: string; displayName?: string }>
  /** 优先级选项：label → value */
  priorityOptions?: Array<{ label: string; value: string }>
  /** 当前用户 ID（用于解析 "我" → userId） */
  currentUserId?: string
}

/**
 * 解析搜索查询文本
 *
 * @param text 用户在 QueryInput 中输入的原始文本
 * @param context 字段值上下文，提供标签到内部值的映射
 * @returns 解析结果，包含 FilterCondition[] 和是否为结构化查询的标志
 */
export function parseSearchQuery(text: string, context?: FieldValueContext): ParsedQuery {
  if (!text || !text.trim()) {
    return { filters: [], hasStructuredFields: false }
  }

  const trimmed = text.trim()

  // 构建正则匹配所有已知字段的 "fieldName:" 或 "fieldName：" 模式
  interface FieldMatch {
    queryKey: string
    filterField: string
    startIndex: number  // field name 起始位置
    colonEndIndex: number  // 冒号（含后续空格）结束位置
  }

  const matches: FieldMatch[] = []

  for (const qk of QUERY_KEYS) {
    // 匹配 "字段名:" 或 "字段名：" 后面可能有一个空格
    const pattern = new RegExp(`${escapeRegex(qk)}[：:]\\s*`, 'g')
    let m: RegExpExecArray | null
    while ((m = pattern.exec(trimmed)) !== null) {
      matches.push({
        queryKey: qk,
        filterField: FIELD_MAP[qk],
        startIndex: m.index,
        colonEndIndex: m.index + m[0].length,
      })
    }
  }

  // 如果没有任何结构化字段匹配，则整个文本作为关键词
  if (matches.length === 0) {
    return {
      filters: [{ field: 'keyword', operator: 'contains', value: [trimmed] }],
      hasStructuredFields: false,
    }
  }

  // 按位置排序
  matches.sort((a, b) => a.startIndex - b.startIndex)

  const filters: FilterCondition[] = []
  const freeTextParts: string[] = []

  // 提取每个字段的值（从冒号结束位置到下一个字段起始位置或文本结尾）
  for (let i = 0; i < matches.length; i++) {
    const current = matches[i]
    const nextStart = i + 1 < matches.length ? matches[i + 1].startIndex : trimmed.length
    const rawValue = trimmed.substring(current.colonEndIndex, nextStart).trim()

    if (rawValue) {
      // 解析值：支持逗号分隔的多值
      const values = rawValue.split(',').map(v => v.trim()).filter(v => v.length > 0)
      if (values.length > 0) {
        // 将用户可见标签翻译为后端期望的内部值
        const resolved = resolveFieldValues(current.filterField, values, context)
        filters.push(resolved)
      }
    }
  }

  // 提取第一个字段之前的自由文本
  if (matches[0].startIndex > 0) {
    const prefix = trimmed.substring(0, matches[0].startIndex).trim()
    if (prefix) {
      freeTextParts.push(prefix)
    }
  }

  // 将自由文本作为 keyword 条件
  if (freeTextParts.length > 0) {
    const keywordText = freeTextParts.join(' ').trim()
    if (keywordText) {
      // 检查是否已有 keyword filter，如有则合并
      const existingKw = filters.find(f => f.field === 'keyword')
      if (existingKw && existingKw.value) {
        existingKw.value = [keywordText + ' ' + existingKw.value.join(' ')]
      } else {
        filters.push({ field: 'keyword', operator: 'contains', value: [keywordText] })
      }
    }
  }

  return { filters, hasStructuredFields: true }
}

/**
 * 将用户输入的标签值翻译为后端 QueryExecutor 期望的内部值
 *
 * 特殊处理：
 * - 状态字段："未关闭"→operator:open, "已关闭"→operator:closed, 其他→映射为status code
 * - 负责人字段："我"→使用 currentUserId
 */
function resolveFieldValues(
  field: string,
  values: string[],
  context?: FieldValueContext
): FilterCondition {
  switch (field) {
    case 'status':
      return resolveStatusValues(values, context)
    case 'assignee':
    case 'reporter':
      return resolveUserValues(field, values, context)
    default:
      return { field, operator: field === 'keyword' ? 'contains' : 'in', value: values }
  }
}

/**
 * 解析状态字段值：
 * - "未关闭" → operator "open"（后端 applyStatusFilter case "open"）
 * - "已关闭" → operator "closed"（后端 applyStatusFilter case "closed"）
 * - 具体状态名（如"进行中"、"In Progress"）→ 映射为 status code，使用 operator "in"
 */
function resolveStatusValues(values: string[], context?: FieldValueContext): FilterCondition {
  // 检查是否为特殊虚拟状态（单值时）
  if (values.length === 1) {
    const v = values[0]
    if (v === '未关闭' || v.toLowerCase() === 'open' || v === '__open__') {
      return { field: 'status', operator: 'open', value: [] }
    }
    if (v === '已关闭' || v.toLowerCase() === 'closed' || v === '__closed__') {
      return { field: 'status', operator: 'closed', value: [] }
    }
  }

  // 多值或具体状态名：映射为 status code
  const codes: string[] = []
  for (const v of values) {
    // 先检查特殊值
    if (v === '未关闭' || v.toLowerCase() === 'open' || v === '__open__') {
      // 多值中混入了虚拟状态 — 不应发生，但做兼容处理
      // 返回 open operator（忽略其他值）
      return { field: 'status', operator: 'open', value: [] }
    }
    if (v === '已关闭' || v.toLowerCase() === 'closed' || v === '__closed__') {
      return { field: 'status', operator: 'closed', value: [] }
    }

    const code = mapStatusLabelToCode(v, context)
    if (code) {
      codes.push(code)
    } else {
      // 如果映射不到 code，尝试直接作为 code 使用（用户可能直接输入了 code）
      codes.push(v)
    }
  }

  return { field: 'status', operator: 'in', value: codes }
}

/**
 * 将状态显示标签映射到后端 status code
 * 查找顺序：displayName → name（不区分大小写）→ code（精确）
 */
function mapStatusLabelToCode(label: string, context?: FieldValueContext): string | null {
  if (!context?.statusOptions) return null

  const lower = label.toLowerCase()
  for (const opt of context.statusOptions) {
    // 匹配 displayName（中文名）
    if (opt.displayName && opt.displayName === label) return opt.code
    if (opt.displayName && opt.displayName.toLowerCase() === lower) return opt.code
    // 匹配 label（通常是 name 或 displayName）
    if (opt.label === label) return opt.code
    if (opt.label.toLowerCase() === lower) return opt.code
    // 匹配 code 本身（用户直接输入 code 的情况）
    if (opt.code === label || opt.code === lower) return opt.code
  }
  return null
}

/**
 * 解析用户字段值："我" → currentUserId
 */
function resolveUserValues(field: string, values: string[], context?: FieldValueContext): FilterCondition {
  const resolved = values.map(v => {
    if ((v === '我' || v.toLowerCase() === 'me') && context?.currentUserId) {
      return context.currentUserId
    }
    return v
  })
  return { field, operator: 'in', value: resolved }
}

function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
