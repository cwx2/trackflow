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
      <!-- 工作流定义附加 Section (YouTrack 风格) -->
      <div class="workflow-section">
        <div class="section-header">
          <div class="section-info">
            <h3 class="section-title">工作流</h3>
            <p class="section-desc">
              管理此项目使用的工作流。工作流定义了工单的状态转换规则。未附加工作流时使用全局默认规则。
            </p>
          </div>
          <a-button
            v-if="canManage && !isArchived"
            type="primary"
            size="small"
            @click="showAttachWorkflowModal"
          >
            <template #icon><icon-plus /></template>
            附加工作流
          </a-button>
        </div>

        <!-- 已附加工作流列表 -->
        <div v-if="attachedWorkflows.length > 0" class="workflow-list">
          <div
            v-for="wf in attachedWorkflows"
            :key="wf.id"
            class="workflow-def-card"
          >
            <div class="wf-main">
              <div class="wf-header">
                <icon-settings class="wf-icon" />
                <span class="wf-name">{{ wf.name }}</span>
                <a-tag v-if="wf.isDefault" color="arcoblue" size="small">默认</a-tag>
              </div>
              <div v-if="wf.description" class="wf-desc">{{ wf.description }}</div>
              <div class="wf-meta">
                <span>{{ wf.transitionCount }} 条转换规则</span>
              </div>
            </div>
            <div v-if="canManage && !isArchived" class="wf-actions">
              <a-popconfirm
                content="确定从项目中分离此工作流？分离后将使用全局默认规则。"
                @ok="handleDetachWorkflow(wf.id)"
              >
                <a-button size="mini" type="text" status="danger">
                  <template #icon><icon-minus /></template>
                  分离
                </a-button>
              </a-popconfirm>
            </div>
          </div>
        </div>

        <!-- 空状态：未附加工作流 -->
        <div v-else class="wf-empty-state">
          <icon-branch class="empty-icon" style="font-size: 36px; color: var(--color-text-4);" />
          <p class="empty-title">尚未附加工作流</p>
          <p class="empty-desc">当前项目使用全局默认工作流规则。附加工作流以使用自定义状态转换。</p>
          <a-button v-if="canManage && !isArchived" type="primary" size="small" @click="showAttachWorkflowModal">
            <template #icon><icon-plus /></template>
            附加工作流
          </a-button>
        </div>
      </div>

      <!-- 分隔线 -->
      <div class="section-divider"></div>

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
              当工单创建、字段变更或按计划定时执行的规则。全局规则自动对此项目生效。
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
                <!-- on_schedule 规则显示调度频率 Tag，on_change 规则显示触发事件 Tag -->
                <template v-if="rule.ruleType === 'on_schedule'">
                  <a-tag color="purple" size="small">定时执行</a-tag>
                  <a-tag size="small" color="gray">{{ scheduleLabel(rule.cronExpression) }}</a-tag>
                </template>
                <template v-else>
                  <a-tag :color="eventColor(rule.triggerEvent)" size="small">
                    {{ eventLabel(rule.triggerEvent) }}
                  </a-tag>
                </template>
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
              <!-- on_schedule 规则显示上次执行时间 -->
              <div v-if="rule.ruleType === 'on_schedule'" class="rule-meta">
                <span v-if="rule.lastExecutedAt" class="meta-item">
                  上次执行: {{ formatTime(rule.lastExecutedAt) }}
                </span>
                <span v-else class="meta-item">尚未执行</span>
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
                <!-- on_schedule 规则额外提供手动执行按钮 -->
                <a-button
                  v-if="rule.ruleType === 'on_schedule'"
                  size="mini"
                  @click="handleExecuteRule(rule)"
                >
                  <template #icon><icon-play-arrow /></template>
                  执行
                </a-button>
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
          <p class="empty-desc">创建自动化规则，在工单创建、字段变更或按计划定时执行动作。</p>
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
        <!-- 规则类型选择（编辑时不可更改） -->
        <a-form-item label="规则类型" field="ruleType">
          <a-radio-group v-model="ruleForm.ruleType" type="button" :disabled="!!editingRule">
            <a-radio value="on_change">变更触发</a-radio>
            <a-radio value="on_schedule">定时触发</a-radio>
          </a-radio-group>
          <template #extra>
            <span v-if="ruleForm.ruleType === 'on_change'">工单创建或字段变更时自动触发</span>
            <span v-else>按照设定的时间周期定期检查工单并执行动作</span>
          </template>
        </a-form-item>

        <a-form-item label="规则名称" field="name" :rules="[{ required: true, message: '请输入规则名称' }]">
          <a-input
            v-model="ruleForm.name"
            :placeholder="ruleForm.ruleType === 'on_schedule' ? '如：每天检查超期工单并添加逾期标签' : '如：Bug 创建时自动设置高优先级'"
            :max-length="100"
          />
        </a-form-item>

        <a-form-item label="描述" field="description">
          <a-textarea v-model="ruleForm.description" placeholder="规则功能说明（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>

        <!-- on_change 专属：触发事件 -->
        <template v-if="ruleForm.ruleType === 'on_change'">
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="触发事件" field="triggerEvent" :rules="[{ required: true, message: '请选择触发事件' }]">
                <a-select v-model="ruleForm.triggerEvent">
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
                v-if="ruleForm.triggerEvent === 'field_changed'"
                label="监听字段"
                field="triggerField"
              >
                <a-select v-model="ruleForm.triggerField" placeholder="全部字段" allow-clear>
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
        </template>

        <!-- on_schedule 专属：执行频率 -->
        <template v-if="ruleForm.ruleType === 'on_schedule'">
          <a-form-item label="执行频率" field="cronExpression" :rules="[{ required: true, message: '请选择执行频率' }]">
            <a-select v-model="ruleForm.cronExpression">
              <a-option value="hourly">每小时</a-option>
              <a-option value="daily">每天</a-option>
              <a-option value="weekly">每周</a-option>
              <a-option value="custom">自定义 Cron</a-option>
            </a-select>
          </a-form-item>
          <a-form-item
            v-if="ruleForm.cronExpression === 'custom'"
            label="Cron 表达式"
            field="customCron"
            :rules="[{ required: true, message: '请输入 Cron 表达式' }]"
          >
            <a-input v-model="ruleForm.customCron" placeholder="如：0 9 * * *（每天 9 点）" />
            <template #extra>标准 5 段 cron 格式：分 时 日 月 周</template>
          </a-form-item>
        </template>

        <!-- 前置条件 -->
        <a-form-item label="前置条件" field="conditions">
          <template #extra>
            <span v-if="ruleForm.ruleType === 'on_change'">所有条件为 AND 关系（全部满足才触发）</span>
            <span v-else>所有条件为 AND 关系（全部满足的工单才会被处理）</span>
          </template>
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
                <!-- on_schedule 额外支持逾期相关操作符 -->
                <template v-if="ruleForm.ruleType === 'on_schedule'">
                  <a-option value="overdue">已逾期</a-option>
                  <a-option value="due_within_days">N天内到期</a-option>
                </template>
              </a-select>
              <a-input
                v-if="!['is_empty', 'is_not_empty', 'overdue'].includes(cond.operator)"
                v-model="cond.value"
                style="flex: 1"
                :placeholder="cond.operator === 'due_within_days' ? '天数（如 3）' : '值（如 Bug, Critical）'"
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

    <!-- 附加工作流弹窗 -->
    <a-modal
      v-model:visible="attachWorkflowModalVisible"
      title="附加工作流到项目"
      :width="480"
      @ok="handleAttachWorkflow"
      @cancel="attachWorkflowModalVisible = false"
      :ok-loading="attaching"
      ok-text="附加"
      cancel-text="取消"
      unmount-on-close
    >
      <div v-if="availableWorkflows.length === 0" class="empty-available">
        <p>暂无可附加的工作流定义。</p>
        <a-link href="/workflow" target="_blank">前往全局工作流管理页创建</a-link>
      </div>
      <a-form v-else layout="vertical">
        <a-form-item label="选择工作流定义" required>
          <a-select
            v-model="selectedWorkflowId"
            placeholder="请选择工作流定义"
            style="width: 100%"
            :loading="loadingAvailable"
          >
            <a-option
              v-for="wf in availableWorkflows"
              :key="wf.id"
              :value="wf.id"
            >
              {{ wf.name }}
              <span v-if="wf.isDefault" style="color: var(--color-text-3); font-size: 12px;"> (默认)</span>
              <span style="color: var(--color-text-3); font-size: 12px;"> · {{ wf.transitionCount }} 条规则</span>
            </a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { IconLock, IconSettings, IconPlus, IconThunderbolt, IconInfoCircle, IconDelete, IconPlayArrow, IconMinus, IconBranch } from '@arco-design/web-vue/es/icon'
