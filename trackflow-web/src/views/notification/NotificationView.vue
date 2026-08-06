<template>
  <div class="notification-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <!-- 第一行：标题 + 操作按钮 -->
      <div class="header-row1">
        <div class="header-left">
          <h1 class="page-title">通知中心</h1>
          <span v-if="unreadCount > 0" class="unread-badge">{{ unreadCount }}</span>
        </div>
        <div class="header-actions">
          <button
            class="action-link"
            :class="{ active: unreadOnly }"
            @click="handleToggleUnread"
          >仅未读</button>
          <span class="action-divider">·</span>
          <button
            class="action-link"
            :disabled="unreadCount === 0"
            @click="handleMarkAllRead"
          >全部已读</button>
          <span class="action-divider">·</span>
          <button
            class="action-link danger"
            :disabled="!hasRead"
            @click="handleDeleteAllRead"
          >清除已读</button>
        </div>
      </div>
      <!-- 第二行：项目筛选 -->
      <div class="header-row2">
        <a-select
          v-model="selectedProjectId"
          placeholder="全部项目"
          allow-clear
          size="small"
          class="project-filter-select"
          @change="handleProjectChange"
          @clear="handleProjectClear"
        >
          <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
        </a-select>
      </div>
    </div>

    <!-- 分类标签页 -->
    <div class="page-tabs">
      <button
        v-for="tab in visibleTabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeCategory === tab.key }"
        @click="handleCategoryChange(tab.key)"
      >
        <span class="tab-label">{{ tab.label }}</span>
        <span v-if="getCategoryCount(tab.key) > 0" class="tab-badge">
          {{ getCategoryCount(tab.key) }}
        </span>
      </button>
    </div>

    <!-- 内容区 -->
    <div class="page-body">
      <!-- 加载状态 -->
      <div v-if="loading && notifications.length === 0" class="page-loading">
        <div v-for="i in 8" :key="i" class="loading-skeleton">
          <div class="skeleton-indicator"></div>
          <div class="skeleton-icon"></div>
          <div class="skeleton-content">
            <div class="skeleton-line short"></div>
            <div class="skeleton-line long"></div>
            <div class="skeleton-line medium"></div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else-if="notifications.length === 0" class="page-empty">
        <div class="empty-icon">{{ getEmptyIcon() }}</div>
        <div class="empty-title">{{ getEmptyTitle() }}</div>
        <div class="empty-desc">{{ getEmptyDesc() }}</div>
      </div>

      <!-- 通知列表 -->
      <div v-else class="notification-list">
        <div
          v-for="item in notifications"
          :key="item.id"
          class="notification-item-wrapper"
        >
          <div
            class="notification-item"
            :class="{ unread: !item.isRead }"
            @click="handleItemClick(item)"
          >
            <div class="item-indicator">
              <span v-if="!item.isRead" class="unread-dot"></span>
          </div>
          <div class="item-icon" :class="{ 'has-avatar': item.actorAvatar }">
            <img v-if="item.actorAvatar" :src="item.actorAvatar" :alt="item.actorName" class="actor-avatar" />
            <span v-else-if="item.actorName" class="actor-initial">{{ item.actorName.charAt(0) }}</span>
            <span v-else class="actor-system" title="系统操作">
              <icon-common :size="14" />
            </span>
          </div>
          <div class="item-content">
            <div class="item-title">
              {{ item.title }}
              <span v-if="item.aggregationCount && item.aggregationCount > 1" class="aggregation-badge">
                {{ item.aggregationCount }}次变更
              </span>
            </div>
            <div class="item-body">{{ item.content }}</div>
            <div class="item-meta">
              <span v-if="item.reasonLabel" class="item-reason">{{ item.reasonLabel }}</span>
              <span class="item-time">{{ formatTime(item.updatedAt || item.createdAt) }}</span>
              <span v-if="item.resourceUrl || (item.resourceType === 'issue' && item.resourceId)" class="item-link">
                {{ item.resourceType === 'sprint' ? '查看 Sprint →' : '查看工单 →' }}
              </span>
            </div>
          </div>
          <div class="item-actions">
            <button
              v-if="!item.isRead"
              class="item-action-btn"
              title="标记已读"
              @click.stop="handleMarkRead(item.id)"
            >
              <icon-check :size="14" />
            </button>
            <button
              v-else
              class="item-action-btn"
              title="标记未读"
              @click.stop="handleMarkUnread(item.id)"
            >
              <icon-record :size="14" />
            </button>
            <button
              v-if="canReply(item)"
              class="item-action-btn item-reply-btn"
              title="回复"
              @click.stop="toggleReply(item.id)"
            >
              <icon-reply :size="14" />
            </button>
            <button
              class="item-action-btn item-delete-btn"
              title="删除通知"
              @click.stop="handleDelete(item.id)"
            >
              <icon-close :size="14" />
            </button>
          </div>
        </div>
        <!-- 内联回复编辑器 -->
        <div v-if="replyingItemId === item.id" class="reply-editor" @click.stop>
          <textarea
            v-model="replyContent"
            class="reply-textarea"
            placeholder="输入回复内容..."
            rows="3"
            :disabled="replySubmitting"
            @keydown.meta.enter="submitReply(item)"
            @keydown.ctrl.enter="submitReply(item)"
          ></textarea>
          <div class="reply-actions">
            <span class="reply-hint">Ctrl+Enter 发送</span>
            <div class="reply-btns">
              <button class="reply-cancel-btn" :disabled="replySubmitting" @click="cancelReply">取消</button>
              <button
                class="reply-submit-btn"
                :disabled="!replyContent.trim() || replySubmitting"
                @click="submitReply(item)"
              >
                {{ replySubmitting ? '发送中...' : '发送回复' }}
              </button>
            </div>
          </div>
        </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="totalCount > pageSize" class="page-pagination">
        <button
          class="pagination-btn"
          :disabled="currentPage <= 1"
          @click="changePage(currentPage - 1)"
        >
          ← 上一页
        </button>
        <span class="pagination-info">
          第 {{ currentPage }} / {{ totalPages }} 页
        </span>
        <button
          class="pagination-btn"
          :disabled="currentPage >= totalPages"
          @click="changePage(currentPage + 1)"
        >
          下一页 →
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotification } from '@/composables/useNotification'
import { projectApi, issueApi } from '@/api'
import type { NotificationVO, NotificationCategory } from '@/api/notification'
import { Message } from '@arco-design/web-vue'

