import request from './request'
import type { R, PageResult, SprintVO, SprintBurndownVO, SprintAssigneeDistributionVO, SprintVelocityVO, CompletionPreviewVO, CreationPreviewVO, DeletionPreviewVO } from './types'
import type { AxiosRequestConfig } from 'axios'

/** 可选请求配置（支持 _silent403 静默 403） */
type RequestOptions = AxiosRequestConfig & { _silent403?: boolean }

/**
 * Sprint 模块 API
 */
export const sprintApi = {
  /** 跨项目 Sprint 概览列表（默认显示所有可访问项目的 Sprint） */
  listAll(params?: { projectId?: string; page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<SprintVO>>>('/sprints', { params })
  },

  /** 项目的 Sprint 列表（分页，默认 pageSize=200 以兼容不需要分页的场景） */
  listByProject(projectId: string, options?: { page?: number; pageSize?: number } & RequestOptions) {
    const { page = 1, pageSize = 200, ...config } = options || {}
    return request.get<any, R<PageResult<SprintVO>>>(`/projects/${projectId}/sprints`, {
      ...config,
      params: { page, pageSize }
    })
  },

  /** 创建 Sprint（含可选的移入未完成工单 + 设为默认 Sprint） */
  create(projectId: string, data: {
    name: string
    goal?: string
    startDate?: string
    endDate?: string
    moveUnresolvedIssues?: boolean
    setAsDefault?: boolean
    confirmOverlap?: boolean
  }) {
    return request.post<any, R<SprintVO>>(`/projects/${projectId}/sprints`, data)
  },

  /** Sprint 创建预览（获取当前活跃 Sprint 的未完成工单信息） */
  creationPreview(projectId: string) {
    return request.get<any, R<CreationPreviewVO>>(`/projects/${projectId}/sprints/creation-preview`)
  },

  /** Sprint 详情 */
  getById(id: string) {
    return request.get<any, R<SprintVO>>(`/sprints/${id}`)
  },

  /** 更新 Sprint */
  update(id: string, data: { name?: string; goal?: string; startDate?: string; endDate?: string }) {
    return request.put<any, R<SprintVO>>(`/sprints/${id}`, data)
  },

  /** 激活 Sprint */
  activate(id: string) {
    return request.put<any, R<SprintVO>>(`/sprints/${id}/activate`)
  },

  /** 完成 Sprint（含未完成工单处理选项） */
  complete(id: string, data?: { moveOption: string; targetSprintId?: string }) {
    return request.put<any, R<SprintVO>>(`/sprints/${id}/complete`, data || undefined)
  },

  /** Sprint 完成预览（获取未完成工单和可迁移目标） */
  completionPreview(id: string) {
    return request.get<any, R<CompletionPreviewVO>>(`/sprints/${id}/completion-preview`)
  },

  /** Sprint 删除预览（获取受影响工单数量和可迁移目标） */
  deletionPreview(id: string) {
    return request.get<any, R<DeletionPreviewVO>>(`/sprints/${id}/deletion-preview`)
  },

  /** 删除 Sprint（含关联工单处理选项） */
  delete(id: string, data?: { moveOption: string; targetSprintId?: string }) {
    return request.delete<any, R<void>>(`/sprints/${id}`, { data })
  },

  /** 获取 Sprint 燃尽图数据 */
  burndown(id: string, mode?: 'issue_count' | 'estimation' | 'work_items') {
    return request.get<any, R<SprintBurndownVO>>(`/sprints/${id}/burndown`, {
      params: mode ? { mode } : undefined
    })
  },

  /** 获取 Sprint 负责人工作量分布 */
  assigneeDistribution(id: string) {
    return request.get<any, R<SprintAssigneeDistributionVO>>(`/sprints/${id}/assignee-distribution`)
  },

  /** 归档 Sprint（completed → archived） */
  archive(id: string) {
    return request.put<any, R<SprintVO>>(`/sprints/${id}/archive`)
  },

  /** 恢复 Sprint（archived → completed） */
  restore(id: string) {
    return request.put<any, R<SprintVO>>(`/sprints/${id}/restore`)
  },

  /**
   * 获取项目最近已完成 Sprint 的速率统计数据
   * 用于规划页展示历史速率参考（工时完成量/Sprint）
   */
  velocity(projectId: string, limit = 5) {
    return request.get<any, R<SprintVelocityVO>>(`/projects/${projectId}/sprint-velocity`, {
      params: { limit }
    })
  }
}
