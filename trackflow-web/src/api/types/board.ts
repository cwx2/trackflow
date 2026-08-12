/**
 * 看板模块类型定义
 */
import type { CustomFieldValueVO } from './customField'

export interface BoardColumnVO {
  /** 状态 ID。status模式为状态ID字符串，priority模式为 null */
  statusId: string | null
  /** 通用列标识值（status模式=statusId, priority模式=Critical/High/Normal/Low） */
  fieldValue: string
  statusName: string
  statusCode: string
  statusColor: string
  statusCategory: string
  visible: boolean
  sortOrder: number
  collapsed: boolean
  wipMin: number | null
  wipMax: number | null
  /** 该隐藏列中是否有工单存在（仅 visible=false 时有意义） */
  hasHiddenIssues: boolean | null
  /** 该项目中处于此状态的工单数量 */
  issueCount: number | null
  /** 该列所有工单的预估工时总和 */
  totalEstimation: number | null
  /** 该状态是否出现在项目的工作流转换路径中 */
  inWorkflow: boolean | null
}

export interface BoardColumnItem {
  statusId: number
  visible: boolean
  sortOrder: number
  collapsed?: boolean
  wipMin?: number | null
  wipMax?: number | null
}

// ========== 看板聚合数据 ==========

/** 看板卡片精简 VO — 仅包含卡片渲染所需字段（替代完整 IssueVO，性能优化） */
export interface BoardCardVO {
  id: string
  projectId: string
  /** 项目 Key（如 DE4、APP），多项目看板时用于区分来源项目 */
  projectKey?: string
  issueKey: string
  title: string
  issueType: string
  statusId: string
  statusName?: string
  statusColor?: string
  priority: string
  /** 优先级颜色（HEX 格式，来自自定义字段配置） */
  priorityColor?: string
  /** 工单类型颜色（HEX 格式，来自自定义字段配置） */
  issueTypeColor?: string
  assigneeId?: string
  assigneeName?: string
  assigneeAvatarUrl?: string
  sprintId?: string
  sprintName?: string
  dueDate?: string
  estimatedHours?: number
  createdAt?: string
  resolvedAt?: string
  childCount?: number
  childClosedCount?: number
  /** 父工单 ID（用于 Issues 类型泳道分组） */
  parentId?: string
  /** 父工单 issue key */
  parentIssueKey?: string
  /** 父工单标题 */
  parentTitle?: string
  customFieldDetails?: CustomFieldValueVO[]
  /** 工单关联的标签列表（当卡片配置 visibleFields 包含 "tags" 时返回） */
  tags?: Array<{ id: string; name: string; color?: string }>
}

/** 看板聚合数据 — 按列分组的工单 */
export interface BoardDataVO {
  columns: BoardColumnData[]
  /** 本次聚合所使用的完整列配置快照（含可见+隐藏列），前端直接使用此字段渲染列 */
  columnConfigs: BoardColumnVO[]
  totalIssueCount: number
  truncated: boolean
}

/** 单列数据 */
export interface BoardColumnData {
  statusId: string
  statusName: string
  issues: BoardCardVO[]
  totalCount: number
  totalEstimation: number | null
  collapsed: boolean
}

// ========== 看板卡片配置 ==========

export interface BoardCardConfigVO {
  /** 卡片上显示的字段列表 */
  visibleFields: string[]
  /** 颜色方案：none / priority / type / project */
  colorScheme: string
  /**
   * 当前估算字段 ID（String，避免 JS Long 精度丢失）。
   * 对应 YouTrack Board Settings Cards Tab 的 "Current estimation field"。
   * null 表示未配置。
   */
  currentEstimationFieldId?: string | null
  /**
   * 原始估算字段 ID（String，避免 JS Long 精度丢失）。
   * 对应 YouTrack Board Settings Cards Tab 的 "Original estimation field"。
   * null 表示未配置。
   */
  originalEstimationFieldId?: string | null
  /**
   * 每个字段的显示格式，key 为字段名，value 为 "full_name" 或 "initial"。
   * 示例：{"assignee":"initial","priority":"full_name"}
   * 对应 YouTrack Cards Tab 字段的 Display menu（Full name / Initial）。
   * null 或缺失 key 表示该字段使用默认的 full_name 模式。
   */
  fieldDisplayModes?: Record<string, 'full_name' | 'initial'> | null
  /**
   * 是否在卡片自定义字段值旁显示颜色指示器。
   * 对应 YouTrack Board Settings > Cards Tab 的 "Show colors for other custom fields"。
   * 默认 true。
   */
  showCustomFieldColors?: boolean
}

// ========== 看板泳道配置 ==========

