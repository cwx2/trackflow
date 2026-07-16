import request from './request'
import type { R, SprintVO, SprintBurndownVO, CompletionPreviewVO, CreationPreviewVO, DeletionPreviewVO } from './types'
import type { AxiosRequestConfig } from 'axios'

/** 可选请求配置（支持 _silent403 静默 403） */
type RequestOptions = AxiosRequestConfig & { _silent403?: boolean }

/**
 * Sprint 模块 API
 */
export const sprintApi = {
  /** 项目的 Sprint 列表 */
  listByProject(projectId: string, config?: RequestOptions) {
    return request.get<any, R<SprintVO[]>>(`/projects/${projectId}/sprints`, config)
  },

  /** 创建 Sprint（含可选的移入未完成工单 + 设为默认 Sprint） */
  create(projectId: string, data: {
    name: string
    goal?: string
    startDate?: string
    endDate?: string
    moveUnresolvedIssues?: boolean
    setAsDefault?: boolean
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
  burndown(id: string) {
    return request.get<any, R<SprintBurndownVO>>(`/sprints/${id}/burndown`)
  }
}
