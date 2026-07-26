<template>
  <div class="workflow-rules">
    <!-- 工具栏 -->
    <div class="rules-toolbar">
      <a-space>
        <a-input
          v-model="searchKeyword"
          placeholder="搜索规则..."
          style="width: 200px"
          allow-clear
        >
          <template #prefix><icon-search /></template>
        </a-input>
        <a-select
          v-model="filterEvent"
          placeholder="触发事件"
          allow-clear
          style="width: 160px"
        >
          <a-option value="issue_created">工单创建时</a-option>
          <a-option value="field_changed">字段变更时</a-option>
        </a-select>
      </a-space>
      <a-button type="primary" @click="showCreateModal">
        <template #icon><icon-plus /></template>
        创建规则
      </a-button>
    </div>

    <!-- 规则列表 -->
    <a-spin :loading="loading">
      <div v-if="filteredRules.length > 0" class="rules-list">
        <div
          v-for="rule in filteredRules"
          :key="rule.id"
          class="rule-card"
          :class="{ disabled: !rule.enabled }"
        >
          <div class="rule-main">
            <div class="rule-header">
              <span class="rule-name">{{ rule.name }}</span>
              <a-tag :color="eventColor(rule.triggerEvent)" size="small">
                {{ eventLabel(rule.triggerEvent) }}
              </a-tag>
              <a-tag v-if="rule.triggerField" size="small" color="gray">
                字段: {{ fieldLabel(rule.triggerField) }}
              </a-tag>
              <a-tag v-if="rule.projectId === null" size="small" color="orangered">
                全局
              </a-tag>
            </div>
            <div v-if="rule.description" class="rule-description">{{ rule.description }}</div>
            <div class="rule-summary">
              <span class="summary-section">
                <span class="summary-label">条件:</span>
                {{ conditionSummary(rule.conditionJson) }}
              </span>
              <span class="summary-divider">→</span>
              <span class="summary-section">
                <span class="summary-label">动作:</span>
                {{ actionSummary(rule.actionJson) }}
              </span>
            </div>
          </div>
          <div class="rule-actions">
            <a-switch
              :model-value="rule.enabled"
              size="small"
              @change="handleToggle(rule)"
            />
            <a-button size="mini" @click="handleEdit(rule)">编辑</a-button>
            <a-popconfirm
              content="确定删除此规则？"
              @ok="handleDelete(rule)"
            >
              <a-button size="mini" type="text" status="danger">删除</a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <icon-thunderbolt style="font-size: 48px; color: var(--color-text-4)" />
        <h3>暂无自动化规则</h3>
        <p>创建 on-change 规则，在工单创建或字段变更时自动执行动作。</p>
        <a-button type="primary" @click="showCreateModal">
          <template #icon><icon-plus /></template>
          创建第一条规则
        </a-button>
      </div>
    </a-spin>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="modalVisible"
      :title="editingRule ? '编辑规则' : '创建规则'"
      :width="680"
      @ok="handleSubmit"
      @cancel="modalVisible = false"
      :ok-loading="submitting"
      ok-text="保存规则"
      cancel-text="取消"
      unmount-on-close
    >
      <a-form :model="formData" layout="vertical" ref="formRef">
        <!-- 基本信息 -->
        <a-form-item label="规则名称" field="name" :rules="[{ required: true, message: '请输入规则名称' }]">
          <a-input v-model="formData.name" placeholder="如：Bug 创建时自动设置高优先级" :max-length="100" />
        </a-form-item>

        <a-form-item label="描述" field="description">
          <a-textarea v-model="formData.description" placeholder="规则功能说明（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="触发事件" field="triggerEvent" :rules="[{ required: true, message: '请选择触发事件' }]">
              <a-select v-model="formData.triggerEvent">
                <a-option value="issue_created">工单创建时</a-option>
                <a-option value="field_changed">字段变更时</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              v-if="formData.triggerEvent === 'field_changed'"
              label="监听字段"
              field="triggerField"
            >
              <a-select v-model="formData.triggerField" placeholder="全部字段" allow-clear>
                <a-option value="status_id">状态</a-option>
                <a-option value="issue_type">工单类型</a-option>
                <a-option value="priority">优先级</a-option>
                <a-option value="assignee">负责人</a-option>
                <a-option value="sprint">迭代</a-option>
                <a-option value="title">标题</a-option>
                <a-option value="due_date">截止日期</a-option>
              </a-select>
              <template #extra>为空表示任意字段变更都触发</template>
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 前置条件 -->
        <a-form-item label="前置条件" field="conditions">
          <template #extra>所有条件为 AND 关系（全部满足才触发）</template>
          <div class="condition-list">
            <div v-for="(cond, idx) in formData.conditions" :key="idx" class="condition-row">
              <a-select v-model="cond.field" style="width: 140px" placeholder="字段">
                <a-option value="type">工单类型</a-option>
                <a-option value="priority">优先级</a-option>
                <a-option value="assignee">负责人</a-option>
                <a-option value="status">状态</a-option>
                <a-option value="sprint">迭代</a-option>
              </a-select>
              <a-select v-model="cond.operator" style="width: 150px" placeholder="操作符" @change="() => { cond.value = '' }">
                <a-optgroup label="当前值匹配">
                  <a-option value="equals">等于</a-option>
                  <a-option value="not_equals">不等于</a-option>
                  <a-option value="contains">包含</a-option>
                  <a-option value="in">属于（逗号分隔）</a-option>
                  <a-option value="is_empty">为空</a-option>
                  <a-option value="is_not_empty">不为空</a-option>
                </a-optgroup>
                <!-- 仅当触发事件为 field_changed 且选择了监听字段时，显示旧值匹配操作符 -->
                <a-optgroup
                  v-if="formData.triggerEvent === 'field_changed' && formData.triggerField"
                  label="变更前值匹配（旧值）"
                >
                  <a-option value="old_value_equals">旧值等于</a-option>
                  <a-option value="old_value_not_equals">旧值不等于</a-option>
                  <a-option value="old_value_in">旧值属于（逗号分隔）</a-option>
                  <a-option value="old_value_is_empty">旧值为空</a-option>
                  <a-option value="old_value_is_not_empty">旧值不为空</a-option>
                </a-optgroup>
              </a-select>
              <a-input
                v-if="!['is_empty', 'is_not_empty', 'old_value_is_empty', 'old_value_is_not_empty'].includes(cond.operator)"
                v-model="cond.value"
                style="flex: 1"
                placeholder="值（如 Bug, Critical）"
              />
              <a-button type="text" status="danger" size="mini" @click="removeCondition(idx)">
                <icon-delete />
              </a-button>
            </div>
            <a-button type="dashed" size="small" @click="addCondition" long>
              <template #icon><icon-plus /></template>
              添加条件
            </a-button>
          </div>
        </a-form-item>

        <!-- 执行动作 -->
        <a-form-item label="执行动作" field="actions" :rules="[{ validator: validateActions }]">
          <div class="action-list">
            <div v-for="(act, idx) in formData.actions" :key="idx" class="action-row">
              <a-select v-model="act.type" style="width: 140px" placeholder="动作类型">
                <a-option value="set_field">设置字段</a-option>
                <a-option value="add_tag">添加标签</a-option>
                <a-option value="add_comment">添加评论</a-option>
              </a-select>

              <!-- set_field -->
              <template v-if="act.type === 'set_field'">
                <a-select v-model="act.field" style="width: 120px" placeholder="字段" @change="() => { act.value = '' }">
                  <a-option value="priority">优先级</a-option>
                  <a-option value="assignee">负责人</a-option>
                  <a-option value="issue_type">工单类型</a-option>
                  <a-option value="status">状态</a-option>
                  <a-option value="sprint">迭代</a-option>
                  <a-option value="due_date">截止日期</a-option>
                </a-select>
                <!-- 状态：下拉选择器 -->
                <a-select
                  v-if="act.field === 'status'"
                  v-model="act.value"
                  style="flex: 1"
                  placeholder="选择目标状态"
                  :loading="statusesLoading"
                  allow-search
                >
                  <a-option
                    v-for="s in availableStatuses"
                    :key="s.id"
                    :value="s.id"
                  >
                    <span class="status-option">
                      <span
                        class="status-dot"
                        :style="{ backgroundColor: s.color || '#999' }"
                      ></span>
                      {{ s.name }}
                    </span>
                  </a-option>
                </a-select>
                <!-- 迭代：下拉选择器 -->
                <a-select
                  v-else-if="act.field === 'sprint'"
                  v-model="act.value"
                  style="flex: 1"
                  placeholder="选择目标迭代"
                  :loading="sprintsLoading"
                  allow-search
                >
                  <a-option
                    v-for="sp in availableSprints"
                    :key="sp.id"
                    :value="sp.id"
                  >{{ sp.name }}</a-option>
                </a-select>
                <!-- 截止日期：支持绝对日期或相对值 (+7d) -->
                <a-input
                  v-else-if="act.field === 'due_date'"
                  v-model="act.value"
                  style="flex: 1"
                  placeholder="日期 (YYYY-MM-DD) 或相对值 (+7d, -3d)"
                />
                <!-- 其他字段：文本输入 -->
                <a-input
                  v-else
                  v-model="act.value"
                  style="flex: 1"
                  placeholder="新值"
                />
              </template>

              <!-- add_tag -->
              <template v-else-if="act.type === 'add_tag'">
                <a-input v-model="act.tagId" style="flex: 1" placeholder="标签 ID" />
              </template>

              <!-- add_comment -->
              <template v-else-if="act.type === 'add_comment'">
                <a-input v-model="act.content" style="flex: 1" placeholder="评论内容（支持 {{rule_name}}、{{issue_key}}）" />
              </template>

              <a-button type="text" status="danger" size="mini" @click="removeAction(idx)">
                <icon-delete />
              </a-button>
            </div>
            <a-button type="dashed" size="small" @click="addAction" long>
              <template #icon><icon-plus /></template>
              添加动作
            </a-button>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { workflowRuleApi, issueApi, sprintApi } from '@/api'
