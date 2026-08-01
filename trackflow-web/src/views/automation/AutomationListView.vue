<template>
  <div class="automation-list-view">
    <div class="page-header">
      <h1 class="page-title">自动化工作流</h1>
      <a-button v-if="workflows.length > 0" type="primary" @click="showCreateModal = true">
        <template #icon><span class="btn-icon">➕</span></template>
        新建工作流
      </a-button>
    </div>

    <div class="page-content">
      <a-spin :loading="loading" class="full-spin">
        <!-- 空状态 -->
        <div v-if="!loading && workflows.length === 0" class="empty-state">
          <div class="empty-icon">🤖</div>
          <h3 class="empty-title">暂无工作流</h3>
          <p class="empty-desc">创建您的第一个 Agent 工作流，自动化处理需求、测试和代码审核</p>
          <a-button type="primary" @click="showCreateModal = true">新建工作流</a-button>
        </div>

        <!-- 工作流列表表格 -->
        <a-table
          v-else
          :data="workflows"
          :columns="columns"
          :pagination="false"
          :bordered="false"
          row-key="id"
          class="workflow-table"
        >
          <template #name="{ record }">
            <a class="workflow-name-link" @click="goToEditor(record.id)">
              {{ record.name }}
            </a>
          </template>
          <template #description="{ record }">
            <span class="workflow-desc">{{ record.description || '-' }}</span>
          </template>
          <template #updatedAt="{ record }">
            <span class="workflow-time">{{ formatTime(record.updatedAt) }}</span>
          </template>
          <template #actions="{ record }">
            <div class="action-buttons">
              <a-button type="text" size="small" @click="goToEditor(record.id)">编辑</a-button>
              <a-button type="text" size="small" @click="goToHistory(record.id)">执行历史</a-button>
              <a-button type="text" size="small" status="danger" @click="confirmDelete(record)">删除</a-button>
            </div>
          </template>
        </a-table>
      </a-spin>
    </div>

    <!-- 新建工作流弹窗 -->
    <a-modal
      v-model:visible="showCreateModal"
      title="新建工作流"
      :ok-loading="createLoading"
      @ok="handleCreate"
      @cancel="resetCreateForm"
    >
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="名称" field="name" :rules="[{ required: true, message: '请输入工作流名称' }]">
          <a-input v-model="createForm.name" placeholder="例如：TrackFlow 需求处理" :max-length="200" />
        </a-form-item>
        <a-form-item label="描述" field="description">
          <a-textarea v-model="createForm.description" placeholder="工作流的用途说明" :max-length="2000" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { automationApi, type WorkflowVO, type CreateWorkflowDTO } from '@/api'

const router = useRouter()

// 状态
const loading = ref(false)
const workflows = ref<WorkflowVO[]>([])
const showCreateModal = ref(false)
const createLoading = ref(false)
const createForm = ref<CreateWorkflowDTO>({ name: '', description: '' })

// 表格列配置
const columns = [
  { title: '名称', dataIndex: 'name', slotName: 'name', width: 300 },
  { title: '描述', dataIndex: 'description', slotName: 'description' },
  { title: '更新时间', dataIndex: 'updatedAt', slotName: 'updatedAt', width: 180 },
  { title: '操作', slotName: 'actions', width: 220 }
]

// 加载工作流列表
async function loadWorkflows() {
  loading.value = true
  try {
    const res = await automationApi.list()
    if (res.code === 0) {
      workflows.value = res.data || []
    } else {
      Message.error(res.message || '加载失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 创建工作流
async function handleCreate() {
  if (!createForm.value.name.trim()) {
    Message.warning('请输入工作流名称')
    return
  }
  createLoading.value = true
  try {
    const res = await automationApi.create(createForm.value)
    if (res.code === 0) {
      Message.success('创建成功')
      showCreateModal.value = false
      resetCreateForm()
      // 跳转到编辑器
      router.push(`/automation/${res.data.id}`)
    } else {
      Message.error(res.message || '创建失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    createLoading.value = false
  }
}

// 重置创建表单
function resetCreateForm() {
  createForm.value = { name: '', description: '' }
}

// 跳转到编辑器
function goToEditor(id: string) {
  router.push(`/automation/${id}`)
}

// 跳转到执行历史
function goToHistory(id: string) {
  router.push(`/automation/${id}/executions`)
}

// 确认删除
function confirmDelete(workflow: WorkflowVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除工作流「${workflow.name}」吗？此操作不可恢复。`,
    okText: '删除',
    okButtonProps: { status: 'danger' },
    async onOk() {
      try {
        const res = await automationApi.delete(workflow.id)
        if (res.code === 0) {
          Message.success('删除成功')
          loadWorkflows()
        } else {
          Message.error(res.message || '删除失败')
        }
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

// 格式化时间
function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hours}:${minutes}`
}

onMounted(() => {
  loadWorkflows()
})
</script>

<style scoped>
.automation-list-view {
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.btn-icon {
  margin-right: 4px;
}

.page-content {
  flex: 1;
  overflow: auto;
}

.full-spin {
  width: 100%;
  min-height: 200px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 14px;
  color: var(--tf-text-secondary);
  margin: 0 0 24px 0;
  max-width: 400px;
}

/* 表格样式 */
.workflow-table {
  background: var(--tf-bg-surface);
  border-radius: 6px;
}

.workflow-name-link {
  color: var(--tf-accent);
  cursor: pointer;
  font-weight: 500;
}

.workflow-name-link:hover {
  text-decoration: underline;
}

.workflow-desc {
  color: var(--tf-text-secondary);
}

.workflow-time {
  color: var(--tf-text-tertiary);
  font-size: 13px;
}

.action-buttons {
  display: flex;
  gap: 4px;
}

/* 深色主题适配 */
:deep(.arco-table) {
  background: var(--tf-bg-surface);
}

:deep(.arco-table-th) {
  background: var(--tf-bg-elevated);
  color: var(--tf-text-secondary);
}

:deep(.arco-table-td) {
  color: var(--tf-text-primary);
}

:deep(.arco-table-tr:hover .arco-table-td) {
  background: var(--tf-bg-hover);
}
</style>
