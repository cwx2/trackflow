<template>
  <section class="activity-stream">
    <div class="stream-toolbar">
      <h3 class="stream-title">活动</h3>
      <div class="filter-group">
        <button
          v-for="f in filters"
          :key="f.key"
          :class="['filter-btn', { active: current === f.key }]"
          @click="current = f.key"
        >{{ f.label }}</button>
      </div>
      <button v-if="showAddTime" class="add-time-btn" @click="emit('addTime')" title="记录工时">
        <span class="add-time-icon">⏱</span> 记录工时
      </button>
      <div class="settings-dropdown-wrap">
        <button class="settings-btn" @click="showSettings = !showSettings">
          活动的设置 ▾
        </button>
        <div class="settings-dropdown" v-if="showSettings" @mouseleave="showSettings = false">
          <div class="dropdown-item" :class="{ checked: ascending }" @click="ascending = true; showSettings = false">
            <span class="check-mark">{{ ascending ? '✓' : '' }}</span>
            排序: 旧→新
          </div>
          <div class="dropdown-item" :class="{ checked: !ascending }" @click="ascending = false; showSettings = false">
            <span class="check-mark">{{ !ascending ? '✓' : '' }}</span>
            排序: 新→旧
          </div>
          <div class="dropdown-divider"></div>
          <div class="dropdown-item" :class="{ checked: expandComments }" @click="expandComments = !expandComments">
            <span class="check-mark">{{ expandComments ? '✓' : '' }}</span>
            展开注释
          </div>
        </div>
      </div>
    </div>

    <div class="stream-list">
      <div v-for="item in sorted" :key="item.id" :id="item.id" class="stream-item" :class="{ 'stream-item--highlighted': highlightedId === item.id }" @mouseenter="hoveredId = item.id" @mouseleave="hoveredId = ''">
        <UserHoverCard :user-id="item.userId">
          <UserAvatar :name="item.user" :avatar="item.userAvatar" :size="28" />
        </UserHoverCard>
        <div class="item-body">
          <div class="item-head">
            <UserHoverCard :user-id="item.userId">
              <strong class="user-name-link">{{ item.user }}</strong>
            </UserHoverCard>
            <span class="item-time">{{ item.timeAgo }}</span>
            <span v-if="item.type === 'comment' && item.isEdited" class="edited-badge">已编辑</span>
            <span v-if="item.type === 'comment' && item.visibleToGroupNames && item.visibleToGroupNames.length > 0" class="visibility-badge" :title="'仅 ' + item.visibleToGroupNames.join(', ') + ' 可见'">
              🔒 {{ item.visibleToGroupNames.join(', ') }}
            </span>
            <!-- 自动化规则来源标签（仅对变更类活动显示） -->
            <span
              v-if="item.type === 'change' && item.detail && (item.detail.source === 'action_rule' || item.detail.source === 'automation')"
              class="automation-badge"
              :title="item.detail.ruleName ? '自动规则：' + item.detail.ruleName : '由自动化规则触发'"
            >⚡ {{ item.detail.ruleName || '自动规则' }}</span>
            <!-- Comment actions: reply + copy link (visible to all relevant users) -->
            <div v-if="item.type === 'comment' && !item.isDeleted && !hideCommentText(item) && editingCommentId !== item.commentId" class="comment-actions" :class="{ 'comment-actions--visible': hoveredId === item.id }">
              <button v-if="props.canComment" class="action-btn" title="回复" @click="emit('replyComment', item)"><icon-reply /></button>
              <button class="action-btn" title="复制评论链接" @click="handleCopyLink(item)"><icon-link /></button>
              <button v-if="canModifyComment(item)" class="action-btn" title="编辑评论" @click="startEdit(item)"><icon-edit /></button>
              <button v-if="canModifyComment(item)" class="action-btn action-btn-danger" title="删除评论" @click="confirmDelete(item)"><icon-delete /></button>
            </div>
          </div>
          <!-- Deleted comment placeholder -->
          <div v-if="item.type === 'comment' && item.isDeleted" class="deleted-comment-placeholder">
            <span class="deleted-text">评论已删除。</span>
            <button v-if="canModifyComment(item)" class="deleted-action-btn" @click="doRestore(item)">还原</button>
            <button v-if="props.canManageComments" class="deleted-action-btn deleted-action-btn-danger" @click="confirmPermanentDelete(item)">永久删除</button>
          </div>
          <!-- Editing mode -->
          <div v-else-if="item.type === 'comment' && !item.isDeleted && editingCommentId === item.commentId" class="comment-edit">
            <div class="edit-area">
              <EditorContent :editor="editEditor" />
            </div>
            <div class="edit-actions">
              <button class="btn-cancel" @click="cancelEdit">取消</button>
              <button class="btn-save" :disabled="editEmpty || editComposing" @click="saveEdit">保存修改</button>
            </div>
          </div>
          <!-- Normal display -->
          <template v-else>
            <!-- 评论 -->
            <div v-if="item.type === 'comment' && !item.isDeleted && !hideCommentText(item)" class="comment-text" :class="{ collapsed: !expandComments }" v-html="item.html" @click="handleCommentTextClick($event, item)"></div>
            <!-- 评论关联的字段变更块（YouTrack 风格：评论后 1 分钟内的变更合并展示） -->
            <div v-if="item.type === 'comment' && !item.isDeleted && item.relatedChanges && item.relatedChanges.length > 0" class="related-changes-block">
              <div v-for="(change, idx) in item.relatedChanges" :key="idx" class="related-change-row">
                <span class="rc-field">{{ change.field }}:</span>
                <span class="rc-old">{{ change.from || '未设置' }}</span>
                <span class="rc-arrow">→</span>
                <span class="rc-new">{{ change.to || '未设置' }}</span>
              </div>
            </div>
            <div v-else-if="item.type !== 'comment'" class="change-text">
              <template v-if="item.action === 'created'">创建了此工单</template>
              <template v-else-if="item.action === 'deleted'">删除了此工单</template>
              <template v-else-if="item.action === 'restored'">恢复了此工单</template>
              <template v-else-if="item.action === 'action_rule_executed'">
                <span class="system-event-badge">⚡</span> 规则「<span class="val-new">{{ item.to }}</span>」自动执行
              </template>
              <template v-else-if="item.action === 'time_logged'">
                <span class="time-badge">⏱</span>
                <template v-if="item.detail && item.detail.type === 'time_entry'">
                  记录了工时
                  <span class="time-entry-table">
                    <span class="te-cell te-duration">{{ formatDurationMin(item.detail.duration) }}</span>
                    <span v-if="item.detail.workDate" class="te-cell te-date">{{ formatWorkDate(item.detail.workDate) }}</span>
                    <span v-if="item.detail.workType" class="te-cell te-type">{{ item.detail.workType }}</span>
                    <span v-if="item.detail.description" class="te-cell te-desc">{{ item.detail.description }}</span>
                  </span>
                </template>
                <template v-else>
                  记录了工时: <span class="val-new">{{ item.to }}</span>
                </template>
              </template>
              <template v-else-if="item.action === 'time_removed'">
                <span class="time-badge">⏱</span>
                <template v-if="item.detail && item.detail.type === 'time_entry'">
                  删除了工时
                  <span class="time-entry-table">
                    <span class="te-cell te-duration te-old">{{ formatDurationMin(item.detail.duration) }}</span>
                    <span v-if="item.detail.workDate" class="te-cell te-date te-old">{{ formatWorkDate(item.detail.workDate) }}</span>
                    <span v-if="item.detail.workType" class="te-cell te-type te-old">{{ item.detail.workType }}</span>
                    <span v-if="item.detail.description" class="te-cell te-desc te-old">{{ item.detail.description }}</span>
                  </span>
                </template>
                <template v-else>
                  删除了工时: <span class="val-old">{{ item.from }}</span>
                </template>
              </template>
              <template v-else-if="item.action === 'time_updated'">
                <span class="time-badge">⏱</span>
                <template v-if="item.detail && item.detail.type === 'time_entry'">
                  修改了工时
                  <span class="time-entry-table">
                    <span class="te-cell te-duration">{{ formatDurationMin(item.detail.duration) }}</span>
                    <span v-if="item.detail.workDate" class="te-cell te-date">{{ formatWorkDate(item.detail.workDate) }}</span>
                    <span v-if="item.detail.workType" class="te-cell te-type">{{ item.detail.workType }}</span>
                    <span v-if="item.detail.description" class="te-cell te-desc">{{ item.detail.description }}</span>
                  </span>
                  <span class="te-changes">{{ item.to }}</span>
                </template>
                <template v-else>
                  修改了工时: <span class="val-new">{{ item.to }}</span>
                </template>
              </template>
              <template v-else-if="item.action === 'attachment_added'">
                添加了附件: <span class="val-new">{{ item.to }}</span>
              </template>
              <template v-else-if="item.action === 'attachment_removed'">
                删除了附件: <span class="val-old">{{ item.from }}</span>
              </template>
              <template v-else-if="item.action === 'moved_to_project'">
                移动到项目：<span class="val-old">{{ item.from || '未知' }}</span> → <span class="val-new">{{ item.to || '未知' }}</span>
              </template>
              <template v-else-if="item.action === 'link_added'">
                添加了关联: <span class="val-new">{{ localizeLinkValue(item.to) }}</span>
              </template>
              <template v-else-if="item.action === 'link_removed'">
                移除了关联: <span class="val-old">{{ localizeLinkValue(item.from) }}</span>
              </template>
              <template v-else-if="item.action === 'auto_assign_skipped'">
                <span class="system-event-badge">⚙</span> {{ formatAutoAssignSkipped(item.detail) }}
              </template>
              <template v-else-if="item.action === 'comment_deleted'">
                删除了评论<template v-if="item.from">: <span class="val-old comment-deleted-content">{{ item.from }}</span></template>
              </template>
              <template v-else-if="item.action === 'comment_restored'">
                还原了评论
              </template>
              <template v-else-if="item.action === 'comment_permanently_deleted'">
                永久删除了评论
              </template>
              <template v-else-if="item.action === 'status_reverted'">
                撤销了状态变更：<span class="val-old">{{ item.from || '未设置' }}</span> → <span class="val-new">{{ item.to || '未设置' }}</span>
              </template>
              <template v-else-if="item.field">
                修改了{{ item.field }}：<span class="val-old">{{ item.from || '未设置' }}</span> → <span class="val-new">{{ item.to || '未设置' }}</span>
                <span v-if="item.detail && item.detail.reason === 'member_removed'" class="auto-reason">（成员已从项目移除）</span>
                <span v-else-if="item.detail && item.detail.reason === 'manual_override'" class="auto-reason">（手动设置）</span>
              </template>
              <template v-else>{{ localizeAction(item.action) }}</template>
            </div>
          </template>
        </div>
      </div>
      <p v-if="sorted.length === 0" class="empty">暂无活动</p>
    </div>

    <!-- Delete confirmation modal -->
    <a-modal
      v-model:visible="deleteModalVisible"
      title="删除评论"
      :ok-text="'删除评论'"
      :cancel-text="'取消'"
      :ok-button-props="{ status: 'danger' }"
      @ok="doDelete"
      @cancel="deleteModalVisible = false"
    >
      <p>确定要删除这条评论吗？删除后可通过"还原"恢复。</p>
    </a-modal>

    <!-- Permanent delete confirmation modal -->
    <a-modal
      v-model:visible="permanentDeleteModalVisible"
      title="永久删除评论"
      :ok-text="'永久删除'"
      :cancel-text="'取消'"
      :ok-button-props="{ status: 'danger' }"
      @ok="doPermanentDelete"
      @cancel="permanentDeleteModalVisible = false"
    >
      <p>确定要永久删除这条评论吗？此操作不可恢复。</p>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { localizeAction, localizeLinkType } from '@/utils/fieldLabels'
