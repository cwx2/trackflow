<template>
  <div class="issue-detail-page" v-if="issue">
    <!-- 实时更新提示 banner（活动流不在视口时显示） -->

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
      :readonly="!canEditIssueEffective && !canChangeStatusEffective"
      :from-query-id="fromQueryId"
      :from-query-name="fromQueryName"
      @copy="copyIssue"
      @create="showCreatePanel = true"
      @toggle-sidebar="toggleSidebar"
      @quick-action-executed="onQuickActionExecuted"
    />

    <div class="page-body">
      <div class="detail-main-column">
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
          @add-link="openAddLinkModal"
          @delete-link="onDeleteLink"
          @upload="triggerUpload(false)"
          @upload-private="triggerUpload(true)"
          @upload-files="onDropFiles"
          @delete-attachment="onDeleteAttachment"
          @delete-all-attachments="onDeleteAllAttachments"
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
              ref="activityStreamCompRef"
              :items="activityItems"
              :current-user-id="currentUserId"
              :can-manage-comments="canManageComments"
              :can-comment="canCommentEffective"
              :show-add-time="projectTimeTrackingEnabled && canLogTime"
              :has-more="activityHasMore"
              :loading-more="activityLoadingMore"
              :total-activities="activityTotal"
              :loaded-activities="activities.length"
              @edit-comment="onEditComment"
              @delete-comment="onDeleteComment"
              @restore-comment="onRestoreComment"
              @permanently-delete-comment="onPermanentlyDeleteComment"
              @reply-comment="onReplyComment"
              @add-time="openTimeDialog"
            />
            </div>
          </template>
        </DetailMainContent>

        <!-- Fixed "Load More" bar between scroll area and comment input -->
        <div v-if="activityHasMore" class="load-more-fixed">
          <button class="load-more-btn" :disabled="activityLoadingMore" @click="loadMoreActivities">
            <template v-if="activityLoadingMore">加载中...</template>
            <template v-else>加载更多活动<span v-if="remainingActivitiesCount > 0" class="load-more-hint">（还有 {{ remainingActivitiesCount }} 条未加载）</span><span v-else-if="activityTotal" class="load-more-hint">（共 {{ activityTotal }} 条）</span></template>
          </button>
          <p v-if="activityStreamCompRef?.currentFilter !== 'all'" class="load-more-filter-hint">当前仅显示「{{ activityStreamCompRef?.currentFilterLabel }}」，加载的内容为全部类型的活动记录</p>
        </div>

        <CommentInput
          v-if="canCommentEffective"
          ref="commentInputRef"
          class="comment-input-fixed"
          :project-id="issue?.projectId"
          :show-add-time="projectTimeTrackingEnabled && canLogTime"
          :can-set-visibility="canEditIssue"
          :timer-running="timerStore.isRunning"
          :timer-issue-match="timerStore.issueId === issue?.id"
          :timer-elapsed="timerStore.elapsedDisplay"
          @submit="handleAddComment"
          @add-time="openTimeDialog"
          @start-timer="handleStartTimer"
          @stop-timer="handleStopTimerFromDetail"
        />
      </div>

      <DetailSidebar
        ref="sidebarRef"
        :collapsed="sidebarCollapsed"
        :status="currentStatus"
        :transitions="availableTransitions"
        :fields="sidebarFields"
        @toggle-collapse="toggleSidebar"
        @transition="onTransition"
        @edit-field="onEditField"
        @clear-field="onClearField"
        @add-option="onAddOption"
        @spent-time-click="onSpentTimeClick"
      />

      <!-- 工时明细浮层 -->
      <SpentTimePopover
        v-if="issue"
        :issue-id="issue.id"
        :visible="spentTimePopoverVisible"
        :can-add-time="canLogTime"
        @update:visible="spentTimePopoverVisible = $event"
        @add-time="onSpentTimeAddTime"
        @edit-time="(entry) => { spentTimePopoverVisible = false; openTimeDialog(entry) }"
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

  <!-- Add Link Modal -->
  <AddLinkModal
    v-if="issue"
    v-model:visible="showAddLinkModal"
    :issue-id="issue.id"
    :project-id="issue.projectId"
    :project-key="issue.issueKey?.split('-')[0] || ''"
    @linked="onLinked"
  />

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
    :show-assignee="true"
    :members="members"
    @confirm="onTransitionConfirm"
    @cancel="showTransitionModal = false"
  />

  <!-- Attachment Privacy Modal (private upload group selection) -->
  <AttachmentPrivacyModal
    :visible="showPrivacyModal"
    @update:visible="showPrivacyModal = $event"
    @confirm="onPrivacyGroupConfirm"
  />

  <!-- Add / Edit Time Entry Dialog -->
  <a-modal
    v-model:visible="showTimeDialog"
    :title="editingTimeEntry ? '编辑工时' : '添加花费的时间'"
    :width="480"
    :footer="false"
    @cancel="showTimeDialog = false"
  >
    <WorkTimeForm
      ref="workTimeFormRef"
      :can-log-for-others="timeFormCanLogForOthers"
      :project-members="timeFormProjectMembers"
      :current-user-id="currentUserId"
      :work-type-values="issueWorkTypeValues"
      :extra-attributes="issueExtraAttributes"
    />
    <div class="time-dialog-footer">
      <div class="time-dialog-footer-left">
        <a-button
          v-if="editingTimeEntry"
          status="danger"
          :loading="timeDeleting"
          @click="deleteTimeEntry"
        >删除</a-button>
      </div>
      <div class="time-dialog-footer-right">
        <a-button @click="showTimeDialog = false">取消</a-button>
        <a-button type="primary" :loading="timeSaving" @click="submitTimeEntry">保存</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
