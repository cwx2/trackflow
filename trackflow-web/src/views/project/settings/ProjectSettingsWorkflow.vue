<template>
  <div class="settings-workflow">
    <!-- 归档提示 -->
    <div v-if="isArchived" class="archived-notice">
      <icon-lock class="notice-icon" />
      <span>项目已归档，工作流设置为只读状态</span>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <a-spin :size="24" />
    </div>

    <template v-else>
      <!-- 状态转换概览 Section -->
      <div class="workflow-section">
        <div class="section-header">
          <div class="section-info">
            <h3 class="section-title">状态转换规则</h3>
            <p class="section-desc">
              定义此项目中工单状态转换的允许路径。未单独配置项目规则时使用全局默认工作流。
            </p>
          </div>
          <a-button
            v-if="canManage && !isArchived"
            size="small"
            @click="goToWorkflowEditor"
          >
            <template #icon><icon-settings /></template>
            编辑转换矩阵
          </a-button>
        </div>

        <!-- 转换规则概览 -->
        <div class="transition-summary">
          <div class="summary-stats">
            <div class="stat-item">
              <span class="stat-value">{{ transitionStats.totalTransitions }}</span>
              <span class="stat-label">转换规则</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ transitionStats.statusCount }}</span>
              <span class="stat-label">状态数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ transitionStats.scope }}</span>
              <span class="stat-label">作用域</span>
            </div>
          </div>
          <div v-if="!transitionStats.hasProjectOverride" class="scope-notice">
            <icon-info-circle />
            <span>当前使用全局工作流规则。点击"编辑转换矩阵"可为此项目创建独立规则。</span>
          </div>
        </div>
      </div>

      <!-- 分隔线 -->
      <div class="section-divider"></div>

      <!-- 自动化规则 Section -->
      <div class="workflow-section">
        <div class="section-header">
          <div class="section-info">
            <h3 class="section-title">自动化规则</h3>
            <p class="section-desc">
              当工单创建或字段变更时自动执行的规则。全局规则自动对此项目生效。
            </p>
          </div>
          <a-button
            v-if="canManage && !isArchived"
            type="primary"
            size="small"
            @click="showCreateRuleModal"
          >
            <template #icon><icon-plus /></template>
            创建规则
          </a-button>
        </div>

        <!-- 规则列表 -->
        <div v-if="rules.length > 0" class="rules-list">
          <div
            v-for="rule in rules"
            :key="rule.id"
            class="rule-card"
            :class="{ disabled: !rule.enabled, 'is-global': rule.projectId === null }"
          >
            <div class="rule-main">
              <div class="rule-header">
                <span class="rule-name">{{ rule.name }}</span>
                <a-tag :color="eventColor(rule.triggerEvent)" size="small">
                  {{ eventLabel(rule.triggerEvent) }}
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
            <div class="rule-actions" v-if="canManage && !isArchived">
              <a-switch
                :model-value="rule.enabled"
                size="small"
                :disabled="rule.projectId === null"
                @change="handleToggleRule(rule)"
              />
              <template v-if="rule.projectId !== null">
                <a-button size="mini" @click="handleEditRule(rule)">编辑</a-button>
                <a-popconfirm
                  content="确定删除此规则？"
                  @ok="handleDeleteRule(rule)"
                >
                  <a-button size="mini" type="text" status="danger">删除</a-button>
                </a-popconfirm>
              </template>
              <a-tooltip v-else content="全局规则只能在全局工作流编辑器中管理">
                <a-button size="mini" disabled>全局管理</a-button>
              </a-tooltip>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <icon-thunderbolt class="empty-icon" />
          <h4 class="empty-title">暂无自动化规则</h4>
          <p class="empty-desc">创建自动化规则，在工单创建或字段变更时自动执行动作。</p>
          <a-button v-if="canManage && !isArchived" type="primary" size="small" @click="showCreateRuleModal">
            <template #icon><icon-plus /></template>
            创建第一条规则
          </a-button>
        </div>
      </div>
    </template>

    <!-- 创建/编辑规则弹窗 -->
    <a-modal
      v-model:visible="ruleModalVisible"
      :title="editingRule ? '编辑规则' : '创建规则'"
      :width="680"
      @ok="handleSubmitRule"
      @cancel="ruleModalVisible = false"
      :ok-loading="submitting"
      ok-text="保存规则"
      cancel-text="取消"
      unmount-on-close
    >
      <a-form :model="ruleForm" layout="vertical" ref="ruleFormRef">
        <a-form-item label="规则名称" field="name" :rules="[{ required: true, message: '请输入规则名称' }]">
          <a-input v-model="ruleForm.name" placeholder="如：Bug 创建时自动设置高优先级" :max-length="100" />
        </a-form-item>

        <a-form-item label="描述" field="description">
          <a-textarea v-model="ruleForm.description" placeholder="规则功能说明（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="触发事件" field="triggerEvent" :rules="[{ required: true, message: '请选择触发事件' }]">
              <a-select v-model="ruleForm.triggerEvent">
                <a-option value="issue_created">工单创建时</a-option>
                <a-option value="field_changed">字段变更时</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              v-if="ruleForm.triggerEvent === 'field_changed'"
              label="监听字段"
              field="triggerField"
            >
              <a-select v-model="ruleForm.triggerField" placeholder="全部字段" allow-clear>
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
            <div v-for="(cond, idx) in ruleForm.conditions" :key="idx" class="condition-row">
              <a-select v-model="cond.field" style="width: 140px" placeholder="字段">
                <a-option value="type">工单类型</a-option>
                <a-option value="priority">优先级</a-option>
                <a-option value="assignee">负责人</a-option>
                <a-option value="status">状态</a-option>
                <a-option value="sprint">迭代</a-option>
              </a-select>
              <a-select v-model="cond.operator" style="width: 130px" placeholder="操作符">
                <a-option value="equals">等于</a-option>
                <a-option value="not_equals">不等于</a-option>
                <a-option value="contains">包含</a-option>
                <a-option value="in">属于</a-option>
                <a-option value="is_empty">为空</a-option>
                <a-option value="is_not_empty">不为空</a-option>
              </a-select>
              <a-input
                v-if="!['is_empty', 'is_not_empty'].includes(cond.operator)"
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
            <div v-for="(act, idx) in ruleForm.actions" :key="idx" class="action-row">
              <a-select v-model="act.type" style="width: 140px" placeholder="动作类型">
                <a-option value="set_field">设置字段</a-option>
                <a-option value="add_tag">添加标签</a-option>
                <a-option value="add_comment">添加评论</a-option>
              </a-select>

              <template v-if="act.type === 'set_field'">
                <a-select v-model="act.field" style="width: 120px" placeholder="字段">
                  <a-option value="priority">优先级</a-option>
                  <a-option value="assignee">负责人</a-option>
                  <a-option value="issue_type">工单类型</a-option>
                </a-select>
                <a-input v-model="act.value" style="flex: 1" placeholder="新值" />
              </template>

              <template v-else-if="act.type === 'add_tag'">
                <a-input v-model="act.tagId" style="flex: 1" placeholder="标签 ID" />
              </template>

              <template v-else-if="act.type === 'add_comment'">
                <a-input v-model="act.content" style="flex: 1" placeholder="评论内容" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { IconLock, IconSettings, IconPlus, IconThunderbolt, IconInfoCircle, IconDelete } from '@arco-design/web-vue/es/icon'