import { ReplyBlockquote } from '../extensions/ReplyBlockquote'
import UserHoverCard from './UserHoverCard.vue'
import { UserAvatar } from '@/components/base'

/** 评论关联的字段变更（1分钟内的变更合并到评论条目展示） */
export interface RelatedChange {
  field: string
  from?: string
  to?: string
}

export interface ActivityItem {
  id: string
  type: 'comment' | 'change'
  user: string
  userId?: string
  userAvatar?: string
  commentId?: string
  isEdited?: boolean
  isDeleted?: boolean
  rawContent?: string
  visibleToGroupNames?: string[]
  timeAgo: string
  html?: string
  action?: string
  field?: string
  from?: string
  to?: string
  ts: number
  /**
   * 操作元数据（已解析的对象），存储变更来源信息。
   * 示例：{ source: 'action_rule' } | { source: 'automation', ruleName: '...' } | { reason: 'member_removed' }
   */
  detail?: Record<string, any>
  /**
   * 评论关联的字段变更列表（评论创建后 1 分钟内的字段变更）。
   * 仅对 type === 'comment' 的条目有效。
   */
  relatedChanges?: RelatedChange[]
}

const props = defineProps<{
  items: ActivityItem[]
  currentUserId?: string
  canManageComments?: boolean
  canComment?: boolean
  showAddTime?: boolean
  hasMore?: boolean
  loadingMore?: boolean
  totalActivities?: number
  loadedActivities?: number
}>()

