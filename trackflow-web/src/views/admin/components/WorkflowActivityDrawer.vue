<template>
  <a-drawer
    :visible="visible"
    :width="520"
    title="工作流变更历史"
    @cancel="$emit('update:visible', false)"
    unmount-on-close
  >
    <!-- 筛选区域 -->
    <div class="filter-bar">
      <a-range-picker
        v-model="dateRange"
        style="width: 240px"
        size="small"
        @change="onFilterChange"
      />
      <a-select
        v-model="filterUserId"
        placeholder="操作人"
        style="width: 140px"
        size="small"
        allow-clear
        @change="onFilterChange"
      >
        <a-option v-for="u in users" :key="u.id" :value="u.id">
          {{ u.displayName }}
        </a-option>
      </a-select>
    </div>

    <!-- 活动列表 -->
    <DataContainer
      :loading="loading"
      :is-empty="activities.length === 0"
      empty-title="暂无变更记录"
      empty-description="保存工作流配置后，变更历史将显示在这里。"
    >
      <div class="activity-list">
        <div
          v-for="activity in activities"
          :key="activity.id"
          class="activity-item"
        >
          <div class="activity-header">
            <span class="activity-user">{{ activity.userDisplayName }}</span>
            <span class="activity-time">{{ formatDateTime(activity.createdAt) }}</span>
          </div>
          <div class="activity-context">
            <span class="context-tag">{{ activity.roleName || '—' }}</span>
            <span class="context-tag">{{ activity.issueType === '*' ? '所有类型' : activity.issueType }}</span>
            <span class="context-tag">{{ activity.projectName }}</span>
          </div>
          <div v-if="activity.summary" class="activity-summary">
            {{ activity.summary }}
          </div>
          <div v-if="activity.added && activity.added.length > 0" class="change-group added">
            <div class="change-label">
              <icon-plus-circle :size="14" /> 新增转换
            </div>
            <div
              v-for="(item, idx) in activity.added"
              :key="'a' + idx"
              class="change-item"
            >
              {{ item.fromStatus }} → {{ item.toStatus }}
            </div>
          </div>
          <div v-if="activity.removed && activity.removed.length > 0" class="change-group removed">
            <div class="change-label">
              <icon-minus-circle :size="14" /> 删除转换
            </div>
            <div
              v-for="(item, idx) in activity.removed"
              :key="'r' + idx"
              class="change-item"
            >
              {{ item.fromStatus }} → {{ item.toStatus }}
            </div>
          </div>
        </div>
      </div>
    </DataContainer>

    <!-- 分页 -->
    <AdminPagination
      v-model:current="pagination.page"
      v-model:page-size="pagination.pageSize"
      :total="total"
      :page-size-options="[10, 20, 50]"
      inline
      @change="onPageChange"
      @page-size-change="onPageSizeChange"
    />
  </a-drawer>
</template>

<script setup lang="ts">
import { formatDateTime } from '@/utils/date'
import { ref, watch } from 'vue'
import { IconPlusCircle, IconMinusCircle } from '@arco-design/web-vue/es/icon'
import { workflowApi } from '@/api'
import { AdminPagination } from '@/components/admin'
import DataContainer from '@/components/base/DataContainer.vue'
import type { WorkflowActivityVO } from '@/api/types'
import { usePagedList } from '@/composables/usePagedList'

const props = defineProps<{
  visible: boolean
  projectId: string
}>()

defineEmits<{
  'update:visible': [value: boolean]
}>()

interface ActivityFilters {
  userId: string
  startDate: string
  endDate: string
}

const { list: activities, total, loading, pagination, filters: activityFilters, refresh: loadActivities, onPageChange, onPageSizeChange } = usePagedList<WorkflowActivityVO, ActivityFilters>(
  (params) => {
    const requestParams: Record<string, any> = { page: params.page, pageSize: params.pageSize }
    if (params.userId) requestParams.userId = params.userId
    if (params.startDate) requestParams.startDate = params.startDate
    if (params.endDate) requestParams.endDate = params.endDate
    return workflowApi.listActivities(props.projectId, requestParams)
  },
  { pageSize: 10, immediate: false, initialFilters: { userId: '', startDate: '', endDate: '' } }
)

// 筛选
const dateRange = ref<string[]>([])
const filterUserId = ref<string | undefined>(undefined)
const users = ref<{ id: string; displayName: string }[]>([])



function onFilterChange() {
  activityFilters.userId = filterUserId.value || ''
  if (dateRange.value && dateRange.value.length === 2) {
    activityFilters.startDate = dateRange.value[0] || ''
    activityFilters.endDate = dateRange.value[1] || ''
  } else {
    activityFilters.startDate = ''
    activityFilters.endDate = ''
  }
  pagination.page = 1
  loadActivities()
}

// Collect users from activities for filter dropdown
watch(activities, (newActivities) => {
  if (users.value.length === 0 && newActivities.length > 0) {
    const userMap = new Map<string, string>()
    for (const a of newActivities) {
      if (a.userId && a.userDisplayName) {
        userMap.set(a.userId, a.userDisplayName)
      }
    }
    if (userMap.size > 0) {
      users.value = Array.from(userMap.entries()).map(([id, displayName]) => ({ id, displayName }))
    }
  }
})

watch(() => props.visible, (val) => {
  if (val) {
    pagination.page = 1
    dateRange.value = []
    filterUserId.value = undefined
    activityFilters.userId = ''
    activityFilters.startDate = ''
    activityFilters.endDate = ''
    loadActivities()
  }
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-item {
  padding: 12px;
  border-radius: 6px;
  background: var(--bg-secondary, var(--color-fill-1));
  border: 1px solid var(--border-color, var(--color-border));
}

.activity-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.activity-user {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, var(--color-text-1));
}

.activity-time {
  font-size: 11px;
  color: var(--text-muted, var(--color-text-3));
}

.activity-context {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.context-tag {
  display: inline-block;
  padding: 1px 6px;
  font-size: 11px;
  border-radius: 3px;
  background: var(--bg-tertiary, var(--color-fill-2));
  color: var(--text-secondary, var(--color-text-2));
}

.activity-summary {
  font-size: 13px;
  color: var(--text-primary, var(--color-text-1));
  margin-bottom: 8px;
  line-height: 1.4;
}

.change-group {
  margin-top: 6px;
  padding: 8px;
  border-radius: 4px;
}

.change-group.added {
  background: var(--tf-success-bg);
}

.change-group.removed {
  background: var(--tf-danger-bg);
}

.change-label {
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.change-group.added .change-label {
  color: var(--tf-success);
}

.change-group.removed .change-label {
  color: var(--tf-danger);
}

.change-item {
  font-size: 12px;
  color: var(--text-secondary, var(--color-text-2));
  padding: 2px 0 2px 18px;
  line-height: 1.5;
}

</style>
