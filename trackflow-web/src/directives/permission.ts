import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * v-permission 指令
 *
 * 用法：
 *   v-permission="'project:create'"        — 需要指定全局权限
 *   v-permission="['issue:create', 'issue:edit']"  — 需要任一权限（OR）
 *
 * 如果用户没有对应权限，元素会被隐藏（display: none）。
 * 如果需要完全不渲染（v-if 效果），请继续使用 computed + v-if。
 */
export const vPermission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  }
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const authStore = useAuthStore()
  const permissions = binding.value

  if (!permissions) return

  const permList = Array.isArray(permissions) ? permissions : [permissions]
  const hasPermission = permList.some(p => authStore.hasGlobalPermission(p))

  if (!hasPermission) {
    el.style.display = 'none'
  } else {
    el.style.display = ''
  }
}

export default vPermission
