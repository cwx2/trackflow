<template>
  <div class="widget-card" v-if="widget">
    <!-- Widget 头部 -->
    <div class="widget-header">
      <div class="widget-header-left">
        <span class="widget-type-icon">{{ widgetIcon }}</span>
        <span class="widget-title">{{ widget.title || widgetTypeLabel }}</span>
      </div>
      <div class="widget-header-right" v-if="isOwner">
        <a-dropdown trigger="click" :popup-max-height="false">
          <button class="widget-menu-btn" @click.stop>
            <icon-more />
          </button>
          <template #content>
            <a-doption @click="$emit('edit', widget)">
              <template #icon><icon-edit /></template>
              编辑配置
            </a-doption>
            <a-doption class="danger-option" @click="$emit('delete', widget)">
              <template #icon><icon-delete /></template>
              删除微件
            </a-doption>
          </template>
        </a-dropdown>
      </div>
    </div>

    <!-- Widget 内容区 -->
    <div class="widget-body">
      <!-- 笔记微件 -->
      <div v-if="widget.widgetType === 'note'" class="widget-note">
        <p class="note-placeholder" v-if="!parsedConfig.content">
          点击编辑添加笔记内容...
        </p>
        <div v-else class="note-content" v-html="parsedConfig.content"></div>
      </div>

      <!-- 数字卡片微件 -->
      <div v-else-if="widget.widgetType === 'number_card'" class="widget-number-card">
        <div class="number-value">{{ parsedConfig.value ?? '—' }}</div>
        <div class="number-label">{{ parsedConfig.label || '统计数值' }}</div>
      </div>

      <!-- Issue 列表微件 -->
      <div v-else-if="widget.widgetType === 'issue_list'" class="widget-issue-list">
        <div class="placeholder-content">
          <span class="placeholder-icon">📋</span>
          <span class="placeholder-text">Issue 列表（待配置查询条件）</span>
        </div>
      </div>

      <!-- 活动流微件 -->
      <div v-else-if="widget.widgetType === 'activity_feed'" class="widget-activity">
        <div class="placeholder-content">
          <span class="placeholder-icon">🔔</span>
          <span class="placeholder-text">活动流（待配置数据源）</span>
        </div>
      </div>

      <!-- 报表分布图微件 -->
      <div v-else-if="widget.widgetType === 'report_distribution'" class="widget-chart">
        <div class="placeholder-content">
          <span class="placeholder-icon">📊</span>
          <span class="placeholder-text">分布图表（待关联报表）</span>
        </div>
      </div>

      <!-- 报表微件 -->
      <div v-else-if="widget.widgetType === 'report'" class="widget-chart">
        <div class="placeholder-content">
          <span class="placeholder-icon">📈</span>
          <span class="placeholder-text">报表图表（待关联报表定义）</span>
        </div>
      </div>

      <!-- Sprint 进度 -->
      <div v-else-if="widget.widgetType === 'sprint_progress'" class="widget-sprint">
        <div class="placeholder-content">
          <span class="placeholder-icon">🏃</span>
          <span class="placeholder-text">Sprint 进度（待配置 Sprint）</span>
        </div>
      </div>

      <!-- 日历微件 -->
      <div v-else-if="widget.widgetType === 'calendar'" class="widget-calendar">
        <div class="placeholder-content">
          <span class="placeholder-icon">📅</span>
          <span class="placeholder-text">到期日历（待配置项目）</span>
        </div>
      </div>

      <!-- 未知类型 -->
      <div v-else class="widget-unknown">
        <div class="placeholder-content">
          <span class="placeholder-icon">❓</span>
          <span class="placeholder-text">未知微件类型: {{ widget.widgetType }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { IconMore, IconEdit, IconDelete } from '@arco-design/web-vue/es/icon'
import type { DashboardWidgetVO } from '@/api/customDashboard'

const props = defineProps<{
  widget: DashboardWidgetVO | undefined
  isOwner: boolean
}>()

defineEmits<{
  edit: [widget: DashboardWidgetVO]
  delete: [widget: DashboardWidgetVO]
}>()

const widgetTypeMap: Record<string, { icon: string; label: string }> = {
  note: { icon: '📝', label: '快捷笔记' },
  number_card: { icon: '🔢', label: '数字卡片' },
  report_distribution: { icon: '📊', label: '分布图表' },
  issue_list: { icon: '📋', label: 'Issue 列表' },
  activity_feed: { icon: '🔔', label: '活动流' },
  report: { icon: '📈', label: '报表图表' },
  sprint_progress: { icon: '🏃', label: 'Sprint 进度' },
  calendar: { icon: '📅', label: '到期日历' }
}

const widgetIcon = computed(() => {
  if (!props.widget) return '❓'
  return widgetTypeMap[props.widget.widgetType]?.icon || '❓'
})

const widgetTypeLabel = computed(() => {
  if (!props.widget) return ''
  return widgetTypeMap[props.widget.widgetType]?.label || props.widget.widgetType
})

const parsedConfig = computed(() => {
  if (!props.widget?.config) return {}
  try {
    return JSON.parse(props.widget.config)
  } catch {
    return {}
  }
})
</script>

<style scoped>
.widget-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  overflow: hidden;
}

.widget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--tf-border-light);
  flex-shrink: 0;
}

.widget-header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.widget-type-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.widget-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.widget-menu-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  color: var(--tf-text-tertiary);
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.widget-menu-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.widget-body {
  flex: 1;
  padding: 12px;
  overflow: auto;
  min-height: 0;
}

/* 笔记微件 */
.note-placeholder {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  font-style: italic;
  margin: 0;
}

.note-content {
  font-size: 13px;
  color: var(--tf-text-primary);
  line-height: 1.5;
}

/* 数字卡片 */
.widget-number-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 4px;
}

.number-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--tf-text-primary);
  line-height: 1.1;
}

.number-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* 占位内容 */
.placeholder-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
}

.placeholder-icon {
  font-size: 28px;
  opacity: 0.5;
}

.placeholder-text {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.danger-option {
  color: var(--tf-danger) !important;
}
</style>
