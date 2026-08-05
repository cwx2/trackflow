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
                <icon-history /> 上次: {{ formatTime(rule.lastExecutedAt) }}
              </span>
              <span v-else class="meta-item">尚未执行</span>
              <span v-if="getNextExecution(rule.cronExpression)" class="meta-item meta-next">
                <icon-clock-circle /> 下次: {{ getNextExecution(rule.cronExpression) }}
              </span>
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

        <a-form-item label="常用周期">
          <a-space wrap>
            <a-tag
              v-for="preset in CRON_PRESETS"
              :key="preset.value"
              :color="formData.cronExpression === preset.value ? 'arcoblue' : undefined"
              class="cron-preset-tag"
              @click="applyCronPreset(preset.value)"
            >
              {{ preset.label }}
            </a-tag>
          </a-space>
        </a-form-item>

        <a-form-item
          label="Cron 表达式"
          field="cronExpression"
          :rules="[{ required: true, message: '请输入 Cron 表达式' }]"
        >
          <a-input
            v-model="formData.cronExpression"
            placeholder="如：0 9 * * 1（每周一 09:00）"
            @input="parseCronExpression"
          />
          <template #extra>标准 5 段 cron 格式：分 时 日 月 周</template>
        </a-form-item>

        <!-- Cron 人类可读描述 -->
        <div v-if="cronDescription" class="cron-feedback cron-description">
          <icon-clock-circle /> {{ cronDescription }}
        </div>
        <div v-if="cronError" class="cron-feedback cron-error">
          <icon-exclamation-circle /> Cron 表达式格式有误
        </div>

        <!-- 下次执行时间预览 -->
        <div v-if="nextExecutions.length > 0" class="cron-next-executions">
          <div class="cron-next-label">接下来的 5 次执行：</div>
          <div v-for="(t, i) in nextExecutions" :key="i" class="cron-next-time">
            {{ t }}
          </div>
        </div>

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
                <a-option value="copy_issue">克隆工单</a-option>
                <a-option value="move_to_project">移动到项目</a-option>
                <a-option value="add_work_item">添加工时</a-option>
                <a-option value="add_vote">添加投票</a-option>
                <a-option value="remove_vote">移除投票</a-option>
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

              <!-- copy_issue -->
              <template v-else-if="act.type === 'copy_issue'">
                <div style="display: flex; flex-direction: column; gap: 6px; flex: 1">
                  <a-select v-model="act.targetProjectId" style="width: 100%" placeholder="目标项目（same=同项目）" allow-search>
                    <a-option value="same">同项目</a-option>
                    <a-option
                      v-for="p in allProjects"
                      :key="p.id"
                      :value="p.id"
                    >{{ p.name }} ({{ p.key }})</a-option>
                  </a-select>
                  <a-input v-model="act.summaryPrefix" placeholder="标题前缀（如 [COPY] ，支持变量）" />
                  <a-space>
                    <a-checkbox v-model="act.copyAttachments">复制附件</a-checkbox>
                    <a-checkbox v-model="act.copySprint">复制 Sprint</a-checkbox>
                  </a-space>
                </div>
              </template>

              <!-- move_to_project -->
              <template v-else-if="act.type === 'move_to_project'">
                <a-select v-model="act.targetProjectId" style="flex: 1" placeholder="目标项目" allow-search>
                  <a-option
                    v-for="p in allProjects"
                    :key="p.id"
                    :value="p.id"
                  >{{ p.name }} ({{ p.key }})</a-option>
                </a-select>
              </template>

              <!-- add_work_item -->
              <template v-else-if="act.type === 'add_work_item'">
                <div style="display: flex; gap: 8px; flex: 1">
                  <a-input-number v-model="act.duration" :min="1" :max="1440" style="width: 120px" placeholder="分钟" />
                  <a-input v-model="act.description" style="flex: 1" placeholder="工时描述（支持变量）" />
                </div>
              </template>

              <!-- add_vote / remove_vote -->
              <template v-else-if="act.type === 'add_vote' || act.type === 'remove_vote'">
                <span class="action-hint">以规则创建者身份{{ act.type === 'add_vote' ? '投票' : '取消投票' }}</span>
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
import cronstrue from 'cronstrue/i18n'
import { CronExpressionParser } from 'cron-parser'
import { workflowRuleApi, projectApi } from '@/api'
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

// ==================== Cron Presets ====================
const CRON_PRESETS = [
  { label: '每小时', value: '0 * * * *' },
  { label: '每天 09:00', value: '0 9 * * *' },
  { label: '每周一 09:00', value: '0 9 * * 1' },
  { label: '每周五 18:00', value: '0 18 * * 5' },
  { label: '每月1日 09:00', value: '0 9 1 * *' },
]

// ==================== Cron Parsing State ====================
const cronDescription = ref('')
const cronError = ref(false)
const nextExecutions = ref<string[]>([])

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
  // copy_issue / move_to_project
  targetProjectId?: string
  summaryPrefix?: string
  copyAttachments?: boolean
  copySprint?: boolean
  // add_work_item
  duration?: number
  description?: string
}

