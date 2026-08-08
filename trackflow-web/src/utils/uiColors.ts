/**
 * UI 颜色常量管理
 *
 * 集中管理前端 UI 组件使用的颜色常量（非 ECharts 图表）。
 * 主题相关颜色应通过 CSS 变量获取，此处定义的是：
 * 1. 不随主题变化的固定色板（如项目颜色池、头像色板）
 * 2. 动态数据的默认/回退颜色
 * 3. 需要在 JS 中使用的颜色常量
 *
 * @module uiColors
 */

// ─── 项目颜色池（用于项目卡片/头像） ──────────────────────────────

/**
 * 项目颜色池 - 用于根据项目 ID 分配稳定颜色
 * Material Design 色系，保证辨识度
 */
export const PROJECT_COLOR_POOL = [
  '#e91e63', '#9c27b0', '#673ab7', '#3f51b5', '#2196f3',
  '#00bcd4', '#009688', '#4caf50', '#ff9800', '#ff5722',
  '#795548', '#607d8b'
] as const

/**
 * 根据项目 ID 获取稳定的颜色
 */
export function getProjectColor(projectId: string | number): string {
  const id = typeof projectId === 'string' ? parseInt(projectId, 10) || 0 : projectId
  return PROJECT_COLOR_POOL[id % PROJECT_COLOR_POOL.length]
}

// ─── 用户头像颜色池 ──────────────────────────────────────────────

/**
 * 用户头像背景色板
 * 用于无头像时根据用户名生成稳定的背景颜色
 */
export const AVATAR_COLOR_PALETTE = [
  '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#84cc16', '#f97316', '#ec4899', '#6366f1'
] as const

/**
 * 根据名称生成稳定的头像背景颜色
 */
export function getAvatarColor(name: string): string {
  if (!name) return AVATAR_COLOR_PALETTE[0]
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return AVATAR_COLOR_PALETTE[Math.abs(hash) % AVATAR_COLOR_PALETTE.length]
}

// ─── 自定义字段预设颜色 ──────────────────────────────────────────

/**
 * 自定义字段选项的预设颜色（14 种 Material Design 色）
 * 供字段配置界面选色使用
 */
export const FIELD_PRESET_COLORS = [
  '#4CAF50', '#2196F3', '#9C27B0', '#FF9800',
  '#F44336', '#00BCD4', '#607D8B', '#E91E63',
  '#8BC34A', '#3F51B5', '#FF5722', '#009688',
  '#795548', '#FFC107'
] as const

// ─── 默认/回退颜色 ──────────────────────────────────────────────

/** 通用灰色回退（用于缺少颜色数据时的默认值） */
export const DEFAULT_GRAY = '#6b7280'

/** 未定义状态默认颜色 */
export const DEFAULT_STATUS_COLOR = '#6b7280'

/** 未定义标签默认颜色 */
export const DEFAULT_TAG_COLOR = '#6b7280'

/** 默认 badge 颜色（accent 蓝） */
export const DEFAULT_BADGE_COLOR = '#3b82f6'

/** 工作流节点默认颜色（indigo） */
export const DEFAULT_NODE_COLOR = '#6366f1'

/** 字段值缺省颜色 */
export const DEFAULT_FIELD_VALUE_COLOR = '#6e7681'

// ─── 语义色 JS 常量（用于 JS 逻辑中无法使用 CSS var 的场景） ─────────

/**
 * 从 CSS 变量动态读取语义色
 * 在 JS 逻辑中需要颜色值时使用此函数，确保跟随主题
 */
export function getSemanticColor(name: 'success' | 'danger' | 'warning' | 'accent' | 'purple'): string {
  const varName = `--tf-${name}`
  const value = getComputedStyle(document.documentElement).getPropertyValue(varName).trim()
  const fallbacks: Record<string, string> = {
    success: '#3fb950',
    danger: '#f85149',
    warning: '#d29922',
    accent: '#58a6ff',
    purple: '#a371f7'
  }
  return value || fallbacks[name]
}
