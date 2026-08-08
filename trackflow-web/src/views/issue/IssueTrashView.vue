<template>
  <div class="trash-page">
    <div class="trash-header">
      <div class="header-left">
        <span class="header-icon"><icon-delete /></span>
        <h1 class="header-title">回收站</h1>
        <span class="header-hint">{{ retentionHint }}</span>
      </div>
      <div class="header-right">
        <a-select
          :model-value="selectedProjectId ?? undefined"
          @update:model-value="(v) => { selectedProjectId = (v as string) ?? null }"
          placeholder="选择项目"
          size="small"
          style="width: 180px"
          @change="onProjectChange"
        >
          <a-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
        <a-dropdown v-if="selectedProjectId && isProjectAdmin" trigger="click">
          <a-button size="small" type="text" title="保留策略设置">
            <template #icon><icon-settings /></template>
          </a-button>
          <template #content>
            <div class="retention-dropdown">
              <div class="retention-title">回收站保留策略</div>
              <div
                v-for="opt in retentionOptions"
                :key="opt.value"
                class="retention-option"
                :class="{ active: retentionDays === opt.value }"
                @click="setRetention(opt.value)"
              >
                <span>{{ opt.label }}</span>
                <span v-if="retentionDays === opt.value" class="check-mark">✓</span>
              </div>
            </div>
          </template>
        </a-dropdown>
      </div>
    </div>

    <div class="trash-content">
      <!-- 未选项目时的空状态 -->
      <div v-if="!selectedProjectId" class="empty-state">
        <span class="empty-icon"><icon-folder /></span>
        <h3 class="empty-title">请选择项目</h3>
        <p class="empty-desc">选择一个项目以查看其回收站中的工单</p>
      </div>

      <!-- 加载中 -->
      <a-spin v-else-if="loading" :loading="true" style="width: 100%; padding: 60px 0;" />

      <!-- 空回收站 -->
      <div v-else-if="trashList.length === 0" class="empty-state">
        <span class="empty-icon"><icon-check-circle /></span>
        <h3 class="empty-title">回收站是空的</h3>
        <p class="empty-desc">该项目中没有已删除的工单</p>
      </div>

      <!-- 回收站列表 -->
      <div v-else class="trash-list">
        <!-- 批量操作栏 -->
        <div v-if="selectedIds.length > 0" class="batch-bar">
          <span class="batch-count">已选 {{ selectedIds.length }} 个工单</span>
          <a-button size="mini" type="text" @click="batchRestoreSelected">
            <template #icon><icon-undo /></template>
            批量恢复
          </a-button>
          <a-button v-if="isProjectAdmin" size="mini" type="text" status="danger" @click="batchPermanentDelete">
            <template #icon><icon-delete /></template>
            批量永久删除
          </a-button>
          <a-button size="mini" type="text" @click="selectedIds = []">取消选择</a-button>
        </div>

        <a-table
          :data="trashList"
          :columns="columns"
          :pagination="paginationConfig"
          :loading="loading"
          :row-selection="{ type: 'checkbox', showCheckedAll: true }"
          row-key="id"
          size="small"
          @selection-change="onSelectionChange"
          @page-change="onPageChange"
          @page-size-change="onPageSizeChange"
        >
          <template #issueKey="{ record }">
            <span class="issue-key">{{ record.issueKey }}</span>
          </template>
          <template #title="{ record }">
            <span class="issue-title">{{ record.title }}</span>
          </template>
          <template #issueType="{ record }">
            <span class="type-label">{{ localizeIssueType(record.issueType) }}</span>
          </template>
          <template #priority="{ record }">
            <span class="priority-tag" :class="`priority-${record.priority?.toLowerCase()}`">
              {{ localizePriority(record.priority) }}
            </span>
          </template>
          <template #deletedAt="{ record }">
            <span class="deleted-time">{{ formatTime(record.deletedAt) }}</span>
          </template>
          <template #deletedBy="{ record }">
            <span class="deleted-by">{{ record.deletedByName || '-' }}</span>
          </template>
          <template #actions="{ record }">
            <div class="action-btns">
              <a-button size="mini" type="text" @click="handleRestore(record)">
                <template #icon><icon-undo /></template>
                恢复
              </a-button>
              <a-button
                v-if="isProjectAdmin"
                size="mini" type="text" status="danger"
                @click="handlePermanentDelete(record)"
              >
                <template #icon><icon-delete /></template>
                永久删除
              </a-button>
            </div>
          </template>
        </a-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { IconUndo, IconDelete, IconSettings, IconFolder, IconCheckCircle } from '@arco-design/web-vue/es/icon'
