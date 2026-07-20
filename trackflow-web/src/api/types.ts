/**
 * 后端统一响应结构 R<T>
 */
export interface R<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId?: string
}

/**
 * /api/v1/auth/me 返回的当前用户信息 VO
 * 对应后端 UserInfoVO，字段来自 Keycloak JWT + 数据库
 */
export interface UserInfoVO {
  /** 数据库用户 ID（用于资源级权限判断） */
  userId?: string
  /** Keycloak subject ID */
  keycloakId: string
  /** 登录用户名 */
  username: string
  /** 显示名称 */
  displayName: string
  /** 邮箱 */
  email: string
}

/**
 * 从 JWT Token 解析出的当前认证用户信息
 * 存储在 auth store 的 user 字段中，持久化到 localStorage
 */
export interface AuthUser {
  /** Keycloak subject ID */
  id: string
  /** 数据库用户 ID（用于资源级权限判断，与 Issue 的 reporterId/assigneeId 对比） */
  userId?: string
  /** 登录用户名 */
  username: string
  /** 显示名称（CJK 姓+名，西方名+姓） */
  displayName: string
  /** 邮箱 */
  email: string
  /** Keycloak realm_access.roles（如 tf_admin, tf_user） */
  roles: string[]
}

/**
 * 分页结果
 */
export interface PageResult<T = any> {
  list: T[]
  pagination: {
    page: number
    pageSize: number
    total: number
    totalPages: number
  }
}

/**
 * 分页查询参数
 */
export interface PageQuery {
  page?: number
  pageSize?: number
  sort?: string
}

// ========== 项目 ==========
export interface ProjectVO {
  id: string
  name: string
  key: string
  description?: string
  orgId?: string
  leadId?: string
  status: string
  visibility: 'private' | 'internal' | 'public'
  issueSequence: number
  createdAt: string
  updatedAt: string
  /** 项目成员总数 */
  memberCount?: number
  /** 前几名成员的显示名称 */
  topMembers?: string[]
  /** 当前用户是否已收藏该项目 */
  favorited?: boolean
}

export interface ProjectDetailVO extends ProjectVO {
  /** 当前登录用户在该项目中的角色名称 */
  myRoleName?: string
  /** 当前登录用户在该项目中的角色代码 */
  myRoleCode?: string
  /** 项目成员总数 */
  memberCount?: number
  /** 项目负责人显示名称 */
  leadName?: string
  /** 项目负责人状态（active/disabled） */
  leadStatus?: string
}

export interface ProjectMemberVO {
  id: string
  projectId: string
  userId: string
  /** 主角色ID（向后兼容） */
  roleId: string
  /** 所有角色ID列表 */
  roleIds: string[]
  /** 所有角色名称列表 */
  roleNames: string[]
  username: string
  displayName: string
  email?: string
  joinedAt: string
}

// ========== 项目活动日志 ==========
export interface ProjectActivityVO {
  id: string
  projectId: string
  userId: string
  action: string
  targetUserId?: string
  detail?: string
  createdAt: string
  userName: string
  targetUserName?: string
}

// ========== 项目统计 ==========
export interface ProjectStatisticsVO {
  totalIssues: number
  openIssues: number
  closedIssues: number
  completionRate: number
  createdThisWeek: number
  closedThisWeek: number
  statusDistribution: StatusDistribution[]
  activeSprint?: ActiveSprintInfo
}

export interface StatusDistribution {
  statusId: string
  statusName: string
  statusColor: string
  category: string
  closed: boolean
  count: number
}

export interface ActiveSprintInfo {
  id: string
  name: string
  startDate?: string
  endDate?: string
  totalIssues: number
  completedIssues: number
  remainingDays: number
}

