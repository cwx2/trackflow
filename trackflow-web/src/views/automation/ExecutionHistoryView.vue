<template>
  <div class="execution-history-view">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <a-button type="text" class="back-btn" @click="router.back()">
          <template #icon><span>←</span></template>
          返回
        </a-button>
        <div class="header-info">
          <h1 class="page-title">执行历史</h1>
          <span v-if="workflowName" class="workflow-name">{{ workflowName }}</span>
        </div>
      </div>
      <a-button type="primary" @click="router.push(`/automation/${workflowId}`)">
        打开编辑器
      </a-button>
    </div>

    <!-- 执行列表 -->
    <div class="page-content">
      <a-spin :loading="loading" class="full-spin">
        <div v-if="!loading && executions.length === 0" class="empty-state">
          <div class="empty-icon">📋</div>
          <h3 class="empty-title">暂无执行记录</h3>
          <p class="empty-desc">在工作流编辑器中点击「试运行」开始第一次执行</p>
          <a-button type="primary" @click="router.push(`/automation/${workflowId}`)">
            打开编辑器
          </a-button>
        </div>

        <a-table
          v-else
          :data="executions"
          :columns="columns"
          :pagination="{ total: total, current: page, pageSize: pageSize, showTotal: true }"
          :bordered="false"
          row-key="id"
          class="execution-table"
          @page-change="handlePageChange"
        >
          <template #status="{ record }">
            <a-tag :color="statusColor(record.status)" size="small">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template #startedAt="{ record }">
            <span class="time-text">{{ formatTime(record.startedAt) }}</span>
          </template>
          <template #duration="{ record }">
            <span class="time-text">{{ formatDuration(record.durationMs) }}</span>
          </template>
          <template #createdBy="{ record }">
            <span class="user-text">{{ record.createdBy || '手动触发' }}</span>
          </template>
          <template #actions="{ record }">
            <a-button type="text" size="small" @click="openDetail(record)">
              查看详情
            </a-button>
          </template>
        </a-table>
      </a-spin>
    </div>

    <!-- 执行详情抽屉 -->
    <ExecutionDetailDrawer
      v-model:visible="drawerVisible"
      :execution-id="selectedExecutionId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { automationApi, type ExecutionVO } from '@/api'
import ExecutionDetailDrawer from './components/ExecutionDetailDrawer.vue'

const route = useRoute()
const router = useRouter()

const workflowId = computed(() => route.params.id as string)
const workflowName = ref('')
const loading = ref(false)
const executions = ref<ExecutionVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

// 详情抽屉
const drawerVisible = ref(false)
const selectedExecutionId = ref('')

const columns = [
  { title: '状态',     dataIndex: 'status',    slotName: 'status',    width: 90 },
  { title: '开始时间', dataIndex: 'startedAt', slotName: 'startedAt', width: 160 },
  { title: '耗时',     dataIndex: 'durationMs', slotName: 'duration', width: 100 },
  { title: '触发人',   dataIndex: 'createdBy', slotName: 'createdBy', width: 120 },
  { title: '操作',     slotName: 'actions',    width: 100 },
]

function statusColor(status: string) {
  const map: Record<string, string> = {
    running: 'blue', success: 'green', failed: 'red', cancelled: 'gray'
  }
  return map[status] || 'gray'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    running: '运行中', success: '成功', failed: '失败', cancelled: '已取消'
  }
  return map[status] || status
}

function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth()+1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatDuration(ms?: number): string {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms/1000).toFixed(1)}s`
  return `${Math.floor(ms/60000)}m ${Math.floor((ms%60000)/1000)}s`
}

async function loadExecutions() {
  loading.value = true
  try {
    const res = await automationApi.listExecutions(workflowId.value, { page: page.value, pageSize: pageSize.value })
    if (res.code === 0 && res.data) {
      executions.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (e: any) {
    Message.error('加载执行历史失败')
  } finally {
    loading.value = false
  }
}

async function loadWorkflowName() {
  try {
    const res = await automationApi.getById(workflowId.value)
    if (res.code === 0) workflowName.value = res.data?.name || ''
  } catch {}
}

function openDetail(execution: ExecutionVO) {
  selectedExecutionId.value = execution.id
  drawerVisible.value = true
}

function handlePageChange(p: number) {
  page.value = p
  loadExecutions()
}

onMounted(() => {
  loadWorkflowName()
  loadExecutions()
})
</script>

<style scoped>
.execution-history-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  border-bottom: 1px solid var(--tf-border);
  background: var(--tf-bg-surface);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  color: var(--tf-text-secondary);
}

.header-info {
  display: flex;
  flex-direction: column;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
  line-height: 1.3;
}

.workflow-name {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-top: 2px;
}

.page-content {
  flex: 1;
  overflow: auto;
  padding: 24px;
}

.full-spin {
  width: 100%;
  min-height: 200px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 20px;
  text-align: center;
}

.empty-icon { font-size: 56px; margin-bottom: 16px; }
.empty-title { font-size: 16px; font-weight: 600; color: var(--tf-text-primary); margin: 0 0 8px; }
.empty-desc { font-size: 13px; color: var(--tf-text-secondary); margin: 0 0 20px; }

.execution-table { background: var(--tf-bg-surface); border-radius: 8px; }
.time-text { font-size: 13px; color: var(--tf-text-secondary); font-family: monospace; }
.user-text { font-size: 13px; color: var(--tf-text-secondary); }

:deep(.arco-table-th) { background: var(--tf-bg-elevated); color: var(--tf-text-secondary); }
:deep(.arco-table-td) { color: var(--tf-text-primary); }
:deep(.arco-table-tr:hover .arco-table-td) { background: var(--tf-bg-hover); }
</style>
