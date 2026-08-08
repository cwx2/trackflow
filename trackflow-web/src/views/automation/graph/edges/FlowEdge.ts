/**
 * FlowEdge — 数据流动画边 + 类型感知连线验证
 *
 * 继承 BezierEdge，运行时在 SVG 上叠加发光粒子沿线游走动画。
 * 边颜色和粒子颜色跟随 --wf-* CSS 变量（3套主题自动适配）。
 *
 * 类型兼容规则：
 * - 相同类型或 any → 允许（绿色预览）
 * - string↔number/object↔string → 警告（黄色边）
 * - string→boolean 等不兼容 → 拒绝连接
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

  // 警告级别（允许连线但标黄）：常见隐式转换场景
  const warningPairs = new Set([
    'string->number', 'number->string',
    'object->string', 'string->object',
    'array->object', 'object->array',
    'number->boolean', 'boolean->number',
  ])
  const key = `${sourceType}->${targetType}`
  if (warningPairs.has(key)) return 'warning'

  // 其余不兼容
  return 'incompatible'
}

// ─── Model ───────────────────────────────────────────────────────────────────

export class FlowEdgeModel extends BezierEdgeModel {
  getEdgeStyle() {
    const style = super.getEdgeStyle()
    const props = this.properties as any
    const status = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'

    style.strokeWidth = 2

    if (status === 'running') {
      style.stroke = 'var(--wf-status-running)'
    } else if (status === 'done') {
      style.stroke = 'var(--wf-status-success)'
    } else if (typeCompat === 'warning') {
      style.stroke = 'var(--wf-status-warning, #d29922)'
    } else if (typeCompat === 'incompatible') {
      style.stroke = 'var(--wf-status-failed)'
      style.strokeDasharray = '4 3'
    } else {
      style.stroke = 'var(--wf-edge-color)'
    }
    return style
  }

  getArrowStyle() {
    const style = super.getArrowStyle()
    const props = this.properties as any
    const status = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'

    let color: string
    if (status === 'running') {
      color = 'var(--wf-status-running)'
    } else if (status === 'done') {
      color = 'var(--wf-status-success)'
    } else if (typeCompat === 'warning') {
      color = 'var(--wf-status-warning, #d29922)'
    } else if (typeCompat === 'incompatible') {
      color = 'var(--wf-status-failed)'
    } else {
      color = 'var(--wf-edge-color)'
    }
    style.fill = color
    style.stroke = color
    return style
  }
}

// ─── View ─────────────────────────────────────────────────────────────────────

export class FlowEdgeView extends BezierEdge {
  /**
   * 覆盖 getShape：在贝塞尔曲线上叠加数据流动画粒子
   */
  getShape() {
    const { model } = this.props as any
    const props = model?.properties as any
    const status = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    const isRunning = status === 'running'

    // 父类基础路径
    const baseShape = super.getShape()

    // 类型警告图标
    const warningIcon = typeCompat === 'warning'
      ? this._warningBadge(model)
      : null

    if (!isRunning && !warningIcon) return baseShape

    const pathId = `flow-path-${model.id}`
    const pathD  = this._getPathD(model)

    const elements: any[] = [baseShape]

    if (isRunning) {
      elements.push(
        h('defs', {}, [
          h('path', { id: pathId, d: pathD, fill: 'none', stroke: 'none' }),
        ]),
        this._particle(pathId, 0),
        this._particle(pathId, 0.6),
        this._particle(pathId, 1.2),
      )
    }

    if (warningIcon) elements.push(warningIcon)

    return h('g', {}, elements)
  }

  /** 类型警告标识（边中点显示 ⚠️） */
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

  /** 生成单个数据流粒子（晕圈 + 核心点） */
  private _particle(pathId: string, delayS: number) {
    const motionProps = {
      dur:          '1.8s',
      begin:        `${delayS}s`,
      repeatCount:  'indefinite',
      rotate:       'auto',
      calcMode:     'spline',
      keySplines:   '0.4 0 0.6 1',
      keyTimes:     '0;1',
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
