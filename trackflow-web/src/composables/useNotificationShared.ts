/**
 * useNotificationShared — 通知面板 / 全页面共享逻辑
 *
 * 提取两个视图共同使用的：
 * - 分类标签页配置（visibleTabs）
 * - 分类未读计数工具（getCategoryCount）
 * - 空状态文案（getEmptyIcon / getEmptyTitle / getEmptyDesc）
 * - 通知 hash 锚点构建（buildSourceHash）
 */

import { computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import type { NotificationCategory, NotificationVO } from '@/api/notification'

export interface TabConfig {
  key: NotificationCategory
  label: string
  adminOnly?: boolean
}

const ALL_TABS: TabConfig[] = [
  { key: 'all', label: '全部' },
  { key: 'mention', label: '@提及' },
  { key: 'subscription', label: '订阅更新' },
  { key: 'system', label: '系统', adminOnly: true },
]

export function useNotificationShared(
  isSystemAdmin: Ref<boolean>,
  activeCategory: Ref<NotificationCategory>,
  unreadOnly: Ref<boolean>,
  categoryUnreadCounts: Ref<Record<string, number>>,
) {
  // ===== 分类标签页 =====

  /** 当前用户可见的分类标签页 */
  const visibleTabs: ComputedRef<TabConfig[]> = computed(() =>
    ALL_TABS.filter(tab => !tab.adminOnly || isSystemAdmin.value),
  )

  /** 获取指定分类的未读通知数 */
  function getCategoryCount(category: NotificationCategory): number {
    return categoryUnreadCounts.value[category] || 0
  }

  // ===== 空状态文案 =====

  function getEmptyIcon(): string {
    switch (activeCategory.value) {
      case 'mention':      return '📢'
      case 'subscription': return '🔔'
      case 'system':       return '⚙️'
      default:             return '🔔'
    }
  }

  function getEmptyTitle(): string {
    if (unreadOnly.value) return '没有未读通知'
    switch (activeCategory.value) {
      case 'mention':      return '暂无@提及'
      case 'subscription': return '暂无订阅更新'
      case 'system':       return '暂无系统通知'
      default:             return '暂无新通知'
    }
  }

  function getEmptyDesc(): string {
    if (unreadOnly.value) return '所有通知都已阅读'
    switch (activeCategory.value) {
      case 'mention':
        return '当其他人在评论中@你时，通知会出现在这里'
      case 'subscription':
        return '当你关注的工单有状态变更、评论或分配时，通知会出现在这里'
      case 'system':
        return '项目成员变更、归档等系统级事件会出现在这里'
      default:
        return '当有新的工单分配、评论或状态变更时，通知会出现在这里'
    }
  }

  // ===== 通知 hash 锚点 =====

  /**
   * 根据通知类型构建 sourceId 对应的 hash 锚点（不含 # 前缀）。
   * - 评论类通知（issue_commented, mention）→ "c_{sourceId}"（评论 ID）
   * - 其他活动类通知 → "a_{sourceId}"（活动记录 ID）
   */
  function buildSourceHash(item: NotificationVO): string {
    if (!item.sourceId) return ''
    const commentTypes = ['issue_commented', 'mention']
    if (commentTypes.includes(item.type || '')) {
      return `c_${item.sourceId}`
    }
    return `a_${item.sourceId}`
  }

  return {
    visibleTabs,
    getCategoryCount,
    getEmptyIcon,
    getEmptyTitle,
    getEmptyDesc,
    buildSourceHash,
  }
}
