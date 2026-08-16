<template>
  <div class="sprint-progress-section" v-if="sprint.totalIssues > 0">
    <!-- ===== 多段进度条：按实际工作流状态着色 ===== -->
    <div class="progress-bar-container">
      <div class="progress-bar">
        <template v-if="hasBreakdown">
          <a-tooltip
            v-for="item in sprint.statusBreakdown"
            :key="item.statusId"
            :content="`${item.statusName}: ${item.count}`"
            position="top"
            mini
          >
            <div
              class="progress-segment"
              :style="{ width: getStatusPercent(item.count) + '%', backgroundColor: item.statusColor }"
              @click.stop="$emit('viewStatus', item.statusId, item.statusName)"
            ></div>
          </a-tooltip>
        </template>
        <template v-else>
          <a-tooltip :content="`已完成: ${sprint.doneIssues}`" position="top" mini>
            <div
              class="progress-segment done"
              :style="{ width: getProgressPercent('done') + '%' }"
            ></div>
          </a-tooltip>
          <a-tooltip :content="`进行中: ${sprint.inProgressIssues}`" position="top" mini>
            <div
              class="progress-segment in-progress"
              :style="{ width: getProgressPercent('inProgress') + '%' }"
            ></div>
          </a-tooltip>
          <a-tooltip :content="`待办: ${sprint.todoIssues}`" position="top" mini>
            <div
              class="progress-segment todo"
              :style="{ width: getProgressPercent('todo') + '%' }"
            ></div>
          </a-tooltip>
        </template>
      </div>
      <span class="progress-percent">{{ completionPercent }}%</span>
    </div>

    <!-- ===== 按状态细分统计标签 ===== -->
    <div class="progress-stats" v-if="hasBreakdown">
      <span
        v-for="item in visibleBreakdownItems"
        :key="item.statusId"
        class="stat-item stat-clickable"
        @click.stop="$emit('viewStatus', item.statusId, item.statusName)"
      >
        <span class="stat-dot" :style="{ backgroundColor: item.statusColor }"></span>
        {{ item.statusName }} {{ item.count }}
      </span>
      <span class="stat-item total stat-clickable" @click.stop="$emit('viewTotal')">
        共 {{ sprint.totalIssues }} 个工单
      </span>
      <span
        v-if="sprint.overdueIssues && sprint.overdueIssues > 0 && sprint.status === 'active'"
        class="stat-item overdue stat-clickable"
        @click.stop="$emit('viewOverdue')"
      >
        <span class="stat-dot"></span>
        逾期 {{ sprint.overdueIssues }}
      </span>
      <span
        v-if="sprint.unassignedIssues && sprint.unassignedIssues > 0"
        class="stat-item unassigned stat-clickable"
        @click.stop="$emit('viewUnassigned')"
      >
        <span class="stat-dot"></span>
        未分配 {{ sprint.unassignedIssues }}
      </span>
      <span v-if="sprint.totalEstimatedHours && sprint.totalEstimatedHours > 0" class="stat-item estimation">
        <icon-clock-circle class="stat-icon" />
        <template v-if="sprint.completedEstimatedHours !== undefined">
          已完成 {{ formatHours(sprint.completedEstimatedHours) }} / 共 {{ formatHours(sprint.totalEstimatedHours) }}
        </template>
        <template v-else>
          共 {{ formatHours(sprint.totalEstimatedHours) }}
        </template>
      </span>
    </div>

    <!-- ===== 旧的三级统计标签（fallback，无 statusBreakdown 时） ===== -->
    <div class="progress-stats" v-else>
      <span class="stat-item done stat-clickable" @click.stop="$emit('viewCategory', 'done')">
        <span class="stat-dot"></span>
        完成 {{ sprint.doneIssues }}
      </span>
      <span
        v-if="showInProgress"
        class="stat-item in-progress stat-clickable"
        @click.stop="$emit('viewCategory', 'in_progress')"
      >
        <span class="stat-dot"></span>
        进行中 {{ sprint.inProgressIssues }}
      </span>
      <span
        v-if="showTodo"
        class="stat-item todo stat-clickable"
        @click.stop="$emit('viewCategory', 'open')"
      >
        <span class="stat-dot"></span>
        待办 {{ sprint.todoIssues }}
      </span>
      <span class="stat-item total stat-clickable" @click.stop="$emit('viewTotal')">
        共 {{ sprint.totalIssues }} 个工单
      </span>
      <span
        v-if="sprint.overdueIssues && sprint.overdueIssues > 0 && sprint.status === 'active'"
        class="stat-item overdue stat-clickable"
        @click.stop="$emit('viewOverdue')"
      >
        <span class="stat-dot"></span>
        逾期 {{ sprint.overdueIssues }}
      </span>
      <span
        v-if="sprint.unassignedIssues && sprint.unassignedIssues > 0"
        class="stat-item unassigned stat-clickable"
        @click.stop="$emit('viewUnassigned')"
      >
        <span class="stat-dot"></span>
        未分配 {{ sprint.unassignedIssues }}
      </span>
      <span v-if="sprint.totalEstimatedHours && sprint.totalEstimatedHours > 0" class="stat-item estimation">
        <icon-clock-circle class="stat-icon" />
        <template v-if="sprint.completedEstimatedHours !== undefined">
          已完成 {{ formatHours(sprint.completedEstimatedHours) }} / 共 {{ formatHours(sprint.totalEstimatedHours) }}
        </template>
        <template v-else>
          共 {{ formatHours(sprint.totalEstimatedHours) }}
        </template>
      </span>
    </div>
  </div>
  <div class="sprint-no-issues" v-else-if="showEmptyHint">
    <!-- ===== 空进度条：保持与有工单卡片一致的视觉结构 ===== -->
    <div class="progress-bar-container">
      <div class="progress-bar">
        <!-- 空进度条，无填充 -->
      </div>
      <span class="progress-percent">0%</span>
    </div>
    <div class="progress-stats">
      <span class="stat-item total">共 0 个工单</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * SprintProgress — Sprint 卡片进度统计组件
 *
 * 职责：
 * - 展示多段进度条，按实际工作流状态着色（优先使用 statusBreakdown）
 * - 展示每个状态的工单计数标签，支持点击跳转筛选
 * - 向后兼容：无 statusBreakdown 时降级为三级（完成/进行中/待办）
 *
 * 对外接口：
 * - Props：sprint 数据、显示控制
 * - Emits：viewCategory（旧三级）、viewStatus（新按状态跳转）、viewTotal/viewOverdue/viewUnassigned
 */