import { workflowApi, workflowRuleApi } from '@/api'
import { workflowDefinitionApi } from '@/api/workflowDefinition'
import type { WorkflowDefinitionVO } from '@/api/workflowDefinition'
import type { WorkflowRuleVO, WorkflowRuleDTO } from '@/api/workflowRule'
import type { ProjectDetailVO } from '@/api/types'

const props = defineProps<{
  project: ProjectDetailVO
  canManage: boolean
  isArchived: boolean
}>()

const router = useRouter()

// ==================== Workflow Definition State ====================
const attachedWorkflows = ref<WorkflowDefinitionVO[]>([])
const availableWorkflows = ref<WorkflowDefinitionVO[]>([])
const attachWorkflowModalVisible = ref(false)
const selectedWorkflowId = ref<string | undefined>(undefined)
const attaching = ref(false)
const loadingAvailable = ref(false)

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
  ruleType: 'on_change' as 'on_change' | 'on_schedule',
  triggerEvent: 'issue_created',
  triggerField: null as string | null,
  cronExpression: 'daily',
  customCron: '',
  conditions: [] as ConditionItem[],
  actions: [] as ActionItem[]
})

// ==================== Data Loading ====================
async function loadData() {
  loading.value = true
  try {
    await Promise.all([loadAttachedWorkflows(), loadTransitionStats(), loadRules()])
  } finally {
    loading.value = false
  }
}