import { workflowApi, workflowRuleApi } from '@/api'
import type { WorkflowRuleVO, WorkflowRuleDTO } from '@/api/workflowRule'
import type { ProjectDetailVO } from '@/api/types'

const props = defineProps<{
  project: ProjectDetailVO
  canManage: boolean
  isArchived: boolean
}>()

const router = useRouter()

// ==================== State ====================
const loading = ref(false)
const rules = ref<WorkflowRuleVO[]>([])
const ruleModalVisible = ref(false)
const editingRule = ref<WorkflowRuleVO | null>(null)
const submitting = ref(false)
const ruleFormRef = ref()

const transitionStats = ref({
  totalTransitions: 0,
  statusCount: 0,
  scope: '全局',
  hasProjectOverride: false
})

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

const ruleForm = reactive({
  name: '',
  description: '',
  triggerEvent: 'issue_created',
  triggerField: null as string | null,
  conditions: [] as ConditionItem[],
  actions: [] as ActionItem[]
})

// ==================== Data Loading ====================
async function loadData() {
  loading.value = true
  try {
    await Promise.all([loadTransitionStats(), loadRules()])
  } finally {
    loading.value = false
  }
}

async function loadTransitionStats() {
  try {
    // Try project-level first
    const res = await workflowApi.getTransitionMatrix(props.project.id)
    if (res.code === 0 && res.data) {
      const transitions = res.data.transitions || []
      // Each transition in the result IS an allowed transition
      const projectTransitionCount = transitions.length

      // Collect unique status IDs from transitions
      const statusIds = new Set<string>()
      transitions.forEach((t: any) => {
        statusIds.add(t.oldStatusId)
        statusIds.add(t.newStatusId)
      })

      if (projectTransitionCount > 0) {
        transitionStats.value = {
          totalTransitions: projectTransitionCount,
          statusCount: statusIds.size,
          scope: '项目级',
          hasProjectOverride: true
        }
      } else {
        // No project-level rules, check global
        const globalRes = await workflowApi.getTransitionMatrix('0')
        if (globalRes.code === 0 && globalRes.data) {
          const gTransitions = globalRes.data.transitions || []
          const gStatusIds = new Set<string>()
          gTransitions.forEach((t: any) => {
            gStatusIds.add(t.oldStatusId)
            gStatusIds.add(t.newStatusId)
          })
          transitionStats.value = {
            totalTransitions: gTransitions.length,
            statusCount: gStatusIds.size,
            scope: '全局',
            hasProjectOverride: false
          }
        }
      }
    }
  } catch {
    // Use defaults
  }
}

