<script setup lang="ts">
/**
 * NotificationItem — 单条通知渲染组件
 *
 * 职责：
 * - 渲染一条通知（actor 头像、标题、内容、原因、时间、操作按钮）
 * - 不持有任何列表状态，数据全部通过 Props 传入
 *
 * 对外接口：
 * - Props: item, highlightId
 * - Emits: 'click', 'mark-read', 'mark-unread', 'reply'
 */
import { UserAvatar } from '@/components/base'
import { IconCheck, IconRecord, IconCommon } from '@arco-design/web-vue/es/icon'
import type { NotificationVO } from '@/api/notification'

interface Props {
  item: NotificationVO
  /** 高亮 flash 的通知 id */
  highlightId?: string
  /** 是否显示回复按钮（NotificationView 模式） */
  canReply?: boolean
}

const { item, highlightId, canReply = false } = defineProps<Props>()

const emit = defineEmits<{
  click: [item: NotificationVO]
  'mark-read': [id: string]
  'mark-unread': [id: string]
  reply: [item: NotificationVO]
}>()

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = (now.getTime() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 604800) return `${Math.floor(diff / 86400)} 天前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}
</script>

<template>
  <div
    class="notification-item"
    :class="{
      unread: !item.isRead,
      'highlight-flash': highlightId === item.id,
    }"
    :data-notification-id="item.id"
    :data-unread="!item.isRead ? 'true' : undefined"
    @click="emit('click', item)"
  >
    <!-- 未读指示点 -->
    <div class="item-indicator">
      <span v-if="!item.isRead" class="unread-dot" />
    </div>

    <!-- Actor 头像 -->
    <div class="item-icon" :class="{ 'has-avatar': item.actorAvatar }">
      <img v-if="item.actorAvatar" :src="item.actorAvatar" :alt="item.actorName" class="actor-avatar" />
      <UserAvatar v-else-if="item.actorName" :name="item.actorName" :size="28" />
      <span v-else class="actor-system" title="系统操作">
        <icon-common :size="14" />
      </span>
    </div>

    <!-- 内容 -->
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

    <!-- 操作按钮：默认插槽提供已读/未读按钮，父组件可通过 #actions 覆盖 -->
    <div class="item-actions">
      <slot name="actions">
        <button
          v-if="!item.isRead"
          class="item-action-btn"
          title="标记已读"
          @click.stop="emit('mark-read', item.id)"
        >
          <icon-check :size="14" />
        </button>
        <button
          v-else
          class="item-action-btn"
          title="标记未读"
          @click.stop="emit('mark-unread', item.id)"
        >
          <icon-record :size="14" />
        </button>
        <button
          v-if="canReply"
          class="item-action-btn item-reply-btn"
          title="回复"
          @click.stop="emit('reply', item)"
        >
          ↩
        </button>
      </slot>
    </div>
  </div>
</template>

<style scoped>
.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.15s;
  position: relative;
}
.notification-item:hover {
  background: var(--tf-bg-hover);
}
.notification-item.unread {
  background: var(--tf-bg-elevated);
}
.notification-item.highlight-flash {
  animation: flash-highlight 1.5s ease-out;
}
@keyframes flash-highlight {
  0%   { background: var(--color-primary-light-3); }
  100% { background: transparent; }
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
  background: var(--color-primary-6);
}
.item-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--tf-bg-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}
.item-icon.has-avatar {
  background: transparent;
}
.actor-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}
.actor-system {
  color: var(--tf-text-tertiary);
}
.item-content {
  flex: 1;
  min-width: 0;
}
.item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  line-height: 1.4;
  margin-bottom: 2px;
}
.aggregation-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 400;
  border-radius: 10px;
  background: var(--color-primary-light-3);
  color: var(--color-primary-6);
}
.item-body {
  font-size: 12px;
  color: var(--tf-text-secondary);
  line-height: 1.4;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}
.item-reason {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-elevated);
  padding: 1px 5px;
  border-radius: 3px;
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
  opacity: 0;
  transition: opacity 0.15s;
}
.notification-item:hover .item-actions {
  opacity: 1;
}
.item-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  color: var(--tf-text-tertiary);
  font-size: 14px;
  transition: background-color 0.15s, color 0.15s;
}
.item-action-btn:hover {
  background: var(--tf-bg-elevated);
  color: var(--tf-text-primary);
}
.item-reply-btn {
  font-size: 16px;
}
</style>
