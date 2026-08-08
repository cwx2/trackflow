/**
 * BaseNodeModel — 所有工作流节点的 Model 基类
 *
 * 职责：
 * - 统一管理节点尺寸（根据端口数量动态计算）
 * - 根据输入/输出端口列表动态计算具名锚点坐标
 * - 支持可选端口折叠（optional ports shown when optionalExpanded=true）
 * - 提供 runStatus 状态管理
 */
import { HtmlNodeModel } from '@logicflow/core'
import { getNodeDefinition } from '../../../node-definitions'

/** 端口定义（与 node-definitions 保持一致） */
export interface PortDef {
  name: string
  label?: string
  valueType?: string
  required?: boolean
  optional?: boolean
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
export const NODE_WIDTH         = 280  // 节点宽度
export const PORT_ROW_H         = 28   // 每个端口行高
export const HEADER_H           = 48   // 标题区高度
export const PADDING_V          = 8    // 端口区上下 padding
export const OPTIONAL_TOGGLE_H  = 24   // "可选参数"提示行高

export abstract class BaseNodeModel extends HtmlNodeModel {
  /** 子类需覆盖：提供节点类型名（用于从 node-definitions 读取默认值） */
  abstract get nodeType(): string

  /** 初始化完成标志，防止 initNodeData 期间触发 y 轴补偿 */
  private _initialized = false

  initNodeData(data: any) {
    super.initNodeData(data)
    this.width = NODE_WIDTH
    this.height = this._calcHeight(data.properties)
    this.text = { value: '', x: 0, y: 0, draggable: false, editable: false }
    this._initialized = true
  }

  /**
   * 属性变化时重新计算高度（可选端口展开/收起切换）
   *
   * 使用 setAttributes 确保 properties 已同步后再执行高度计算。
   * 节点 y 为中心点，高度变化时补偿 y 使顶部边缘位置不变。
   */
  setAttributes() {
    if (!this._initialized) return
    const newHeight = this._calcHeight(this.properties)
    if (newHeight !== this.height) {
      const delta = newHeight - this.height
      this.height = newHeight
      this.y = this.y + delta / 2
    }
  }

  setProperty(key: string, val: any) {
    super.setProperty(key, val)
  }

  /**
   * 获取当前可见的输入端口（考虑可选折叠状态）
   * - 若节点定义无 optional 端口，返回全部
   * - optionalExpanded=false 时，只返回非 optional 端口
   * - optionalExpanded=true 时，返回全部
   */
  _getVisibleInputs(props: any): PortDef[] {
    const inputs: PortDef[] = props?.inputs || []
    const optionalExpanded: boolean = props?.optionalExpanded ?? false
    const nodeType: string | undefined = props?.nodeType

    if (optionalExpanded || !nodeType) return inputs

    const def = getNodeDefinition(nodeType)
    if (!def) return inputs

    const optionalNames = new Set(def.inputPorts.filter(p => p.optional).map(p => p.name))
    if (optionalNames.size === 0) return inputs

    return inputs.filter(p => !optionalNames.has(p.name))
  }

  /** 获取隐藏的可选端口数量 */
  _getHiddenOptionalCount(props: any): number {
    const inputs: PortDef[] = props?.inputs || []
    const optionalExpanded: boolean = props?.optionalExpanded ?? false
    const nodeType: string | undefined = props?.nodeType

    if (optionalExpanded || !nodeType) return 0

    const def = getNodeDefinition(nodeType)
    if (!def) return 0

    const optionalNames = new Set(def.inputPorts.filter(p => p.optional).map(p => p.name))
    return inputs.filter(p => optionalNames.has(p.name)).length
  }

  /** 根据端口数量和可选折叠状态计算节点高度 */
  _calcHeight(props: any): number {
    const visibleInputs = this._getVisibleInputs(props)
    const outputs: PortDef[] = props?.outputs || []
    const hiddenOptionalCount = this._getHiddenOptionalCount(props)
    const optionalExpanded: boolean = props?.optionalExpanded ?? false

    let portRows = visibleInputs.length + outputs.length
    let extraHeight = 0

    if (optionalExpanded) {
      const allInputs: PortDef[] = props?.inputs || []
      portRows = allInputs.length + outputs.length
      extraHeight += OPTIONAL_TOGGLE_H // "收起可选参数" toggle
    } else if (hiddenOptionalCount > 0) {
      extraHeight += OPTIONAL_TOGGLE_H // "+ N 个可选参数" toggle
    }

    return HEADER_H + Math.max(portRows, 1) * PORT_ROW_H + PADDING_V * 2 + extraHeight
  }

  /** 动态生成具名锚点（只为可见端口生成） */
  getDefaultAnchor() {
    const { x, y, width, height, id, properties } = this
    const optionalExpanded: boolean = (properties as any)?.optionalExpanded ?? false
    const outputs: PortDef[] = (properties as any)?.outputs || []

    const inputsForAnchors = optionalExpanded
      ? ((properties as any)?.inputs || []) as PortDef[]
      : this._getVisibleInputs(properties as any)

    const anchors: any[] = []

    // 锚点 x 偏移：port-row padding(10px) + port-dot 半径(4px) = 14px
    // 使线条精准连接到卡片内 port-dot 圆心，而非节点边框
    const DOT_OFFSET = 14

    // 输入端口锚点：左侧（向内偏移 14px，对准 port-dot 圆心）
    const startY = y - height / 2 + HEADER_H + PADDING_V + PORT_ROW_H / 2
    inputsForAnchors.forEach((p, i) => {
      anchors.push({
        id:          `${id}-in-${p.name}`,
        x:           x - width / 2 + DOT_OFFSET,
        y:           startY + i * PORT_ROW_H,
        type:        'input',
        edgeAddable: true,
        connectable: true,
        _portName:   p.name,
      })
    })

    // 输出端口锚点：右侧（向内偏移 14px，对准 port-dot 圆心）
    const hiddenOptionalCount = this._getHiddenOptionalCount(properties as any)
    const toggleHeight = (hiddenOptionalCount > 0 && !optionalExpanded) || optionalExpanded
      ? OPTIONAL_TOGGLE_H : 0
    const outStartY = startY + inputsForAnchors.length * PORT_ROW_H + toggleHeight
    outputs.forEach((p, i) => {
      anchors.push({
        id:          `${id}-out-${p.name}`,
        x:           x + width / 2 - DOT_OFFSET,
        y:           outStartY + i * PORT_ROW_H,
        type:        'output',
        edgeAddable: true,
        connectable: true,
        _portName:   p.name,
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
