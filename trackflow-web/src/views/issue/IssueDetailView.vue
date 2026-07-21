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

    <!-- 快捷动作栏 -->
    <div v-if="!isProjectArchived" class="quick-action-wrapper">
      <QuickActionBar
        :issue-id="issue.id"
        :project-id="issue.projectId"
        @executed="onQuickActionExecuted"
      />
    </div>

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
        :readonly="!canEditIssueEffective"
        :can-delete="canDeleteIssue"
        :can-move="canMoveIssue"
        :children="issue.children || []"
        :child-progress="issue.childProgress || null"
        @update-title="onUpdateTitle"
        @update-desc="onUpdateDesc"
        @remove-tag="onRemoveTag"
        @add-tag="onAddTag"
        @create-tag="onCreateTag"
        @add-link="() => {}"
        @upload="() => {}"
        @copy-id="onCopyId"
        @clone="onCloneIssue"
        @move="onMoveIssue"
        @delete="onDeleteIssue"
      >
        <template #activity>
          <ActivityStream
            :items="activityItems"
            :current-user-id="currentUserId"
            :can-manage-comments="canManageComments"
            @edit-comment="onEditComment"
            @delete-comment="onDeleteComment"
          />
          <CommentInput
            v-if="canComment"
            :show-add-time="projectTimeTrackingEnabled && canLogTime"
            :timer-running="timerStore.isRunning"
            :timer-issue-match="timerStore.issueId === issue?.id"
            :timer-elapsed="timerStore.elapsedDisplay"
            @submit="onAddComment"
            @add-time="openTimeDialog"
            @start-timer="handleStartTimer"
            @stop-timer="handleStopTimerFromDetail"
          />
        </template>
      </DetailMainContent>

      <DetailSidebar
        v-show="sidebarVisible"
        :status="currentStatus"
        :transitions="availableTransitions"
        :fields="sidebarFields"
        @transition="onTransition"
        @edit-field="onEditField"
        @add-option="onAddOption"
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

  <IssueCreatePanel ref="createPanelRef" :visible="showCreatePanel" :project-id="issue?.projectId" :clone-data="cloneData" @update:visible="onCreatePanelClose" @created="onIssueCreated" />

  <!-- Move Issue Modal -->
  <MoveIssueModal
    ref="moveModalRef"
    :visible="showMoveModal"
    :issue-key="issue?.issueKey || ''"
    :current-project-id="issue?.projectId || ''"
    @update:visible="showMoveModal = $event"
    @confirm="onMoveConfirm"
  />

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
        <a-select v-model="timeFormAttrValues['1']" placeholder="选择工作类型" allow-clear>
          <a-option v-for="val in issueWorkTypeValues" :key="val.id" :value="val.id">
            <span v-if="val.color" class="attr-value-dot" :style="{ background: val.color }"></span>
            {{ val.name }}
          </a-option>
        </a-select>
      </a-form-item>
      <!-- Dynamic work item attributes (excluding built-in Work type) -->
      <a-form-item v-for="attr in issueExtraAttributes" :key="attr.id" :label="attr.name">
        <a-select v-model="timeFormAttrValues[attr.id]" :placeholder="`选择${attr.name}`" allow-clear>
          <a-option v-for="val in attr.values" :key="val.id" :value="val.id">
            <span v-if="val.color" class="attr-value-dot" :style="{ background: val.color }"></span>
            {{ val.name }}
          </a-option>
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
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { IconLock } from '@arco-design/web-vue/es/icon'
import { renderMarkdown } from '@/utils/markdown'
import { showActionFeedback } from '@/utils/transition'
import { issueApi, projectApi, sprintApi, tagApi, timeEntryApi, customFieldApi } from '@/api'
import { workItemAttributeApi } from '@/api/timeEntry'
import type { WorkItemAttributeVO, AttributeValueVO } from '@/api/timeEntry'
import { ERROR_CODES } from '@/api/error-codes'
import { usePermission, loadProjectPermissions } from '@/composables/usePermission'
import { useTabStore } from '@/stores/tabs'
import { useTimerStore } from '@/stores/timer'
import { useRecentIssues } from './composables/useRecentIssues'
import type { IssueDetailVO, IssueStatusVO, IssueCommentVO, IssueActivityVO, IssueAttachmentVO, IssueLinkVO, IssueTagVO, ProjectMemberVO, SprintVO, CustomFieldDefinitionVO } from '@/api/types'
import DetailTopBar from './components/DetailTopBar.vue'
import QuickActionBar from './components/QuickActionBar.vue'
import DetailMainContent from './components/DetailMainContent.vue'
import DetailSidebar from './components/DetailSidebar.vue'
import ActivityStream from './components/ActivityStream.vue'
import CommentInput from './components/CommentInput.vue'
import IssueCreatePanel from './IssueCreatePanel.vue'
import MoveIssueModal from './components/MoveIssueModal.vue'
import type { ActivityItem } from './components/ActivityStream.vue'
import type { SidebarField, StatusInfo } from './components/DetailSidebar.vue'
import { localizeFieldName, localizeFieldValue, localizeStatusName, issueTypeLabelMap } from '@/utils/fieldLabels'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()
const timerStore = useTimerStore()
const { recordVisit: recordRecentVisit } = useRecentIssues()
const sidebarVisible = ref(true)
const showCreatePanel = ref(false)
const createPanelRef = ref<InstanceType<typeof IssueCreatePanel> | null>(null)
const cloneData = ref<{ projectId: string; title: string; description: string; issueType: string; priority: string } | undefined>(undefined)
const showTimeDialog = ref(false)
const timeSaving = ref(false)

