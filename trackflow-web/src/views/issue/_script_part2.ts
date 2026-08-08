// ===== Inline Edit State =====
const statusDropdowns = reactive<Record<string, boolean>>({})
const assigneeDropdowns = reactive<Record<string, boolean>>({})
const sprintDropdowns = reactive<Record<string, boolean>>({})
const priorityDropdowns = reactive<Record<string, boolean>>({})
const transitionsLoading = reactive<Record<string, boolean>>({})
const availableTransitions = reactive<Record<string, IssueStatusVO[]>>({})
const assigneeSearch = ref('')
const assigneeOptions = ref<ProjectMemberVO[]>([])
const assigneeOptionsLoading = ref(false)
const sprintOptionsLoading = reactive<Record<string, boolean>>({})

// Badge fields
import type { BadgeFieldConfig, BadgeColorRule } from './components/badgeTypes'
const badgeFieldsMap = reactive<Record<string, BadgeFieldConfig[]>>({})
const badgeFieldsLoadedProjects = new Set<string>()

async function loadBadgeFields(projectIds: string[]) {
  const toLoad = projectIds.filter(pid => pid && !badgeFieldsLoadedProjects.has(pid))
  if (toLoad.length === 0) return
  for (const pid of toLoad) {
    badgeFieldsLoadedProjects.add(pid)
    try {
      const res = await customFieldApi.listByProject(pid)
      const fields = (res.data || []).filter((f: any) => f.showAsBadge && (f.fieldFormat === 'int' || f.fieldFormat === 'integer'))
      if (fields.length > 0) {
        badgeFieldsMap[pid] = fields.slice(0, 2).map((f: any) => {
          let colorRules: BadgeColorRule[] | null = null
          if (f.badgeColorRules) { try { colorRules = JSON.parse(f.badgeColorRules) } catch { /* ignore */ } }
          return { fieldId: f.id, fieldName: f.name, colorRules }
        })
      }
    } catch { /* non-critical */ }
  }
}
function loadBadgeFieldsForIssues() {
  const projectIds = [...new Set(issues.value.map(i => i.projectId).filter(Boolean))]
  if (projectIds.length > 0) loadBadgeFields(projectIds)
}

// Load priority/type options when project changes
watch(activeProjectId, async (projectId) => {
  if (projectId) {
    const loaded = await loadPriorityOptions(projectId)
    priorityOptions.value = loaded.map(o => ({ value: o.value, label: o.label, color: o.color || DEFAULT_PRIORITY_COLOR }))
    const loadedTypes = await loadIssueTypeOptions(projectId)
    issueTypeOptions.value = loadedTypes.map(o => ({ value: o.value, label: o.label, color: o.color || DEFAULT_ISSUE_TYPE_COLOR }))
  }
}, { immediate: true })

function getIssueTypeColorForRecord(issueType: string | null | undefined): string {
  const t = issueType || '任务'
  const opt = issueTypeOptions.value.find(o => o.value === t || o.value.toLowerCase() === t.toLowerCase())
  return opt?.color || DEFAULT_ISSUE_TYPE_COLOR
}
function getIssueTypeLabelForRecord(issueType: string | null | undefined): string {
  if (!issueType) return '未知'
  const opt = issueTypeOptions.value.find(o => o.value === issueType || o.value.toLowerCase() === issueType.toLowerCase())
  return opt?.label || issueType
}

// ===== Inline Edit Handlers =====
async function openStatusEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'statusId')) return
  statusDropdowns[issue.id] = true
  transitionsLoading[issue.id] = true
  try { const res = await issueApi.getAvailableTransitions(issue.id); availableTransitions[issue.id] = res.data || [] }
  catch { availableTransitions[issue.id] = []; Message.error({ content: '获取可用状态失败', duration: 3000 }); statusDropdowns[issue.id] = false }
  finally { transitionsLoading[issue.id] = false }
}

