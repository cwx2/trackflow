import { ref, onMounted, onUnmounted } from 'vue'
import type { Ref } from 'vue'
import type { CardSize } from './useKanbanBoard'

/**
 * 看板全屏/TV 模式逻辑。
 *
 * TV 模式进入全屏并自动切换卡片尺寸为 XL，退出时恢复。
 */
export function useBoardFullscreen(cardSize: Ref<CardSize>, cardSizeStorageKey: string) {
  const isTvMode = ref(false)
  const TV_MODE_KEY = 'tf_kanban_tv_mode'

  function toggleTvMode() {
    if (!isTvMode.value) {
      // 进入 TV 模式
      const el = document.documentElement
      if (el.requestFullscreen) {
        el.requestFullscreen().then(() => {
          isTvMode.value = true
          cardSize.value = 'XL'
          localStorage.setItem(TV_MODE_KEY, 'true')
        }).catch(() => {
          // Fullscreen denied — fallback to just large card mode
          isTvMode.value = true
          cardSize.value = 'XL'
        })
      } else {
        isTvMode.value = true
        cardSize.value = 'XL'
      }
    } else {
      // 退出 TV 模式
      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => { /* ignore */ })
      }
      isTvMode.value = false
      // 恢复之前的卡片尺寸
      const saved = localStorage.getItem(cardSizeStorageKey) as CardSize
      cardSize.value = saved || 'M'
      localStorage.removeItem(TV_MODE_KEY)
    }
  }

  // 监听全屏退出事件（用户按 Esc 退出）
  function onFullscreenChange() {
    if (!document.fullscreenElement && isTvMode.value) {
      isTvMode.value = false
      const saved = localStorage.getItem(cardSizeStorageKey) as CardSize
      cardSize.value = saved || 'M'
      localStorage.removeItem(TV_MODE_KEY)
    }
  }

  onMounted(() => {
    document.addEventListener('fullscreenchange', onFullscreenChange)
  })
  onUnmounted(() => {
    document.removeEventListener('fullscreenchange', onFullscreenChange)
  })

  return {
    isTvMode,
    toggleTvMode
  }
}
