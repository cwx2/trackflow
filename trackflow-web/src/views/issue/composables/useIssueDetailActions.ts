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
import { ref, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { issueApi, tagApi, customFieldApi, timeEntryApi } from '@/api'
import { showActionFeedback } from '@/utils/transition'
import { ERROR_CODES } from '@/api/error-codes'
import { useNavBadge } from '@/composables/useNavBadge'
import { useTimerStore } from '@/stores/timer'
import { useDrafts } from './useDrafts'
import type { IssueDetailVO, ProjectMemberVO } from '@/api/types'
import type { StatusInfo } from '../components/DetailSidebar.vue'

interface ActionDeps {
  issue: { value: IssueDetailVO | null }
  loadAll: () => Promise<void>
  loadAttachments: () => Promise<void>
  loadLinks: () => Promise<void>
  canEditIssueEffective: { value: boolean }
  hasProjectPermission: (perm: string) => boolean
  currentUserId: { value: string }
  sidebarCollapsed: { value: boolean }
  sidebarRef: { value: any }
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
    } catch (e: any) { Message.error(e?.response?.data?.message || '删除关联失败') }
  }

  // ============ Move modal ============
  const showMoveModal = ref(false)
  const moveModalRef = ref<any>(null)

  // ============ Privacy modal ============
  const showPrivacyModal = ref(false)

  // ============ Time entry dialog ============
  const showTimeDialog = ref(false)
  const timeSaving = ref(false)
  const timeForm = ref({ workDate: new Date().toISOString().slice(0, 10), durationText: '', description: '' })
  const timeFormAttrValues = ref<Record<string, string>>({})
  const timeFormAuthorId = ref<string>('')

  // ============ Copy / Clone ============
  function copyIssue() {
    navigator.clipboard.writeText(`${deps.issue.value?.issueKey} ${deps.issue.value?.title}`)
    Message.success('已复制')
  }

  function onCopyId() {
    if (!deps.issue.value) return
    navigator.clipboard.writeText(deps.issue.value.issueKey)
    Message.success(`已复制 ${deps.issue.value.issueKey}`)
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
    } catch (e: any) { Message.error(e.response?.data?.message || `${file.name} 上传失败`) }
  }

  async function onDeleteAttachment(attachmentId: string) {
    if (!deps.issue.value) return
    try {
      await issueApi.deleteAttachment(deps.issue.value.id, attachmentId)
      Message.success('附件已删除')
      deps.loadAttachments()
    } catch (e: any) { Message.error(e.response?.data?.message || '删除附件失败') }
  }

  async function onDeleteAllAttachments() {
    if (!deps.issue.value) return
    try {
      for (const att of []) { /* placeholder - caller passes attachments */ }
      // This needs the attachments list - call from component
    } catch { /* ignore */ }
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
        } catch (e: any) { Message.error(e.response?.data?.message || '删除失败') }
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
    } catch (e: any) {
      Message.error(e.response?.data?.message || '移动失败')
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
    catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
  }

  async function onAddTag(tag: { id: string }) {
    try { await issueApi.addTag(deps.issue.value!.id, tag.id); await deps.loadAll() }
    catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
  }

  async function onAddTags(tags: { id: string }[]) {
    try { await issueApi.addTags(deps.issue.value!.id, tags.map(t => t.id)); await deps.loadAll() }
    catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
  }

  async function onCreateTag(name: string) {
    try {
      const res = await tagApi.createProjectTag(deps.issue.value!.projectId, { name })
      if (res.data) await issueApi.addTag(deps.issue.value!.id, res.data.id)
      await deps.loadAll()
    } catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
  }

  // ============ Comments ============
  async function onAddComment(content: string, visibleToGroupIds?: string[]) {
    try {
      await issueApi.addComment(deps.issue.value!.id, content, visibleToGroupIds)
      await deps.loadAll()
      Message.success('评论已发布')
    } catch (e: any) { Message.error(e.response?.data?.message || '评论失败') }
  }

  async function onEditComment(commentId: string, content: string) {
    try { await issueApi.updateComment(deps.issue.value!.id, commentId, content); await deps.loadAll(); Message.success('评论已更新') }
    catch (e: any) { Message.error(e.response?.data?.message || '编辑评论失败') }
  }

  async function onDeleteComment(commentId: string) {
    try { await issueApi.deleteComment(deps.issue.value!.id, commentId); await deps.loadAll(); Message.success('评论已删除') }
    catch (e: any) { Message.error(e.response?.data?.message || '删除评论失败') }
  }

  async function onRestoreComment(commentId: string) {
    try { await issueApi.restoreComment(deps.issue.value!.id, commentId); await deps.loadAll(); Message.success('评论已还原') }
    catch (e: any) { Message.error(e.response?.data?.message || '还原评论失败') }
  }

  async function onPermanentlyDeleteComment(commentId: string) {
    try { await issueApi.permanentlyDeleteComment(deps.issue.value!.id, commentId); await deps.loadAll(); Message.success('评论已永久删除') }
    catch (e: any) { Message.error(e.response?.data?.message || '永久删除评论失败') }
  }

  // ============ Status transition ============
  async function onQuickActionExecuted() { await deps.loadAll() }

  async function onTransition(target: StatusInfo) {
    if (target.requireComment) {
      transitionTarget.value = target
      transitionRequireComment.value = true
      showTransitionModal.value = true
      return
    }
    await executeTransition(target, undefined)
  }

  async function onTransitionConfirm(comment: string, assigneeId: string | undefined, assigneeExplicit: boolean) {
    showTransitionModal.value = false
    if (transitionTarget.value) {
      await executeTransition(transitionTarget.value, comment || undefined, undefined, assigneeId, assigneeExplicit)
    }
  }

  async function executeTransition(
    target: StatusInfo,
    comment: string | undefined,
    forceFlags?: { force?: boolean; forceWip?: boolean; forceDescEmpty?: boolean },
    assigneeId?: string,
    assigneeExplicit?: boolean
  ) {
    const { refresh: refreshNavBadge } = useNavBadge()
    try {
      const res = await issueApi.transitStatus(
        deps.issue.value!.id, target.id, comment, deps.issue.value!.version,
        forceFlags?.force, forceFlags?.forceWip, forceFlags?.forceDescEmpty,
        assigneeId, assigneeExplicit
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

    const val = prop === 'estimatedHours' ? (newValue ? Number(newValue) : null) : (newValue || null)

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
    } catch (e: any) { Message.error(e.response?.data?.message || '添加选项失败') }
  }

  async function onUpdateVisibility(visibility: string, userIds: string[] = []) {
    if (!deps.issue.value) return
    try {
      await issueApi.update(deps.issue.value.id, { visibility, visibilityUserIds: userIds.map(Number), version: deps.issue.value.version })
      await deps.loadAll()
      Message.success('可见性已更新')
    } catch (e: any) { handleUpdateError(e, '更新可见性失败') }
  }

  // ============ Time entry ============
  function openTimeDialog() {
    timeForm.value = { workDate: new Date().toISOString().slice(0, 10), durationText: '', description: '' }
    timeFormAttrValues.value = {}
    timeFormAuthorId.value = ''
    showTimeDialog.value = true
  }

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

  async function submitTimeEntry() {
    if (!timeForm.value.durationText) { Message.warning('请输入时长'); return }
    const duration = parseDurationText(timeForm.value.durationText)
    if (!duration || duration <= 0) { Message.warning('时长格式无效，请使用如 2h30m, 1h, 45m'); return }

    const attrVals = Object.keys(timeFormAttrValues.value).length > 0
      ? Object.fromEntries(Object.entries(timeFormAttrValues.value).filter(([, v]) => v))
      : undefined

    const forUserId = (deps.currentUserId.value && timeFormAuthorId.value && timeFormAuthorId.value !== deps.currentUserId.value)
      ? timeFormAuthorId.value
      : undefined

    timeSaving.value = true
    try {
      await timeEntryApi.create({
        issueId: deps.issue.value!.id,
        workDate: timeForm.value.workDate,
        duration,
        description: timeForm.value.description || undefined,
        forUserId,
        attributeValues: attrVals
      })
      Message.success('工时已记录')
      showTimeDialog.value = false
      await deps.loadAll()
    } catch (e: any) { Message.error(e.response?.data?.message || '记录失败') }
    finally { timeSaving.value = false }
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

  function parseDurationText(text: string): number | null {
    const cleaned = text.trim().toLowerCase()
    let total = 0
    const weekMatch = cleaned.match(/(\d+)\s*w/)
    const dayMatch = cleaned.match(/(\d+)\s*d/)
    const hourMatch = cleaned.match(/(\d+)\s*h/)
    const minMatch = cleaned.match(/(\d+)\s*m/)
    if (weekMatch) total += parseInt(weekMatch[1]) * 5 * 8 * 60
    if (dayMatch) total += parseInt(dayMatch[1]) * 8 * 60
    if (hourMatch) total += parseInt(hourMatch[1]) * 60
    if (minMatch) total += parseInt(minMatch[1])
    if (!weekMatch && !dayMatch && !hourMatch && !minMatch) {
      const num = parseFloat(cleaned)
      if (!isNaN(num)) total = Math.round(num * 60)
    }
    return total > 0 ? total : null
  }

  return {
    // Transition state
    showTransitionModal, transitionTarget, transitionRequireComment,
    // Create panel
    showCreatePanel, cloneData, createSubtaskParentId,
    // Modals
    showAddLinkModal, showMoveModal, moveModalRef, showPrivacyModal,
    // Time
    showTimeDialog, timeSaving, timeForm, timeFormAttrValues, timeFormAuthorId,

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
    openTimeDialog, handleStartTimer, handleStopTimerFromDetail, submitTimeEntry,
    onPasteUpload,
    handleUpdateError,
  }
}
