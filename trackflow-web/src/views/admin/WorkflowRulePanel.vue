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
                <a-option value="comment_added">评论添加时</a-option>
                <a-option value="attachment_added">附件被添加时</a-option>
                <a-option value="attachment_removed">附件被移除时</a-option>
                <a-option value="link_added">关联工单被添加时</a-option>
                <a-option value="link_removed">关联工单被移除时</a-option>
                <a-option value="work_item_added">工时被记录时</a-option>
                <a-option value="work_item_deleted">工时被删除时</a-option>
                <a-option value="issue_resolved">工单变为已解决时</a-option>
                <a-option value="issue_unresolved">工单变为未解决时</a-option>
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
          <template #extra>条件组内为 {{ formData.conditionLogic === 'or' ? 'OR（任一满足即触发）' : 'AND（全部满足才触发）' }} 关系</template>
          <div class="condition-list">
            <!-- 顶层逻辑切换 -->
            <div class="condition-logic-toggle">
              <a-radio-group v-model="formData.conditionLogic" type="button" size="small">
                <a-radio value="and">AND（全部满足）</a-radio>
                <a-radio value="or">OR（任一满足）</a-radio>
              </a-radio-group>
            </div>

            <div v-for="(cond, idx) in formData.conditions" :key="idx" class="condition-row" :class="{ 'condition-negated': cond.negated }">
              <!-- NOT 切换 -->
              <a-tooltip content="取反（NOT）">
                <a-button
                  :type="cond.negated ? 'primary' : 'text'"
                  size="mini"
                  :class="{ 'not-active': cond.negated }"
                  @click="cond.negated = !cond.negated"
                >
                  NOT
                </a-button>
              </a-tooltip>

              <!-- 条件类型选择 -->
              <a-select v-model="cond.conditionType" style="width: 160px" placeholder="条件类型" @change="() => onConditionTypeChange(cond)">
                <a-optgroup label="字段匹配">
                  <a-option value="field_check">字段值检查</a-option>
                </a-optgroup>
                <a-optgroup label="工单状态类">
                  <a-option value="issue_resolved">工单已解决</a-option>
                  <a-option value="issue_has_tag">工单有标签</a-option>
                  <a-option value="issue_attribute_count">工单属性数量</a-option>
                  <a-option value="issue_created_within">创建于 N 天内</a-option>
                  <a-option value="issue_updated_within">更新于 N 天内</a-option>
                </a-optgroup>
                <a-optgroup label="用户类">
                  <a-option value="created_by">创建者是</a-option>
                  <a-option value="updated_by">更新者是</a-option>
                  <a-option value="user_has_role">触发者角色</a-option>
                </a-optgroup>
                <a-optgroup label="项目类">
                  <a-option value="issue_in_project">属于项目</a-option>
                </a-optgroup>
              </a-select>

              <!-- field_check: 旧的字段+操作符+值模式 -->
              <template v-if="cond.conditionType === 'field_check'">
                <a-select v-model="cond.field" style="width: 120px" placeholder="字段">
                  <a-option value="type">工单类型</a-option>
                  <a-option value="priority">优先级</a-option>
                  <a-option value="assignee">负责人</a-option>
                  <a-option value="status">状态</a-option>
                  <a-option value="sprint">迭代</a-option>
                  <a-option value="due_date">截止日期</a-option>
                </a-select>
                <a-select v-model="cond.operator" style="width: 140px" placeholder="操作符" @change="() => { cond.value = '' }">
                  <a-optgroup label="当前值匹配">
                    <a-option value="equals">等于</a-option>
                    <a-option value="not_equals">不等于</a-option>
                    <a-option value="contains">包含</a-option>
                    <a-option value="in">属于（逗号分隔）</a-option>
                    <a-option value="is_empty">为空</a-option>
                    <a-option value="is_not_empty">不为空</a-option>
                  </a-optgroup>
                  <a-optgroup label="时间条件">
                    <a-option value="overdue">已逾期</a-option>
                    <a-option value="due_within_days">N天内到期</a-option>
                  </a-optgroup>
                  <a-optgroup
                    v-if="formData.triggerEvent === 'field_changed' && formData.triggerField"
                    label="变更前值匹配（旧值）"
                  >
                    <a-option value="old_value_equals">旧值等于</a-option>
                    <a-option value="old_value_not_equals">旧值不等于</a-option>
                    <a-option value="old_value_in">旧值属于</a-option>
                    <a-option value="old_value_is_empty">旧值为空</a-option>
                    <a-option value="old_value_is_not_empty">旧值不为空</a-option>
                  </a-optgroup>
                </a-select>
                <a-input
                  v-if="!['is_empty', 'is_not_empty', 'old_value_is_empty', 'old_value_is_not_empty', 'overdue'].includes(cond.operator)"
                  v-model="cond.value"
                  style="flex: 1"
                  :placeholder="cond.operator === 'due_within_days' ? '天数（如 3）' : '值（如 Bug, Critical）'"
                />
              </template>

              <!-- issue_resolved: 无额外参数 -->
              <!-- issue_has_tag: 标签选择 -->
              <template v-else-if="cond.conditionType === 'issue_has_tag'">
                <a-input v-model="cond.tagId" style="flex: 1" placeholder="标签 ID" />
              </template>

              <!-- issue_attribute_count: 属性 + 操作符 + 值 -->
              <template v-else-if="cond.conditionType === 'issue_attribute_count'">
                <a-select v-model="cond.attribute" style="width: 100px" placeholder="属性">
                  <a-option value="comments">评论数</a-option>
                  <a-option value="links">关联数</a-option>
                  <a-option value="attachments">附件数</a-option>
                </a-select>
                <a-select v-model="cond.operator" style="width: 100px" placeholder="操作符">
                  <a-option value="greater_than">大于</a-option>
                  <a-option value="less_than">小于</a-option>
                  <a-option value="equals">等于</a-option>
                  <a-option value="greater_than_or_equals">≥</a-option>
                  <a-option value="less_than_or_equals">≤</a-option>
                </a-select>
                <a-input-number v-model="cond.value" style="width: 80px" placeholder="数量" :min="0" />
              </template>

              <!-- issue_created_within / issue_updated_within: 天数 -->
              <template v-else-if="cond.conditionType === 'issue_created_within' || cond.conditionType === 'issue_updated_within'">
                <a-input-number v-model="cond.days" style="width: 100px" placeholder="天数" :min="1" />
                <span class="condition-suffix">天内</span>
              </template>

              <!-- created_by / updated_by: 用户选择 -->
              <template v-else-if="cond.conditionType === 'created_by' || cond.conditionType === 'updated_by'">
                <a-select v-model="cond.userId" style="flex: 1" placeholder="选择用户" allow-search>
                  <a-option value="current_user">当前操作用户</a-option>
                </a-select>
              </template>

              <!-- user_has_role: 角色选择 -->
              <template v-else-if="cond.conditionType === 'user_has_role'">
                <a-select v-model="cond.role" style="flex: 1" placeholder="选择角色">
                  <a-option value="project_admin">项目管理员</a-option>
                  <a-option value="tech_lead">技术负责人</a-option>
                  <a-option value="developer">开发人员</a-option>
                  <a-option value="product_manager">产品经理</a-option>
                  <a-option value="tester">测试人员</a-option>
                  <a-option value="observer">观察者</a-option>
                </a-select>
              </template>

              <!-- issue_in_project: 项目选择 -->
              <template v-else-if="cond.conditionType === 'issue_in_project'">
                <a-input v-model="cond.projectId" style="flex: 1" placeholder="项目 ID" />
              </template>

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
                <a-option value="remove_tag">移除标签</a-option>
                <a-option value="add_comment">添加评论</a-option>
                <a-option value="require_field">要求必填字段</a-option>
                <a-option value="send_email">发送邮件通知</a-option>
                <a-option value="show_alert">显示提示消息</a-option>
                <a-option value="update_summary">修改工单标题</a-option>
                <a-option value="update_description">修改工单描述</a-option>
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

              <!-- remove_tag -->
              <template v-else-if="act.type === 'remove_tag'">
                <a-input v-model="act.tagId" style="flex: 1" placeholder="标签 ID" />
              </template>

              <!-- require_field -->
              <template v-else-if="act.type === 'require_field'">
                <a-select v-model="act.field" style="width: 120px" placeholder="检查字段">
                  <a-option value="assignee">负责人</a-option>
                  <a-option value="priority">优先级</a-option>
                  <a-option value="due_date">截止日期</a-option>
                  <a-option value="sprint">迭代</a-option>
                  <a-option value="description">描述</a-option>
                </a-select>
                <a-input v-model="act.errorMessage" style="flex: 1" placeholder="阻断提示消息（如：请先分配负责人）" />
              </template>

              <!-- send_email -->
              <template v-else-if="act.type === 'send_email'">
                <div style="display: flex; flex-direction: column; gap: 6px; flex: 1">
                  <a-select v-model="act.target" style="width: 100%" placeholder="收件人">
                    <a-option value="reporter">报告人</a-option>
                    <a-option value="assignee">负责人</a-option>
                    <a-option value="creator">规则创建者</a-option>
                  </a-select>
                  <a-input v-model="act.subject" placeholder="邮件主题（支持 {issue.key}、{issue.summary} 等变量）" />
                  <a-textarea v-model="act.body" :auto-size="{ minRows: 2, maxRows: 4 }" placeholder="邮件正文（支持变量插值）" />
                </div>
              </template>

              <!-- show_alert -->
              <template v-else-if="act.type === 'show_alert'">
                <a-select v-model="act.style" style="width: 100px" placeholder="样式">
                  <a-option value="acknowledgment">普通提示</a-option>
                  <a-option value="error">错误提示</a-option>
                </a-select>
                <a-input v-model="act.message" style="flex: 1" placeholder="提示消息（支持变量插值）" />
              </template>

              <!-- update_summary -->
              <template v-else-if="act.type === 'update_summary'">
                <a-input v-model="act.value" style="flex: 1" placeholder="新标题模板（支持 {issue.summary}、{issue.type} 等变量）" />
              </template>

              <!-- update_description -->
              <template v-else-if="act.type === 'update_description'">
                <a-textarea v-model="act.value" :auto-size="{ minRows: 2, maxRows: 4 }" style="flex: 1" placeholder="新描述模板（支持变量插值）" />
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
  conditionType: string
  field: string
  operator: string
  value: string
  negated: boolean
  // Advanced condition fields
  tagId?: string
  attribute?: string
  days?: number
  userId?: string
  role?: string
  projectId?: string
}

