<template>
  <div class="issue-detail-page" v-if="issue">
    <!-- 实时更新提示 banner（活动流不在视口时显示） -->
    <Transition name="slide-up">
      <div v-if="realtimeUpdateBanner.visible" class="realtime-update-banner" @click="scrollToActivity">
        <span class="realtime-update-icon">🔄</span>
        <span class="realtime-update-text">{{ realtimeUpdateBanner.message }}</span>
        <span class="realtime-update-action">点击查看</span>
        <button class="realtime-update-close" @click.stop="realtimeUpdateBanner.visible = false">✕</button>
      </div>
    </Transition>

    <!-- 归档项目提示 -->
    <div v-if="isProjectArchived" class="archived-banner">
      <icon-lock class="archived-icon" />
      <div class="archived-info">
        <span class="archived-title">此工单所属项目已归档</span>
        <span class="archived-desc">归档项目为只读状态，无法编辑工单、评论或变更状态</span>
      </div>
    </div>

    <DetailTopBar
      :issue-id="issue.id"
      :project-id="issue.projectId"
      :project-name="projectName"
      :issue-key="issue.issueKey"
      :created-by="createdByName"
      :updated-by="updatedByName"
      :created-ago="timeAgo(issue.createdAt)"
      :updated-ago="timeAgo(issue.updatedAt)"
      :show-create="canCreateIssue"
      :is-restricted="issue.visibility === 'restricted'"
      :can-quick-actions="!isProjectArchived && canChangeStatusEffective"
      @copy="copyIssue"
      @create="showCreatePanel = true"
      @toggle-sidebar="toggleSidebar"
      @quick-action-executed="onQuickActionExecuted"
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
        :readonly="!canEditIssueEffective"
        :can-delete="canDeleteIssue"
        :can-move="canMoveIssue"
        :show-add-time="projectTimeTrackingEnabled && canLogTime"
        :children="issue.children || []"
        :child-progress="issue.childProgress || null"
        @update-title="onUpdateTitle"
        @update-desc="onUpdateDesc"
        @remove-tag="onRemoveTag"
        @add-tag="onAddTag"
        @add-tags="onAddTags"
        @create-tag="onCreateTag"
        @add-link="() => {}"
        @upload="triggerUpload(false)"
        @upload-private="triggerUpload(true)"
        @copy-id="onCopyId"
        @clone="onCloneIssue"
        @create-subtask="onCreateSubtask"
        @move="onMoveIssue"
        @delete="onDeleteIssue"
        @add-time="openTimeDialog"
      >
        <template #activity>
          <div ref="activityStreamRef" class="activity-stream-anchor">
          <ActivityStream
            :items="activityItems"
            :current-user-id="currentUserId"
            :can-manage-comments="canManageComments"
            :show-add-time="projectTimeTrackingEnabled && canLogTime"
            @edit-comment="onEditComment"
            @delete-comment="onDeleteComment"
            @add-time="openTimeDialog"
          />
          <CommentInput
            v-if="canCommentEffective"
            :show-add-time="projectTimeTrackingEnabled && canLogTime"
            :can-set-visibility="canEditIssue"
            :timer-running="timerStore.isRunning"
            :timer-issue-match="timerStore.issueId === issue?.id"
            :timer-elapsed="timerStore.elapsedDisplay"
            @submit="onAddComment"
            @add-time="openTimeDialog"
            @start-timer="handleStartTimer"
            @stop-timer="handleStopTimerFromDetail"
          />
          </div>
        </template>
      </DetailMainContent>

      <DetailSidebar
        :collapsed="sidebarCollapsed"
        :status="currentStatus"
        :transitions="availableTransitions"
        :fields="sidebarFields"
        @toggle-collapse="toggleSidebar"
        @transition="onTransition"
        @edit-field="onEditField"
        @clear-field="onClearField"
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

  <IssueCreatePanel ref="createPanelRef" :visible="showCreatePanel" :project-id="issue?.projectId" :parent-id="createSubtaskParentId" :clone-data="cloneData" @update:visible="onCreatePanelClose" @created="onIssueCreated" @expand-to-fullscreen="onCreatePanelExpand" />

  <!-- Move Issue Modal -->
  <MoveIssueModal
    ref="moveModalRef"
    :visible="showMoveModal"
    :issue-key="issue?.issueKey || ''"
    :current-project-id="issue?.projectId || ''"
    :child-count="issue?.childCount || 0"
    @update:visible="showMoveModal = $event"
    @confirm="onMoveConfirm"
  />

  <!-- Transition Comment Modal -->
  <TransitionCommentModal
    :visible="showTransitionModal"
    :target-status="transitionTarget"
    :require-comment="transitionRequireComment"
    @confirm="onTransitionConfirm"
    @cancel="showTransitionModal = false"
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
      <!-- Author field: 仅管理员/具备 time:log_for_others 权限的用户可见且可修改 -->
      <a-form-item v-if="timeFormCanLogForOthers" label="记录人">
        <a-select
          v-model="timeFormAuthorId"
          placeholder="选择记录人（默认为自己）"
          allow-search
          allow-clear
          style="width: 100%"
        >
          <a-option v-for="member in timeFormProjectMembers" :key="member.userId" :value="member.userId">
            <span class="time-author-option">
              <span class="time-author-avatar">{{ (member.displayName || member.username || '?').charAt(0) }}</span>
              <span class="time-author-name">{{ member.displayName || member.username }}</span>
              <span v-if="member.userId === currentUserId" class="time-author-self">（我）</span>
            </span>
          </a-option>
        </a-select>
      </a-form-item>
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
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
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
import { useIssueDetailSubscription } from '@/composables/useWebSocket'
import type { IssueRealtimeEvent } from '@/composables/useWebSocket'
import { useTabStore } from '@/stores/tabs'
import { useTimerStore } from '@/stores/timer'
import { useRecentIssues } from './composables/useRecentIssues'
import { useDrafts } from './composables/useDrafts'
import type { IssueDetailVO, IssueStatusVO, IssueCommentVO, IssueActivityVO, IssueAttachmentVO, IssueLinkVO, IssueTagVO, ProjectMemberVO, SprintVO, CustomFieldDefinitionVO, FilterRule } from '@/api/types'
import DetailTopBar from './components/DetailTopBar.vue'
import DetailMainContent from './components/DetailMainContent.vue'
import DetailSidebar from './components/DetailSidebar.vue'
import ActivityStream from './components/ActivityStream.vue'
import CommentInput from './components/CommentInput.vue'
import IssueCreatePanel from './IssueCreatePanel.vue'
import MoveIssueModal from './components/MoveIssueModal.vue'
import TransitionCommentModal from './components/TransitionCommentModal.vue'
import type { ActivityItem } from './components/ActivityStream.vue'
import type { SidebarField, StatusInfo } from './components/DetailSidebar.vue'
import { localizeFieldName, localizeFieldValue, localizeStatusName, issueTypeLabelMap } from '@/utils/fieldLabels'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()
const timerStore = useTimerStore()
const { recordVisit: recordRecentVisit } = useRecentIssues()
const { saveDraft: saveIssueDraft } = useDrafts()