const emit = defineEmits<{
  editComment: [commentId: string, content: string]
  deleteComment: [commentId: string]
  restoreComment: [commentId: string]
  permanentlyDeleteComment: [commentId: string]
  replyComment: [item: ActivityItem]
  addTime: []
}>()

const filters = [
  { key: 'all', label: '全部' },
  { key: 'comments', label: '评论' },
  { key: 'time', label: '花费时间' },
  { key: 'changes', label: '变更' }
]
const current = ref('all')
const ascending = ref(true)
const showSettings = ref(false)
const expandComments = ref(true)
const hoveredId = ref('')

// Edit state
const editingCommentId = ref<string | null>(null)
// 追踪编辑框 IME 组合输入状态
const editComposing = ref(false)

const editEditor = useEditor({
  content: '',
  extensions: [
    StarterKit.configure({
      blockquote: false, // 使用自定义 ReplyBlockquote 替代
    }),
    ReplyBlockquote,
    Link.configure({ openOnClick: false }),
    Placeholder.configure({ placeholder: '编辑评论...' }),
  ],
  editorProps: {
    attributes: { class: 'tiptap-comment' },
    handleDOMEvents: {
      compositionstart: () => { editComposing.value = true; return false },
      compositionend: () => { editComposing.value = false; return false },
    }
  },
  onBlur: () => { editComposing.value = false },
})

