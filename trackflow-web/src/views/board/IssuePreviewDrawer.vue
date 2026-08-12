<template>
  <a-drawer
    :visible="visible"
    :width="480"
    :footer="false"
    unmount-on-close
    class="issue-preview-drawer"
    @cancel="close"
  >
    <template #title>
      <div class="preview-title-bar">
        <span class="preview-issue-key" @click="goToDetail">{{ detail?.issueKey }}</span>
        <div class="preview-actions">
          <a-tooltip content="在新页面打开详情">
            <a-button type="text" size="mini" @click="goToDetail">
              <template #icon><icon-share-external /></template>
            </a-button>
          </a-tooltip>
        </div>
      </div>
    </template>

    <!-- 加载状态 -->
    <div v-if="loading" class="preview-loading">
      <a-spin :size="24" />
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="preview-error">
      <div class="preview-error-icon">⚠️</div>
      <p class="preview-error-msg">{{ loadError }}</p>
      <a-button size="small" @click="loadDetail">重试</a-button>
    </div>

    <!-- 内容 -->
    <div v-else-if="detail" class="preview-content">
      <!-- 标题 -->
      <h2 class="preview-issue-title">{{ detail.title }}</h2>

      <!-- 状态与优先级（可编辑） -->
      <div class="preview-meta-row">
        <a-trigger
          trigger="click"
          position="bl"
          :popup-visible="editingStatus"
          @update:popup-visible="v => v ? openStatusEdit() : closeStatusEdit()"
        >
          <span
            class="preview-status-badge preview-status-badge--editable"
            :style="{ backgroundColor: statusColor, color: 'var(--tf-text-on-accent)' }"
            title="点击切换状态"
          >{{ detail.status?.name }} <span class="edit-chevron">▾</span></span>
          <template #content>
            <div class="inline-edit-panel">
              <div class="inline-edit-title">变更状态</div>
              <div v-if="loadingTransitions" class="inline-edit-loading"><a-spin :size="16" /></div>
              <div v-else-if="availableTransitions.length === 0" class="inline-edit-empty">无可用状态转换</div>
              <div v-else class="inline-edit-list">
                <div
                  v-for="t in availableTransitions"
                  :key="t.id"
                  class="inline-edit-item"
                  @click="selectStatus(t)"
                >
                  <span class="status-dot" :style="{ background: t.color || 'var(--color-fill-4)' }"></span>
                  <span class="item-label">{{ t.transitionName || t.name }}</span>
                </div>
              </div>
            </div>
          </template>
        </a-trigger>

        <a-trigger
          trigger="click"
          position="bl"
          :popup-visible="editingPriority"
          @update:popup-visible="v => v ? editingPriority = true : editingPriority = false"
        >
          <span class="preview-priority preview-priority--editable" title="点击修改优先级">
            {{ priorityIcon(detail.priority) }} {{ detail.priority }} <span class="edit-chevron">▾</span>
          </span>
          <template #content>
            <div class="inline-edit-panel">
              <div class="inline-edit-title">修改优先级</div>
              <div class="inline-edit-list">
                <div
                  v-for="p in priorityOptions"
                  :key="p.value"
                  class="inline-edit-item"
                  :class="{ 'inline-edit-item--selected': detail.priority === p.value }"
                  @click="selectPriority(p.value)"
                >
                  <span class="item-icon">{{ p.icon }}</span>
                  <span class="item-label">{{ p.label }}</span>
                </div>
              </div>
            </div>
          </template>
        </a-trigger>

        <span class="preview-type">{{ typeLabel(detail.issueType) }}</span>
      </div>

      <!-- 关键字段 -->
      <div class="preview-fields">
        <div class="preview-field">
          <span class="field-label">负责人</span>
          <a-trigger
            trigger="click"
            position="bl"
            :popup-visible="editingAssignee"
            @update:popup-visible="v => v ? openAssigneeEdit() : closeAssigneeEdit()"
          >
            <span class="field-value field-value--editable" title="点击修改负责人">
              {{ detail.assigneeName || '未分配' }}
              <span class="edit-chevron">▾</span>
            </span>
            <template #content>
              <div class="inline-edit-panel inline-edit-panel--wide">
                <div class="inline-edit-title">分配负责人</div>
                <div class="inline-edit-search">
                  <input
                    v-model="assigneeSearch"
                    class="inline-search-input"
                    placeholder="搜索成员..."
                    @keyup.escape="closeAssigneeEdit"
                  />
                </div>
                <div v-if="loadingMembers" class="inline-edit-loading"><a-spin :size="16" /></div>
                <div v-else class="inline-edit-list">
                  <div
                    class="inline-edit-item"
                    :class="{ 'inline-edit-item--selected': !detail.assigneeId }"
                    @click="selectAssignee(null, null)"
                  >
                    <span class="item-label">未分配</span>
                  </div>
                  <div
                    v-for="m in filteredMembers"
                    :key="m.userId"
                    class="inline-edit-item"
                    :class="{ 'inline-edit-item--selected': detail.assigneeId === m.userId }"
                    @click="selectAssignee(m.userId, m.displayName)"
                  >
                    <span class="item-label">{{ m.displayName }}</span>
                  </div>
                  <div v-if="filteredMembers.length === 0 && assigneeSearch" class="inline-edit-empty">无匹配成员</div>
                </div>
              </div>
            </template>
          </a-trigger>
        </div>
        <div class="preview-field" v-if="detail.reporterName">
          <span class="field-label">报告者</span>
          <span class="field-value">{{ detail.reporterName }}</span>
        </div>
        <div class="preview-field" v-if="detail.sprintName">
          <span class="field-label">迭代</span>
          <span class="field-value">{{ detail.sprintName }}</span>
        </div>
        <div class="preview-field" v-if="detail.dueDate">
          <span class="field-label">截止日期</span>
          <span class="field-value" :class="dueDateFieldClass">
            {{ detail.dueDate }}
            <span v-if="dueDateInfo.status !== 'normal'" class="due-date-hint" :class="'due-date-hint--' + dueDateInfo.status">
              {{ dueDateInfo.tooltip }}
            </span>
          </span>
        </div>
        <div class="preview-field" v-if="timeTrackingEnabled && detail.estimatedHours">
          <span class="field-label">预估工时</span>
          <span class="field-value">
            <TimeProgressIndicator
              v-if="detail.spentHours != null || detail.estimatedHours"
              :spent="detail.spentHours || 0"
              :estimated="detail.estimatedHours"
            />
            {{ detail.estimatedHours }}h
          </span>
        </div>
        <div class="preview-field" v-if="timeTrackingEnabled && detail.spentHours">
          <span class="field-label">已花费</span>
          <span 
            class="field-value" 
            :class="{ 'field-value--over-budget': detail.estimatedHours && detail.estimatedHours > 0 && detail.spentHours > detail.estimatedHours }"
            :title="(detail.estimatedHours && detail.estimatedHours > 0 && detail.spentHours > detail.estimatedHours) ? `已超出预估 ${(detail.spentHours - detail.estimatedHours).toFixed(1)}h` : ''"
          >
            {{ detail.spentHours }}h
          </span>
        </div>
        <!-- 自定义字段 -->
        <template v-if="detail.customFieldDetails && detail.customFieldDetails.length > 0">
          <div class="preview-field" v-for="cf in detail.customFieldDetails" :key="cf.customFieldId">
            <span class="field-label">{{ cf.fieldName }}</span>
            <span class="field-value">{{ cf.displayValue || cf.value || '—' }}</span>
          </div>
        </template>
      </div>

      <!-- 标签 -->
      <div v-if="detail.tags && detail.tags.length > 0" class="preview-tags">
        <span
          v-for="tag in detail.tags"
          :key="tag.id"
          class="preview-tag"
          :style="{ backgroundColor: tag.color + '22', color: tag.color, borderColor: tag.color + '44' }"
        >{{ tag.name }}</span>
      </div>

      <!-- 描述 -->
      <div class="preview-section">
        <h4 class="section-title">描述</h4>
        <div
          v-if="detail.description"
          class="preview-description"
          v-html="renderedDescription"
        ></div>
        <p v-else class="preview-empty-text">暂无描述</p>
      </div>

      <!-- 子任务进度 -->
      <div v-if="detail.childProgress && detail.childProgress.total > 0" class="preview-section">
        <h4 class="section-title">子任务</h4>
        <div class="preview-child-progress">
          <div class="progress-bar-track">
            <div
              class="progress-bar-fill"
              :style="{ width: childProgressPercent + '%' }"
            ></div>
          </div>
          <span class="progress-text">{{ detail.childProgress.closed }}/{{ detail.childProgress.total }}</span>
        </div>
      </div>

      <!-- 最近评论 -->
      <div v-if="comments.length > 0" class="preview-section">
        <h4 class="section-title">最近评论 ({{ comments.length }})</h4>
        <div class="preview-comments">
          <div
            v-for="comment in displayedComments"
            :key="comment.id"
            class="preview-comment"
          >
            <div class="comment-header">
              <span class="comment-author">{{ comment.userName }}</span>
              <span class="comment-time">{{ formatRelativeTime(comment.createdAt) }}</span>
            </div>
            <div class="comment-body" v-html="renderCommentContent(comment.content)"></div>
          </div>
          <a-button
            v-if="comments.length > 3"
            type="text"
            size="small"
            class="view-all-comments"
            @click="goToDetail"
          >查看全部 {{ comments.length }} 条评论</a-button>
        </div>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { formatRelativeTime } from '@/utils/date'
