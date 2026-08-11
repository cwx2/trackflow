<template>
  <!-- 通用空状态组件
       用于列表为空、加载失败、无权限、未选择等场景
       支持图标组件/emoji/图片、标题、描述、操作按钮（slot） -->
  <div
    class="empty-state"
    :class="[`empty-state--${type}`, { 'empty-state--compact': compact }]"
  >
    <!-- ===== 图标区：优先用 slot，其次用 icon 字符串，最后用默认图标 ===== -->
    <div class="empty-state__icon">
      <slot name="icon">
        <!-- icon prop 传 Arco 图标名（如 "search"）或 emoji（如 "📋"） -->
        <component :is="resolvedIcon" v-if="resolvedIcon" class="empty-state__icon-inner" />
        <span v-else-if="iconEmoji" class="empty-state__emoji">{{ iconEmoji }}</span>
        <icon-exclamation-circle v-else class="empty-state__icon-inner" />
      </slot>
    </div>

    <!-- ===== 标题 ===== -->
    <p class="empty-state__title">
      <slot name="title">{{ title }}</slot>
    </p>

    <!-- ===== 描述文字 ===== -->
    <p v-if="description || $slots.description" class="empty-state__desc">
      <slot name="description">{{ description }}</slot>
    </p>

    <!-- ===== 操作按钮区（完全由外部决定，不限制内容） ===== -->
    <div v-if="$slots.action" class="empty-state__action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * EmptyState — 通用空状态组件
 *
 * 职责：
 * - 统一全项目的空状态视觉样式（图标 + 标题 + 描述 + 操作按钮）
 * - 支持 normal / error / warning 三种语义类型
 * - 支持紧凑模式（用于侧边栏、小面板内的空状态）
 *
 * 用法一：简单文字
 *   <EmptyState title="暂无工单" description="尝试调整筛选条件" />
 *
 * 用法二：带图标和按钮
 *   <EmptyState icon="search" title="未找到结果" description="换个关键词试试">
 *     <template #action>
 *       <a-button type="primary" @click="reset">重置筛选</a-button>
 *     </template>
 *   </EmptyState>
 *
 * 用法三：错误状态
 *   <EmptyState type="error" icon="close-circle" title="加载失败" description="请检查网络">
 *     <template #action>
 *       <a-button @click="retry">重试</a-button>
 *     </template>
 *   </EmptyState>
 *
 * 用法四：自定义图标 slot
 *   <EmptyState title="暂无草稿">
 *     <template #icon><icon-file style="font-size: 32px;" /></template>
 *   </EmptyState>
 */
import { computed } from 'vue'
import {
  IconSearch, IconCloseCircle, IconCheckCircle, IconFolder,
  IconExclamationCircle, IconInfoCircle, IconFile, IconCalendar,
  IconUser, IconSettings, IconRefresh, IconBug,
  IconRobot, IconClockCircle, IconBarChart, IconUserGroup,
  IconLock, IconLink, IconNotification, IconTrophy, IconLanguage,
  IconOrderedList
} from '@arco-design/web-vue/es/icon'

// ===== Props =====
const props = withDefaults(defineProps<{
  /** 标题文字 */
  title: string
  /** 描述文字（可选，也可用 #description slot） */
  description?: string
  /**
   * 图标名称，对应 Arco Design 图标（不含 icon- 前缀）
   * 支持：search / close-circle / check-circle / folder / exclamation-circle /
   *       info-circle / file / calendar / user / settings / refresh / bug /
   *       robot / clock-circle / bar-chart / user-group / lock / link /
   *       notification / trophy / language / ordered-list
   * 如需其他图标，改用 #icon slot
   */
  icon?: string
  /**
   * emoji 图标（当 icon prop 不满足时使用，如 "📋" "🏃"）
   * icon 和 iconEmoji 同时传时，icon 优先
   */
  iconEmoji?: string
  /** 语义类型，影响图标和标题颜色 */
  type?: 'normal' | 'error' | 'warning'
  /** 紧凑模式：减小 padding，用于侧边栏/小面板 */
  compact?: boolean
}>(), {
  type: 'normal',
  compact: false,
})

// ===== 图标名 → 组件映射 =====
// 只内置最常用的图标，其他通过 #icon slot 传入
const iconMap: Record<string, any> = {
  'search': IconSearch,
  'close-circle': IconCloseCircle,
  'check-circle': IconCheckCircle,
  'folder': IconFolder,
  'exclamation-circle': IconExclamationCircle,
  'info-circle': IconInfoCircle,
  'file': IconFile,
  'calendar': IconCalendar,
  'user': IconUser,
  'settings': IconSettings,
  'refresh': IconRefresh,
  'bug': IconBug,
  'robot': IconRobot,
  'clock-circle': IconClockCircle,
  'bar-chart': IconBarChart,
  'user-group': IconUserGroup,
  'lock': IconLock,
  'link': IconLink,
  'notification': IconNotification,
  'trophy': IconTrophy,
  'language': IconLanguage,
  'ordered-list': IconOrderedList,
}

const resolvedIcon = computed(() => {
  if (!props.icon) return null
  return iconMap[props.icon] ?? null
})
</script>

<style scoped>
/* ===== 基础布局 ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  gap: 8px;
  text-align: center;
}

.empty-state--compact {
  padding: 16px 12px;
  gap: 6px;
}

/* ===== 图标 ===== */
.empty-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}

.empty-state__icon-inner {
  font-size: 36px;
  color: var(--tf-text-quaternary);
}

.empty-state--compact .empty-state__icon-inner {
  font-size: 24px;
}

.empty-state__emoji {
  font-size: 32px;
  opacity: 0.6;
  line-height: 1;
}

.empty-state--compact .empty-state__emoji {
  font-size: 22px;
}

/* ===== 标题 ===== */
.empty-state__title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  line-height: 1.4;
}

.empty-state--compact .empty-state__title {
  font-size: 12px;
}

/* ===== 描述 ===== */
.empty-state__desc {
  margin: 0;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  line-height: 1.5;
  max-width: 320px;
}

.empty-state--compact .empty-state__desc {
  font-size: 11px;
  max-width: 240px;
}

/* ===== 操作按钮区 ===== */
.empty-state__action {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
  justify-content: center;
}

/* ===== 语义类型 ===== */
.empty-state--error .empty-state__icon-inner {
  color: var(--tf-danger);
}

.empty-state--error .empty-state__title {
  color: var(--tf-danger);
}

.empty-state--warning .empty-state__icon-inner {
  color: var(--tf-warning, var(--color-warning-6));
}
</style>