const timeForm = ref({
  workDate: new Date().toISOString().slice(0, 10),
  durationText: '',
  description: ''
})
const timeFormAttrValues = ref<Record<string, string>>({})
const issueProjectAttributes = ref<WorkItemAttributeVO[]>([])
const projectTimeTrackingEnabled = ref(true)
const issueWorkTypeValues = computed(() => {
  // Find the "Work type" built-in attribute values
  const wt = issueProjectAttributes.value.find(a => a.name === 'Work type' || a.isBuiltin)
  return wt?.values || [
    { id: 'Development', name: '开发', color: '#58a6ff' },
    { id: 'Testing', name: '测试', color: '#3fb950' },
    { id: 'Documentation', name: '文档', color: '#d29922' },
    { id: 'Design', name: '设计', color: '#a371f7' },
    { id: 'Review', name: '代码审查', color: '#f0883e' },
    { id: 'Meeting', name: '会议', color: '#8b949e' },
    { id: 'Other', name: '其他', color: '#6e7681' }
  ]
})
const issueExtraAttributes = computed(() => {
  // Extra attributes beyond the built-in "Work type"
  return issueProjectAttributes.value.filter(a => !a.isBuiltin && a.name !== 'Work type')
})
const loading = ref(false)
const loadError = ref<string | null>(null)

// ============ 数据 ============
const issue = ref<IssueDetailVO | null>(null)

// 归档状态
const isProjectArchived = computed(() => issue.value?.projectStatus === 'archived')

// 权限控制（必须在 issue ref 声明之后）
const { canCreateIssue, canEditIssue, canDeleteIssue, canChangeStatus, canComment, canAssignIssue, canEditSprint, canLogTime, hasPermission: hasProjectPermission } = usePermission(
  () => issue.value?.projectId,
  { isProjectArchived: () => isProjectArchived.value }
)

// 资源级权限覆盖：reporter 需 issue:edit_own，assignee 需 issue:edit_assigned
import { useAuthStore } from '@/stores/auth'
const authStore = useAuthStore()

/** 当前用户数据库 ID */
const currentUserId = computed(() => authStore.user?.userId || '')

/** 是否可以管理他人评论 */
const canManageComments = computed(() => hasProjectPermission('issue:manage_comments'))

/** 是否可以管理自定义字段（用于内联添加选项） */
const canManageCustomFieldsComputed = computed(() => hasProjectPermission('project:manage_custom_fields'))

/** 是否为 Issue 的创建者 */
const isReporter = computed(() => {
  const dbUserId = authStore.user?.userId
  if (!dbUserId || !issue.value) return false
  return dbUserId === issue.value.reporterId
})

