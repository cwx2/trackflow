import type { Directive, DirectiveBinding } from 'vue'
import { watchEffect } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * v-permission 指令
 *
 * 用法：
 *   v-permission="'project:create'"        — 需要指定全局权限
 *   v-permission="['issue:create', 'issue:edit']"  — 需要任一权限（OR）
 *
 * 如果用户没有对应权限，元素会被隐藏（display: none）。
 * 如果需要完全不渲染（v-if 效果），请使用 computed + v-if。
 *
 * 本指令使用 watchEffect 监听 store 变化，权限异步加载完成后自动更新可见性。
 */
export const vPermission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const authStore = useAuthStore()

    const stop = watchEffect(() => {
      const permissions = binding.value
      if (!permissions) return

      const permList = Array.isArray(permissions) ? permissions : [permissions]
      const hasPermission = permList.some(p => authStore.hasGlobalPermission(p))

      if (!hasPermission) {
        el.dataset.originalDisplay = el.style.display
        el.style.display = 'none'
      } else {
        el.style.display = el.dataset.originalDisplay || ''
      }
    })

    ;(el as any).__permissionCleanup = stop
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    // 当指令值变化时重新检查权限
    const authStore = useAuthStore()
    const permissions = binding.value
    if (!permissions) return

    const permList = Array.isArray(permissions) ? permissions : [permissions]
    const hasPermission = permList.some(p => authStore.hasGlobalPermission(p))

    if (!hasPermission) {
      el.dataset.originalDisplay = el.style.display
      el.style.display = 'none'
    } else {
      el.style.display = el.dataset.originalDisplay || ''
    }
  },
  unmounted(el: HTMLElement) {
    ;(el as any).__permissionCleanup?.()
  }
}

export default vPermission