import type { WorkflowRuleVO, WorkflowRuleDTO } from '@/api/workflowRule'
import type { IssueStatusVO, SprintVO } from '@/api/types'

const props = defineProps<{
  projectId: string
}>()

// ==================== State ====================
const loading = ref(false)
const rules = ref<WorkflowRuleVO[]>([])
const searchKeyword = ref('')
const filterEvent = ref<string | undefined>(undefined)
const modalVisible = ref(false)
const editingRule = ref<WorkflowRuleVO | null>(null)
const submitting = ref(false)
const formRef = ref()

interface ConditionItem {
  field: string
  operator: string
  value: string
}

interface ActionItem {
  type: string
  field?: string
  value?: string
  tagId?: string
  content?: string
}

const formData = reactive({
  name: '',
  description: '',
  triggerEvent: 'issue_created',
  triggerField: null as string | null,
  conditions: [] as ConditionItem[],
  actions: [] as ActionItem[]
})

// ==================== Status & Sprint Data ====================
const availableStatuses = ref<IssueStatusVO[]>([])
const statusesLoading = ref(false)
const availableSprints = ref<SprintVO[]>([])
const sprintsLoading = ref(false)

async function loadStatuses() {
  if (availableStatuses.value.length > 0) return
  statusesLoading.value = true
  try {
    const res = await issueApi.listStatuses()
    if (res.code === 0) {
      availableStatuses.value = res.data
    }
  } catch {
    // silent
  } finally {
    statusesLoading.value = false
  }
}