// ========== Issue ==========
export interface IssueVO {
  id: string
  projectId: string
  issueKey: string
  title: string
  issueType: string
  statusId: string
  statusName?: string
  statusColor?: string
  priority: string
  assigneeId?: string
  assigneeName?: string
  assigneeAvatarUrl?: string
  reporterId: string
  reporterName?: string
  sprintId?: string
  sprintName?: string
  dueDate?: string
  createdAt: string
  updatedAt: string
  resolvedAt?: string
  version: number
  /** 直接子工单总数 */
  childCount?: number
  /** 已关闭的直接子工单数 */
  childClosedCount?: number
  /** 自定义字段展示值，key 格式 "cf_{fieldId}"，value 为已解析的展示文本 */
  customFieldValues?: Record<string, string>
  /** 自定义字段颜色值，key 格式 "cf_{fieldId}"，value 为 HEX 颜色（仅有颜色的 list 类型字段） */
  customFieldColors?: Record<string, string>
}

export interface IssueDetailVO extends IssueVO {
  description?: string
  projectName?: string
  projectStatus?: string
  reporterName?: string
  sprintName?: string
  parentId?: string
  parentKey?: string
  status?: IssueStatusVO
  estimatedHours?: number
  spentHours?: number
  /** 派生字段：自身 + 所有后代 spent_hours 总和 */
  derivedSpentHours?: number
  /** 派生字段：自身 + 所有后代 estimated_hours 总和 */
  derivedEstimatedHours?: number
  /** 结构化自定义字段值（带字段名称和类型，用于前端渲染） */
  customFieldDetails?: CustomFieldValueVO[]
  tags?: IssueTagVO[]
  resolvedAt?: string
  children?: ChildIssueVO[]
  childProgress?: ChildProgressVO
  /** 类型变更导致状态自动重置时为 true（仅 update 响应中出现） */
  statusAutoReset?: boolean
}

/** 子任务简要信息 */
export interface ChildIssueVO {
  id: string
  issueKey: string
  title: string
  issueType: string
  priority: string
  statusName: string
  statusColor: string
  statusCategory: string
  assigneeName?: string
}

/** 子任务进度汇总 */
export interface ChildProgressVO {
  total: number
  closed: number
  percent: number
  aggregatedEstimate?: number
  aggregatedSpent?: number
}

export interface IssueStatusVO {
  id: string
  name: string
  displayName?: string
  code: string
  color: string
  category: string
  isDefault: boolean
  isClosed: boolean
  sortOrder: number
  /** 是否被阻塞（当前 issue 有未解决的 blocker 且此状态为关闭状态时为 true） */
  blocked?: boolean
  /** 阻塞方的 issueKey 列表 */
  blockedBy?: string[]
}

/** 批量操作中每个状态的可达性信息 */
export interface BatchAvailableStatusVO {
  id: string
  name: string
  color: string
  category: string
  isClosed: boolean
  sortOrder: number
  /** 可以转换到此状态的工单数量 */
  reachableCount: number
  /** 选中的工单总数 */
  totalCount: number
}

export interface IssueTrashVO {
  id: string
  projectId: string
  issueKey: string
  title: string
  issueType: string
  priority: string
  assigneeName?: string
  deletedAt: string
  deletedByName?: string
}

export interface IssueCommentVO {
  id: string
  issueId: string
  userId: string
  userName?: string
  userAvatar?: string
  content: string
  source: string
  isEdited?: boolean
  createdAt: string
  updatedAt: string
}

export interface IssueActivityVO {
  id: string
  issueId: string
  userId: string
  userName?: string
  action: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  createdAt: string
}

export interface IssueAttachmentVO {
  id: string
  issueId: string
  fileName: string
  filePath: string
  fileSize: number
  contentType?: string
  uploadedBy: string
  createdAt: string
}

export interface IssueTagVO {
  id: string
  name: string
  color: string
}

export interface IssueLinkVO {
  id: string
  linkType: string
  issueId: string
  issueKey: string
  issueTitle: string
  issueStatus?: IssueStatusVO
}

