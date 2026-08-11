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
export const HEADER_H                 = 49 // 标题区（含底部分隔线）高度
export const PORT_SECTION_PADDING_V   = 4  // 每个输入/输出分区的上下内边距
export const PORT_DIVIDER_H           = 1  // 输入、输出分区之间的分隔线
export const OPTIONAL_TOGGLE_H        = 24 // 可选参数开关行高
export const PORT_HANDLE_OFFSET       = 6  // 锚点相对卡片边缘的外伸距离

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

  /** 返回和 NodeCard 第一段输入区完全一致的必显端口。 */
  _getVisibleInputs(props: any): PortDef[] {
    const inputs: PortDef[] = props?.inputs || []
    const nodeType: string | undefined = props?.nodeType

    if (!nodeType) return inputs

    const def = getNodeDefinition(nodeType)
    if (!def) return inputs

    const optionalNames = new Set(def.inputPorts.filter(p => p.optional).map(p => p.name))
    if (optionalNames.size === 0) return inputs

    return inputs.filter(p => !optionalNames.has(p.name))
  }

  /** 返回 NodeCard 第二段输入区中的可选端口。 */
  _getOptionalInputs(props: any): PortDef[] {
    const inputs: PortDef[] = props?.inputs || []
    const nodeType: string | undefined = props?.nodeType

    if (!nodeType) return []

    const def = getNodeDefinition(nodeType)
    if (!def) return []

    const optionalNames = new Set(def.inputPorts.filter(p => p.optional).map(p => p.name))
    return inputs.filter(p => optionalNames.has(p.name))
  }

  /**
   * 计算 NodeCard 的真实布局。
   *
   * 模型锚点和 Vue 卡片必须遵循同一套分区规则：不能再以“端口数量 × 行高”
   * 粗略推算，否则分区 padding、折叠行和分隔线会令连线偏离视觉端口。
   */
  _getPortLayout(props: any) {
    const visibleInputs = this._getVisibleInputs(props)
    const optionalInputs = this._getOptionalInputs(props)
    const outputs: PortDef[] = props?.outputs || []
    const optionalExpanded: boolean = props?.optionalExpanded ?? false

    const inputGroups: PortDef[][] = []
    if (visibleInputs.length) inputGroups.push(visibleInputs)
    if (optionalExpanded && optionalInputs.length) inputGroups.push(optionalInputs)

    return {
      inputGroups,
      outputs,
      // 折叠时显示“+ N 个可选参数”；展开时显示“收起可选参数”。
      hasOptionalToggle: optionalInputs.length > 0,
    }
  }

  /** 根据 NodeCard 的实际分区高度计算节点高度。 */
  _calcHeight(props: any): number {
    const { inputGroups, outputs, hasOptionalToggle } = this._getPortLayout(props)
    let height = HEADER_H

    inputGroups.forEach(group => {
      height += PORT_SECTION_PADDING_V * 2 + group.length * PORT_ROW_H
    })
    if (hasOptionalToggle) height += OPTIONAL_TOGGLE_H
    if (inputGroups.length && outputs.length) height += PORT_DIVIDER_H
    if (outputs.length) height += PORT_SECTION_PADDING_V * 2 + outputs.length * PORT_ROW_H

    return height
  }

  /** 动态生成具名锚点（只为可见端口生成） */
  getDefaultAnchor() {
    const { x, y, width, height, id, properties } = this
    const { inputGroups, outputs, hasOptionalToggle } = this._getPortLayout(properties as any)

    const anchors: any[] = []
    let cursorY = y - height / 2 + HEADER_H

    // 每个输入分区都有自己的上下 padding，锚点圆心取端口行的精确中心。
    inputGroups.forEach(group => {
      cursorY += PORT_SECTION_PADDING_V
      group.forEach(p => {
        anchors.push({
          id:          `${id}-in-${p.name}`,
          x:           x - width / 2 - PORT_HANDLE_OFFSET,
          y:           cursorY + PORT_ROW_H / 2,
          type:        'input',
          edgeAddable: true,
          connectable: true,
          _portName:   p.name,
        })
        cursorY += PORT_ROW_H
      })
      cursorY += PORT_SECTION_PADDING_V
    })

    if (hasOptionalToggle) cursorY += OPTIONAL_TOGGLE_H
    if (inputGroups.length && outputs.length) cursorY += PORT_DIVIDER_H

    if (outputs.length) {
      cursorY += PORT_SECTION_PADDING_V
      outputs.forEach(p => {
        anchors.push({
          id:          `${id}-out-${p.name}`,
          x:           x + width / 2 + PORT_HANDLE_OFFSET,
          y:           cursorY + PORT_ROW_H / 2,
          type:        'output',
          edgeAddable: true,
          connectable: true,
          _portName:   p.name,
        })
        cursorY += PORT_ROW_H
      })
    }

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
