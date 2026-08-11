/**
 * useModalEscapeGuard — 修复 Arco Design Modal 中 Escape 键层级关闭问题
 *
 * 问题：Arco Design 的 Modal 在 document.documentElement 上以 bubble 阶段监听 keydown，
 * 通过 `isLastDialog()` 判断是否响应 Escape。但 `isLastDialog()` 只检查 dialog 堆栈，
 * 不感知 Trigger popup 堆栈。因此当 Modal 内的 DatePicker/Select/Dropdown 弹层打开时，
 * 按 Escape 会同时关闭弹层和 Modal。
 *
 * 解决方案：在 document.documentElement 上以 bubble 阶段注册 keydown 监听器（应用启动时
 * 注册，早于任何 Modal 组件注册）。当检测到有 Arco Trigger 弹层可见时，调用
 * stopImmediatePropagation() 阻止后续注册的 Modal keydown handler 执行。
 *
 * 对于有自己 Escape 处理的组件（如 Select），它们在目标元素的 onKeydown 中处理 Escape，
 * 在事件到达 documentElement 之前就已经执行了（bubble 阶段从内到外）。
 * 对于没有 Escape 处理的组件（如 DatePicker），本 guard 通过模拟 mousedown 触发
 * Trigger 的 clickOutside 逻辑来关闭弹层。
 *
 * 效果：Escape 键逐层关闭 — 先关闭 DatePicker/Select 弹层，再关闭 Modal。
 *
 * 使用方式：在 main.ts 中调用 `installModalEscapeGuard()` 即可全局生效。
 */

let installed = false

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
    // 使用 AND 确保只有真正展开的弹层才被判定为可见。
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
  // dispatch 到 document.body，确保不在任何 popup/trigger 元素内
  document.body.dispatchEvent(event)
}

/**
 * 安装全局 Escape 键层级管理器。
 *
 * 注册位置：document.documentElement，bubble 阶段
 * 注册时机：应用启动时（早于任何 Modal 组件的动态注册）
 *
 * 执行逻辑：
 * 1. 检测是否有可见的 Trigger 弹层
 * 2. 如果有，调用 stopImmediatePropagation 阻止 Modal handler 执行
 * 3. 模拟 mousedown 关闭弹层（针对没有自带 Escape 处理的组件如 DatePicker）
 */
export function installModalEscapeGuard(): void {
  if (installed) return
  installed = true

  document.documentElement.addEventListener('keydown', (e: KeyboardEvent) => {
    if (e.key !== 'Escape') return

    if (hasVisibleTriggerPopup()) {
      // 阻止后续注册的 keydown 监听器（即 Modal 的 handleGlobalKeyDown）
      e.stopImmediatePropagation()
      // 关闭 Trigger 弹层（对于没有自带 Escape 处理的组件如 DatePicker）
      // 注：Select 组件在其自身的 onKeydown（目标元素 bubble 阶段）中已关闭 popup，
      // 此时 DOM 中可能已无可见 popup，closeTriggerPopups() 不会造成问题
      closeTriggerPopups()
    }
  }, false) // false = bubble phase（与 Modal 相同，但注册更早所以先执行）
}