// ========== Sprint ==========
export interface SprintVO {
  id: string
  projectId: string
  name: string
  goal?: string
  status: string
  startDate?: string
  endDate?: string
  createdAt: string
  /** 状态提示信息：当 status 与日期矛盾时后端返回警告文案 */
  statusHint?: string
  /** 是否已超期（active 且 end_date < today） */
  overdue?: boolean
  /** 工单总数 */
  totalIssues: number
  /** 已完成工单数 */
  doneIssues: number
  /** 进行中工单数 */
  inProgressIssues: number
  /** 待办工单数 */
  todoIssues: number
  /** 逾期工单数 */
  overdueIssues: number
}

/** Sprint 燃尽图数据 */
export interface SprintBurndownVO {
  sprintId: string
  sprintName: string
  /** X 轴日期列表 (yyyy-MM-dd) */
  dates: string[]
  /** 理想线：基于 Sprint 开始时的工单数线性递减 */
  idealLine: number[]
  /** 实际线：每天的实际剩余工单数（scope - resolved，仅到今天） */
  actualLine: number[]
  /** 范围线：每天的实际工单总数（追踪 scope change） */
  scopeLine: number[]
  /** 今天在 dates 中的索引（-1 = 不在范围内） */
  todayIndex: number
  /** Sprint 当前总工单数 */
  totalIssues: number
  /** Sprint 开始时的工单数（理想线起点） */
  startScopeIssues: number
  /** 日均完成速率 */
  velocity: number
  /** 预测完成日期 (yyyy-MM-dd)，null 表示速率为 0 */
  forecastDate: string | null
}

// ========== 用户/角色/组织 ==========
export interface UserVO {
  id: string
  username: string
  displayName: string
  email?: string
  orgId?: string
  status: string
  banStatus?: string
  banReason?: string
  bannedAt?: string
  bannedByName?: string
  lastLoginAt?: string
  createdAt: string
  globalRoles?: GlobalRoleInfo[]
}

export interface GlobalRoleInfo {
  id: string
  name: string
  code: string
}

export interface RoleVO {
  id: string
  name: string
  code: string
  description?: string
  roleType: string
  builtin: boolean
  sortOrder: number
  userCount?: number
}

export interface RoleUsersVO {
  roleId: string
  roleName: string
  roleType: string
  globalUsers: UserVO[]
  projectGroups: ProjectRoleGroup[]
  totalUserCount: number
}

export interface ProjectRoleGroup {
  projectId: string
  projectName: string
  projectKey: string
  users: UserVO[]
}

export interface SavedQueryVO {
  id: string
  name: string
  projectId?: string
  userId: string
  shared: boolean
  pinned: boolean
  folder?: string
  filters?: string
  icon?: string
  sortOrder: number
  createdAt: string
}

// ========== 自定义字段 ==========
export interface CustomFieldDefinitionVO {
  id: string
  name: string
  fieldFormat: 'string' | 'text' | 'int' | 'float' | 'date' | 'datetime' | 'bool' | 'list' | 'user'
  isRequired: boolean
  isForAll: boolean
  isMulti: boolean
  /** 是否在工单列表的默认列选择器中隐藏 */
  isHiddenInList: boolean
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
}

export interface CustomFieldOptionVO {
  id: string
  customFieldId: string
  value: string
  position: number
  isDefault: boolean
  isArchived?: boolean
  /** 选项颜色（HEX 格式如 #4CAF50），null 表示无颜色 */
  color?: string | null
}

