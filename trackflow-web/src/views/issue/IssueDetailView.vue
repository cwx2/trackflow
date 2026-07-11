<template>
  <div class="issue-detail-page" v-if="issue">
    <DetailTopBar
      :project-name="projectName"
      :issue-key="issue.issueKey"
      :reporter="reporterName"
      :created-ago="timeAgo(issue.createdAt)"
      :updated-ago="timeAgo(issue.updatedAt)"
      @copy="copyIssue"
      @create="showCreatePanel = true"
      @toggle-sidebar="sidebarVisible = !sidebarVisible"
    />

    <div class="page-body">
      <DetailMainContent
        :issue-key="issue.issueKey"
        :issue-type="issue.issueType"
        :title="issue.title"
        :description="issue.description || ''"
        :tags="issueTags"
        :available-tags="projectTags"
        :links="issueLinks"
        :attachments="issueAttachments"
        @update-title="onUpdateTitle"
        @update-desc="onUpdateDesc"
        @remove-tag="onRemoveTag"
        @add-tag="onAddTag"
        @create-tag="onCreateTag"
        @add-link="() => {}"
        @upload="() => {}"
      >
        <template #activity>
          <ActivityStream :items="activityItems" />
          <CommentInput @submit="onAddComment" />
        </template>
      </DetailMainContent>

      <DetailSidebar
        v-show="sidebarVisible"
        :status="currentStatus"
        :transitions="availableTransitions"
        :fields="sidebarFields"
        @transition="onTransition"
        @edit-field="onEditField"
      />
    </div>
  </div>

  <div v-else-if="loading" class="loading-page">
    <a-spin :size="28" tip="加载中..." />
  </div>

  <IssueCreatePanel v-model:visible="showCreatePanel" :project-id="issue?.projectId" />
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { renderMarkdown } from '@/utils/markdown'
import { issueApi, projectApi, sprintApi, tagApi } from '@/api'
import type { IssueDetailVO, IssueStatusVO, IssueCommentVO, IssueActivityVO, IssueAttachmentVO, IssueLinkVO, IssueTagVO, ProjectMemberVO, SprintVO } from '@/api/types'
import DetailTopBar from './components/DetailTopBar.vue'
import DetailMainContent from './components/DetailMainContent.vue'
import DetailSidebar from './components/DetailSidebar.vue'
import ActivityStream from './components/ActivityStream.vue'
import CommentInput from './components/CommentInput.vue'
import IssueCreatePanel from './IssueCreatePanel.vue'
import type { ActivityItem } from './components/ActivityStream.vue'
import type { SidebarField, StatusInfo } from './components/DetailSidebar.vue'

// ============ 尝试真实 API，失败时 fallback 到 mock ============
import {
  mockProjects, mockTags, mockSprints, mockIssues, mockUsers,
  getIssue as mockGetIssue, getUser, getStatus, getIssueTags, getIssueLinks,
  getIssueComments, getIssueActivities, getIssueAttachments as mockGetAttachments,
  addComment as mockAddComment, addIssueTag, removeIssueTag, createTag as mockCreateTag,
  updateIssueField, transitStatus as mockTransitStatus, getAvailableTransitions
} from '@/mock/data'

const route = useRoute()
const sidebarVisible = ref(true)
const showCreatePanel = ref(false)
const loading = ref(false)
const useMock = ref(true) // 默认用 mock，API 可用时自动切换

// ============ 数据 ============
const issue = ref<IssueDetailVO | null>(null)
const transitions = ref<IssueStatusVO[]>([])
const comments = ref<IssueCommentVO[]>([])
const activities = ref<IssueActivityVO[]>([])
const attachments = ref<IssueAttachmentVO[]>([])
const links = ref<IssueLinkVO[]>([])
const projectTagList = ref<IssueTagVO[]>([])
const members = ref<ProjectMemberVO[]>([])
const sprints = ref<SprintVO[]>([])

const issueId = computed(() => route.params.id as string || '1389')

// ============ 加载数据 ============
onMounted(() => loadAll())
watch(() => route.params.id, () => loadAll())

async function loadAll() {
  loading.value = true
  try {
    // 尝试真实 API
    const res = await issueApi.getById(issueId.value)
    if (res.code === 0 && res.data) {
      useMock.value = false
      issue.value = res.data
      await loadRelatedData()
      loading.value = false
      return
    }
  } catch {
    // API 不可用，fallback 到 mock
  }

  // Mock fallback
  useMock.value = true
  loadFromMock()
  loading.value = false
}