const editEmpty = computed(() => !editEditor.value || editEditor.value.isEmpty)

// Delete state
const deleteModalVisible = ref(false)
const deletingCommentId = ref<string | null>(null)

const filtered = computed(() => {
  if (current.value === 'comments') return props.items.filter(i => i.type === 'comment')
  if (current.value === 'time') return props.items.filter(i => i.action === 'time_logged' || i.action === 'time_removed' || i.action === 'time_updated')
  if (current.value === 'changes') {
    // Show standalone change items + comments that have relatedChanges (to display their merged changes)
    return props.items.filter(i =>
      (i.type === 'change' && i.action !== 'time_logged' && i.action !== 'time_removed' && i.action !== 'time_updated') ||
      (i.type === 'comment' && i.relatedChanges && i.relatedChanges.length > 0)
    )
  }
  return props.items
})

/**
 * Whether to hide the comment text for an item (used when filter is "changes" and
 * we're only showing the relatedChanges block).
 */
function hideCommentText(item: ActivityItem): boolean {
  return current.value === 'changes' && item.type === 'comment'
}

const sorted = computed(() => {
  const arr = [...filtered.value]
  arr.sort((a, b) => ascending.value ? a.ts - b.ts : b.ts - a.ts)
  return arr
})

// Track filtered count before load-more to detect "loaded but nothing visible"
const filteredCountBeforeLoad = ref(0)

watch(() => props.loadingMore, (newVal, oldVal) => {
  if (newVal && !oldVal) {
    // Loading started: capture current filtered count
    filteredCountBeforeLoad.value = filtered.value.length
  }
  if (!newVal && oldVal) {
    // Loading finished: check if filtered count increased (use nextTick to ensure items are updated)
    nextTick(() => {
      if (current.value !== 'all' && filtered.value.length === filteredCountBeforeLoad.value && props.hasMore) {
        const filterLabel = filters.find(f => f.key === current.value)?.label || current.value
        Message.info({ content: `已加载新的活动记录，当前「${filterLabel}」筛选下暂无新内容，可切换筛选查看`, duration: 4000 })
      }
    })
  }
})

/**
 * 本地化关联活动的值（格式："{linkType} {issueKey}"）
 * 例如："relates_to DE4-1060" → "相关 DE4-1060"
 */
function localizeLinkValue(value?: string): string {
  if (!value) return ''
  const parts = value.split(' ')
  if (parts.length >= 2) {
    const linkType = parts[0]
    const issueKey = parts.slice(1).join(' ')
    return `${localizeLinkType(linkType)} ${issueKey}`
  }
  return value
}

