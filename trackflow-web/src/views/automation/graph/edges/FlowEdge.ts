/**
 * FlowEdge — 数据流动画边
 *
 * 继承 BezierEdge，运行时在 SVG 上叠加发光粒子沿线游走动画。
 * 边颜色和粒子颜色跟随 --wf-* CSS 变量（3套主题自动适配）。
 */
import { BezierEdge, BezierEdgeModel, h } from '@logicflow/core'

// ─── Model ───────────────────────────────────────────────────────────────────

export class FlowEdgeModel extends BezierEdgeModel {
  getEdgeStyle() {
    const style = super.getEdgeStyle()
    const status = (this.properties as any)?.flowStatus || 'idle'
    style.strokeWidth = 2
    style.stroke = status === 'running'
      ? 'var(--wf-status-running)'
      : status === 'done'
      ? 'var(--wf-status-success)'
      : 'var(--wf-edge-color)'
    return style
  }

  getArrowStyle() {
    const style = super.getArrowStyle()
    const status = (this.properties as any)?.flowStatus || 'idle'
    const color = status === 'running'
      ? 'var(--wf-status-running)'
      : status === 'done'
      ? 'var(--wf-status-success)'
      : 'var(--wf-edge-color)'
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
    const status = (model?.properties as any)?.flowStatus || 'idle'
    const isRunning = status === 'running'

    // 父类基础路径
    const baseShape = super.getShape()
    if (!isRunning) return baseShape

    const pathId = `flow-path-${model.id}`
    const pathD  = this._getPathD(model)

    // 用 LogicFlow 内置的 h() 渲染（兼容 .ts 文件）
    return h('g', {}, [
      baseShape,
      // 隐藏参考路径
      h('defs', {}, [
        h('path', { id: pathId, d: pathD, fill: 'none', stroke: 'none' }),
      ]),
      // 3 个粒子，间隔 0.6s
      this._particle(pathId, 0),
      this._particle(pathId, 0.6),
      this._particle(pathId, 1.2),
    ])
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