/**
 * IssueDetailView — 工单详情页面
 *
 * 拆分说明（REQ-241）：
 * - composables/useIssueDetailData.ts：数据加载、状态管理、WebSocket、权限
 * - composables/useIssueDetailActions.ts：所有操作处理函数
 * - 本文件：模板 + 计算属性 + 样式
 */
import { formatDateTime } from '@/utils/date'
import { computed, ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { Modal } from '@arco-design/web-vue'
import { IconLock } from '@arco-design/web-vue/es/icon'
import { renderMarkdown, renderHtmlWithMarkdown } from '@/utils/markdown'
import { useTimerStore } from '@/stores/timer'
import { useIssueDetailData } from './composables/useIssueDetailData'
import { useIssueDetailActions } from './composables/useIssueDetailActions'
import { getPriorityColor } from './composables/usePriorityOptions'
import type { IssueDetailVO, CustomFieldDefinitionVO, FilterRule, SprintVO } from '@/api/types'
import DetailTopBar from './components/DetailTopBar.vue'
import DetailMainContent from './components/DetailMainContent.vue'
import DetailSidebar from './components/DetailSidebar.vue'
import SpentTimePopover from './components/SpentTimePopover.vue'
import ActivityStream from './components/ActivityStream.vue'
import CommentInput from './components/CommentInput.vue'
import IssueCreatePanel from '@/components/IssueCreatePanel.vue'
import MoveIssueModal from './components/MoveIssueModal.vue'
import TransitionCommentModal from './components/TransitionCommentModal.vue'
import AttachmentPrivacyModal from './components/AttachmentPrivacyModal.vue'
import AddLinkModal from './components/AddLinkModal.vue'
import WorkTimeForm from '@/components/WorkTimeForm.vue'
import type { ActivityItem } from './components/ActivityStream.vue'
import type { SidebarField, StatusInfo, FieldOption } from './components/DetailSidebar.vue'
import { localizeFieldName, localizeFieldValue, localizeStatusName, localizeLinkType } from '@/utils/fieldLabels'

const route = useRoute()
const timerStore = useTimerStore()

// ============ Route query context: Saved Query breadcrumb ============
const fromQueryId = computed(() => (route.query.fromQuery as string) || undefined)
const fromQueryName = computed(() => (route.query.fromQueryName as string) || undefined)

// ============ Composables ============
const data = useIssueDetailData()
const {
  loading, loadError, issue,
  transitions, comments, activities, activityTotal, activityHasMore, activityLoadingMore,
  attachments, links, projectTagList, members, allProjectMembers, sprints, customFieldDefs,
  dynamicPriorityOptions, dynamicIssueTypeOptions,
  projectTimeTrackingEnabled, issueWorkTypeValues, issueExtraAttributes,
  timeFormCanLogForOthers, timeFormProjectMembers,
  isProjectArchived, currentUserId,
  canCreateIssue, canEditIssue, canDeleteIssue,
  canAssignIssue, canEditSprint, canLogTime, hasProjectPermission,
  canManageComments, canManageCustomFieldsComputed,
  canEditIssueEffective, canChangeStatusEffective, canCommentEffective, canMoveIssue,
  activityStreamRef, onRemoteCommentAdded,
  loadAll, loadMoreActivities, loadAttachments, loadLinks,
  loadIssueProjectAttributes, loadTimeFormPermissions,
} = data

// Sidebar state
const SIDEBAR_COLLAPSED_KEY = 'tf_issue_detail_sidebar_collapsed'
const sidebarCollapsed = ref<boolean>(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true')

// Comment input ref (for reply functionality)
const commentInputRef = ref<InstanceType<typeof CommentInput> | null>(null)

// WorkTimeForm ref（工时弹窗）
const workTimeFormRef = ref<InstanceType<typeof WorkTimeForm> | null>(null)

// ActivityStream component ref (for accessing currentFilter in fixed load-more bar)
const activityStreamCompRef = ref<InstanceType<typeof ActivityStream> | null>(null)

// Remaining activities count for the fixed load-more bar
const remainingActivitiesCount = computed(() => {
  if (!activityTotal.value || !activities.value.length) return 0
  return Math.max(0, activityTotal.value - activities.value.length)
})

// User groups for 'group' type custom fields
const allUserGroups = ref<Array<{ id: string; name: string }>>([])
async function loadUserGroups() {
  if (allUserGroups.value.length > 0) return // already loaded
  try {
    const { groupApi } = await import('@/api/group')
    const res = await groupApi.listSimple()
    allUserGroups.value = res.data || []
  } catch { /* non-critical */ }
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(sidebarCollapsed.value))
}

const sidebarRef = ref<InstanceType<typeof DetailSidebar> | null>(null)
const createPanelRef = ref<InstanceType<typeof IssueCreatePanel> | null>(null)

// ============ Spent Time Popover ============
const spentTimePopoverVisible = ref(false)

function onSpentTimeClick() {
  spentTimePopoverVisible.value = true
}

// ============ Actions composable ============
const actions = useIssueDetailActions({
  issue, attachments, loadAll, loadAttachments, loadLinks,
  loadIssueProjectAttributes, loadTimeFormPermissions,
  canEditIssueEffective, hasProjectPermission,
  currentUserId, sidebarCollapsed, sidebarRef,
  workTimeFormRef,
})
const {
  showTransitionModal, transitionTarget, transitionRequireComment,
  showCreatePanel, cloneData, createSubtaskParentId,
  showAddLinkModal, showMoveModal, moveModalRef, showPrivacyModal,
  showTimeDialog, timeSaving, timeDeleting, editingTimeEntry,
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
} = actions

function onSpentTimeAddTime() {
  spentTimePopoverVisible.value = false
  openTimeDialog()
}

// ============ Comment submit with auto-scroll ============
/**
 * 发布评论后自动滚动到新评论并高亮。
 * onAddComment 负责 API 调用和数据刷新，刷新完成后再高亮。
 */
async function handleAddComment(content: string, visibleToGroupIds?: string[]) {
  await onAddComment(content, visibleToGroupIds)
  // loadAll 已完成，nextTick 后 DOM 已更新，调高亮
  await nextTick()
  activityStreamCompRef.value?.highlightLatest()
}

// 注册远端评论（WebSocket 推送）到达后的高亮回调
onRemoteCommentAdded.value = () => {
  nextTick(() => { activityStreamCompRef.value?.highlightLatest() })
}

// ============ Reply & Copy Comment Link ============
/**
 * 点击「回复」按钮时，将引用块插入评论输入框
 */
function onReplyComment(item: ActivityItem) {
  if (!commentInputRef.value) return
  // 从 HTML 中提取纯文本（去标签）
  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = item.html || item.rawContent || ''
  const plainText = tempDiv.textContent || tempDiv.innerText || ''
  commentInputRef.value.insertReplyQuote(item.user, plainText.trim(), item.commentId)
}

// ============ Lifecycle ============
onMounted(() => {
  loadAll()
  document.addEventListener('paste', onPasteUpload)
})

// Load user groups when custom fields include 'group' type
watch(customFieldDefs, (defs) => {
  if (defs.some(cf => cf.fieldFormat === 'group')) {
    loadUserGroups()
  }
}, { immediate: true })

onUnmounted(() => {
  document.removeEventListener('paste', onPasteUpload)
})

/**
 * 监听 route.hash 变化（通知跳转场景：router.push('/issues/DE4-1544#c_xxx')）。
 * immediate: true 保证首次加载带 hash 的 URL 时也会触发。
 * 等数据加载完成（loading === false）后才开始查找目标 DOM 元素。
 */
watch(
  () => route.hash,
  (newHash) => {
    if (!newHash || newHash.length <= 1) return
    // 如果数据正在加载，等加载完再执行滚动定位
    if (loading.value) {
      const stopWatch = watch(loading, (isLoading) => {
        if (!isLoading) {
          stopWatch()
          nextTick(() => scrollToHashAnchor(newHash))
        }
      })
    } else {
      nextTick(() => scrollToHashAnchor(newHash))
    }
  },
  { immediate: true }
)

/**
 * 解析 URL hash，等待对应 DOM 元素出现后滚动定位并闪动高亮两次。
 * 格式：#c_{commentId}（评论）或 #a_{activityId}（活动记录）
 *
 * @param hash - 完整 hash 字符串（如 "#c_12345"）
 */
function scrollToHashAnchor(hash: string) {
  if (!hash || hash.length <= 1) return
  const targetId = hash.slice(1) // 去掉 '#'

  // 数据已加载完，DOM 可能需要渲染一小段时间，最多等 3 秒
  const maxWaitMs = 3000
  const intervalMs = 150
  let elapsed = 0

  const tryScroll = () => {
    const el = document.getElementById(targetId)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      flashElement(el)
      return
    }
    elapsed += intervalMs
    if (elapsed < maxWaitMs) {
      setTimeout(tryScroll, intervalMs)
    }
  }
  // 给 DOM 一个 tick 的时间渲染
  setTimeout(tryScroll, intervalMs)
}

