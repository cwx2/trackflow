import request from './request'
import type {
  R, PageResult, IssueVO, IssueDetailVO, IssueCommentVO,
  IssueActivityVO, IssueStatusVO, IssueAttachmentVO,
  IssueTagVO, IssueLinkVO, IssueTrashVO
} from './types'

/**
 * Issue 模块 API
 */
export const issueApi = {
  /** Issue 列表 */
  list(params?: {
    projectId?: string; statusId?: string; priority?: string
    assigneeId?: string; reporterId?: string; sprintId?: string
    issueType?: string; keyword?: string; page?: number; pageSize?: number
    sort?: string
  }) {
    return request.get<any, R<PageResult<IssueVO>>>('/issues', { params })
  },

  /** Issue 详情 */
  getById(id: string) {
    return request.get<any, R<IssueDetailVO>>(`/issues/${id}`)
  },

  /** 通过 Key 获取 */
  getByKey(issueKey: string) {
    return request.get<any, R<IssueDetailVO>>(`/issues/key/${issueKey}`)
  },

  /** 创建 Issue */
  create(data: {
    projectId: string; title: string; description?: string
    issueType?: string; priority?: string; assigneeId?: string
    sprintId?: string; dueDate?: string; estimatedHours?: number
    customFields?: Record<string, string>
  }) {
    return request.post<any, R<IssueDetailVO>>('/issues', data)
  },

  /** 更新 Issue（部分更新） */
  update(id: string, data: Record<string, any>) {
    return request.put<any, R<IssueDetailVO>>(`/issues/${id}`, data)
  },

  /** 更新单个自定义字段值（内联编辑） */
  updateCustomFieldValue(issueId: string, fieldId: string, value: string) {
    return request.put<any, R<IssueDetailVO>>(`/issues/${issueId}/custom-fields/${fieldId}`, { value })
  },

  /** 删除 Issue */
  delete(id: string) {
    return request.delete<any, R<void>>(`/issues/${id}`)
  },

  // ========== 回收站 ==========

  /** 回收站列表 */
  listTrash(params: { projectId: string; page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<IssueTrashVO>>>('/issues/trash', { params })
  },

  /** 恢复工单 */
  restore(id: string) {
    return request.post<any, R<void>>(`/issues/${id}/restore`)
  },

  /** 永久删除工单 */
  permanentDelete(id: string) {
    return request.delete<any, R<void>>(`/issues/${id}/permanent`)
  },

  // ========== 状态 ==========

  /** 状态流转 */
  transitStatus(id: string, statusId: string, comment?: string, version?: number, force?: boolean) {
    return request.post<any, R<void>>(`/issues/${id}/transitions`, { statusId, comment, version, force })
  },

  /** 撤销状态流转（绕过工作流校验） */
  undoTransitStatus(id: string, statusId: string) {
    return request.post<any, R<void>>(`/issues/${id}/transitions/undo`, { statusId })
  },

  /** 获取可用状态转换 */
  getAvailableTransitions(id: string) {
    return request.get<any, R<IssueStatusVO[]>>(`/issues/${id}/available-transitions`)
  },

  /** 状态列表 */
  listStatuses() {
    return request.get<any, R<IssueStatusVO[]>>('/issues/statuses')
  },

  // ========== 分配 ==========

  /** 分配 */
  assign(id: string, assigneeId: string) {
    return request.put<any, R<void>>(`/issues/${id}/assign`, { assigneeId })
  },

  // ========== 评论 ==========

  /** 评论列表 */
  listComments(issueId: string) {
    return request.get<any, R<IssueCommentVO[]>>(`/issues/${issueId}/comments`)
  },

  /** 添加评论 */
  addComment(issueId: string, content: string) {
    return request.post<any, R<IssueCommentVO>>(`/issues/${issueId}/comments`, { content })
  },

  // ========== 活动记录 ==========

  /** 活动记录 */
  listActivities(issueId: string) {
    return request.get<any, R<IssueActivityVO[]>>(`/issues/${issueId}/activities`)
  },

  // ========== 附件 ==========

  /** 附件列表 */
  listAttachments(issueId: string) {
    return request.get<any, R<IssueAttachmentVO[]>>(`/issues/${issueId}/attachments`)
  },

  /** 上传附件 */
  uploadAttachment(issueId: string, file: File, onProgress?: (percent: number) => void) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<any, R<IssueAttachmentVO>>(`/issues/${issueId}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e: any) => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded * 100) / e.total))
        }
      }
    })
  },

  /** 删除附件 */
  deleteAttachment(issueId: string, attachmentId: string) {
    return request.delete<any, R<void>>(`/issues/${issueId}/attachments/${attachmentId}`)
  },

  // ========== 标签 ==========

  /** 获取 Issue 标签 */
  listTags(issueId: string) {
    return request.get<any, R<IssueTagVO[]>>(`/issues/${issueId}/tags`)
  },

  /** 添加标签 */
  addTag(issueId: string, tagId: string) {
    return request.post<any, R<void>>(`/issues/${issueId}/tags`, { tagId })
  },

  /** 移除标签 */
  removeTag(issueId: string, tagId: string) {
    return request.delete<any, R<void>>(`/issues/${issueId}/tags/${tagId}`)
  },

  // ========== 关联 ==========

  /** 获取关联列表 */
  listLinks(issueId: string) {
    return request.get<any, R<IssueLinkVO[]>>(`/issues/${issueId}/links`)
  },

  /** 创建关联 */
  createLink(issueId: string, data: { targetIssueId: string; linkType: string }) {
    return request.post<any, R<void>>(`/issues/${issueId}/links`, data)
  },

  /** 删除关联 */
  deleteLink(issueId: string, linkId: string) {
    return request.delete<any, R<void>>(`/issues/${issueId}/links/${linkId}`)
  },

  // ========== 批量操作 ==========

  /** 批量操作 */
  batch(data: {
    operation: string
    issueIds: string[]
    statusId?: string
    assigneeId?: string
    sprintId?: string
    priority?: string
  }) {
    return request.post<any, R<{
      total: number
      succeeded: number
      failed: number
      failures: Array<{ issueId: string; issueKey: string; reason: string }>
    }>>('/issues/batch', data)
  }
}