async function loadSprints() {
  if (!props.projectId || availableSprints.value.length > 0) return
  sprintsLoading.value = true
  try {
    const res = await sprintApi.listByProject(props.projectId)
    if (res.code === 0) {
      availableSprints.value = res.data
    }
  } catch {
    // silent
  } finally {
    sprintsLoading.value = false
  }
}

// ==================== Computed ====================
const filteredRules = computed(() => {
  let list = rules.value.filter(r => r.ruleType !== 'on_schedule')
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(r => r.name.toLowerCase().includes(kw))
  }
  if (filterEvent.value) {
    list = list.filter(r => r.triggerEvent === filterEvent.value)
  }
  return list
})

// ==================== Data Loading ====================
async function loadRules() {
  loading.value = true
  try {
    const res = await workflowRuleApi.list(props.projectId)
    if (res.code === 0) {
      rules.value = res.data
    }
  } catch (e) {
    // silent
  } finally {
    loading.value = false
  }
}

// ==================== Form ====================
function showCreateModal() {
  editingRule.value = null
  Object.assign(formData, {
    name: '',
    description: '',
    triggerEvent: 'issue_created',
    triggerField: null,
    conditions: [],
    actions: []
  })
  modalVisible.value = true
  // 预加载状态和迭代数据
  loadStatuses()
  loadSprints()
}

function handleEdit(rule: WorkflowRuleVO) {
  editingRule.value = rule
  const conditions = parseJson(rule.conditionJson, [])
  const actions = parseJson(rule.actionJson, [])
  Object.assign(formData, {
    name: rule.name,
    description: rule.description || '',
    triggerEvent: rule.triggerEvent,
    triggerField: rule.triggerField,
    conditions,
    actions
  })
  modalVisible.value = true
  // 预加载状态和迭代数据
  loadStatuses()
  loadSprints()
}

function addCondition() {
  formData.conditions.push({ field: '', operator: 'equals', value: '' })
}

function removeCondition(idx: number) {
  formData.conditions.splice(idx, 1)
}

function addAction() {
  formData.actions.push({ type: 'set_field', field: '', value: '' })
}

function removeAction(idx: number) {
  formData.actions.splice(idx, 1)
}

