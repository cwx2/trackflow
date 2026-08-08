/**
 * FlowEdge — 管道风格边（pipe-style edge）
 *
 * 外层彩色粗线 + 内层白色细线叠加，产生管道感。
 * 起点实心圆点，末端实心三角箭头（通过 getEndArrow 重写）。
 *
 * 类型兼容规则：
 * - 相同类型或 any → 允许（默认蓝色）
 * - string↔number/object↔string → 警告（黄色边）
 * - string→boolean 等不兼容 → 拒绝（红色虚线）
 */
import { BezierEdge, BezierEdgeModel, h } from '@logicflow/core'

// ─── 类型兼容矩阵 ──────────────────────────────────────────────────────────

export type TypeCompat = 'compatible' | 'warning' | 'incompatible'

/**
 * 检查源端口类型到目标端口类型的兼容性
 */
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

    // 外层线宽（管道外层）
    style.strokeWidth = 8

    if (status === 'running') {
      style.stroke = 'var(--wf-status-running)'
    } else if (status === 'done') {
      style.stroke = 'var(--wf-status-success)'
    } else if (typeCompat === 'warning') {
      style.stroke = 'var(--wf-status-warning, #d29922)'
    } else if (typeCompat === 'incompatible') {
      style.stroke = 'var(--wf-status-failed)'
      style.strokeDasharray = '8 5'
    } else {
      style.stroke = 'var(--wf-edge-color, var(--tf-accent))'
    }
    return style
  }

  getArrowStyle() {
    const style = super.getArrowStyle()
    // 我们通过 getEndArrow() 自绘箭头，禁用默认箭头参数
    // offset 必须保留，供 getLastTwoPoints 计算切线用
    style.offset = 0
    style.verticalLength = 0
    return style
  }
}

// ─── View ─────────────────────────────────────────────────────────────────────

export class FlowEdgeView extends BezierEdge {
  /**
   * 重写 getEdge：管道风格（外层彩色 + 内层白色）+ 起点圆点 + 数据流粒子
   *
   * getShape() 会调用 getEdge()，所以这里是正确的切入点。
   */
  getEdge() {
    const { model } = this.props as any
    const props = model?.properties as any
    const status = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    const isRunning = status === 'running'

    const pathD = this._getPathD(model)
    const color = this._getColor(status, typeCompat)
    const isDashed = typeCompat === 'incompatible'

    const { startPoint } = model

    // ── 管道外层（彩色粗线）──────────────────────────────────────────────────
    const outerPath = h('path', {
      d: pathD,
      fill: 'none',
      stroke: color,
      'stroke-width': '8',
      'stroke-linecap': 'round',
      ...(isDashed ? { 'stroke-dasharray': '8 5' } : {}),
    })

    // ── 管道内层（白色细线）──────────────────────────────────────────────────
    const innerPath = h('path', {
      d: pathD,
      fill: 'none',
      stroke: 'white',
      'stroke-width': '4',
      'stroke-linecap': 'round',
      ...(isDashed ? { 'stroke-dasharray': '8 5' } : {}),
    })

    // ── 起点实心圆 ──────────────────────────────────────────────────────────
    const startDot = startPoint
      ? h('circle', {
          cx: startPoint.x,
          cy: startPoint.y,
          r: '5',
          fill: color,
          stroke: 'white',
          'stroke-width': '1.5',
        })
      : null

    // ── 类型警告图标 ──────────────────────────────────────────────────────────
    const warningIcon = typeCompat === 'warning'
      ? this._warningBadge(model)
      : null

    // ── 动画粒子（running 状态） ──────────────────────────────────────────────
    const pathId = `flow-path-${model.id}`
    const particles: any[] = []

    if (isRunning) {
      particles.push(
        h('defs', {}, [
          h('path', { id: pathId, d: pathD, fill: 'none', stroke: 'none' }),
        ]),
        this._particle(pathId, 0),
        this._particle(pathId, 0.6),
        this._particle(pathId, 1.2),
      )
    }

    return h('g', {}, [
      outerPath,
      innerPath,
      startDot,
      warningIcon,
      ...particles,
    ].filter(Boolean))
  }

