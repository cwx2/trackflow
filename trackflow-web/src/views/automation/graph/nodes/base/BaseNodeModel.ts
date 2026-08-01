/**
 * BaseNodeModel — 所有工作流节点的 Model 基类
 *
 * 职责：
 * - 统一管理节点尺寸（紧凑/展开两种高度）
 * - 根据输入/输出端口列表动态计算具名锚点坐标
 * - 提供 runStatus 状态管理
 */
import { HtmlNodeModel } from '@logicflow/core'

/** 端口定义（与 node-definitions 保持一致） */
export interface PortDef {
  name: string
  label?: string
  valueType?: string
  required?: boolean
  description?: string
}

/** 节点运行状态 */
export type NodeRunStatus = 'idle' | 'running' | 'success' | 'failed' | 'skipped' | 'cancelled'

/** 节点元信息 */
export interface NodeMeta {
  title: string
  icon: string
  color: string
  description?: string
}

// 布局常量
export const NODE_WIDTH      = 280   // 节点宽度
export const PORT_ROW_H      = 28    // 每个端口行高
export const HEADER_H        = 48    // 标题区高度
export const COMPACT_H       = 80    // 紧凑态最小高度（无端口时）
export const PADDING_V       = 8     // 端口区上下 padding

export abstract class BaseNodeModel extends HtmlNodeModel {
  /** 子类需覆盖：提供节点类型名（用于从 node-definitions 读取默认值） */
  abstract get nodeType(): string

  initNodeData(data: any) {
    super.initNodeData(data)
    this.width = NODE_WIDTH
    this.height = this._calcHeight(data.properties)
    // 禁用默认文本渲染
    this.text = { value: '', x: 0, y: 0, draggable: false, editable: false }
  }

  /** 属性变化时重新计算高度（展开/收起切换）
   *
   * 关键：节点 y 是中心点，高度变化会上下均等撑开导致头部移位。
   * 解决方案：记录旧高度，展开时把 y 往下移 delta/2，
   * 使顶部边缘位置不变（头部固定）。
   */
  setProperty(key: string, val: any) {
    super.setProperty(key, val)
    if (key === 'expanded' || key === 'inputs' || key === 'outputs') {
      const oldHeight = this.height
      const newHeight = this._calcHeight(this.properties)
      if (newHeight !== oldHeight) {
        const delta = newHeight - oldHeight
        this.height = newHeight
        // 向下偏移，保持顶部不动
        this.y = this.y + delta / 2
      }
    }
  }

  /** 根据端口数量和展开状态计算节点高度 */
  _calcHeight(props: any): number {
    const expanded = props?.expanded ?? false
    const inputs: PortDef[]  = props?.inputs  || []
    const outputs: PortDef[] = props?.outputs || []

    // 基础高度 = 标题区 + 输入端口行 + 输出端口行（永远显示）
    const portRows = inputs.length + outputs.length
    const baseHeight = HEADER_H + Math.max(portRows, 1) * PORT_ROW_H + PADDING_V * 2

    if (!expanded) return baseHeight

    // 展开时：基础高度 + 配置区（每个参数一行 + section header）
    const configRows = (inputs.length + outputs.length) * 2 + 6  // 粗估
    const configHeight = Math.min(configRows * PORT_ROW_H + 60, 400)
    return baseHeight + configHeight
  }

  /** 动态生成具名锚点（输入端口左侧，输出端口右侧） */
  getDefaultAnchor() {
    const { x, y, width, height, id, properties } = this
    const inputs:  PortDef[] = (properties as any)?.inputs  || []
    const outputs: PortDef[] = (properties as any)?.outputs || []

    const anchors: any[] = []

    // 输入端口锚点：左侧，从 HEADER_H + PADDING 起，每行 PORT_ROW_H
    const startY = y - height / 2 + HEADER_H + PADDING_V + PORT_ROW_H / 2
    inputs.forEach((p, i) => {
      anchors.push({
        id:           `${id}-in-${p.name}`,
        x:            x - width / 2,
        y:            startY + i * PORT_ROW_H,
        type:         'input',
        edgeAddable:  true,
        connectable:  true,
        _portName:    p.name,
      })
    })

    // 输出端口锚点：右侧
    const outStartY = startY + inputs.length * PORT_ROW_H
    outputs.forEach((p, i) => {
      anchors.push({
        id:           `${id}-out-${p.name}`,
        x:            x + width / 2,
        y:            outStartY + i * PORT_ROW_H,
        type:         'output',
        edgeAddable:  true,
        connectable:  true,
        _portName:    p.name,
      })
    })

    return anchors
  }

  /** 禁用默认外框（由 NodeCard 自己的 border 替代） */
  getOutlineStyle() {
    const style = super.getOutlineStyle()
    style.stroke = 'none'
    if (style.hover) style.hover.stroke = 'none'
    return style
  }
}
