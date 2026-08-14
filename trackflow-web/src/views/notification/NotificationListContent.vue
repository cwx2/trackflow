<template>
  <!--
    NotificationListContent — 通知列表内容区公共组件

    职责：
    - 渲染分类标签页（全部 / @提及 / 订阅更新 / 系统）
    - 渲染加载中骨架屏
    - 渲染空状态
    - 通过默认 slot 交给父组件渲染具体列表内容

    对外接口：
    - Props：activeCategory / visibleTabs / getCategoryCount / loading / isEmpty /
             getEmptyIcon / getEmptyTitle / getEmptyDesc / compactSkeleton / compactEmpty
    - Emits：category-change（切换分类时）
    - Slots：default（列表内容区）
  -->

  <!-- 分类标签页 -->
  <a-tabs
    :active-key="activeCategory"
    :class="tabsClass"
    @change="(key: any) => emit('category-change', key as NotificationCategory)"
  >
    <a-tab-pane v-for="tab in visibleTabs" :key="tab.key">
      <template #title>
        {{ tab.label }}
        <span v-if="getCategoryCount(tab.key) > 0" class="tab-badge">
          {{ getCategoryCount(tab.key) }}
        </span>
      </template>
    </a-tab-pane>
  </a-tabs>

  <!-- 骨架屏 -->
  <template v-if="loading && isEmpty">
    <div :class="skeletonWrapClass">
      <div
        v-for="i in (compactSkeleton ? 4 : 8)"
        :key="i"
        :class="skeletonItemClass"
      >
        <div v-if="!compactSkeleton" class="skeleton-indicator"></div>
        <div class="skeleton-icon"></div>
        <div class="skeleton-content">
          <div class="skeleton-line short"></div>
          <div class="skeleton-line long"></div>
          <div v-if="!compactSkeleton" class="skeleton-line medium"></div>
        </div>
      </div>
    </div>
  </template>

  <!-- 空状态 -->
  <EmptyState
    v-else-if="!loading && isEmpty"
    :icon-emoji="getEmptyIcon()"
    :title="getEmptyTitle()"
    :description="getEmptyDesc()"
    :compact="compactEmpty"
  />

  <!-- 默认 slot：通知列表内容（由 Panel/View 各自渲染） -->
  <slot v-else />
</template>

<script setup lang="ts">
/**
 * NotificationListContent — 通知列表内容区公共组件
 *
 * 职责：
 * - 分类标签页 + 骨架屏 + 空状态
 * - 具体通知列表通过默认 slot 注入
 *
 * 对外接口：
 * - Props: activeCategory、visibleTabs、getCategoryCount、loading、isEmpty、
 *          getEmptyIcon/Title/Desc、tabsClass、compactSkeleton、compactEmpty
 * - Emits: category-change
 * - Slots: default（列表内容）
 */
import type { NotificationCategory } from '@/api/notification'
import type { TabConfig } from '@/composables/useNotificationShared'
import { EmptyState } from '@/components/base'

const props = defineProps<{
  /** 当前激活的分类 */
  activeCategory: NotificationCategory
  /** 可见标签页配置 */
  visibleTabs: TabConfig[]
  /** 获取指定分类的未读数 */
  getCategoryCount: (category: NotificationCategory) => number
  /** 是否正在加载（控制骨架屏） */
  loading: boolean
  /** 通知列表是否为空（控制空状态 / 骨架屏） */
  isEmpty: boolean
  /** 获取空状态 emoji 图标 */
  getEmptyIcon: () => string
  /** 获取空状态标题 */
  getEmptyTitle: () => string
  /** 获取空状态描述 */
  getEmptyDesc: () => string
  /** a-tabs 额外 class（Panel / View 样式不同） */
  tabsClass?: string
  /** 使用紧凑型骨架屏（面板用 4 行，页面用 8 行） */
  compactSkeleton?: boolean
  /** EmptyState 使用 compact 模式 */
  compactEmpty?: boolean
}>()

const emit = defineEmits<{
  /** 切换分类标签页 */
  'category-change': [category: NotificationCategory]
}>()

const skeletonWrapClass = props.compactSkeleton
  ? 'panel-loading'
  : 'page-loading'

const skeletonItemClass = props.compactSkeleton
  ? 'loading-skeleton loading-skeleton--compact'
  : 'loading-skeleton'
</script>

<style scoped>
/* ===== 分类标签页 ===== */

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
  margin-left: 4px;
}
:deep(.arco-tabs-tab:not(.arco-tabs-tab-active)) .tab-badge {
  background: var(--tf-text-quaternary, var(--tf-text-tertiary));
  opacity: 0.7;
}

/* ===== 骨架屏（公共） ===== */

.page-loading {
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

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
  padding: 12px 16px;
}

/* 紧凑模式（面板） */
.loading-skeleton--compact {
  padding: 0;
  gap: 10px;
}

.skeleton-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tf-bg-hover);
  margin-top: 8px;
  flex-shrink: 0;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

.skeleton-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--tf-bg-hover);
  flex-shrink: 0;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

/* 页面模式骨架屏图标稍大 */
.loading-skeleton:not(.loading-skeleton--compact) .skeleton-icon {
  width: 32px;
  height: 32px;
}

.skeleton-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.loading-skeleton:not(.loading-skeleton--compact) .skeleton-content {
  gap: 8px;
}

.skeleton-line {
  height: 12px;
  border-radius: 3px;
  background: var(--tf-bg-hover);
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}
.skeleton-line.short  { width: 35%; }
.skeleton-line.medium { width: 60%; }
.skeleton-line.long   { width: 85%; }

@keyframes skeleton-pulse {
  0%, 100% { opacity: 0.4; }
  50%       { opacity: 0.8; }
}
</style>