async function loadRules() {
  try {
    const res = await workflowRuleApi.list(props.project.id)
    if (res.code === 0) {
      rules.value = res.data
    }
  } catch {
    // silent
  }
}

// ==================== Actions ====================
function goToWorkflowEditor() {
  // Navigate to the workflow editor page with project pre-selected
  router.push(`/workflow?project=${props.project.id}`)
}

function showCreateRuleModal() {
  editingRule.value = null
  Object.assign(ruleForm, {
    name: '',
    description: '',
    triggerEvent: 'issue_created',
    triggerField: null,
    conditions: [],
    actions: []
  })
  ruleModalVisible.value = true
}

function handleEditRule(rule: WorkflowRuleVO) {
  editingRule.value = rule
  const conditions = parseJson(rule.conditionJson, [])
  const actions = parseJson(rule.actionJson, [])
  Object.assign(ruleForm, {
    name: rule.name,
    description: rule.description || '',
    triggerEvent: rule.triggerEvent,
    triggerField: rule.triggerField,
    conditions,
    actions
  })
  ruleModalVisible.value = true
}

async function handleSubmitRule() {
  const valid = await ruleFormRef.value?.validate()
  if (valid) return

  submitting.value = true
  try {
    const dto: WorkflowRuleDTO = {
      name: ruleForm.name,
      description: ruleForm.description || undefined,
      triggerEvent: ruleForm.triggerEvent,
      triggerField: ruleForm.triggerField || undefined,
      conditionJson: JSON.stringify(ruleForm.conditions.filter(c => c.field)),
      actionJson: JSON.stringify(ruleForm.actions.filter(a => a.type)),
      enabled: true
    }

    if (editingRule.value) {
      await workflowRuleApi.update(editingRule.value.id, dto)
      Message.success('规则已更新')
    } else {
      await workflowRuleApi.create(props.project.id, dto)
      Message.success('规则已创建')
    }
    ruleModalVisible.value = false
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleToggleRule(rule: WorkflowRuleVO) {
  try {
    await workflowRuleApi.toggle(rule.id)
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDeleteRule(rule: WorkflowRuleVO) {
  try {
    await workflowRuleApi.delete(rule.id)
    Message.success('规则已删除')
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

// ==================== Form helpers ====================
function addCondition() {
  ruleForm.conditions.push({ field: '', operator: 'equals', value: '' })
}

function removeCondition(idx: number) {
  ruleForm.conditions.splice(idx, 1)
}

function addAction() {
  ruleForm.actions.push({ type: 'set_field', field: '', value: '' })
}

function removeAction(idx: number) {
  ruleForm.actions.splice(idx, 1)
}

function validateActions(_value: any, callback: (msg?: string) => void) {
  if (ruleForm.actions.length === 0) {
    callback('请至少添加一个动作')
  } else {
    callback()
  }
}

// ==================== Display helpers ====================
function eventLabel(event: string) {
  const map: Record<string, string> = {
    issue_created: '创建时',
    field_changed: '变更时',
    on_schedule: '定时执行'
  }
  return map[event] || event
}

function eventColor(event: string) {
  const map: Record<string, string> = {
    issue_created: 'green',
    field_changed: 'blue',
    on_schedule: 'purple'
  }
  return map[event] || 'gray'
}

function fieldLabel(field: string) {
  const map: Record<string, string> = {
    issue_type: '类型', priority: '优先级', assignee: '负责人',
    sprint: '迭代', title: '标题', due_date: '截止日期',
    type: '类型', status: '状态'
  }
  return map[field] || field
}

function conditionSummary(json: string): string {
  const conditions = parseJson(json, [])
  if (conditions.length === 0) return '无条件（始终触发）'
  return conditions.map((c: any) => {
    const op: Record<string, string> = { equals: '=', not_equals: '≠', contains: '含', in: '∈', is_empty: '为空', is_not_empty: '非空' }
    return `${fieldLabel(c.field)} ${op[c.operator] || c.operator} ${c.value || ''}`
  }).join(' 且 ')
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
onMounted(loadData)
</script>

<style scoped>
.settings-workflow {
  padding: 0;
}

.archived-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 6px;
  background: var(--color-warning-light-1, rgba(255, 125, 0, 0.1));
  color: var(--color-warning-6, #ff7d00);
  font-size: 13px;
  margin-bottom: 24px;
}

.notice-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

/* Sections */
.workflow-section {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-info {
  flex: 1;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary, var(--color-text-1));
  margin: 0 0 4px;
  line-height: 1.3;
}

.section-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  margin: 0;
}

.section-divider {
  height: 1px;
  background: var(--color-border-2);
  margin: 24px 0;
}

/* Transition summary */
.transition-summary {
  padding: 16px;
  border-radius: 6px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
}

.summary-stats {
  display: flex;
  gap: 32px;
  margin-bottom: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary, var(--color-text-1));
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--tf-text-tertiary, var(--color-text-3));
}

.scope-notice {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--tf-text-secondary, var(--color-text-2));
  padding-top: 8px;
  border-top: 1px solid var(--color-border-1);
}

/* Rules list */
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

.rule-card.is-global {
  border-left: 3px solid var(--color-warning-6, #ff7d00);
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

.summary-section .summary-label {
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
  border-radius: 6px;
  background: var(--color-bg-2);
  border: 1px dashed var(--color-border-2);
}

.empty-icon {
  font-size: 48px;
  color: var(--color-text-4);
}

.empty-title {
  margin: 16px 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

.empty-desc {
  font-size: 13px;
  color: var(--color-text-3);
  margin: 0 0 16px;
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
</style>
