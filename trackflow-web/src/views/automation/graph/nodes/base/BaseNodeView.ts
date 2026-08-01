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
    return {
      nodeId:     model.id,
      properties: model.getProperties?.() ?? model.properties ?? {},
      // 通过 onXxx 传回调，让 Vue 组件内的操作能通知 LogicFlow
      onToggleExpand: () => {
        const cur = model.getProperties?.()?.expanded ?? false
        model.setProperty('expanded', !cur)
      },
      onNodeClick: () => {
        // 触发外部点击事件（打开右侧配置面板）
        model.graphModel?.eventCenter?.emit('node:click', {
          data: { ...model.getData(), _openPanel: true }
        })
      },
    }
  }

  constructor(props: any) {
    super(props)

    const model = props?.model
    const initProps = this.getInitialProps(model)

    // 用 h() 持有 vnode 引用，后续直接改 component.props
    this._vnode = h(this.getVueComponent(), initProps)
    this._app   = createApp({ render: () => this._vnode! })
  }

  getText(): null {
    return null
  }

  setHtml(rootEl: SVGForeignObjectElement) {
    if (!this._mounted) {
      // 首次：挂载
      this._mounted = true
      const container = document.createElement('div')
      container.style.cssText = 'width:100%;height:100%;'
      rootEl.appendChild(container)
      this._app!.mount(container)
    } else {
      // 后续：直接更新 props，不重建 app
      const model = (this.props as any)?.model
      if (model && this._vnode?.component) {
        const newProps = this.getInitialProps(model)
        Object.assign(this._vnode.component.props, newProps)
      }
    }
  }

  /** 组件卸载时清理 */
  destroy?() {
    this._app?.unmount()
    this._app   = null
    this._vnode = null
    this._mounted = false
  }
}
