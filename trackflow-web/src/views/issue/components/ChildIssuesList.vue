<template>
  <section class="section children-section" v-if="children.length > 0 || showProgress">
    <div class="section-head">
      <h3>子任务</h3>
      <span class="child-count">{{ children.length }}</span>
    </div>

    <!-- 进度条 -->
    <div v-if="progress" class="progress-bar-wrap">
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progress.percent + '%' }"></div>
      </div>
      <span class="progress-text">{{ progress.closed }}/{{ progress.total }} 完成 ({{ progress.percent }}%)</span>
    </div>

    <!-- 子任务列表 -->
    <div class="child-list">
      <div
        v-for="child in children"
        :key="child.id"
        class="child-item"
        :class="{ 'child-resolved': child.statusCategory === 'done' }"
      >
        <span class="child-status-dot" :style="{ background: child.statusColor || '#666' }"></span>
        <router-link :to="`/issues/${child.issueKey}`" class="child-key">{{ child.issueKey }}</router-link>
        <span class="child-title">{{ child.title }}</span>
        <span class="child-assignee" v-if="child.assigneeName">{{ child.assigneeName }}</span>
        <span class="child-status" :style="{ color: child.statusColor || '#666' }">{{ localizeStatusName(child.statusName) }}</span>
      </div>
    </div>

    <!-- 汇总工时 -->
    <div v-if="progress && (progress.aggregatedEstimate || progress.aggregatedSpent)" class="hours-summary">
      <span v-if="progress.aggregatedEstimate" class="hours-item">
        预估合计: {{ progress.aggregatedEstimate }}h
      </span>
      <span v-if="progress.aggregatedSpent" class="hours-item">
        花费合计: {{ progress.aggregatedSpent }}h
      </span>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ChildIssueVO, ChildProgressVO } from '@/api/types'
import { localizeStatusName } from '@/utils/fieldLabels'

defineProps<{
  children: ChildIssueVO[]
  progress?: ChildProgressVO | null
  showProgress?: boolean
}>()
</script>

<style scoped>
.children-section {
  margin-bottom: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--tf-border-light);
}

.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.section-head h3 {
  font-size: 11px;
  font-weight: 700;
  color: var(--tf-text-tertiary);
  margin: 0;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.child-count {
  font-size: 11px;
  color: var(--tf-text-muted);
  background: var(--tf-bg-elevated);
  padding: 1px 6px;
  border-radius: 10px;
}

/* Progress bar */
.progress-bar-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.progress-bar {
  flex: 1;
  height: 4px;
  background: var(--tf-bg-elevated);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--tf-accent, #58a6ff);
  border-radius: 2px;
  transition: width 300ms ease;
}

.progress-text {
  font-size: 11px;
  color: var(--tf-text-muted);
  white-space: nowrap;
}

/* Child list */
.child-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.child-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 3px;
  font-size: 12px;
  transition: background 150ms;
}

.child-item:hover {
  background: var(--tf-bg-hover);
}

.child-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.child-key {
  color: var(--tf-accent);
  font-weight: 500;
  text-decoration: none;
  flex-shrink: 0;
}

.child-key:hover {
  text-decoration: underline;
}

.child-title {
  color: var(--tf-text-secondary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.child-assignee {
  font-size: 11px;
  color: var(--tf-text-muted);
  flex-shrink: 0;
}

.child-status {
  font-size: 11px;
  font-weight: 500;
  flex-shrink: 0;
  margin-left: auto;
}

/* Resolved child issue styling */
.child-resolved .child-key {
  text-decoration: line-through;
  color: var(--tf-text-tertiary);
}

.child-resolved .child-title {
  color: var(--tf-text-tertiary);
}

.child-resolved .child-assignee {
  color: var(--tf-text-quaternary);
}

/* Hours summary */
.hours-summary {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--tf-border-light);
}

.hours-item {
  font-size: 11px;
  color: var(--tf-text-muted);
}
</style>