const formData = reactive({
  name: '',
  description: '',
  cronExpression: '0 9 * * *',
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

// ==================== Cron Parsing ====================
function parseCronExpression() {
  const expr = formData.cronExpression?.trim()
  if (!expr) {
    cronDescription.value = ''
    cronError.value = false
    nextExecutions.value = []
    return
  }
  try {
    cronDescription.value = cronstrue.toString(expr, { locale: 'zh_CN' })
    const interval = CronExpressionParser.parse(expr)
    const times: string[] = []
    for (let i = 0; i < 5; i++) {
      const next = interval.next()
      times.push(formatDateTime(next.toDate()))
    }
    nextExecutions.value = times
    cronError.value = false
  } catch {
    cronDescription.value = ''
    nextExecutions.value = []
    cronError.value = true
  }
}

function applyCronPreset(value: string) {
  formData.cronExpression = value
  parseCronExpression()
}

function getNextExecution(cronExpr: string | null): string {
  if (!cronExpr) return ''
  // Skip named presets (legacy data)
  if (['hourly', 'daily', 'weekly'].includes(cronExpr)) {
    const map: Record<string, string> = {
      hourly: '0 * * * *',
      daily: '0 9 * * *',
      weekly: '0 9 * * 1'
    }
    cronExpr = map[cronExpr]
  }
  try {
    const interval = CronExpressionParser.parse(cronExpr)
    return formatDateTime(interval.next().toDate())
  } catch {
    return ''
  }
}

function formatDateTime(date: Date): string {
  const weekDays = ['日', '一', '二', '三', '四', '五', '六']
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  const w = weekDays[date.getDay()]
  return `${y}-${m}-${d} ${h}:${min} (周${w})`
}

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

// ==================== Project Data (for copy_issue / move_to_project) ====================
const allProjects = ref<Array<{ id: string; name: string; key: string }>>([])

async function loadProjects() {
  if (allProjects.value.length > 0) return
  try {
    const res = await projectApi.list({ page: 1, pageSize: 200 })
    if (res.code === 0) {
      allProjects.value = (res.data?.list || []).map((p: any) => ({
        id: p.id,
        name: p.name,
        key: p.key
      }))
    }
  } catch {
    // silent
  }
}

// ==================== Form ====================
function showCreateModal() {
  editingRule.value = null
  Object.assign(formData, {
    name: '',
    description: '',
    cronExpression: '0 9 * * *',
    conditions: [],
    actions: []
  })
  cronDescription.value = ''
  cronError.value = false
  nextExecutions.value = []
  modalVisible.value = true
  // Parse default expression
  parseCronExpression()
  loadProjects()
}

function handleEdit(rule: WorkflowRuleVO) {
  editingRule.value = rule
  const conditions = parseJson(rule.conditionJson, [])
  const actions = parseJson(rule.actionJson, [])
  // Convert legacy preset names to cron expressions
  let cronExpr = rule.cronExpression || '0 9 * * *'
  const legacyMap: Record<string, string> = {
    hourly: '0 * * * *',
    daily: '0 9 * * *',
    weekly: '0 9 * * 1'
  }
  if (legacyMap[cronExpr]) {
    cronExpr = legacyMap[cronExpr]
  }
  Object.assign(formData, {
    name: rule.name,
    description: rule.description || '',
    cronExpression: cronExpr,
    conditions,
    actions
  })
  modalVisible.value = true
  parseCronExpression()
  loadProjects()
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
      ruleType: 'on_schedule',
      triggerEvent: 'scheduled',
      conditionJson: JSON.stringify(formData.conditions.filter(c => c.field)),
      actionJson: JSON.stringify(formData.actions.filter(a => a.type)),
      enabled: true,
      cronExpression: formData.cronExpression.trim()
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
  if (!cron) return '未设置'
  const legacyMap: Record<string, string> = {
    hourly: '0 * * * *',
    daily: '0 9 * * *',
    weekly: '0 9 * * 1'
  }
  const expr = legacyMap[cron] || cron
  try {
    return cronstrue.toString(expr, { locale: 'zh_CN' })
  } catch {
    return `cron: ${cron}`
  }
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
  display: flex;
  gap: 16px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.meta-next {
  color: var(--color-primary-light-4);
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

.action-hint {
  color: var(--color-text-3);
  font-size: 12px;
  font-style: italic;
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

/* Cron Feedback */
.cron-feedback {
  margin: -8px 0 12px;
  padding: 6px 12px;
  font-size: 12px;
  border-radius: 4px;
}

.cron-description {
  color: var(--color-success-6);
  background: var(--color-success-light-1);
  display: flex;
  align-items: center;
  gap: 6px;
}

.cron-error {
  color: var(--color-danger-6);
  background: var(--color-danger-light-1);
  display: flex;
  align-items: center;
  gap: 6px;
}

.cron-next-executions {
  margin: -4px 0 16px;
  padding: 8px 12px;
  background: var(--color-fill-2);
  border-radius: 4px;
  font-size: 12px;
}

.cron-next-label {
  font-weight: 500;
  color: var(--color-text-2);
  margin-bottom: 4px;
}

.cron-next-time {
  color: var(--color-text-3);
  line-height: 1.8;
  padding-left: 8px;
}

.cron-preset-tag {
  cursor: pointer;
  transition: all 150ms;
}

.cron-preset-tag:hover {
  opacity: 0.8;
}
</style>