function canModifyComment(item: ActivityItem): boolean {
  if (!props.currentUserId) return false
  // Author can always edit/delete their own comment
  if (item.userId === props.currentUserId) return true
  // Users with manage_comments permission can edit/delete anyone's
  return !!props.canManageComments
}

function startEdit(item: ActivityItem) {
  editingCommentId.value = item.commentId || null
  nextTick(() => {
    if (editEditor.value && item.rawContent) {
      // If rawContent starts with '<', it's HTML; otherwise set as paragraph
      const isHtml = item.rawContent.trim().startsWith('<')
      if (isHtml) {
        editEditor.value.commands.setContent(item.rawContent)
      } else {
        editEditor.value.commands.setContent(`<p>${item.rawContent}</p>`)
      }
      editEditor.value.commands.focus('end')
    }
  })
}

function cancelEdit() {
  editingCommentId.value = null
  editEditor.value?.commands.clearContent()
}

function saveEdit() {
  if (!editingCommentId.value || !editEditor.value || editEditor.value.isEmpty) return
  // 防止在 IME 输入法组合状态下保存，避免末尾内容丢失
  if (editComposing.value || editEditor.value.view.composing) return
  const html = editEditor.value.getHTML()
  emit('editComment', editingCommentId.value, html)
  editingCommentId.value = null
  editEditor.value.commands.clearContent()
}

function confirmDelete(item: ActivityItem) {
  deletingCommentId.value = item.commentId || null
  deleteModalVisible.value = true
}

function doDelete() {
  if (deletingCommentId.value) {
    emit('deleteComment', deletingCommentId.value)
  }
  deleteModalVisible.value = false
  deletingCommentId.value = null
}

function doRestore(item: ActivityItem) {
  if (item.commentId) {
    emit('restoreComment', item.commentId)
  }
}

// Permanent delete confirmation
const permanentDeleteModalVisible = ref(false)
const permanentDeletingCommentId = ref<string | null>(null)

function confirmPermanentDelete(item: ActivityItem) {
  permanentDeletingCommentId.value = item.commentId || null
  permanentDeleteModalVisible.value = true
}

function doPermanentDelete() {
  if (permanentDeletingCommentId.value) {
    emit('permanentlyDeleteComment', permanentDeletingCommentId.value)
  }
  permanentDeleteModalVisible.value = false
  permanentDeletingCommentId.value = null
}


/** 将分钟数格式化为 "Xh Ym" 或 "Xm" */
function formatDurationMin(minutes?: number): string {
  if (minutes == null || isNaN(minutes)) return ''
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

/** 将 ISO 日期字符串（"2026-07-20"）格式化为中文日期形式（"2026/07/20"） */
function formatWorkDate(dateStr?: string): string {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr + 'T00:00:00')
    return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  } catch {
    return dateStr
  }
}

/**
 * 将 auto_assign_skipped 的 detail 对象格式化为用户可读的中文描述。
 * 根据 reason 字段展示不同解释文案。
 */
function formatAutoAssignSkipped(detail?: Record<string, any>): string {
  if (!detail || !detail.reason) return '自动分配已跳过'
  switch (detail.reason) {
    case 'manual_override':
      return '自动分配已跳过 — 负责人由用户手动指定'
    case 'existing_assignee_matches_role':
      return '自动分配已跳过 — 当前负责人已属于目标角色，无需变更'
    default:
      return `自动分配已跳过 — 原因：${detail.reason}`
  }
}

/**
 * 复制评论的永久链接到剪贴板。
 * 链接格式：{当前页面URL}#{commentDomId}
 */
function handleCopyLink(item: ActivityItem) {
  const url = `${window.location.origin}${window.location.pathname}#${item.id}`
  navigator.clipboard.writeText(url).then(() => {
    Message.success({ content: '链接已复制', duration: 2000 })
  }).catch(() => {
    Message.error('复制链接失败')
  })
}

/** 当前正在高亮的评论 DOM ID */
const highlightedId = ref('')

/**
 * 处理评论文本区域的点击事件（事件委托）。
 * 检测点击目标是否在引用块内，如果是且有 data-reply-to-comment-id 属性，则滚动到原始评论。
 */
