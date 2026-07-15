<template>
  <div class="issue-detail-page" v-if="issue">
    <!-- 归档项目提示 -->
    <div v-if="isProjectArchived" class="archived-banner">
      <icon-lock class="archived-icon" />
      <div class="archived-info">
        <span class="archived-title">此工单所属项目已归档</span>
        <span class="archived-desc">归档项目为只读状态，无法编辑工单、评论或变更状态</span>
      </div>
    </div>

    <DetailTopBar
      :project-name="projectName"
      :issue-key="issue.issueKey"
      :reporter="reporterName"
      :created-ago="timeAgo(issue.createdAt)"
      :updated-ago="timeAgo(issue.updatedAt)"
      :show-create="canCreateIssue"
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
        :readonly="!canEditIssue"
        :children="issue.children || []"
        :child-progress="issue.childProgress || null"
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
          <CommentInput v-if="canComment" @submit="onAddComment" @add-time="openTimeDialog" />
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

  <!-- 加载失败状态 -->
  <div v-else-if="loadError" class="error-page">
    <div class="error-icon">⚠️</div>
    <h3 class="error-title">无法加载工单</h3>
    <p class="error-desc">{{ loadError }}</p>
    <a-button type="primary" size="small" @click="loadAll">重试</a-button>
  </div>

  <IssueCreatePanel v-model:visible="showCreatePanel" :project-id="issue?.projectId" />

  <!-- Add Time Entry Dialog -->
  <a-modal
    v-model:visible="showTimeDialog"
    title="添加花费的时间"
    :width="480"
    :footer="false"
    @cancel="showTimeDialog = false"
  >
    <a-form :model="timeForm" layout="vertical">
      <a-form-item label="日期" required>
        <a-date-picker v-model="timeForm.workDate" style="width: 100%" />
      </a-form-item>
      <a-form-item label="实际用时" required>
        <a-input v-model="timeForm.durationText" placeholder="例如: 2h30m, 1h, 45m">
          <template #prefix>⏱</template>
        </a-input>
      </a-form-item>
      <a-form-item label="工作类型">
        <a-select v-model="timeForm.workType" placeholder="选择工作类型" allow-clear>
          <a-option value="Development">开发</a-option>
          <a-option value="Testing">测试</a-option>
          <a-option value="Documentation">文档</a-option>
          <a-option value="Design">设计</a-option>
          <a-option value="Review">代码审查</a-option>
          <a-option value="Meeting">会议</a-option>
          <a-option value="Other">其他</a-option>
        </a-select>
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model="timeForm.description" placeholder="描述这段时间您做了什么" :auto-size="{ minRows: 2 }" />
      </a-form-item>
    </a-form>
    <div style="display:flex;justify-content:flex-end;gap:8px;padding-top:12px;border-top:1px solid var(--tf-border-light)">
      <a-button @click="showTimeDialog = false">取消</a-button>
      <a-button type="primary" :loading="timeSaving" @click="submitTimeEntry">保存</a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { IconLock } from '@arco-design/web-vue/es/icon'
import { renderMarkdown } from '@/utils/markdown'
import { issueApi, projectApi, sprintApi, tagApi, timeEntryApi } from '@/api'
import { ERROR_CODES } from '@/api/error-codes'
import { usePermission, loadProjectPermissions } from '@/composables/usePermission'
import type { IssueDetailVO, IssueStatusVO, IssueCommentVO, IssueActivityVO, IssueAttachmentVO, IssueLinkVO, IssueTagVO, ProjectMemberVO, SprintVO } from '@/api/types'
import DetailTopBar from './components/DetailTopBar.vue'
import DetailMainContent from './components/DetailMainContent.vue'
import DetailSidebar from './components/DetailSidebar.vue'
import ActivityStream from './components/ActivityStream.vue'
import CommentInput from './components/CommentInput.vue'
import IssueCreatePanel from './IssueCreatePanel.vue'
import type { ActivityItem } from './components/ActivityStream.vue'
import type { SidebarField, StatusInfo } from './components/DetailSidebar.vue'
import { localizeFieldName } from '@/utils/fieldLabels'