// localStorage key for sidebar collapsed state
const SIDEBAR_COLLAPSED_KEY = 'tf_issue_detail_sidebar_collapsed'

// Read initial value from localStorage (default: false = expanded)
const sidebarCollapsed = ref<boolean>(
  localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true'
)

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(sidebarCollapsed.value))
}

// Keep backward-compat: no longer needed, sidebarCollapsed drives the UI directly
const showCreatePanel = ref(false)
const createPanelRef = ref<InstanceType<typeof IssueCreatePanel> | null>(null)
const cloneData = ref<{ projectId: string; title: string; description: string; issueType: string; priority: string } | undefined>(undefined)
const createSubtaskParentId = ref<string | null>(null)
const showTimeDialog = ref(false)
const timeSaving = ref(false)

/** 工时记录弹窗：是否有权为他人记录工时 */
const timeFormCanLogForOthers = ref(false)
/** 工时记录弹窗：选择的记录人 ID（空=默认为自己） */
const timeFormAuthorId = ref<string>('')
/** 工时记录弹窗：项目成员列表（用于 Author 选择器） */
const timeFormProjectMembers = ref<{ userId: string; username?: string; displayName: string }[]>([])

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

/**
 * 综合权限：项目级 issue:edit OR 固有权限（reporter 无条件） OR 资源级（reporter + edit_own / assignee + edit_assigned）
 * 
 * 固有权限（Inherent Permissions）参考 YouTrack：
 * Reporter 天然拥有 issue:view, issue:edit, issue:comment，无需角色显式授予。
 * 注意：不包含 issue:change_status，状态转换需通过工作流引擎控制，需要明确的角色权限授权。
 */
