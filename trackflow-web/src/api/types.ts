/**
 * 后端统一响应结构 R<T>
 */
export interface R<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId?: string
  /** 字段级警告信息（部分字段因权限不足被跳过时出现） */
  warnings?: string[]
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
  /** 当前登录用户在该项目中的角色名称（向后兼容，取第一个角色） */
  myRoleName?: string
  /** 当前登录用户在该项目中的角色代码（向后兼容，取第一个角色） */
  myRoleCode?: string
  /** 当前登录用户在该项目中的所有角色名称列表 */
  myRoleNames?: string[]
  /** 当前登录用户在该项目中的所有角色代码列表 */
  myRoleCodes?: string[]
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
  /** 是否为已离开项目的历史成员（仍有工单分配，保留在 Assignee 候选列表中） */
  formerMember?: boolean
}

/** 通过用户组获得项目访问权的组信息 */
export interface ProjectGroupMemberVO {
  groupId: string
  groupName: string
  projectId: string
  roleId: string
  roleName: string
  users: GroupUserVO[]
  assignedAt: string
}

export interface GroupUserVO {
  userId: string
  username: string
  displayName: string
  email?: string
}

/** 项目成员完整视图（对标 YouTrack People 页面） */
export interface ProjectMembersViewVO {
  /** 直接添加到项目的个人成员 */
  directMembers: ProjectMemberVO[]
  /** 通过用户组获得项目访问权的组 */
  groupMembers: ProjectGroupMemberVO[]
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

// ========== 项目模块 ==========
export interface ProjectModulesVO {
  enabledModules: string[]
  allModules: string[]
  coreModules: string[]
}

// ========== 项目操作结果 ==========

/** 成员操作结果（移除/角色变更后返回） */
export interface MemberOperationResultVO {
  affectedIssueCount: number
}

/** 收藏切换结果 */
export interface FavoriteToggleVO {
  favorited: boolean
}

/** 项目复制概要统计 */
export interface ProjectCopySummaryVO {
  workflow: number
  tags: number
  customFields: number
  board: number
  actions: number
  members: number
  queries: number
}

/** 项目回收站设置 */
export interface ProjectTrashSettingsVO {
  trashRetentionDays: number
}

/** 成员被分配工单数量 */
export interface AssignedIssueCountVO {
  count: number
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
/** 相似工单 VO — 创建工单时用于重复检测的轻量结构 */
export interface SimilarIssueVO {
  id: string
  issueKey: string
  title: string
  statusName?: string
  statusColor?: string
  assigneeName?: string
}

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
  /** Sprint 状态（planned/active/completed/archived），用于前端视觉区分已完成迭代 */
  sprintStatus?: string
  /** 多 Sprint 模式：工单关联的所有 Sprint ID */
  sprintIds?: string[]
  /** 多 Sprint 模式：工单关联的所有 Sprint 名称（与 sprintIds 对应） */
  sprintNames?: string[]
  dueDate?: string
  createdAt: string
  updatedAt: string
  resolvedAt?: string
  version: number
  /** 直接子工单总数 */
  childCount?: number
  /** 已关闭的直接子工单数 */
  childClosedCount?: number
  /** 预估工时 */
  estimatedHours?: number
  /** 已花时间 */
  spentHours?: number
  /** 派生字段：自身 + 所有后代 spent_hours 总和 */
  derivedSpentHours?: number
  /** 派生字段：自身 + 所有后代 estimated_hours 总和 */
  derivedEstimatedHours?: number
  /** 自定义字段结构化详情，每个字段独立表达 value/values、displayValue/displayValues、color/colors */
  customFieldDetails?: CustomFieldValueVO[]
  /**
   * @deprecated 使用 customFieldDetails 代替
   * 自定义字段展示值，key 格式 "cf_{fieldId}"，value 为已解析的展示文本
   */
  customFieldValues?: Record<string, string>
  /**
   * @deprecated 使用 customFieldDetails 代替
   * 自定义字段颜色值，key 格式 "cf_{fieldId}"，value 为 HEX 颜色（仅有颜色的 list 类型字段）
   */
  customFieldColors?: Record<string, string>
  /** 投票数（来自 issue.vote_count 冗余字段） */
  voteCount?: number
  /** 工单可见性：public（项目所有成员可见）或 restricted（仅限指定用户） */
  visibility?: string
  /** 工单关联的标签列表（列表查询时批量填充） */
  tags?: IssueTagVO[]
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
  tags?: IssueTagVO[]
  resolvedAt?: string
  /** 创建者ID */
  createdById?: string
  /** 创建者名称 */
  createdByName?: string
  /** 更新者ID */
  updatedById?: string
  /** 更新者名称 */
  updatedByName?: string
  children?: ChildIssueVO[]
  childProgress?: ChildProgressVO
  /** 类型变更导致状态自动重置时为 true（仅 update 响应中出现） */
  statusAutoReset?: boolean
  /** 受限工单的可见用户 ID 列表（visibility=restricted 时非空） */
  visibilityUserIds?: string[]
  /** 受限工单的可见用户显示名列表 */
  visibilityUserNames?: string[]
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
  /** 此转换是否要求必须填写评论/理由 */
  requireComment?: boolean
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
  deletedAt?: string | null
  visibleToGroupIds?: string[]
  visibleToGroupNames?: string[]
  createdAt: string
  updatedAt: string
}

export interface IssueActivityVO {
  id: string
  issueId: string
  userId?: string
  userName?: string
  userAvatar?: string
  action: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  /**
   * 操作元数据（JSON 字符串），存储变更来源等信息。
   * 示例：{"source":"action_rule"} | {"source":"automation","ruleName":"..."} | {"reason":"member_removed"}
   */
  detail?: string
  /**
   * 活动来源标识：manual（用户手动）/ automation（自动化规则）/ workflow_action（转换动作）/ system（系统）
   * null 等同于 manual。
   */
  source?: string
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
  /** 是否为私有附件 */
  isPrivate?: boolean
  /** 可见性限制的组 ID 列表 */
  visibleToGroupIds?: string[]
  /** 可见性限制的组名称列表 */
  visibleToGroupNames?: string[]
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

export interface IssueLinkTypeVO {
  id: string
  name: string
  outwardName: string
  inwardName: string
  direction: string
  isSystem: boolean
}

// ========== Sprint ==========
export interface SprintVO {
  id: string
  projectId: string
  /** 项目名称（跨项目查询时返回） */
  projectName?: string
  /** 项目 Key（跨项目查询时返回） */
  projectKey?: string
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
  /** 未分配负责人的工单数 */
  unassignedIssues: number
  /** 该 Sprint 所有工单的预估总工时 */
  totalEstimatedHours: number
  /** 该 Sprint 已完成工单的预估工时总和 */
  completedEstimatedHours: number
  /** Sprint 实际激活时间 */
  startedAt?: string
  /** 激活时的总预估工时快照 */
  startScopeHours?: number
  /** 激活时的工单数量快照 */
  startScopeIssues?: number
}

/** Sprint 燃尽图数据 */
export interface SprintBurndownVO {
  sprintId: string
  sprintName: string
  /** X 轴日期列表 (yyyy-MM-dd) */
  dates: string[]
  /** 理想线：基于 Sprint 开始时的工单数/工时线性递减 */
  idealLine: number[]
  /** 实际线：每天的实际剩余工单数/工时（scope - resolved，仅到今天） */
  actualLine: number[]
  /** 范围线：每天的实际工单总数/总工时（追踪 scope change） */
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
  /** Sprint 激活时的总预估工时快照 */
  startScopeHours?: number
  /** 当前模式: "issue_count" 或 "estimation" */
  mode?: string
}

/** Sprint 负责人工作量分布 */
export interface SprintAssigneeDistributionVO {
  sprintId: string
  sprintName: string
  totalIssues: number
  unassignedCount: number
  /** 所有工单预估工时总和 */
  totalEstimatedHours: number
  /** 未分配工单的预估工时总和 */
  unassignedEstimatedHours: number
  assignees: SprintAssigneeItem[]
}

/** 负责人分布中的单项 */
export interface SprintAssigneeItem {
  userId: string
  displayName: string
  issueCount: number
  doneCount: number
  inProgressCount: number
  todoCount: number
  /** 该负责人承担的预估工时总和 */
  estimatedHoursTotal: number
}

/** Sprint 速率统计（用于规划页历史速率参考） */
export interface SprintVelocityVO {
  /** 用于统计的 Sprint 数量 */
  sprintCount: number
  /** 最近已完成 Sprint 的速率列表（时间从旧到新） */
  sprints: SprintVelocityItem[]
  /** 平均速率（已完成工时/Sprint，单位：小时） */
  averageVelocity: number
  /** 最近一个 Sprint 的速率 */
  lastVelocity: number
}

/** Sprint 速率统计中的单个 Sprint 数据 */
export interface SprintVelocityItem {
  id: string
  name: string
  startDate?: string
  endDate?: string
  /** 该 Sprint 完成的工时（已关闭工单的 estimated_hours 之和，单位：小时） */
  completedHours: number
  /** 该 Sprint 规划的总工时 */
  plannedHours: number
  totalIssues: number
  doneIssues: number
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
  sortCriteria?: string
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
  requireComment?: boolean
  /** 守卫条件 JSON 字符串，{} 或 undefined 表示无条件限制 */
  conditions?: string
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
  /** 动作类型（如 "auto_assign", "require_field"） */
  actionType?: string
  /** 执行结果 */
  outcome: 'ASSIGNED' | 'MANUAL_OVERRIDE' | 'STRATEGY_FAILED' | 'NO_ACTIONS' | 'EXECUTION_ERROR' | 'COMMENT_ADDED' | 'KEPT_EXISTING' | 'FIELD_VALIDATION_FAILED'
  /** 分配给了谁的用户 ID */
  newAssigneeId?: string
  /** 分配给了谁的显示名称 */
  newAssigneeName?: string
  /** 使用了哪个策略 */
  strategyUsed?: string
  /** 校验失败的字段 ID（FIELD_VALIDATION_FAILED 时非空） */
  requiredFieldId?: string
  /** 校验失败的字段名称（FIELD_VALIDATION_FAILED 时非空） */
  requiredFieldName?: string
  /** 校验失败的警告消息（FIELD_VALIDATION_FAILED 时非空） */
  warningMessage?: string
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

// ========== Sprint 完成结果（包含统计信息）==========
export interface SprintCompleteResultVO {
  sprint: SprintVO
  /** 该 Sprint 中工单总数 */
  totalIssues: number
  /** 已完成工单数 */
  completedIssues: number
  /** 未完成工单数（被移走或回 Backlog） */
  unresolvedIssues: number
  /** 未完成工单处理方式 */
  moveOption?: 'backlog' | 'next_sprint'
  /** 如果移入其他迭代，目标迭代名称 */
  targetSprintName?: string
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

// ========== 工单模板 ==========
export interface IssueTemplateVO {
  id: string
  projectId: string
  name: string
  description?: string
  issueType?: string
  priority?: string
  defaultTags?: string
  isSystem: boolean
  sortOrder: number
  createdBy?: string
}

// ========== 手动排序 ==========

export interface ManualOrderVO {
  contextType: string
  contextId: string
  ownerOrder: boolean
  issueIds: string[]
}

export interface GroupSimpleVO {
  id: string
  name: string
}

// ========== API Key / Permanent Token ==========

/** API Key 列表项 */
export interface ApiKeyVO {
  id: string
  userId: string
  name: string
  /** Key 前缀（如 "tf_abc1"），用于识别 */
  prefix: string
  /** 权限范围（逗号分隔或描述文本） */
  permissions: string
  expiresAt?: string
  lastUsedAt?: string
  createdAt: string
}

/** API Key 创建结果（仅返回一次，包含明文 Key） */
export interface ApiKeyCreatedVO {
  id: string
  name: string
  /** 完整的 API Key 明文，仅此一次展示 */
  key: string
  prefix: string
  expiresAt?: string
  createdAt: string
}