function selectStatus(issue: IssueVO, status: IssueStatusVO) {
  statusDropdowns[issue.id] = false
  if (status.requireComment) {
    let commentText = ''
    Modal.confirm({
      title: '状态变更 — 请填写理由',
      content: () => h('div', { style: 'display:flex;flex-direction:column;gap:8px' }, [
        h('div', { style: 'display:flex;align-items:center;gap:6px' }, [
          h('span', { style: 'color:var(--color-text-3);font-size:13px' }, '目标状态：'),
          h('span', { style: `background:${status.color};color: var(--tf-text-on-accent);padding:2px 8px;border-radius:3px;font-size:12px` }, localizeStatusName(status.name))
        ]),
        h('textarea', { placeholder: '请说明退回/变更的原因（必填）', style: 'width:100%;min-height:80px;margin-top:8px;padding:8px;border:1px solid var(--color-border-2);border-radius:4px;resize:vertical;font-size:13px;background:var(--color-bg-2);color:var(--color-text-1)', onInput: (e: Event) => { commentText = (e.target as HTMLTextAreaElement).value } })
      ]),
      okText: '确认变更', cancelText: '取消', width: 480,
      onBeforeOk: () => { if (!commentText.trim()) { Message.warning('请填写变更理由'); return false }; return true },
      onOk: () => { performStatusTransition(issue, status, commentText.trim()) }
    })
  } else { performStatusTransition(issue, status, undefined) }
}

async function performStatusTransition(issue: IssueVO, status: IssueStatusVO, comment?: string, forceFlags?: { force?: boolean; forceWip?: boolean; forceDescEmpty?: boolean }) {
  const oldStatusId = issue.statusId
  issue.statusId = status.id
  try {
    const res = await issueApi.transitStatus(issue.id, status.id, comment, issue.version, forceFlags?.force, forceFlags?.forceWip, forceFlags?.forceDescEmpty)
    if (res.code === 0) {
      const actionResult = res.data?.actionResult
      if (actionResult?.outcome === 'FIELD_VALIDATION_FAILED') {
        issue.statusId = oldStatusId
        Modal.warning({ title: '字段校验', content: actionResult.warningMessage || `请先填写「${actionResult.requiredFieldName}」字段`, okText: '打开详情填写', cancelText: '知道了', hideCancel: false, onOk: () => { router.push(`/issues/${issue.issueKey}`) } })
        return
      }
      if (res.data != null) { const version = extractVersion(res.data); if (version != null) issue.version = version; else issue.version = (issue.version || 0) + 1; showActionFeedback(res.data) }
      onInlineEditSuccess(issue, 'statusId', status.id)
      return
    }
    issue.statusId = oldStatusId
    if (res.code === ERROR_CODES.DESCRIPTION_EMPTY_WARNING) { Modal.warning({ title: '工单描述为空', content: res.message, okText: '继续变更', cancelText: '取消', hideCancel: false, onOk: () => performStatusTransition(issue, status, comment, { ...forceFlags, forceDescEmpty: true }) }) }
    else if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) { Modal.warning({ title: 'WIP 限制', content: res.message, okText: '继续移入', cancelText: '取消', hideCancel: false, onOk: () => performStatusTransition(issue, status, comment, { ...forceFlags, forceWip: true }) }) }
    else if (res.code === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) { Modal.warning({ title: '确认关闭', content: res.message, okText: '强制关闭', cancelText: '取消', hideCancel: false, onOk: () => performStatusTransition(issue, status, comment, { ...forceFlags, force: true }) }) }
    else { Message.error({ content: res.message || '状态变更失败', duration: 3000 }) }
  } catch (e: any) { issue.statusId = oldStatusId; Message.error({ content: e.response?.data?.message || '状态变更失败', duration: 3000 }) }
}

async function openAssigneeEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'assigneeId')) return
  assigneeDropdowns[issue.id] = true; assigneeSearch.value = ''; assigneeOptionsLoading.value = true
  try { const res = await projectApi.listAssignableMembers(issue.projectId); assigneeOptions.value = res.data || [] }
  catch { assigneeOptions.value = [] }
  finally { assigneeOptionsLoading.value = false }
}
const filteredAssigneeOptions = computed(() => {
  if (!assigneeSearch.value) return assigneeOptions.value
  const kw = assigneeSearch.value.toLowerCase()
  return assigneeOptions.value.filter(m => m.displayName?.toLowerCase().includes(kw))
})
function selectAssignee(issue: IssueVO, member: ProjectMemberVO | null) {
  Object.keys(assigneeDropdowns).forEach(k => { assigneeDropdowns[k] = false })
  executeEdit(issue.id, 'assigneeId', member?.userId || null, (_signal) => issueApi.assign(issue.id, member?.userId || ''), () => ({ assigneeId: member?.userId || undefined, assigneeName: member?.displayName || undefined }), onInlineEditSuccess)
}