const router = useRouter()
const {
  notifications,
  loading,
  unreadOnly,
  unreadCount,
  totalCount,
  hasRead,
  activeCategory,
  activeProjectId,
  categoryUnreadCounts,
  isSystemAdmin,
  toggleUnreadOnly,
  setCategory,
  setProjectFilter,
  markRead,
  markUnread,
  markAllRead,
  deleteNotification,
  deleteAllRead,
  fetchNotifications,
  fetchCategoryUnreadCounts,
  panelVisible
} = useNotification()

const pageSize = 50
const currentPage = ref(1)

const totalPages = computed(() => Math.ceil(totalCount.value / pageSize))

/** 用户所属项目列表（用于过滤下拉） */
const projects = ref<{ id: string; name: string }[]>([])
const selectedProjectId = ref<string | undefined>(activeProjectId.value || undefined)

/** 内联回复状态 */
const replyingItemId = ref<string | null>(null)
const replyContent = ref('')
const replySubmitting = ref(false)

/** 是否可以回复（评论/提及类通知且有关联工单） */
function canReply(item: NotificationVO): boolean {
  return (item.type === 'issue_commented' || item.type === 'mention') &&
    item.resourceType === 'issue' && !!item.resourceId
}

/** 展开/收起回复编辑器 */
function toggleReply(itemId: string) {
  if (replyingItemId.value === itemId) {
    replyingItemId.value = null
    replyContent.value = ''
  } else {
    replyingItemId.value = itemId
    replyContent.value = ''
  }
}

/** 提交回复 */
async function submitReply(item: NotificationVO) {
  if (!replyContent.value.trim() || !item.resourceId) return
  replySubmitting.value = true
  try {
    const res = await issueApi.addComment(item.resourceId, replyContent.value.trim())
    if (res.code === 0) {
      Message.success('回复已发送')
      replyingItemId.value = null
      replyContent.value = ''
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '回复失败')
  } finally {
    replySubmitting.value = false
  }
}

/** 取消回复 */
function cancelReply() {
  replyingItemId.value = null
  replyContent.value = ''
}

/** 加载项目列表 */
async function loadProjects() {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    if (res.code === 0 && res.data) {
      projects.value = res.data.list.map(p => ({ id: p.id, name: p.name }))
    }
  } catch {
    // 静默失败
  }
}

function handleProjectChange(value: string | undefined) {
  currentPage.value = 1
  setProjectFilter(value || null)
}

function handleProjectClear() {
  currentPage.value = 1
  setProjectFilter(null)
}

/** 标签页配置 */
interface TabConfig {
  key: NotificationCategory
  label: string
  adminOnly?: boolean
}

const allTabs: TabConfig[] = [
  { key: 'all', label: '全部' },
  { key: 'mention', label: '@提及' },
  { key: 'subscription', label: '订阅更新' },
  { key: 'system', label: '系统', adminOnly: true }
]