function loadFromMock() {
  const id = issueId.value
  const mockIssue = mockGetIssue(id) || mockIssues.find(i => i.issueKey === id) || mockIssues[0]
  if (!mockIssue) return

  const status = getStatus(mockIssue.statusId)
  issue.value = {
    id: mockIssue.id,
    projectId: mockIssue.projectId,
    projectName: mockProjects.find(p => p.id === mockIssue.projectId)?.name,
    issueKey: mockIssue.issueKey,
    title: mockIssue.title,
    description: mockIssue.description,
    issueType: mockIssue.issueType,
    statusId: mockIssue.statusId,
    status: status ? { id: status.id, name: status.name, code: status.code, color: status.color, category: status.category, isDefault: status.isDefault, isClosed: status.isClosed, sortOrder: 0 } : undefined,
    priority: mockIssue.priority,
    assigneeId: mockIssue.assigneeId,
    assigneeName: getUser(mockIssue.assigneeId)?.displayName,
    reporterId: mockIssue.reporterId,
    reporterName: getUser(mockIssue.reporterId)?.displayName,
    sprintId: mockIssue.sprintId,
    sprintName: mockSprints.find(s => s.id === mockIssue.sprintId)?.name,
    parentId: mockIssue.parentId,
    dueDate: mockIssue.dueDate,
    estimatedHours: mockIssue.estimatedHours,
    spentHours: mockIssue.spentHours,
    tags: getIssueTags(mockIssue.id),
    createdAt: mockIssue.createdAt,
    updatedAt: mockIssue.updatedAt,
  } as any

  projectTagList.value = mockTags.filter(t => t.projectId === mockIssue.projectId)
}

async function loadRelatedData() {
  if (!issue.value) return
  const id = issue.value.id
  const pid = issue.value.projectId
  try {
    const [transRes, commRes, actRes, attRes, linkRes, memRes, spRes, tagRes] = await Promise.allSettled([
      issueApi.getAvailableTransitions(id),
      issueApi.listComments(id),
      issueApi.listActivities(id),
      issueApi.listAttachments(id),
      issueApi.listLinks(id),
      projectApi.listMembers(pid),
      sprintApi.listByProject(pid),
      tagApi.listProjectTags(pid),
    ])
    if (transRes.status === 'fulfilled') transitions.value = transRes.value.data || []
    if (commRes.status === 'fulfilled') comments.value = commRes.value.data || []
    if (actRes.status === 'fulfilled') activities.value = actRes.value.data || []
    if (attRes.status === 'fulfilled') attachments.value = attRes.value.data || []
    if (linkRes.status === 'fulfilled') links.value = linkRes.value.data || []
    if (memRes.status === 'fulfilled') members.value = memRes.value.data || []
    if (spRes.status === 'fulfilled') sprints.value = spRes.value.data || []
    if (tagRes.status === 'fulfilled') projectTagList.value = tagRes.value.data || []
  } catch { /* ignore partial failures */ }
}

// ============ Computed ============
const projectName = computed(() => issue.value?.projectName || '')
const reporterName = computed(() => issue.value?.reporterName || '未知')

const issueTags = computed(() => {
  if (useMock.value) return getIssueTags(issue.value?.id || '')
  return issue.value?.tags || []
})

const projectTags = computed(() => projectTagList.value)

const issueLinks = computed(() => {
  if (useMock.value) {
    return getIssueLinks(issue.value?.id || '').map(l => ({ ...l, typeLabel: l.linkType }))
  }
  return links.value.map(l => ({ ...l, typeLabel: l.linkType, statusName: l.issueStatus?.name || '', statusColor: l.issueStatus?.color || '' }))
})

const issueAttachments = computed(() => {
  if (useMock.value) {
    return mockGetAttachments(issue.value?.id || '').map(a => ({ id: a.id, fileName: a.fileName, sizeText: formatSize(a.fileSize) }))
  }
  return attachments.value.map(a => ({ id: a.id, fileName: a.fileName, sizeText: formatSize(a.fileSize) }))
})

const currentStatus = computed<StatusInfo>(() => {
  if (issue.value?.status) {
    return { id: issue.value.status.id, name: issue.value.status.name, color: issue.value.status.color }
  }
  const s = getStatus(issue.value?.statusId || '1')
  return { id: s?.id || '1', name: s?.name || '未知', color: s?.color || '#666' }
})

const availableTransitions = computed<StatusInfo[]>(() => {
  if (!useMock.value) {
    return transitions.value.map(s => ({ id: s.id, name: s.name, color: s.color }))
  }
  const allowed = getAvailableTransitions(issue.value?.statusId || '1', issue.value?.issueType || 'Task')
  return allowed.map(s => ({ id: s.id, name: s.name, color: s.color }))
})

