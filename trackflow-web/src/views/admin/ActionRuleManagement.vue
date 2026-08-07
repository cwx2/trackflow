<template>
  <AdminPageLayout title="自定义动作" subtitle="配置工单快捷动作按钮，如"一键延期"、"标记为重复"、"升级优先级"">
    <template #actions>
      <a-button type="primary" @click="showCreateForm">
        <template #icon><icon-plus /></template>
        创建动作
      </a-button>
    </template>

    <!-- 动作列表 -->
    <a-table
      :data="definitions"
      :loading="loading"
      :pagination="false"
      row-key="id"
      :bordered="false"
      size="medium"
    >
      <template #columns>
        <a-table-column title="动作名称" :width="180">
          <template #cell="{ record }">
            <div class="action-name-cell">
              <span class="action-name">{{ record.label }}</span>
              <span class="action-key">{{ record.actionKey }}</span>
            </div>
          </template>
        </a-table-column>
        <a-table-column title="类型" :width="100">
          <template #cell="{ record }">
            <a-tag :color="record.actionType === 'rule' ? 'arcoblue' : 'gray'">
              {{ record.actionType === 'rule' ? '自动执行' : '填表执行' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="可见条件" :width="200">
          <template #cell="{ record }">
            <span class="visibility-text">{{ formatVisibility(record.visibility) }}</span>
          </template>
        </a-table-column>
        <a-table-column title="执行动作" :width="250">
          <template #cell="{ record }">
            <div class="actions-preview" v-if="record.actionType === 'rule'">
              <a-tag
                v-for="(act, idx) in parseExecutionActions(record.executionActions)"
                :key="idx"
                size="small"
                :color="actionTypeColor(act.type)"
              >{{ actionTypeLabel(act.type) }}{{ act.field ? ': ' + act.field : '' }}{{ act.tagName ? ': ' + act.tagName : '' }}{{ act.statusName ? ': ' + act.statusName : '' }}</a-tag>
            </div>
            <span v-else class="actions-text">表单 → 评论{{ record.statusTransitionTo ? ' + 状态变更' : '' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="状态" :width="80">
          <template #cell="{ record }">
            <a-switch
              :model-value="record.enabled"
              size="small"
              @change="handleToggle(record)"
            />
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="150" align="center">
          <template #cell="{ record }">
            <a-space>
              <a-button size="mini" @click="handleEdit(record)">编辑</a-button>
              <a-popconfirm content="确定删除此动作？" @ok="handleDelete(record)">
                <a-button size="mini" type="text" status="danger">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="formVisible"
      :title="editingDef ? '编辑动作' : '创建动作'"
      :width="720"
      @ok="handleSubmit"
      @cancel="formVisible = false"
      :ok-loading="submitting"
      ok-text="保存"
      cancel-text="取消"
    >
      <a-form :model="form" layout="vertical" ref="formRef">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="动作名称" field="label" :rules="[{ required: true, message: '请输入动作名称' }]">
              <a-input v-model="form.label" placeholder="如：一键延期一周" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="动作标识" field="actionKey" :rules="[{ required: true, message: '请输入动作标识' }]">
              <a-input v-model="form.actionKey" placeholder="如：postpone_one_week" :disabled="!!editingDef" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="动作类型" field="actionType">
              <a-select v-model="form.actionType">
                <a-option value="rule">自动执行（点击即执行）</a-option>
                <a-option value="form">填表执行（需用户填写表单）</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="图标标识" field="icon">
              <a-input v-model="form.icon" placeholder="可选，如 icon-thunder-bolt" />
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 可见条件 -->
        <a-form-item label="可见条件（可选）">
          <div class="condition-section">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="限定角色" class="nested-item">
                  <a-select
                    v-model="visibilityRoles"
                    multiple
                    allow-clear
                    placeholder="不限（所有角色可见）"
                  >
                    <a-option value="developer">开发人员</a-option>
                    <a-option value="tech_lead">技术负责人</a-option>
                    <a-option value="product_manager">产品经理</a-option>
                    <a-option value="tester">测试人员</a-option>
                    <a-option value="project_admin">项目管理员</a-option>
                    <a-option value="observer">观察者</a-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="限定工单状态" class="nested-item">
                  <a-select
                    v-model="visibilityStatuses"
                    multiple
                    allow-clear
                    placeholder="不限（所有状态可见）"
                  >
                    <a-option v-for="s in availableStatuses" :key="s" :value="s">{{ s }}</a-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </div>
        </a-form-item>

        <!-- 执行动作列表（rule 类型） -->
        <a-form-item v-if="form.actionType === 'rule'" label="执行动作列表">
          <div class="execution-actions">
            <div
              v-for="(action, idx) in executionActions"
              :key="idx"
              class="action-item"
            >
              <a-select v-model="action.type" style="width: 140px" placeholder="动作类型">
                <a-option value="set_field">设置字段</a-option>
                <a-option value="add_tag">添加标签</a-option>
                <a-option value="add_comment">添加评论</a-option>
                <a-option value="set_status">变更状态</a-option>
                <a-option value="send_notification">发送通知</a-option>
              </a-select>

              <!-- set_field 参数 -->
              <template v-if="action.type === 'set_field'">
                <a-select v-model="action.field" style="width: 130px" placeholder="字段">
                  <a-option value="priority">优先级</a-option>
                  <a-option value="assignee">负责人</a-option>
                  <a-option value="due_date">到期日</a-option>
                  <a-option value="issue_type">类型</a-option>
                </a-select>
                <a-input v-model="action.value" style="width: 150px" :placeholder="fieldValuePlaceholder(action.field)" />
              </template>

              <!-- add_tag 参数 -->
              <template v-if="action.type === 'add_tag'">
                <a-input v-model="action.tagName" style="width: 200px" placeholder="标签名称" />
              </template>

              <!-- add_comment 参数 -->
              <template v-if="action.type === 'add_comment'">
                <a-input v-model="action.content" style="flex: 1" placeholder="评论内容（支持 {issueKey} {issueTitle} 变量）" />
              </template>

              <!-- set_status 参数 -->
              <template v-if="action.type === 'set_status'">
                <a-select v-model="action.statusName" style="width: 200px" placeholder="目标状态">
                  <a-option v-for="s in availableStatuses" :key="s" :value="s">{{ s }}</a-option>
                </a-select>
              </template>

              <!-- send_notification 参数 -->
              <template v-if="action.type === 'send_notification'">
                <a-select v-model="action.recipientType" style="width: 130px" placeholder="接收人">
                  <a-option value="assignee">负责人</a-option>
                  <a-option value="reporter">报告人</a-option>
                </a-select>
              </template>

              <a-button size="mini" type="text" status="danger" @click="removeAction(idx)">
                <icon-delete />
              </a-button>
            </div>

            <a-button size="small" type="dashed" long @click="addAction">
              <template #icon><icon-plus /></template>
              添加动作
            </a-button>
          </div>
        </a-form-item>

        <!-- 表单配置（form 类型） -->
        <template v-if="form.actionType === 'form'">
          <a-form-item label="表单字段 (JSON)" field="formSchema">
            <a-textarea
              v-model="form.formSchema"
              :auto-size="{ minRows: 3, maxRows: 8 }"
              class="code-input"
              placeholder='[{"key":"reason","label":"原因","type":"textarea","required":true}]'
            />
          </a-form-item>
          <a-form-item label="操作按钮 (JSON)" field="actions">
            <a-textarea
              v-model="form.actions"
              :auto-size="{ minRows: 2, maxRows: 5 }"
              class="code-input"
              placeholder='[{"key":"only_comment","label":"仅评论","type":"primary"}]'
            />
          </a-form-item>
          <a-form-item label="执行后目标状态" field="statusTransitionTo">
            <a-select v-model="form.statusTransitionTo" allow-clear placeholder="不变更状态">
              <a-option v-for="s in availableStatuses" :key="s" :value="s">{{ s }}</a-option>
            </a-select>
          </a-form-item>
        </template>
      </a-form>
    </a-modal>
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconPlus, IconDelete } from '@arco-design/web-vue/es/icon'
import { quickActionApi } from '@/api'
import type { QuickActionDefinitionVO } from '@/api/quickAction'
import AdminPageLayout from '@/components/admin/AdminPageLayout.vue'

// ==================== State ====================
const loading = ref(false)
const definitions = ref<QuickActionDefinitionVO[]>([])
const formVisible = ref(false)
const editingDef = ref<QuickActionDefinitionVO | null>(null)
const submitting = ref(false)
const formRef = ref()

const form = reactive({
  actionKey: '',
  label: '',
  icon: '',
  actionType: 'rule' as string,
  formSchema: '[]',
  actions: '[]',
  statusTransitionTo: null as string | null,
})

const visibilityRoles = ref<string[]>([])
const visibilityStatuses = ref<string[]>([])

interface ExecutionAction {
  type: string
  field?: string
  value?: string
  tagName?: string
  content?: string
  statusName?: string
  recipientType?: string
}

const executionActions = ref<ExecutionAction[]>([])

const availableStatuses = [
  'Open', 'In Progress', 'Testing', 'Done', 'Closed',
  'Reopened', 'On Hold', 'Cancelled', 'Duplicate',
  'Waiting for Response', 'Under Review', 'Blocked'
]

// ==================== Methods ====================

function formatVisibility(vis: string) {
  if (!vis || vis === '{}') return '所有人可见'
  try {
    const obj = JSON.parse(vis)
    const parts: string[] = []
    if (obj.roles?.length) parts.push(`角色: ${obj.roles.join(', ')}`)
    if (obj.issueStatuses?.length) parts.push(`状态: ${obj.issueStatuses.join(', ')}`)
    return parts.join(' | ') || '所有人可见'
  } catch {
    return '所有人可见'
  }
}

function parseExecutionActions(json: string): ExecutionAction[] {
  if (!json || json === '[]') return []
  try { return JSON.parse(json) } catch { return [] }
}

function actionTypeColor(type: string) {
  const map: Record<string, string> = {
    set_field: 'orange', add_tag: 'green', add_comment: 'blue',
    set_status: 'purple', send_notification: 'cyan'
  }
  return map[type] || 'gray'
}

function actionTypeLabel(type: string) {
  const map: Record<string, string> = {
    set_field: '设置字段', add_tag: '添加标签', add_comment: '评论',
    set_status: '变更状态', send_notification: '通知'
  }
  return map[type] || type
}

function fieldValuePlaceholder(field: string | undefined) {
  switch (field) {
    case 'priority': return 'Critical / Major / Normal'
    case 'assignee': return '用户名或ID'
    case 'due_date': return '+7d 或 2026-08-01'
    case 'issue_type': return 'Bug / Task / Feature'
    default: return '值'
  }
}

function addAction() {
  executionActions.value.push({ type: '' })
}

function removeAction(idx: number) {
  executionActions.value.splice(idx, 1)
}

// ==================== CRUD ====================

async function loadDefinitions() {
  loading.value = true
  try {
    const res = await quickActionApi.listDefinitions()
    if (res.code === 0) definitions.value = res.data || []
  } catch (e) {
    // silent
  } finally {
    loading.value = false
  }
}

function showCreateForm() {
  editingDef.value = null
  Object.assign(form, {
    actionKey: '', label: '', icon: '', actionType: 'rule',
    formSchema: '[]', actions: '[]', statusTransitionTo: null
  })
  visibilityRoles.value = []
  visibilityStatuses.value = []
  executionActions.value = []
  formVisible.value = true
}

function handleEdit(def: QuickActionDefinitionVO) {
  editingDef.value = def
  Object.assign(form, {
    actionKey: def.actionKey,
    label: def.label,
    icon: def.icon || '',
    actionType: def.actionType || 'form',
    formSchema: def.formSchema || '[]',
    actions: def.actions || '[]',
    statusTransitionTo: def.statusTransitionTo || null,
  })

  // Parse visibility
  try {
    const vis = JSON.parse(def.visibility || '{}')
    visibilityRoles.value = vis.roles || []
    visibilityStatuses.value = vis.issueStatuses || []
  } catch {
    visibilityRoles.value = []
    visibilityStatuses.value = []
  }

  // Parse execution actions
  executionActions.value = parseExecutionActions(def.executionActions || '[]')
  formVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate()
  if (valid) return

  submitting.value = true
  try {
    // Build visibility JSON
    const visibility: Record<string, any> = {}
    if (visibilityRoles.value.length > 0) visibility.roles = visibilityRoles.value
    if (visibilityStatuses.value.length > 0) visibility.issueStatuses = visibilityStatuses.value

    const dto: any = {
      actionKey: form.actionKey,
      label: form.label,
      icon: form.icon || null,
      actionType: form.actionType,
      formSchema: form.formSchema || '[]',
      actions: form.actions || '[]',
      visibility: JSON.stringify(visibility),
      statusTransitionTo: form.statusTransitionTo || null,
      executionActions: JSON.stringify(executionActions.value.filter(a => a.type)),
      enabled: true,
      sortOrder: 0,
    }

    if (editingDef.value) {
      await quickActionApi.updateDefinition(editingDef.value.id, dto)
      Message.success('动作已更新')
    } else {
      await quickActionApi.createDefinition(dto)
      Message.success('动作已创建')
    }
    formVisible.value = false
    await loadDefinitions()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleToggle(def: QuickActionDefinitionVO) {
  try {
    await quickActionApi.updateDefinition(def.id, {
      ...def,
      enabled: !def.enabled
    })
    await loadDefinitions()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDelete(def: QuickActionDefinitionVO) {
  try {
    await quickActionApi.deleteDefinition(def.id)
    Message.success('动作已删除')
    await loadDefinitions()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

// ==================== Lifecycle ====================
onMounted(loadDefinitions)
</script>

<style scoped>
.action-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.action-name {
  font-weight: 500;
  color: var(--color-text-1);
}

.action-key {
  font-size: 11px;
  color: var(--color-text-4);
  font-family: monospace;
}

.visibility-text {
  font-size: 12px;
  color: var(--color-text-3);
}

.actions-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.actions-text {
  font-size: 12px;
  color: var(--color-text-3);
}

/* Form */
.condition-section {
  background: var(--color-fill-1);
  border-radius: 6px;
  padding: 12px;
}

.nested-item {
  margin-bottom: 0;
}

.execution-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
}

.code-input :deep(textarea) {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
}
</style>