const visibleTabs = computed(() => {
  return allTabs.filter(tab => !tab.adminOnly || isSystemAdmin.value)
})

function getCategoryCount(category: NotificationCategory): number {
  return categoryUnreadCounts.value[category] || 0
}

function getEmptyIcon(): string {
  switch (activeCategory.value) {
    case 'mention': return '📢'
    case 'subscription': return '🔔'
    case 'system': return '⚙️'
    default: return '🔔'
  }
}

function getEmptyTitle(): string {
  if (unreadOnly.value) return '没有未读通知'
  switch (activeCategory.value) {
    case 'mention': return '暂无@提及'
    case 'subscription': return '暂无订阅更新'
    case 'system': return '暂无系统通知'
    default: return '暂无新通知'
  }
}

function getEmptyDesc(): string {
  if (unreadOnly.value) return '所有通知都已阅读'
  switch (activeCategory.value) {
    case 'mention': return '当其他人在评论中@你时，通知会出现在这里'
    case 'subscription': return '当你关注的工单有状态变更、评论或分配时，通知会出现在这里'
    case 'system': return '项目成员变更、归档等系统级事件会出现在这里'
    default: return '当有新的工单分配、评论或状态变更时，通知会出现在这里'
  }
}

function formatTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  if (diffHour < 24) return `${diffHour} 小时前`
  if (diffDay < 7) return `${diffDay} 天前`

  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
}

function handleCategoryChange(category: NotificationCategory) {
  currentPage.value = 1
  setCategory(category)
}

function handleToggleUnread() {
  currentPage.value = 1
  toggleUnreadOnly()
}

function handleItemClick(item: NotificationVO) {
  if (!item.isRead) {
    markRead(item.id)
  }
  // 优先使用后端返回的 resourceUrl（通用化导航）
  if (item.resourceUrl) {
    router.push(item.resourceUrl)
  } else if (item.resourceType === 'issue' && item.resourceId) {
    router.push(`/issues/${item.resourceId}`)
  } else if (item.resourceType === 'project' && item.resourceId) {
    router.push(`/projects/${item.resourceId}`)
  } else if (item.resourceType === 'sprint' && item.projectId) {
    router.push(`/sprints?projectId=${item.projectId}`)
  }
}

function handleMarkRead(id: string) {
  markRead(id)
}

function handleMarkUnread(id: string) {
  markUnread(id)
}

function handleMarkAllRead() {
  markAllRead()
}

function handleDelete(id: string) {
  deleteNotification(id)
}

function handleDeleteAllRead() {
  deleteAllRead()
}

function changePage(page: number) {
  currentPage.value = page
  fetchNotifications(page, pageSize)
}

onMounted(() => {
  // Close the popup panel if it was open when navigating to full page
  if (panelVisible.value) {
    panelVisible.value = false
  }
  // Load data for full page view
  fetchNotifications(1, pageSize)
  fetchCategoryUnreadCounts()
  loadProjects()
})
</script>

<style scoped>
.notification-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 32px;
}

/* Page Header */
.page-header {
  margin-bottom: 16px;
  flex-shrink: 0;
}

.header-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.header-row2 {
  display: flex;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
  letter-spacing: -0.3px;
}

.unread-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  background: var(--tf-accent);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  border-radius: 10px;
  line-height: 1;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-divider {
  color: var(--tf-border);
  font-size: 14px;
  user-select: none;
}

.project-filter-select {
  width: 160px;
}

.action-link {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border: none;
  background: transparent;
  border-radius: 4px;
  color: var(--tf-text-tertiary);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}
.action-link:hover {
  color: var(--tf-text-primary);
  background: var(--tf-bg-hover);
}
.action-link.active {
  color: var(--tf-accent);
  background: var(--tf-accent-bg);
}
.action-link:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.action-link:disabled:hover {
  color: var(--tf-text-tertiary);
  background: transparent;
}
.action-link.danger:hover:not(:disabled) {
  color: var(--tf-danger);
  background: rgba(230, 126, 128, 0.08);
}

