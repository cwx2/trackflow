<template>
  <Teleport to="body">
    <Transition name="panel-fade">
      <div v-if="panelVisible" class="notification-overlay" @click.self="closePanel">
        <div class="notification-panel">
          <!-- 面板头部 -->
          <div class="panel-header">
            <h3 class="panel-title panel-title-link" title="在全页面中打开通知中心" @click="openFullPage">通知</h3>
            <div class="panel-actions">
              <button
                class="panel-action-btn"
                title="展开为全页面"
                @click="openFullPage"
              >
                <icon-expand :size="14" />
              </button>
              <button
                class="panel-action-btn"
                :class="{ active: unreadOnly }"
                title="仅显示未读"
                @click="toggleUnreadOnly"
              >
                <icon-check-circle :size="14" />
              </button>
              <button
                class="panel-action-btn"
                title="全部标记已读"
                :disabled="unreadCount === 0"
                @click="handleMarkAllRead"
              >
                <!-- 保留：Arco 无等效的双勾/全部已读图标 -->
                <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                  <path d="M1.5 3.25a2.25 2.25 0 1 1 3 2.122v5.256a2.251 2.251 0 1 1-1.5 0V5.372A2.25 2.25 0 0 1 1.5 3.25Zm5.677-.177L9.573.677A.25.25 0 0 1 10 .854V2.5h1A2.5 2.5 0 0 1 13.5 5v5.628a2.251 2.251 0 1 1-1.5 0V5a1 1 0 0 0-1-1h-1v1.646a.25.25 0 0 1-.427.177L7.177 3.427a.25.25 0 0 1 0-.354Z"/>
                </svg>
              </button>
              <button
                class="panel-action-btn"
                title="清除所有已读通知"
                :disabled="!hasRead"
                @click="handleDeleteAllRead"
              >
                <icon-delete :size="14" />
              </button>
              <button class="panel-action-btn panel-close-btn" title="关闭" @click="closePanel">
                <icon-close :size="14" />
              </button>
            </div>
          </div>

          <!-- 分类标签页 -->
          <div class="panel-tabs">
            <button
              v-for="tab in visibleTabs"
              :key="tab.key"
              class="tab-item"
              :class="{ active: activeCategory === tab.key }"
              @click="setCategory(tab.key)"
            >
              <span class="tab-label">{{ tab.label }}</span>
              <span
                v-if="getCategoryCount(tab.key) > 0"
                class="tab-badge"
              >{{ getCategoryCount(tab.key) }}</span>
            </button>
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
              <div class="empty-icon">{{ getEmptyIcon() }}</div>
              <div class="empty-title">{{ getEmptyTitle() }}</div>
              <div class="empty-desc">{{ getEmptyDesc() }}</div>
            </div>

            <!-- 通知列表（按工单分组） -->
            <div v-else class="notification-list">
              <div
                v-for="group in groupedNotifications"
                :key="`${group.resourceType}:${group.resourceId}`"
                class="notification-group"
                :class="{ 'has-multiple': group.items.length > 1 }"
              >
                <!-- 分组头部（多条通知时显示） -->
                <div v-if="group.items.length > 1 && group.resourceType" class="group-header">
                  <span class="group-title">{{ group.resourceTitle }}</span>
                  <span class="group-count">{{ group.items.length }} 条通知</span>
                  <button
                    v-if="group.resourceType === 'issue'"
                    class="group-mute-btn"
                    :class="{ muted: group.resourceMuted }"
                    :title="group.resourceMuted ? '取消静音' : '静音此工单'"
                    @click.stop="handleMuteToggle(group)"
                  >
                    {{ group.resourceMuted ? '🔇' : '🔔' }}
                  </button>
                </div>

                <!-- 通知项 -->
                <div
                  v-for="item in getVisibleItems(group)"
                  :key="item.id"
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
                    <div class="item-footer">
                      <span v-if="item.reasonLabel" class="item-reason">{{ item.reasonLabel }}</span>
                      <span class="item-time">{{ formatTime(item.updatedAt || item.createdAt) }}</span>
                    </div>
                  </div>
                  <div class="item-actions">
                    <button
                      v-if="!item.isRead"
                      class="item-action-btn"
                      title="标记已读"
                      @click.stop="handleMarkRead(item.id)"
                    >
                      <icon-check :size="12" />
                    </button>
                    <!-- 单条通知的静音按钮（仅单条分组时显示） -->
                    <button
                      v-if="group.items.length === 1 && item.resourceType === 'issue' && item.resourceId"
                      class="item-action-btn"
                      :class="{ 'muted-active': item.resourceMuted }"
                      :title="item.resourceMuted ? '取消静音此工单' : '静音此工单'"
                      @click.stop="handleMuteToggle(group)"
                    >
                      <icon-mute v-if="item.resourceMuted" :size="12" />
                      <icon-notification v-else :size="12" />
                    </button>
                    <button
                      class="item-action-btn item-delete-btn"
                      title="删除通知"
                      @click.stop="handleDelete(item.id)"
                    >
                      <icon-close :size="12" />
                    </button>
                  </div>
                </div>

                <!-- 展开/收起按钮 -->
                <button
                  v-if="getHiddenCount(group) > 0"
                  class="group-expand-btn"
                  @click="toggleGroupExpanded(`${group.resourceType}:${group.resourceId}`)"
                >
                  显示更多 ({{ getHiddenCount(group) }} 条)
                </button>
                <button
                  v-else-if="group.items.length > DEFAULT_VISIBLE && isGroupExpanded(`${group.resourceType}:${group.resourceId}`)"
                  class="group-expand-btn"
                  @click="toggleGroupExpanded(`${group.resourceType}:${group.resourceId}`)"
                >
                  收起
                </button>
              </div>
            </div>
          </div>

          <!-- 面板底部 -->
          <div v-if="notifications.length > 0" class="panel-footer">
            <span class="footer-count">{{ unreadCount }} 条未读</span>
            <button class="footer-link" @click="openFullPage">查看全部 →</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useNotification } from '@/composables/useNotification'
