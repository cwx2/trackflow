<template>
  <div class="assignee-distribution" v-if="visible">
    <div class="distribution-header" @click="toggleExpand">
      <!-- 折叠箭头：用 Arco 图标替代 ▾/▸ 字符 -->
      <icon-down v-if="expanded" class="toggle-icon" />
      <icon-right v-else class="toggle-icon" />
      <span class="distribution-title">负责人分布</span>
      <span class="distribution-summary" v-if="!expanded && distribution">
        {{ distribution.assignees.length }} 人
        <template v-if="distribution.unassignedCount > 0">
          · {{ distribution.unassignedCount }} 未分配
        </template>
      </span>
    </div>

    <div class="distribution-content" v-if="expanded">
      <DataContainer
        :loading="loading"
        :error="error ? '加载失败' : null"
        :is-empty="!!(distribution && distribution.assignees.length === 0 && distribution.unassignedCount === 0)"
        empty-title="暂无工单数据"
        :retry="loadDistribution"
        class="distribution-container"
      >
        <!-- 自定义 loading slot：更小更紧凑 -->
        <template #loading>
          <div class="distribution-loading">
            <a-spin :size="16" />
            <span>加载中…</span>
          </div>
        </template>

        <!-- 分布数据 -->
        <div v-if="distribution" class="distribution-body">
        <!-- 未分配行：用 UserAvatar 替代手写 ? 头像 -->
        <div
          v-if="distribution.unassignedCount > 0"
          class="assignee-row unassigned"
          @click="handleClickAssignee(null)"
        >
          <div class="assignee-info">
            <UserAvatar name="未分配" :size="22" />
            <span class="assignee-name">未分配</span>
          </div>
          <div class="assignee-stats">
            <span class="issue-count">{{ distribution.unassignedCount }}</span>
            <span class="hours-count" v-if="distribution.unassignedEstimatedHours > 0">{{ formatHours(distribution.unassignedEstimatedHours) }}</span>
            <div class="mini-bar">
              <div
                class="mini-bar-fill unassigned-fill"
                :style="{ width: getBarWidth(distribution.unassignedCount) + '%' }"
              ></div>
            </div>
          </div>
        </div>

        <!-- 负责人列表 -->
        <div
          v-for="assignee in distribution.assignees"
          :key="assignee.userId"
          class="assignee-row"
          @click="handleClickAssignee(assignee.userId)"
        >
          <div class="assignee-info">
            <UserAvatar :name="assignee.displayName" :size="22" />
            <span class="assignee-name">{{ assignee.displayName }}</span>
          </div>
          <div class="assignee-stats">
            <span class="issue-count">{{ assignee.issueCount }}</span>
            <span class="hours-count" v-if="assignee.estimatedHoursTotal > 0">{{ formatHours(assignee.estimatedHoursTotal) }}</span>
            <div class="mini-bar">
              <div
                class="mini-bar-segment done"
                :style="{ width: getSegmentWidth(assignee, 'done') + '%' }"
                :title="`完成: ${assignee.doneCount}`"
              ></div>
              <div
                class="mini-bar-segment in-progress"
                :style="{ width: getSegmentWidth(assignee, 'inProgress') + '%' }"
                :title="`进行中: ${assignee.inProgressCount}`"
              ></div>
              <div
                class="mini-bar-segment todo"
                :style="{ width: getSegmentWidth(assignee, 'todo') + '%' }"
                :title="`待办: ${assignee.todoCount}`"
              ></div>
            </div>
          </div>
        </div>

        </div>
      </DataContainer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { sprintApi } from '@/api'
import type { SprintAssigneeDistributionVO } from '@/api/types'
import { UserAvatar, DataContainer } from '@/components/base'
import { IconDown, IconRight } from '@arco-design/web-vue/es/icon'

const props = defineProps<{
  sprintId: string
  sprintName: string
  projectKey?: string
}>()

const emit = defineEmits<{
  (e: 'view-issues', filter: 'unassigned' | string): void
}>()

const visible = ref(true)
const expanded = ref(false)
const loading = ref(false)
const error = ref(false)
const distribution = ref<SprintAssigneeDistributionVO | null>(null)

watch(expanded, (val) => {
  if (val && !distribution.value && !loading.value) {
    loadDistribution()
  }
})

async function loadDistribution() {
  loading.value = true
  error.value = false
  try {
    const res = await sprintApi.assigneeDistribution(props.sprintId)
    distribution.value = res.data
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

function toggleExpand() {
  expanded.value = !expanded.value
}

function getBarWidth(count: number): number {
  if (!distribution.value || distribution.value.totalIssues === 0) return 0
  return (count / distribution.value.totalIssues) * 100
}

function getSegmentWidth(assignee: { issueCount: number; doneCount: number; inProgressCount: number; todoCount: number }, type: 'done' | 'inProgress' | 'todo'): number {
  if (assignee.issueCount === 0) return 0
  const map = { done: assignee.doneCount, inProgress: assignee.inProgressCount, todo: assignee.todoCount }
  return (map[type] / assignee.issueCount) * 100
}

function formatHours(hours: number): string {
  if (hours === 0) return '0h'
  if (hours >= 1) return `${Math.round(hours * 10) / 10}h`
  return `${Math.round(hours * 60)}m`
}

function handleClickAssignee(userId: string | null) {
  emit('view-issues', userId === null ? 'unassigned' : userId)
}
</script>

<style scoped>
.assignee-distribution {
  margin-top: 12px;
  border-top: 1px solid var(--color-border);
  padding-top: 8px;
}

.distribution-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  cursor: pointer;
  border-radius: 4px;
  user-select: none;
  transition: background 0.15s;
}
.distribution-header:hover { background: var(--color-fill-1); }

.toggle-icon {
  font-size: 11px;
  color: var(--color-text-3);
  width: 12px;
  flex-shrink: 0;
}

.distribution-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
}

.distribution-summary {
  font-size: 11px;
  color: var(--color-text-3);
  margin-left: auto;
}

.distribution-content { margin-top: 4px; }

.distribution-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 4px;
  font-size: 12px;
  color: var(--color-text-3);
}

/* DataContainer 在紧凑卡片场景里去掉最小高度 */
.distribution-container :deep(.data-container) {
  min-height: unset;
}

.distribution-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.assignee-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}
.assignee-row:hover { background: var(--color-fill-1); }
.assignee-row.unassigned {
  background: var(--tf-warning-bg);
  border: 1px solid rgba(var(--warning-6), 0.12);
}
.assignee-row.unassigned:hover { background: var(--tf-warning-bg); }

.assignee-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-shrink: 0;
}

.assignee-name {
  font-size: 12px;
  color: var(--color-text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100px;
}

.assignee-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: flex-end;
}

.issue-count {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
  min-width: 20px;
  text-align: right;
}

.hours-count {
  font-size: 11px;
  color: var(--color-text-3);
  min-width: 28px;
  text-align: right;
}

.mini-bar {
  width: 80px;
  height: 6px;
  background: var(--color-fill-2);
  border-radius: 3px;
  overflow: hidden;
  display: flex;
}

.mini-bar-fill.unassigned-fill {
  background: rgb(var(--warning-6));
  height: 100%;
  border-radius: 3px;
}

.mini-bar-segment { height: 100%; }
.mini-bar-segment.done { background: var(--tf-success); }
.mini-bar-segment.in-progress { background: var(--tf-accent); }
.mini-bar-segment.todo { background: var(--color-fill-3); }
</style>
