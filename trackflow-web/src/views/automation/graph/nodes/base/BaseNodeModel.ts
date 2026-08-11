/**
 * BaseNodeModel — 所有工作流节点的 Model 基类
 *
 * 职责：
 * - 统一管理节点尺寸（根据端口数量动态计算）
 * - 根据输入/输出端口列表动态计算具名锚点坐标
 * - 仅展示必填/已使用的参数，其他能力按需展开
 * - 提供 runStatus 状态管理
 */
import { HtmlNodeModel } from '@logicflow/core'
import { FLOW_PORT } from '../../connection-semantics'
import { getNodePortPresentation } from './node-presentation'

export { FLOW_PORT } from '../../connection-semantics'

/** 端口定义（与 node-definitions 保持一致） */
export interface PortDef {
  name: string
  label?: string
  valueType?: string
  required?: boolean
  optional?: boolean
  description?: string
  cardinality?: 'single' | 'collection'
  semanticType?: string
  /** 当前输入值；输出端口不会使用此字段。 */
  value?: unknown
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
export const COLLAPSED_INPUT_TOGGLE_H = 28 // 收纳参数开关行高
export const PORT_HANDLE_OFFSET       = 6  // 锚点相对卡片边缘的外伸距离
/** 可拖拽热区半径；大于视觉插座，保证端口在稠密行中仍易于命中。 */
export const PORT_CONNECT_HIT_RADIUS  = 12
/** 执行顺序与参数数据是两种不同的关系，不能共用业务端口。 */

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
   * 计算 NodeCard 的真实布局。
   *
   * 模型锚点和 Vue 卡片必须遵循同一套分区规则：不能再以“端口数量 × 行高”
   * 粗略推算，否则分区 padding、折叠行和分隔线会令连线偏离视觉端口。
   */
  _getPortLayout(props: any) {
    const presentation = getNodePortPresentation(props)
    const visibleInputs = presentation.visibleInputs as PortDef[]
    const outputs = presentation.outputs as PortDef[]

    const inputGroups: PortDef[][] = []
    if (visibleInputs.length) inputGroups.push(visibleInputs)

    return {
      inputGroups,
      outputs,
      // 紧凑态显示“更多配置”；展开态显示“收起参数”。
      hasCollapsedInputToggle: presentation.collapsibleInputCount > 0,
    }
  }

  /** 根据 NodeCard 的实际分区高度计算节点高度。 */
  _calcHeight(props: any): number {
    const { inputGroups, outputs, hasCollapsedInputToggle } = this._getPortLayout(props)
    let height = HEADER_H

    inputGroups.forEach(group => {
      height += PORT_SECTION_PADDING_V * 2 + group.length * PORT_ROW_H
    })
    if (hasCollapsedInputToggle) height += COLLAPSED_INPUT_TOGGLE_H
    if (inputGroups.length && outputs.length) height += PORT_DIVIDER_H
    if (outputs.length) height += PORT_SECTION_PADDING_V * 2 + outputs.length * PORT_ROW_H

    return height
  }

  /** 动态生成具名锚点（只为可见端口生成） */
  getDefaultAnchor() {
    const { x, y, width, height, id, properties } = this
    const { inputGroups, outputs, hasCollapsedInputToggle } = this._getPortLayout(properties as any)

    // 标题栏两侧是独立的流程端口，用于表达执行顺序，不会绑定业务参数。
    const anchors: any[] = [
      {
        id: `${id}-in-${FLOW_PORT}`,
        x: x - width / 2 - PORT_HANDLE_OFFSET,
        y: y - height / 2 + HEADER_H / 2,
        type: 'input', edgeAddable: true, connectable: true,
        _portName: FLOW_PORT, _edgeKind: 'control',
      },
      {
        id: `${id}-out-${FLOW_PORT}`,
        x: x + width / 2 + PORT_HANDLE_OFFSET,
        y: y - height / 2 + HEADER_H / 2,
        type: 'output', edgeAddable: true, connectable: true,
        _portName: FLOW_PORT, _edgeKind: 'control',
      },
    ]
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

    if (hasCollapsedInputToggle) cursorY += COLLAPSED_INPUT_TOGGLE_H
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

  /**
   * 连线即数据绑定，只允许 output -> input 的直接类型赋值。
   * object 到字段、数组元素等转换必须显式使用路径或转换节点，不能静默猜测。
   */
  isAllowConnectedAsSource(target: any, sourceAnchor?: any, targetAnchor?: any, edgeId?: string) {
    const inherited = super.isAllowConnectedAsSource(target, sourceAnchor, targetAnchor, edgeId)
    if (!inherited.isAllPass) return inherited
    if (sourceAnchor?.type !== 'output' || targetAnchor?.type !== 'input') {
      return { isAllPass: false, msg: '请从输出端口连接到输入端口' }
    }
    const isFlowSource = sourceAnchor?._portName === FLOW_PORT
    const isFlowTarget = targetAnchor?._portName === FLOW_PORT
    if (isFlowSource || isFlowTarget) {
      return isFlowSource && isFlowTarget
        ? { isAllPass: true }
        : { isAllPass: false, msg: '这是流程端口。请连接到目标节点标题栏左侧的流程入口；参数请连接到具体字段。' }
    }
    const sourceOutputs = (this.properties?.outputs || []) as PortDef[]
    const targetInputs = (target?.properties?.inputs || []) as PortDef[]
    const sourcePort = sourceOutputs.find(port => port.name === sourceAnchor._portName)
    const targetPort = targetInputs.find(port => port.name === targetAnchor._portName)
    if (!sourcePort || !targetPort) return { isAllPass: false, msg: '端口信息不完整，无法建立数据绑定' }
    const isCollectionToItem = portCardinality(sourcePort) === 'collection'
      && portCardinality(targetPort) === 'single'
    if (!isCollectionToItem && !isDirectlyAssignable(sourcePort.valueType, targetPort.valueType)) {
      return { isAllPass: false, msg: `类型不兼容：${sourcePort.valueType} 不能直接连接到 ${targetPort.valueType}` }
    }
    if (!isCollectionToItem && portCardinality(sourcePort) !== portCardinality(targetPort)) {
      return { isAllPass: false, msg: portCardinality(sourcePort) === 'collection'
        ? '列表不能直接连接到单项。请插入“批处理”节点。'
        : '单条数据不能直接连接到列表端口。' }
    }
    if (sourcePort.semanticType && targetPort.semanticType && sourcePort.semanticType !== targetPort.semanticType) {
      return { isAllPass: false, msg: `业务对象不兼容：${sourcePort.semanticType} 不能连接到 ${targetPort.semanticType}` }
    }
    return inherited
  }

  /** 禁用默认外框（由 NodeCard 自己的 border 替代） */
  getOutlineStyle() {
    const style = super.getOutlineStyle()
    style.stroke = 'none'
    if (style.hover) style.hover.stroke = 'none'
    return style
  }
}

function isDirectlyAssignable(source?: string, target?: string) {
  return !source || !target || source === target || source === 'any' || target === 'any'
}

function portCardinality(port: PortDef) {
  return port.cardinality || (port.valueType === 'array' ? 'collection' : 'single')
}