/**
 * 给元素添加闪动高亮动画（高亮 → 正常 → 高亮 → 正常，共两次）
 */
function flashElement(el: HTMLElement) {
  // 如果元素已有闪动动画（重复点击通知场景），先移除再重新添加
  el.classList.remove('stream-item--flash')
  // 强制 reflow 以重新触发动画
  void el.offsetWidth
  el.classList.add('stream-item--flash')
  setTimeout(() => {
    el.classList.remove('stream-item--flash')
  }, 1600)
}

// ============ Computed (view-specific) ============
const projectName = computed(() => issue.value?.projectName || '')
const reporterName = computed(() => issue.value?.reporterName || '未知')
const createdByName = computed(() => issue.value?.createdByName || issue.value?.reporterName || '未知')
const updatedByName = computed(() => issue.value?.updatedByName || '未知')
const issueTags = computed(() => issue.value?.tags || [])
const projectTags = computed(() => projectTagList.value)

const issueLinks = computed(() => {
  return links.value.map(l => ({
    ...l,
    typeLabel: localizeLinkType(l.linkType),
    statusName: localizeStatusName(l.issueStatus?.name),
    statusColor: l.issueStatus?.color || '',
    isUnresolvedBlocker: l.linkType === 'blocked_by' && l.issueStatus && !l.issueStatus.isClosed,
    priority: l.priority,
    priorityColor: l.priorityColor,
    priorityOrder: l.priorityOrder,
  }))
})

