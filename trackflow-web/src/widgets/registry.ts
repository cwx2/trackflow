import type { WidgetDefinition } from './types'

/**
 * Widget 注册表
 *
 * 插件化体系核心：所有 Widget 通过注册表统一管理。
 * 主程序通过注册表发现 Widget，不再 hardcode 映射表。
 */
const registry = new Map<string, WidgetDefinition>()

/** 注册一个 Widget 定义 */
export function registerWidget(def: WidgetDefinition): void {
  if (registry.has(def.type)) {
    console.warn(`[Widget Registry] 类型 "${def.type}" 已注册，将被覆盖`)
  }
  registry.set(def.type, def)
}

/** 辅助函数：定义并立即注册一个 Widget */
export function defineWidget(def: WidgetDefinition): WidgetDefinition {
  registerWidget(def)
  return def
}

/** 获取所有已注册的 Widget 定义（有序列表） */
export function getAllWidgets(): WidgetDefinition[] {
  return Array.from(registry.values())
}

/** 根据类型获取 Widget 定义 */
export function getWidget(type: string): WidgetDefinition | undefined {
  return registry.get(type)
}

/** 按分组获取 Widget */
export function getWidgetsByGroup(): Record<string, WidgetDefinition[]> {
  const result: Record<string, WidgetDefinition[]> = {}
  for (const def of registry.values()) {
    if (!result[def.group]) result[def.group] = []
    result[def.group].push(def)
  }
  return result
}

/** 判断类型是否已注册 */
export function isWidgetRegistered(type: string): boolean {
  return registry.has(type)
}