/** 是否为 Issue 的负责人 */
const isAssignee = computed(() => {
  const dbUserId = authStore.user?.userId
  if (!dbUserId || !issue.value) return false
  return dbUserId === issue.value.assigneeId
})

/** 综合权限：项目级 issue:edit OR 资源级（reporter + edit_own / assignee + edit_assigned） */
const canEditIssueEffective = computed(() => {
  if (isProjectArchived.value) return false
  if (canEditIssue.value) return true
  // 资源级：reporter 需要 issue:edit_own 权限
  if (isReporter.value && hasProjectPermission('issue:edit_own')) return true
  // 资源级：assignee 需要 issue:edit_assigned 权限
  if (isAssignee.value && hasProjectPermission('issue:edit_assigned')) return true
  return false
})

/** 综合状态变更权限：项目级 issue:change_status OR assignee + edit_assigned */
const canChangeStatusEffective = computed(() => {
  if (isProjectArchived.value) return false
  if (canChangeStatus.value) return true
  // 资源级：assignee 需要 issue:edit_assigned 权限
  if (isAssignee.value && hasProjectPermission('issue:edit_assigned')) return true
  return false
})

/** 是否可以移动工单到其他项目 */
const canMoveIssue = computed(() => {
  if (isProjectArchived.value) return false
  return hasProjectPermission('issue:move')
})

// Move modal state
const showMoveModal = ref(false)
const moveModalRef = ref<InstanceType<typeof MoveIssueModal> | null>(null)

const transitions = ref<IssueStatusVO[]>([])
const comments = ref<IssueCommentVO[]>([])
const activities = ref<IssueActivityVO[]>([])
const attachments = ref<IssueAttachmentVO[]>([])
const links = ref<IssueLinkVO[]>([])
const projectTagList = ref<IssueTagVO[]>([])
const members = ref<ProjectMemberVO[]>([])
const sprints = ref<SprintVO[]>([])
const customFieldDefs = ref<CustomFieldDefinitionVO[]>([])

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
      // 记录最近浏览
      recordRecentVisit({
        id: res.data.id,
        issueKey: res.data.issueKey,
        title: res.data.title,
      })
      // 更新标签标题为工单编号（替代路由守卫中的占位标题）
      tabStore.openTab({
        id: `issue-${id}`,
        title: res.data.issueKey,
        path: route.fullPath,
        closable: true,
        issueId: id
      })
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
  const isAdmin = authStore.hasGlobalPermission('system:admin')
  const needTransitions = isAdmin || perms.has('issue:change_status')
  const needSprintOptions = isAdmin || perms.has('sprint:edit')
  const needMemberOptions = isAdmin || perms.has('issue:assign')

  // 加载项目时间追踪开关
  try {
    const ttRes = await projectApi.getTimeTrackingSettings(pid)
    if (ttRes.code === 0 && ttRes.data) {
      projectTimeTrackingEnabled.value = ttRes.data.enabled
    }
  } catch {
    projectTimeTrackingEnabled.value = true // 默认启用
  }

  // 无状态变更权限时清空 transitions（确保 UI 渲染为只读）
  if (!needTransitions) {
    transitions.value = []
  }

  try {
    // 核心数据：始终加载（comments, activities, attachments, links, tags, custom fields）
    const promises: Promise<any>[] = [
      issueApi.listComments(id),
      issueApi.listActivities(id),
      issueApi.listAttachments(id),
      issueApi.listLinks(id),
      tagApi.listProjectTags(pid),
      customFieldApi.listByProject(pid, issue.value!.issueType),
    ]
    // 仅在有状态变更权限时加载可用转换（避免无权限用户触发 403）
    if (needTransitions) {
      promises.push(issueApi.getAvailableTransitions(id))
    }
    // 仅在有分配权限时加载可分配成员列表（编辑负责人的下拉选项，排除观察者等角色）
    if (needMemberOptions) {
      promises.push(projectApi.listAssignableMembers(pid))
    }
    // 仅在有迭代编辑权限时加载 Sprint 列表（编辑迭代的下拉选项）
    if (needSprintOptions) {
      promises.push(sprintApi.listByProject(pid))
    }

    const results = await Promise.allSettled(promises)

    let idx = 0
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
    if (results[idx].status === 'fulfilled') customFieldDefs.value = (results[idx] as any).value.data || []
    idx++
    if (needTransitions) {
      if (results[idx].status === 'fulfilled') transitions.value = (results[idx] as any).value.data || []
      idx++
    }
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
    statusName: localizeStatusName(l.issueStatus?.name),
    statusColor: l.issueStatus?.color || '',
    isUnresolvedBlocker: l.linkType === 'blocked_by' && l.issueStatus && !l.issueStatus.isClosed
  }))
})

