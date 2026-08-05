<template>
  <div class="scheduled-rules">
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
      </a-space>
      <a-button type="primary" @click="showCreateModal">
        <template #icon><icon-plus /></template>
        创建定时规则
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
              <a-tag color="purple" size="small">
                {{ scheduleLabel(rule.cronExpression) }}
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
            <div class="rule-meta">
              <span v-if="rule.lastExecutedAt" class="meta-item">
                上次执行: {{ formatTime(rule.lastExecutedAt) }}
              </span>
              <span v-else class="meta-item">尚未执行</span>
            </div>
          </div>
          <div class="rule-actions">
            <a-switch
              :model-value="rule.enabled"
              size="small"
              @change="handleToggle(rule)"
            />
            <a-button size="mini" @click="handleExecute(rule)">
              <template #icon><icon-play-arrow /></template>
              执行
            </a-button>
            <a-button size="mini" @click="handleViewLogs(rule)">日志</a-button>
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
        <icon-clock-circle style="font-size: 48px; color: var(--color-text-4)" />
        <h3>暂无定时规则</h3>
        <p>创建 on-schedule 规则，按计划定期检查工单并自动执行动作。</p>
        <a-button type="primary" @click="showCreateModal">
          <template #icon><icon-plus /></template>
          创建第一条定时规则
        </a-button>
      </div>
    </a-spin>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="modalVisible"
      :title="editingRule ? '编辑定时规则' : '创建定时规则'"
      :width="680"
      @ok="handleSubmit"
      @cancel="modalVisible = false"
      :ok-loading="submitting"
      ok-text="保存规则"
      cancel-text="取消"
      unmount-on-close
    >
      <a-form :model="formData" layout="vertical" ref="formRef">
        <a-form-item label="规则名称" field="name" :rules="[{ required: true, message: '请输入规则名称' }]">
          <a-input v-model="formData.name" placeholder="如：每天检查超期工单并添加逾期标签" :max-length="100" />
        </a-form-item>

        <a-form-item label="描述" field="description">
          <a-textarea v-model="formData.description" placeholder="规则功能说明（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>

        <a-form-item label="执行频率" field="cronExpression" :rules="[{ required: true, message: '请选择执行频率' }]">
          <a-select v-model="formData.cronExpression">
            <a-option value="hourly">每小时</a-option>
            <a-option value="daily">每天</a-option>
            <a-option value="weekly">每周</a-option>
            <a-option value="custom">自定义 Cron</a-option>
          </a-select>
        </a-form-item>

        <a-form-item
          v-if="formData.cronExpression === 'custom'"
          label="Cron 表达式"
          field="customCron"
          :rules="[{ required: true, message: '请输入 Cron 表达式' }]"
        >
          <a-input v-model="formData.customCron" placeholder="如：0 9 * * * （每天 9 点）" />
          <template #extra>标准 5 段 cron 格式：分 时 日 月 周</template>
        </a-form-item>

        <!-- 工单匹配条件 -->
        <a-form-item label="工单匹配条件">
          <template #extra>所有条件为 AND 关系（全部满足的工单才会被处理）</template>
          <div class="condition-list">
            <div v-for="(cond, idx) in formData.conditions" :key="idx" class="condition-row">
              <a-select v-model="cond.field" style="width: 140px" placeholder="字段">
                <a-option value="type">工单类型</a-option>
                <a-option value="priority">优先级</a-option>
                <a-option value="status">状态</a-option>
                <a-option value="assignee">负责人</a-option>
                <a-option value="sprint">迭代</a-option>
              </a-select>
              <a-select v-model="cond.operator" style="width: 130px" placeholder="操作符">
                <a-option value="equals">等于</a-option>
                <a-option value="not_equals">不等于</a-option>
                <a-option value="contains">包含</a-option>
                <a-option value="in">属于</a-option>
                <a-option value="is_empty">为空</a-option>
                <a-option value="is_not_empty">不为空</a-option>
                <a-option value="overdue">已逾期</a-option>
                <a-option value="due_within_days">N天内到期</a-option>
              </a-select>
              <a-input
                v-if="!['is_empty', 'is_not_empty', 'overdue'].includes(cond.operator)"
                v-model="cond.value"
                style="flex: 1"
                :placeholder="cond.operator === 'due_within_days' ? '天数（如 3）' : '值'"
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
                <VariableInput
                  v-model="act.content"
                  placeholder="评论内容（支持变量插值，如 {issue.summary}）"
                  type="input"
                  style="flex: 1"
                />
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

    <!-- 执行日志弹窗 -->
    <a-modal
      v-model:visible="logsVisible"
      title="执行日志"
      :width="640"
      :footer="false"
      unmount-on-close
    >
      <a-spin :loading="logsLoading">
        <div v-if="executionLogs.length > 0" class="logs-list">
          <div v-for="logEntry in executionLogs" :key="logEntry.id" class="log-entry">
            <div class="log-header">
              <span class="log-time">{{ formatTime(logEntry.executedAt) }}</span>
              <span class="log-duration">{{ logEntry.durationMs }}ms</span>
            </div>
            <div class="log-stats">
              <a-tag color="blue" size="small">匹配: {{ logEntry.matchedCount }}</a-tag>
              <a-tag color="green" size="small">成功: {{ logEntry.successCount }}</a-tag>
              <a-tag v-if="logEntry.failureCount > 0" color="red" size="small">失败: {{ logEntry.failureCount }}</a-tag>
            </div>
            <div v-if="logEntry.errorMessage" class="log-error">
              {{ logEntry.errorMessage }}
            </div>
          </div>
        </div>
        <div v-else class="empty-logs">
          <p>暂无执行日志</p>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { workflowRuleApi } from '@/api'
