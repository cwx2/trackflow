<template>
  <div class="sprint-no-active-state">
    <!-- 主提示区域 -->
    <EmptyState
      icon="trophy"
      title="当前项目没有进行中的迭代"
      description="所有迭代均已完成。创建新迭代来规划下一阶段的团队工作。"
    >
      <template #action>
        <div class="no-active-actions">
          <a-button v-if="canCreate" type="primary" size="small" @click="$emit('create')">
            <template #icon><icon-plus /></template>
            创建新迭代
          </a-button>
          <a-button size="small" @click="$emit('viewBacklog')">
            <template #icon><icon-unordered-list /></template>
            查看 Backlog
          </a-button>
        </div>
      </template>
    </EmptyState>

    <!-- 遗留工单警告 -->
    <div v-if="lingeringIssueCount > 0" class="lingering-warning">
      <icon-exclamation-circle class="lingering-icon" />
      <span class="lingering-text">
        有 <strong>{{ lingeringIssueCount }}</strong> 个工单仍在已完成迭代中未处理
      </span>
      <a-button size="mini" type="text" @click="$emit('viewLingering')">
        查看详情
        <template #icon><icon-right /></template>
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { EmptyState } from '@/components/base'
import { IconPlus, IconUnorderedList, IconExclamationCircle, IconRight } from '@arco-design/web-vue/es/icon'

defineProps<{
  canCreate: boolean
  lingeringIssueCount: number
}>()

defineEmits<{
  create: []
  viewBacklog: []
  viewLingering: []
}>()
</script>

<style scoped>
.sprint-no-active-state {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.no-active-actions {
  display: flex;
  gap: 8px;
}

.lingering-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 6px;
  background: var(--tf-warning-bg, rgba(255, 180, 0, 0.08));
  border: 1px solid rgba(var(--warning-6), 0.25);
}

.lingering-icon {
  font-size: 16px;
  color: var(--color-warning-6);
  flex-shrink: 0;
}

.lingering-text {
  flex: 1;
  font-size: 13px;
  color: var(--color-text-1);
  line-height: 1.4;
}

.lingering-text strong {
  color: var(--color-warning-light-4, var(--color-warning-6));
}
</style>
