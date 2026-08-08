/**
 * Issue 颜色常量管理
 *
 * 集中管理优先级、工单类型的默认颜色映射，供 fallback 使用。
 * 实际运行时颜色从后端自定义字段系统动态加载，此处仅为初始默认值。
 *
 * @module issueColors
 */

// ─── 优先级默认颜色 ──────────────────────────────────────────────

export interface ColorOption {
  value: string
  label: string
  color: string
}

/** 优先级默认选项（API 加载前的初始值） */
export const DEFAULT_PRIORITY_OPTIONS: ColorOption[] = [
  { value: '阻塞', label: '阻塞', color: '#b91c1c' },
  { value: '紧急', label: '紧急', color: '#ef4444' },
  { value: '高', label: '高', color: '#f59e0b' },
  { value: '普通', label: '普通', color: '#6366f1' },
  { value: '低', label: '低', color: '#64748b' }
]

/** 优先级颜色映射（快速查找用） */
export const PRIORITY_COLORS: Record<string, string> = {
  '阻塞': '#b91c1c',
  '紧急': '#ef4444',
  '高': '#f59e0b',
  '普通': '#6366f1',
  '低': '#64748b',
  'Show-stopper': '#b91c1c',
  'Critical': '#ef4444',
  'High': '#f59e0b',
  'Medium': '#6366f1',
  'Normal': '#6366f1',
  'Low': '#64748b'
}

/** 默认优先级颜色（找不到匹配时） */
export const DEFAULT_PRIORITY_COLOR = '#6366f1'

// ─── 工单类型默认颜色 ────────────────────────────────────────────

/** 工单类型默认选项（API 加载前的初始值） */
export const DEFAULT_ISSUE_TYPE_OPTIONS: ColorOption[] = [
  { value: '缺陷', label: '缺陷', color: '#ef4444' },
  { value: '任务', label: '任务', color: '#6366f1' },
  { value: '需求', label: '需求', color: '#22c55e' },
  { value: '史诗', label: '史诗', color: '#a855f7' },
  { value: '故事', label: '故事', color: '#3b82f6' }
]

/** 工单类型颜色映射（快速查找用） */
export const ISSUE_TYPE_COLORS: Record<string, string> = {
  '缺陷': '#ef4444',
  '任务': '#6366f1',
  '需求': '#22c55e',
  '史诗': '#a855f7',
  '故事': '#3b82f6',
  'Bug': '#ef4444',
  'Task': '#6366f1',
  'Feature': '#22c55e',
  'Epic': '#a855f7',
  'Story': '#3b82f6'
}

/** 默认工单类型颜色（找不到匹配时） */
export const DEFAULT_ISSUE_TYPE_COLOR = '#6366f1'
