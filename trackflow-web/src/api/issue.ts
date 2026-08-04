import request from './request'
import type {
  R, PageResult, IssueVO, IssueDetailVO, IssueCommentVO,
  IssueActivityVO, IssueStatusVO, IssueAttachmentVO,
  IssueTagVO, IssueLinkVO, IssueTrashVO, BatchAvailableStatusVO,
  TransitStatusResultVO, ManualOrderVO
} from './types'
import { validateFile } from '@/utils/attachment'

/**
 * Issue 模块 API
 */
export const issueApi = {
  /** Issue 列表 */
  list(params?: {
    projectId?: string; statusId?: string; priority?: string
    assigneeId?: string; reporterId?: string; sprintId?: string
    issueType?: string; keyword?: string; page?: number; pageSize?: number
    sort?: string; hideResolved?: string; onlyResolved?: string
    statusIdNot?: string; priorityNot?: string; assigneeIdNot?: string
    sprintIdNot?: string; issueTypeNot?: string
    tagId?: string; parentId?: string; hasParent?: string
    createdAfter?: string; createdBefore?: string
    updatedAfter?: string; updatedBefore?: string
    resolvedAfter?: string; resolvedBefore?: string
    dueAfter?: string; dueBefore?: string
    excludeDoneBefore?: string
    reportedByMe?: string; assignedToMe?: string
    overdue?: string; dueSoon?: string
  }, signal?: AbortSignal) {
    return request.get<any, R<PageResult<IssueVO>>>('/issues', { params, signal })
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
    issueType?: string; priority?: string; statusId?: string
    assigneeId?: string; sprintId?: string; dueDate?: string; estimatedHours?: number
    parentId?: string
    customFields?: Record<string, string>
    tagIds?: string[]
    links?: Array<{ targetIssueId: string; linkType: string }>
  }) {
    return request.post<any, R<IssueDetailVO>>('/issues', data)
  },

  /** 更新 Issue（部分更新） */
  update(id: string, data: Record<string, any>) {
    return request.put<any, R<IssueDetailVO>>(`/issues/${id}`, data)
  },

  /** 更新单个自定义字段值（内联编辑） */
  updateCustomFieldValue(issueId: string, fieldId: string, value: string | string[]) {
    const body = Array.isArray(value) ? { values: value } : { value }
    return request.put<any, R<IssueDetailVO>>(`/issues/${issueId}/custom-fields/${fieldId}`, body)
  },

  /** 查找相似工单（创建工单时防重复）— 复用 list 接口 */
  findSimilar(params: { keyword: string; projectId?: string; limit?: number }) {
    return request.get<any, R<PageResult<IssueVO>>>('/issues', {
      params: {
        keyword: params.keyword,
        projectId: params.projectId,
        pageSize: params.limit || 5,
        page: 1
      }
    })
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

  /** 状态流转（返回版本号 + 动作执行结果） */
  transitStatus(id: string, statusId: string, comment?: string, version?: number, force?: boolean, forceWip?: boolean, forceDescEmpty?: boolean, assigneeId?: string, assigneeExplicit?: boolean) {
    return request.post<any, R<TransitStatusResultVO>>(`/issues/${id}/transitions`, { statusId, comment, version, force, forceWip, forceDescEmpty, assigneeId, assigneeExplicit })
  },

  /** 撤销状态流转（限 30 秒内、仅本人操作可撤销，返回版本号 + 动作执行结果） */
  undoTransitStatus(id: string, statusId: string) {
    return request.post<any, R<TransitStatusResultVO>>(`/issues/${id}/transitions/undo`, { statusId })
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
  addComment(issueId: string, content: string, visibleToGroupIds?: string[]) {
    return request.post<any, R<IssueCommentVO>>(`/issues/${issueId}/comments`, { content, visibleToGroupIds })
  },

  /** 编辑评论 */
  updateComment(issueId: string, commentId: string, content: string, visibleToGroupIds?: string[] | null) {
    const body: Record<string, any> = { content }
    if (visibleToGroupIds !== undefined) {
      body.visibleToGroupIds = visibleToGroupIds
    }
    return request.put<any, R<IssueCommentVO>>(`/issues/${issueId}/comments/${commentId}`, body)
  },

  /** 删除评论（软删除） */
  deleteComment(issueId: string, commentId: string) {
    return request.delete<any, R<void>>(`/issues/${issueId}/comments/${commentId}`)
  },

  /** 还原已删除的评论 */
  restoreComment(issueId: string, commentId: string) {
    return request.post<any, R<void>>(`/issues/${issueId}/comments/${commentId}/restore`)
  },

  /** 永久删除评论（不可恢复） */
  permanentlyDeleteComment(issueId: string, commentId: string) {
    return request.delete<any, R<void>>(`/issues/${issueId}/comments/${commentId}/permanent`)
  },

  // ========== 活动记录 ==========

  /** 活动记录（分页，按时间倒序） */
  listActivities(issueId: string, params?: { page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<IssueActivityVO>>>(`/issues/${issueId}/activities`, { params })
  },

  // ========== 附件 ==========

  /** 附件列表 */
  listAttachments(issueId: string) {
    return request.get<any, R<IssueAttachmentVO[]>>(`/issues/${issueId}/attachments`)
  },

  /** 上传附件（含客户端校验） */
  uploadAttachment(issueId: string, file: File, onProgress?: (percent: number) => void, visibleToGroupIds?: string[]) {
    // 客户端预校验（快速反馈，减少无效请求）
    const validation = validateFile(file)
    if (!validation.valid) {
      return Promise.reject({ response: { data: { message: validation.message } } })
    }

    const formData = new FormData()
    formData.append('file', file)
    if (visibleToGroupIds && visibleToGroupIds.length > 0) {
      visibleToGroupIds.forEach(id => formData.append('visibleToGroupIds', id))
    }
    return request.post<any, R<IssueAttachmentVO>>(`/issues/${issueId}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e: any) => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded * 100) / e.total))
        }
      }
    })
  },

  /** 更新附件可见性 */
  updateAttachmentVisibility(issueId: string, attachmentId: string, visibleToGroupIds: string[] | null) {
    return request.put<any, R<IssueAttachmentVO>>(`/issues/${issueId}/attachments/${attachmentId}/visibility`, {
      visibleToGroupIds: visibleToGroupIds?.map(Number) ?? null
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

  /** 添加标签（支持单个或批量） */
  addTag(issueId: string, tagId: string) {
    return request.post<any, R<void>>(`/issues/${issueId}/tags`, { tagIds: [tagId] })
  },

  /** 批量添加标签 */
  addTags(issueId: string, tagIds: string[]) {
    return request.post<any, R<void>>(`/issues/${issueId}/tags`, { tagIds })
  },

  /** 移除标签 */
  removeTag(issueId: string, tagId: string) {
    return request.delete<any, R<void>>(`/issues/${issueId}/tags/${tagId}`)
  },

  // ========== 关联 ==========

  /** 获取所有关联类型（普通用户可用） */
  listLinkTypes() {
    return request.get<any, R<any[]>>('/issues/link-types')
  },

  /** 获取关联列表 */
  listLinks(issueId: string) {
    return request.get<any, R<IssueLinkVO[]>>(`/issues/${issueId}/links`)
  },

  /** 添加关联（封装 createLink，参数更清晰） */
  addLink(issueId: string, data: { targetIssueId: string; linkTypeId: string }) {
    return request.post<any, R<void>>(`/issues/${issueId}/links`, {
      targetIssueId: data.targetIssueId,
      linkType: data.linkTypeId,   // 后端字段名为 linkType
    })
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

  /** 获取批量状态转换的可用状态列表（带可达性信息） */
  getBatchAvailableTransitions(issueIds: string[]) {
    return request.post<any, R<BatchAvailableStatusVO[]>>('/issues/batch-available-transitions', { issueIds })
  },

  /** 批量操作 */
  batch(data: {
    operation: string
    issueIds: string[]
    statusId?: string
    assigneeId?: string
    sprintId?: string
    priority?: string
    comment?: string
    versions?: Record<string, number>
    silent?: boolean
    tagId?: string
    forceWip?: boolean
  }) {
    return request.post<any, R<{
      total: number
      succeeded: number
      failed: number
      failures: Array<{ issueId: string; issueKey: string; reason: string }>
    }>>('/issues/batch', data)
  },

  /** 导出工单数据（XLSX/CSV），返回 Blob */
  export(data: {
    format: 'xlsx' | 'csv'
    issueIds?: string[]
    projectId?: string
    statusId?: string
    priority?: string
    assigneeId?: string
    sprintId?: string
    issueType?: string
    keyword?: string
    hideResolved?: string
    statusIdNot?: string
    priorityNot?: string
    assigneeIdNot?: string
    sprintIdNot?: string
    issueTypeNot?: string
  }) {
    return request.post('/issues/export', data, { responseType: 'blob' })
  },

  // ========== 移动工单 ==========

  /** 移动工单到目标项目 */
  move(issueId: string, targetProjectId: string) {
    return request.post<any, R<IssueDetailVO>>(`/issues/${issueId}/move`, { targetProjectId })
  },

  // ========== 优先级选项 ==========

  /** 获取项目的优先级选项列表（从自定义字段系统动态获取，含颜色和描述） */
  getPriorityOptions(projectId: string) {
    return request.get<any, R<Array<{
      id: string
      customFieldId: string
      projectId: string | null
      value: string
      position: number
      isDefault: boolean
      isArchived: boolean
      color: string | null
      description: string | null
    }>>>('/issues/priority-options', { params: { projectId } })
  },

  // ========== 手动排序 ==========

  /** 获取手动排序 */
  getManualOrder(contextType: string, contextId: string) {
    return request.get<any, R<ManualOrderVO>>('/manual-orders', {
      params: { contextType, contextId }
    })
  },

  /** 保存完整手动排序 */
  saveManualOrder(data: { contextType: string; contextId: number; issueIds: number[] }) {
    return request.post<any, R<ManualOrderVO>>('/manual-orders', data)
  },

  /** 移动单个工单到指定位置 */
  moveManualOrder(data: { contextType: string; contextId: number; issueId: number; targetPosition: number }) {
    return request.put<any, R<ManualOrderVO>>('/manual-orders/move', data)
  },

  /** 丢弃手动排序 */
  discardManualOrder(contextType: string, contextId: string) {
    return request.delete<any, R<void>>('/manual-orders', {
      params: { contextType, contextId }
    })
  }
}
