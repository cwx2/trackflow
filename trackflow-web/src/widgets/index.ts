/**
 * Widget 插件体系入口
 *
 * 导出注册表 API 和类型定义，供主程序和第三方 Widget 使用。
 */
export { registerWidget, defineWidget, getAllWidgets, getWidget, getWidgetsByGroup, isWidgetRegistered } from './registry'
export type { WidgetDefinition, WidgetConfigField, WidgetDataSource } from './types'
export { default as WidgetConfigForm } from './WidgetConfigForm.vue'