const sidebarFields = computed<SidebarField[]>(() => {
  const i = issue.value
  if (!i) return []
  const sprint = useMock.value
    ? mockSprints.find(s => s.id === i.sprintId)
    : sprints.value.find(s => s.id === i.sprintId)

  // 状态选项
  const statusOptions = [
    { value: currentStatus.value.id, label: `● ${currentStatus.value.name}（当前）` },
    ...availableTransitions.value.map(s => ({ value: s.id, label: s.name }))
  ]

  // 人员选项
  const userOptions = useMock.value
    ? mockUsers.map(u => ({ value: u.id, label: u.displayName }))
    : members.value.map(m => ({ value: m.userId, label: m.displayName }))

  // Sprint 选项
  const sprintOptions = [
    { value: '', label: 'Unscheduled' },
    ...(useMock.value ? mockSprints : sprints.value).filter((s: any) => s.projectId === i.projectId).map((s: any) => ({ value: s.id, label: s.name }))
  ]

  return [
    { key: 'project', label: '项目', value: projectName.value, readonly: true },
    { key: 'priority', label: '优先级', value: i.priority, dot: priorityDot(i.priority), editType: 'select' as const, rawValue: i.priority, options: [
      { value: 'Critical', label: 'Critical' }, { value: 'High', label: 'High' },
      { value: 'Normal', label: 'Normal' }, { value: 'Low', label: 'Low' },
    ]},
    { key: 'state', label: '状态', value: currentStatus.value.name, dot: currentStatus.value.color, editType: 'select' as const, rawValue: currentStatus.value.id, options: statusOptions },
    { key: 'issueType', label: '类型', value: i.issueType, editType: 'select' as const, rawValue: i.issueType, options: [
      { value: 'Bug', label: 'Bug' }, { value: 'Task', label: 'Task' },
      { value: 'Feature', label: 'Feature' }, { value: 'Epic', label: 'Epic' }, { value: 'Story', label: 'Story' },
    ]},
    { key: 'assignee', label: '负责人', value: i.assigneeName || '未分配', editType: 'user-select' as const, rawValue: i.assigneeId || '', options: userOptions },
    { key: 'reporter', label: '报告人', value: reporterName.value, readonly: true },
    { key: 'sprint', label: '迭代', value: sprint?.name || 'Unscheduled', editType: 'select' as const, rawValue: i.sprintId || '', options: sprintOptions },
    { key: 'dueDate', label: '截止日期', value: i.dueDate || '-', editType: 'date' as const, rawValue: i.dueDate || '' },
    { key: 'estimatedHours', label: '预估工时', value: i.estimatedHours ? `${i.estimatedHours}h` : '-', editType: 'number' as const, rawValue: i.estimatedHours ? String(i.estimatedHours) : '' },
    { key: 'spentHours', label: '已花时间', value: i.spentHours ? `${i.spentHours}h` : '-', readonly: true },
    { key: '_sep', label: '', value: '', readonly: true },
    { key: 'createdAt', label: '创建时间', value: formatDateTime(i.createdAt), readonly: true },
    { key: 'updatedAt', label: '更新时间', value: formatDateTime(i.updatedAt), readonly: true },
  ]
})

const activityItems = computed<ActivityItem[]>(() => {
  const items: ActivityItem[] = []

  if (useMock.value) {
    const mockComments = getIssueComments(issue.value?.id || '')
    const mockActivities = getIssueActivities(issue.value?.id || '')
    for (const c of mockComments) {
      const isHtml = c.content.trim().startsWith('<')
      items.push({ id: 'c_' + c.id, type: 'comment', user: c.userName, html: isHtml ? c.content : renderMarkdown(c.content), timeAgo: timeAgo(c.createdAt), ts: new Date(c.createdAt).getTime() })
    }
    for (const a of mockActivities) {
      if (a.action === 'commented') continue
      items.push({ id: 'a_' + a.id, type: 'change', user: a.userName, action: a.action, field: a.fieldName || undefined, from: a.oldValue || undefined, to: a.newValue || undefined, timeAgo: timeAgo(a.createdAt), ts: new Date(a.createdAt).getTime() })
    }
  } else {
    for (const c of comments.value) {
      const isHtml = c.content.trim().startsWith('<')
      items.push({ id: 'c_' + c.id, type: 'comment', user: c.userName || '用户', html: isHtml ? c.content : renderMarkdown(c.content), timeAgo: timeAgo(c.createdAt), ts: new Date(c.createdAt).getTime() })
    }
    for (const a of activities.value) {
      if (a.action === 'commented') continue
      items.push({ id: 'a_' + a.id, type: 'change', user: a.userName || '用户', action: a.action, field: a.fieldName || undefined, from: a.oldValue || undefined, to: a.newValue || undefined, timeAgo: timeAgo(a.createdAt), ts: new Date(a.createdAt).getTime() })
    }
  }
  return items
})