const route = useRoute()
const sidebarVisible = ref(true)
const showCreatePanel = ref(false)
const showTimeDialog = ref(false)
const timeSaving = ref(false)

const timeForm = ref({
  workDate: new Date().toISOString().slice(0, 10),
  durationText: '',
  workType: undefined as string | undefined,
  description: ''
})
const loading = ref(false)
const loadError = ref<string | null>(null)

// ============ 数据 ============
const issue = ref<IssueDetailVO | null>(null)

// 归档状态
const isProjectArchived = computed(() => issue.value?.projectStatus === 'archived')

// 权限控制（必须在 issue ref 声明之后）
const { canCreateIssue, canEditIssue, canChangeStatus, canComment, canAssignIssue, canEditSprint } = usePermission(
  () => issue.value?.projectId,
  { isProjectArchived: () => isProjectArchived.value }
)
const transitions = ref<IssueStatusVO[]>([])
const comments = ref<IssueCommentVO[]>([])
const activities = ref<IssueActivityVO[]>([])
const attachments = ref<IssueAttachmentVO[]>([])
const links = ref<IssueLinkVO[]>([])
const projectTagList = ref<IssueTagVO[]>([])
const members = ref<ProjectMemberVO[]>([])
const sprints = ref<SprintVO[]>([])

const issueId = computed(() => route.params.id as string || '')

// ============ 加载数据 ============
onMounted(() => loadAll())
watch(() => route.params.id, () => loadAll())

async function loadAll() {
  const id = issueId.value
  if (!id) {
    loadError.value = '缺少工单 ID'
    return
  }

  loading.value = true
  loadError.value = null

  try {
    // 判断 ID 格式：包含连字符且非纯数字 → issue key，否则 → numeric ID
    const isKey = id.includes('-') && !/^\d+$/.test(id)
    const res = isKey ? await issueApi.getByKey(id) : await issueApi.getById(id)
    if (res.code === 0 && res.data) {
      issue.value = res.data
      await loadRelatedData()
    } else {
      loadError.value = res.message || '加载工单失败'
    }
  } catch (e: any) {
    const status = e.response?.status
    if (status === 404) {
      loadError.value = `工单 ${id} 不存在`
    } else if (status === 403) {
      loadError.value = '无权访问此工单'
    } else {
      loadError.value = e.response?.data?.message || '网络错误，请稍后重试'
    }
    console.warn('[IssueDetail] Failed to load issue:', e)
  } finally {
    loading.value = false
  }
}

async function loadRelatedData() {
  if (!issue.value) return
  const id = issue.value.id
  const pid = issue.value.projectId

  // 先加载权限，决定是否需要加载编辑选项
  const perms = await loadProjectPermissions(pid)
  const needSprintOptions = perms.has('sprint:edit')
  const needMemberOptions = perms.has('issue:assign')

  try {
    // 核心数据：始终加载（transitions, comments, activities, attachments, links, tags）
    const promises: Promise<any>[] = [
      issueApi.getAvailableTransitions(id),
      issueApi.listComments(id),
      issueApi.listActivities(id),
      issueApi.listAttachments(id),
      issueApi.listLinks(id),
      tagApi.listProjectTags(pid),
    ]
    // 仅在有分配权限时加载成员列表（编辑负责人的下拉选项）
    if (needMemberOptions) {
      promises.push(projectApi.listMembers(pid))
    }
    // 仅在有迭代编辑权限时加载 Sprint 列表（编辑迭代的下拉选项）
    if (needSprintOptions) {
      promises.push(sprintApi.listByProject(pid))
    }

    const results = await Promise.allSettled(promises)

    let idx = 0
    if (results[idx].status === 'fulfilled') transitions.value = (results[idx] as any).value.data || []
    idx++
    if (results[idx].status === 'fulfilled') comments.value = (results[idx] as any).value.data || []
    idx++
    if (results[idx].status === 'fulfilled') activities.value = (results[idx] as any).value.data || []
    idx++
    if (results[idx].status === 'fulfilled') attachments.value = (results[idx] as any).value.data || []
    idx++
    if (results[idx].status === 'fulfilled') links.value = (results[idx] as any).value.data || []
    idx++
    if (results[idx].status === 'fulfilled') projectTagList.value = (results[idx] as any).value.data || []
    idx++
    if (needMemberOptions) {
      if (results[idx].status === 'fulfilled') members.value = (results[idx] as any).value.data || []
      idx++
    }
    if (needSprintOptions) {
      if (results[idx].status === 'fulfilled') sprints.value = (results[idx] as any).value.data || []
      idx++
    }
  } catch { /* ignore partial failures */ }
}