const canEditIssueEffective = computed(() => {
  if (isProjectArchived.value) return false
  if (canEditIssue.value) return true
  // 固有权限：reporter 无条件拥有 edit 权限
  if (isReporter.value) return true
  // 资源级：reporter 需要 issue:edit_own 权限（覆盖场景：非 reporter 但有 edit_own）
  if (isReporter.value && hasProjectPermission('issue:edit_own')) return true
  // 资源级：assignee 需要 issue:edit_assigned 权限
  if (isAssignee.value && hasProjectPermission('issue:edit_assigned')) return true
  return false
})

/**
 * 综合状态变更权限：项目级 issue:change_status OR assignee + edit_assigned
 * 注意：Reporter 的固有权限不包含 change_status，状态变更需要明确的角色权限授权。
 */
const canChangeStatusEffective = computed(() => {
  if (isProjectArchived.value) return false
  if (canChangeStatus.value) return true
  // 资源级：assignee 需要 issue:edit_assigned 权限
  if (isAssignee.value && hasProjectPermission('issue:edit_assigned')) return true
  return false
})

/**
 * 综合评论权限：项目级 issue:comment OR 固有权限（reporter 无条件）
 */
const canCommentEffective = computed(() => {
  if (isProjectArchived.value) return false
  if (canComment.value) return true
  // 固有权限：reporter 无条件拥有 comment 权限
  if (isReporter.value) return true
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

// Transition comment modal state
const showTransitionModal = ref(false)
const transitionTarget = ref<StatusInfo | null>(null)
const transitionRequireComment = ref(false)

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

// ===== WebSocket 实时更新（详情页） =====
const hasRealtimeUpdates = ref(false)

/**
 * 活动流区域的 DOM 引用（用于检测是否在视口内）
 */
const activityStreamRef = ref<HTMLElement | null>(null)

/**
 * 实时更新提示 banner 状态
 */
const realtimeUpdateBanner = ref<{ visible: boolean; message: string }>({
  visible: false,
  message: ''
})

/**
 * 非活动标签页期间积累的更新计数
 */
let inactiveUpdateCount = 0
const originalTitle = ref('')

/**
 * 检测活动流区域是否在视口内
 */
function isActivityStreamVisible(): boolean {
  const el = activityStreamRef.value
  if (!el) return true // 找不到元素时保守处理，不显示 banner
  const rect = el.getBoundingClientRect()
  const viewHeight = window.innerHeight || document.documentElement.clientHeight
  // 元素顶部在视口内或部分可见
  return rect.top < viewHeight && rect.bottom > 0
}

/**
 * 滚动到活动流区域
 */
function scrollToActivity() {
  realtimeUpdateBanner.value.visible = false
  const el = activityStreamRef.value
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

/**
 * 显示实时更新 banner（若活动流不在视口内）或直接 Message 提示
 */
function showRealtimeNotification(message: string, isActivityUpdate: boolean) {
  // 非活动标签页：修改 title 提示
  if (document.hidden) {
    inactiveUpdateCount++
    document.title = `(${inactiveUpdateCount}) ${originalTitle.value || document.title.replace(/^\(\d+\)\s*/, '')}`
    return
  }

  // 活动类更新（评论、附件、关联）：若活动流不在视口内，显示 banner
  if (isActivityUpdate && !isActivityStreamVisible()) {
    realtimeUpdateBanner.value = { visible: true, message }
    // 10 秒后自动消失
    setTimeout(() => {
      realtimeUpdateBanner.value.visible = false
    }, 10000)
  } else {
    // 字段更新或活动流已可见：使用 Message 提示
    Message.info({ content: message, duration: 3000 })
  }
}

/**
 * 当用户重新激活标签页时，清除 title 中的未读计数
 */
function onVisibilityChange() {
  if (!document.hidden && inactiveUpdateCount > 0) {
    inactiveUpdateCount = 0
    document.title = originalTitle.value || document.title.replace(/^\(\d+\)\s*/, '')
  }
}

onMounted(() => {
  originalTitle.value = document.title.replace(/^\(\d+\)\s*/, '')
  document.addEventListener('visibilitychange', onVisibilityChange)
})

useIssueDetailSubscription(
  () => issue.value?.id,
  (event: IssueRealtimeEvent) => {
    // 忽略自己的操作
    const myUserId = authStore.user?.userId
    if (myUserId && String(event.operatorId) === String(myUserId)) return

    if (event.action === 'FIELD_UPDATED' && issue.value) {
      // 实时更新当前工单字段
      for (const [key, value] of Object.entries(event.changes)) {
        ;(issue.value as any)[key] = value
      }
      // 如果状态变更，重新加载可用转换
      if ('statusId' in event.changes) {
        loadTransitions()
      }
      showRealtimeNotification(
        `${event.operatorName || '其他用户'} 更新了此工单`,
        false
      )
    } else if (event.action === 'COMMENT_ADDED') {
      // 有新评论 → 重新加载评论和活动列表
      loadCommentsAndActivities()
      showRealtimeNotification(
        `${event.operatorName || '其他用户'} 添加了新评论`,
        true
      )
    } else if (event.action === 'ATTACHMENT_CHANGED') {
      // 附件变更 → 重新加载附件列表
      loadAttachments()
      showRealtimeNotification(
        `${event.operatorName || '其他用户'} 更新了附件`,
        true
      )
    } else if (event.action === 'LINK_CHANGED') {
      // 关联变更 → 重新加载关联列表
      loadLinks()
      showRealtimeNotification(
        `${event.operatorName || '其他用户'} 更新了关联工单`,
        true
      )
    } else if (event.action === 'DELETED') {
      // 工单被删除 → 提示用户并导航回列表
      Message.warning({ content: '此工单已被删除', duration: 5000 })
      router.push({ name: 'issues' })
    }
  }
)

// 辅助加载函数（用于实时更新时局部刷新）
async function loadTransitions() {
  if (!issue.value) return
  try {
    const res = await issueApi.getAvailableTransitions(issue.value.id)
    if (res.code === 0) transitions.value = res.data || []
  } catch { /* ignore */ }
}

async function loadCommentsAndActivities() {
  if (!issue.value) return
  try {
    const [commRes, actRes] = await Promise.all([
      issueApi.listComments(issue.value.id),
      issueApi.listActivities(issue.value.id)
    ])
    if (commRes.code === 0) comments.value = commRes.data || []
    if (actRes.code === 0) activities.value = actRes.data || []
  } catch { /* ignore */ }
}

async function loadAttachments() {
  if (!issue.value) return
  try {
    const res = await issueApi.listAttachments(issue.value.id)
    if (res.code === 0) attachments.value = res.data || []
  } catch { /* ignore */ }
}

async function loadLinks() {
  if (!issue.value) return
  try {
    const res = await issueApi.listLinks(issue.value.id)
    if (res.code === 0) links.value = res.data || []
  } catch { /* ignore */ }
}
// ===== End WebSocket =====

// ============ 加载数据 ============
onMounted(() => {
  loadAll()
  // 记录原始页面标题（供实时更新未读计数使用）
  if (!originalTitle.value) {
    originalTitle.value = document.title.replace(/^\(\d+\)\s*/, '')
  }
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  // 恢复 title
  if (inactiveUpdateCount > 0) {
    document.title = originalTitle.value || document.title.replace(/^\(\d+\)\s*/, '')
  }
})
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

  // 加载项目时间追踪开关（静默处理 403，观察者等低权限角色可能无权访问）
  try {
    const ttRes = await projectApi.getTimeTrackingSettings(pid, { _silent403: true })
    if (ttRes.code === 0 && ttRes.data) {
      projectTimeTrackingEnabled.value = ttRes.data.enabled
    }
  } catch {
    projectTimeTrackingEnabled.value = false // 403 时默认禁用（不展示无权限功能入口）
  }

  // 无状态变更权限时清空 transitions（确保 UI 渲染为只读）
  if (!needTransitions) {
    transitions.value = []
  }

  try {
    // 核心数据：始终加载（comments, activities, attachments, links, tags, custom fields）
    // tags 使用 _silent403：观察者等低权限角色可能触发 403，不应弹出提示
    const promises: Promise<any>[] = [
      issueApi.listComments(id),
      issueApi.listActivities(id),
      issueApi.listAttachments(id),
      issueApi.listLinks(id),
      tagApi.listProjectTags(pid, { _silent403: true }),
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
      if (results[idx].status === 'fulfilled') sprints.value = (results[idx] as any).value.data?.list || []
      idx++
    }
  } catch { /* ignore partial failures */ }
}

// ============ Computed ============
const projectName = computed(() => issue.value?.projectName || '')
const reporterName = computed(() => issue.value?.reporterName || '未知')
const createdByName = computed(() => issue.value?.createdByName || issue.value?.reporterName || '未知')
const updatedByName = computed(() => issue.value?.updatedByName || '未知')

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
  return attachments.value.map(a => ({
    id: a.id,
    fileName: a.fileName,
    sizeText: formatSize(a.fileSize),
    isPrivate: a.isPrivate || false,
    visibleToGroupNames: a.visibleToGroupNames || []
  }))
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
    blockedBy: s.blockedBy || [],
    requireComment: s.requireComment || false
  }))
})

