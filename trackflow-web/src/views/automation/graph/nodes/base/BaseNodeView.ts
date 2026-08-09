/**
 * BaseNodeView — 所有工作流节点的 View 基类
 *
 * 采用 LogicFlow 官方推荐的 createApp + h() 模式：
 * - 构造函数里创建 Vue app 和 vnode
 * - 第一次 setHtml 时 mount 到 DOM
 * - 后续 setHtml 只更新 vnode.component.props（零重建开销）
 *
 * 子类可覆盖 getVueComponent() 返回不同的 Vue 组件（默认 NodeCard）
 */
import { HtmlNode } from '@logicflow/core'
import { createApp, h, type Component, type App, type VNode } from 'vue'
import NodeCard from './NodeCard.vue'

export abstract class BaseNodeView extends HtmlNode {
  protected _app:     App | null = null
  protected _vnode:   VNode | null = null
  protected _mounted: boolean = false

  /** 子类可覆盖，使用不同的卡片组件 */
  getVueComponent(): Component {
    return NodeCard
  }

  /** 提取传给 Vue 组件的初始 props */
  getInitialProps(model: any): Record<string, any> {
    const rawProps = model.getProperties?.() ?? model.properties ?? {}
    return {
      nodeId:     model.id,
      properties: { ...rawProps },
      onSetProperty: (key: string, val: any) => {
        model.setProperty(key, val)
      },
      onNodeClick: () => {
        model.graphModel?.toFront(model.id)
        model.graphModel?.eventCenter?.emit('node:click', {
          data: { ...model.getData(), _openPanel: true },
        })
      },
      onRunNode: () => {
        model.graphModel?.eventCenter?.emit('node:test', {
          data: { ...model.getData() },
        })
      },
    }
  }

  constructor(props: any) {
    super(props)
    const model = props?.model
    const initProps = this.getInitialProps(model)
    this._vnode = h(this.getVueComponent(), initProps)
    this._app   = createApp({ render: () => this._vnode! })
  }

  getText(): null {
    return null
  }

  setHtml(rootEl: SVGForeignObjectElement) {
    if (!this._mounted) {
      this._mounted = true
      rootEl.style.overflow = 'visible'
      const container = document.createElement('div')
      container.style.cssText = 'width:100%;height:fit-content;overflow:visible;'
      rootEl.appendChild(container)
      this._app!.mount(container)
    } else {
      const model = (this.props as any)?.model
      if (model && this._vnode?.component) {
        const newProps = this.getInitialProps(model)
        Object.assign(this._vnode.component.props, newProps)
      }
    }
  }

  destroy?() {
    this._app?.unmount()
    this._app   = null
    this._vnode = null
    this._mounted = false
  }
}