import { ref, computed, watch, h } from 'vue'
import { useRouter } from 'vue-router'
import { issueApi, projectApi } from '@/api'
import type { IssueDetailVO, IssueCommentVO, IssueStatusVO } from '@/api/types'
import { Message, Modal } from '@arco-design/web-vue'
import TimeProgressIndicator from '@/components/base/TimeProgressIndicator.vue'
import { renderMarkdown, renderHtmlWithMarkdown } from '@/utils/markdown'
import { getDueDateInfo } from '@/utils/dueDate'
import type { DueDateInfo } from '@/utils/dueDate'
import { IconShareExternal } from '@arco-design/web-vue/es/icon'
import { useNavBadge } from '@/composables/useNavBadge'
import { ERROR_CODES } from '@/api/error-codes'

const props = defineProps<{
  visible: boolean
  issueId: string | null
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  'go-detail': [issueId: string]
  'issue-updated': [issueId: string, changes: Record<string, any>]
}>()

const router = useRouter()

const loading = ref(false)
const loadError = ref<string | null>(null)
const detail = ref<IssueDetailVO | null>(null)
const comments = ref<IssueCommentVO[]>([])
const timeTrackingEnabled = ref(true)

// ===== Inline Edit State =====
const editingStatus = ref(false)
const loadingTransitions = ref(false)
const availableTransitions = ref<IssueStatusVO[]>([])

