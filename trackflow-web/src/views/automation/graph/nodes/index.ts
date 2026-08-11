/**
 * graph/nodes — 节点类型注册文件
 *
 * Start / End / Comment / WorkflowNode
 * + registerAllNodes() 统一注册入口
 */
import { BaseNodeView } from './base/BaseNodeView'
import { BaseNodeModel, FLOW_PORT } from './base/BaseNodeModel'
import { HtmlNode, HtmlNodeModel } from '@logicflow/core'
import TerminalNode from './base/TerminalNode.vue'

// ─── 通用工作流节点（使用 NodeCard.vue）─────────────────────────────────────

export class WorkflowNodeView extends BaseNodeView {
  get nodeType() { return 'workflow' }
}

export class WorkflowNodeModel extends BaseNodeModel {
  get nodeType() { return 'workflow' }
}

export const WorkflowNodeDef = {
  type: 'workflow-node',
  view: WorkflowNodeView,
  model: WorkflowNodeModel,
}

// ─── 开始 / 结束节点（紧凑的流程端点，与普通节点保持同一视觉语言） ─────────

abstract class TerminalNodeView extends BaseNodeView {
  abstract get nodeType(): 'start' | 'end'
  getVueComponent() { return TerminalNode }

  getInitialProps(model: any) {
    return {
      ...super.getInitialProps(model),
      variant: this.nodeType,
    }
  }
}

class StartView extends TerminalNodeView {
  get nodeType() { return 'start' as const }
}

class StartModel extends HtmlNodeModel {
  initNodeData(data: any) {
    super.initNodeData(data)
    this.width = 112; this.height = 64
    this.text = { value: '', x: 0, y: 0, draggable: false, editable: false }
  }
  getDefaultAnchor() {
    return [{ id: `${this.id}-out-${FLOW_PORT}`, x: this.x + 62, y: this.y, type: 'output', edgeAddable: true, connectable: true, _portName: FLOW_PORT, _edgeKind: 'control' }]
  }
  isAllowConnectedAsSource(target: any, sourceAnchor?: any, targetAnchor?: any, edgeId?: string) {
    const inherited = super.isAllowConnectedAsSource(target, sourceAnchor, targetAnchor, edgeId)
    if (!inherited.isAllPass) return inherited
    return validateDirectPortBinding(this, target, sourceAnchor, targetAnchor)
  }
  getOutlineStyle() {
    const s = super.getOutlineStyle()
    s.stroke = 'none'; if (s.hover) s.hover.stroke = 'none'
    return s
  }
}

export const StartNodeDef = { type: 'start', view: StartView, model: StartModel }

class EndView extends TerminalNodeView {
  get nodeType() { return 'end' as const }
}

class EndModel extends HtmlNodeModel {
  initNodeData(data: any) {
    super.initNodeData(data)
    this.width = 112; this.height = 64
    this.text = { value: '', x: 0, y: 0, draggable: false, editable: false }
  }
  getDefaultAnchor() {
    return [{ id: `${this.id}-in-${FLOW_PORT}`, x: this.x - 62, y: this.y, type: 'input', edgeAddable: true, connectable: true, _portName: FLOW_PORT, _edgeKind: 'control' }]
  }
  isAllowConnectedAsTarget(source: any, sourceAnchor?: any, targetAnchor?: any, edgeId?: string) {
    const inherited = super.isAllowConnectedAsTarget(source, sourceAnchor, targetAnchor, edgeId)
    if (!inherited.isAllPass) return inherited
    return validateDirectPortBinding(source, this, sourceAnchor, targetAnchor)
  }
  getOutlineStyle() {
    const s = super.getOutlineStyle()
    s.stroke = 'none'; if (s.hover) s.hover.stroke = 'none'
    return s
  }
}

export const EndNodeDef = { type: 'end', view: EndView, model: EndModel }

/** Start/End 与通用卡片走同一数据边规则，只是外观和尺寸不同。 */
function validateDirectPortBinding(source: any, target: any, sourceAnchor?: any, targetAnchor?: any) {
  if (sourceAnchor?.type !== 'output' || targetAnchor?.type !== 'input') {
    return { isAllPass: false, msg: '请从输出端口连接到输入端口' }
  }
  const isFlowSource = sourceAnchor?._portName === FLOW_PORT
  const isFlowTarget = targetAnchor?._portName === FLOW_PORT
  if (isFlowSource || isFlowTarget) {
    return isFlowSource && isFlowTarget
      ? { isAllPass: true }
      : { isAllPass: false, msg: '开始/结束连接的是流程线。请连接到节点标题栏左侧的流程入口，而不是参数端口。' }
  }
  const sourcePort = sourcePortInfo(source, sourceAnchor._portName, 'output')
  const targetPort = sourcePortInfo(target, targetAnchor._portName, 'input')
  if (!sourcePort || !targetPort) return { isAllPass: false, msg: '端口信息不完整，无法建立数据绑定' }
  if (sourcePort.valueType !== targetPort.valueType
    && sourcePort.valueType !== 'any' && targetPort.valueType !== 'any') {
    return { isAllPass: false, msg: `类型不兼容：${sourcePort.valueType} 不能直接连接到 ${targetPort.valueType}` }
  }
  if (portCardinality(sourcePort) !== portCardinality(targetPort)) {
    const isCollectionToItem = portCardinality(sourcePort) === 'collection'
      && portCardinality(targetPort) === 'single'
    return {
      isAllPass: false,
      msg: isCollectionToItem
        ? '列表不能直接连接到单项。请插入“批处理”节点，让每一项进入子工作流。'
        : '单条数据与列表端口不能直接连接，请检查数据形态。',
    }
  }
  if (sourcePort.semanticType && targetPort.semanticType
    && sourcePort.semanticType !== targetPort.semanticType) {
    return { isAllPass: false, msg: `业务对象不兼容：${sourcePort.semanticType} 不能连接到 ${targetPort.semanticType}` }
  }
  return { isAllPass: true }
}