function handleCommentTextClick(event: MouseEvent, _item: ActivityItem) {
  const target = event.target as HTMLElement
  // 向上查找最近的 blockquote 祖先（在 comment-text 范围内）
  const blockquote = target.closest('blockquote')
  if (!blockquote) return

  const commentId = blockquote.getAttribute('data-reply-to-comment-id')
  if (!commentId) {
    // 如果没有 data 属性（旧评论），尝试按 @username + 内容匹配
    handleQuoteFallbackClick(blockquote)
    return
  }

  scrollToComment(commentId)
}

/**
 * 旧评论回复引用（没有 data-reply-to-comment-id 属性）的回退匹配逻辑：
 * 解析引用块中的 @用户名 和内容，在已加载的评论列表中搜索匹配。
 */
function handleQuoteFallbackClick(blockquote: Element) {
  const text = blockquote.textContent || ''
  // 格式：@displayName：content...
  const match = text.match(/^@(.+?)：(.+)/)
  if (!match) return

  const quotedUser = match[1].trim()
  const quotedContent = match[2].trim().replace(/\.{3}$/, '') // 移除截断的省略号

  // 在已加载的活动项中搜索匹配的评论
  const found = props.items.find(item => {
    if (item.type !== 'comment' || !item.html) return false
    if (item.user !== quotedUser) return false
    // 从 HTML 中提取纯文本进行前缀匹配
    const tempDiv = document.createElement('div')
    tempDiv.innerHTML = item.html
    const plainText = (tempDiv.textContent || '').trim()
    return plainText.startsWith(quotedContent.slice(0, 30)) // 前 30 字匹配
  })

  if (found) {
    scrollToComment(found.commentId || found.id.replace('c_', ''))
  } else {
    Message.info({ content: '原始评论可能需要加载更多才能查看', duration: 3000 })
  }
}

/**
 * 平滑滚动到指定评论并短暂高亮。
 * @param commentId 评论 ID（不含 c_ 前缀）
 */
function scrollToComment(commentId: string) {
  const domId = 'c_' + commentId
  const el = document.getElementById(domId)
  if (!el) {
    // 评论不在当前已加载的 DOM 中（可能在"加载更多"之前）
    Message.info({ content: '原始评论需要加载更多后才能查看', duration: 3000 })
    return
  }

  // 平滑滚动到评论位置
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })

  // 短暂高亮
  highlightedId.value = domId
  setTimeout(() => {
    highlightedId.value = ''
  }, 2000)
}

onBeforeUnmount(() => { editEditor.value?.destroy() })

// Expose current filter state for parent "load more" fixed bar
const currentFilterLabel = computed(() => filters.find(f => f.key === current.value)?.label ?? '全部')
defineExpose({
  currentFilter: current,
  currentFilterLabel,
})
</script>

<style scoped>
.activity-stream {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--tf-border-light);
}

.stream-toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.stream-title { font-size: 11px; font-weight: 700; color: var(--tf-text-tertiary); margin: 0; text-transform: uppercase; letter-spacing: 0.5px; }
.filter-group { display: flex; gap: 4px; }
.filter-btn {
  font-size: 11px; padding: 2px 8px; border-radius: 3px;
  background: none; border: 1px solid transparent;
  color: var(--tf-text-tertiary); cursor: pointer;
  transition: color 150ms, background 150ms, border-color 150ms;
}
.filter-btn:hover { color: var(--tf-text-primary); }
.filter-btn.active { background: var(--tf-bg-elevated); color: var(--tf-text-primary); border-color: var(--tf-border); }

.add-time-btn {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 11px; padding: 3px 10px; border-radius: 3px;
  background: none; border: 1px solid var(--tf-accent);
  color: var(--tf-accent); cursor: pointer;
  transition: background 150ms, color 150ms;
  font-weight: 500; white-space: nowrap;
}
.add-time-btn:hover { background: var(--tf-accent); color: #fff; }
.add-time-btn .add-time-icon { font-size: 12px; }

.settings-dropdown-wrap { margin-left: auto; position: relative; }
.settings-btn {
  font-size: 11px; background: none; border: none;
  color: var(--tf-accent); cursor: pointer;
  transition: opacity 150ms;
}
.settings-btn:hover { opacity: 0.8; }

.settings-dropdown {
  position: absolute; top: 100%; right: 0; z-index: 20;
  margin-top: 4px; min-width: 160px;
  background: var(--tf-bg-elevated); border: 1px solid var(--tf-border);
  border-radius: 6px; padding: 4px 0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}
.dropdown-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; font-size: 13px;
  color: var(--tf-text-primary); cursor: pointer;
  transition: background 150ms;
}
.dropdown-item:hover { background: var(--tf-bg-hover); }
.dropdown-item.checked { font-weight: 500; }
.check-mark { width: 16px; font-size: 12px; color: var(--tf-accent); }
.dropdown-divider { height: 1px; background: var(--tf-border-light); margin: 4px 0; }