// ============ Computed ============
const projectName = computed(() => issue.value?.projectName || '')
const reporterName = computed(() => issue.value?.reporterName || '未知')

const issueTags = computed(() => issue.value?.tags || [])
const projectTags = computed(() => projectTagList.value)

const issueLinks = computed(() => {
  return links.value.map(l => ({
    ...l,
    typeLabel: l.linkType,
    statusName: l.issueStatus?.name || '',
    statusColor: l.issueStatus?.color || '',
    isUnresolvedBlocker: l.linkType === 'blocked_by' && l.issueStatus && !l.issueStatus.isClosed
  }))
})

const issueAttachments = computed(() => {
  return attachments.value.map(a => ({ id: a.id, fileName: a.fileName, sizeText: formatSize(a.fileSize) }))
})

const currentStatus = computed<StatusInfo>(() => {
  if (issue.value?.status) {
    return { id: issue.value.status.id, name: issue.value.status.name, color: issue.value.status.color }
  }
  return { id: '', name: '未知', color: '#666' }
})

const availableTransitions = computed<StatusInfo[]>(() => {
  return transitions.value.map(s => ({
    id: s.id,
    name: s.name,
    color: s.color,
    blocked: s.blocked || false,
    blockedBy: s.blockedBy || []
  }))
})