import { issueApi, projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { localizeIssueType, localizePriority } from '@/utils/fieldLabels'
import type { IssueTrashVO } from '@/api/types'
import { usePagedList } from '@/composables/usePagedList'

const authStore = useAuthStore()

const projectList = ref<any[]>([])
const selectedProjectId = ref<string | null>(null)
const selectedIds = ref<string[]>([])
const retentionDays = ref(30)

interface TrashFilters {
  projectId: string
}

const { list: trashList, total, loading, pagination, refresh: loadTrash, onPageChange, onPageSizeChange } = usePagedList<IssueTrashVO, TrashFilters>(
  (params) => issueApi.listTrash({ projectId: selectedProjectId.value!, page: params.page, pageSize: params.pageSize }),
  { pageSize: 20, immediate: false }
)

const retentionOptions = [
  { value: 7, label: '7 天' },
  { value: 14, label: '14 天' },
  { value: 30, label: '30 天（默认）' },
  { value: 60, label: '60 天' },
  { value: 90, label: '90 天' },
  { value: 0, label: '永久保留（不自动清理）' }
]

const isProjectAdmin = computed(() => {
  return authStore.hasGlobalPermission('system:admin')
})

const retentionHint = computed(() => {
  if (retentionDays.value === 0) {
    return '已删除的工单将永久保留，不会自动清理'
  }
  return `已删除的工单将保留 ${retentionDays.value} 天后自动清除`
})

const columns = [
  { title: 'Key', dataIndex: 'issueKey', slotName: 'issueKey', width: 100 },
  { title: '标题', dataIndex: 'title', slotName: 'title', ellipsis: true },
  { title: '类型', dataIndex: 'issueType', slotName: 'issueType', width: 80 },
  { title: '优先级', dataIndex: 'priority', slotName: 'priority', width: 80 },
  { title: '负责人', dataIndex: 'assigneeName', width: 100 },
  { title: '删除时间', dataIndex: 'deletedAt', slotName: 'deletedAt', width: 160 },
  { title: '删除人', dataIndex: 'deletedByName', slotName: 'deletedBy', width: 100 },
  { title: '操作', slotName: 'actions', width: 160, fixed: 'right' as const }
]

const paginationConfig = computed(() => ({
  current: pagination.page,
  pageSize: pagination.pageSize,
  total: total.value,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: [20, 50, 100, 200]
}))

function formatTime(dt: string) {
  if (!dt) return '-'
  const d = new Date(dt)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 50 })
    projectList.value = res.data?.list || []
    if (projectList.value.length === 1) {
      selectedProjectId.value = projectList.value[0].id
      await onProjectChange()
    }
  } catch {
    projectList.value = []
  }
}

async function onProjectChange() {
  if (!selectedProjectId.value) return
  selectedIds.value = []
  pagination.page = 1
  await Promise.all([loadTrash(), loadRetentionSettings()])
}

async function loadRetentionSettings() {
  if (!selectedProjectId.value) return
  try {
    const res = await projectApi.getTrashSettings(selectedProjectId.value)
    retentionDays.value = res.data?.trashRetentionDays ?? 30
  } catch {
    retentionDays.value = 30
  }
}

async function setRetention(days: number) {
  if (!selectedProjectId.value) return
  try {
    await projectApi.updateTrashSettings(selectedProjectId.value, { trashRetentionDays: days })
    retentionDays.value = days
    Message.success(days === 0 ? '已设为永久保留' : `已设为保留 ${days} 天`)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '设置失败')
  }
}

