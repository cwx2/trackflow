/**
 * parseSearchQuery — 解析 QueryInput 中的结构化搜索文本为 FilterCondition[]
 *
 * QueryInput 使用中文字段名格式（queryKey），例如：
 *   "状态: 未关闭 负责人: 我 规则引擎"
 *
 * 解析规则：
 * - 识别已知的"字段名:"模式，提取字段值（支持逗号分隔多值）
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
 * 解析搜索查询文本
 *
 * @param text 用户在 QueryInput 中输入的原始文本
 * @returns 解析结果，包含 FilterCondition[] 和是否为结构化查询的标志
 */
export function parseSearchQuery(text: string): ParsedQuery {
  if (!text || !text.trim()) {
    return { filters: [], hasStructuredFields: false }
  }

  const trimmed = text.trim()

  // 构建正则匹配所有已知字段的 "fieldName:" 或 "fieldName：" 模式
  // 使用贪婪匹配：找到所有字段位置，然后提取各字段的值
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
        filters.push({
          field: current.filterField,
          operator: current.filterField === 'keyword' ? 'contains' : 'in',
          value: values,
        })
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

function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
