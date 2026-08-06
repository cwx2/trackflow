import type { Component } from 'vue'

/**
 * Widget 配置表单字段定义
 *
 * 每个字段描述一个 Widget 配置项的类型、校验规则和 UI 属性。
 * 由 WidgetConfigForm.vue 通用渲染器消费。
 *
 * `key` 直接对应 config JSON 中的属性名，实现通用 load/save 无需逐类型映射。
 */
export interface WidgetConfigField {
  /** 字段 key，直接对应 config JSON 中的属性名（load/save 通用） */
  key: string
  /** 显示标签 */
  label: string
  /** 字段类型 */
  type:
    | 'text'
    | 'textarea'
    | 'select'
    | 'project-select'
    | 'report-select'
    | 'sprint-select'
    | 'user-select'
    | 'number'
    | 'boolean'
    | 'query-input'
    | 'multi-select'
    | 'multi-project-select'
    | 'multi-user-select'
  /** 是否必填 */
  required?: boolean
  /** 默认值 */
  defaultValue?: unknown
  /** select 类型的静态选项 */
  options?: Array<{ label: string; value: string | number }>
  /** 提示文字 */
  placeholder?: string
  /** 帮助说明（显示在字段下方） */
  hint?: string
  /** 最小值（number 类型） */
  min?: number
  /** 最大值（number 类型） */
  max?: number
  /** 条件显示：仅当指定字段等于某值时才显示本字段 */
  showWhen?: { field: string; value: unknown }
  /** 多选时最大标签显示数 */
  maxTagCount?: number
  /**
   * 标记此字段存储在 Widget 实体的独立列而非 config JSON 中。
   * 如 reportId 存储在 DashboardWidget.reportId 列。
   * 设为 true 时，save 逻辑将把值放在 update payload 顶层而非 config 对象内。
   */
  topLevelField?: boolean
}

/**
 * Widget 需要的动态数据源类型
 *
 * 声明后，编辑配置弹窗会自动加载对应数据传递给 WidgetConfigForm。
 */
export type WidgetDataSource = 'projects' | 'reports' | 'sprints' | 'users'

/**
 * Widget 完整定义
 *
 * 每个 Widget 通过此接口自描述其元信息、配置 Schema 和渲染组件。
 * 主程序通过注册表发现所有 Widget，无需 hardcode 映射。
 */
export interface WidgetDefinition {
  /** 唯一类型标识，英文小写 + 下划线，如 'issue_list' */
  type: string
  /** 显示名称 */
  label: string
  /** Emoji 图标 */
  icon: string
  /** 简短描述，显示在添加 Widget 面板 */
  description: string
  /** 默认标题 */
  defaultTitle: string
  /** 分组，用于添加面板的分类显示 */
  group: 'basic' | 'report' | 'agile' | 'custom'
  /** 分组显示名称（中文） */
  groupLabel?: string
  /** 默认宽度（1-12，基于 12 列网格） */
  defaultWidth?: number
  /** 默认高度（行数） */
  defaultHeight?: number
  /** 配置表单字段定义 */
  configSchema: WidgetConfigField[]
  /**
   * 此 Widget 需要加载的动态数据源列表。
   * 编辑配置弹窗根据此声明自动加载数据。
   */
  dataSources?: WidgetDataSource[]
  /** 渲染组件 */
  component: Component | (() => Promise<{ default: Component }>)
}
