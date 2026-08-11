/**
 * useModalEscapeGuard — 全局 Escape 键层级管理器
 *
 * 解决两个问题：
 * 1. Arco Modal 内嵌套的 DatePicker/Select 弹层打开时按 Escape 会同时关闭弹层和 Modal
 *    → 修复：检测到 Trigger 弹层时，阻止 Modal handler 并只关闭弹层
 * 2. Arco Modal 的 escToClose 机制在 open-close-reopen 周期后可能不可靠
 *    → 修复：通过注册制管理 Modal 的 Escape 行为，不依赖 Arco 内部的 keydown 注册机制
 *
 * 架构说明：
 * - 在 document.documentElement 的 bubble 阶段注册 keydown 监听器（应用启动时注册）
 * - 使用 stopImmediatePropagation() 完全接管 Escape 键，阻止 Arco 的 handleGlobalKeyDown
 * - 通过 registerModalEscapeHandler / useModalEscapeHandler 让各 Modal 注册自己的关闭逻辑
 * - 层级逻辑：先关闭 Trigger 弹层，再关闭最后注册的 Modal
 *
 * 使用方式：
 * - main.ts 中调用 installModalEscapeGuard() 全局生效
 * - 各 Modal 组件中使用 useModalEscapeHandler(visible, handler) 注册关闭逻辑
 */

import { watch, onBeforeUnmount, type Ref } from 'vue'

let installed = false

// ===== Handler 注册表 =====

/**
 * Modal escape handler 栈。
 * 栈顶（最后注册的）最先响应 Escape。
 * handler 返回值：
 * - true: 已处理（Modal 关闭或弹出确认），guard 阻止事件传播
 * - false: 未处理，交给栈中下一个 handler
 */
const modalHandlerStack: Array<{ id: symbol; handler: () => boolean }> = []

/**
 * 注册一个 Modal 的 Escape 处理器。返回注销函数。
 */
export function registerModalEscapeHandler(handler: () => boolean): () => void {
  const id = Symbol()
  modalHandlerStack.push({ id, handler })
  return () => {
    const idx = modalHandlerStack.findIndex(entry => entry.id === id)
    if (idx !== -1) modalHandlerStack.splice(idx, 1)
  }
}

// ===== Trigger 弹层检测 =====

/**
 * 检测当前是否有 Arco Trigger 弹层处于可见状态。
 * Arco Trigger 弹层以 .arco-trigger-popup 类名存在于 DOM，
 * 可见时有实际尺寸且 display/visibility 正常。
 */
function hasVisibleTriggerPopup(): boolean {
  const popups = document.querySelectorAll('.arco-trigger-popup')
  for (const popup of popups) {
    const el = popup as HTMLElement
    // 必须同时有宽度和高度才算可见。
    // Arco Trigger 的 popup 容器在关闭后仍保留在 DOM 中，
    // 外层 .arco-trigger-popup 可能有 offsetWidth（由 min-width 设置），
    // 但 offsetHeight 为 0（内容区 display:none 导致高度塌陷）。
    if (el.offsetWidth > 0 && el.offsetHeight > 0) {
      const style = window.getComputedStyle(el)
      if (style.display !== 'none' && style.visibility !== 'hidden') {
        return true
      }
    }
  }
  return false
}

/**
 * 通过模拟 mousedown 事件关闭 Trigger 弹层。
 * Arco Trigger 使用 clickOutside（监听 document.documentElement 上的 mousedown）
 * 来关闭弹层。模拟一个 target 为 document.body 的 mousedown 即可触发其关闭逻辑。
 */
function closeTriggerPopups(): void {
  const event = new MouseEvent('mousedown', {
    bubbles: true,
    cancelable: true,
    view: window
  })
  document.body.dispatchEvent(event)
}

// ===== Guard 安装 =====

/**
 * 安装全局 Escape 键层级管理器。
 *
 * 注册位置：document.documentElement，bubble 阶段
 * 注册时机：应用启动时（早于任何 Modal 组件的动态注册）
 *
 * 执行逻辑：
 * 1. 如果有可见 Trigger 弹层 → 关闭弹层，阻止后续 handler
 * 2. 如果没有弹层但有注册的 Modal handler → 调用最顶层 handler
 * 3. 以上都没有 → 不做任何事（让事件自然传播给 Arco 默认机制）
 */
export function installModalEscapeGuard(): void {
  if (installed) return
  installed = true

  document.documentElement.addEventListener('keydown', (e: KeyboardEvent) => {
    if (e.key !== 'Escape') return

    // 层级 1：关闭 Trigger 弹层（DatePicker、Select、Dropdown 等）
    if (hasVisibleTriggerPopup()) {
      e.stopImmediatePropagation()
      closeTriggerPopups()
      return
    }

    // 层级 2：调用注册的 Modal escape handler（栈顶优先）
    if (modalHandlerStack.length > 0) {
      for (let i = modalHandlerStack.length - 1; i >= 0; i--) {
        const { handler } = modalHandlerStack[i]
        const handled = handler()
        if (handled) {
          // handler 已处理（关闭了 Modal 或弹出了确认），阻止 Arco 的 handler
          e.stopImmediatePropagation()
          return
        }
      }
    }

    // 层级 3：没有注册的 handler，让事件自然传播
    // （Arco Modal 的默认 escToClose 机制仍可生效，适用于未注册 handler 的普通 Modal）
  }, false)
}

// ===== Vue Composable =====

/**
 * Vue composable：在 Modal visible 期间注册 Escape handler，invisible 时自动注销。
 *
 * 用法示例：
 * ```ts
 * const showModal = ref(false)
 *
 * useModalEscapeHandler(showModal, () => {
 *   if (!isDirty()) {
 *     showModal.value = false
 *     return true // 已处理：直接关闭
 *   }
 *   Modal.confirm({ onOk: () => { showModal.value = false } })
 *   return true // 已处理：弹出确认
 * })
 * ```
 *
 * 注意：使用此 composable 的 Modal 应设置 :esc-to-close="false"，
 * 避免 Arco 的默认 Escape handler 与我们的 handler 双重触发。
 *
 * @param visible - Modal 的 visible ref
 * @param handler - Escape 处理器，返回 true 表示已处理
 */
export function useModalEscapeHandler(
  visible: Ref<boolean> | { value: boolean },
  handler: () => boolean
): void {
  let unregister: (() => void) | null = null

  watch(
    () => visible.value,
    (isVisible: boolean) => {
      if (isVisible) {
        unregister = registerModalEscapeHandler(handler)
      } else {
        unregister?.()
        unregister = null
      }
    },
    { immediate: true }
  )

  onBeforeUnmount(() => {
    unregister?.()
    unregister = null
  })
}
