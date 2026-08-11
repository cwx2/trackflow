<template>
  <!--
    ===== ProjectsTab — 项目视图 =====
    展示所有可见项目的工时汇总，或选中某个项目后显示明细日历。
  -->
  <!-- 日期导航栏 -->
  <TimesheetDateBar
    :date-range-label="dateRangeLabel"
    :total-time-label="totalTimeLabel"
    :view-mode="viewMode"
    @navigate="$emit('navigate', $event)"
    @go-today="$emit('goToday')"
    @update:view-mode="$emit('update:viewMode', $event)"
  />

  <!-- 项目选择器 -->
  <div class="timesheet-controls">
    <div class="controls-left">
      <div class="project-selector">
        <a-select
          :model-value="selectedProjectId"
          placeholder="选择项目查看明细"
          allow-clear
          allow-search
          style="width: 260px"
          @change="$emit('projectChange', $event)"
          @clear="$emit('projectChange', undefined)"
        >
          <a-option v-for="p in projectSummaries" :key="p.projectId" :value="p.projectId">
            {{ p.projectKey }} - {{ p.projectName }}
          </a-option>
        </a-select>
      </div>
      <div class="filters">
        <span class="filter-label">汇总范围:</span>
        <span class="filter-value">{{ selectedProjectId ? '项目明细' : '所有可见项目' }}</span>
      </div>
    </div>
  </div>

  <!-- 加载失败 -->
  <EmptyState
    v-if="loadError && !loading"
    type="error"
    icon="exclamation-circle"
    title="加载失败"
    :description="loadError"
  >
    <template #action>
      <a-button type="primary" size="small" @click="$emit('refresh')">重试</a-button>
    </template>
  </EmptyState>

  <!-- 项目汇总列表（未选择项目时） -->
  <div v-else-if="!selectedProjectId" class="project-overview">
    <EmptyState
      v-if="projectSummaries.length === 0 && !loading"
      icon="bar-chart"
      title="暂无项目工时数据"
      description="当前日期范围内您可见的项目没有工时记录"
    />
    <div v-else class="project-summary-list">
      <div
        v-for="p in projectSummaries"
        :key="p.projectId"
        class="project-summary-card"
        @click="$emit('projectChange', p.projectId)"
      >
        <div class="project-summary-left">
          <span class="project-key-badge">{{ p.projectKey }}</span>
          <span class="project-name-text">{{ p.projectName }}</span>
        </div>
        <div class="project-summary-right">
          <span class="project-total-dur">{{ formatDuration(p.totalDuration) }}</span>
          <span class="project-entry-count">{{ p.entries.length }} 条记录</span>
        </div>
      </div>
    </div>
  </div>

  <!-- 项目明细（选择了项目时） -->
  <div v-else class="project-detail-view">
    <WeekGrid
      v-if="viewMode === 'week'"
      :week-days="weekDays"
      :entries="projectEntries"
      :show-user="true"
    />
    <MonthGrid
      v-else
      :month-days="monthDays"
      :entries="projectEntries"
    />
  </div>

  <div v-if="loading" class="loading-overlay"><a-spin :size="24" /></div>
</template>

<script setup lang="ts">
/**
 * ProjectsTab — 项目视图
 *
 * Props: 所有数据来自父级 TimesheetView
 * Emits: 用户操作通知父级
 */
import { formatDuration } from '@/utils/duration'
import TimesheetDateBar from './TimesheetDateBar.vue'
import WeekGrid from './WeekGrid.vue'
import MonthGrid from './MonthGrid.vue'
import { EmptyState } from '@/components/base'
import type { WeekDayInfo, MonthDayInfo } from '@/utils/timesheet'
import type { TimeEntryVO, ProjectTimeSummaryVO } from '@/api/timeEntry'

defineProps<{
  dateRangeLabel: string
  totalTimeLabel: string
  viewMode: 'week' | 'month'
  weekDays: WeekDayInfo[]
  monthDays: MonthDayInfo[]
  projectEntries: TimeEntryVO[]
  projectSummaries: ProjectTimeSummaryVO[]
  selectedProjectId: string | undefined
  loading: boolean
  loadError: string | null
}>()

defineEmits<{
  navigate:       [delta: number]
  goToday:        []
  'update:viewMode': [value: 'week' | 'month']
  projectChange:  [projectId: any]
  refresh:        []
}>()
</script>

<style scoped>
.project-selector { display: flex; align-items: center; gap: 8px; }
.filters { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.filter-label { color: var(--tf-text-tertiary); white-space: nowrap; }
.filter-value { font-size: 12px; color: var(--tf-text-secondary); }

.project-overview { flex: 1; overflow-y: auto; padding: 0 24px 24px; }
.project-summary-list { display: flex; flex-direction: column; gap: 8px; }
.project-summary-card { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-radius: var(--tf-radius-md); background: var(--tf-bg-elevated); border: 1px solid var(--tf-border-light); cursor: pointer; transition: border-color 0.15s, background 0.1s; }
.project-summary-card:hover { border-color: var(--tf-accent); background: var(--tf-bg-hover); }
.project-summary-left { display: flex; align-items: center; gap: 10px; }
.project-key-badge { font-size: 11px; font-weight: 600; color: var(--tf-accent); background: var(--tf-accent-bg); padding: 2px 8px; border-radius: var(--tf-radius-sm); }
.project-name-text { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.project-summary-right { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.project-total-dur { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); }
.project-entry-count { font-size: 11px; color: var(--tf-text-tertiary); }
.project-detail-view { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
</style>