function validateActions(_value: any, callback: (msg?: string) => void) {
  if (formData.actions.length === 0) {
    callback('请至少添加一个动作')
  } else {
    callback()
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate()
  if (valid) return

  submitting.value = true
  try {
    const dto: WorkflowRuleDTO = {
      name: formData.name,
      description: formData.description || undefined,
      ruleType: 'on_change',
      triggerEvent: formData.triggerEvent,
      triggerField: formData.triggerField || undefined,
      conditionJson: JSON.stringify(formData.conditions.filter(c => c.field)),
      actionJson: JSON.stringify(formData.actions.filter(a => a.type)),
      enabled: true
    }

    if (editingRule.value) {
      await workflowRuleApi.update(editingRule.value.id, dto)
      Message.success('规则已更新')
    } else {
      await workflowRuleApi.create(props.projectId, dto)
      Message.success('规则已创建')
    }
    modalVisible.value = false
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ==================== Actions ====================
async function handleToggle(rule: WorkflowRuleVO) {
  try {
    await workflowRuleApi.toggle(rule.id)
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDelete(rule: WorkflowRuleVO) {
  try {
    await workflowRuleApi.delete(rule.id)
    Message.success('规则已删除')
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

// ==================== Helpers ====================
function eventLabel(event: string) {
  const map: Record<string, string> = {
    issue_created: '创建时',
    field_changed: '变更时'
  }
  return map[event] || event
}

function eventColor(event: string) {
  return event === 'issue_created' ? 'green' : 'blue'
}

function fieldLabel(field: string) {
  const map: Record<string, string> = {
    status_id: '状态', issue_type: '类型', priority: '优先级', assignee: '负责人',
    sprint: '迭代', title: '标题', due_date: '截止日期',
    type: '类型', status: '状态'
  }
  return map[field] || field
}

function operatorLabel(op: string) {
  const map: Record<string, string> = {
    equals: '等于', not_equals: '不等于', contains: '包含', in: '属于',
    is_empty: '为空', is_not_empty: '不为空',
    old_value_equals: '旧值等于', old_value_not_equals: '旧值不等于',
    old_value_in: '旧值属于', old_value_is_empty: '旧值为空', old_value_is_not_empty: '旧值不为空'
  }
  return map[op] || op
}

function conditionSummary(json: string): string {
  const conditions = parseJson(json, [])
  if (conditions.length === 0) return '无条件（始终触发）'
  return conditions.map((c: any) => {
    const isEmptyOp = ['is_empty', 'is_not_empty', 'old_value_is_empty', 'old_value_is_not_empty'].includes(c.operator)
    if (isEmptyOp) {
      return `${fieldLabel(c.field)} ${operatorLabel(c.operator)}`
    }
    return `${fieldLabel(c.field)} ${operatorLabel(c.operator)} "${c.value}"`
  }).join(' AND ')
}

function actionSummary(json: string): string {
  const actions = parseJson(json, [])
  if (actions.length === 0) return '无动作'
  return actions.map((a: any) => {
    switch (a.type) {
      case 'set_field': return `设置 ${fieldLabel(a.field)}=${a.value}`
      case 'add_tag': return `添加标签 #${a.tagId}`
      case 'add_comment': return '添加评论'
      default: return a.type
    }
  }).join(', ')
}

function parseJson(str: string, fallback: any[]): any[] {
  try {
    const parsed = JSON.parse(str)
    return Array.isArray(parsed) ? parsed : fallback
  } catch {
    return fallback
  }
}

// ==================== Lifecycle ====================
onMounted(loadRules)

watch(() => props.projectId, () => {
  availableSprints.value = [] // 切换项目时清空缓存，下次打开弹窗时重新加载
  loadRules()
})
</script>

<style scoped>
.workflow-rules {
  padding: 16px 0;
}

.rules-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

/* Rule cards */
.rules-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rule-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 6px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  transition: border-color 150ms;
}

.rule-card:hover {
  border-color: var(--color-primary-light-4);
}

.rule-card.disabled {
  opacity: 0.5;
}

.rule-main {
  flex: 1;
  min-width: 0;
}

.rule-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.rule-name {
  font-weight: 500;
  font-size: 14px;
  color: var(--color-text-1);
}

.rule-description {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 6px;
}

.rule-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-2);
}

.summary-label {
  color: var(--color-text-3);
}

.summary-divider {
  color: var(--color-text-4);
  font-weight: 600;
}

.rule-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 16px;
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-state h3 {
  margin: 16px 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
}

.empty-state p {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 16px;
}

/* Form */
.condition-list,
.action-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-row,
.action-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Status option in dropdown */
.status-option {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
</style>