const sidebarFields = computed<SidebarField[]>(() => {
  const i = issue.value
  if (!i) return []

  // 权限判断
  const canEdit = canEditIssue.value
  const canTransition = canChangeStatus.value
  const canAssign = canAssignIssue.value
  const canSprint = canEditSprint.value

  // 状态选项
  const statusOptions = [
    { value: currentStatus.value.id, label: `● ${currentStatus.value.name}（当前）` },
    ...availableTransitions.value.map(s => ({
      value: s.id,
      label: s.blocked ? `⚠ ${s.name}` : s.name,
      badge: s.blocked ? '被阻塞' : undefined,
      badgeColor: s.blocked ? '#d29922' : undefined
    }))
  ]

  // 人员选项（仅在有分配权限时提供）
  const userOptions = canAssign ? members.value.map(m => ({ value: m.userId, label: m.displayName })) : []

  // Sprint 选项（仅在有编辑权限时提供）
  const sprintOptions = canSprint ? [
    { value: '', label: 'Unscheduled' },
    ...sprints.value.filter(s => s.projectId === i.projectId).map(s => ({ value: s.id, label: s.name }))
  ] : []

  // Sprint 显示值：优先使用 issue 自带的 sprintName，不依赖 sprints 列表
  const sprintDisplayName = i.sprintName || (i.sprintId ? sprints.value.find(s => s.id === i.sprintId)?.name : null) || 'Unscheduled'

  return [
    { key: 'project', label: '项目', value: projectName.value, readonly: true },
    { key: 'priority', label: '优先级', value: i.priority, dot: priorityDot(i.priority), editType: 'select' as const, rawValue: i.priority, readonly: !canEdit, options: [
      { value: 'Critical', label: 'Critical' }, { value: 'High', label: 'High' },
      { value: 'Normal', label: 'Normal' }, { value: 'Low', label: 'Low' },
    ]},
    { key: 'state', label: '状态', value: currentStatus.value.name, dot: currentStatus.value.color, editType: 'select' as const, rawValue: currentStatus.value.id, readonly: !canTransition || availableTransitions.value.length === 0, options: statusOptions },
    { key: 'issueType', label: '类型', value: i.issueType, editType: 'select' as const, rawValue: i.issueType, readonly: !canEdit, options: [
      { value: 'Bug', label: 'Bug' }, { value: 'Task', label: 'Task' },
      { value: 'Feature', label: 'Feature' }, { value: 'Epic', label: 'Epic' }, { value: 'Story', label: 'Story' },
    ]},
    { key: 'assignee', label: '负责人', value: i.assigneeName || '未分配', editType: 'user-select' as const, rawValue: i.assigneeId || '', readonly: !canAssign, options: userOptions },
    { key: 'reporter', label: '报告人', value: reporterName.value, readonly: true },
    { key: 'sprint', label: '迭代', value: sprintDisplayName, editType: 'select' as const, rawValue: i.sprintId || '', readonly: !canSprint, options: sprintOptions },
    { key: 'dueDate', label: '截止日期', value: i.dueDate || '-', editType: 'date' as const, rawValue: i.dueDate || '', readonly: !canEdit },
    { key: 'estimatedHours', label: '预估工时', value: i.estimatedHours ? `${i.estimatedHours}h` : '-', editType: 'number' as const, rawValue: i.estimatedHours ? String(i.estimatedHours) : '', readonly: !canEdit },
    { key: 'spentHours', label: '已花时间', value: i.spentHours ? `${i.spentHours}h` : '-', readonly: true },
    { key: '_sep', label: '', value: '', readonly: true },
    { key: 'createdAt', label: '创建时间', value: formatDateTime(i.createdAt), readonly: true },
    { key: 'updatedAt', label: '更新时间', value: formatDateTime(i.updatedAt), readonly: true },
  ]
})

const activityItems = computed<ActivityItem[]>(() => {
  const items: ActivityItem[] = []
  for (const c of comments.value) {
    const isHtml = c.content.trim().startsWith('<')
    items.push({ id: 'c_' + c.id, type: 'comment', user: c.userName || '用户', html: isHtml ? c.content : renderMarkdown(c.content), timeAgo: timeAgo(c.createdAt), ts: new Date(c.createdAt).getTime() })
  }
  for (const a of activities.value) {
    if (a.action === 'commented') continue
    items.push({ id: 'a_' + a.id, type: 'change', user: a.userName || '用户', action: a.action, field: localizeFieldName(a.fieldName), from: a.oldValue || undefined, to: a.newValue || undefined, timeAgo: timeAgo(a.createdAt), ts: new Date(a.createdAt).getTime() })
  }
  return items
})

// ============ Actions ============
function copyIssue() {
  navigator.clipboard.writeText(`${issue.value?.issueKey} ${issue.value?.title}`)
  Message.success('已复制')
}

async function onUpdateTitle(val: string) {
  try { await issueApi.update(issue.value!.id, { title: val, version: issue.value!.version }); await loadAll() } catch (e: any) { handleUpdateError(e) }
}

async function onUpdateDesc(val: string) {
  try { await issueApi.update(issue.value!.id, { description: val, version: issue.value!.version }); await loadAll() } catch (e: any) { handleUpdateError(e) }
}

async function onRemoveTag(tagId: string) {
  try { await issueApi.removeTag(issue.value!.id, tagId); await loadAll() } catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
}

async function onAddTag(tag: { id: string }) {
  try { await issueApi.addTag(issue.value!.id, tag.id); await loadAll() } catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
}

async function onCreateTag(name: string) {
  try {
    const res = await tagApi.createProjectTag(issue.value!.projectId, { name })
    if (res.data) await issueApi.addTag(issue.value!.id, res.data.id)
    await loadAll()
  } catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
}