function sourcePortInfo(node: any, name: string, direction: 'input' | 'output') {
  const definition = getNodeDefinition(node?.properties?.nodeType || node?.type)
  const ports = direction === 'output'
    ? node?.properties?.outputs || definition?.outputPorts || []
    : node?.properties?.inputs || definition?.inputPorts || []
  return ports.find((port: any) => port.name === name) as {
    name: string
    valueType: string
    cardinality?: 'single' | 'collection'
    semanticType?: string
  } | undefined
}

function portCardinality(port: { valueType: string, cardinality?: 'single' | 'collection' }) {
  return port.cardinality || (port.valueType === 'array' ? 'collection' : 'single')
}

// ─── Comment 注释节点 ─────────────────────────────────────────────────────────

class CommentView extends HtmlNode {
  private _mounted = false
  getText() { return null }

  setHtml(rootEl: SVGForeignObjectElement) {
    const model = (this as any).props?.model
    const text = model?.properties?.text || '在这里写注释...'

    if (!this._mounted) {
      this._mounted = true
      const el = document.createElement('div')
      el.style.cssText = 'width:200px;min-height:80px;'
      rootEl.appendChild(el)
    }

    const el = rootEl.firstElementChild as HTMLElement
    if (!el) return
    el.innerHTML = `
      <div style="width:200px;min-height:80px;background:var(--wf-node-bg);
        border:1.5px dashed var(--wf-port-out);border-radius:8px;padding:12px 14px;
        box-shadow:0 2px 8px rgba(0,0,0,0.2);">
        <div style="font-size:10px;color:var(--wf-port-out);font-weight:600;margin-bottom:6px;letter-spacing:0.5px;">
          &#128172; 注释
        </div>
        <div contenteditable="true"
          style="font-size:12px;color:var(--wf-node-title);line-height:1.6;
            min-height:36px;outline:none;white-space:pre-wrap;word-break:break-word;"
        >${text}</div>
      </div>`

    el.querySelector('[contenteditable]')?.addEventListener('blur', (e: any) => {
      model?.setProperty('text', e.target.innerText)
    })
  }
}

class CommentModel extends HtmlNodeModel {
  initNodeData(data: any) {
    super.initNodeData(data)
    this.width = 200; this.height = 100
    this.text = { value: '', x: 0, y: 0, draggable: false, editable: false }
  }
  getDefaultAnchor() { return [] }
  getOutlineStyle() {
    const s = super.getOutlineStyle()
    s.stroke = 'none'; if (s.hover) s.hover.stroke = 'none'
    return s
  }
}

export const CommentNodeDef = { type: 'comment', view: CommentView, model: CommentModel }

// ─── 统一注册函数 ─────────────────────────────────────────────────────────────

import type LogicFlow from '@logicflow/core'
import { getNodeDefinition, DRAGGABLE_NODES } from '../../node-definitions'

export function registerAllNodes(lf: LogicFlow) {
  lf.register(StartNodeDef)
  lf.register(EndNodeDef)
  lf.register(CommentNodeDef)

  // 业务节点：从注册表动态生成子类
  DRAGGABLE_NODES
    .filter(d => d.type !== 'start' && d.type !== 'end')
    .forEach(nodeDef => {
      const { type: nodeType } = nodeDef

      class DynView extends WorkflowNodeView {
        get nodeType() { return nodeType }
      }

      class DynModel extends WorkflowNodeModel {
        get nodeType() { return nodeType }

        initNodeData(data: any) {
          if (!data.properties) data.properties = {}
          // Ensure nodeType is always set (for backward-compat with older saved workflows)
          if (!data.properties.nodeType) data.properties.nodeType = nodeType
          const def = getNodeDefinition(nodeType)
          if (!data.properties.inputs)   data.properties.inputs   = def?.inputPorts  || []
          if (!data.properties.outputs)  data.properties.outputs  = def?.outputPorts || []
          if (!data.properties.nodeMeta) data.properties.nodeMeta = {}
          // 始终用节点定义补全缺失字段，兼容旧数据中某字段为空的情况
          const meta = data.properties.nodeMeta
          if (!meta.title)       meta.title       = def?.meta.title       || nodeType
          if (!meta.icon)        meta.icon        = def?.meta.icon        || '⬡'
          if (!meta.color)       meta.color       = def?.meta.color       || '#6366f1'
          if (meta.description === undefined) meta.description = def?.meta.description || ''
          super.initNodeData(data)
        }
      }

      lf.register({ type: nodeType, view: DynView, model: DynModel })
    })
}
