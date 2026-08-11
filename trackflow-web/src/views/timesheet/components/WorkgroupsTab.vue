<template>
  <!--
    ===== WorkgroupsTab — 工作群组视图 =====
    展示所有工作群组的成员工时，支持展开/折叠查看成员明细。
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

  <!-- 空状态 -->
  <EmptyState
    v-if="groupSummaries.length === 0 && !loading"
    icon="user-group"
    title="暂无工作组数据"
    description="当前系统中没有工作组，或工作组内尚无成员。管理员可在「系统管理 → 用户组」中创建工作组并分配成员。"
  />

  <!-- 工作组列表 -->
  <div v-else class="group-overview">
    <div v-for="group in groupSummaries" :key="group.groupId" class="group-block">
      <!-- 标题行（点击展开/折叠） -->
      <div
        class="group-header"
        :class="{ expanded: expandedGroups.has(group.groupId) }"
        @click="$emit('toggleExpand', group.groupId)"
      >
        <div class="group-header-left">
          <icon-down v-if="expandedGroups.has(group.groupId)" class="group-expand-icon" />
          <icon-right v-else class="group-expand-icon" />
          <icon-user-group class="group-icon" />
          <span class="group-name">{{ group.groupName }}</span>
          <span class="group-member-count">{{ group.memberCount }} 人</span>
        </div>
        <div class="group-header-right">
          <span class="group-total-dur">{{ formatDuration(group.totalDuration) }}</span>
        </div>
      </div>

      <!-- 展开后的成员列表 -->
      <div v-if="expandedGroups.has(group.groupId)" class="group-members">
        <div v-for="member in group.members" :key="member.userId" class="member-row">
          <div class="member-info">
            <UserAvatar :name="member.displayName || member.username || '?'" :size="24" />
            <span class="member-name">{{ member.displayName || member.username }}</span>
          </div>
          <div class="member-bar-area">
            <div v-if="member.entries && member.entries.length > 0" class="member-entries">
              <span
                v-for="entry in member.entries"
                :key="entry.id"
                class="member-entry-chip"
                :title="`${entry.issueKey || entry.issueId} ${entry.workDate} ${formatDuration(entry.duration || 0)}${entry.description ? ' - ' + entry.description : ''}`"
                @click="$emit('openEdit', entry)"
              >
                {{ entry.issueKey || '?' }} {{ formatDuration(entry.duration || 0) }}
              </span>
            </div>
            <span v-else class="member-no-entries">无工时记录</span>
          </div>
          <div class="member-total">
            <span :class="member.totalDuration > 0 ? 'member-total-dur' : 'member-total-zero'">
              {{ formatDuration(member.totalDuration) }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div v-if="loading" class="loading-overlay"><a-spin :size="24" /></div>
</template>

<script setup lang="ts">
/**
 * WorkgroupsTab — 工作群组视图
 *
 * Props: 所有数据来自父级 TimesheetView
 * Emits: 用户操作通知父级
 */
import { formatDuration } from '@/utils/duration'
import TimesheetDateBar from './TimesheetDateBar.vue'
import { EmptyState, UserAvatar } from '@/components/base'
import { IconDown, IconRight, IconUserGroup } from '@arco-design/web-vue/es/icon'
import type { GroupTimeSummaryVO, TimeEntryVO } from '@/api/timeEntry'

defineProps<{
  dateRangeLabel: string
  totalTimeLabel: string
  viewMode: 'week' | 'month'
  groupSummaries: GroupTimeSummaryVO[]
  expandedGroups: Set<string>
  loading: boolean
}>()

defineEmits<{
  navigate:     [delta: number]
  goToday:      []
  'update:viewMode': [value: 'week' | 'month']
  toggleExpand: [groupId: string]
  openEdit:     [entry: TimeEntryVO]
}>()
</script>

<style scoped>
.group-overview { flex: 1; overflow-y: auto; padding: 0 24px 24px; display: flex; flex-direction: column; gap: 8px; }
.group-block { border: 1px solid var(--tf-border-light); border-radius: var(--tf-radius-md); overflow: hidden; }
.group-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: var(--tf-bg-elevated); cursor: pointer; transition: background 0.15s; user-select: none; }
.group-header:hover { background: var(--tf-bg-hover); }
.group-header.expanded { border-bottom: 1px solid var(--tf-border-light); }
.group-header-left { display: flex; align-items: center; gap: 8px; }
.group-expand-icon { font-size: 10px; color: var(--tf-text-tertiary); width: 12px; }
.group-icon { font-size: 14px; }
.group-name { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); }
.group-member-count { font-size: 11px; color: var(--tf-text-tertiary); background: var(--tf-bg-surface); padding: 1px 6px; border-radius: 10px; }
.group-header-right { display: flex; align-items: center; }
.group-total-dur { font-size: 14px; font-weight: 600; color: var(--tf-accent); }
.group-members { display: flex; flex-direction: column; }
.member-row { display: flex; align-items: center; gap: 12px; padding: 8px 16px; border-bottom: 1px solid var(--tf-border-light); }
.member-row:last-child { border-bottom: none; }
.member-info { display: flex; align-items: center; gap: 8px; min-width: 140px; max-width: 140px; }
.member-name { font-size: 13px; color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.member-bar-area { flex: 1; display: flex; flex-wrap: wrap; gap: 4px; min-height: 24px; align-items: center; }
.member-entries { display: flex; flex-wrap: wrap; gap: 4px; }
.member-entry-chip { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: var(--tf-accent-bg); color: var(--tf-accent); cursor: pointer; transition: opacity 0.15s; white-space: nowrap; }
.member-entry-chip:hover { opacity: 0.8; }
.member-no-entries { font-size: 12px; color: var(--tf-text-tertiary); font-style: italic; }
.member-total { min-width: 48px; text-align: right; }
.member-total-dur { font-size: 13px; font-weight: 600; color: var(--tf-text-primary); }
.member-total-zero { font-size: 13px; color: var(--tf-text-tertiary); }
</style>