export interface CustomFieldUsageVO {
  issueCount: number
  valueCount: number
  projectCount: number
  isForAll: boolean
  issueTypeCount: number
  optionCount: number
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

// ========== 工作流 ==========
export interface WorkflowTransitionVO {
  id: string
  projectId: string
  issueType: string
  roleId: string
  oldStatusId: string
  newStatusId: string
  author: boolean
  assignee: boolean
}

export interface UpdateWorkflowDTO {
  issueType: string
  roleId: number
  author?: boolean
  assignee?: boolean
  version?: number
  transitions: { from: number; to: number; allowed: boolean }[]
}

/** 工作流矩阵响应（含版本号，用于乐观锁并发控制） */
export interface WorkflowMatrixVO {
  transitions: WorkflowTransitionVO[]
  version: number | null
}

/** 工作流变更历史审计日志 VO */
export interface WorkflowActivityVO {
  id: string
  projectId: string
  projectName: string
  issueType: string
  roleId: string
  roleName: string
  userId: string
  userDisplayName: string
  action: string
  summary: string
  added: TransitionChangeItem[]
  removed: TransitionChangeItem[]
  createdAt: string
}

export interface TransitionChangeItem {
  fromStatus: string
  toStatus: string
}

/** 工作流影响分析响应 */
export interface WorkflowImpactAnalysisVO {
  /** 状态 ID → 该状态下的工单数量 */
  statusIssueCounts: Record<string, number>
  /** 总影响工单数 */
  totalAffectedIssues: number
}

// ========== 状态转换结果 ==========

/** 自动化动作执行结果（TransitionActionEngine 返回） */
export interface ActionExecutionResult {
  /** 是否有动作被成功执行 */
  executed: boolean
  /** 动作类型（如 "auto_assign"） */
  actionType?: string
  /** 执行结果 */
  outcome: 'ASSIGNED' | 'MANUAL_OVERRIDE' | 'STRATEGY_FAILED' | 'NO_ACTIONS' | 'EXECUTION_ERROR'
  /** 分配给了谁的用户 ID */
  newAssigneeId?: string
  /** 分配给了谁的显示名称 */
  newAssigneeName?: string
  /** 使用了哪个策略 */
  strategyUsed?: string
}

/** 状态转换 API 响应体 */
export interface TransitStatusResultVO {
  /** 更新后的乐观锁版本号 */
  version: number
  /** 自动化动作执行结果（可为 null，表示无动作配置） */
  actionResult?: ActionExecutionResult | null
}

// ========== 看板 ==========
export interface BoardColumnVO {
  statusId: string
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

// ========== 看板卡片配置 ==========

export interface BoardCardConfigVO {
  /** 卡片上显示的字段列表 */
  visibleFields: string[]
  /** 颜色方案：none / priority / type / project */
  colorScheme: string
}

// ========== 看板泳道配置 ==========

export interface BoardSwimlaneConfigVO {
  /** 泳道分组字段：none / assignee / priority / type / sprint / tag */
  groupByField: string
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
  /** 当前用户是否有看板查看权限 */
  currentUserCanView: boolean
  /** 当前用户是否有看板编辑权限 */
  currentUserCanEdit: boolean
}

// ========== Sprint 完成预览 ==========
export interface CompletionPreviewVO {
  openIssues: CompletionPreviewIssue[]
  targetSprints: CompletionPreviewTarget[]
}

export interface CompletionPreviewIssue {
  id: string
  issueKey: string
  title: string
  priority: string
  statusName: string
  statusColor: string
  assigneeName?: string
}

export interface CompletionPreviewTarget {
  id: string
  name: string
  status: string
}

// ========== Sprint 删除预览 ==========
export interface DeletionPreviewVO {
  sprintName: string
  dateRange?: string
  totalIssues: number
  targetSprints: DeletionPreviewTarget[]
}

export interface DeletionPreviewTarget {
  id: string
  name: string
  status: string
}

// ========== Sprint 创建预览 ==========
export interface CreationPreviewVO {
  /** 当前活跃 Sprint 的 ID（无活跃 Sprint 时为 null） */
  activeSprintId?: string
  /** 当前活跃 Sprint 的名称 */
  activeSprintName?: string
  /** 当前活跃 Sprint 中未关闭的工单数量 */
  unresolvedIssueCount: number
  /** 该项目当前是否已设置默认 Sprint */
  hasDefaultSprint: boolean
  /** 当前默认 Sprint 的名称（如有） */
  defaultSprintName?: string
}

// ========== Sprint 日期重叠警告 ==========
export interface SprintOverlapWarning {
  overlappingSprints: Array<{
    name: string
    startDate: string
    endDate: string
    status: string
  }>
}