const editingPriority = ref(false)
const priorityOptions = [
  { value: '紧急', label: '紧急', icon: '🔴' },
  { value: '高', label: '高', icon: '🟠' },
  { value: '普通', label: '普通', icon: '🔵' },
  { value: '低', label: '低', icon: '⚪' }
]

const editingAssignee = ref(false)
const loadingMembers = ref(false)
const projectMembers = ref<Array<{ userId: string; displayName: string }>>([])
const assigneeSearch = ref('')

const filteredMembers = computed(() => {
  if (!assigneeSearch.value) return projectMembers.value
  const q = assigneeSearch.value.toLowerCase()
  return projectMembers.value.filter(m => m.displayName.toLowerCase().includes(q))
})

// ===== Status Edit =====
async function openStatusEdit() {
  if (!props.issueId) return
  editingStatus.value = true
  loadingTransitions.value = true
  try {
    const res = await issueApi.getAvailableTransitions(props.issueId)
    availableTransitions.value = res.data || []
  } catch {
    availableTransitions.value = []
  } finally {
    loadingTransitions.value = false
  }
}

function closeStatusEdit() {
  editingStatus.value = false
  availableTransitions.value = []
}

async function selectStatus(target: IssueStatusVO) {
  if (!props.issueId || !detail.value) return
  editingStatus.value = false

  if (target.requireComment) {
    // Show comment modal for transitions that require a reason
    let commentText = ''
    Modal.confirm({
      title: '状态变更 — 请填写理由',
      content: () => h('div', { style: 'display:flex;flex-direction:column;gap:8px' }, [
        h('div', { style: 'display:flex;align-items:center;gap:6px' }, [
          h('span', { style: 'color:var(--color-text-3);font-size:13px' }, '目标状态：'),
          h('span', { style: `background:${target.color};color: var(--tf-text-on-accent);padding:2px 8px;border-radius:3px;font-size:12px` }, target.name)
        ]),
        h('textarea', {
          placeholder: '请说明退回/变更的原因（必填）',
          style: 'width:100%;min-height:80px;margin-top:8px;padding:8px;border:1px solid var(--color-border-2);border-radius:4px;resize:vertical;font-size:13px;background:var(--color-bg-2);color:var(--color-text-1)',
          onInput: (e: Event) => { commentText = (e.target as HTMLTextAreaElement).value }
        })
      ]),
      okText: '确认变更',
      cancelText: '取消',
      width: 480,
      onBeforeOk: () => {
        if (!commentText.trim()) {
          Message.warning('请填写变更理由')
          return false
        }
        return true
      },
      onOk: async () => {
        await doTransitStatus(target, commentText.trim())
      }
    })
    return
  }

  await doTransitStatus(target, undefined)
}