async function loadAttachedWorkflows() {
  try {
    const res = await workflowDefinitionApi.getProjectWorkflows(props.project.id)
    if (res.code === 0) {
      attachedWorkflows.value = res.data || []
    }
  } catch {
    // silent - user may not have manage_workflow permission
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

// ==================== Workflow Definition Actions ====================
async function showAttachWorkflowModal() {
  selectedWorkflowId.value = undefined
  loadingAvailable.value = true
  attachWorkflowModalVisible.value = true
  try {
    // 获取所有定义，过滤已附加的
    const res = await workflowDefinitionApi.list()
    if (res.code === 0) {
      const allDefs = res.data || []
      const attachedIds = new Set(attachedWorkflows.value.map(w => w.id))
      availableWorkflows.value = allDefs.filter(w => !attachedIds.has(w.id))
    }
  } catch {
    Message.error('加载工作流列表失败')
  } finally {
    loadingAvailable.value = false
  }
}

async function handleAttachWorkflow() {
  if (!selectedWorkflowId.value) {
    Message.warning('请选择要附加的工作流')
    return
  }
  attaching.value = true
  try {
    await workflowDefinitionApi.attachToProject(props.project.id, selectedWorkflowId.value)
    Message.success('工作流已附加到项目')
    attachWorkflowModalVisible.value = false
    await loadAttachedWorkflows()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '附加失败')
  } finally {
    attaching.value = false
  }
}

async function handleDetachWorkflow(workflowDefinitionId: string) {
  try {
    await workflowDefinitionApi.detachFromProject(props.project.id, workflowDefinitionId)
    Message.success('工作流已从项目中分离')
    await loadAttachedWorkflows()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '分离失败')
  }
}

// ==================== Actions ====================
function goToWorkflowEditor() {
  // Navigate to the workflow editor page with project pre-selected
  router.push(`/admin/workflow?project=${props.project.id}`)
}

function showCreateRuleModal() {
  editingRule.value = null
  Object.assign(ruleForm, {
    name: '',
    description: '',
    ruleType: 'on_change',
    triggerEvent: 'issue_created',
    triggerField: null,
    cronExpression: 'daily',
    customCron: '',
    conditions: [],
    actions: []
  })
  ruleModalVisible.value = true
}