const issueAttachments = computed(() => {
  return attachments.value.map(a => ({
    id: a.id, fileName: a.fileName, filePath: a.filePath, fileSize: a.fileSize,
    contentType: a.contentType || '', createdAt: a.createdAt, uploadedBy: a.uploadedBy,
    isPrivate: a.isPrivate || false, visibleToGroupNames: a.visibleToGroupNames || []
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
    id: s.id, name: localizeStatusName(s.name), color: s.color,
    blocked: s.blocked || false, blockedBy: s.blockedBy || [],
    requireComment: s.requireComment || false, transitionName: s.transitionName || undefined
  }))
})

function getDueDateStatus(dueDate: string | undefined | null): 'overdue' | 'due-soon' | 'normal' {
  if (!dueDate) return 'normal'
  if (issue.value?.status?.isClosed) return 'normal'
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const due = new Date(dueDate); due.setHours(0, 0, 0, 0)
  const diffDays = Math.floor((due.getTime() - today.getTime()) / 86400000)
  if (diffDays < 0) return 'overdue'
  if (diffDays <= 3) return 'due-soon'
  return 'normal'
}

function getDueDateTooltip(dueDate: string | undefined | null): string {
  if (!dueDate) return ''
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const due = new Date(dueDate); due.setHours(0, 0, 0, 0)
  const diffDays = Math.floor((due.getTime() - today.getTime()) / 86400000)
  if (diffDays < 0) return `已逾期 ${Math.abs(diffDays)} 天`
  if (diffDays === 0) return '今天到期'
  if (diffDays === 1) return '明天到期'
  return `${diffDays} 天后到期`
}

function priorityDot(p: string) {
  return getPriorityColor(p, issue.value?.projectId)
}

function getDetailIssueTypeColor(issueType: string | null | undefined): string {
  if (!issueType) return '#6366f1'
  const opt = dynamicIssueTypeOptions.value.find(o => o.value === issueType || o.value.toLowerCase() === issueType.toLowerCase())
  return opt?.color || '#6366f1'
}

function getDetailIssueTypeLabel(issueType: string | null | undefined): string {
  if (!issueType) return '未知'
  const opt = dynamicIssueTypeOptions.value.find(o => o.value === issueType || o.value.toLowerCase() === issueType.toLowerCase())
  return opt?.label || issueType
}

function getFilteredOptions(
  cf: CustomFieldDefinitionVO,
  valuesMap: Map<string, { value: string; values?: string[]; displayValue: string; displayValues?: string[]; isMulti?: boolean; color?: string | null; colors?: (string | null)[] }>
): { value: string; label: string; description?: string }[] {
  let activeOptions = (cf.options || []).filter(o => !o.isArchived)
  if (cf.filterFieldId && cf.filterRules) {
    try {
      const rules: FilterRule[] = JSON.parse(cf.filterRules)
      if (rules?.length) {
        const sourceStored = valuesMap.get(cf.filterFieldId)
        const sourceValue = sourceStored?.value || ''
        if (sourceValue) {
          const matchedRule = rules.find(r => r.whenValue === sourceValue)
          if (matchedRule?.showOnly?.length) {
            activeOptions = activeOptions.filter(o => matchedRule.showOnly.includes(o.id))
          }
        }
      }
    } catch { /* 条件规则解析容错，显示所有选项 */ }
  }
  // version 类型：未发布版本排在前（Fix versions 语义）
  if (cf.fieldFormat === 'version') {
    activeOptions = [...activeOptions].sort((a, b) => {
      const aReleased = a.isReleased ? 1 : 0
      const bReleased = b.isReleased ? 1 : 0
      return aReleased - bReleased
    })
  }
  return activeOptions.map(o => {
    let label = o.value
    if (cf.fieldFormat === 'ownedField' && o.ownerDisplayName) { label = `${o.value} → ${o.ownerDisplayName}` }
    return { value: o.id, label, description: o.description || undefined }
  })
}