const issueAttachments = computed(() => {
  return attachments.value.map(a => ({ id: a.id, fileName: a.fileName, sizeText: formatSize(a.fileSize) }))
})

const currentStatus = computed<StatusInfo>(() => {
  if (issue.value?.status) {
    return { id: issue.value.status.id, name: localizeStatusName(issue.value.status.name), color: issue.value.status.color }
  }
  return { id: '', name: '未知', color: '#666' }
})

const availableTransitions = computed<StatusInfo[]>(() => {
  return transitions.value.map(s => ({
    id: s.id,
    name: localizeStatusName(s.name),
    color: s.color,
    blocked: s.blocked || false,
    blockedBy: s.blockedBy || []
  }))
})

const sidebarFields = computed<SidebarField[]>(() => {
  const i = issue.value
  if (!i) return []

  // 权限判断（使用资源级覆盖后的综合权限）
  const canEdit = canEditIssueEffective.value
  const canTransition = canChangeStatusEffective.value
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
    { key: 'issueType', label: '类型', value: i.issueType, editType: 'select' as const, rawValue: i.issueType, readonly: !canEdit, options: Object.entries(issueTypeLabelMap).map(([value, label]) => ({ value, label })) },
    { key: 'assignee', label: '负责人', value: i.assigneeName || '未分配', editType: 'user-select' as const, rawValue: i.assigneeId || '', readonly: !canAssign, options: userOptions },
    { key: 'reporter', label: '报告人', value: reporterName.value, readonly: true },
    { key: 'sprint', label: '迭代', value: sprintDisplayName, editType: 'select' as const, rawValue: i.sprintId || '', readonly: !canSprint, options: sprintOptions },
    { key: 'dueDate', label: '截止日期', value: i.dueDate || '-', editType: 'date' as const, rawValue: i.dueDate || '', readonly: !canEdit },
    ...(projectTimeTrackingEnabled.value ? [
      { key: 'estimatedHours', label: '预估工时', value: i.estimatedHours ? `${i.estimatedHours}h` : '-', editType: 'number' as const, rawValue: i.estimatedHours ? String(i.estimatedHours) : '', readonly: !canEdit, progress: i.estimatedHours ? { spent: i.spentHours || 0, estimated: i.estimatedHours } : undefined },
      { key: 'spentHours', label: '已花时间', value: i.spentHours ? `${i.spentHours}h` : '-', readonly: true },
    ] : []),
    ...(projectTimeTrackingEnabled.value && i.derivedEstimatedHours != null ? [{ key: 'derivedEstimatedHours', label: '总预估工时', value: `${i.derivedEstimatedHours}h`, readonly: true }] : []),
    ...(projectTimeTrackingEnabled.value && i.derivedSpentHours != null ? [{ key: 'derivedSpentHours', label: '总花费时间', value: `${i.derivedSpentHours}h`, readonly: true }] : []),
    // 自定义字段
    ...buildCustomFieldSidebarEntries(i, canEdit),
    { key: '_sep', label: '', value: '', readonly: true },
    { key: 'createdAt', label: '创建时间', value: formatDateTime(i.createdAt), readonly: true },
    { key: 'updatedAt', label: '更新时间', value: formatDateTime(i.updatedAt), readonly: true },
  ]
})

/**
 * 将自定义字段定义 + 已存储的值转为 SidebarField 数组
 * 支持条件显示：根据条件源字段的当前值动态过滤
 */