interface ActionItem {
  type: string
  field?: string
  value?: string
  tagId?: string
  content?: string
  errorMessage?: string
  target?: string
  subject?: string
  body?: string
  style?: string
  message?: string
}

const formData = reactive({
  name: '',
  description: '',
  triggerEvent: 'issue_created',
  triggerField: null as string | null,
  conditionLogic: 'and' as 'and' | 'or',
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
      availableSprints.value = res.data?.list || []
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
    conditionLogic: 'and',
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
  const { conditions, logic } = parseConditionJson(rule.conditionJson)
  const actions = parseJson(rule.actionJson, [])
  Object.assign(formData, {
    name: rule.name,
    description: rule.description || '',
    triggerEvent: rule.triggerEvent,
    triggerField: rule.triggerField,
    conditionLogic: logic,
    conditions,
    actions
  })
  modalVisible.value = true
  // 预加载状态和迭代数据
  loadStatuses()
  loadSprints()
}

/**
 * 解析条件 JSON 为表单可编辑结构。
 * 支持旧格式（平铺数组）和新格式（递归逻辑节点）。
 */
function parseConditionJson(json: string): { conditions: ConditionItem[]; logic: 'and' | 'or' } {
  if (!json || json === '[]' || json === '{}') {
    return { conditions: [], logic: 'and' }
  }
  try {
    const parsed = JSON.parse(json)
    // 旧格式：直接是数组
    if (Array.isArray(parsed)) {
      return {
        conditions: parsed.map((c: any) => parseLeafToConditionItem(c, false)),
        logic: 'and'
      }
    }
    // 新格式：对象 { type: "and"/"or", conditions: [...] }
    if (parsed && typeof parsed === 'object' && parsed.type) {
      const logic = (parsed.type === 'or' ? 'or' : 'and') as 'and' | 'or'
      const childNodes: any[] = parsed.conditions || []
      const conditions: ConditionItem[] = childNodes.map((node: any) => {
        if (node.type === 'not' && node.condition) {
          return parseLeafToConditionItem(node.condition, true)
        }
        return parseLeafToConditionItem(node, false)
      })
      return { conditions, logic }
    }
    return { conditions: [], logic: 'and' }
  } catch {
    return { conditions: [], logic: 'and' }
  }
}

function parseLeafToConditionItem(leaf: any, negated: boolean): ConditionItem {
  const conditionType = leaf.conditionType || 'field_check'
  const item: ConditionItem = {
    conditionType,
    field: leaf.field || '',
    operator: leaf.operator || 'equals',
    value: leaf.value || '',
    negated
  }
  // 解析高级字段
  if (leaf.tagId) item.tagId = leaf.tagId
  if (leaf.attribute) item.attribute = leaf.attribute
  if (leaf.days) item.days = Number(leaf.days)
  if (leaf.userId) item.userId = leaf.userId
  if (leaf.role) item.role = leaf.role
  if (leaf.projectId) item.projectId = leaf.projectId
  return item
}

function addCondition() {
  formData.conditions.push({ conditionType: 'field_check', field: '', operator: 'equals', value: '', negated: false })
}

function onConditionTypeChange(cond: ConditionItem) {
  // Reset all fields when condition type changes
  cond.field = ''
  cond.operator = ''
  cond.value = ''
  cond.tagId = undefined
  cond.attribute = undefined
  cond.days = undefined
  cond.userId = undefined
  cond.role = undefined
  cond.projectId = undefined
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
      conditionJson: buildConditionJson(),
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

/**
 * 构建条件 JSON。
 * - field_check 类型：保留旧的 field/operator/value 格式
 * - 高级类型：使用 conditionType + 类型专属字段
 */
function buildConditionJson(): string {
  const validConditions = formData.conditions.filter(c => c.conditionType)
  if (validConditions.length === 0) return '[]'

  const hasNegated = validConditions.some(c => c.negated)
  const isOr = formData.conditionLogic === 'or'
  const hasAdvanced = validConditions.some(c => c.conditionType !== 'field_check')

  // 如果有高级条件或 OR 逻辑或 NOT，统一使用新格式
  if (isOr || hasNegated || hasAdvanced) {
    const leafNodes = validConditions.map(c => {
      const leaf = buildConditionLeaf(c)
      if (c.negated) {
        return { type: 'not', condition: leaf }
      }
      return leaf
    })
    return JSON.stringify({
      type: formData.conditionLogic,
      conditions: leafNodes
    })
  }

  // 全部是 field_check + AND + 无 NOT = 旧格式（向后兼容）
  return JSON.stringify(validConditions.map(c => ({
    field: c.field,
    operator: c.operator,
    value: c.value
  })))
}

function buildConditionLeaf(c: ConditionItem): any {
  if (c.conditionType === 'field_check') {
    return { type: 'condition', field: c.field, operator: c.operator, value: c.value }
  }
  // 高级条件类型
  const leaf: any = { type: 'condition', conditionType: c.conditionType }
  switch (c.conditionType) {
    case 'issue_has_tag':
      leaf.tagId = c.tagId || ''
      break
    case 'issue_attribute_count':
      leaf.attribute = c.attribute || ''
      leaf.operator = c.operator || 'greater_than'
      leaf.value = String(c.value ?? '')
      break
    case 'issue_created_within':
    case 'issue_updated_within':
      leaf.days = String(c.days ?? '')
      break
    case 'created_by':
    case 'updated_by':
      leaf.userId = c.userId || 'current_user'
      break
    case 'user_has_role':
      leaf.role = c.role || ''
      break
    case 'issue_in_project':
      leaf.projectId = c.projectId || ''
      break
    // issue_resolved: 无额外参数
  }
  return leaf
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
    field_changed: '变更时',
    comment_added: '评论时',
    attachment_added: '附件添加',
    attachment_removed: '附件移除',
    link_added: '关联添加',
    link_removed: '关联移除',
    work_item_added: '工时记录',
    work_item_deleted: '工时删除',
    issue_resolved: '已解决',
    issue_unresolved: '未解决'
  }
  return map[event] || event
}

function eventColor(event: string) {
  const map: Record<string, string> = {
    issue_created: 'green',
    field_changed: 'blue',
    comment_added: 'cyan',
    attachment_added: 'orange',
    attachment_removed: 'orange',
    link_added: 'purple',
    link_removed: 'purple',
    work_item_added: 'gold',
    work_item_deleted: 'gold',
    issue_resolved: 'green',
    issue_unresolved: 'red'
  }
  return map[event] || 'gray'
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
    overdue: '已逾期', due_within_days: 'N天内到期',
    old_value_equals: '旧值等于', old_value_not_equals: '旧值不等于',
    old_value_in: '旧值属于', old_value_is_empty: '旧值为空', old_value_is_not_empty: '旧值不为空'
  }
  return map[op] || op
}

