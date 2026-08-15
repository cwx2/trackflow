/**
 * useIssueDetailActions — 工单详情页所有操作处理函数
 *
 * 从 IssueDetailView.vue 提取，负责：
 * - 字段编辑（标题、描述、标签、负责人、Sprint、自定义字段等）
 * - 状态转换（含 WIP 超限、关闭确认、描述为空等交互式警告）
 * - 附件上传/删除
 * - 评论增删改
 * - 工单删除/移动/克隆/子任务创建
 * - 工时记录
 */
import { copyToClipboard } from '@/utils/clipboard'
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { issueApi, tagApi, customFieldApi, timeEntryApi } from '@/api'
import { handleApiError } from '@/utils/errorHandler'
import { showActionFeedback } from '@/utils/transition'
import { ERROR_CODES } from '@/api/error-codes'
import { useNavBadge } from '@/composables/useNavBadge'
import { useTimerStore } from '@/stores/timer'
import { useDrafts } from './useDrafts'
import type { IssueDetailVO, IssueAttachmentVO } from '@/api/types'
import type { StatusInfo } from '../components/DetailSidebar.vue'
import type { TimeEntryVO } from '@/api/timeEntry'

interface ActionDeps {
  issue: { value: IssueDetailVO | null }
  attachments: { value: IssueAttachmentVO[] }
  loadAll: () => Promise<void>
  loadAttachments: () => Promise<void>
  loadLinks: () => Promise<void>
  loadIssueProjectAttributes: (projectId: string) => Promise<void>
  loadTimeFormPermissions: (projectId: string) => Promise<void>
  canEditIssueEffective: { value: boolean }
  hasProjectPermission: (perm: string) => boolean
  currentUserId: { value: string }
  sidebarCollapsed: { value: boolean }
  sidebarRef: { value: any }
  /** WorkTimeForm 组件的 ref，用于获取/填充表单数据 */
  workTimeFormRef: { value: { validate: () => string | null; getFormData: () => any; fill: (entry: any) => void; reset: () => void } | null }
}