async function doTransitStatus(target: IssueStatusVO, comment: string | undefined, forceFlags?: { force?: boolean; forceWip?: boolean; forceDescEmpty?: boolean }) {
  if (!props.issueId || !detail.value) return

  const oldStatusId = detail.value.statusId
  const oldStatus = detail.value.status
  // Optimistic update
  detail.value.status = { ...detail.value.status!, id: target.id, name: target.name, color: target.color }
  detail.value.statusId = target.id

  try {
    const res = await issueApi.transitStatus(props.issueId, target.id, comment, detail.value.version, forceFlags?.force, forceFlags?.forceWip, forceFlags?.forceDescEmpty)

    if (res.code === 0) {
      // 检查是否为字段校验失败（状态转换被阻止）
      const actionResult = res.data?.actionResult
      if (actionResult?.outcome === 'FIELD_VALIDATION_FAILED') {
        // 回滚乐观更新
        detail.value.statusId = oldStatusId
        detail.value.status = oldStatus
        // 显示警告消息
        Modal.warning({
          title: '字段校验',
          content: actionResult.warningMessage || `请先填写「${actionResult.requiredFieldName}」字段`,
          okText: '知道了'
        })
        return
      }

      // 成功：同步版本号
      detail.value.version = (detail.value.version || 0) + 1
      Message.success(`状态已变更为「${target.name}」`)
      emit('issue-updated', props.issueId, { statusId: target.id })
      useNavBadge().refresh() // 状态变更后刷新导航栏 badge
      return
    }

    // 回滚
    detail.value.statusId = oldStatusId
    detail.value.status = oldStatus

    if (res.code === ERROR_CODES.DESCRIPTION_EMPTY_WARNING) {
      // 描述为空警告
      Modal.warning({
        title: '工单描述为空',
        content: res.message,
        okText: '继续变更',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => doTransitStatus(target, comment, { ...forceFlags, forceDescEmpty: true })
      })
    } else if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
      // WIP 超限
      Modal.warning({
        title: 'WIP 限制',
        content: res.message,
        okText: '继续移入',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => doTransitStatus(target, comment, { ...forceFlags, forceWip: true })
      })
    } else if (res.code === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) {
      // 关闭确认
      Modal.warning({
        title: '确认关闭',
        content: res.message,
        okText: '强制关闭',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => doTransitStatus(target, comment, { ...forceFlags, force: true })
      })
    } else {
      Message.error(res.message || '状态变更失败')
    }
  } catch (e: any) {
    // Rollback
    detail.value.statusId = oldStatusId
    detail.value.status = oldStatus
    Message.error(e.response?.data?.message || '状态变更失败')
  }
}

// ===== Priority Edit =====
async function selectPriority(priority: string) {
  if (!props.issueId || !detail.value) return
  editingPriority.value = false

  if (detail.value.priority === priority) return

  const oldPriority = detail.value.priority
  detail.value.priority = priority

  try {
    await issueApi.update(props.issueId, { priority })
    Message.success(`优先级已变更为「${priority}」`)
    emit('issue-updated', props.issueId, { priority })
  } catch (e: any) {
    detail.value.priority = oldPriority
    Message.error(e.response?.data?.message || '优先级变更失败')
  }
}

// ===== Assignee Edit =====
async function openAssigneeEdit() {
  if (!detail.value?.projectId) return
  editingAssignee.value = true
  assigneeSearch.value = ''

  if (projectMembers.value.length === 0) {
    loadingMembers.value = true
    try {
      const res = await projectApi.listAssignableMembers(detail.value.projectId)
      projectMembers.value = (res.data || []).map(m => ({
        userId: m.userId,
        displayName: m.displayName
      }))
    } catch {
      projectMembers.value = []
    } finally {
      loadingMembers.value = false
    }
  }
}

