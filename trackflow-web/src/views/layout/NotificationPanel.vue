<template>
  <Teleport to="body">
    <Transition name="panel-fade">
      <div v-if="panelVisible" class="notification-overlay" @click.self="closePanel">
        <div class="notification-panel">
          <!-- 面板头部 -->
          <div class="panel-header">
            <h3 class="panel-title">通知</h3>
            <div class="panel-actions">
              <button
                class="panel-action-btn"
                :class="{ active: unreadOnly }"
                title="仅显示未读"
                @click="toggleUnreadOnly"
              >
                <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                  <path d="M8 16A8 8 0 1 1 8 0a8 8 0 0 1 0 16zm3.78-9.72a.75.75 0 0 0-1.06-1.06L6.75 9.19 5.28 7.72a.75.75 0 0 0-1.06 1.06l2 2a.75.75 0 0 0 1.06 0l4.5-4.5z" />
                </svg>
              </button>
              <button
                class="panel-action-btn"
                title="全部标记已读"
                :disabled="unreadCount === 0"
                @click="handleMarkAllRead"
              >
                <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                  <path d="M1.5 3.25a2.25 2.25 0 1 1 3 2.122v5.256a2.251 2.251 0 1 1-1.5 0V5.372A2.25 2.25 0 0 1 1.5 3.25Zm5.677-.177L9.573.677A.25.25 0 0 1 10 .854V2.5h1A2.5 2.5 0 0 1 13.5 5v5.628a2.251 2.251 0 1 1-1.5 0V5a1 1 0 0 0-1-1h-1v1.646a.25.25 0 0 1-.427.177L7.177 3.427a.25.25 0 0 1 0-.354Z"/>
                </svg>
              </button>
              <button class="panel-action-btn panel-close-btn" title="关闭" @click="closePanel">
                <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                  <path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94l3.22-3.22a.75.75 0 1 1 1.06 1.06L9.06 8l3.22 3.22a.75.75 0 1 1-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 0 1-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 0 1 0-1.06Z"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 面板内容 -->
          <div class="panel-body">
            <!-- 加载状态 -->
            <div v-if="loading && notifications.length === 0" class="panel-loading">
              <div class="loading-skeleton" v-for="i in 4" :key="i">
                <div class="skeleton-icon"></div>
                <div class="skeleton-content">
                  <div class="skeleton-line short"></div>
                  <div class="skeleton-line long"></div>
                </div>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-else-if="notifications.length === 0" class="panel-empty">
              <div class="empty-icon">🔔</div>
              <div class="empty-title">{{ unreadOnly ? '没有未读通知' : '暂无新通知' }}</div>
              <div class="empty-desc">{{ unreadOnly ? '所有通知都已阅读' : '当有新的工单分配、评论或状态变更时，通知会出现在这里' }}</div>
            </div>

            <!-- 通知列表 -->
            <div v-else class="notification-list">
              <div
                v-for="item in notifications"
                :key="item.id"
                class="notification-item"
                :class="{ unread: !item.isRead }"
                @click="handleItemClick(item)"
              >
                <div class="item-indicator">
                  <span v-if="!item.isRead" class="unread-dot"></span>
                </div>
                <div class="item-icon">
                  {{ getTypeIcon(item.type) }}
                </div>
                <div class="item-content">
                  <div class="item-title">{{ item.title }}</div>
                  <div class="item-body">{{ item.content }}</div>
                  <div class="item-time">{{ formatTime(item.createdAt) }}</div>
                </div>
                <button
                  v-if="!item.isRead"
                  class="item-mark-btn"
                  title="标记已读"
                  @click.stop="handleMarkRead(item.id)"
                >
                  <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M13.78 4.22a.75.75 0 0 1 0 1.06l-7.25 7.25a.75.75 0 0 1-1.06 0L2.22 9.28a.75.75 0 0 1 1.06-1.06L6 10.94l6.72-6.72a.75.75 0 0 1 1.06 0Z"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <!-- 面板底部 -->
          <div v-if="notifications.length > 0" class="panel-footer">
            <span class="footer-count">共 {{ totalCount }} 条通知</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useNotification } from '@/composables/useNotification'
import type { NotificationVO } from '@/api/notification'

const router = useRouter()
const {
  panelVisible,
  notifications,
  loading,
  unreadOnly,
  unreadCount,
  totalCount,
  closePanel,
  toggleUnreadOnly,
  markRead,
  markAllRead
} = useNotification()

function getTypeIcon(type: string): string {
  switch (type) {
    case 'issue_assigned': return '👤'
    case 'issue_commented': return '💬'
    case 'issue_status_changed': return '🔄'
    case 'issue_created': return '📋'
    case 'member_added': return '➕'
    case 'member_removed': return '➖'
    case 'lead_changed': return '👑'
    case 'mention': return '📢'
    default: return '🔔'
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

  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function handleItemClick(item: NotificationVO) {
  // 标记已读
  if (!item.isRead) {
    markRead(item.id)
  }
  // 跳转到对应资源
  if (item.resourceType === 'issue' && item.resourceId) {
    closePanel()
    router.push(`/issues/${item.resourceId}`)
  } else if (item.resourceType === 'project' && item.resourceId) {
    closePanel()
    router.push(`/projects/${item.resourceId}`)
  }
}

function handleMarkRead(id: string) {
  markRead(id)
}

function handleMarkAllRead() {
  markAllRead()
}
</script>

<style scoped>
.notification-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
}

.notification-panel {
  position: fixed;
  left: var(--tf-sidebar-width, 200px);
  bottom: 60px;
  width: 380px;
  max-height: 520px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 101;
}

/* Panel Header */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--tf-border-light);
  flex-shrink: 0;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.panel-action-btn {
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
.panel-action-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}
.panel-action-btn.active {
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
}
.panel-action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.panel-action-btn:disabled:hover {
  background: transparent;
  color: var(--tf-text-tertiary);
}

/* Panel Body */
.panel-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

/* Loading Skeleton */
.panel-loading {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.loading-skeleton {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.skeleton-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--tf-bg-hover);
  flex-shrink: 0;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

.skeleton-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.skeleton-line {
  height: 12px;
  border-radius: 3px;
  background: var(--tf-bg-hover);
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}
.skeleton-line.short { width: 40%; }
.skeleton-line.long { width: 80%; }

@keyframes skeleton-pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.8; }
}

/* Empty State */
.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 6px;
}

.empty-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  line-height: 1.5;
  max-width: 240px;
}

/* Notification List */
.notification-list {
  padding: 4px 0;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
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
  width: 8px;
  flex-shrink: 0;
  padding-top: 6px;
}

.unread-dot {
  display: block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--tf-accent);
}

.item-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--tf-bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-body {
  font-size: 12px;
  color: var(--tf-text-secondary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.item-mark-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 4px;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
  flex-shrink: 0;
  margin-top: 2px;
}
.notification-item:hover .item-mark-btn {
  opacity: 1;
}
.item-mark-btn:hover {
  background: var(--tf-bg-active);
  color: var(--tf-accent);
}

/* Panel Footer */
.panel-footer {
  padding: 8px 16px;
  border-top: 1px solid var(--tf-border-light);
  flex-shrink: 0;
}

.footer-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* Transition */
.panel-fade-enter-active,
.panel-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.panel-fade-enter-from,
.panel-fade-leave-to {
  opacity: 0;
}
.panel-fade-enter-from .notification-panel,
.panel-fade-leave-to .notification-panel {
  transform: translateY(8px) scale(0.98);
}
</style>
