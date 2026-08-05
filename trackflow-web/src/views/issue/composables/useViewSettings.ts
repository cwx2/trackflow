import { ref, computed, watch } from 'vue'

/**
 * 视图布局类型
 * - table: 表格视图（每列一个字段，支持列排序和列配置）
 * - list: 列表视图（卡片式展示，支持 S/M/L 密度）
 */
export type LayoutMode = 'table' | 'list'

/**
 * 信息密度（仅 List 模式下有效）
 * - S: 单行 — 优先级图标、编号、标题、标签、附件/评论计数
 * - M: 紧凑 — S + 第二行显示自定义字段值和报告人
 * - L: 详细 — M + 显示描述前 2-3 行预览
 */
export type DensityLevel = 'S' | 'M' | 'L'

/**
 * 结构模式
 * - flat: 平铺列表
 * - tree: 层级树形（展示父子工单嵌套关系）
 */
export type StructureMode = 'flat' | 'tree'

const STORAGE_KEY = 'trackflow:view-settings'

interface ViewSettingsState {
  layout: LayoutMode
  density: DensityLevel
  structure: StructureMode
}

const DEFAULT_SETTINGS: ViewSettingsState = {
  layout: 'table',
  density: 'S',
  structure: 'flat'
}

/**
 * 视图设置 composable
 * 管理工单列表的布局、密度和结构模式，持久化到 localStorage
 */
export function useViewSettings() {
  const stored = loadFromStorage()

  const layout = ref<LayoutMode>(stored.layout)
  const density = ref<DensityLevel>(stored.density)
  const structure = ref<StructureMode>(stored.structure)

  // 是否是树形模式
  const isTreeMode = computed(() => structure.value === 'tree')

  // 是否是列表布局
  const isListLayout = computed(() => layout.value === 'list')

  // 是否是表格布局
  const isTableLayout = computed(() => layout.value === 'table')

  function setLayout(mode: LayoutMode) {
    layout.value = mode
  }

  function setDensity(level: DensityLevel) {
    density.value = level
  }

  function setStructure(mode: StructureMode) {
    structure.value = mode
  }

  // 持久化
  watch([layout, density, structure], () => {
    const state: ViewSettingsState = {
      layout: layout.value,
      density: density.value,
      structure: structure.value
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  })

  return {
    layout,
    density,
    structure,
    isTreeMode,
    isListLayout,
    isTableLayout,
    setLayout,
    setDensity,
    setStructure
  }
}

function loadFromStorage(): ViewSettingsState {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const parsed = JSON.parse(stored)
      return {
        layout: parsed.layout || DEFAULT_SETTINGS.layout,
        density: parsed.density || DEFAULT_SETTINGS.density,
        structure: parsed.structure || DEFAULT_SETTINGS.structure
      }
    }
  } catch { /* JSON 解析容错，降级为默认视图设置 */ }
  return { ...DEFAULT_SETTINGS }
}