async function openSprintEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'sprintId')) return
  sprintDropdowns[issue.id] = true
  if (!sprintOptionsCache[issue.projectId]) {
    sprintOptionsLoading[issue.id] = true
    try { const res = await sprintApi.listByProject(issue.projectId, { _silent403: true }); sprintOptionsCache[issue.projectId] = res.data?.list || [] }
    catch { sprintOptionsCache[issue.projectId] = [] }
    finally { sprintOptionsLoading[issue.id] = false }
  }
}
function getSprintGroups(projectId: string) {
  const sprints = sprintOptionsCache[projectId] || []
  const groups: { label: string; items: SprintVO[] }[] = []
  const active = sprints.filter(s => s.status?.toLowerCase() === 'active')
  const planned = sprints.filter(s => s.status?.toLowerCase() === 'planned')
  const completed = sprints.filter(s => s.status?.toLowerCase() === 'completed')
  if (active.length) groups.push({ label: '进行中', items: active })
  if (planned.length) groups.push({ label: '计划中', items: planned })
  if (completed.length) groups.push({ label: '已完成', items: completed })
  return groups
}
function selectSprint(issue: IssueVO, sprint: SprintVO | null) { sprintDropdowns[issue.id] = false; executeEdit(issue.id, 'sprintId', sprint?.id || null, (_signal) => issueApi.update(issue.id, { sprintId: sprint?.id || null, version: issue.version }), undefined, onInlineEditSuccess) }
function selectPriority(issue: IssueVO, priority: string) { priorityDropdowns[issue.id] = false; executeEdit(issue.id, 'priority', priority, (_signal) => issueApi.update(issue.id, { priority, version: issue.version }), undefined, onInlineEditSuccess) }

// List layout Sprint inline edit
const listSprintLoadingIds = reactive<Set<string>>(new Set())
async function onListSprintEdit(issue: IssueVO) {
  if (!issue.projectId) return
  if (sprintOptionsCache[issue.projectId] !== undefined) return
  listSprintLoadingIds.add(issue.id)
  try { const res = await sprintApi.listByProject(issue.projectId, { _silent403: true }); sprintOptionsCache[issue.projectId] = res.data?.list || [] }
  catch { sprintOptionsCache[issue.projectId] = [] }
  finally { listSprintLoadingIds.delete(issue.id) }
}
function onListSprintSelect(issue: IssueVO, sprint: SprintVO | null) {
  const newSprintId = sprint?.id ?? null; const newSprintName = sprint?.name ?? null
  executeEdit(issue.id, 'sprintId', newSprintId, (_signal) => issueApi.update(issue.id, { sprintId: newSprintId, version: issue.version }), (_iss) => ({ sprintId: newSprintId ?? undefined, sprintName: newSprintName ?? undefined }), onInlineEditSuccess)
}

// ===== Batch Operations =====
async function onBatchState(statusId: string) { const result = await batchTransitStatus(selectedIssues.value, statusId); if (result.succeeded > 0) { selectedIssues.value.forEach(issue => { if (!result.failures.find(f => f.issueId === issue.id)) updateLocalIssue(issue.id, { statusId }) }); useNavBadge().refresh() }; clearSelection() }
async function onBatchAssign(assigneeId: string | null) { await batchAssign(selectedIssues.value, assigneeId || ''); refreshList(); clearSelection() }
async function onBatchSprint(sprintId: string | null) { const result = await batchUpdateSprint(selectedIssues.value, sprintId); if (result.succeeded > 0) selectedIssues.value.forEach(issue => { if (!result.failures.find(f => f.issueId === issue.id)) updateLocalIssue(issue.id, { sprintId: sprintId || undefined }) }); clearSelection() }
async function onBatchPriority(priority: string) { const result = await batchUpdatePriority(selectedIssues.value, priority); if (result.succeeded > 0) selectedIssues.value.forEach(issue => { if (!result.failures.find(f => f.issueId === issue.id)) updateLocalIssue(issue.id, { priority }) }); clearSelection() }
async function onBatchTagAdd(tagId: string) { const result = await batchTagAdd(selectedIssues.value, tagId); if (result.succeeded > 0) refreshList(); clearSelection() }
async function onBatchTagRemove(tagId: string) { const result = await batchTagRemove(selectedIssues.value, tagId); if (result.succeeded > 0) refreshList(); clearSelection() }
async function onBatchLink(linkType: string, targetIssueId: string) { const result = await batchAddLink(selectedIssues.value, linkType, targetIssueId); if (result.succeeded > 0) refreshList(); clearSelection() }
async function onBatchDelete() { const result = await batchDelete(selectedIssues.value); if (result.succeeded > 0) refreshList(); clearSelection() }
function onCommandExecuted() { refreshList(); clearSelection() }