function conditionSummary(json: string): string {
  if (!json || json === '[]' || json === '{}') return '无条件（始终触发）'
  try {
    const parsed = JSON.parse(json)
    // 旧格式：平铺数组
    if (Array.isArray(parsed)) {
      if (parsed.length === 0) return '无条件（始终触发）'
      return parsed.map((c: any) => formatLeafCondition(c)).join(' AND ')
    }
    // 新格式：递归逻辑节点
    if (parsed && typeof parsed === 'object' && parsed.type) {
      return formatLogicNode(parsed)
    }
    return '无条件（始终触发）'
  } catch {
    return '无条件（始终触发）'
  }
}

function formatLogicNode(node: any): string {
  if (!node) return ''
  const type = node.type
  if (type === 'not') {
    return `NOT(${formatLogicNode(node.condition)})`
  }
  if (type === 'and' || type === 'or') {
    const children: any[] = node.conditions || []
    if (children.length === 0) return '无条件'
    const separator = type === 'and' ? ' AND ' : ' OR '
    const parts = children.map((child: any) => formatLogicNode(child))
    return parts.length > 1 ? `(${parts.join(separator)})` : parts[0]
  }
  // 叶子节点（type=condition 或其他）
  return formatLeafCondition(node)
}

function formatLeafCondition(c: any): string {
  // Advanced condition types
  if (c.conditionType) {
    switch (c.conditionType) {
      case 'issue_resolved': return '工单已解决'
      case 'issue_has_tag': return `有标签 #${c.tagId || '?'}`
      case 'issue_attribute_count': {
        const attrLabel: Record<string, string> = { comments: '评论数', links: '关联数', attachments: '附件数' }
        const opLabel: Record<string, string> = { greater_than: '>', less_than: '<', equals: '=', greater_than_or_equals: '≥', less_than_or_equals: '≤' }
        return `${attrLabel[c.attribute] || c.attribute} ${opLabel[c.operator] || c.operator} ${c.value || '?'}`
      }
      case 'issue_created_within': return `创建于 ${c.days || '?'} 天内`
      case 'issue_updated_within': return `更新于 ${c.days || '?'} 天内`
      case 'created_by': return `创建者是 ${c.userId === 'current_user' ? '当前用户' : c.userId || '?'}`
      case 'updated_by': return `更新者是 ${c.userId === 'current_user' ? '当前用户' : c.userId || '?'}`
      case 'user_has_role': return `触发者角色 = ${c.role || '?'}`
      case 'issue_in_project': return `属于项目 ${c.projectId || '?'}`
    }
  }
  // field_check or legacy format
  if (c.operator === 'overdue') return `${fieldLabel(c.field)} 已逾期`
  if (c.operator === 'due_within_days') return `${fieldLabel(c.field)} ${c.value || '?'}天内到期`
  const isEmptyOp = ['is_empty', 'is_not_empty', 'old_value_is_empty', 'old_value_is_not_empty'].includes(c.operator)
  if (isEmptyOp) {
    return `${fieldLabel(c.field)} ${operatorLabel(c.operator)}`
  }
  return `${fieldLabel(c.field)} ${operatorLabel(c.operator)} "${c.value}"`
}

function actionSummary(json: string): string {
  const actions = parseJson(json, [])
  if (actions.length === 0) return '无动作'
  return actions.map((a: any) => {
    switch (a.type) {
      case 'set_field': return `设置 ${fieldLabel(a.field)}=${a.value}`
      case 'add_tag': return `添加标签 #${a.tagId}`
      case 'remove_tag': return `移除标签 #${a.tagId}`
      case 'add_comment': return '添加评论'
      case 'require_field': return `要求 ${fieldLabel(a.field)} 必填`
      case 'send_email': return `发邮件→${a.target || '?'}`
      case 'show_alert': return `提示: ${a.message || '?'}`
      case 'update_summary': return '修改标题'
      case 'update_description': return '修改描述'
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

.condition-logic-toggle {
  margin-bottom: 4px;
}

.condition-row,
.action-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.condition-row.condition-negated {
  border-left: 2px solid rgb(var(--red-6));
  padding-left: 8px;
  border-radius: 2px;
}

.not-active {
  font-weight: 600;
  font-size: 11px;
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

.condition-suffix {
  font-size: 13px;
  color: var(--color-text-3);
  white-space: nowrap;
}
</style>
