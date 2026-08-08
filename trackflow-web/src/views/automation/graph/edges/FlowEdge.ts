/**
 * FlowEdge — 工作流边（现代简洁风格）
 *
 * 设计规范：
 * - idle：2px 细线 + 75% 透明度，紫色，低调不抢眼
 * - running：2.5px 加亮，蓝色 + 流动粒子动画
 * - done：绿色
 * - warning：黄色虚线（类型宽松兼容）
 * - incompatible：红色虚线（类型不兼容）
 *
 * 实现说明：
 * - Model.getEdgeStyle() 只设定颜色供 LogicFlow 内部使用（选中框等）
 * - View.getEdge() 完全自绘路径，不依赖 Model 样式渲染
 * - Model.getArrowStyle() offset=0 禁用默认箭头尺寸参数
 * - View.getEndArrow() 自绘小三角；View.getStartArrow() 返回 null（起点用圆点代替）
 */
import { BezierEdge, BezierEdgeModel, h } from '@logicflow/core'

// ─── 类型定义 ─────────────────────────────────────────────────────────────────

/** 端口数据类型兼容性 */
export type TypeCompat = 'compatible' | 'warning' | 'incompatible'

/**
 * 检查源端口类型 → 目标端口类型的兼容性
 * - compatible：完全兼容（相同类型、any 类型）
 * - warning：宽松兼容（string↔number 等可隐式转换）
 * - incompatible：不兼容（无法安全转换）
 */
export function checkTypeCompatibility(sourceType: string, targetType: string): TypeCompat {
  if (!sourceType || !targetType) return 'compatible'
  if (sourceType === targetType || sourceType === 'any' || targetType === 'any') return 'compatible'

  const warningPairs = new Set([
    'string->number', 'number->string',
    'object->string', 'string->object',
    'array->object',  'object->array',
    'number->boolean','boolean->number',
  ])
  return warningPairs.has(`${sourceType}->${targetType}`) ? 'warning' : 'incompatible'
}

// ─── 常量 ─────────────────────────────────────────────────────────────────────

const STROKE_WIDTH_NORMAL  = '2'
const STROKE_WIDTH_RUNNING = '2.5'
const OPACITY_NORMAL       = '0.75'
const OPACITY_RUNNING      = '1'
const OPACITY_DOT_NORMAL   = '0.8'

// ─── Model ───────────────────────────────────────────────────────────────────

export class FlowEdgeModel extends BezierEdgeModel {
  /** 提供颜色给 LogicFlow 内部（选中框等），不负责实际线条渲染 */
  getEdgeStyle() {
    const style = super.getEdgeStyle()
    style.strokeWidth = 2
    style.stroke = this._resolveColor()
    return style
  }

  /** 禁用 LogicFlow 默认箭头尺寸参数（由 View.getEndArrow 自绘） */
  getArrowStyle() {
    const style = super.getArrowStyle()
    style.offset = 0
    style.verticalLength = 0
    return style
  }

  private _resolveColor(): string {
    const props = this.properties as any
    const status    = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    return resolveEdgeColor(status, typeCompat)
  }
}

// ─── View ─────────────────────────────────────────────────────────────────────

export class FlowEdgeView extends BezierEdge {
  /** 主体：路径 + 起点圆点 + 可选粒子动画 */
  getEdge() {
    const { model }  = this.props as any
    const props      = model?.properties as any
    const status     = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    const isRunning  = status === 'running'

    const pathD  = buildPathD(model)
    const color  = resolveEdgeColor(status, typeCompat)
    const isDashed = typeCompat === 'warning' || typeCompat === 'incompatible'
    const { startPoint } = model
    const pathId = `flow-path-${model.id}`

    const mainPath = h('path', {
      d: pathD,
      fill: 'none',
      stroke: color,
      'stroke-width': isRunning ? STROKE_WIDTH_RUNNING : STROKE_WIDTH_NORMAL,
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
      opacity: isRunning ? OPACITY_RUNNING : OPACITY_NORMAL,
      ...(isDashed ? { 'stroke-dasharray': '6 4' } : {}),
    })

    const startDot = startPoint ? h('circle', {
      cx: startPoint.x, cy: startPoint.y,
      r: '3.5',
      fill: color,
      opacity: isRunning ? OPACITY_RUNNING : OPACITY_DOT_NORMAL,
    }) : null

    const warningBadge = typeCompat === 'warning' ? buildWarningBadge(model) : null

    const particles: ReturnType<typeof h>[] = []
    if (isRunning) {
      particles.push(
        h('defs', {}, [h('path', { id: pathId, d: pathD, fill: 'none', stroke: 'none' })]),
        buildParticle(pathId, color, 0),
        buildParticle(pathId, color, 0.7),
        buildParticle(pathId, color, 1.4),
      )
    }

    return h('g', {}, [mainPath, startDot, warningBadge, ...particles].filter(Boolean))
  }

