/**
 * ECharts 图表颜色管理
 *
 * 集中管理所有 ECharts 图表使用的颜色，适配暗色/亮色/护眼三种主题。
 * 通过 getComputedStyle 动态读取 CSS 变量，实现主题联动。
 *
 * @module chartColors
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'

// ─── 图表主题色（从 CSS 变量动态读取） ─────────────────────────────

export interface ChartThemeColors {
  textColor: string
  axisColor: string
  tooltipBg: string
  tooltipBorder: string
  tooltipText: string
  cardBorder: string
  bgColor: string
}

/** 暗色主题默认值（作为 CSS 变量读取失败时的 fallback） */
const DARK_DEFAULTS: ChartThemeColors = {
  textColor: '#9ca3af',
  axisColor: '#30363d',
  tooltipBg: '#22252a',
  tooltipBorder: '#30363d',
  tooltipText: '#e6edf3',
  cardBorder: '#2a2d33',
  bgColor: 'transparent'
}

/**
 * 从 CSS 变量读取当前主题的图表颜色
 */
export function readChartThemeColors(): ChartThemeColors {
  const style = getComputedStyle(document.documentElement)
  return {
    textColor: style.getPropertyValue('--tf-text-secondary').trim() || DARK_DEFAULTS.textColor,
    axisColor: style.getPropertyValue('--tf-border').trim() || DARK_DEFAULTS.axisColor,
    tooltipBg: style.getPropertyValue('--tf-bg-elevated').trim() || DARK_DEFAULTS.tooltipBg,
    tooltipBorder: style.getPropertyValue('--tf-border').trim() || DARK_DEFAULTS.tooltipBorder,
    tooltipText: style.getPropertyValue('--tf-text-primary').trim() || DARK_DEFAULTS.tooltipText,
    cardBorder: style.getPropertyValue('--tf-bg-elevated').trim() || DARK_DEFAULTS.cardBorder,
    bgColor: 'transparent'
  }
}

/**
 * Vue Composable：响应式图表主题色（自动监听主题切换）
 *
 * @example
 * ```ts
 * const { chartColors } = useChartColors()
 * // chartColors.value.textColor 自动跟随主题变化
 * ```
 */
export function useChartColors() {
  const chartColors = ref<ChartThemeColors>({ ...DARK_DEFAULTS })
  let observer: MutationObserver | null = null

  function refresh() {
    chartColors.value = readChartThemeColors()
  }

  onMounted(() => {
    refresh()
    observer = new MutationObserver(refresh)
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme', 'class']
    })
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
  })

  return { chartColors, refresh }
}

// ─── 状态色映射 ───────────────────────────────────────────────

/**
 * Issue 状态名称到颜色的映射
 * 这些颜色通常来自后端 issue_status 表的 color 字段，此处为回退默认值
 */
export const STATUS_COLORS: Record<string, string> = {
  'Open': '#58a6ff',
  'In Progress': '#f0883e',
  'Code Review': '#a371f7',
  'Testing': '#d29922',
  'Done': '#3fb950',
  'Cancelled': '#6b7280',
  'Reopened': '#f85149',
  'Todo': '#58a6ff',
  'Closed': '#3fb950',
  'Solved': '#3fb950',
  'Online': '#3fb950'
}

/**
 * 状态分类到颜色的映射（workflow 图使用）
 */
export const STATUS_CATEGORY_COLORS: Record<string, string> = {
  open: '#58a6ff',
  in_progress: '#d29922',
  done: '#3fb950',
  cancelled: '#f85149'
}

// ─── 通用色板 ─────────────────────────────────────────────────

/**
 * ECharts 通用色板（轮循分配颜色）
 * 10 色序列，覆盖常见数据可视化场景
 */
export const CHART_PALETTE = [
  '#58a6ff', '#3fb950', '#f0883e', '#a371f7', '#d29922',
  '#f85149', '#79c0ff', '#56d364', '#ffa657', '#d2a8ff'
]

/**
 * 根据标签/分类/索引获取图表项颜色
 *
 * @param label - 数据标签
 * @param groupBy - 分组维度（status/priority/type 等）
 * @param idx - 数据索引（用于色板轮循）
 */
export function getChartItemColor(label: string, groupBy: string, idx: number): string {
  if (groupBy === 'status' || groupBy === 'by_status') {
    return STATUS_COLORS[label] || CHART_PALETTE[idx % CHART_PALETTE.length]
  }
  return CHART_PALETTE[idx % CHART_PALETTE.length]
}

// ─── ECharts 图表渐变色工具 ──────────────────────────────────────

/**
 * 生成 ECharts 面积图使用的线性渐变色停止点
 *
 * @param color - 基础颜色（hex 格式）
 * @param opacity - 顶部透明度（默认 0.2）
 */
export function areaGradient(color: string, opacity = 0.2) {
  const rgb = hexToRgb(color)
  return {
    type: 'linear' as const,
    x: 0, y: 0, x2: 0, y2: 1,
    colorStops: [
      { offset: 0, color: `rgba(${rgb}, ${opacity})` },
      { offset: 1, color: `rgba(${rgb}, 0)` }
    ]
  }
}

/**
 * hex 颜色转 RGB 数字字符串
 * @example hexToRgb('#58a6ff') → '88, 166, 255'
 */
function hexToRgb(hex: string): string {
  const h = hex.replace('#', '')
  const r = parseInt(h.substring(0, 2), 16)
  const g = parseInt(h.substring(2, 4), 16)
  const b = parseInt(h.substring(4, 6), 16)
  return `${r}, ${g}, ${b}`
}

// ─── 图表系列预设色（常用于趋势图/燃尽图） ──────────────────────────

/** 创建趋势色：accent 蓝 */
export const SERIES_ACCENT = '#58a6ff'
/** 完成/已解决色：success 绿 */
export const SERIES_SUCCESS = '#3fb950'
/** 危险/实际剩余色：danger 红 */
export const SERIES_DANGER = '#f85149'
/** 理想/参考线色：tertiary 灰 */
export const SERIES_TERTIARY = '#6b7280'
/** 进行中/警告色：warning 橙 */
export const SERIES_WARNING = '#d29922'
/** 范围线/紫色系列 */
export const SERIES_PURPLE = '#a371f7'
/** 标记线/今日标识色：橘色 */
export const SERIES_MARKER = '#f0883e'

// ─── 图表下载背景色（透明不适合下载，用暗色兜底） ──────────────────────

/**
 * 获取图表下载用的背景色（从 CSS 变量读取）
 */
export function getChartDownloadBgColor(): string {
  const style = getComputedStyle(document.documentElement)
  return style.getPropertyValue('--tf-bg-body').trim() || '#1b1d21'
}