function buildCustomFieldSidebarEntries(i: IssueDetailVO, canEdit: boolean): SidebarField[] {
  if (!customFieldDefs.value.length) return []

  // 已存储的值 map: fieldId → { value, values, displayValue, displayValues, isMulti, color, colors }
  const valuesMap = new Map<string, { value: string; values?: string[]; displayValue: string; displayValues?: string[]; isMulti?: boolean; color?: string | null; colors?: (string | null)[] }>()
  if (i.customFieldDetails) {
    for (const v of i.customFieldDetails) {
      valuesMap.set(v.customFieldId, {
        value: v.value || '',
        values: v.values,
        displayValue: v.displayValue || v.value || '',
        displayValues: v.displayValues,
        isMulti: v.isMulti,
        color: v.color,
        colors: v.colors
      })
    }
  }

  // 条件过滤：只显示条件满足的字段
  const visibleDefs = customFieldDefs.value.filter(cf => {
    if (!cf.conditionFieldId || !cf.conditionValues || cf.conditionValues.length === 0) {
      return true // 无条件，始终显示
    }
    // 查找条件源字段的当前值
    const condStored = valuesMap.get(cf.conditionFieldId)
    const condValue = condStored?.value || ''
    if (!condValue) return false // 条件源字段无值 → 隐藏
    return cf.conditionValues.includes(condValue)
  })

  return visibleDefs.map(cf => {
    const stored = valuesMap.get(cf.id)
    const isMulti = cf.isMulti || stored?.isMulti
    const rawValue = stored?.value || ''
    const rawValues = stored?.values || []
    const displayValue = isMulti && stored?.displayValues?.length
      ? stored.displayValues.join(', ')
      : stored?.displayValue || ((cf.effectiveIsRequired ?? cf.isRequired) ? '设置值' : '-')

    // 根据字段类型确定 editType
    let editType: 'select' | 'multi-select' | 'user-select' | 'date' | 'datetime' | 'number' | 'text' | undefined
    let options: { value: string; label: string }[] | undefined

    switch (cf.fieldFormat) {
      case 'list':
        editType = isMulti ? 'multi-select' : 'select'
        // Only show active (non-archived) options in the selector;
        // if current value references an archived option, it's still displayed via displayValue
        options = (cf.options || [])
          .filter(o => !o.isArchived)
          .map(o => ({ value: o.id, label: o.value }))
        break
      case 'user':
        editType = 'user-select'
        options = members.value.map(m => ({ value: m.userId, label: m.displayName }))
        break
      case 'date':
        editType = 'date'
        break
      case 'datetime':
        editType = 'datetime'
        break
      case 'int':
      case 'float':
        editType = 'number'
        break
      case 'bool':
        editType = 'select'
        options = [{ value: 'true', label: '是' }, { value: 'false', label: '否' }]
        break
      case 'text':
        editType = 'text'
        break
      case 'string':
      default:
        editType = 'text'
        break
    }

    // 确定颜色（仅 list 类型且有颜色配置时）
    let fieldColor: string | undefined
    if (cf.fieldFormat === 'list' && stored) {
      if (isMulti && stored.colors?.length) {
        // 多值：取第一个有颜色的
        fieldColor = stored.colors.find(c => c != null) || undefined
      } else if (stored.color) {
        fieldColor = stored.color
      }
    }

    return {
      key: `cf_${cf.id}`,
      label: cf.name,
      value: displayValue,
      dot: fieldColor,
      editType: editType as any,
      rawValue,
      rawValues: isMulti ? rawValues : undefined,
      readonly: !canEdit || cf.editable === false,
      options,
      canAddOption: cf.fieldFormat === 'list' && canManageCustomFieldsComputed.value,
      customFieldId: cf.id
    }
  })
}

