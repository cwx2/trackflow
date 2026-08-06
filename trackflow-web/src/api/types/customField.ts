/**
 * 自定义字段模块类型定义
 */

export interface CustomFieldDefinitionVO {
  id: string
  name: string
  fieldFormat: 'string' | 'text' | 'int' | 'float' | 'date' | 'datetime' | 'bool' | 'list' | 'user' | 'ownedField' | 'version' | 'group' | 'build' | 'state'
  isRequired: boolean
  isForAll: boolean
  isMulti: boolean
  /** 是否为内置字段（Priority / Type / Due Date），不可删除不可改名 */
  isBuiltIn?: boolean
  /** 是否在工单列表的默认列选择器中隐藏 */
  isHiddenInList: boolean
  /** 字段别名（逗号分隔），用于搜索时字段名匹配 */
  aliases?: string | null
  /** 是否为私有字段 */
  isPrivate: boolean
  /** 是否自动附加到新创建的项目（YouTrack Auto-attach 行为） */
  isAutoAttach?: boolean
  /** 选项排序模式: manual / name_asc / name_desc / name_ci_asc / name_ci_desc */
  sortMode?: string
  defaultValue?: string
  minLength: number
  maxLength: number
  regexp?: string
  position: number
  options?: CustomFieldOptionVO[]
  projectIds?: string[]
  issueTypes?: string[]
  createdAt: string
  updatedAt: string
  /** 条件源字段 ID（项目级配置，null 表示无条件始终显示） */
  conditionFieldId?: string | null
  /** 触发显示的选项 ID 列表 */
  conditionValues?: string[] | null
  /** 可以查看此字段的角色 ID 列表（null = 所有人可见） */
  visibleToRoles?: number[] | null
  /** 可以编辑此字段的角色 ID 列表（null = 所有可见用户可编辑） */
  updatableByRoles?: number[] | null
  /** 当前用户是否可编辑此字段（后端根据角色计算） */
  editable?: boolean
  /** 项目级必填性覆盖（null = 使用全局 isRequired） */
  projectIsRequired?: boolean | null
  /** 项目级默认值覆盖（null = 使用全局 defaultValue） */
  projectDefaultValue?: string | null
  /** 有效必填性（考虑项目覆盖后的实际值） */
  effectiveIsRequired?: boolean
  /** 有效默认值（考虑项目覆盖后的实际值） */
  effectiveDefaultValue?: string | null
  /**
   * 值依赖过滤 - 源字段 ID（项目级配置）。
   * 当此字段不为 null 时，表示该字段的可选值取决于 filterFieldId 对应字段的当前值。
   * 参考 YouTrack "Filter values based on" 功能。
   */
  filterFieldId?: string | null
  /**
   * 值依赖过滤规则（JSON 字符串，解析后为 FilterRule[] 数组）。
   * 每条规则描述：当源字段值为 whenValue 时，只显示 showOnly 中的选项 ID。
   */
  filterRules?: string | null
  /** 项目级"是否允许为空"覆盖（null = 默认可以为空） */
  projectCanBeEmpty?: boolean | null
  /** 有效"是否允许为空"（考虑项目覆盖后的实际值）*/
  effectiveCanBeEmpty?: boolean
  /**
   * 是否为"无默认值但必填"模式。
   * 当 effectiveCanBeEmpty=false 且 effectiveDefaultValue=null 时为 true。
   * 此模式下前端应显示 "Set value" 提示，且不自动预填默认选项。
   */
  requiresExplicitSelection?: boolean
}

/**
 * 值依赖过滤规则接口。
 * 当源字段值等于 whenValue 时，仅显示 showOnly 列表中的选项。
 */
export interface FilterRule {
  /** 源字段的选项值 ID */
  whenValue: string
  /** 允许显示的目标字段选项 ID 列表 */
  showOnly: string[]
}

export interface CustomFieldOptionVO {
  id: string
  customFieldId: string
  /** 所属项目 ID，null 表示全局共享选项 */
  projectId?: string | null
  value: string
  position: number
  isDefault: boolean
  isArchived?: boolean
  /** 选项颜色（HEX 格式如 #4CAF50），null 表示无颜色 */
  color?: string | null
  /** 选项描述，在下拉选择时以 tooltip 形式展示 */
  description?: string | null
  /** 仅 state 类型字段使用：标记该状态值是否视为"已解决" */
  isResolved?: boolean
  /** 选项负责人用户 ID（仅 ownedField 类型使用） */
  ownerUserId?: string | null
  /** 选项负责人显示名（后端填充） */
  ownerDisplayName?: string | null
  /** 版本发布日期（仅 version 类型使用） */
  releaseDate?: string | null
  /** 是否已正式发布（仅 version 类型使用） */
  isReleased?: boolean
  /** 构建生成日期（仅 build 类型使用） */
  assembleDate?: string | null
}

