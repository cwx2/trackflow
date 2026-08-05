/**
 * 工作流模块类型定义
 */

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
  /** 转换显示名（如"开始处理"），为空时使用目标状态名 */
  transitionName?: string
  /** 是否标记为初始状态（新建工单默认状态） */
  isInitial?: boolean
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

/** 工作流初始状态配置 VO */
export interface WorkflowInitialStatusVO {
  id: string
  projectId: string | null
  issueType: string
  statusId: string
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