import type { WorkflowRuleVO, WorkflowRuleDTO, WorkflowRuleExecutionLogVO } from '@/api/workflowRule'
import VariableInput from './components/VariableInput.vue'

const props = defineProps<{
  projectId: string
}>()

// ==================== State ====================
const loading = ref(false)
const rules = ref<WorkflowRuleVO[]>([])
const searchKeyword = ref('')
const modalVisible = ref(false)
const editingRule = ref<WorkflowRuleVO | null>(null)
const submitting = ref(false)
const formRef = ref()
const logsVisible = ref(false)
const logsLoading = ref(false)
const executionLogs = ref<WorkflowRuleExecutionLogVO[]>([])

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
  cronExpression: 'daily',
  customCron: '',
  conditions: [] as ConditionItem[],
  actions: [] as ActionItem[]
})

// ==================== Computed ====================
const filteredRules = computed(() => {
  let list = rules.value.filter(r => r.ruleType === 'on_schedule')
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(r => r.name.toLowerCase().includes(kw))
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
    cronExpression: 'daily',
    customCron: '',
    conditions: [],
    actions: []
  })
  modalVisible.value = true
}

function handleEdit(rule: WorkflowRuleVO) {
  editingRule.value = rule
  const conditions = parseJson(rule.conditionJson, [])
  const actions = parseJson(rule.actionJson, [])
  const cron = rule.cronExpression || 'daily'
  const isPreset = ['hourly', 'daily', 'weekly'].includes(cron)
  Object.assign(formData, {
    name: rule.name,
    description: rule.description || '',
    cronExpression: isPreset ? cron : 'custom',
    customCron: isPreset ? '' : cron,
    conditions,
    actions
  })
  modalVisible.value = true
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
    const cronExpr = formData.cronExpression === 'custom' ? formData.customCron : formData.cronExpression
    const dto: WorkflowRuleDTO = {
      name: formData.name,
      description: formData.description || undefined,
      ruleType: 'on_schedule',
      triggerEvent: 'scheduled',
      conditionJson: JSON.stringify(formData.conditions.filter(c => c.field)),
      actionJson: JSON.stringify(formData.actions.filter(a => a.type)),
      enabled: true,
      cronExpression: cronExpr
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

async function handleExecute(rule: WorkflowRuleVO) {
  try {
    const res = await workflowRuleApi.execute(rule.id)
    if (res.code === 0) {
      const log = res.data
      Message.success(`执行完成：匹配 ${log.matchedCount} 个工单，成功 ${log.successCount}，失败 ${log.failureCount}`)
      await loadRules()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行失败')
  }
}

async function handleViewLogs(rule: WorkflowRuleVO) {
  logsVisible.value = true
  logsLoading.value = true
  try {
    const res = await workflowRuleApi.getExecutionLogs(rule.id, 20)
    if (res.code === 0) {
      executionLogs.value = res.data
    }
  } catch (e) {
    // silent
  } finally {
    logsLoading.value = false
  }
}

// ==================== Helpers ====================
function scheduleLabel(cron: string | null) {
  const map: Record<string, string> = {
    hourly: '每小时',
    daily: '每天',
    weekly: '每周'
  }
  return cron ? (map[cron] || `cron: ${cron}`) : '未设置'
}

function conditionSummary(json: string): string {
  const conditions = parseJson(json, [])
  if (conditions.length === 0) return '所有工单'
  return conditions.map((c: any) => {
    const op: Record<string, string> = {
      equals: '=', not_equals: '≠', contains: '含', in: '∈',
      is_empty: '为空', is_not_empty: '非空', overdue: '已逾期',
      due_within_days: '天内到期'
    }
    const opStr = op[c.operator] || c.operator
    if (c.operator === 'overdue') return '已逾期'
    if (c.operator === 'due_within_days') return `${c.value}天内到期`
    return `${fieldLabel(c.field)} ${opStr} ${c.value || ''}`
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

function fieldLabel(field: string) {
  const map: Record<string, string> = {
    issue_type: '类型', priority: '优先级', assignee: '负责人',
    sprint: '迭代', title: '标题', due_date: '截止日期',
    type: '类型', status: '状态'
  }
  return map[field] || field
}

function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
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
watch(() => props.projectId, loadRules)
</script>

<style scoped>
.scheduled-rules {
  padding: 16px 0;
}

.rules-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

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

.rule-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-4);
}

.rule-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 16px;
}

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

/* Logs */
.logs-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-entry {
  padding: 12px;
  border-radius: 6px;
  background: var(--color-fill-2);
}

.log-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.log-time {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.log-duration {
  font-size: 12px;
  color: var(--color-text-3);
}

.log-stats {
  display: flex;
  gap: 8px;
}

.log-error {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-danger-6);
}

.empty-logs {
  text-align: center;
  padding: 24px;
  color: var(--color-text-3);
}
</style>
