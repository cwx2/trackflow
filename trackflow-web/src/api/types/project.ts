/**
 * 项目模块类型定义
 */

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