import type { NotificationVO, NotificationCategory } from '@/api/notification'

const router = useRouter()
const {
  panelVisible,
  notifications,
  loading,
  unreadOnly,
  unreadCount,
  totalCount,
  hasRead,
  activeCategory,
  categoryUnreadCounts,
  isSystemAdmin,
  closePanel,
  toggleUnreadOnly,
  setCategory,
  markRead,
  markAllRead,
  deleteNotification,
  deleteAllRead,
  muteThread,
  unmuteThread
} = useNotification()

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

/** 当前用户可见的标签页 */
const visibleTabs = computed(() => {
  return allTabs.filter(tab => !tab.adminOnly || isSystemAdmin.value)
})

/** 获取指定分类的未读计数 */
function getCategoryCount(category: NotificationCategory): number {
  return categoryUnreadCounts.value[category] || 0
}

// ===== 分组逻辑 =====

interface NotificationGroup {
  resourceId: string
  resourceType: string
  resourceTitle: string
  items: NotificationVO[]
  latestTime: string
  unreadCount: number
  resourceMuted: boolean
}

/** 按工单分组后的通知列表 */
const groupedNotifications = computed<NotificationGroup[]>(() => {
  const items = notifications.value
  if (items.length === 0) return []

  const groupMap = new Map<string, NotificationGroup>()
  const ungrouped: NotificationGroup[] = []

  for (const item of items) {
    const key = item.resourceType && item.resourceId
      ? `${item.resourceType}:${item.resourceId}`
      : null

    if (key) {
      let group = groupMap.get(key)
      if (!group) {
        group = {
          resourceId: item.resourceId!,
          resourceType: item.resourceType!,
          resourceTitle: extractResourceTitle(item),
          items: [],
          latestTime: item.updatedAt || item.createdAt,
          unreadCount: 0,
          resourceMuted: item.resourceMuted || false
        }
        groupMap.set(key, group)
      }
      group.items.push(item)
      if (!item.isRead) group.unreadCount++
      const itemTime = item.updatedAt || item.createdAt
      if (itemTime > group.latestTime) group.latestTime = itemTime
    } else {
      ungrouped.push({
        resourceId: item.id,
        resourceType: '',
        resourceTitle: '',
        items: [item],
        latestTime: item.updatedAt || item.createdAt,
        unreadCount: item.isRead ? 0 : 1,
        resourceMuted: false
      })
    }
  }

  const groups = [...groupMap.values(), ...ungrouped]
  groups.sort((a, b) => b.latestTime.localeCompare(a.latestTime))
  return groups
})

/** 从通知标题中提取资源标识 */
function extractResourceTitle(item: NotificationVO): string {
  const match = item.title.match(/^([A-Z0-9]+-\d+)/)
  return match ? match[1] : item.title.split(' ')[0]
}

/** 每个分组默认展示的通知数量 */
const DEFAULT_VISIBLE = 3

/** 跟踪哪些分组已展开 */
const expandedGroups = ref<Set<string>>(new Set())

function isGroupExpanded(groupKey: string): boolean {
  return expandedGroups.value.has(groupKey)
}

function toggleGroupExpanded(groupKey: string) {
  const newSet = new Set(expandedGroups.value)
  if (newSet.has(groupKey)) {
    newSet.delete(groupKey)
  } else {
    newSet.add(groupKey)
  }
  expandedGroups.value = newSet
}

function getVisibleItems(group: NotificationGroup): NotificationVO[] {
  const key = `${group.resourceType}:${group.resourceId}`
  if (group.items.length <= DEFAULT_VISIBLE || isGroupExpanded(key)) {
    return group.items
  }
  return group.items.slice(0, DEFAULT_VISIBLE)
}

