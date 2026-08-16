/**
 * Sprint 模块类型定义
 */

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
  /** 按实际工作流状态逐一统计的工单数量（用于多段进度条） */
  statusBreakdown?: StatusBreakdownItem[]
}

/** Sprint 中按实际工作流状态统计的工单数量项 */
export interface StatusBreakdownItem {
  /** 状态 ID */
  statusId: string
  /** 状态名称 */
  statusName: string
  /** 状态颜色（十六进制） */
  statusColor: string
  /** 状态类别（open / in_progress / done / cancelled） */
  category: string
  /** 该状态的工单数量 */
  count: number
  /** 排序序号 */
  sortOrder: number
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

// ========== Sprint 完成预览 ==========
export interface CompletionPreviewVO {
  totalIssues: number
  completedIssues: number
  openIssues: CompletionPreviewIssue[]
  targetSprints: CompletionPreviewTarget[]
}

export interface CompletionPreviewIssue {
  id: string
  issueKey: string
  title: string
  priority: string
  priorityColor?: string
  statusName: string
  statusColor: string
  assigneeName?: string
  dueDate?: string
  estimatedHours?: number
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
  /** 源 Sprint 的 ID（即含未完成工单的 Sprint，无匹配时为 null） */
  sourceSprintId?: string
  /** 源 Sprint 的名称 */
  sourceSprintName?: string
  /** 源 Sprint 的状态（"active" 或 "completed"），前端据此显示不同文案 */
  sourceSprintStatus?: string
  /** 源 Sprint 中未关闭的工单数量 */
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