.stream-item { display: flex; gap: 8px; padding: 12px 8px; margin: 0 -8px; border-radius: 6px; transition: background 0.2s; }
.stream-item:hover { background: var(--tf-bg-hover); }
.stream-item + .stream-item { border-top: 1px solid var(--tf-border-light); }
/* 点击引用跳转后的高亮闪烁效果 */
.stream-item--highlighted {
  animation: highlight-flash 2s ease-out;
}
@keyframes highlight-flash {
  0% { background: var(--tf-accent-subtle, rgba(88, 166, 255, 0.2)); }
  50% { background: var(--tf-accent-subtle, rgba(88, 166, 255, 0.15)); }
  100% { background: transparent; }
}
.item-body { flex: 1; min-width: 0; }
.item-head { display: flex; align-items: baseline; gap: 8px; }
.item-head strong { font-size: 12px; color: var(--tf-text-primary); font-weight: 600; }
.user-name-link {
  font-size: 12px; color: var(--tf-text-primary); font-weight: 600;
  transition: color 150ms;
  cursor: pointer;
}
.user-name-link:hover { color: var(--tf-accent); }
.item-time { font-size: 11px; color: var(--tf-text-muted); }

.edited-badge {
  font-size: 10px;
  color: var(--tf-text-muted);
  font-style: italic;
}

.automation-badge {
  font-size: 10px;
  color: var(--tf-warning, #d29922);
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(210, 153, 34, 0.12);
  font-weight: 500;
  white-space: nowrap;
  cursor: default;
}

.auto-reason {
  font-size: 11px;
  color: var(--tf-text-muted);
  font-style: italic;
  margin-left: 4px;
}

.visibility-badge {
  font-size: 10px;
  color: var(--tf-warning, #d29922);
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(210, 153, 34, 0.1);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.comment-actions {
  display: flex; gap: 4px; margin-left: auto;
  opacity: 0; pointer-events: none;
  transition: opacity 150ms;
}
.comment-actions--visible {
  opacity: 1; pointer-events: auto;
}
.action-btn {
  font-size: 14px; padding: 2px 6px; border-radius: 3px;
  background: none; border: none; color: var(--tf-text-tertiary);
  cursor: pointer; transition: color 150ms, background 150ms;
  display: inline-flex; align-items: center; justify-content: center;
}
.action-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-hover); }
.action-btn-danger:hover { color: var(--tf-error); background: rgba(248, 81, 73, 0.1); }

.comment-text { font-size: 13px; color: var(--tf-text-secondary); line-height: 1.5; margin-top: 4px; }
.comment-text.collapsed { max-height: 60px; overflow: hidden; position: relative; }
.comment-text.collapsed::after {
  content: '';
  position: absolute; bottom: 0; left: 0; right: 0; height: 24px;
  background: linear-gradient(transparent, var(--tf-bg-body));
  pointer-events: none;
}
.comment-text :deep(p) { margin: 4px 0; }
.comment-text :deep(code) { background: var(--tf-bg-code); padding: 0 3px; border-radius: 2px; font-size: 11px; }
/* Reply quote blockquote in rendered comments - clickable */
.comment-text :deep(blockquote) {
  border-left: 3px solid var(--tf-accent);
  margin: 4px 0 8px;
  padding: 6px 12px;
  color: var(--tf-text-tertiary);
  font-size: 12px;
  background: var(--tf-bg-surface);
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  transition: background 150ms, border-color 150ms;
}
.comment-text :deep(blockquote:hover) {
  background: var(--tf-bg-hover);
  border-color: var(--tf-accent-hover, var(--tf-accent));
}

/* Related changes block (YouTrack style: field changes within 1 minute of a comment) */
.related-changes-block {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--tf-bg-surface);
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
}
.related-change-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tf-text-tertiary);
}
.related-change-row + .related-change-row {
  margin-top: 2px;
}
.rc-field {
  color: var(--tf-text-secondary);
  font-weight: 500;
  white-space: nowrap;
}
.rc-old {
  text-decoration: line-through;
  color: var(--tf-text-muted);
}
.rc-arrow {
  color: var(--tf-text-muted);
  flex-shrink: 0;
}
.rc-new {
  color: var(--tf-accent);
  font-weight: 500;
}