async function onAddComment(content: string) {
  try { await issueApi.addComment(issue.value!.id, content); await loadAll() } catch (e: any) { Message.error(e.response?.data?.message || '评论失败') }
}

async function onTransition(target: StatusInfo) {
  try {
    const res = await issueApi.transitStatus(issue.value!.id, target.id, undefined, issue.value!.version)
    if (res.code === 0) {
      await loadAll()
      Message.success(`状态已变更为 ${target.name}`)
    } else if (res.code === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) {
      // 关闭前置检查警告（子任务未完成 / 被阻塞 / 组合）— 统一弹窗
      Modal.warning({
        title: '确认关闭',
        content: res.message,
        okText: '强制关闭',
        cancelText: '取消',
        hideCancel: false,
        onOk: async () => {
          try {
            const forceRes = await issueApi.transitStatus(issue.value!.id, target.id, undefined, issue.value!.version, true)
            if (forceRes.code === 0) {
              await loadAll()
              Message.success(`状态已变更为 ${target.name}`)
            } else {
              Message.error(forceRes.message || '变更失败')
            }
          } catch (e2: any) {
            handleUpdateError(e2, '变更失败')
          }
        }
      })
    } else {
      Message.error(res.message || '变更失败')
    }
  } catch (e: any) { handleUpdateError(e, '变更失败') }
}

function openTimeDialog() {
  timeForm.value = {
    workDate: new Date().toISOString().slice(0, 10),
    durationText: '',
    workType: undefined,
    description: ''
  }
  showTimeDialog.value = true
}

async function submitTimeEntry() {
  if (!timeForm.value.durationText) {
    Message.warning('请输入时长')
    return
  }
  const duration = parseDurationText(timeForm.value.durationText)
  if (!duration || duration <= 0) {
    Message.warning('时长格式无效，请使用如 2h30m, 1h, 45m')
    return
  }

  timeSaving.value = true
  try {
    await timeEntryApi.create({
      issueId: issue.value!.id,
      workDate: timeForm.value.workDate,
      duration,
      workType: timeForm.value.workType || undefined,
      description: timeForm.value.description || undefined
    })
    Message.success('工时已记录')
    showTimeDialog.value = false
    await loadAll()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '记录失败')
  } finally {
    timeSaving.value = false
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

async function onEditField(key: string, newValue: string) {
  const fieldMap: Record<string, string> = { priority: 'priority', issueType: 'issueType', assignee: 'assigneeId', sprint: 'sprintId', dueDate: 'dueDate', estimatedHours: 'estimatedHours' }
  const prop = fieldMap[key]
  if (!prop) return

  const val = prop === 'estimatedHours' ? (newValue ? Number(newValue) : null) : (newValue || null)

  try {
    if (key === 'assignee' && newValue) { await issueApi.assign(issue.value!.id, newValue) }
    else { await issueApi.update(issue.value!.id, { [prop]: val, version: issue.value!.version }) }
    await loadAll()
    Message.success('已更新')
  } catch (e: any) { handleUpdateError(e) }
}

/**
 * 统一处理更新错误：409 冲突时显示特殊提示 + 自动刷新
 */
function handleUpdateError(e: any, fallbackMsg = '更新失败') {
  if (e.response?.status === 409) {
    Message.warning({ content: '该工单已被其他人修改，正在刷新...', duration: 3000 })
    loadAll()
  } else {
    Message.error(e.response?.data?.message || fallbackMsg)
  }
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

/* 归档状态横幅 */
.archived-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: rgba(210, 153, 34, 0.08);
  border-bottom: 1px solid rgba(210, 153, 34, 0.25);
  flex-shrink: 0;
}

.archived-icon {
  font-size: 18px;
  color: #d29922;
  flex-shrink: 0;
}

.archived-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.archived-title {
  font-size: 12px;
  font-weight: 600;
  color: #d29922;
}

.archived-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
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

.error-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.error-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.error-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-1);
  margin-bottom: 8px;
}

.error-desc {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 16px;
}
</style>