  /** 末端箭头：小实心三角（9×8） */
  getEndArrow() {
    const { model }  = this.props as any
    const props      = model?.properties as any
    const status     = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    const isRunning  = status === 'running'
    const color      = resolveEdgeColor(status, typeCompat)

    return h('polygon', {
      points: '5,0 -4,4 -4,-4',
      fill: color,
      stroke: 'none',
      opacity: isRunning ? OPACITY_RUNNING : OPACITY_DOT_NORMAL,
    })
  }

  /** 不显示起点箭头（由 getEdge 中的圆点代替） */
  getStartArrow() { return null as any }
}

// ─── 纯函数工具（无副作用，可单元测试） ────────────────────────────────────────

/** 根据运行状态和类型兼容性计算边颜色（CSS 变量优先，硬编码兜底） */
export function resolveEdgeColor(status: string, typeCompat: TypeCompat): string {
  if (status === 'running')             return 'var(--wf-status-running, #3b82f6)'
  if (status === 'done')                return 'var(--wf-status-success, #22c55e)'
  if (typeCompat === 'warning')         return 'var(--wf-status-warning, #f59e0b)'
  if (typeCompat === 'incompatible')    return 'var(--wf-status-failed, #ef4444)'
  return 'var(--wf-edge-color, #6366f1)'
}

/** 从 LogicFlow model 构建贝塞尔路径 d 属性 */
function buildPathD(model: any): string {
  try {
    const { startPoint, endPoint, pointsList } = model
    if (!startPoint || !endPoint) return ''
    if (pointsList?.length >= 2) {
      const [cp1, cp2] = pointsList
      return `M ${startPoint.x} ${startPoint.y} C ${cp1.x} ${cp1.y} ${cp2.x} ${cp2.y} ${endPoint.x} ${endPoint.y}`
    }
    return `M ${startPoint.x} ${startPoint.y} L ${endPoint.x} ${endPoint.y}`
  } catch { return '' }
}

/** 在边中点上方渲染警告图标 */
function buildWarningBadge(model: any) {
  try {
    const { startPoint, endPoint } = model
    if (!startPoint || !endPoint) return null
    return h('text', {
      x: (startPoint.x + endPoint.x) / 2,
      y: (startPoint.y + endPoint.y) / 2 - 6,
      'font-size': '11',
      'text-anchor': 'middle',
      fill: 'var(--wf-status-warning, #f59e0b)',
    }, '⚠')
  } catch { return null }
}

/**
 * 构建单个流动粒子（沿路径运动的 SVG animateMotion）
 * 注意：glow 和 core 各自持有独立的 mpath 实例，不共享虚拟节点
 */
function buildParticle(pathId: string, color: string, delayS: number) {
  const motionAttrs = {
    dur: '1.6s',
    begin: `${delayS}s`,
    repeatCount: 'indefinite',
    rotate: 'auto',
    calcMode: 'spline' as const,
    keySplines: '0.4 0 0.6 1',
    keyTimes: '0;1',
  }

  const href = `#${pathId}`

  // 每个 animateMotion 需要独立的 mpath 实例
  const glow = h('circle', { r: 4, fill: color, opacity: '0.25' }, [
    h('animateMotion', motionAttrs, [h('mpath', { 'xlink:href': href })]),
  ])
  const core = h('circle', { r: 2.5, fill: '#fff', opacity: '0.9' }, [
    h('animateMotion', motionAttrs, [h('mpath', { 'xlink:href': href })]),
  ])

  return h('g', {}, [glow, core])
}

// ─── 注册导出 ─────────────────────────────────────────────────────────────────

export const FlowEdge = {
  type: 'flow-edge',
  view: FlowEdgeView,
  model: FlowEdgeModel,
}

export default FlowEdge
