/**
 * 自定义字段类型常量
 *
 * field_format 是系统枚举，与后端 CustomFieldTypeHandler 一一对应，不可由用户配置。
 * 每个类型对应后端一个 Handler（BoolFieldHandler、ListFieldHandler 等）。
 *
 * 注意：state 和 ownedField 是系统内置字段专用类型，前端创建字段时不暴露这两个选项，
 * 但 formatFieldType() 支持展示它们（用于已有内置字段的类型标签显示）。
 */

export interface FieldTypeOption {
  value: string
  label: string
}

/** 用户可创建的字段类型（不含系统内置专用类型） */
export const FIELD_TYPE_OPTIONS: FieldTypeOption[] = [
  { value: 'string',   label: '文本(单行)' },
  { value: 'text',     label: '文本(多行/Markdown)' },
  { value: 'int',      label: '整数' },
  { value: 'float',    label: '小数' },
  { value: 'date',     label: '日期' },
  { value: 'datetime', label: '日期时间' },
  { value: 'bool',     label: '布尔' },
  { value: 'list',     label: '列表(枚举)' },
  { value: 'user',     label: '用户' },
  { value: 'period',   label: '时间周期' },
  { value: 'version',  label: '版本(Version)' },
  { value: 'build',    label: '构建号(Build)' },
  { value: 'group',    label: '用户组(Group)' },
]

/** 所有字段类型的展示标签映射（含系统内置专用类型，用于标签展示） */
const FIELD_TYPE_LABEL_MAP: Record<string, string> = {
  ...Object.fromEntries(FIELD_TYPE_OPTIONS.map(o => [o.value, o.label])),
  state:      '状态(State)',
  ownedField: '子系统(Owned Field)',
}

/**
 * 将 field_format 转换为中文展示标签
 * @param format 字段格式（如 'list'、'string'）
 * @returns 中文标签（如 '列表(枚举)'），未匹配时原样返回
 */
export function formatFieldType(format?: string | null): string {
  if (!format) return '未知'
  return FIELD_TYPE_LABEL_MAP[format] || format
}