/**
 * 构建迭代选择器的分组选项列表。
 * 按状态分组：活跃 → 计划中 → 已完成，每组有标题。
 * 已完成的 Sprint 以弱化样式展示，选择时弹出确认。
 */
function buildSprintOptions(allSprints: SprintVO[], projectId: string): FieldOption[] {
  const projectSprints = allSprints.filter(s => s.projectId === projectId && s.status !== 'archived' && s.status !== 'Archived')
  const active = projectSprints.filter(s => s.status?.toLowerCase() === 'active')
  const planned = projectSprints.filter(s => s.status?.toLowerCase() === 'planned')
  const completed = projectSprints.filter(s => s.status?.toLowerCase() === 'completed')

  const options: FieldOption[] = [{ value: '', label: '未排期' }]

  if (active.length === 0 && planned.length === 0) {
    // No active/planned sprints - show a hint
    options.push({ value: '__hint_no_active', label: '暂无活跃或计划中的迭代', isGroupLabel: true })
  }

  if (active.length > 0) {
    options.push({ value: '__group_active', label: '进行中', isGroupLabel: true })
    for (const s of active) {
      options.push({ value: s.id, label: s.name, badge: '活跃', badgeColor: 'var(--green-6, #00b42a)' })
    }
  }

  if (planned.length > 0) {
    options.push({ value: '__group_planned', label: '计划中', isGroupLabel: true })
    for (const s of planned) {
      options.push({ value: s.id, label: s.name })
    }
  }

  if (completed.length > 0) {
    options.push({ value: '__group_completed', label: '已完成', isGroupLabel: true })
    for (const s of completed) {
      options.push({
        value: s.id,
        label: s.name,
        badge: '已完成',
        badgeColor: 'var(--tf-text-quaternary, rgba(128,128,128,0.4))',
        dimmed: true,
        confirmMessage: `迭代"${s.name}"已完成，确定要将工单移入已结束的迭代吗？`,
      })
    }
  }

  return options
}

function buildCustomFieldSidebarEntries(i: IssueDetailVO, canEdit: boolean): SidebarField[] {
  if (!customFieldDefs.value.length) return []
  const valuesMap = new Map<string, { value: string; values?: string[]; displayValue: string; displayValues?: string[]; isMulti?: boolean; color?: string | null; colors?: (string | null)[] }>()
  if (i.customFieldDetails) {
    for (const v of i.customFieldDetails) {
      valuesMap.set(v.customFieldId, { value: v.value || '', values: v.values, displayValue: v.displayValue || v.value || '', displayValues: v.displayValues, isMulti: v.isMulti, color: v.color, colors: v.colors })
    }
  }
  // 已由侧边栏专用条目渲染的内置字段名称（REQ-387: 避免重复展示）
  const SYSTEM_RENDERED_BUILTIN = new Set(['priority', 'type', 'state'])
  const visibleDefs = customFieldDefs.value.filter(cf => {
    // 跳过已有独立系统控件的内置字段
    if (cf.isBuiltIn && SYSTEM_RENDERED_BUILTIN.has(cf.name.toLowerCase())) return false
    if (!cf.conditionFieldId || !cf.conditionValues || cf.conditionValues.length === 0) return true
    const condStored = valuesMap.get(cf.conditionFieldId)
    const condValue = condStored?.value || ''
    if (!condValue) return false
    return cf.conditionValues.includes(condValue)
  })
  return visibleDefs.map(cf => {
    const stored = valuesMap.get(cf.id)
    const isMulti = cf.isMulti || stored?.isMulti
    const rawValue = stored?.value || ''
    const rawValues = stored?.values || []
    let displayValue: string
    if (isMulti && stored?.displayValues?.length) { displayValue = stored.displayValues.join(', ') }
    else if (stored?.displayValue) { displayValue = stored.displayValue }
    else if (cf.requiresExplicitSelection) { displayValue = '设置值' }
    else { displayValue = '-' }
    const isSetValuePrompt = !stored?.displayValue && !stored?.value && cf.requiresExplicitSelection
    let editType: 'select' | 'multi-select' | 'user-select' | 'date' | 'datetime' | 'number' | 'text' | 'period' | undefined
    let options: { value: string; label: string }[] | undefined
    switch (cf.fieldFormat) {
      case 'list': case 'ownedField': case 'version': case 'build':
        editType = isMulti ? 'multi-select' : 'select'
        options = getFilteredOptions(cf, valuesMap)
        break
      case 'user':
        editType = 'user-select'
        options = allProjectMembers.value.map(m => ({ value: m.userId, label: m.displayName }))
        break
      case 'group':
        editType = isMulti ? 'multi-select' : 'select'
        options = allUserGroups.value.map(g => ({ value: g.id, label: g.name }))
        break
      case 'date': editType = 'date'; break
      case 'datetime': editType = 'datetime'; break
      case 'int': case 'float': editType = 'number'; break
      case 'bool': editType = 'select'; options = [{ value: 'true', label: '是' }, { value: 'false', label: '否' }]; break
      case 'period' as any: editType = 'period'; break
      default: editType = 'text'; break
    }
    let fieldColor: string | undefined
    if ((cf.fieldFormat === 'list' || cf.fieldFormat === 'ownedField' || cf.fieldFormat === 'version' || cf.fieldFormat === 'build') && stored) {
      if (isMulti && stored.colors?.length) { fieldColor = stored.colors.find(c => c != null) || undefined }
      else if (stored.color) { fieldColor = stored.color }
    }
    return {
      key: `cf_${cf.id}`, label: cf.name, value: displayValue, dot: fieldColor,
      editType: editType as any, rawValue, rawValues: isMulti ? rawValues : undefined,
      readonly: !canEdit || cf.editable === false, options,
      canAddOption: (cf.fieldFormat === 'list' || cf.fieldFormat === 'ownedField' || cf.fieldFormat === 'version' || cf.fieldFormat === 'build') && canManageCustomFieldsComputed.value,
      customFieldId: cf.id, isSetValuePrompt,
      isEmptyCustomField: !isSetValuePrompt && !rawValue && !(isMulti && rawValues.length > 0)
    }
  })
}