function onSelectionChange(keys: string[]) {
  selectedIds.value = keys
}

async function handleRestore(record: IssueTrashVO) {
  try {
    await issueApi.restore(record.id)
    Message.success(`工单 ${record.issueKey} 已恢复`)
    loadTrash()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '恢复失败')
  }
}

async function batchRestoreSelected() {
  if (selectedIds.value.length === 0) return
  try {
    const res = await issueApi.batch({
      operation: 'restore',
      issueIds: selectedIds.value
    })
    const data = res.data
    if (data && data.failed === 0) {
      Message.success(`已恢复 ${data.succeeded} 个工单`)
    } else if (data) {
      Message.warning(`恢复 ${data.succeeded} 个，失败 ${data.failed} 个`)
    }
    selectedIds.value = []
    loadTrash()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '批量恢复失败')
  }
}

function handlePermanentDelete(record: IssueTrashVO) {
  const { confirmDangerDelete } = useConfirmDelete()
  confirmDangerDelete({
    itemName: `工单 ${record.issueKey}「${record.title}」`,
    impactDescription: '所有关联数据（评论、附件、活动记录）将一并删除',
    confirmText: '永久删除',
    onConfirm: async () => {
      try {
        await issueApi.permanentDelete(record.id)
        Message.success(`工单 ${record.issueKey} 已永久删除`)
        loadTrash()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '永久删除失败')
      }
    }
  })
}

function batchPermanentDelete() {
  if (selectedIds.value.length === 0) return
  const { confirmDangerDelete } = useConfirmDelete()
  confirmDangerDelete({
    itemName: `选中的 ${selectedIds.value.length} 个工单`,
    impactDescription: '永久删除后无法恢复',
    confirmText: '永久删除',
    onConfirm: async () => {
      let succeeded = 0
      let failed = 0
      for (const id of selectedIds.value) {
        try {
          await issueApi.permanentDelete(id)
          succeeded++
        } catch {
          failed++
        }
      }
      if (failed === 0) {
        Message.success(`已永久删除 ${succeeded} 个工单`)
      } else {
        Message.warning(`删除 ${succeeded} 个，失败 ${failed} 个`)
      }
      selectedIds.value = []
      loadTrash()
    }
  })
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
.trash-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
  overflow: auto;
}

.trash-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 20px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.header-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-left: 8px;
}

.trash-content {
  flex: 1;
}

/* Empty states */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

/* Table styles */
.trash-list {
  background: var(--tf-bg-surface);
  border-radius: 8px;
  border: 1px solid var(--tf-border-light);
  overflow: hidden;
}

.issue-key {
  font-family: var(--tf-font-mono, monospace);
  font-size: 12px;
  color: var(--tf-text-secondary);
}

.issue-title {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.priority-tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 500;
}

.priority-critical { background: var(--tf-danger-medium); color: var(--tf-danger); }
.priority-major { background: var(--tf-warning-medium); color: var(--tf-warning); }
.priority-normal { background: var(--tf-accent-bg-light); color: var(--tf-accent); }
.priority-minor { background: var(--tf-bg-hover); color: var(--tf-text-tertiary); }

.deleted-time {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.deleted-by {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

.action-btns {
  display: flex;
  gap: 4px;
}

/* Header right area */
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Retention dropdown */
.retention-dropdown {
  padding: 8px;
  min-width: 200px;
}

.retention-title {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 4px 8px 8px;
  font-weight: 500;
}

.retention-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 13px;
  color: var(--tf-text-primary);
  cursor: pointer;
  transition: background 0.15s;
}

.retention-option:hover {
  background: var(--tf-bg-hover);
}

.retention-option.active {
  color: var(--tf-accent);
  font-weight: 500;
}

.check-mark {
  color: var(--tf-accent);
  font-weight: 600;
}

/* Batch bar */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--tf-border-light);
  background: var(--tf-bg-elevated);
}

.batch-count {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}
</style>
