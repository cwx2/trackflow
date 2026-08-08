/**
 * BaseNodeModel — 所有工作流节点的 Model 基类
 *
 * 职责：
 * - 统一管理节点尺寸（紧凑/展开两种高度）
 * - 根据输入/输出端口列表动态计算具名锚点坐标
 * - 支持可选端口折叠（optional ports only shown when optionalExpanded=true）
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
export const NODE_WIDTH      = 280   // 节点宽度
export const PORT_ROW_H      = 28    // 每个端口行高
export const HEADER_H        = 48    // 标题区高度
export const COMPACT_H       = 80    // 紧凑态最小高度（无端口时）
export const PADDING_V       = 8     // 端口区上下 padding
export const OPTIONAL_TOGGLE_H = 24  // "可选参数"提示行高

export abstract class BaseNodeModel extends HtmlNodeModel {
  /** 子类需覆盖：提供节点类型名（用于从 node-definitions 读取默认值） */
  abstract get nodeType(): string

  /** 初始化完成标志，防止 initNodeData 期间触发 y 轴补偿 */
  private _initialized = false

  initNodeData(data: any) {
    super.initNodeData(data)
    this.width = NODE_WIDTH
    this.height = this._calcHeight(data.properties)
    // 禁用默认文本渲染
    this.text = { value: '', x: 0, y: 0, draggable: false, editable: false }
    this._initialized = true
  }

  /** 属性变化时重新计算高度（展开/收起切换）
   *
   * 使用 setAttributes 而非 setProperty，确保在 properties 已同步后才执行高度计算。
   * LogicFlow 在 updateProperties 最后会调用 setAttributes，此时 this.properties 已是新值。
   *
   * 关键：节点 y 是中心点，高度变化会上下均等撑开导致头部移位。
   * 解决方案：记录旧高度，高度变化时把 y 往下/上移 delta/2，使顶部边缘位置不变。
   */
  setAttributes() {
    if (!this._initialized) return
    const newHeight = this._calcHeight(this.properties)
    if (newHeight !== this.height) {
      const delta = newHeight - this.height
      this.height = newHeight
      // 展开时 delta > 0，y 下移 delta/2，顶部不动
      // 收起时 delta < 0，y 上移 |delta|/2，顶部不动
      this.y = this.y + delta / 2
    }
  }

  setProperty(key: string, val: any) {
    super.setProperty(key, val)
  }

  /**
   * 获取当前可见的输入端口（考虑可选折叠状态）
   * - 如果节点定义中没有 optional 端口，返回全部
   * - 如果有 optional 端口且 optionalExpanded=false，只返回非 optional 的
   * - 如果 optionalExpanded=true，返回全部
   */
  _getVisibleInputs(props: any): PortDef[] {
    const inputs: PortDef[] = props?.inputs || []
    const optionalExpanded = props?.optionalExpanded ?? false
    const nodeType = props?.nodeType

    if (optionalExpanded || !nodeType) return inputs

    const def = getNodeDefinition(nodeType)
    if (!def) return inputs

    const optionalNames = new Set(
      def.inputPorts.filter(p => p.optional).map(p => p.name)
    )

    // No optional ports defined → show all
    if (optionalNames.size === 0) return inputs

    return inputs.filter(p => !optionalNames.has(p.name))
  }

  /** 获取隐藏的可选端口数量 */
  _getHiddenOptionalCount(props: any): number {
    const inputs: PortDef[] = props?.inputs || []
    const optionalExpanded = props?.optionalExpanded ?? false
    const nodeType = props?.nodeType

    if (optionalExpanded || !nodeType) return 0

    const def = getNodeDefinition(nodeType)
    if (!def) return 0

    const optionalNames = new Set(
      def.inputPorts.filter(p => p.optional).map(p => p.name)
    )

    return inputs.filter(p => optionalNames.has(p.name)).length
  }

  /** 根据端口数量和展开状态计算节点高度 */
  _calcHeight(props: any): number {
    const expanded = props?.expanded ?? false
    const visibleInputs = this._getVisibleInputs(props)
    const outputs: PortDef[] = props?.outputs || []
    const hiddenOptionalCount = this._getHiddenOptionalCount(props)
    const optionalExpanded = props?.optionalExpanded ?? false

    // 基础高度 = 标题区 + visible输入端口行 + 输出端口行 + 可选折叠提示行
    let portRows = visibleInputs.length + outputs.length
    let extraHeight = 0

    // If optional ports are expanded, count them too
    if (optionalExpanded) {
      const allInputs: PortDef[] = props?.inputs || []
      portRows = allInputs.length + outputs.length
      extraHeight += OPTIONAL_TOGGLE_H // "收起可选参数" toggle
    } else if (hiddenOptionalCount > 0) {
      extraHeight += OPTIONAL_TOGGLE_H // "+ N 个可选参数" toggle
    }

    const baseHeight = HEADER_H + Math.max(portRows, 1) * PORT_ROW_H + PADDING_V * 2 + extraHeight

    if (!expanded) return baseHeight

    // 展开时：基础高度 + 配置区（所有参数 + section headers）
    const allInputs: PortDef[] = props?.inputs || []
    const configRows = (allInputs.length + outputs.length) * 2 + 6
    const configHeight = Math.min(configRows * PORT_ROW_H + 60, 400)
    return baseHeight + configHeight
  }

  /** 动态生成具名锚点（只为可见端口生成） */
  getDefaultAnchor() {
    const { x, y, width, height, id, properties } = this
    const visibleInputs = this._getVisibleInputs(properties as any)
    const optionalExpanded = (properties as any)?.optionalExpanded ?? false
    const outputs: PortDef[] = (properties as any)?.outputs || []

    // If optional expanded, anchors for ALL inputs
    const inputsForAnchors = optionalExpanded
      ? ((properties as any)?.inputs || []) as PortDef[]
      : visibleInputs

    const anchors: any[] = []

    // 输入端口锚点：左侧，从 HEADER_H + PADDING 起，每行 PORT_ROW_H
    const startY = y - height / 2 + HEADER_H + PADDING_V + PORT_ROW_H / 2
    inputsForAnchors.forEach((p, i) => {
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

    // 输出端口锚点：右侧，positioned after visible inputs + optional toggle
    const hiddenOptionalCount = this._getHiddenOptionalCount(properties as any)
    const toggleHeight = (hiddenOptionalCount > 0 && !optionalExpanded) || optionalExpanded
      ? OPTIONAL_TOGGLE_H : 0
    const outStartY = startY + inputsForAnchors.length * PORT_ROW_H + toggleHeight
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