const sidebarFields = computed<SidebarField[]>(() => {
  const i = issue.value
  if (!i) return []
  const canEdit = canEditIssueEffective.value
  const canEditCF = canEdit || hasProjectPermission('issue:edit_custom_fields')
  const canTransition = canChangeStatusEffective.value
  const canAssign = canAssignIssue.value
  const canSprint = canEditSprint.value
  const statusOptions = [
    { value: currentStatus.value.id, label: `${currentStatus.value.name}（当前）`, dot: currentStatus.value.color },
    ...availableTransitions.value.map(s => ({ value: s.id, label: s.blocked ? `⚠ ${s.transitionName || s.name}` : (s.transitionName || s.name), dot: s.color, badge: s.blocked ? '被阻塞' : undefined, badgeColor: s.blocked ? '#d29922' : undefined }))
  ]
  const userOptions = canAssign ? members.value.map(m => ({ value: m.userId, label: m.displayName })) : []
  const sprintOptions = canSprint ? buildSprintOptions(sprints.value, i.projectId) : []
  const sprintDisplayName = i.sprintName || (i.sprintId ? sprints.value.find(s => s.id === i.sprintId)?.name : null) || '未排期'
  // 判断当前 Sprint 是否已完成（用于字段面板视觉标识）
  const sprintCompleted = i.sprintStatus?.toLowerCase() === 'completed' ||
    (i.sprintId ? sprints.value.find(s => s.id === i.sprintId)?.status?.toLowerCase() === 'completed' : false)
  return [
    { key: 'project', label: '项目', value: projectName.value, readonly: true, readonlyReason: '工单创建后不可变更项目' },
    { key: 'priority', label: '优先级', value: i.priority, dot: priorityDot(i.priority), editType: 'select' as const, rawValue: i.priority, readonly: !canEdit, options: dynamicPriorityOptions.value.map(o => ({ value: o.value, label: o.label })) },
    { key: 'state', label: '状态', value: currentStatus.value.name, dot: currentStatus.value.color, editType: 'select' as const, rawValue: currentStatus.value.id, readonly: !canTransition || availableTransitions.value.length === 0, options: statusOptions },
    { key: 'issueType', label: '类型', value: getDetailIssueTypeLabel(i.issueType), dot: getDetailIssueTypeColor(i.issueType), editType: 'select' as const, rawValue: i.issueType, readonly: !canEdit, options: dynamicIssueTypeOptions.value.map(o => ({ value: o.value, label: o.label })) },
    { key: 'assignee', label: '负责人', value: i.assigneeName || '未分配', editType: 'user-select' as const, rawValue: i.assigneeId || '', readonly: !canAssign, options: userOptions },
    { key: 'reporter', label: '报告人', value: reporterName.value, readonly: true, readonlyReason: '报告人为工单创建者，不可修改', userId: i.reporterId || undefined },
    { key: 'sprint', label: '迭代', value: sprintDisplayName, editType: 'select' as const, rawValue: i.sprintId || '', readonly: !canSprint, options: sprintOptions, class: sprintCompleted ? 'sprint-completed' : undefined, badge: sprintCompleted ? '已结束' : undefined, badgeColor: sprintCompleted ? 'var(--tf-warning, #d29922)' : undefined, tooltip: sprintCompleted ? '该迭代已完成，工单仍未关闭。建议移至当前活跃迭代或 Backlog' : undefined },
    { key: 'dueDate', label: '截止日期', value: i.dueDate || '-', editType: 'date' as const, rawValue: i.dueDate || '', readonly: !canEdit, class: getDueDateStatus(i.dueDate) !== 'normal' ? `due-${getDueDateStatus(i.dueDate)}` : undefined, tooltip: getDueDateStatus(i.dueDate) !== 'normal' ? getDueDateTooltip(i.dueDate) : undefined },
    ...(projectTimeTrackingEnabled.value ? [
      { key: 'estimatedHours', label: '预估工时', value: i.estimatedHours ? `${i.estimatedHours}h` : '-', editType: 'number' as const, rawValue: i.estimatedHours ? String(i.estimatedHours) : '', readonly: !canEdit, progress: i.estimatedHours ? { spent: i.spentHours || 0, estimated: i.estimatedHours } : undefined },
      { key: 'spentHours', label: '已花时间', value: i.spentHours ? `${i.spentHours}h` : '-', readonly: true, readonlyReason: 'computed', class: (i.estimatedHours && i.estimatedHours > 0 && (i.spentHours || 0) > i.estimatedHours) ? 'time-over-budget' : undefined, tooltip: (i.estimatedHours && i.estimatedHours > 0 && (i.spentHours || 0) > i.estimatedHours) ? `已超出预估 ${((i.spentHours || 0) - i.estimatedHours).toFixed(1)}h` : undefined },
    ] : []),
    ...(projectTimeTrackingEnabled.value && i.derivedEstimatedHours != null ? [{ key: 'derivedEstimatedHours', label: '总预估工时', value: `${i.derivedEstimatedHours}h`, readonly: true, readonlyReason: 'derived' }] : []),
    ...(projectTimeTrackingEnabled.value && i.derivedSpentHours != null ? [{ key: 'derivedSpentHours', label: '总花费时间', value: `${i.derivedSpentHours}h`, readonly: true, readonlyReason: 'derived', class: (i.derivedEstimatedHours && i.derivedEstimatedHours > 0 && i.derivedSpentHours > i.derivedEstimatedHours) ? 'time-over-budget' : undefined, tooltip: (i.derivedEstimatedHours && i.derivedEstimatedHours > 0 && i.derivedSpentHours > i.derivedEstimatedHours) ? `已超出预估 ${(i.derivedSpentHours - i.derivedEstimatedHours).toFixed(1)}h` : undefined }] : []),
    ...buildCustomFieldSidebarEntries(i, canEditCF),
    { key: '_sep', label: '', value: '', readonly: true },
    { key: 'visibility', label: '可见性', value: i.visibility === 'restricted' ? '受限访问' : '所有成员', editType: 'select' as const, rawValue: i.visibility || 'public', readonly: !(canEdit || hasProjectPermission('project:admin')), options: [{ value: 'public', label: '所有成员' }, { value: 'restricted', label: '受限访问（仅指定用户）' }] },
    { key: 'createdAt', label: '创建时间', value: formatDateTime(i.createdAt), readonly: true },
    { key: 'updatedAt', label: '更新时间', value: formatDateTime(i.updatedAt), readonly: true },
  ]
})

