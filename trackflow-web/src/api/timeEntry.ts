import request from './request'
import type { R } from './types'

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
}

export interface ProjectTimeSummaryVO {
  projectId: string
  projectName: string
  projectKey: string
  totalDuration: number  // minutes
  entries: TimeEntryVO[]
}

export const timeEntryApi = {
  /** 查询用户在日期范围内的工时 */
  list(params: { userId?: string; startDate: string; endDate: string }) {
    return request.get<any, R<TimeEntryVO[]>>('/time-entries', { params })
  },

  /** 创建工时记录 */
  create(data: { issueId: string; workDate: string; duration: number; startTime?: number; workType?: string; description?: string }) {
    return request.post<any, R<TimeEntryVO>>('/time-entries', data)
  },

  /** 更新工时记录 */
  update(id: string, data: { issueId?: string; workDate?: string; duration?: number; startTime?: number; workType?: string; description?: string }) {
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
  }
}