/* Category Tabs */
.page-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  border-bottom: 1px solid var(--tf-border-light);
  margin-bottom: 0;
  flex-shrink: 0;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border: none;
  background: transparent;
  color: var(--tf-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  position: relative;
  transition: color 0.15s;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tab-item:hover {
  color: var(--tf-text-primary);
}
.tab-item.active {
  color: var(--tf-accent);
  border-bottom-color: var(--tf-accent);
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  color: var(--tf-text-on-accent, #fff);
  background: var(--tf-accent);
  border-radius: 9px;
}
.tab-item:not(.active) .tab-badge {
  background: var(--tf-text-quaternary, var(--tf-text-tertiary));
  opacity: 0.7;
}

/* Page Body */
.page-body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

/* Loading Skeleton */
.page-loading {
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.loading-skeleton {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
}

.skeleton-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tf-bg-hover);
  margin-top: 8px;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

.skeleton-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: var(--tf-bg-hover);
  flex-shrink: 0;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

.skeleton-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-line {
  height: 12px;
  border-radius: 3px;
  background: var(--tf-bg-hover);
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}
.skeleton-line.short { width: 35%; }
.skeleton-line.medium { width: 60%; }
.skeleton-line.long { width: 85%; }

@keyframes skeleton-pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.8; }
}

/* Empty State */
.page-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  line-height: 1.5;
  max-width: 320px;
}

/* Notification List */
.notification-list {
  padding: 8px 0;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  cursor: pointer;
  transition: background 0.15s;
  border-radius: 8px;
  margin: 2px 0;
}
.notification-item:hover {
  background: var(--tf-bg-hover);
}
.notification-item.unread {
  background: var(--tf-accent-bg);
}
.notification-item.unread:hover {
  background: var(--tf-bg-hover);
}

.item-indicator {
  width: 10px;
  flex-shrink: 0;
  padding-top: 10px;
}

.unread-dot {
  display: block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tf-accent);
}

.item-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: var(--tf-bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}
.item-icon.has-avatar {
  border-radius: 50%;
  background: transparent;
  overflow: hidden;
}

.actor-avatar {
  width: 32px;
  height: 32px;
  object-fit: cover;
  border-radius: 50%;
}

.actor-initial {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  text-transform: uppercase;
}

.actor-system {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--tf-text-tertiary);
  opacity: 0.7;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.aggregation-badge {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent);
  background: var(--tf-accent-bg);
  padding: 2px 6px;
  border-radius: 3px;
  line-height: 1.4;
}

.item-body {
  font-size: 13px;
  color: var(--tf-text-secondary);
  line-height: 1.5;
  margin-bottom: 6px;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-reason {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 2px 6px;
  border-radius: 3px;
  line-height: 1.4;
}

.item-time {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.item-link {
  font-size: 12px;
  color: var(--tf-accent);
  opacity: 0;
  transition: opacity 0.15s;
}
.notification-item:hover .item-link {
  opacity: 1;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}
.notification-item:hover .item-actions {
  opacity: 1;
}

.item-action-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.item-action-btn:hover {
  background: var(--tf-bg-active);
  color: var(--tf-accent);
}
.item-delete-btn:hover {
  color: var(--tf-error, #f85149);
}

/* Pagination */
.page-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 20px 0;
  border-top: 1px solid var(--tf-border-light);
  margin-top: 8px;
}

.pagination-btn {
  padding: 6px 14px;
  border: 1px solid var(--tf-border);
  background: transparent;
  border-radius: 6px;
  color: var(--tf-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.pagination-btn:hover:not(:disabled) {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}
.pagination-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pagination-info {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* Notification Item Wrapper */
.notification-item-wrapper {
  margin: 2px 0;
}

/* Reply Button */
.item-reply-btn:hover {
  color: var(--tf-accent);
}

/* Inline Reply Editor */
.reply-editor {
  margin: 0 16px 12px 52px;
  padding: 12px;
  background: var(--tf-bg-surface, var(--tf-bg-hover));
  border: 1px solid var(--tf-border);
  border-radius: 8px;
}

.reply-textarea {
  width: 100%;
  min-height: 72px;
  padding: 10px 12px;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  background: var(--tf-bg-body, var(--color-bg-1));
  color: var(--tf-text-primary);
  font-size: 13px;
  line-height: 1.5;
  resize: vertical;
  font-family: inherit;
  transition: border-color 0.15s;
}
.reply-textarea:focus {
  outline: none;
  border-color: var(--tf-accent);
}
.reply-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.reply-textarea::placeholder {
  color: var(--tf-text-tertiary);
}

.reply-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.reply-hint {
  font-size: 11px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
}

.reply-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reply-cancel-btn {
  padding: 5px 12px;
  border: 1px solid var(--tf-border);
  background: transparent;
  border-radius: 6px;
  color: var(--tf-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.reply-cancel-btn:hover:not(:disabled) {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.reply-submit-btn {
  padding: 5px 14px;
  border: none;
  background: var(--tf-accent);
  border-radius: 6px;
  color: var(--tf-text-on-accent, #fff);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.reply-submit-btn:hover:not(:disabled) {
  opacity: 0.9;
}
.reply-submit-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