// ============ Activity items ============
const activityItems = computed<ActivityItem[]>(() => {
  const items: ActivityItem[] = []
  // Build comment items (no longer merge field changes into comments)
  for (const c of comments.value) {
    const isDeleted = !!c.deletedAt
    const content = c.content || ''
    const isHtml = content.trim().startsWith('<')
    items.push({ id: 'c_' + c.id, type: 'comment', user: c.userName || '用户', userId: c.userId, userAvatar: c.userAvatar || undefined, commentId: c.id, isEdited: c.isEdited || false, isDeleted, rawContent: content, visibleToGroupNames: c.visibleToGroupNames || undefined, html: isDeleted ? '' : (isHtml ? renderHtmlWithMarkdown(content) : renderMarkdown(content)), timeAgo: timeAgo(c.createdAt), ts: new Date(c.createdAt).getTime() })
  }
  // Build field change items, grouping consecutive changes from the same user within 5 seconds
  const CHANGE_GROUP_WINDOW_MS = 5_000
  const fieldChanges = activities.value.filter(a => a.action !== 'commented' && a.fieldName)
  // Sort by timestamp ascending for grouping
  const sortedChanges = [...fieldChanges].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
  const mergedChangeIds = new Set<string>()
  for (let i = 0; i < sortedChanges.length; i++) {
    if (mergedChangeIds.has(sortedChanges[i].id)) continue
    const anchor = sortedChanges[i]
    const anchorTs = new Date(anchor.createdAt).getTime()
    const groupedChanges: { field: string; from?: string; to?: string }[] = []
    // Find all subsequent changes from same user within the window
    for (let j = i + 1; j < sortedChanges.length; j++) {
      const candidate = sortedChanges[j]
      if (candidate.userId !== anchor.userId) continue
      const candidateTs = new Date(candidate.createdAt).getTime()
      if (candidateTs - anchorTs > CHANGE_GROUP_WINDOW_MS) break
      if (mergedChangeIds.has(candidate.id)) continue
      const fromVal = localizeFieldValue(candidate.fieldName, candidate.oldValue)
      const toVal: string = localizeFieldValue(candidate.fieldName, candidate.newValue) ?? ''
      groupedChanges.push({ field: (localizeFieldName(candidate.fieldName) || candidate.fieldName) as string, ...(fromVal !== undefined && { from: fromVal }), to: toVal })
      mergedChangeIds.add(candidate.id)
    }
    let detail: Record<string, any> | undefined
    if (anchor.detail) { try { detail = JSON.parse(anchor.detail) } catch { detail = undefined } }
    const anchorFrom = localizeFieldValue(anchor.fieldName, anchor.oldValue)
    const anchorTo = localizeFieldValue(anchor.fieldName, anchor.newValue)
    const item: ActivityItem = { id: 'a_' + anchor.id, type: 'change', user: anchor.userName || '用户', userId: anchor.userId, userAvatar: anchor.userAvatar || undefined, action: anchor.action, field: localizeFieldName(anchor.fieldName), ...(anchorFrom !== undefined && { from: anchorFrom }), ...(anchorTo !== undefined && { to: anchorTo }), detail, timeAgo: timeAgo(anchor.createdAt), ts: anchorTs }
    if (groupedChanges.length > 0) {
      item.relatedChanges = groupedChanges
    }
    items.push(item)
  }
  // Add non-field-change activities (created, deleted, etc.)
  for (const a of activities.value) {
    if (a.action === 'commented') continue
    if (a.fieldName) continue // already handled above
    let detail: Record<string, any> | undefined
    if (a.detail) { try { detail = JSON.parse(a.detail) } catch { detail = undefined } }
    items.push({ id: 'a_' + a.id, type: 'change', user: a.userName || '用户', userId: a.userId, userAvatar: a.userAvatar || undefined, action: a.action, field: localizeFieldName(a.fieldName), from: localizeFieldValue(a.fieldName, a.oldValue) || undefined, to: localizeFieldValue(a.fieldName, a.newValue) || undefined, detail, timeAgo: timeAgo(a.createdAt), ts: new Date(a.createdAt).getTime() })
  }
  return items
})

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