export function useIssueDetailActions(deps: ActionDeps) {
  const router = useRouter()
  const timerStore = useTimerStore()
  const { saveDraft: saveIssueDraft } = useDrafts()
  const SIDEBAR_COLLAPSED_KEY = 'tf_issue_detail_sidebar_collapsed'

  // ============ Transition modal state ============
  const showTransitionModal = ref(false)
  const transitionTarget = ref<StatusInfo | null>(null)
  const transitionRequireComment = ref(false)

  // ============ Create panel state ============
  const showCreatePanel = ref(false)
  const cloneData = ref<{ projectId: string; title: string; description: string; issueType: string; priority: string } | undefined>(undefined)
  const createSubtaskParentId = ref<string | null>(null)

  // ============ Link modal ============
  const showAddLinkModal = ref(false)
  function openAddLinkModal() { showAddLinkModal.value = true }
  async function onLinked() { await deps.loadLinks() }
  async function onDeleteLink(linkId: string) {
    if (!deps.issue.value) return
    try {
      await issueApi.deleteLink(deps.issue.value.id, linkId)
      Message.success('关联已删除')
      await deps.loadLinks()
    } catch (e) { handleApiError(e, '删除关联失败') }
  }

  // ============ Move modal ============
  const showMoveModal = ref(false)
  const moveModalRef = ref<any>(null)

  // ============ Privacy modal ============
  const showPrivacyModal = ref(false)

  // ============ Time entry dialog ============
  const showTimeDialog = ref(false)
  const timeSaving = ref(false)
  const timeDeleting = ref(false)
  /** 当前正在编辑的工时条目，null 表示新增模式 */
  const editingTimeEntry = ref<TimeEntryVO | null>(null)

  /** 打开工时弹窗
   * @param entry 传入已有条目时进入编辑模式，不传则新增模式
   */
  function openTimeDialog(entry?: TimeEntryVO) {
    editingTimeEntry.value = entry ?? null
    showTimeDialog.value = true
    // 异步等 DOM 渲染后操作 form ref
    import('vue').then(({ nextTick: nt }) => nt(() => {
      if (entry) {
        deps.workTimeFormRef.value?.fill(entry)
      } else {
        deps.workTimeFormRef.value?.reset()
      }
    }))
    // 按需加载项目工时属性和权限
    if (deps.issue.value?.projectId) {
      deps.loadIssueProjectAttributes(deps.issue.value.projectId)
      deps.loadTimeFormPermissions(deps.issue.value.projectId)
    }
  }

  async function submitTimeEntry() {
    const formRef = deps.workTimeFormRef.value
    if (!formRef) return
    const error = formRef.validate()
    if (error) { Message.warning(error); return }

    const { workDate, durationText: _dt, description, forUserId, attrValues, duration } = formRef.getFormData()

    const attrValsFiltered = Object.keys(attrValues).length > 0
      ? Object.fromEntries(Object.entries(attrValues as Record<string, string>).filter(([, v]) => v))
      : undefined

    const forUserIdFinal = (deps.currentUserId.value && forUserId && forUserId !== deps.currentUserId.value)
      ? forUserId : undefined

    timeSaving.value = true
    try {
      if (editingTimeEntry.value) {
        await timeEntryApi.update(editingTimeEntry.value.id, {
          workDate,
          duration,
          description: description || undefined,
          attributeValues: attrValsFiltered,
        })
        Message.success('工时已更新')
      } else {
        await timeEntryApi.create({
          issueId: deps.issue.value!.id,
          workDate,
          duration,
          description: description || undefined,
          forUserId: forUserIdFinal,
          attributeValues: attrValsFiltered,
        })
        Message.success('工时已记录')
      }
      showTimeDialog.value = false
      await deps.loadAll()
    } catch (e) { handleApiError(e, '操作失败') }
    finally { timeSaving.value = false }
  }

  async function deleteTimeEntry() {
    if (!editingTimeEntry.value) return
    Modal.warning({
      title: '删除工时',
      content: '确定要删除这条工时记录吗？此操作不可撤销。',
      okText: '删除',
      cancelText: '取消',
      hideCancel: false,
      onOk: async () => {
        timeDeleting.value = true
        try {
          await timeEntryApi.delete(editingTimeEntry.value!.id)
          Message.success('工时已删除')
          showTimeDialog.value = false
          await deps.loadAll()
        } catch (e) { handleApiError(e, '删除失败') }
        finally { timeDeleting.value = false }
      }
    })
  }

  // ============ Copy / Clone ============
  function copyIssue() {
    copyToClipboard(`${deps.issue.value?.issueKey} ${deps.issue.value?.title}`, { successMessage: '已复制' })
  }

  function onCopyId() {
    if (!deps.issue.value) return
    copyToClipboard(deps.issue.value.issueKey, { successMessage: '已复制工单号' })
  }

  function onCloneIssue() {
    if (!deps.issue.value) return
    showCreatePanel.value = true
    cloneData.value = {
      projectId: deps.issue.value.projectId,
      title: `[Clone] ${deps.issue.value.title}`,
      description: deps.issue.value.description || '',
      issueType: deps.issue.value.issueType,
      priority: deps.issue.value.priority
    }
  }

  function onCreateSubtask() {
    if (!deps.issue.value) return
    createSubtaskParentId.value = deps.issue.value.id
    cloneData.value = undefined
    showCreatePanel.value = true
  }

  // ============ Attachments ============
  function triggerUpload(isPrivate: boolean) {
    if (isPrivate) { showPrivacyModal.value = true }
    else { openFilePicker(undefined) }
  }

  function onPrivacyGroupConfirm(groupIds: string[]) { openFilePicker(groupIds) }

  function openFilePicker(visibleToGroupIds: string[] | undefined) {
    const input = document.createElement('input')
    input.type = 'file'
    input.multiple = true
    input.onchange = async () => {
      if (!input.files || input.files.length === 0) return
      for (const file of Array.from(input.files)) { await doUploadFile(file, visibleToGroupIds) }
    }
    input.click()
  }

  async function onDropFiles(files: File[]) {
    if (!deps.issue.value || files.length === 0) return
    for (const file of files) { await doUploadFile(file, undefined) }
  }

  async function doUploadFile(file: File, visibleToGroupIds: string[] | undefined) {
    if (!deps.issue.value) return
    try {
      await issueApi.uploadAttachment(deps.issue.value.id, file, undefined, visibleToGroupIds)
      Message.success(`${file.name} 上传成功`)
      deps.loadAttachments()
    } catch (e) { handleApiError(e, `${file.name} 上传失败`) }
  }

  async function onDeleteAttachment(attachmentId: string) {
    if (!deps.issue.value) return
    try {
      await issueApi.deleteAttachment(deps.issue.value.id, attachmentId)
      Message.success('附件已删除')
      deps.loadAttachments()
    } catch (e) { handleApiError(e, '删除附件失败') }
  }

  async function onDeleteAllAttachments() {
    if (!deps.issue.value) return
    try {
      for (const att of deps.attachments.value) {
        await issueApi.deleteAttachment(deps.issue.value.id, att.id)
      }
      Message.success('所有附件已删除')
      deps.loadAttachments()
    } catch (e) { handleApiError(e, '删除附件失败') }
  }

  // ============ Delete / Move ============
  function onDeleteIssue() {
    if (!deps.issue.value) return
    Modal.warning({
      title: '删除工单',
      content: `确定要删除工单 ${deps.issue.value.issueKey} 吗？删除后可在回收站恢复。`,
      okText: '删除',
      cancelText: '取消',
      hideCancel: false,
      onOk: async () => {
        try {
          await issueApi.delete(deps.issue.value!.id)
          Message.success('工单已删除')
          router.push('/issues')
        } catch (e) { handleApiError(e, '删除失败') }
      }
    })
  }

  function onMoveIssue() { showMoveModal.value = true }

  async function onMoveConfirm(targetProjectId: string) {
    if (!deps.issue.value) return
    try {
      const res = await issueApi.move(deps.issue.value.id, targetProjectId)
      if (res.code === 0 && res.data) {
        Message.success(`已移动到项目，新编号：${res.data.issueKey}`)
        showMoveModal.value = false
        router.replace(`/issues/${res.data.issueKey}`)
      } else {
        Message.error(res.message || '移动失败')
        moveModalRef.value?.resetSubmitting()
      }
    } catch (e) {
      handleApiError(e, '移动失败')
      moveModalRef.value?.resetSubmitting()
    }
  }

  // ============ Create panel ============
  function onCreatePanelClose(val: boolean) {
    showCreatePanel.value = val
    if (!val) { cloneData.value = undefined; createSubtaskParentId.value = null }
  }

  function onIssueCreated() {
    const wasSubtask = !!createSubtaskParentId.value
    showCreatePanel.value = false
    cloneData.value = undefined
    createSubtaskParentId.value = null
    if (wasSubtask) { deps.loadAll() }
  }

  function onCreatePanelExpand(formData: any) {
    showCreatePanel.value = false
    cloneData.value = undefined
    if (formData && (formData.title?.trim() || formData.description?.trim())) {
      const draftId = saveIssueDraft(formData)
      if (draftId) { router.push({ name: 'IssueCreate', query: { draftId } }); return }
    }
    router.push({ name: 'IssueCreate' })
  }

  // ============ Field updates ============
  async function onUpdateTitle(val: string) {
    try { await issueApi.update(deps.issue.value!.id, { title: val, version: deps.issue.value!.version }); await deps.loadAll() }
    catch (e: any) { handleUpdateError(e) }
  }

  async function onUpdateDesc(val: string) {
    try { await issueApi.update(deps.issue.value!.id, { description: val, version: deps.issue.value!.version }); await deps.loadAll() }
    catch (e: any) { handleUpdateError(e) }
  }

  async function onRemoveTag(tagId: string) {
    try { await issueApi.removeTag(deps.issue.value!.id, tagId); await deps.loadAll() }
    catch (e) { handleApiError(e, '操作失败') }
  }

  async function onAddTag(tag: { id: string }) {
    try { await issueApi.addTag(deps.issue.value!.id, tag.id); await deps.loadAll() }
    catch (e) { handleApiError(e, '操作失败') }
  }

  async function onAddTags(tags: { id: string }[]) {
    try { await issueApi.addTags(deps.issue.value!.id, tags.map(t => t.id)); await deps.loadAll() }
    catch (e) { handleApiError(e, '操作失败') }
  }

  async function onCreateTag(name: string) {
    try {
      const res = await tagApi.createProjectTag(deps.issue.value!.projectId, { name })
      if (res.data) await issueApi.addTag(deps.issue.value!.id, res.data.id)
      await deps.loadAll()
    } catch (e) { handleApiError(e, '操作失败') }
  }

  // ============ Comments ============
  async function onAddComment(content: string, visibleToGroupIds?: string[]) {
    try {
      await issueApi.addComment(deps.issue.value!.id, content, visibleToGroupIds)
      await deps.loadAll()
      Message.success('评论已发布')
    } catch (e) {
      handleApiError(e, '评论失败')
      throw e // Re-throw so CommentInput can keep editor open on failure
    }
  }

  async function onEditComment(commentId: string, content: string) {
    try { await issueApi.updateComment(deps.issue.value!.id, commentId, content); await deps.loadAll(); Message.success('评论已更新') }
    catch (e) { handleApiError(e, '编辑评论失败') }
  }

  async function onDeleteComment(commentId: string) {
    try { await issueApi.deleteComment(deps.issue.value!.id, commentId); await deps.loadAll(); Message.success('评论已删除') }
    catch (e) { handleApiError(e, '删除评论失败') }
  }

  async function onRestoreComment(commentId: string) {
    try { await issueApi.restoreComment(deps.issue.value!.id, commentId); await deps.loadAll(); Message.success('评论已还原') }
    catch (e) { handleApiError(e, '还原评论失败') }
  }

  async function onPermanentlyDeleteComment(commentId: string) {
    try { await issueApi.permanentlyDeleteComment(deps.issue.value!.id, commentId); await deps.loadAll(); Message.success('评论已永久删除') }
    catch (e) { handleApiError(e, '永久删除评论失败') }
  }

  // ============ Status transition ============
  async function onQuickActionExecuted() { await deps.loadAll() }

  async function onTransition(target: StatusInfo) {
    // 判断是否为需要额外输入的复杂转换（需要填写评论或有必填字段）
    const needsModal = target.requireComment || (target.requiredFieldIds && target.requiredFieldIds.length > 0)
    if (needsModal) {
      // 复杂转换：弹出确认弹窗让用户填写必要信息
      transitionTarget.value = target
      transitionRequireComment.value = target.requireComment || false
      showTransitionModal.value = true
    } else {
      // 简单转换：直接执行，无需确认弹窗（YouTrack 标准行为）
      await executeTransition(target, undefined, undefined, undefined, false)
    }
  }

  async function onTransitionConfirm(comment: string, assigneeId: string | undefined, assigneeExplicit: boolean, customFieldValues?: Record<string, string>) {
    showTransitionModal.value = false
    if (transitionTarget.value) {
      await executeTransition(transitionTarget.value, comment || undefined, undefined, assigneeId, assigneeExplicit, customFieldValues)
    }
  }

  async function executeTransition(
    target: StatusInfo,
    comment: string | undefined,
    forceFlags?: { force?: boolean; forceWip?: boolean; forceDescEmpty?: boolean },
    assigneeId?: string,
    assigneeExplicit?: boolean,
    customFieldValues?: Record<string, string>
  ) {
    const { refresh: refreshNavBadge } = useNavBadge()
    try {
      const res = await issueApi.transitStatus(
        deps.issue.value!.id, target.id, comment, deps.issue.value!.version,
        forceFlags?.force, forceFlags?.forceWip, forceFlags?.forceDescEmpty,
        assigneeId, assigneeExplicit, customFieldValues
      )
      if (res.code === 0) {
        const actionResult = res.data?.actionResult
        if (actionResult?.outcome === 'FIELD_VALIDATION_FAILED') {
          const fieldKey = actionResult.requiredFieldId ? `cf_${actionResult.requiredFieldId}` : null
          Modal.warning({
            title: '字段校验',
            content: actionResult.warningMessage || `请先填写「${actionResult.requiredFieldName}」字段`,
            okText: '前往填写', cancelText: '知道了', hideCancel: false,
            onOk: () => {
              if (fieldKey) {
                if (deps.sidebarCollapsed.value) {
                  deps.sidebarCollapsed.value = false
                  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, 'false')
                }
                nextTick(() => { deps.sidebarRef.value?.highlightField(fieldKey) })
              }
            }
          })
          return
        }
        await deps.loadAll()
        Message.success(`状态已变更为 ${target.name}`)
        showActionFeedback(res.data)
        refreshNavBadge()
      } else if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
        Modal.warning({ title: 'WIP 限制', content: res.message, okText: '继续移入', cancelText: '取消', hideCancel: false,
          onOk: () => executeTransition(target, comment, { ...forceFlags, forceWip: true }, assigneeId, assigneeExplicit)
        })
      } else if (res.code === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) {
        Modal.warning({ title: '确认关闭', content: res.message, okText: '强制关闭', cancelText: '取消', hideCancel: false,
          onOk: () => executeTransition(target, comment, { ...forceFlags, force: true }, assigneeId, assigneeExplicit)
        })
      } else if (res.code === ERROR_CODES.DESCRIPTION_EMPTY_WARNING) {
        Modal.warning({ title: '工单描述为空', content: res.message, okText: '继续变更', cancelText: '取消', hideCancel: false,
          onOk: () => executeTransition(target, comment, { ...forceFlags, forceDescEmpty: true }, assigneeId, assigneeExplicit)
        })
      } else {
        Message.error(res.message || '变更失败')
      }
    } catch (e: any) {
      const errorCode = e.response?.data?.code
      const errorMessage = e.response?.data?.message
      if (errorCode === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
        Modal.warning({ title: 'WIP 限制', content: errorMessage, okText: '继续移入', cancelText: '取消', hideCancel: false,
          onOk: () => executeTransition(target, comment, { ...forceFlags, forceWip: true }, assigneeId, assigneeExplicit)
        })
      } else if (errorCode === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) {
        Modal.warning({ title: '确认关闭', content: errorMessage, okText: '强制关闭', cancelText: '取消', hideCancel: false,
          onOk: () => executeTransition(target, comment, { ...forceFlags, force: true }, assigneeId, assigneeExplicit)
        })
      } else if (errorCode === ERROR_CODES.DESCRIPTION_EMPTY_WARNING) {
        Modal.warning({ title: '工单描述为空', content: errorMessage, okText: '继续变更', cancelText: '取消', hideCancel: false,
          onOk: () => executeTransition(target, comment, { ...forceFlags, forceDescEmpty: true }, assigneeId, assigneeExplicit)
        })
      } else {
        handleUpdateError(e, '变更失败')
      }
    }
  }

  // ============ Sidebar field edit ============
  async function onEditField(key: string, newValue: string | string[]) {
    if (key.startsWith('cf_')) {
      const fieldId = key.substring(3)
      try {
        const res = await issueApi.updateCustomFieldValue(deps.issue.value!.id, fieldId, newValue)
        await deps.loadAll()
        if (res.warnings?.length) { res.warnings.forEach((w: string) => Message.info({ content: w, duration: 5000 })) }
        Message.success('已更新')
      } catch (e: any) { handleUpdateError(e) }
      return
    }

    if (key === 'visibility') {
      const visValue = Array.isArray(newValue) ? newValue[0] : newValue
      await onUpdateVisibility(visValue as string, [])
      return
    }

    const fieldMap: Record<string, string> = { priority: 'priority', issueType: 'issueType', assignee: 'assigneeId', sprint: 'sprintId', dueDate: 'dueDate', estimatedHours: 'estimatedHours' }
    const prop = fieldMap[key]
    if (!prop) return

    const val = prop === 'estimatedHours'
      ? (newValue ? Number(newValue) : null)
      : prop === 'sprintId'
        ? (newValue || '0')  // Backend expects "0" to clear sprint (null means "don't change")
        : (newValue || null)

    try {
      if (key === 'assignee' && newValue) { await issueApi.assign(deps.issue.value!.id, newValue as string) }
      else {
        const res = await issueApi.update(deps.issue.value!.id, { [prop]: val, version: deps.issue.value!.version })
        if (res.data?.statusAutoReset) { Message.warning({ content: '类型变更导致状态与工作流不兼容，已自动重置为默认状态', duration: 5000 }) }
        if (res.warnings?.length) { res.warnings.forEach((w: string) => Message.warning({ content: w, duration: 5000 })) }
      }
      await deps.loadAll()
      Message.success('已更新')
    } catch (e: any) { handleUpdateError(e) }
  }

  async function onClearField(key: string) {
    if (!deps.issue.value) return
    const clearKeyMap: Record<string, string> = { dueDate: 'clearDueDate', estimatedHours: 'clearEstimatedHours' }
    const clearProp = clearKeyMap[key]
    if (!clearProp) return
    try {
      await issueApi.update(deps.issue.value.id, { [clearProp]: true, version: deps.issue.value.version })
      await deps.loadAll()
      Message.success('已清除')
    } catch (e: any) { handleUpdateError(e) }
  }

  async function onAddOption(fieldId: string, value: string) {
    if (!deps.issue.value) return
    try {
      await customFieldApi.addOption(deps.issue.value.projectId, fieldId, { value })
      Message.success(`已添加选项"${value}"`)
      // Caller should refresh custom field defs
      await deps.loadAll()
    } catch (e) { handleApiError(e, '添加选项失败') }
  }

  async function onUpdateVisibility(visibility: string, userIds: string[] = []) {
    if (!deps.issue.value) return
    try {
      await issueApi.update(deps.issue.value.id, { visibility, visibilityUserIds: userIds, version: deps.issue.value.version })
      await deps.loadAll()
      Message.success('可见性已更新')
    } catch (e: any) { handleUpdateError(e, '更新可见性失败') }
  }

  // ============ Time entry ============

  async function handleStartTimer() {
    if (!deps.issue.value) return
    if (timerStore.isRunning) { Message.warning('已有活跃计时器，请先停止当前计时器'); return }
    const result = await timerStore.startTimer(deps.issue.value.id)
    if (result.success) { Message.success('计时器已启动') }
    else { Message.error(result.message || '启动计时器失败') }
  }

  async function handleStopTimerFromDetail() {
    const result = await timerStore.stopTimer()
    if (result.success) { Message.success('计时器已停止'); await deps.loadAll() }
    else { Message.error(result.message || '停止计时器失败') }
  }

  // ============ Paste upload ============
  function onPasteUpload(e: ClipboardEvent) {
    const target = e.target as HTMLElement
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) return
    if (!deps.issue.value || !deps.canEditIssueEffective.value) return
    const items = e.clipboardData?.items
    if (!items) return
    for (const item of Array.from(items)) {
      if (item.type.startsWith('image/')) {
        const file = item.getAsFile()
        if (file) {
          e.preventDefault()
          const name = `paste-${Date.now()}.${item.type.split('/')[1] || 'png'}`
          const namedFile = new File([file], name, { type: file.type })
          doUploadFile(namedFile, undefined)
        }
        break
      }
    }
  }

  // ============ Utils ============
  function handleUpdateError(e: any, fallbackMsg = '更新失败') {
    if (e.response?.status === 409) {
      Message.warning({ content: '该工单已被其他人修改，正在刷新...', duration: 3000 })
      deps.loadAll()
    } else {
      Message.error(e.response?.data?.message || fallbackMsg)
    }
  }

  return {
    // Transition state
    showTransitionModal, transitionTarget, transitionRequireComment,
    // Create panel
    showCreatePanel, cloneData, createSubtaskParentId,
    // Modals
    showAddLinkModal, showMoveModal, moveModalRef, showPrivacyModal,
    // Time
    showTimeDialog, timeSaving, timeDeleting, editingTimeEntry,

    // Actions
    copyIssue, onCopyId, onCloneIssue, onCreateSubtask,
    openAddLinkModal, onLinked, onDeleteLink,
    triggerUpload, onPrivacyGroupConfirm, onDropFiles, onDeleteAttachment, onDeleteAllAttachments,
    onDeleteIssue, onMoveIssue, onMoveConfirm,
    onCreatePanelClose, onIssueCreated, onCreatePanelExpand,
    onUpdateTitle, onUpdateDesc,
    onRemoveTag, onAddTag, onAddTags, onCreateTag,
    onAddComment, onEditComment, onDeleteComment, onRestoreComment, onPermanentlyDeleteComment,
    onQuickActionExecuted, onTransition, onTransitionConfirm,
    onEditField, onClearField, onAddOption,
    openTimeDialog, handleStartTimer, handleStopTimerFromDetail, submitTimeEntry, deleteTimeEntry,
    onPasteUpload,
    handleUpdateError,
  }
}
