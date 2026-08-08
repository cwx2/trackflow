<template>
  <div class="operations-view">
    <div class="page-header">
      <div class="title-wrap">
        <a-button type="text" @click="router.push('/automation')">← 返回</a-button>
        <h1>角色与审批</h1>
      </div>
      <a-button type="primary" @click="openRole()">新建角色</a-button>
    </div>

    <a-tabs default-active-key="roles">
      <a-tab-pane key="roles" title="Agent 角色">
        <a-table :data="roles" :pagination="false" row-key="id" :loading="loadingRoles">
          <template #columns>
            <a-table-column title="ID" data-index="id" :width="170" />
            <a-table-column title="名称" data-index="name" />
            <a-table-column title="Provider" data-index="providerType" />
            <a-table-column title="模型" data-index="model" />
            <a-table-column title="状态">
              <template #cell="{ record }"><a-tag :color="record.enabled ? 'green' : 'gray'">{{ record.enabled ? '启用' : '停用' }}</a-tag></template>
            </a-table-column>
            <a-table-column title="操作" :width="160">
              <template #cell="{ record }">
                <a-button type="text" size="small" @click="openRole(record)">编辑</a-button>
                <a-popconfirm content="确定删除这个角色？" @ok="deleteRole(record.id)">
                  <a-button type="text" size="small" status="danger">删除</a-button>
                </a-popconfirm>
              </template>
            </a-table-column>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="approvals" title="待审批">
        <a-table :data="approvals" :pagination="false" row-key="id" :loading="loadingApprovals">
          <template #columns>
            <a-table-column title="风险" data-index="riskLevel" :width="90" />
            <a-table-column title="审批事项" data-index="title" />
            <a-table-column title="说明" data-index="description" />
            <a-table-column title="发起时间" data-index="createdAt" :width="190" />
            <a-table-column title="操作" :width="170">
              <template #cell="{ record }">
                <a-button size="small" type="primary" @click="decide(record.id, 'approved')">批准</a-button>
                <a-button size="small" status="danger" @click="decide(record.id, 'rejected')">拒绝</a-button>
              </template>
            </a-table-column>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="queue" title="任务队列">
        <div class="queue-toolbar"><a-button @click="loadWorkItems">刷新</a-button></div>
        <a-table :data="workItems" :pagination="false" row-key="id" :loading="loadingWorkItems">
          <template #columns>
            <a-table-column title="工作流" data-index="automationId" :width="170" />
            <a-table-column title="工单" data-index="issueId" :width="150" />
            <a-table-column title="状态" data-index="state" :width="110" />
            <a-table-column title="尝试" :width="90"><template #cell="{ record }">{{ record.attempt }}/{{ record.maxAttempts }}</template></a-table-column>
            <a-table-column title="最近错误" data-index="lastError" />
            <a-table-column title="操作" :width="160">
              <template #cell="{ record }">
                <a-button v-if="record.state === 'failed' || record.state === 'dead_letter'" type="text" @click="retryWorkItem(record.id)">重试</a-button>
                <a-button v-if="record.state === 'queued' || record.state === 'leased'" type="text" status="danger" @click="cancelWorkItem(record.id)">取消</a-button>
              </template>
            </a-table-column>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <RoleWizard
      :visible="roleModalOpen"
      :edit-role="editingRole"
      @close="roleModalOpen = false"
      @saved="roleModalOpen = false; loadRoles()"
    />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { automationApi, type AutomationApproval, type AutomationRoleProfile, type AutomationWorkItem } from '@/api/automation'
import RoleWizard from './components/RoleWizard.vue'

const router = useRouter()
const roles = ref<AutomationRoleProfile[]>([])
const approvals = ref<AutomationApproval[]>([])
const loadingRoles = ref(false)
const loadingApprovals = ref(false)
const workItems = ref<AutomationWorkItem[]>([])
const loadingWorkItems = ref(false)
const roleModalOpen = ref(false)
const editingRole = ref<AutomationRoleProfile | null>(null)

onMounted(() => { loadRoles(); loadApprovals(); loadWorkItems() })
async function loadRoles() { loadingRoles.value = true; try { const res = await automationApi.listRoles(); if (res.code === 0) roles.value = res.data || [] } finally { loadingRoles.value = false } }
async function loadApprovals() { loadingApprovals.value = true; try { const res = await automationApi.listApprovals(); if (res.code === 0) approvals.value = res.data || [] } finally { loadingApprovals.value = false } }
async function loadWorkItems() { loadingWorkItems.value = true; try { const res = await automationApi.listWorkItems(); if (res.code === 0) workItems.value = res.data || [] } finally { loadingWorkItems.value = false } }
async function retryWorkItem(id: string) { const res = await automationApi.retryWorkItem(id); if (res.code === 0) { Message.success('已重新入队'); await loadWorkItems() } }
async function cancelWorkItem(id: string) { const res = await automationApi.cancelWorkItem(id); if (res.code === 0) { Message.success('已取消'); await loadWorkItems() } }
function openRole(role?: AutomationRoleProfile) {
  editingRole.value = role || null
  roleModalOpen.value = true
}
async function deleteRole(id: string) { const res = await automationApi.deleteRole(id); if (res.code === 0) { Message.success('角色已删除'); await loadRoles() } }
function decide(id: string, decision: 'approved' | 'rejected') {
  let comment = ''
  Modal.confirm({
    title: decision === 'approved' ? '批准自动化操作' : '拒绝自动化操作',
    content: () => h('textarea', { class: 'arco-textarea', placeholder: '审批意见（可选）', onInput: (event: Event) => { comment = (event.target as HTMLTextAreaElement).value } }),
    onOk: async () => { const res = await automationApi.decideApproval(id, decision, comment); if (res.code === 0) { Message.success('审批已处理'); await loadApprovals() } },
  })
}
</script>

<style scoped>
.operations-view { padding: 24px; }
.page-header, .title-wrap { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.title-wrap h1 { margin: 0; }
.queue-toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
</style>