/** 选项集状态 VO */
export interface OptionSetStatusVO {
  /** 是否为项目独立选项集 */
  isIndependent: boolean
  /** 选项集类型：shared / independent */
  optionSetType: 'shared' | 'independent'
  /** 共享该选项集的项目数量 */
  sharedProjectCount: number
  /** 共享该选项集的项目名称列表 */
  sharedProjectNames: string[]
  /** 是否可以创建独立副本 */
  canMakeIndependent: boolean
  /** 不能创建独立副本的原因 */
  cannotMakeIndependentReason?: string | null
}

export interface CustomFieldUsageVO {
  issueCount: number
  valueCount: number
  projectCount: number
  isForAll: boolean
  issueTypeCount: number
  optionCount: number
  conditionRefCount: number
}

/** 单个选项的使用统计 */
export interface OptionUsageItemVO {
  optionId: string
  optionValue: string
  color: string | null
  issueCount: number
}

/** "Fields in Projects" 矩阵视图——按项目分组展示关联的自定义字段 */
export interface ProjectFieldsVO {
  projectId: string
  projectName: string
  projectKey: string
  fields: FieldSummaryVO[]
}

export interface FieldSummaryVO {
  id: string
  name: string
  fieldFormat: string
  isForAll: boolean
  isRequired: boolean
  isMulti: boolean
  /** 项目级必填性覆盖（null=继承全局） */
  projectIsRequired: boolean | null
  /** 项目级默认值覆盖（null=继承全局） */
  projectDefaultValue: string | null
  /** 项目级"是否允许为空"覆盖（null=继承全局，默认允许） */
  projectCanBeEmpty: boolean | null
  /** 字段在项目中的排序位置 */
  position: number | null
  /** 是否存在项目级覆盖 */
  hasOverride: boolean
}

export interface CustomFieldValueVO {
  customFieldId: string
  fieldName: string
  fieldFormat: string
  /** 单值字段的原始值，多值字段为逗号分隔（兼容） */
  value?: string
  /** 多值字段的原始值数组 */
  values?: string[]
  /** 单值字段的展示值 */
  displayValue?: string
  /** 多值字段的展示值数组 */
  displayValues?: string[]
  /** 是否为多值字段 */
  isMulti?: boolean
  /** 单值字段的选项颜色（仅 list 类型，HEX），null 表示无颜色 */
  color?: string | null
  /** 多值字段的选项颜色列表（仅 list 类型），与 displayValues 对应 */
  colors?: (string | null)[]
}

export interface AvailableColumnVO {
  key: string
  label: string
  group: 'standard' | 'custom'
  fieldFormat?: string
  sortable: boolean
  removable: boolean
}

// ========== 字段类型转换相关 ==========

/** 类型转换选项 */
export interface ConversionOptionVO {
  /** 目标类型代码 */
  format: string
  /** 目标类型显示名称 */
  displayName: string
  /** 是否需要额外选项（如数值转 period 需要指定单位） */
  requiresOptions: boolean
  /** 可用的转换选项（如 MINUTES/HOURS/DAYS） */
  options?: string[]
  /** 转换警告信息（如可能丢失数据） */
  warning?: string
}

/** 可用类型转换 VO */
export interface AvailableConversionsVO {
  /** 当前字段类型 */
  currentFormat: string
  /** 可转换的目标类型列表 */
  availableTargets: ConversionOptionVO[]
  /** 是否允许类型转换 */
  conversionAllowed: boolean
  /** 不允许转换的原因 */
  blockedReason?: string
}

/** 可替换字段列表 VO */
export interface ReplaceableFieldsVO {
  /** 当前字段 ID */
  currentFieldId: string
  /** 当前字段名称 */
  currentFieldName: string
  /** 当前字段类型 */
  currentFieldFormat: string
  /** 可用于替换的目标字段列表（相同类型） */
  availableFields: ReplaceableField[]
}

export interface ReplaceableField {
  /** 字段 ID */
  id: string
  /** 字段名称 */
  name: string
  /** 字段类型 */
  fieldFormat: string
  /** 字段使用的项目数量 */
  projectCount: number
  /** 选项数量（仅 list 类型） */
  optionCount: number
}

/** 字段替换结果 VO */
export interface ReplaceResultVO {
  /** 项目 ID */
  projectId: string
  /** 项目名称 */
  projectName: string
  /** 原字段 ID */
  sourceFieldId: string
  /** 原字段名称 */
  sourceFieldName: string
  /** 目标字段 ID */
  targetFieldId: string
  /** 目标字段名称 */
  targetFieldName: string
  /** 迁移的 issue 数量 */
  migratedIssueCount: number
  /** 合并的选项数量（仅 list 类型） */
  mergedOptionCount: number
  /** 成功消息 */
  message: string
}

/** 类型转换结果 VO */
export interface ConversionResultVO {
  /** 字段 ID */
  fieldId: string
  /** 字段名称 */
  fieldName: string
  /** 原类型 */
  fromFormat: string
  /** 新类型 */
  toFormat: string
  /** 受影响的 issue 数量 */
  affectedIssueCount: number
  /** 成功转换的值数量 */
  convertedValueCount: number
  /** 转换失败的值数量 */
  failedValueCount: number
  /** 警告信息 */
  warning?: string
}
