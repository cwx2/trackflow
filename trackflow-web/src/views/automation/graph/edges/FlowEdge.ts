/**
 * FlowEdge — 现代简洁风格边
 *
 * 设计参考 Linear / n8n：
 * - 细线（2px）+ 轻微透明度，默认状态低调不抢眼
 * - 悬停/选中时高亮
 * - 起点：小实心圆（6px），贴合端口
 * - 终点：圆润箭头（实心，不过大）
 * - running：蓝色加亮 + 流动粒子
 * - done：绿色
 * - warning：黄色虚线
 * - incompatible：红色虚线
 */
import { BezierEdge, BezierEdgeModel, h } from '@logicflow/core'

// ─── 类型兼容矩阵 ──────────────────────────────────────────────────────────

export type TypeCompat = 'compatible' | 'warning' | 'incompatible'

export function checkTypeCompatibility(sourceType: string, targetType: string): TypeCompat {
  if (!sourceType || !targetType) return 'compatible'
  if (sourceType === targetType) return 'compatible'
  if (sourceType === 'any' || targetType === 'any') return 'compatible'

  const warningPairs = new Set([
    'string->number', 'number->string',
    'object->string', 'string->object',
    'array->object', 'object->array',
    'number->boolean', 'boolean->number',
  ])
  const key = `${sourceType}->${targetType}`
  if (warningPairs.has(key)) return 'warning'

  return 'incompatible'
}

// ─── Model ───────────────────────────────────────────────────────────────────

export class FlowEdgeModel extends BezierEdgeModel {
  getEdgeStyle() {
    const style = super.getEdgeStyle()
    const props = this.properties as any
    const status = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'

    // 细线，LogicFlow 用这个控制主路径，View 里会覆盖
    style.strokeWidth = 2

    if (status === 'running') {
      style.stroke = 'var(--wf-status-running, #3b82f6)'
    } else if (status === 'done') {
      style.stroke = 'var(--wf-status-success, #22c55e)'
    } else if (typeCompat === 'warning') {
      style.stroke = 'var(--wf-status-warning, #f59e0b)'
      style.strokeDasharray = '6 4'
    } else if (typeCompat === 'incompatible') {
      style.stroke = 'var(--wf-status-failed, #ef4444)'
      style.strokeDasharray = '6 4'
    } else {
      style.stroke = 'var(--wf-edge-color, #6366f1)'
    }
    return style
  }

  getArrowStyle() {
    const style = super.getArrowStyle()
    style.offset = 0
    style.verticalLength = 0
    return style
  }
}

// ─── View ─────────────────────────────────────────────────────────────────────

export class FlowEdgeView extends BezierEdge {
  getEdge() {
    const { model } = this.props as any
    const props = model?.properties as any
    const status = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    const isRunning = status === 'running'

    const pathD = this._getPathD(model)
    const color = this._getColor(status, typeCompat)
    const isDashed = typeCompat === 'warning' || typeCompat === 'incompatible'
    const { startPoint } = model

    const pathId = `flow-path-${model.id}`

    // ── 主路径（细线，带透明度）──────────────────────────────────────────────
    const mainPath = h('path', {
      d: pathD,
      fill: 'none',
      stroke: color,
      'stroke-width': isRunning ? '2.5' : '2',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
      opacity: isRunning ? '1' : '0.75',
      ...(isDashed ? { 'stroke-dasharray': '6 4' } : {}),
    })

    // ── 起点圆点（小，精致）──────────────────────────────────────────────────
    const startDot = startPoint
      ? h('circle', {
          cx: startPoint.x,
          cy: startPoint.y,
          r: '3.5',
          fill: color,
          opacity: isRunning ? '1' : '0.8',
        })
      : null

    // ── 类型警告标识 ──────────────────────────────────────────────────────────
    const warningIcon = typeCompat === 'warning'
      ? this._warningBadge(model)
      : null

    // ── 动画粒子（running 状态） ──────────────────────────────────────────────
    const particles: any[] = []
    if (isRunning) {
      particles.push(
        h('defs', {}, [
          h('path', { id: pathId, d: pathD, fill: 'none', stroke: 'none' }),
        ]),
        this._particle(pathId, 0),
        this._particle(pathId, 0.7),
        this._particle(pathId, 1.4),
      )
    }

    return h('g', {}, [
      mainPath,
      startDot,
      warningIcon,
      ...particles,
    ].filter(Boolean))
  }

