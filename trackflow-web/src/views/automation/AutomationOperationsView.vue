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

    <a-modal v-model:visible="roleModalOpen" :title="editingRoleId ? '编辑角色' : '新建角色'" :width="720" @ok="saveRole">
      <a-form :model="roleForm" layout="vertical">
        <div class="form-grid">
          <a-form-item label="角色名称"><a-input v-model="roleForm.name" /></a-form-item>
          <a-form-item label="Provider">
            <a-select v-model="roleForm.providerType">
              <a-option value="cli">CLI</a-option>
              <a-option value="http">HTTP</a-option>
              <a-option value="openai_compatible">OpenAI Compatible</a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="模型"><a-input v-model="roleForm.model" /></a-form-item>
          <a-form-item label="启用"><a-switch v-model="roleForm.enabled" /></a-form-item>
        </div>
        <a-form-item label="角色职责"><a-textarea v-model="roleForm.description" /></a-form-item>
        <a-form-item label="系统提示词"><a-textarea v-model="roleForm.systemPrompt" :auto-size="{ minRows: 4, maxRows: 10 }" /></a-form-item>
        <a-form-item label="工具策略 JSON">
          <a-textarea v-model="roleForm.toolPolicy" :auto-size="{ minRows: 5, maxRows: 12 }" />
          <div class="hint">CLI 示例：command、arguments、allowedCommands、timeoutSeconds；HTTP 示例：endpoint、allowedHosts、apiKeyEnv。</div>
        </a-form-item>
        <a-form-item label="工作区策略 JSON"><a-textarea v-model="roleForm.workspacePolicy" :auto-size="{ minRows: 3, maxRows: 8 }" /></a-form-item>
        <a-form-item label="结构化输出 Schema JSON"><a-textarea v-model="roleForm.outputSchema" :auto-size="{ minRows: 3, maxRows: 8 }" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { automationApi, type AutomationApproval, type AutomationRoleProfile, type AutomationRoleProfileDTO, type AutomationWorkItem } from '@/api/automation'

const router = useRouter()
const roles = ref<AutomationRoleProfile[]>([])
const approvals = ref<AutomationApproval[]>([])
const loadingRoles = ref(false)
const loadingApprovals = ref(false)
const workItems = ref<AutomationWorkItem[]>([])
const loadingWorkItems = ref(false)
const roleModalOpen = ref(false)
const editingRoleId = ref<string>()
const defaults: AutomationRoleProfileDTO = {
  name: '', providerType: 'cli', model: '', description: '', systemPrompt: '', enabled: true,
  toolPolicy: JSON.stringify({ command: 'codex', arguments: ['exec'], allowedCommands: ['codex'], timeoutSeconds: 1800 }, null, 2),
  workspacePolicy: JSON.stringify({ allowedRoots: ['D:\\project\\YT'], defaultWorkDir: 'D:\\project\\YT' }, null, 2),
  outputSchema: '{}',
}
const roleForm = reactive<AutomationRoleProfileDTO>({ ...defaults })

onMounted(() => { loadRoles(); loadApprovals(); loadWorkItems() })
async function loadRoles() { loadingRoles.value = true; try { const res = await automationApi.listRoles(); if (res.code === 0) roles.value = res.data || [] } finally { loadingRoles.value = false } }
async function loadApprovals() { loadingApprovals.value = true; try { const res = await automationApi.listApprovals(); if (res.code === 0) approvals.value = res.data || [] } finally { loadingApprovals.value = false } }
async function loadWorkItems() { loadingWorkItems.value = true; try { const res = await automationApi.listWorkItems(); if (res.code === 0) workItems.value = res.data || [] } finally { loadingWorkItems.value = false } }
async function retryWorkItem(id: string) { const res = await automationApi.retryWorkItem(id); if (res.code === 0) { Message.success('已重新入队'); await loadWorkItems() } }
async function cancelWorkItem(id: string) { const res = await automationApi.cancelWorkItem(id); if (res.code === 0) { Message.success('已取消'); await loadWorkItems() } }
function openRole(role?: AutomationRoleProfile) {
  editingRoleId.value = role?.id
  Object.assign(roleForm, role ? {
    name: role.name, description: role.description || '', providerType: role.providerType,
    model: role.model || '', systemPrompt: role.systemPrompt || '', toolPolicy: role.toolPolicy || '{}',
    workspacePolicy: role.workspacePolicy || '{}', outputSchema: role.outputSchema || '{}', enabled: role.enabled,
  } : { ...defaults })
  roleModalOpen.value = true
}
async function saveRole() {
  if (!roleForm.name.trim()) return Message.warning('请输入角色名称')
  const res = editingRoleId.value
    ? await automationApi.updateRole(editingRoleId.value, { ...roleForm })
    : await automationApi.createRole({ ...roleForm })
  if (res.code === 0) { Message.success('角色已保存'); roleModalOpen.value = false; await loadRoles() }
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
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.hint { margin-top: 4px; color: var(--tf-text-tertiary); font-size: 12px; }
.queue-toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
</style>