const sidebarFields = computed<SidebarField[]>(() => {
  const i = issue.value
  if (!i) return []

  // 权限判断（使用资源级覆盖后的综合权限）
  const canEdit = canEditIssueEffective.value
  const canEditCF = canEdit || hasProjectPermission('issue:edit_custom_fields')
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

  // Sprint 选项（仅在有编辑权限时提供，排除已归档 Sprint）
  const sprintOptions = canSprint ? [
    { value: '', label: '未排期' },
    ...sprints.value.filter(s => s.projectId === i.projectId && s.status !== 'archived' && s.status !== 'Archived').map(s => ({ value: s.id, label: s.name }))
  ] : []

  // Sprint 显示值：优先使用 issue 自带的 sprintName，不依赖 sprints 列表
  const sprintDisplayName = i.sprintName || (i.sprintId ? sprints.value.find(s => s.id === i.sprintId)?.name : null) || '未排期'

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
    ...buildCustomFieldSidebarEntries(i, canEditCF),
    { key: '_sep', label: '', value: '', readonly: true },
    // 可见性字段（仅报告者或项目管理员可修改）
    {
      key: 'visibility',
      label: '可见性',
      value: i.visibility === 'restricted' ? '受限访问' : '所有成员',
      editType: 'select' as const,
      rawValue: i.visibility || 'public',
      readonly: !(isReporter.value || hasProjectPermission('project:admin')),
      options: [
        { value: 'public', label: '所有成员' },
        { value: 'restricted', label: '受限访问（仅指定用户）' },
      ]
    },
    { key: 'createdAt', label: '创建时间', value: formatDateTime(i.createdAt), readonly: true },
    { key: 'updatedAt', label: '更新时间', value: formatDateTime(i.updatedAt), readonly: true },
  ]
})