// Route guard
onBeforeRouteLeave((_to, _from, next) => {
  const panel = createPanelRef.value
  if (showCreatePanel.value && panel && (panel as any).isDirty) {
    Modal.confirm({ title: '有未保存的更改', content: '创建工单表单中有未保存的内容，确定要离开吗？', okText: '放弃更改', cancelText: '继续编辑', simple: false, onOk: () => { next() }, onCancel: () => { next(false) } })
  } else { next() }
})
</script>

<style scoped>
.attr-value-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }

/* 工时弹窗底部按钮栏 */
.time-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 12px;
  margin-top: 4px;
  border-top: 1px solid var(--tf-border-light);
}
.time-dialog-footer-left { display: flex; gap: 8px; }
.time-dialog-footer-right { display: flex; gap: 8px; }

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
  background: var(--tf-warning-bg);
  border-bottom: 1px solid var(--tf-warning-medium);
  flex-shrink: 0;
}

.archived-icon {
  font-size: 18px;
  color: var(--tf-warning);
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
  color: var(--tf-warning);
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

.detail-main-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.load-more-fixed {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 4px 24px;
}

.load-more-fixed .load-more-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  background: none;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  transition: background 150ms, color 150ms;
}
.load-more-fixed .load-more-btn:hover:not(:disabled) {
  color: var(--tf-text-secondary);
  background: var(--tf-bg-hover);
}
.load-more-fixed .load-more-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.load-more-fixed .load-more-hint {
  color: var(--tf-text-muted);
  margin-left: 4px;
}
.load-more-fixed .load-more-filter-hint {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: center;
}

.comment-input-fixed {
  flex-shrink: 0;
  padding: 8px 24px 12px;
}

.comment-input-fixed :deep(.comment-input) {
  margin-top: 0;
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

<!-- 全局样式：通知跳转高亮闪动（需穿透 ActivityStream 子组件，不能用 scoped） -->
<style>
@keyframes stream-item-flash {
  0%   { background: transparent; }
  15%  { background: var(--tf-accent-bg, rgba(9, 105, 218, 0.12)); }
  35%  { background: transparent; }
  55%  { background: var(--tf-accent-bg, rgba(9, 105, 218, 0.12)); }
  80%  { background: transparent; }
  100% { background: transparent; }
}
.stream-item--flash {
  animation: stream-item-flash 1.6s ease-in-out forwards !important;
  border-radius: 6px !important;
}
</style>
