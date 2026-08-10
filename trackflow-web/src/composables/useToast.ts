import { ref } from 'vue'

export type ToastType = 'info' | 'success' | 'warning' | 'error'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
  duration: number        // ms，0 = 不自动消失
  onClick?: () => void    // 点击整个 toast 时的回调
}

let _idCounter = 0

// 模块级单例，所有组件共享同一队列
const toasts = ref<ToastItem[]>([])

export function useToast() {
  function show(
    message: string,
    options: {
      type?: ToastType
      duration?: number
      onClick?: () => void
    } = {}
  ): number {
    const id = ++_idCounter
    toasts.value.push({
      id,
      type: options.type ?? 'info',
      message,
      duration: options.duration ?? 5000,
      onClick: options.onClick
    })
    return id
  }

  function dismiss(id: number) {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx !== -1) toasts.value.splice(idx, 1)
  }

  function dismissAll() {
    toasts.value = []
  }

  // 语法糖
  const info    = (msg: string, opts?: Omit<Parameters<typeof show>[1], 'type'>) => show(msg, { ...opts, type: 'info' })
  const success = (msg: string, opts?: Omit<Parameters<typeof show>[1], 'type'>) => show(msg, { ...opts, type: 'success' })
  const warning = (msg: string, opts?: Omit<Parameters<typeof show>[1], 'type'>) => show(msg, { ...opts, type: 'warning' })
  const error   = (msg: string, opts?: Omit<Parameters<typeof show>[1], 'type'>) => show(msg, { ...opts, type: 'error' })

  return { toasts, show, dismiss, dismissAll, info, success, warning, error }
}