function closeAssigneeEdit() {
  editingAssignee.value = false
  assigneeSearch.value = ''
}

async function selectAssignee(userId: string | null, displayName: string | null) {
  if (!props.issueId || !detail.value) return
  editingAssignee.value = false

  const oldAssigneeId = detail.value.assigneeId
  const oldAssigneeName = detail.value.assigneeName
  detail.value.assigneeId = userId || undefined
  detail.value.assigneeName = displayName || undefined

  try {
    // Backend expects 0 for unassign (normalizeAssigneeId converts 0 → null)
    await issueApi.assign(props.issueId, userId || '0')
    Message.success(userId ? `已分配给「${displayName}」` : '已取消分配')
    emit('issue-updated', props.issueId, { assigneeId: userId, assigneeName: displayName })
  } catch (e: any) {
    detail.value.assigneeId = oldAssigneeId
    detail.value.assigneeName = oldAssigneeName
    Message.error(e.response?.data?.message || '分配失败')
  }
}

const statusColor = computed(() => detail.value?.status?.color || 'var(--color-fill-4)')

const dueDateInfo = computed<DueDateInfo>(() => {
  if (!detail.value?.dueDate) return { status: 'normal', diffDays: Infinity, tooltip: '' }
  const isClosed = detail.value?.status?.isClosed === true
  return getDueDateInfo(detail.value.dueDate, isClosed)
})

const dueDateFieldClass = computed(() => {
  if (dueDateInfo.value.status === 'overdue') return 'field-value--overdue'
  if (dueDateInfo.value.status === 'due-soon') return 'field-value--due-soon'
  return ''
})

const renderedDescription = computed(() => {
  if (!detail.value?.description) return ''
  return renderMarkdown(detail.value.description)
})

const childProgressPercent = computed(() => {
  if (!detail.value?.childProgress) return 0
  const { total, closed } = detail.value.childProgress
  return total > 0 ? Math.round((closed / total) * 100) : 0
})

const displayedComments = computed(() => comments.value.slice(0, 3))

watch(() => props.issueId, (newId) => {
  if (newId && props.visible) {
    loadDetail()
  }
}, { immediate: true })

watch(() => props.visible, (v) => {
  if (v && props.issueId) {
    loadDetail()
  }
  if (!v) {
    // Reset state when closing
    detail.value = null
    comments.value = []
    loadError.value = null
    // Reset edit state
    editingStatus.value = false
    editingPriority.value = false
    editingAssignee.value = false
    projectMembers.value = []
  }
})

async function loadDetail() {
  if (!props.issueId) return
  loading.value = true
  loadError.value = null

  try {
    const [detailRes, commentsRes] = await Promise.all([
      issueApi.getById(props.issueId),
      issueApi.listComments(props.issueId)
    ])
    detail.value = detailRes.data
    comments.value = commentsRes.data || []

    // Load time tracking settings for the project
    if (detail.value?.projectId) {
      try {
        const ttRes = await projectApi.getTimeTrackingSettings(detail.value.projectId)
        timeTrackingEnabled.value = ttRes.code === 0 && ttRes.data ? ttRes.data.enabled : true
      } catch {
        timeTrackingEnabled.value = true
      }
    }
  } catch (e: any) {
    loadError.value = e.response?.data?.message || '加载工单详情失败'
  } finally {
    loading.value = false
  }
}

function close() {
  emit('update:visible', false)
}

function goToDetail() {
  if (props.issueId) {
    emit('update:visible', false)
    router.push({ name: 'IssueDetail', params: { id: props.issueId } })
  }
}

function priorityIcon(priority: string): string {
  const map: Record<string, string> = { Critical: '🔴', High: '🟠', Normal: '🔵', Low: '⚪' }
  return map[priority] || '🔵'
}

function typeLabel(type: string): string {
  return type
}



function renderCommentContent(content: string): string {
  if (!content) return ''
  const isHtml = content.trim().startsWith('<')
  return isHtml ? renderHtmlWithMarkdown(content) : renderMarkdown(content)
}
</script>