function getHiddenCount(group: NotificationGroup): number {
  if (group.items.length <= DEFAULT_VISIBLE) return 0
  const key = `${group.resourceType}:${group.resourceId}`
  if (isGroupExpanded(key)) return 0
  return group.items.length - DEFAULT_VISIBLE
}

// ===== 静音操作 =====

function handleMuteToggle(group: NotificationGroup) {
  if (group.resourceMuted) {
    unmuteThread(group.resourceType, group.resourceId)
    group.resourceMuted = false
  } else {
    muteThread(group.resourceType, group.resourceId)
    group.resourceMuted = true
  }
}

/** 分类相关的空状态 */
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

  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function handleItemClick(item: NotificationVO) {
  // 标记已读
  if (!item.isRead) {
    markRead(item.id)
  }
  // 优先使用后端返回的 resourceUrl（通用化导航）
  if (item.resourceUrl) {
    closePanel()
    router.push(item.resourceUrl)
  } else if (item.resourceType === 'issue' && item.resourceId) {
    closePanel()
    router.push(`/issues/${item.resourceId}`)
  } else if (item.resourceType === 'project' && item.resourceId) {
    closePanel()
    router.push(`/projects/${item.resourceId}`)
  } else if (item.resourceType === 'sprint' && item.projectId) {
    closePanel()
    router.push(`/sprints?projectId=${item.projectId}`)
  }
}

function openFullPage() {
  closePanel()
  router.push('/notifications')
}

function handleMarkRead(id: string) {
  markRead(id)
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
  max-height: 560px;
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

.panel-title-link {
  cursor: pointer;
  transition: color 0.15s;
}
.panel-title-link:hover {
  color: var(--tf-accent);
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

/* Category Tabs */
.panel-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 12px;
  border-bottom: 1px solid var(--tf-border-light);
  flex-shrink: 0;
  overflow-x: auto;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  border: none;
  background: transparent;
  color: var(--tf-text-secondary);
  font-size: 12px;
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

.tab-label {
  line-height: 1;
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  color: var(--tf-text-on-accent, #fff);
  background: var(--tf-accent);
  border-radius: 8px;
}

.tab-item:not(.active) .tab-badge {
  background: var(--tf-text-quaternary, var(--tf-text-tertiary));
  opacity: 0.7;
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

.notification-group {
  border-bottom: 1px solid var(--tf-border-light);
}
.notification-group:last-child {
  border-bottom: none;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px 4px;
  font-size: 11px;
}

.group-title {
  font-weight: 600;
  color: var(--tf-text-primary);
}

.group-count {
  color: var(--tf-text-tertiary);
}

.group-mute-btn {
  margin-left: auto;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: background 0.15s;
  opacity: 0.6;
}
.group-mute-btn:hover {
  background: var(--tf-bg-hover);
  opacity: 1;
}
.group-mute-btn.muted {
  opacity: 1;
  color: var(--tf-text-tertiary);
}

.group-expand-btn {
  display: block;
  width: 100%;
  padding: 6px 16px;
  border: none;
  background: transparent;
  font-size: 11px;
  color: var(--tf-accent);
  cursor: pointer;
  text-align: left;
  padding-left: 52px;
  transition: background 0.15s;
}
.group-expand-btn:hover {
  background: var(--tf-bg-hover);
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
.item-icon.has-avatar {
  border-radius: 50%;
  background: transparent;
  overflow: hidden;
}

.actor-avatar {
  width: 28px;
  height: 28px;
  object-fit: cover;
  border-radius: 50%;
}

.actor-initial {
  font-size: 12px;
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
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 6px;
}

.aggregation-badge {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 500;
  color: var(--tf-accent);
  background: var(--tf-accent-bg);
  padding: 1px 5px;
  border-radius: 3px;
  line-height: 1.4;
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

.item-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.item-reason {
  display: inline-flex;
  align-items: center;
  font-size: 10px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 1px 5px;
  border-radius: 3px;
  line-height: 1.4;
}

.item-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  margin-top: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}
.notification-item:hover .item-actions {
  opacity: 1;
}

.item-action-btn {
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
  transition: background 0.15s, color 0.15s;
}
.item-action-btn:hover {
  background: var(--tf-bg-active);
  color: var(--tf-accent);
}
.item-action-btn.muted-active {
  color: var(--tf-text-tertiary);
  opacity: 0.8;
}
.item-delete-btn:hover {
  color: var(--tf-error, #f85149);
}

/* Panel Footer */
.panel-footer {
  padding: 8px 16px;
  border-top: 1px solid var(--tf-border-light);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.footer-link {
  font-size: 11px;
  color: var(--tf-accent);
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
  transition: background 0.15s;
}
.footer-link:hover {
  background: var(--tf-accent-bg);
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