import { computed } from 'vue'
import { IconClockCircle } from '@arco-design/web-vue/es/icon'
import type { SprintVO } from '@/api/types'

const props = withDefaults(defineProps<{
  sprint: SprintVO
  /** 是否在无工单时显示"暂无工单"提示（planned 卡片需要） */
  showEmptyHint?: boolean
  /** 是否始终显示进行中/待办（active 默认显示，completed 仅 >0 时显示） */
  alwaysShowDetails?: boolean
}>(), {
  showEmptyHint: false,
  alwaysShowDetails: true
})

defineEmits<{
  (e: 'viewCategory', category: string): void
  (e: 'viewStatus', statusId: string, statusName: string): void
  (e: 'viewTotal'): void
  (e: 'viewOverdue'): void
  (e: 'viewUnassigned'): void
}>()

// ===== 是否有按状态细分数据 =====
const hasBreakdown = computed(() =>
  props.sprint.statusBreakdown && props.sprint.statusBreakdown.length > 0
)

// ===== 细分数据中可见的状态项（count > 0 的项；或 alwaysShowDetails 时全部显示） =====
const visibleBreakdownItems = computed(() => {
  if (!props.sprint.statusBreakdown) return []
  if (props.alwaysShowDetails) return props.sprint.statusBreakdown
  return props.sprint.statusBreakdown.filter(item => item.count > 0)
})

// ===== 旧三级模式的控制 =====
const showInProgress = computed(() =>
  props.alwaysShowDetails || props.sprint.inProgressIssues > 0
)

const showTodo = computed(() =>
  props.alwaysShowDetails || props.sprint.todoIssues > 0
)

const completionPercent = computed(() => {
  if (props.sprint.totalIssues === 0) return 0
  return Math.round((props.sprint.doneIssues / props.sprint.totalIssues) * 100)
})

function getStatusPercent(count: number): number {
  if (props.sprint.totalIssues === 0) return 0
  return (count / props.sprint.totalIssues) * 100
}

function getProgressPercent(type: 'done' | 'inProgress' | 'todo'): number {
  if (props.sprint.totalIssues === 0) return 0
  const map = {
    done: props.sprint.doneIssues,
    inProgress: props.sprint.inProgressIssues,
    todo: props.sprint.todoIssues
  }
  return (map[type] / props.sprint.totalIssues) * 100
}

function formatHours(hours: number): string {
  if (hours === 0) return '0h'
  if (hours >= 1) return `${Math.round(hours * 10) / 10}h`
  return `${Math.round(hours * 60)}m`
}
</script>

<style scoped>
.sprint-progress-section {
  margin-top: 12px;
}

.progress-bar-container {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: var(--color-fill-2);
  border-radius: 3px;
  overflow: hidden;
  display: flex;
}

.progress-segment {
  height: 100%;
  transition: width 0.3s;
  cursor: pointer;
}

.progress-segment:hover {
  opacity: 0.8;
}

.progress-segment.done {
  background: var(--tf-success);
}

.progress-segment.in-progress {
  background: var(--tf-accent);
}

.progress-segment.todo {
  background: var(--color-text-4);
}

.progress-percent {
  font-size: 11px;
  color: var(--color-text-3);
  min-width: 32px;
  text-align: right;
}

.progress-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-3);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.stat-clickable {
  cursor: pointer;
  border-radius: 4px;
  padding: 2px 4px;
  margin: -2px -4px;
  transition: background 0.15s;
}

.stat-clickable:hover {
  background: var(--color-fill-2);
}

.stat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.stat-item.done .stat-dot {
  background: var(--tf-success);
}

.stat-item.in-progress .stat-dot {
  background: var(--tf-accent);
}

.stat-item.todo .stat-dot {
  background: var(--color-fill-4);
}

.stat-item.overdue .stat-dot {
  background: rgb(var(--danger-6));
}

.stat-item.unassigned .stat-dot {
  background: rgb(var(--warning-6));
}

.stat-icon {
  font-size: 12px;
}

.sprint-no-issues {
  margin-top: 12px;
  padding: 8px 0;
}

.no-issues-text {
  font-size: 12px;
  color: var(--color-text-4);
}
</style>
