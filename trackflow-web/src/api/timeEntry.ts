import request from './request'
import type { R } from './types'

export interface TimeEntryAttributeValueItem {
  attributeId: string
  valueId: string
  valueName: string
  valueColor?: string
}

export interface TimeEntryVO {
  id: string
  issueId: string
  issueKey?: string
  issueTitle?: string
  userId: string
  userName?: string
  workDate: string
  duration: number       // minutes
  startTime?: number     // minutes from midnight
  workType?: string
  description?: string
  createdAt?: string
  updatedAt?: string
  attributeValues?: TimeEntryAttributeValueItem[]
}

export interface ProjectTimeSummaryVO {
  projectId: string
  projectName: string
  projectKey: string
  totalDuration: number  // minutes
  entries: TimeEntryVO[]
}

export interface TimeEntryUserVO {
  id: string
  username: string
  displayName: string
  avatarUrl?: string
}

export interface AttributeValueVO {
  id: string
  name: string
  color?: string
  position: number
}

export interface WorkItemAttributeVO {
  id: string
  name: string
  isBuiltin: boolean
  position: number
  createdAt?: string
  updatedAt?: string
  values: AttributeValueVO[]
  projectIds?: string[]
  usageCount?: number
}

export const timeEntryApi = {
  /** 查询用户在日期范围内的工时 */
  list(params: { userId?: string; startDate: string; endDate: string }) {
    return request.get<any, R<TimeEntryVO[]>>('/time-entries', { params })
  },

  /** 创建工时记录 */
  create(data: { issueId: string; workDate: string; duration: number; startTime?: number; workType?: string; description?: string; attributeValues?: Record<string, string> }) {
    return request.post<any, R<TimeEntryVO>>('/time-entries', data)
  },

  /** 更新工时记录 */
  update(id: string, data: { issueId?: string; workDate?: string; duration?: number; startTime?: number; workType?: string; description?: string; attributeValues?: Record<string, string> }) {
    return request.put<any, R<TimeEntryVO>>(`/time-entries/${id}`, data)
  },

  /** 删除工时记录 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/time-entries/${id}`)
  },

  /** 查询某 Issue 的工时记录 */
  listByIssue(issueId: string) {
    return request.get<any, R<TimeEntryVO[]>>(`/time-entries/issue/${issueId}`)
  },

  /** 汇总工时（分钟） */
  summary(params: { userId?: string; startDate: string; endDate: string }) {
    return request.get<any, R<number>>('/time-entries/summary', { params })
  },

  /** 按项目汇总工时（项目视图概览） */
  listByProject(params: { startDate: string; endDate: string }) {
    return request.get<any, R<ProjectTimeSummaryVO[]>>('/time-entries/by-project', { params })
  },

  /** 查询指定项目在日期范围内的工时明细 */
  listByProjectDetail(projectId: string, params: { startDate: string; endDate: string }) {
    return request.get<any, R<TimeEntryVO[]>>(`/time-entries/by-project/${projectId}`, { params })
  },

  /** 获取可选择的用户列表（用于人员视图选择器） */
  listSelectableUsers(params?: { keyword?: string }) {
    return request.get<any, R<TimeEntryUserVO[]>>('/time-entries/users', { params })
  },

  /** 检查当前用户是否有权查看他人工时 */
  canViewOthers() {
    return request.get<any, R<boolean>>('/time-entries/can-view-others')
  }
}

// ========== 工作项属性管理 API ==========

export const workItemAttributeApi = {
  /** 列出所有工作项属性（管理用） */
  list() {
    return request.get<any, R<WorkItemAttributeVO[]>>('/work-item-attributes')
  },

  /** 获取单个属性详情 */
  getById(id: string) {
    return request.get<any, R<WorkItemAttributeVO>>(`/work-item-attributes/${id}`)
  },

  /** 创建属性 */
  create(data: { name: string; values?: { name: string; color?: string }[] }) {
    return request.post<any, R<WorkItemAttributeVO>>('/work-item-attributes', data)
  },

  /** 更新属性（名称和/或值列表） */
  update(id: string, data: { name?: string; values?: { id?: string; name: string; color?: string }[] }) {
    return request.put<any, R<WorkItemAttributeVO>>(`/work-item-attributes/${id}`, data)
  },

  /** 删除属性 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/work-item-attributes/${id}`)
  },

  /** 管理属性-项目分配 */
  manageProjects(id: string, projectIds: string[]) {
    return request.put<any, R<WorkItemAttributeVO>>(`/work-item-attributes/${id}/projects`, {
      projectIds: projectIds.map(Number)
    })
  },

  /** 获取项目可用的工作项属性（工时弹窗用） */
  listByProject(projectId: string) {
    return request.get<any, R<WorkItemAttributeVO[]>>(`/work-item-attributes/by-project/${projectId}`)
  },

  /** 获取属性使用统计 */
  getUsage(id: string) {
    return request.get<any, R<number>>(`/work-item-attributes/${id}/usage`)
  }
}
