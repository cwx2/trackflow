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
    <a-spin :loading="loading">
      <div v-if="activities.length > 0" class="activity-list">
        <div
          v-for="activity in activities"
          :key="activity.id"
          class="activity-item"
        >
          <div class="activity-header">
            <span class="activity-user">{{ activity.userDisplayName }}</span>
            <span class="activity-time">{{ formatTime(activity.createdAt) }}</span>
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

      <!-- 空状态 -->
      <div v-else-if="!loading" class="empty-state">
        <icon-history :size="40" />
        <h4>暂无变更记录</h4>
        <p>保存工作流配置后，变更历史将显示在这里。</p>
      </div>
    </a-spin>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="pagination-bar">
      <a-pagination
        :total="total"
        :current="currentPage"
        :page-size="pageSize"
        size="small"
        @change="onPageChange"
      />
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconPlusCircle, IconMinusCircle, IconHistory } from '@arco-design/web-vue/es/icon'
import { workflowApi } from '@/api'
import type { WorkflowActivityVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  projectId: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const loading = ref(false)
const activities = ref<WorkflowActivityVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 10

// 筛选
const dateRange = ref<string[]>([])
const filterUserId = ref<string | undefined>(undefined)
const users = ref<{ id: string; displayName: string }[]>([])

function formatTime(isoStr: string): string {
  if (!isoStr) return ''
  const d = new Date(isoStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadActivities() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: currentPage.value,
      pageSize
    }
    if (filterUserId.value) {
      params.userId = filterUserId.value
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }

    const res = await workflowApi.listActivities(props.projectId, params)
    const data = res.data
    activities.value = data?.list || []
    total.value = data?.pagination?.total || 0

    // 收集出现过的用户（用于筛选下拉）
    const userMap = new Map<string, string>()
    for (const a of activities.value) {
      if (a.userId && a.userDisplayName) {
        userMap.set(a.userId, a.userDisplayName)
      }
    }
    // 只在首次加载时填充用户列表
    if (users.value.length === 0 && userMap.size > 0) {
      users.value = Array.from(userMap.entries()).map(([id, displayName]) => ({ id, displayName }))
    }
  } catch {
    Message.error('加载变更历史失败')
    activities.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  currentPage.value = 1
  loadActivities()
}

function onPageChange(page: number) {
  currentPage.value = page
  loadActivities()
}

watch(() => props.visible, (val) => {
  if (val) {
    currentPage.value = 1
    dateRange.value = []
    filterUserId.value = undefined
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
  background: rgba(63, 185, 80, 0.08);
}

.change-group.removed {
  background: rgba(248, 81, 73, 0.08);
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
  color: var(--color-success, #3fb950);
}

.change-group.removed .change-label {
  color: var(--color-danger, #f85149);
}

.change-item {
  font-size: 12px;
  color: var(--text-secondary, var(--color-text-2));
  padding: 2px 0 2px 18px;
  line-height: 1.5;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  color: var(--text-muted, var(--color-text-3));
}

.empty-state h4 {
  margin-top: 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary, var(--color-text-2));
}

.empty-state p {
  margin-top: 6px;
  font-size: 12px;
}

.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