<style scoped>
.preview-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.preview-issue-key {
  font-size: 13px;
  font-weight: 600;
  color: var(--tf-accent);
  cursor: pointer;
  transition: opacity 0.15s;
}
.preview-issue-key:hover {
  opacity: 0.8;
  text-decoration: underline;
}

.preview-actions {
  display: flex;
  gap: 4px;
}

/* Loading */
.preview-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
}

/* Error */
.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 16px;
  text-align: center;
}
.preview-error-icon {
  font-size: 32px;
  margin-bottom: 12px;
}
.preview-error-msg {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 12px;
}

/* Content */
.preview-content {
  padding: 4px 0;
}

.preview-issue-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0 0 12px;
  line-height: 1.4;
}

/* Meta row */
.preview-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.preview-status-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 3px;
  line-height: 1.4;
}

.preview-status-badge--editable {
  cursor: pointer;
  transition: opacity 0.15s, box-shadow 0.15s;
}
.preview-status-badge--editable:hover {
  opacity: 0.85;
  box-shadow: 0 0 0 2px var(--tf-fill-medium);
}

.preview-priority {
  font-size: 12px;
  color: var(--color-text-2);
}

.preview-priority--editable {
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 3px;
  transition: background 0.15s;
}
.preview-priority--editable:hover {
  background: var(--color-fill-2);
}

.edit-chevron {
  font-size: 10px;
  opacity: 0.6;
  margin-left: 2px;
}

.preview-type {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 2px 6px;
  border-radius: 3px;
}

/* Fields */
.preview-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
  margin-bottom: 16px;
}

.preview-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-label {
  font-size: 11px;
  color: var(--color-text-3);
  font-weight: 500;
}

.field-value {
  font-size: 13px;
  color: var(--color-text-1);
  display: flex;
  align-items: center;
  gap: 4px;
}

.field-value--editable {
  cursor: pointer;
  padding: 2px 4px;
  margin: -2px -4px;
  border-radius: 3px;
  transition: background 0.15s;
}
.field-value--editable:hover {
  background: var(--color-fill-2);
}

.field-value--overdue {
  color: var(--tf-danger);
  font-weight: 500;
}

.field-value--due-soon {
  color: var(--tf-warning);
  font-weight: 500;
}

.field-value--over-budget {
  color: var(--tf-danger);
  font-weight: 500;
}

.due-date-hint {
  font-size: 11px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 3px;
}

.due-date-hint--overdue {
  color: var(--tf-danger);
  background: var(--tf-danger-bg);
}

.due-date-hint--due-soon {
  color: var(--tf-warning);
  background: var(--tf-warning-bg);
}

/* Tags */
.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}

.preview-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  border: 1px solid;
  font-weight: 500;
}

/* Sections */
.preview-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
  margin: 0 0 8px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.preview-description {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-1);
  max-height: 200px;
  overflow-y: auto;
  padding-right: 4px;
}

.preview-description :deep(p) {
  margin: 0 0 8px;
}
.preview-description :deep(ul),
.preview-description :deep(ol) {
  padding-left: 20px;
  margin: 0 0 8px;
}
.preview-description :deep(code) {
  font-size: 12px;
  background: var(--color-fill-2);
  padding: 1px 4px;
  border-radius: 3px;
}
.preview-description :deep(pre) {
  background: var(--color-fill-2);
  padding: 8px 12px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 0 0 8px;
}

.preview-empty-text {
  font-size: 13px;
  color: var(--color-text-4);
  font-style: italic;
}

/* Child progress */
.preview-child-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-bar-track {
  flex: 1;
  height: 6px;
  background: var(--color-fill-3);
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: rgb(var(--success-6));
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

/* Comments */
.preview-comments {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-comment {
  padding: 8px 10px;
  background: var(--color-fill-1);
  border-radius: 6px;
  border: 1px solid var(--color-border);
}

.comment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.comment-author {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-1);
}

.comment-time {
  font-size: 11px;
  color: var(--color-text-4);
}

.comment-body {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-2);
  max-height: 60px;
  overflow: hidden;
}

.comment-body :deep(p) {
  margin: 0;
}

.view-all-comments {
  align-self: flex-start;
  font-size: 12px;
  color: var(--tf-accent);
}

/* ===== Inline Edit Panel ===== */
/* 样式已提取到 src/styles/components.css — 不在此重复定义 */
</style>
