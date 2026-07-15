<template>
  <div class="trash-page">
    <div class="trash-header">
      <div class="header-left">
        <span class="header-icon">🗑️</span>
        <h1 class="header-title">回收站</h1>
        <span class="header-hint">已删除的工单将保留 30 天后自动清除</span>
      </div>
      <div class="header-right">
        <a-select
          v-model="selectedProjectId"
          placeholder="选择项目"
          size="small"
          style="width: 180px"
          @change="loadTrash"
        >
          <a-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
      </div>
    </div>

    <div class="trash-content">
      <!-- 未选项目时的空状态 -->
      <div v-if="!selectedProjectId" class="empty-state">
        <span class="empty-icon">📁</span>
        <h3 class="empty-title">请选择项目</h3>
        <p class="empty-desc">选择一个项目以查看其回收站中的工单</p>
      </div>

      <!-- 加载中 -->
      <a-spin v-else-if="loading" :loading="true" style="width: 100%; padding: 60px 0;" />

      <!-- 空回收站 -->
      <div v-else-if="trashList.length === 0" class="empty-state">
        <span class="empty-icon">✨</span>
        <h3 class="empty-title">回收站是空的</h3>
        <p class="empty-desc">该项目中没有已删除的工单</p>
      </div>

      <!-- 回收站列表 -->
      <div v-else class="trash-list">
        <a-table
          :data="trashList"
          :columns="columns"
          :pagination="paginationConfig"
          :loading="loading"
          row-key="id"
          size="small"
          @page-change="onPageChange"
        >
          <template #issueKey="{ record }">
            <span class="issue-key">{{ record.issueKey }}</span>
          </template>
          <template #title="{ record }">
            <span class="issue-title">{{ record.title }}</span>
          </template>
          <template #priority="{ record }">
            <span class="priority-tag" :class="`priority-${record.priority?.toLowerCase()}`">
              {{ record.priority }}
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
import { Message, Modal } from '@arco-design/web-vue'
import { IconUndo, IconDelete } from '@arco-design/web-vue/es/icon'
import { issueApi, projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { IssueTrashVO } from '@/api/types'

const authStore = useAuthStore()

const projectList = ref<any[]>([])
const selectedProjectId = ref<string | null>(null)
const trashList = ref<IssueTrashVO[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const isProjectAdmin = computed(() => {
  return authStore.hasGlobalPermission('system:admin')
})

const columns = [
  { title: 'Key', dataIndex: 'issueKey', slotName: 'issueKey', width: 100 },
  { title: '标题', dataIndex: 'title', slotName: 'title', ellipsis: true },
  { title: '类型', dataIndex: 'issueType', width: 80 },
  { title: '优先级', dataIndex: 'priority', slotName: 'priority', width: 80 },
  { title: '负责人', dataIndex: 'assigneeName', width: 100 },
  { title: '删除时间', dataIndex: 'deletedAt', slotName: 'deletedAt', width: 160 },
  { title: '删除人', dataIndex: 'deletedByName', slotName: 'deletedBy', width: 100 },
  { title: '操作', slotName: 'actions', width: 160, fixed: 'right' as const }
]

const paginationConfig = computed(() => ({
  current: currentPage.value,
  pageSize: pageSize.value,
  total: total.value,
  showTotal: true,
  showPageSize: false
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
    // Auto-select first project if only one
    if (projectList.value.length === 1) {
      selectedProjectId.value = projectList.value[0].id
      await loadTrash()
    }
  } catch {
    projectList.value = []
  }
}

async function loadTrash() {
  if (!selectedProjectId.value) return
  loading.value = true
  try {
    const res = await issueApi.listTrash({
      projectId: selectedProjectId.value,
      page: currentPage.value,
      pageSize: pageSize.value
    })
    trashList.value = res.data?.list || []
    total.value = res.data?.pagination?.total || 0
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载回收站失败')
    trashList.value = []
  } finally {
    loading.value = false
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  loadTrash()
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

function handlePermanentDelete(record: IssueTrashVO) {
  Modal.warning({
    title: '永久删除',
    content: `确定永久删除工单 ${record.issueKey} "${record.title}"？此操作不可撤销，所有关联数据（评论、附件、活动记录）将一并删除。`,
    okText: '永久删除',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
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

.priority-critical { background: rgba(248, 81, 73, 0.15); color: var(--tf-danger); }
.priority-major { background: rgba(210, 153, 34, 0.15); color: var(--tf-warning); }
.priority-normal { background: rgba(88, 166, 255, 0.1); color: var(--tf-accent); }
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
</style>