/**
 * 获取自定义字段（list 类型）经过 filterRules 过滤后的可选项。
 * 实现 YouTrack "Filter values based on" 功能：
 * - 如果字段配置了 filterFieldId 和 filterRules，根据源字段当前值过滤选项
 * - 如果未配置，则仅过滤归档选项
 */
function getFilteredOptions(
  cf: CustomFieldDefinitionVO,
  valuesMap: Map<string, { value: string; values?: string[]; displayValue: string; displayValues?: string[]; isMulti?: boolean; color?: string | null; colors?: (string | null)[] }>
): { value: string; label: string; description?: string }[] {
  // 基础过滤：排除归档选项
  let activeOptions = (cf.options || []).filter(o => !o.isArchived)

  // 值依赖过滤：如果配置了 filterFieldId 和 filterRules
  if (cf.filterFieldId && cf.filterRules) {
    try {
      const rules: FilterRule[] = JSON.parse(cf.filterRules)
      if (rules && rules.length > 0) {
        // 获取源字段的当前值
        const sourceStored = valuesMap.get(cf.filterFieldId)
        const sourceValue = sourceStored?.value || ''

        if (sourceValue) {
          // 查找匹配当前源字段值的规则
          const matchedRule = rules.find(r => r.whenValue === sourceValue)
          if (matchedRule && matchedRule.showOnly && matchedRule.showOnly.length > 0) {
            // 只显示规则中允许的选项
            activeOptions = activeOptions.filter(o => matchedRule.showOnly.includes(o.id))
          }
          // 如果没有匹配的规则，显示所有非归档选项（无限制）
        }
        // 如果源字段无值，显示所有非归档选项
      }
    } catch {
      // filterRules 解析失败时回退到显示所有非归档选项
    }
  }

  return activeOptions.map(o => ({ value: o.id, label: o.value, description: o.description || undefined }))
}

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
    let editType: 'select' | 'multi-select' | 'user-select' | 'date' | 'datetime' | 'number' | 'text' | 'period' | undefined
    let options: { value: string; label: string }[] | undefined

    switch (cf.fieldFormat) {
      case 'list':
        editType = isMulti ? 'multi-select' : 'select'
        // Only show active (non-archived) options in the selector;
        // if current value references an archived option, it's still displayed via displayValue
        options = getFilteredOptions(cf, valuesMap)
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
      case 'period':
        editType = 'period'
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
      userAvatar: c.userAvatar || undefined,
      commentId: c.id,
      isEdited: c.isEdited || false,
      rawContent: c.content,
      visibleToGroupNames: c.visibleToGroupNames || undefined,
      html: isHtml ? c.content : renderMarkdown(c.content),
      timeAgo: timeAgo(c.createdAt),
      ts: new Date(c.createdAt).getTime()
    })
  }
  for (const a of activities.value) {
    if (a.action === 'commented') continue
    // 解析 detail 字段（JSON 字符串 → 对象），解析失败时置为 undefined
    let detail: Record<string, any> | undefined
    if (a.detail) {
      try {
        detail = JSON.parse(a.detail)
      } catch {
        detail = undefined
      }
    }
    items.push({ id: 'a_' + a.id, type: 'change', user: a.userName || '用户', userAvatar: a.userAvatar || undefined, action: a.action, field: localizeFieldName(a.fieldName), from: localizeFieldValue(a.fieldName, a.oldValue) || undefined, to: localizeFieldValue(a.fieldName, a.newValue) || undefined, detail, timeAgo: timeAgo(a.createdAt), ts: new Date(a.createdAt).getTime() })
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

function onCreateSubtask() {
  if (!issue.value) return
  createSubtaskParentId.value = issue.value.id
  cloneData.value = undefined
  showCreatePanel.value = true
}

// ========== 附件上传 ==========

const pendingPrivateUpload = ref(false)

function triggerUpload(isPrivate: boolean) {
  pendingPrivateUpload.value = isPrivate
  const input = document.createElement('input')
  input.type = 'file'
  input.multiple = true
  input.onchange = async () => {
    if (!input.files || input.files.length === 0) return
    for (const file of Array.from(input.files)) {
      await doUploadFile(file, isPrivate)
    }
  }
  input.click()
}

async function doUploadFile(file: File, isPrivate: boolean) {
  if (!issue.value) return
  try {
    // 对于私有上传，暂时使用空组列表（后续可弹窗让用户选择组）
    // 简化实现：私有上传时先上传，然后通过编辑可见性来设置组
    const visibleToGroupIds = isPrivate ? [] : undefined
    await issueApi.uploadAttachment(issue.value.id, file, undefined, visibleToGroupIds)
    Message.success(`${file.name} 上传成功`)
    loadAttachments()
  } catch (e: any) {
    Message.error(e.response?.data?.message || `${file.name} 上传失败`)
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
  if (!val) {
    cloneData.value = undefined
    createSubtaskParentId.value = null
  }
}

function onIssueCreated() {
  const wasSubtask = !!createSubtaskParentId.value
  showCreatePanel.value = false
  cloneData.value = undefined
  createSubtaskParentId.value = null

  // 如果创建的是子工单，重新加载父工单详情以刷新子任务列表
  if (wasSubtask) {
    loadAll()
  }
}

/**
 * 用户点击创建面板的「全屏」按钮，跳转到全屏创建页面
 */
function onCreatePanelExpand(formData: any) {
  showCreatePanel.value = false
  cloneData.value = undefined
  if (formData && (formData.title?.trim() || formData.description?.trim())) {
    const draftId = saveIssueDraft(formData)
    if (draftId) {
      router.push({ name: 'IssueCreate', query: { draftId } })
      return
    }
  }
  router.push({ name: 'IssueCreate' })
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

async function onAddTags(tags: { id: string }[]) {
  try {
    const tagIds = tags.map(t => t.id)
    await issueApi.addTags(issue.value!.id, tagIds)
    await loadAll()
  } catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
}

async function onCreateTag(name: string) {
  try {
    const res = await tagApi.createProjectTag(issue.value!.projectId, { name })
    if (res.data) await issueApi.addTag(issue.value!.id, res.data.id)
    await loadAll()
  } catch (e: any) { Message.error(e.response?.data?.message || '操作失败') }
}

async function onAddComment(content: string, visibleToGroupIds?: string[]) {
  try { await issueApi.addComment(issue.value!.id, content, visibleToGroupIds); await loadAll() } catch (e: any) { Message.error(e.response?.data?.message || '评论失败') }
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
  // If the transition requires a comment, show the modal first
  if (target.requireComment) {
    transitionTarget.value = target
    transitionRequireComment.value = true
    showTransitionModal.value = true
    return
  }

  // Otherwise execute directly
  await executeTransition(target, undefined)
}

/** Called when user confirms the transition comment modal */
async function onTransitionConfirm(comment: string) {
  showTransitionModal.value = false
  if (transitionTarget.value) {
    await executeTransition(transitionTarget.value, comment || undefined)
  }
}

/** Execute the actual status transition API call */
async function executeTransition(target: StatusInfo, comment: string | undefined) {
  try {
    const res = await issueApi.transitStatus(issue.value!.id, target.id, comment, issue.value!.version)
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
            const forceRes = await issueApi.transitStatus(issue.value!.id, target.id, comment, issue.value!.version, undefined, true)
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
            const forceRes = await issueApi.transitStatus(issue.value!.id, target.id, comment, issue.value!.version, true)
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
  timeFormAuthorId.value = ''
  // Load attributes for this issue's project
  if (issue.value?.projectId) {
    loadIssueProjectAttributes(issue.value.projectId)
    loadTimeFormPermissions(issue.value.projectId)
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

/**
 * 加载工时弹窗所需的权限 + 项目成员列表
 * 若当前用户具备 time:log_for_others 权限，则展示 Author 字段并加载成员列表
 */
async function loadTimeFormPermissions(projectId: string) {
  try {
    const res = await timeEntryApi.canLogForOthers()
    timeFormCanLogForOthers.value = res.code === 0 ? (res.data ?? false) : false
  } catch {
    timeFormCanLogForOthers.value = false
  }
  if (timeFormCanLogForOthers.value) {
    try {
      const res = await projectApi.listAssignableMembers(projectId)
      if (res.code === 0 && res.data) {
        timeFormProjectMembers.value = res.data.map((m: ProjectMemberVO) => ({
          userId: m.userId,
          username: m.username || m.displayName,
          displayName: m.displayName || m.username || ''
        }))
      }
    } catch { /* silent */ }
  }
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

  // 确定 forUserId：仅在 Author 字段可见且选择了他人时传入
  const forUserId = (timeFormCanLogForOthers.value && timeFormAuthorId.value && timeFormAuthorId.value !== currentUserId.value)
    ? timeFormAuthorId.value
    : undefined

  timeSaving.value = true
  try {
    await timeEntryApi.create({
      issueId: issue.value!.id,
      workDate: timeForm.value.workDate,
      duration,
      description: timeForm.value.description || undefined,
      forUserId,
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
      const res = await issueApi.updateCustomFieldValue(issue.value!.id, fieldId, newValue)
      await loadAll()
      // 显示级联清除警告（源字段值变更导致依赖字段值被自动清除）
      if (res.warnings?.length) {
        res.warnings.forEach((w: string) => Message.info({ content: w, duration: 5000 }))
      }
      Message.success('已更新')
    } catch (e: any) { handleUpdateError(e) }
    return
  }

  // 可见性字段：切换到 restricted 时需要额外配置（当前版本简单切换，受限用户需后续通过弹窗指定）
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
    if (key === 'assignee' && newValue) { await issueApi.assign(issue.value!.id, newValue) }
    else {
      const res = await issueApi.update(issue.value!.id, { [prop]: val, version: issue.value!.version })
      if (res.data?.statusAutoReset) {
        Message.warning({ content: '类型变更导致状态与工作流不兼容，已自动重置为默认状态', duration: 5000 })
      }
      if (res.warnings?.length) {
        res.warnings.forEach((w: string) => Message.warning({ content: w, duration: 5000 }))
      }
    }
    await loadAll()
    Message.success('已更新')
  } catch (e: any) { handleUpdateError(e) }
}

/**
 * 修改工单可见性
 * @param visibility 新的可见性值：'public' 或 'restricted'
 * @param userIds 受限可见用户 ID 列表（visibility=restricted 时有效）
 */
async function onUpdateVisibility(visibility: string, userIds: string[] = []) {
  if (!issue.value) return
  try {
    await issueApi.update(issue.value.id, {
      visibility,
      visibilityUserIds: userIds.map(Number),
      version: issue.value.version
    })
    await loadAll()
    Message.success('可见性已更新')
  } catch (e: any) {
    handleUpdateError(e, '更新可见性失败')
  }
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

/**
 * 清空字段处理：发送 clear 标志位给后端
 */
async function onClearField(key: string) {
  if (!issue.value) return
  const clearKeyMap: Record<string, string> = {
    dueDate: 'clearDueDate',
    estimatedHours: 'clearEstimatedHours',
  }
  const clearProp = clearKeyMap[key]
  if (!clearProp) return

  try {
    await issueApi.update(issue.value.id, { [clearProp]: true, version: issue.value.version })
    await loadAll()
    Message.success('已清除')
  } catch (e: any) { handleUpdateError(e) }
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
.time-author-option { display: flex; align-items: center; gap: 6px; }
.time-author-avatar { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 50%; background: var(--tf-accent); color: #fff; font-size: 11px; font-weight: 600; flex-shrink: 0; }
.time-author-name { flex: 1; font-size: 13px; }
.time-author-self { font-size: 11px; color: var(--tf-text-tertiary); }

/* 实时更新提示 banner */
.realtime-update-banner {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--tf-accent, #0969da);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.realtime-update-icon {
  font-size: 14px;
  animation: spin 1.5s linear infinite;
  display: inline-block;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.realtime-update-text {
  flex: 1;
  font-weight: 500;
}

.realtime-update-action {
  font-size: 12px;
  opacity: 0.85;
  text-decoration: underline;
  white-space: nowrap;
}

.realtime-update-close {
  background: none;
  border: none;
  color: #fff;
  cursor: pointer;
  padding: 2px 6px;
  font-size: 14px;
  opacity: 0.8;
  line-height: 1;
  border-radius: 3px;
  flex-shrink: 0;
}

.realtime-update-close:hover {
  opacity: 1;
  background: rgba(255,255,255,0.15);
}

/* Banner 滑入/滑出动画 */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 200ms ease, opacity 200ms ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}

/* 活动流锚点容器 */
.activity-stream-anchor {
  display: contents;
}
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
