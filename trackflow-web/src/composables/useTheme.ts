import { ref, watch } from 'vue'

export type ThemeMode = 'dark' | 'light' | 'green'

const STORAGE_KEY = 'tf_theme'

const currentTheme = ref<ThemeMode>(getInitialTheme())

function getInitialTheme(): ThemeMode {
  const saved = localStorage.getItem(STORAGE_KEY) as ThemeMode | null
  if (saved && ['dark', 'light', 'green'].includes(saved)) return saved
  return 'dark'
}

function applyTheme(theme: ThemeMode) {
  // 设置 CSS 变量主题
  document.documentElement.setAttribute('data-theme', theme)
  // 同步 Arco Design 主题
  if (theme === 'light') {
    document.body.removeAttribute('arco-theme')
  } else {
    document.body.setAttribute('arco-theme', 'dark')
  }
  localStorage.setItem(STORAGE_KEY, theme)
}

// 立即初始化
applyTheme(currentTheme.value)

watch(currentTheme, (val) => applyTheme(val))

export function useTheme() {
  function setTheme(theme: ThemeMode) {
    currentTheme.value = theme
  }

  function cycleTheme() {
    const order: ThemeMode[] = ['dark', 'light', 'green']
    const idx = order.indexOf(currentTheme.value)
    currentTheme.value = order[(idx + 1) % order.length]
  }

  return {
    theme: currentTheme,
    setTheme,
    cycleTheme
  }
}