const activityItems = computed<ActivityItem[]>(() => {
  const items: ActivityItem[] = []
  for (const c of comments.value) {
    const isHtml = c.content.trim().startsWith('<')
    items.push({
      id: 'c_' + c.id,
      type: 'comment',
      user: c.userName || '用户',
      userId: c.userId,
      commentId: c.id,
      isEdited: c.isEdited || false,
      rawContent: c.content,
      html: isHtml ? c.content : renderMarkdown(c.content),
      timeAgo: timeAgo(c.createdAt),
      ts: new Date(c.createdAt).getTime()
    })
  }
  for (const a of activities.value) {
    if (a.action === 'commented') continue
    items.push({ id: 'a_' + a.id, type: 'change', user: a.userName || '用户', action: a.action, field: localizeFieldName(a.fieldName), from: localizeFieldValue(a.fieldName, a.oldValue) || undefined, to: localizeFieldValue(a.fieldName, a.newValue) || undefined, timeAgo: timeAgo(a.createdAt), ts: new Date(a.createdAt).getTime() })
  }
  return items
})

// ============ Actions ============
function copyIssue() {
  navigator.clipboard.writeText(`${issue.value?.issueKey} ${issue.value?.title}`)
  Message.success('已复制')
}

function onCopyId() {
  if (!issue.value) return
  navigator.clipboard.writeText(issue.value.issueKey)
  Message.success(`已复制 ${issue.value.issueKey}`)
}

function onCloneIssue() {
  if (!issue.value) return
  showCreatePanel.value = true
  // Defer pre-filling the clone data — IssueCreatePanel watches `visible`
  // We use a custom event approach via a ref to pass clone data
  cloneData.value = {
    projectId: issue.value.projectId,
    title: `[Clone] ${issue.value.title}`,
    description: issue.value.description || '',
    issueType: issue.value.issueType,
    priority: issue.value.priority
  }
}