/* Deleted comment placeholder */
.deleted-comment-placeholder {
  margin-top: 4px;
  padding: 8px 12px;
  background: var(--tf-bg-surface);
  border-radius: 6px;
  border: 1px dashed var(--tf-border);
  font-size: 13px;
  color: var(--tf-text-tertiary);
  display: flex;
  align-items: center;
  gap: 12px;
}
.deleted-text {
  font-style: italic;
}
.deleted-action-btn {
  background: none;
  border: none;
  font-size: 12px;
  color: var(--tf-text-accent);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 3px;
  transition: background 150ms;
}
.deleted-action-btn:hover {
  background: var(--tf-bg-hover);
}
.deleted-action-btn-danger {
  color: var(--tf-text-error, #f85149);
}
.deleted-action-btn-danger:hover {
  background: rgba(248, 81, 73, 0.1);
}

/* Edit mode */
.comment-edit {
  margin-top: 8px;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--tf-bg-body);
}
.edit-area {
  min-height: 60px; max-height: 200px; overflow-y: auto; padding: 12px;
}
.edit-area :deep(.tiptap-comment) {
  outline: none; font-size: 13px; line-height: 1.5; color: var(--tf-text-primary);
}
.edit-area :deep(.tiptap-comment p) { margin: 4px 0; }
.edit-area :deep(.tiptap-comment code) { background: var(--tf-bg-code); padding: 0 3px; border-radius: 2px; font-size: 12px; }
.edit-area :deep(.tiptap-comment .is-empty::before) {
  content: attr(data-placeholder); color: var(--tf-text-muted);
  pointer-events: none; float: left; height: 0;
}
.edit-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  padding: 8px; border-top: 1px solid var(--tf-border);
  background: var(--tf-bg-elevated);
}
.btn-cancel {
  font-size: 12px; padding: 4px 12px; border-radius: 3px; border: none;
  background: none; color: var(--tf-text-tertiary); cursor: pointer;
  transition: color 150ms;
}
.btn-cancel:hover { color: var(--tf-text-primary); }
.btn-save {
  font-size: 12px; padding: 4px 16px; border-radius: 3px; border: none;
  background: var(--tf-accent); color: #fff; font-weight: 500; cursor: pointer;
  transition: background 150ms;
}
.btn-save:disabled { opacity: 0.35; cursor: default; }
.btn-save:hover:not(:disabled) { background: var(--tf-accent-hover); }

.change-text { font-size: 12px; color: var(--tf-text-tertiary); margin-top: 4px; }
.val-old { text-decoration: line-through; color: var(--tf-text-muted); }
.val-new { color: var(--tf-accent); font-weight: 500; }
.time-badge { font-size: 13px; }
.system-event-badge { font-size: 13px; }

/* Structured time entry display (YouTrack style) */
.time-entry-table {
  display: inline-flex;
  gap: 0;
  align-items: center;
  margin-left: 6px;
  border: 1px solid var(--tf-border);
  border-radius: 4px;
  overflow: hidden;
  vertical-align: middle;
}
.te-cell {
  padding: 2px 8px;
  font-size: 12px;
  border-right: 1px solid var(--tf-border);
  color: var(--tf-text-secondary);
  background: var(--tf-bg-elevated);
  white-space: nowrap;
}
.te-cell:last-child { border-right: none; }
.te-duration { font-weight: 600; color: var(--tf-accent); min-width: 36px; text-align: center; }
.te-date { color: var(--tf-text-tertiary); }
.te-type { color: var(--tf-text-secondary); }
.te-desc { color: var(--tf-text-muted); max-width: 200px; overflow: hidden; text-overflow: ellipsis; }
/* Removed/deleted time entry cells */
.te-old { text-decoration: line-through; opacity: 0.6; }
.te-old.te-duration { color: var(--tf-text-muted); }
/* Changes description for time_updated */
.te-changes {
  margin-left: 6px;
  font-size: 11px;
  color: var(--tf-text-muted);
  font-style: italic;
}

.empty { color: var(--tf-text-muted); font-size: 12px; text-align: center; padding: 24px 0; }
</style>