// ===== Filter match check =====
function checkIssueMatchesFilter(issue: IssueVO): boolean {
  const fp = globalFilterParams.value
  if (fp.assigneeId === 'none' && issue.assigneeId) return false
  if (fp.assigneeId && fp.assigneeId !== 'none' && issue.assigneeId !== fp.assigneeId) return false
  if (fp.statusId) { const statusIds = String(fp.statusId).split(','); if (!statusIds.includes(String(issue.statusId))) return false }
  if (fp.sprintId) { if (fp.sprintId === 'none' && issue.sprintId) return false; if (fp.sprintId !== 'none' && issue.sprintId !== fp.sprintId) return false }
  if (fp.priority && issue.priority !== fp.priority) return false
  if (fp.issueType && issue.issueType !== fp.issueType) return false
  if ((fp.hideResolved === 'true' || hideResolved.value) && statusCache.value.find(s => s.id === issue.statusId)?.isClosed) return false
  return true
}
function onInlineEditSuccess(issue: IssueVO, field: string, _newValue: any) {
  if (!checkIssueMatchesFilter(issue)) removeLocalIssue(issue.id)
  if (field === 'statusId') useNavBadge().refresh()
}

// ===== Helpers =====
const selectedKeysArray = computed(() => [...selectedIds.value])
function getCustomFieldDetail(record: any, dataIndex: string): CustomFieldValueVO | undefined {
  if (!record.customFieldDetails || !dataIndex?.startsWith('cf_')) return undefined
  const fieldId = dataIndex.substring(3)
  return record.customFieldDetails.find((d: CustomFieldValueVO) => d.customFieldId === fieldId)
}
function formatCount(count: number) { if (count >= 10000) return Math.floor(count / 1000) + 'k+'; if (count >= 1000) return (count / 1000).toFixed(1) + 'k'; return String(count) }
function formatTime(dt: string) { if (!dt) return ''; const d = new Date(dt); const now = new Date(); const diff = now.getTime() - d.getTime(); const mins = Math.floor(diff / 60000); if (mins < 60) return `${mins}分钟前`; const hours = Math.floor(mins / 60); if (hours < 24) return `${hours}小时前`; const days = Math.floor(hours / 24); if (days < 30) return `${days}天前`; return d.toLocaleDateString('zh-CN') }

// ===== Quick Create =====
const showInlineCreate = ref(false)
const quickCreating = ref(false)
const quickForm = reactive({ projectId: undefined as string | undefined, title: '', issueType: '任务', priority: '普通' })
const QUICK_CREATE_PROJECT_KEY = 'trackflow:quick-create-project'

function resolveQuickCreateProject(): string | undefined {
  if (activeProjectId.value) return activeProjectId.value
  if (filterProject.value) return filterProject.value
  const lastUsed = localStorage.getItem(QUICK_CREATE_PROJECT_KEY)
  if (lastUsed && projectList.value.some(p => p.id === lastUsed)) return lastUsed
  if (projectList.value.length === 1) return projectList.value[0].id
  return undefined
}
function toggleInlineCreate() { showInlineCreate.value = !showInlineCreate.value; if (showInlineCreate.value) quickForm.projectId = resolveQuickCreateProject() }
async function quickCreate() {
  if (!quickForm.projectId) { Message.warning('请先选择项目'); return }
  if (!quickForm.title.trim()) { Message.warning('请输入工单标题'); return }
  quickCreating.value = true
  try { await issueApi.create({ projectId: quickForm.projectId, title: quickForm.title.trim(), issueType: quickForm.issueType, priority: quickForm.priority }); Message.success('工单创建成功'); localStorage.setItem(QUICK_CREATE_PROJECT_KEY, quickForm.projectId); quickForm.title = ''; refreshList() }
  catch (e: any) { Message.error(e.response?.data?.message || '创建失败') }
  finally { quickCreating.value = false }
}