function onDeleteIssue() {
  if (!issue.value) return
  Modal.warning({
    title: '删除工单',
    content: `确定要删除工单 ${issue.value.issueKey} 吗？删除后可在回收站恢复。`,
    okText: '删除',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      try {
        await issueApi.delete(issue.value!.id)
        Message.success('工单已删除')
        router.push('/issues')
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

function onMoveIssue() {
  showMoveModal.value = true
}

async function onMoveConfirm(targetProjectId: string) {
  if (!issue.value) return
  try {
    const res = await issueApi.move(issue.value.id, targetProjectId)
    if (res.code === 0 && res.data) {
      const newKey = res.data.issueKey
      Message.success(`已移动到项目，新编号：${newKey}`)
      showMoveModal.value = false
      // 跳转到新 issue_key 详情页
      router.replace(`/issues/${newKey}`)
    } else {
      Message.error(res.message || '移动失败')
      moveModalRef.value?.resetSubmitting()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移动失败')
    moveModalRef.value?.resetSubmitting()
  }
}

function onCreatePanelClose(val: boolean) {
  showCreatePanel.value = val
  if (!val) cloneData.value = undefined
}

function onIssueCreated() {
  showCreatePanel.value = false
  cloneData.value = undefined
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

async function onEditComment(commentId: string, content: string) {
  try {
    await issueApi.updateComment(issue.value!.id, commentId, content)
    await loadAll()
    Message.success('评论已更新')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '编辑评论失败')
  }
}

async function onDeleteComment(commentId: string) {
  try {
    await issueApi.deleteComment(issue.value!.id, commentId)
    await loadAll()
    Message.success('评论已删除')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除评论失败')
  }
}

/** 快捷动作执行完成后刷新详情 */
async function onQuickActionExecuted() {
  await loadAll()
}

async function onTransition(target: StatusInfo) {
  try {
    const res = await issueApi.transitStatus(issue.value!.id, target.id, undefined, issue.value!.version)
    if (res.code === 0) {
      await loadAll()
      Message.success(`状态已变更为 ${target.name}`)
      showActionFeedback(res.data)
    } else if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
      // WIP 超限警告 — 弹确认框
      Modal.warning({
        title: 'WIP 限制',
        content: res.message,
        okText: '继续移入',
        cancelText: '取消',
        hideCancel: false,
        onOk: async () => {
          try {
            const forceRes = await issueApi.transitStatus(issue.value!.id, target.id, undefined, issue.value!.version, undefined, true)
            if (forceRes.code === 0) {
              await loadAll()
              Message.success(`状态已变更为 ${target.name}`)
              showActionFeedback(forceRes.data)
            } else {
              Message.error(forceRes.message || '变更失败')
            }
          } catch (e2: any) {
            handleUpdateError(e2, '变更失败')
          }
        }
      })
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
              showActionFeedback(forceRes.data)
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
    description: ''
  }
  timeFormAttrValues.value = {}
  // Load attributes for this issue's project
  if (issue.value?.projectId) {
    loadIssueProjectAttributes(issue.value.projectId)
  }
  showTimeDialog.value = true
}

async function handleStartTimer() {
  if (!issue.value) return
  if (timerStore.isRunning) {
    Message.warning('已有活跃计时器，请先停止当前计时器')
    return
  }
  const result = await timerStore.startTimer(issue.value.id)
  if (result.success) {
    Message.success('计时器已启动')
  } else {
    Message.error(result.message || '启动计时器失败')
  }
}

async function handleStopTimerFromDetail() {
  const result = await timerStore.stopTimer()
  if (result.success) {
    Message.success('计时器已停止')
    // Reload issue to refresh spent time
    await loadAll()
  } else {
    Message.error(result.message || '停止计时器失败')
  }
}

async function loadIssueProjectAttributes(projectId: string) {
  try {
    const res = await workItemAttributeApi.listByProject(projectId)
    if (res.code === 0 && res.data) {
      issueProjectAttributes.value = res.data
    }
  } catch { /* silent */ }
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

  // Build attributeValues from timeFormAttrValues
  const attrVals = Object.keys(timeFormAttrValues.value).length > 0
    ? Object.fromEntries(Object.entries(timeFormAttrValues.value).filter(([, v]) => v))
    : undefined

  timeSaving.value = true
  try {
    await timeEntryApi.create({
      issueId: issue.value!.id,
      workDate: timeForm.value.workDate,
      duration,
      description: timeForm.value.description || undefined,
      attributeValues: attrVals
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

async function onAddOption(fieldId: string, value: string) {
  if (!issue.value) return
  try {
    await customFieldApi.addOption(issue.value.projectId, fieldId, { value })
    // Reload custom field definitions to get the new option in the list
    const pid = issue.value.projectId
    const cfRes = await customFieldApi.listByProject(pid, issue.value.issueType)
    customFieldDefs.value = cfRes.data || []
    Message.success(`已添加选项"${value}"`)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加选项失败')
  }
}

async function onEditField(key: string, newValue: string | string[]) {
  // 自定义字段编辑（key 格式: cf_{fieldId}）
  if (key.startsWith('cf_')) {
    const fieldId = key.substring(3)
    try {
      await issueApi.updateCustomFieldValue(issue.value!.id, fieldId, newValue)
      await loadAll()
      Message.success('已更新')
    } catch (e: any) { handleUpdateError(e) }
    return
  }

  const fieldMap: Record<string, string> = { priority: 'priority', issueType: 'issueType', assignee: 'assigneeId', sprint: 'sprintId', dueDate: 'dueDate', estimatedHours: 'estimatedHours' }
  const prop = fieldMap[key]
  if (!prop) return

  const val = prop === 'estimatedHours' ? (newValue ? Number(newValue) : null) : (newValue || null)

  try {
    if (key === 'assignee' && newValue) { await issueApi.assign(issue.value!.id, newValue) }
    else {
      const res = await issueApi.update(issue.value!.id, { [prop]: val, version: issue.value!.version })
      if (res.data?.statusAutoReset) {
        Message.warning({ content: '类型变更导致状态与工作流不兼容，已自动重置为默认状态', duration: 5000 })
      }
    }
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

// 路由守卫：离开时检查克隆创建面板是否有未保存数据
onBeforeRouteLeave((_to, _from, next) => {
  const panel = createPanelRef.value
  if (showCreatePanel.value && panel && panel.isDirty) {
    Modal.confirm({
      title: '有未保存的更改',
      content: '创建工单表单中有未保存的内容，确定要离开吗？',
      okText: '放弃更改',
      cancelText: '继续编辑',
      simple: false,
      onOk: () => { next() },
      onCancel: () => { next(false) }
    })
  } else {
    next()
  }
})
</script>

<style scoped>
.attr-value-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
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

.quick-action-wrapper {
  padding: 0 16px;
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
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
