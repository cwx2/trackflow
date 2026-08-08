/**
 * graph/nodes — 节点类型注册文件
 *
 * Start / End / Comment / WorkflowNode
 * + registerAllNodes() 统一注册入口
 */
import { BaseNodeView } from './base/BaseNodeView'
import { BaseNodeModel } from './base/BaseNodeModel'
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

class TerminalNodeView extends BaseNodeView {
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
    return [{ id: `${this.id}-out-trigger`, x: this.x + 56, y: this.y, type: 'output', edgeAddable: true, connectable: true }]
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
    return [{ id: `${this.id}-in-result`, x: this.x - 56, y: this.y, type: 'input', edgeAddable: true, connectable: true }]
  }
  getOutlineStyle() {
    const s = super.getOutlineStyle()
    s.stroke = 'none'; if (s.hover) s.hover.stroke = 'none'
    return s
  }
}

export const EndNodeDef = { type: 'end', view: EndView, model: EndModel }

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