export interface BoardSwimlaneConfigVO {
  /** 泳道分组字段：none / assignee / priority / type / sprint / tag / parent */
  groupByField: string
  /** 选中的泳道值列表，null 或空表示全选（向后兼容） */
  selectedValues?: string[] | null
  /** 是否显示"未分类"泳道（默认 true） */
  showUncategorized?: boolean
  /** 未分类泳道位置：top / bottom（默认 bottom） */
  uncategorizedPosition?: 'top' | 'bottom'
  /**
   * Issues 模式下作为泳道行的工单类型（如 "Epic"、"Feature"）。
   * 仅当 groupByField = "parent" 时有效。
   */
  swimlaneIssueType?: string | null
}

// ========== 看板图表配置 ==========

export interface BoardChartConfigVO {
  /** 图表类型: burndown / cumulative_flow */
  chartType: string
  /** Burndown 计算方式: issue_count / estimation / work_items */
  burndownCalculation: string
  /** Issue 过滤器模式: all_cards / custom */
  issueFilterMode: string
  /** 自定义过滤条件 */
  issueFilterQuery: string | null
  /** 当前估算字段 ID */
  estimationFieldId: string | null
  /** 原始估算字段 ID */
  originalEstimationFieldId: string | null
}

// ========== 看板列合并配置 ==========

export interface BoardColumnMergeGroupVO {
  /** 合并组标识 */
  mergeGroupId: string
  /** 合并后的列标题 */
  mergeTitle: string
  /** 该组中合并的状态 ID 列表 */
  statusIds: string[]
}

// ========== 看板基本设置 ==========

export interface BoardGeneralConfigVO {
  /** 看板配置版本号（用于乐观锁并发控制） */
  configVersion: number
  /** 看板名称（空字符串时前端使用默认名） */
  name: string
  /** 可查看看板的角色代码列表 */
  canViewRoles: string[]
  /** 可编辑看板设置的角色代码列表 */
  canEditRoles: string[]
  /** 看板过滤模式：all | active_sprint | query */
  filterMode: 'all' | 'active_sprint' | 'query'
  /** 查询过滤条件（JSON 数组字符串），当 filterMode='query' 时有值 */
  filterQuery: string | null
  /** 已完成工单保留天数（null 表示不限制） */
  doneRetentionDays: number | null
  /** 看板列标识字段：status | priority */
  columnField: 'status' | 'priority'
  /**
   * 是否允许卡片分配到多个迭代（Sprint）。
   * 对应 YouTrack Board Settings > Cards Tab: Allow cards to be assigned to multiple sprints。
   */
  allowMultipleSprints: boolean
  /**
   * Backlog 视图模式：list=平铺列表（默认），tree=树形层级。
   * 对应 YouTrack Board Settings > Backlog Settings: View mode。
   */
  backlogViewMode: 'list' | 'tree'
  /**
   * 过滤 Backlog 工单的保存搜索 ID（null 表示使用默认过滤）。
   * 对应 YouTrack Board Settings > Backlog Settings: Saved search。
   */
  backlogSavedQueryId: string | null
  /**
   * 关联项目 ID 列表（String 类型）。
   * 跨项目看板时，除主项目外还关联的其他项目 ID。
   * 对应 YouTrack Board Settings > General: Projects 多选配置。
   */
  linkedProjectIds: string[]
  /**
   * 关联项目简要信息（名称 + Key），运行时从后端填充。
   */
  linkedProjects: Array<{ id: string; name: string; key: string }>
  /** 当前用户是否有看板查看权限 */
  currentUserCanView: boolean
  /** 当前用户是否有看板编辑权限 */
  currentUserCanEdit: boolean
}

// ========== 看板列表（Board Selector） ==========

/** 看板列表项（Board Selector 用） */
export interface BoardListItemVO {
  /** 项目 ID（作为看板标识） */
  projectId: string
  /** 看板名称（来自 board_general_config.name 或项目名） */
  name: string
  /** 项目 Key */
  projectKey: string
  /** 项目名称 */
  projectName: string
  /** 所有者显示名 */
  ownerName: string
  /** 所有者 ID */
  ownerId: string
  /** 是否已收藏 */
  favorite: boolean
}

/** 克隆看板请求 */
export interface CloneBoardDTO {
  /** 源项目 ID */
  sourceProjectId: string
  /** 新看板名称 */
  newName: string
  /** 新项目 Key */
  newKey: string
}

/** 克隆看板结果 */
export interface CloneBoardResultVO {
  /** 新项目 ID */
  projectId: string
  /** 新看板名称 */
  name: string
  /** 新项目 Key */
  projectKey: string
}