// ============ Actions ============
function copyIssue() {
  navigator.clipboard.writeText(`${issue.value?.issueKey} ${issue.value?.title}`)
  Message.success('已复制')
}

async function onUpdateTitle(val: string) {
  if (useMock.value) { updateIssueField(issue.value!.id, 'title', val); loadFromMock(); return }
  try { await issueApi.update(issue.value!.id, { title: val }); await loadAll() } catch (e: any) { Message.error(e.response?.data?.message || '更新失败') }
}

async function onUpdateDesc(val: string) {
  if (useMock.value) { updateIssueField(issue.value!.id, 'description', val); loadFromMock(); return }
  try { await issueApi.update(issue.value!.id, { description: val }); await loadAll() } catch (e: any) { Message.error(e.response?.data?.message || '更新失败') }
}

async function onRemoveTag(tagId: string) {
  if (useMock.value) { removeIssueTag(issue.value!.id, tagId); loadFromMock(); return }
  try { await issueApi.removeTag(issue.value!.id, tagId); await loadAll() } catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
}

async function onAddTag(tag: { id: string }) {
  if (useMock.value) { addIssueTag(issue.value!.id, tag.id); loadFromMock(); return }
  try { await issueApi.addTag(issue.value!.id, tag.id); await loadAll() } catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
}

async function onCreateTag(name: string) {
  if (useMock.value) { const t = mockCreateTag(issue.value!.projectId, name); addIssueTag(issue.value!.id, t.id); loadFromMock(); return }
  try {
    const res = await tagApi.createProjectTag(issue.value!.projectId, { name })
    if (res.data) await issueApi.addTag(issue.value!.id, res.data.id)
    await loadAll()
  } catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
}

async function onAddComment(content: string) {
  if (useMock.value) { mockAddComment(issue.value!.id, '2', content); loadFromMock(); return }
  try { await issueApi.addComment(issue.value!.id, content); await loadAll() } catch (e: any) { Message.error(e.response?.data?.message || '评论失败') }
}

async function onTransition(target: StatusInfo) {
  if (useMock.value) { mockTransitStatus(issue.value!.id, target.id); loadFromMock(); Message.success(`状态已变更为 ${target.name}`); return }
  try { await issueApi.transitStatus(issue.value!.id, target.id); await loadAll(); Message.success(`状态已变更为 ${target.name}`) } catch (e: any) { Message.error(e.response?.data?.message || '变更失败') }
}

async function onEditField(key: string, newValue: string) {
  const fieldMap: Record<string, string> = { priority: 'priority', issueType: 'issueType', assignee: 'assigneeId', sprint: 'sprintId', dueDate: 'dueDate', estimatedHours: 'estimatedHours' }
  const prop = fieldMap[key]
  if (!prop) return

  const val = prop === 'estimatedHours' ? (newValue ? Number(newValue) : null) : (newValue || null)

  if (useMock.value) { updateIssueField(issue.value!.id, prop, val); loadFromMock(); Message.success('已更新'); return }
  try {
    if (key === 'assignee' && newValue) { await issueApi.assign(issue.value!.id, newValue) }
    else { await issueApi.update(issue.value!.id, { [prop]: val }) }
    await loadAll()
    Message.success('已更新')
  } catch (e: any) { Message.error(e.response?.data?.message || '更新失败') }
}

// ============ Utils ============
function timeAgo(dt: string | number) {
  const ts = typeof dt === 'number' ? dt : new Date(dt).getTime()
  const diff = Date.now() - ts
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs} 小时前`
  return `${Math.floor(hrs / 24)} 天前`
}

function formatDateTime(dt?: string) {
  if (!dt) return '-'
  return new Date(dt).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

function priorityDot(p: string) {
  const m: Record<string, string> = { Critical: '#f44336', High: '#ff9800', Normal: '#4caf50', Low: '#9e9e9e' }
  return m[p] || '#666'
}
</script>

<style scoped>
.issue-detail-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--tf-bg-body);
  color: var(--tf-text-primary);
  overflow: hidden;
}

.page-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.loading-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