  /**
   * 重写 getEndArrow：绘制实心三角箭头（管道末端）
   */
  getEndArrow() {
    const { model } = this.props as any
    const props = model?.properties as any
    const status = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    const color = this._getColor(status, typeCompat)

    // 箭头尺寸
    const arrowLen = 14
    const arrowWidth = 8

    // solid 实心三角，尖端在右（marker orient 会自动旋转）
    return h('polygon', {
      points: `${arrowLen / 2},0 ${-arrowLen / 2},${arrowWidth / 2} ${-arrowLen / 2},${-arrowWidth / 2}`,
      fill: color,
      stroke: 'none',
    })
  }

  /**
   * 重写 getStartArrow：不显示起点箭头（我们用圆点代替）
   */
  getStartArrow() {
    return null as any
  }

  // ─── 私有辅助方法 ─────────────────────────────────────────────────────────

  /** 获取当前边颜色 */
  private _getColor(status: string, typeCompat: TypeCompat): string {
    if (status === 'running') return 'var(--wf-status-running, #4a90d9)'
    if (status === 'done') return 'var(--wf-status-success, #3fb950)'
    if (typeCompat === 'warning') return 'var(--wf-status-warning, #d29922)'
    if (typeCompat === 'incompatible') return 'var(--wf-status-failed, #f85149)'
    return 'var(--wf-edge-color, var(--tf-accent, #4a90d9))'
  }

  /** 类型警告标识 */
  private _warningBadge(model: any) {
    try {
      const { startPoint, endPoint } = model
      if (!startPoint || !endPoint) return null
      const mx = (startPoint.x + endPoint.x) / 2
      const my = (startPoint.y + endPoint.y) / 2
      return h('text', {
        x: mx, y: my - 6,
        'font-size': '12',
        'text-anchor': 'middle',
        fill: 'var(--wf-status-warning, #d29922)',
      }, '⚠')
    } catch {
      return null
    }
  }

  /** 生成单个数据流粒子 */
  private _particle(pathId: string, delayS: number) {
    const motionProps = {
      dur: '1.8s',
      begin: `${delayS}s`,
      repeatCount: 'indefinite',
      rotate: 'auto',
      calcMode: 'spline',
      keySplines: '0.4 0 0.6 1',
      keyTimes: '0;1',
    }

    const mpath = h('mpath', { 'xlink:href': `#${pathId}` })

    const glow = h('circle', { r: 5, fill: 'var(--wf-flow-particle-glow)', opacity: '0.6' }, [
      h('animateMotion', motionProps, [mpath]),
    ])

    const core = h('circle', { r: 3, fill: 'var(--wf-flow-particle)' }, [
      h('animateMotion', motionProps, [
        h('mpath', { 'xlink:href': `#${pathId}` }),
      ]),
    ])

    return h('g', {}, [glow, core])
  }

  /** 从 model 生成贝塞尔路径 d 属性 */
  private _getPathD(model: any): string {
    try {
      const { startPoint, endPoint, pointsList } = model
      if (!startPoint || !endPoint) return ''

      if (pointsList && pointsList.length >= 2) {
        const [cp1, cp2] = pointsList
        return `M ${startPoint.x} ${startPoint.y} C ${cp1.x} ${cp1.y} ${cp2.x} ${cp2.y} ${endPoint.x} ${endPoint.y}`
      }
      return `M ${startPoint.x} ${startPoint.y} L ${endPoint.x} ${endPoint.y}`
    } catch {
      return ''
    }
  }
}

// ─── 导出注册对象 ─────────────────────────────────────────────────────────────

export const FlowEdge = {
  type: 'flow-edge',
  view: FlowEdgeView,
  model: FlowEdgeModel,
}

export default FlowEdge
