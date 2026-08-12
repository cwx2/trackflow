<template>
  <div v-if="statistics" class="section statistics-section">
    <h2 class="section-title">项目统计</h2>
    <div class="stats-cards">
      <div class="stat-card">
        <span class="stat-value">{{ statistics.totalIssues }}</span>
        <span class="stat-label">工单总数</span>
      </div>
      <div class="stat-card">
        <span class="stat-value stat-open">{{ statistics.openIssues }}</span>
        <span class="stat-label">未解决</span>
      </div>
      <div class="stat-card">
        <span class="stat-value stat-done">{{ statistics.closedIssues }}</span>
        <span class="stat-label">已关闭</span>
      </div>
      <div class="stat-card">
        <span class="stat-value stat-rate">{{ statistics.completionRate }}%</span>
        <span class="stat-label">完成率</span>
      </div>
      <div class="stat-card">
        <span class="stat-value stat-new">+{{ statistics.createdThisWeek }}</span>
        <span class="stat-label">本周新建</span>
      </div>
      <div class="stat-card">
        <span class="stat-value stat-closed-week">+{{ statistics.closedThisWeek }}</span>
        <span class="stat-label">本周关闭</span>
      </div>
    </div>
  </div>

  <!-- 工单状态分布 -->
  <div v-if="statistics && statistics.statusDistribution.length > 0" class="section">
    <h2 class="section-title">状态分布</h2>
    <div class="status-bar-container">
      <div class="status-bar">
        <div
          v-for="item in statistics.statusDistribution"
          :key="item.statusId"
          class="status-bar-segment"
          :style="{ width: getStatusPercent(item.count) + '%', background: item.statusColor || '#6b7280' }"
          :title="`${item.statusName}: ${item.count} (${getStatusPercent(item.count).toFixed(1)}%)`"
        ></div>
      </div>
      <div class="status-legend">
        <div v-for="item in statistics.statusDistribution" :key="item.statusId" class="legend-item">
          <span class="legend-dot" :style="{ background: item.statusColor || '#6b7280' }"></span>
          <span class="legend-name">{{ item.statusName }}</span>
          <span class="legend-count">{{ item.count }}</span>
        </div>
      </div>
    </div>
  </div>

  <!-- 当前 Sprint 进度 -->
  <div v-if="statistics?.activeSprint" class="section">
    <h2 class="section-title">当前 Sprint</h2>
    <div class="sprint-card">
      <div class="sprint-header">
        <span class="sprint-name">{{ statistics.activeSprint.name }}</span>
        <span class="sprint-remaining">
          {{ statistics.activeSprint.remainingDays > 0 ? `剩余 ${statistics.activeSprint.remainingDays} 天` : '已到期' }}
        </span>
      </div>
      <div class="sprint-progress">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: sprintProgressPercent + '%' }"></div>
        </div>
        <span class="progress-text">
          {{ statistics.activeSprint.completedIssues }}/{{ statistics.activeSprint.totalIssues }} 已完成
          ({{ sprintProgressPercent.toFixed(0) }}%)
        </span>
      </div>
      <div class="sprint-dates">
        <span>{{ formatSprintDate(statistics.activeSprint.startDate) }}</span>
        <span>→</span>
        <span>{{ formatSprintDate(statistics.activeSprint.endDate) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ProjectStatisticsVO } from '@/api/types'

const props = defineProps<{
  statistics: ProjectStatisticsVO | null
}>()

const sprintProgressPercent = computed(() => {
  if (!props.statistics?.activeSprint) return 0
  const { totalIssues, completedIssues } = props.statistics.activeSprint
  if (totalIssues === 0) return 0
  return (completedIssues / totalIssues) * 100
})

function getStatusPercent(count: number): number {
  if (!props.statistics || props.statistics.totalIssues === 0) return 0
  return (count / props.statistics.totalIssues) * 100
}

function formatSprintDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>

<style scoped>
.section { margin-bottom: 32px; }
.section-title { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); margin: 0 0 12px; }

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 12px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
  transition: background 0.15s;
}
.stat-card:hover { background: var(--tf-bg-hover); }

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--tf-text-primary);
  line-height: 1.2;
  letter-spacing: -0.3px;
}
.stat-value.stat-open { color: var(--tf-accent); }
.stat-value.stat-done { color: var(--tf-success); }
.stat-value.stat-rate { color: var(--tf-warning); }
.stat-value.stat-new { color: var(--tf-accent); }
.stat-value.stat-closed-week { color: var(--tf-success); }

.stat-label { font-size: 11px; color: var(--tf-text-tertiary); font-weight: 500; }

/* Status Distribution */
.status-bar-container { display: flex; flex-direction: column; gap: 12px; }
.status-bar {
  display: flex;
  height: 10px;
  border-radius: 5px;
  overflow: hidden;
  background: var(--tf-bg-surface);
}
.status-bar-segment { transition: width 0.3s ease; min-width: 3px; }
.status-bar-segment:first-child { border-radius: 5px 0 0 5px; }
.status-bar-segment:last-child { border-radius: 0 5px 5px 0; }
.status-bar-segment:only-child { border-radius: 5px; }

.status-legend { display: flex; flex-wrap: wrap; gap: 12px 20px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.legend-dot { width: 8px; height: 8px; border-radius: 2px; flex-shrink: 0; }
.legend-name { color: var(--tf-text-secondary); }
.legend-count { color: var(--tf-text-tertiary); font-weight: 500; }

/* Sprint Card */
.sprint-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
}
.sprint-header { display: flex; align-items: center; justify-content: space-between; }
.sprint-name { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); }
.sprint-remaining {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 2px 8px;
  border-radius: 3px;
}
.sprint-progress { display: flex; flex-direction: column; gap: 6px; }
.progress-bar { height: 6px; border-radius: 3px; background: var(--tf-bg-hover); overflow: hidden; }
.progress-fill { height: 100%; border-radius: 3px; background: var(--tf-success); transition: width 0.3s ease; }
.progress-text { font-size: 12px; color: var(--tf-text-secondary); }
.sprint-dates { display: flex; align-items: center; gap: 8px; font-size: 11px; color: var(--tf-text-tertiary); }
</style>