function handleEditRule(rule: WorkflowRuleVO) {
  editingRule.value = rule
  const conditions = parseJson(rule.conditionJson, [])
  const actions = parseJson(rule.actionJson, [])
  const isScheduled = rule.ruleType === 'on_schedule'
  const cron = rule.cronExpression || 'daily'
  const isPreset = ['hourly', 'daily', 'weekly'].includes(cron)
  Object.assign(ruleForm, {
    name: rule.name,
    description: rule.description || '',
    ruleType: isScheduled ? 'on_schedule' : 'on_change',
    triggerEvent: isScheduled ? 'scheduled' : (rule.triggerEvent || 'issue_created'),
    triggerField: rule.triggerField,
    cronExpression: isScheduled ? (isPreset ? cron : 'custom') : 'daily',
    customCron: isScheduled && !isPreset ? cron : '',
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
    let dto: WorkflowRuleDTO
    if (ruleForm.ruleType === 'on_schedule') {
      const cronExpr = ruleForm.cronExpression === 'custom' ? ruleForm.customCron : ruleForm.cronExpression
      dto = {
        name: ruleForm.name,
        description: ruleForm.description || undefined,
        ruleType: 'on_schedule',
        triggerEvent: 'scheduled',
        conditionJson: JSON.stringify(ruleForm.conditions.filter(c => c.field)),
        actionJson: JSON.stringify(ruleForm.actions.filter(a => a.type)),
        enabled: true,
        cronExpression: cronExpr
      }
    } else {
      dto = {
        name: ruleForm.name,
        description: ruleForm.description || undefined,
        ruleType: 'on_change',
        triggerEvent: ruleForm.triggerEvent,
        triggerField: ruleForm.triggerField || undefined,
        conditionJson: JSON.stringify(ruleForm.conditions.filter(c => c.field)),
        actionJson: JSON.stringify(ruleForm.actions.filter(a => a.type)),
        enabled: true
      }
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

async function handleExecuteRule(rule: WorkflowRuleVO) {
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
    comment_added: '评论时',
    attachment_added: '附件添加',
    attachment_removed: '附件移除',
    link_added: '关联添加',
    link_removed: '关联移除',
    work_item_added: '工时记录',
    work_item_deleted: '工时删除',
    issue_resolved: '已解决',
    issue_unresolved: '未解决',
    on_schedule: '定时执行',
    scheduled: '定时执行'
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
    issue_unresolved: 'red',
    on_schedule: 'purple',
    scheduled: 'purple'
  }
  return map[event] || 'gray'
}

function scheduleLabel(cron: string | null) {
  const map: Record<string, string> = {
    hourly: '每小时',
    daily: '每天',
    weekly: '每周'
  }
  return cron ? (map[cron] || `cron: ${cron}`) : '未设置'
}

function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

function fieldLabel(field: string) {
  const map: Record<string, string> = {
    status_id: '状态', issue_type: '类型', priority: '优先级', assignee: '负责人',
    sprint: '迭代', title: '标题', due_date: '截止日期',
    type: '类型', status: '状态'
  }
  return map[field] || field
}

function conditionSummary(json: string): string {
  const conditions = parseJson(json, [])
  if (conditions.length === 0) return '无条件（始终触发）'
  return conditions.map((c: any) => {
    if (c.operator === 'overdue') return `${fieldLabel(c.field)} 已逾期`
    if (c.operator === 'due_within_days') return `${fieldLabel(c.field)} ${c.value || '?'}天内到期`
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

/* Workflow definitions list */
.workflow-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.workflow-def-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 6px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  transition: border-color 150ms;
}

.workflow-def-card:hover {
  border-color: var(--color-primary-light-4);
}

.wf-main {
  flex: 1;
  min-width: 0;
}

.wf-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.wf-icon {
  font-size: 16px;
  color: var(--color-text-3);
  flex-shrink: 0;
}

.wf-name {
  font-weight: 500;
  font-size: 14px;
  color: var(--color-text-1);
}

.wf-desc {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 4px;
}

.wf-meta {
  font-size: 12px;
  color: var(--color-text-4);
}

.wf-actions {
  flex-shrink: 0;
  margin-left: 16px;
}

.wf-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  text-align: center;
  border-radius: 6px;
  background: var(--color-bg-2);
  border: 1px dashed var(--color-border-2);
}

.empty-available {
  text-align: center;
  padding: 16px 0;
  color: var(--color-text-3);
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

.rule-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-4);
}

.meta-item {
  color: var(--color-text-3);
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
