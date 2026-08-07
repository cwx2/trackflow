/**
 * 数字徽章字段配置类型定义
 * 用于工单列表中以数字徽章形式展示整数类型自定义字段的值
 */

/** 徽章颜色规则 */
export interface BadgeColorRule {
  /** 数值上限（包含），匹配逻辑: value <= max */
  max?: number
  /** 徽章背景色（HEX 格式） */
  color: string
}

/** 数字徽章字段配置（由父组件从项目自定义字段定义中提取） */
export interface BadgeFieldConfig {
  fieldId: string
  fieldName: string
  colorRules?: BadgeColorRule[] | null
}
