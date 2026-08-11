<template>
  <!--
    ===== PeopleTab — 人员视图 =====
    显示当前用户（或选中用户）的周/月工时日历。
    支持按用户、项目、工作类型筛选。
  -->
  <!-- 日期导航栏 -->
  <TimesheetDateBar
    :date-range-label="dateRangeLabel"
    :total-time-label="totalTimeLabel"
    :view-mode="viewMode"
    @navigate="$emit('navigate', $event)"
    @go-today="$emit('goToday')"
    @update:view-mode="$emit('update:viewMode', $event)"
  >
    <template #actions>
      <a-button type="primary" size="small" @click="$emit('openAdd')">添加已花费时间</a-button>
    </template>
  </TimesheetDateBar>

  <!-- 用户选择 + 筛选器 -->
  <div class="timesheet-controls">
    <div class="controls-left">
      <div class="user-selector-area">
        <template v-if="canViewOthers">
          <a-select
            :model-value="selectedUserId"
            placeholder="选择用户"
            allow-search
            allow-clear
            style="width: 220px"
            :filter-option="false"
            @search="$emit('searchUsers', $event)"
            @change="$emit('userChange', $event)"
            @clear="$emit('userChange', undefined)"
          >
            <a-option v-for="u in selectableUsers" :key="u.id" :value="u.id" :label="u.displayName || u.username">
              <div class="user-option">
                <UserAvatar :name="u.displayName || u.username" :size="20" />
                <span class="user-option-name">{{ u.displayName || u.username }}</span>
                <span v-if="u.id === currentUserId" class="user-option-self">(我)</span>
              </div>
            </a-option>
          </a-select>
        </template>
        <template v-else>
          <div class="user-selector-static">
            <UserAvatar :name="currentUserName" :size="20" />
            <span class="user-name">{{ currentUserName }}</span>
          </div>
        </template>
      </div>

      <div class="filters">
        <span class="filter-label">项目:</span>
        <a-select
          :model-value="filterProjectId"
          placeholder="全部"
          allow-clear
          allow-search
          style="width: 160px"
          size="small"
          @change="(v: any) => $emit('filterChange', { projectId: v ?? undefined, workType: filterWorkType })"
        >
          <a-option v-for="p in filterProjects" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>

        <span class="filter-label">工作类型:</span>
        <a-select
          :model-value="filterWorkType"
          placeholder="全部"
          allow-clear
          style="width: 140px"
          size="small"
          @change="(v: any) => $emit('filterChange', { projectId: filterProjectId, workType: v ?? undefined })"
        >
          <a-option v-for="wt in filterWorkTypes" :key="wt.id" :value="wt.id">
            <span v-if="wt.color" class="attr-value-dot" :style="{ background: wt.color }"></span>
            {{ wt.name }}
          </a-option>
        </a-select>

        <a v-if="filterProjectId || filterWorkType" class="filter-reset" @click="$emit('resetFilters')">重置</a>
      </div>
    </div>
  </div>

  <!-- 周/月视图 -->
  <WeekGrid
    v-if="viewMode === 'week'"
    :week-days="weekDays"
    :entries="entries"
    :show-quota="true"
    :quota-minutes="quotaMinutes"
    :quota-text="quotaText"
    @day-click="$emit('openAdd', $event)"
    @entry-click="$emit('openEdit', $event)"
    @issue-click="onIssueClick"
  />
  <MonthGrid
    v-else
    :month-days="monthDays"
    :entries="entries"
    @day-click="$emit('openAdd', $event)"
    @entry-click="$emit('openEdit', $event)"
  />

  <!-- 加载中 -->
  <div v-if="loading" class="loading-overlay"><a-spin :size="24" /></div>
</template>

<script setup lang="ts">
/**
 * PeopleTab — 人员视图
 *
 * 职责：
 * - 渲染日期导航栏（带「添加工时」按钮）
 * - 渲染用户选择器 + 项目/工作类型筛选器
 * - 渲染 WeekGrid 或 MonthGrid
 *
 * Props: 所有数据来自父级 TimesheetView
 * Emits: 用户操作通知父级
 */
import { useRouter } from 'vue-router'
import TimesheetDateBar from './TimesheetDateBar.vue'
import WeekGrid from './WeekGrid.vue'
import MonthGrid from './MonthGrid.vue'
import { UserAvatar } from '@/components/base'
import type { WeekDayInfo, MonthDayInfo } from '@/utils/timesheet'
import type { TimeEntryVO, TimeEntryUserVO } from '@/api/timeEntry'

defineProps<{
  dateRangeLabel: string
  totalTimeLabel: string
  viewMode: 'week' | 'month'
  weekDays: WeekDayInfo[]
  monthDays: MonthDayInfo[]
  entries: TimeEntryVO[]
  quotaMinutes: number
  quotaText: string
  loading: boolean
  currentUserId: string | undefined
  currentUserName: string
  canViewOthers: boolean
  selectedUserId: string | undefined
  selectableUsers: TimeEntryUserVO[]
  filterProjectId: string | undefined
  filterWorkType: string | undefined
  filterProjects: { id: string; name: string; key: string }[]
  filterWorkTypes: { id: string; name: string; color?: string }[]
}>()

defineEmits<{
  navigate:      [delta: number]
  goToday:       []
  'update:viewMode': [value: 'week' | 'month']
  openAdd:       [date?: string]
  openEdit:      [entry: TimeEntryVO]
  searchUsers:   [keyword: string]
  userChange:    [value: any]
  filterChange:  [filters: { projectId?: string; workType?: string }]
  resetFilters:  []
}>()

const router = useRouter()

function onIssueClick(entry: TimeEntryVO) {
  if (!entry.issueDeleted) {
    router.push(`/issues/${entry.issueKey || entry.issueId}`)
  }
}
</script>

<style scoped>
.timesheet-controls { padding: 12px 24px; display: flex; align-items: center; }
.controls-left { display: flex; flex-direction: column; gap: 6px; }
.user-selector-area { display: flex; align-items: center; }
.user-selector-static { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.user-option { display: flex; align-items: center; gap: 8px; }
.user-option-name { font-size: 13px; }
.user-option-self { font-size: 11px; color: var(--tf-text-tertiary); }
.filters { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.filter-label { color: var(--tf-text-tertiary); white-space: nowrap; }
.filter-reset { font-size: 12px; color: var(--tf-accent); cursor: pointer; }
.attr-value-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }
.loading-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: var(--tf-fill-light); z-index: 10; pointer-events: none; }
</style>