  /**
   * 末端箭头：小巧的实心三角，不喧宾夺主
   */
  getEndArrow() {
    const { model } = this.props as any
    const props = model?.properties as any
    const status = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    const color = this._getColor(status, typeCompat)
    const isRunning = status === 'running'

    // 7×10 的小三角（比之前 8×14 小很多）
    return h('polygon', {
      points: '5,0 -4,4 -4,-4',
      fill: color,
      stroke: 'none',
      opacity: isRunning ? '1' : '0.8',
    })
  }

  getStartArrow() {
    return null as any
  }

  // ─── 私有辅助方法 ─────────────────────────────────────────────────────────

  private _getColor(status: string, typeCompat: TypeCompat): string {
    if (status === 'running') return 'var(--wf-status-running, #3b82f6)'
    if (status === 'done')    return 'var(--wf-status-success, #22c55e)'
    if (typeCompat === 'warning')      return 'var(--wf-status-warning, #f59e0b)'
    if (typeCompat === 'incompatible') return 'var(--wf-status-failed, #ef4444)'
    return 'var(--wf-edge-color, #6366f1)'
  }

  private _warningBadge(model: any) {
    try {
      const { startPoint, endPoint } = model
      if (!startPoint || !endPoint) return null
      const mx = (startPoint.x + endPoint.x) / 2
      const my = (startPoint.y + endPoint.y) / 2
      return h('text', {
        x: mx, y: my - 6,
        'font-size': '11',
        'text-anchor': 'middle',
        fill: 'var(--wf-status-warning, #f59e0b)',
      }, '⚠')
    } catch { return null }
  }

  private _particle(pathId: string, delayS: number) {
    const motionProps = {
      dur: '1.6s',
      begin: `${delayS}s`,
      repeatCount: 'indefinite',
      rotate: 'auto',
      calcMode: 'spline',
      keySplines: '0.4 0 0.6 1',
      keyTimes: '0;1',
    }
    const mpath = () => h('mpath', { 'xlink:href': `#${pathId}` })

    // 发光外圈
    const glow = h('circle', {
      r: 4,
      fill: 'var(--wf-flow-particle-glow, rgba(99,102,241,0.3))',
      opacity: '0.5',
    }, [h('animateMotion', motionProps, [mpath()])])

    // 实心核心
    const core = h('circle', {
      r: 2.5,
      fill: 'var(--wf-flow-particle, #fff)',
    }, [h('animateMotion', motionProps, [mpath()])])

    return h('g', {}, [glow, core])
  }

  private _getPathD(model: any): string {
    try {
      const { startPoint, endPoint, pointsList } = model
      if (!startPoint || !endPoint) return ''
      if (pointsList && pointsList.length >= 2) {
        const [cp1, cp2] = pointsList
        return `M ${startPoint.x} ${startPoint.y} C ${cp1.x} ${cp1.y} ${cp2.x} ${cp2.y} ${endPoint.x} ${endPoint.y}`
      }
      return `M ${startPoint.x} ${startPoint.y} L ${endPoint.x} ${endPoint.y}`
    } catch { return '' }
  }
}

// ─── 导出注册对象 ─────────────────────────────────────────────────────────────

export const FlowEdge = {
  type: 'flow-edge',
  view: FlowEdgeView,
  model: FlowEdgeModel,
}

export default FlowEdge
