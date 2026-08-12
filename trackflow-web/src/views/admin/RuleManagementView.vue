<template>
  <AdminPageLayout title="规则引擎" subtitle="配置自动化规则，实现工单计分与统计">
    <template #actions>
      <a-button type="primary" @click="showCreateForm">
        <template #icon><icon-plus /></template>
        创建规则
      </a-button>
    </template>

    <!-- Tabs: 规则列表 / 执行记录 / 统计仪表板 -->
    <a-tabs v-model:active-key="activeTab" class="rule-tabs">
      <!-- Tab 1: 规则列表 -->
      <a-tab-pane key="rules" title="规则列表">
        <AdminDataTable
          :show-toolbar="false"
          :data="rules"
          :loading="loadingRules"
          :total="rules.length"
          :current="1"
          :page-size="rules.length || 20"
          :columns="ruleColumns"
          empty-title="暂无计分规则"
          empty-description="创建规则来实现工单计分与统计"
        />
      </a-tab-pane>

      <!-- Tab 2: 执行记录 -->
      <a-tab-pane key="logs" title="执行记录">
        <div class="log-filters">
          <a-space>
            <a-select
              v-model="logFilter.ruleId"
              placeholder="按规则筛选"
              allow-clear
              style="width: 200px"
              @change="loadLogs"
            >
              <a-option v-for="r in rules" :key="r.id" :value="r.id">{{ r.name }}</a-option>
            </a-select>
            <a-range-picker
              v-model="logFilter.dateRange"
              style="width: 260px"
              @change="loadLogs"
            />
          </a-space>
        </div>
        <AdminDataTable
          :show-toolbar="false"
          :data="logs"
          :loading="loadingLogs"
          :total="logPagination.total"
          :current="logPagination.current"
          :page-size="logPagination.pageSize"
          :columns="logColumns"
          empty-title="暂无执行记录"
          @page-change="onLogPageChange"
          @page-size-change="onLogPageSizeChange"
        />
      </a-tab-pane>

      <!-- Tab 3: 统计仪表板 -->
      <a-tab-pane key="statistics" title="统计仪表板">
        <div class="stats-filters">
          <a-space>
            <a-select
              v-model="statsFilter.ruleId"
              placeholder="全部规则"
              allow-clear
              style="width: 200px"
              @change="loadStatistics"
            >
              <a-option v-for="r in rules" :key="r.id" :value="r.id">{{ r.name }}</a-option>
            </a-select>
            <a-range-picker
              v-model="statsFilter.dateRange"
              style="width: 260px"
              @change="loadStatistics"
            />
          </a-space>
        </div>

        <div class="stats-grid" v-if="statistics">
          <!-- 排行榜 -->
          <div class="stats-card">
            <h3 class="card-title">排行榜</h3>
            <div class="ranking-list" v-if="statistics.ranking.length > 0">
              <div v-for="(item, idx) in statistics.ranking" :key="item.userId" class="ranking-item">
                <span class="rank-number" :class="{ top3: idx < 3 }">{{ idx + 1 }}</span>
                <span class="rank-name">{{ item.displayName }}</span>
                <span class="rank-score">¥{{ item.totalScore }}</span>
                <span class="rank-count">{{ item.executionCount }}次</span>
              </div>
            </div>
            <a-empty v-else description="暂无数据" />
          </div>

          <!-- 按规则汇总 -->
          <div class="stats-card">
            <h3 class="card-title">按规则汇总</h3>
            <div class="summary-list" v-if="statistics.summaryByRule.length > 0">
              <div v-for="item in statistics.summaryByRule" :key="item.ruleId" class="summary-item">
                <span class="summary-name">{{ item.ruleName }}</span>
                <span class="summary-score">¥{{ item.totalScore }}</span>
                <span class="summary-count">{{ item.executionCount }}次</span>
              </div>
            </div>
            <a-empty v-else description="暂无数据" />
          </div>

          <!-- 趋势（简单文字展示，后续可接入图表库） -->
          <div class="stats-card wide">
            <h3 class="card-title">每日趋势</h3>
            <div class="trend-table" v-if="statistics.trend.length > 0">
              <a-table :data="statistics.trend" :pagination="false" size="small">
                <template #columns>
                  <a-table-column title="日期" data-index="date" :width="120" />
                  <a-table-column title="金额" :width="100" align="right">
                    <template #cell="{ record }">¥{{ record.dailyScore }}</template>
                  </a-table-column>
                  <a-table-column title="命中次数" data-index="dailyCount" :width="100" align="center" />
                </template>
              </a-table>
            </div>
            <a-empty v-else description="暂无趋势数据" />
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- 创建/编辑规则弹窗 -->
    <a-modal
      v-model:visible="formVisible"
      :title="editingRule ? '编辑规则' : '创建规则'"
      :width="640"
      @ok="handleFormSubmit"
      @cancel="formVisible = false"
      :ok-loading="submitting"
      ok-text="保存"
      cancel-text="取消"
    >
      <a-form :model="formData" layout="vertical" ref="formRef">
        <a-form-item label="规则名称" field="name" :rules="[{ required: true, message: '请输入规则名称' }]">
          <a-input v-model="formData.name" placeholder="如：零食分享（过期罚款）" />
        </a-form-item>

        <a-form-item label="描述" field="description">
          <a-textarea v-model="formData.description" placeholder="规则说明" :auto-size="{ minRows: 2 }" />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="触发类型" field="triggerType" :rules="[{ required: true }]">
              <a-select v-model="formData.triggerType">
                <a-option value="scheduled">定时扫描</a-option>
                <a-option value="event">事件触发</a-option>
                <a-option value="manual">手动录入</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="计算公式" field="scoreFormula" :rules="[{ required: true }]">
              <a-select v-model="formData.scoreFormula">
                <a-option value="linear_daily">按天线性递增</a-option>
                <a-option value="fixed">固定金额</a-option>
                <a-option value="cumulative_increment">按累计次数递增</a-option>
                <a-option value="custom">自定义</a-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="作用对象" field="targetField">
              <a-select v-model="formData.targetField">
                <a-option value="assignee">经办人</a-option>
                <a-option value="reporter">报告人</a-option>
                <a-option value="created_by">创建者</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="去重策略" field="dedupStrategy">
              <a-select v-model="formData.dedupStrategy">
                <a-option value="daily">每天一次</a-option>
                <a-option value="once_per_issue">每工单一次</a-option>
                <a-option value="no_dedup">不去重</a-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item v-if="formData.triggerType === 'scheduled'" label="Cron 表达式" field="scheduleCron">
          <a-input v-model="formData.scheduleCron" placeholder="0 0 9 * * ?（每天9:00）" />
          <template #extra>Spring Cron 格式：秒 分 时 日 月 周</template>
        </a-form-item>

        <a-form-item label="触发条件 (JSON)" field="triggerConfig" :rules="[{ required: true, message: '请配置触发条件' }]">
          <a-textarea
            v-model="formData.triggerConfig"
            placeholder='{"issueFilter": {"statuses_not_in_category": ["closed"], "has_due_date": true, "due_date_before": "now"}}'
            :auto-size="{ minRows: 3, maxRows: 8 }"
            class="code-input"
          />
        </a-form-item>

        <a-form-item label="计算配置 (JSON)" field="scoreConfig" :rules="[{ required: true, message: '请配置计算参数' }]">
          <a-textarea
            v-model="formData.scoreConfig"
            :placeholder="scoreConfigPlaceholder"
            :auto-size="{ minRows: 2, maxRows: 6 }"
            class="code-input"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { formatDateTime } from '@/utils/date'
import { ref, reactive, computed, onMounted, h } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useRouter } from 'vue-router'
import { scoreRuleApi } from '@/api'
import AdminPageLayout from '@/components/admin/AdminPageLayout.vue'
import DataContainer from '@/components/base/DataContainer.vue'
import { AdminDataTable } from '@/components/admin'
import type { ColumnDef } from '@/components/admin'
import type { RuleDefinitionVO, RuleExecutionLogVO, RuleStatisticsVO } from '@/api/scoreRule'

const router = useRouter()

// ==================== State ====================
const activeTab = ref('rules')
const loadingRules = ref(false)
const loadingLogs = ref(false)
const rules = ref<RuleDefinitionVO[]>([])
const logs = ref<RuleExecutionLogVO[]>([])
const statistics = ref<RuleStatisticsVO | null>(null)

// Form
const formVisible = ref(false)
const editingRule = ref<RuleDefinitionVO | null>(null)
const submitting = ref(false)
const formRef = ref()
const formData = reactive({
  name: '',
  description: '',
  triggerType: 'scheduled',
  scoreFormula: 'linear_daily',
  targetField: 'assignee',
  dedupStrategy: 'daily',
  scheduleCron: '0 0 9 * * ?',
  triggerConfig: '{"issueFilter": {"statuses_not_in_category": ["closed"], "has_due_date": true, "due_date_before": "now"}}',
  scoreConfig: '{"base_amount": 5, "field": "due_date"}'
})

// Log filters
const logFilter = reactive({
  ruleId: undefined as string | undefined,
  dateRange: undefined as [string, string] | undefined
})
const logPagination = reactive({ current: 1, pageSize: 20, total: 0, showPageSize: true, pageSizeOptions: [20, 50, 100] })

// Stats filters
const statsFilter = reactive({
  ruleId: undefined as string | undefined,
  dateRange: undefined as [string, string] | undefined
})

// ==================== Computed ====================
const scoreConfigPlaceholder = computed(() => {
  switch (formData.scoreFormula) {
    case 'linear_daily': return '{"base_amount": 5, "field": "due_date"}'
    case 'fixed': return '{"amount": 20}'
    case 'cumulative_increment': return '{"amounts": [50, 100, 200], "scope": "user_lifetime"}'
    case 'custom': return '{"expression": "overdue_days * 10", "fallback_amount": 10}'
    default: return '{}'
  }
})

// ==================== Methods ====================
function triggerTypeLabel(type: string) {
  const map: Record<string, string> = { scheduled: '定时', event: '事件', manual: '手动' }
  return map[type] || type
}

function triggerTypeColor(type: string) {
  const map: Record<string, string> = { scheduled: 'blue', event: 'orange', manual: 'gray' }
  return map[type] || 'gray'
}

function formulaLabel(formula: string) {
  const map: Record<string, string> = {
    linear_daily: '按天递增',
    fixed: '固定金额',
    cumulative_increment: '累计递增',
    custom: '自定义'
  }
  return map[formula] || formula
}

// ==================== Table Columns ====================
const ruleColumns: ColumnDef[] = [
  {
    type: 'render', title: '规则名称', key: 'name', width: 200,
    render: (record: any) => h('div', { style: 'display:flex;flex-direction:column;gap:2px' }, [
      h('span', { style: 'font-weight:500;font-size:13px;color:var(--tf-text-primary)' }, record.name),
      record.description ? h('span', { style: 'font-size:11px;color:var(--tf-text-tertiary)' }, record.description) : null,
    ]),
  },
  {
    type: 'badge', title: '触发类型', key: 'triggerType', width: 100,
    labelMap: { scheduled: '定时', event: '事件', manual: '手动' },
    colorMap: { scheduled: 'blue', event: 'orange', manual: 'gray' },
  },
  { type: 'text', title: '计算公式', key: 'scoreFormula', width: 120, format: (v: string) => formulaLabel(v) },
  { type: 'switch', title: '状态', key: 'enabled', width: 80, onChange: (record: any) => handleToggle(record) },
  { type: 'count', title: '执行次数', key: 'executionCount', width: 90, align: 'center' },
  {
    type: 'render', title: '最近执行', width: 150,
    render: (record: any) => h('span', { style: 'font-size:12px;color:var(--tf-text-secondary)' },
      record.lastExecutedAt ? formatDateTime(record.lastExecutedAt) : '从未执行'),
  },
  {
    type: 'actions', width: 180,
    actions: (record: any) => [
      { label: '编辑', onClick: (r) => handleEdit(r) },
      { label: '执行', hidden: record.triggerType !== 'scheduled', onClick: (r) => handleExecuteNow(r) },
      { label: '删除', danger: true, onClick: (r) => handleDelete(r) },
    ],
  },
]

const logColumns: ColumnDef[] = [
  {
    type: 'render', title: '工单', width: 140,
    render: (record: any) => h('a', {
      style: 'color:var(--tf-accent);font-weight:500;cursor:pointer',
      onClick: () => goToIssue(record.issueId)
    }, record.issueKey),
  },
  { type: 'text', title: '工单标题', key: 'issueTitle', width: 200, ellipsis: true },
  { type: 'text', title: '规则', key: 'ruleName', width: 150 },
  { type: 'text', title: '对象', key: 'targetUserName', width: 100 },
  {
    type: 'render', title: '金额/分数', width: 100, align: 'right',
    render: (record: any) => h('span', { style: 'font-weight:500;color:var(--tf-accent)' }, `¥${record.score}`),
  },
  { type: 'date', title: '执行时间', key: 'executedAt', width: 150, format: 'datetime' },
  {
    type: 'actions', width: 80,
    actions: (record: any) => [
      { label: '删除', danger: true, onClick: (r) => handleDeleteLog(r) },
    ],
  },
]



function goToIssue(issueId: string) {
  router.push(`/issues/${issueId}`)
}

// ==================== Data Loading ====================
async function loadRules() {
  loadingRules.value = true
  try {
    const res = await scoreRuleApi.listRules()
    if (res.code === 0) {
      rules.value = res.data
    }
  } catch (e) {
    // silent
  } finally {
    loadingRules.value = false
  }
}

async function loadLogs() {
  loadingLogs.value = true
  try {
    const params: any = {
      page: logPagination.current,
      pageSize: logPagination.pageSize
    }
    if (logFilter.ruleId) params.ruleId = logFilter.ruleId
    if (logFilter.dateRange) {
      params.startDate = logFilter.dateRange[0]
      params.endDate = logFilter.dateRange[1]
    }
    const res = await scoreRuleApi.listLogs(params)
    if (res.code === 0) {
      logs.value = res.data.list
      logPagination.total = res.data.pagination.total
    }
  } catch (e) {
    // silent
  } finally {
    loadingLogs.value = false
  }
}

async function loadStatistics() {
  try {
    const params: any = { rankLimit: 20 }
    if (statsFilter.ruleId) params.ruleId = statsFilter.ruleId
    if (statsFilter.dateRange) {
      params.startDate = statsFilter.dateRange[0]
      params.endDate = statsFilter.dateRange[1]
    }
    const res = await scoreRuleApi.getStatistics(params)
    if (res.code === 0) {
      statistics.value = res.data
    }
  } catch (e) {
    // silent
  }
}

function onLogPageChange(page: number) {
  logPagination.current = page
  loadLogs()
}

function onLogPageSizeChange(size: number) {
  logPagination.pageSize = size
  logPagination.current = 1
  loadLogs()
}

// ==================== Rule CRUD ====================
function showCreateForm() {
  editingRule.value = null
  Object.assign(formData, {
    name: '',
    description: '',
    triggerType: 'scheduled',
    scoreFormula: 'linear_daily',
    targetField: 'assignee',
    dedupStrategy: 'daily',
    scheduleCron: '0 0 9 * * ?',
    triggerConfig: '{"issueFilter": {"statuses_not_in_category": ["closed"], "has_due_date": true, "due_date_before": "now"}}',
    scoreConfig: '{"base_amount": 5, "field": "due_date"}'
  })
  formVisible.value = true
}

function handleEdit(rule: RuleDefinitionVO) {
  editingRule.value = rule
  Object.assign(formData, {
    name: rule.name,
    description: rule.description || '',
    triggerType: rule.triggerType,
    scoreFormula: rule.scoreFormula,
    targetField: rule.targetField,
    dedupStrategy: rule.dedupStrategy,
    scheduleCron: rule.scheduleCron || '',
    triggerConfig: rule.triggerConfig || '{}',
    scoreConfig: rule.scoreConfig || '{}'
  })
  formVisible.value = true
}

async function handleFormSubmit() {
  const valid = await formRef.value?.validate()
  if (valid) return

  // Validate JSON fields
  try {
    JSON.parse(formData.triggerConfig)
  } catch {
    Message.error('触发条件 JSON 格式无效')
    return
  }
  try {
    JSON.parse(formData.scoreConfig)
  } catch {
    Message.error('计算配置 JSON 格式无效')
    return
  }

  submitting.value = true
  try {
    const dto = { ...formData }
    if (editingRule.value) {
      await scoreRuleApi.updateRule(editingRule.value.id, dto)
      Message.success('规则已更新')
    } else {
      await scoreRuleApi.createRule(dto)
      Message.success('规则已创建')
    }
    formVisible.value = false
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleToggle(rule: RuleDefinitionVO) {
  try {
    await scoreRuleApi.toggleRule(rule.id)
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDelete(rule: RuleDefinitionVO) {
  try {
    await scoreRuleApi.deleteRule(rule.id)
    Message.success('规则已删除')
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

async function handleExecuteNow(rule: RuleDefinitionVO) {
  try {
    const res = await scoreRuleApi.executeNow(rule.id)
    if (res.code === 0) {
      Message.success(`执行完成，新增 ${res.data.executedCount} 条记录`)
      await loadRules()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行失败')
  }
}

async function handleDeleteLog(log: RuleExecutionLogVO) {
  try {
    await scoreRuleApi.deleteLog(log.id)
    Message.success('记录已删除')
    await loadLogs()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

// ==================== Lifecycle ====================
onMounted(async () => {
  await loadRules()
  loadLogs()
  loadStatistics()
})
</script>

<style scoped>
.rule-tabs {
  :deep(.arco-tabs-content) {
    padding-top: 16px;
  }
}

/* Rule list */
.rule-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rule-name {
  font-weight: 500;
  color: var(--color-text-1);
}

.rule-desc {
  font-size: 12px;
  color: var(--color-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}

.formula-label {
  font-size: 12px;
  color: var(--color-text-2);
}

.time-text {
  font-size: 12px;
  color: var(--color-text-3);
}

.time-text.empty {
  color: var(--color-text-4);
  font-style: italic;
}

/* Logs */
.log-filters,
.stats-filters {
  margin-bottom: 16px;
}

.issue-link {
  color: var(--color-primary-6);
  cursor: pointer;
  font-weight: 500;
}

.issue-link:hover {
  text-decoration: underline;
}

.score-value {
  font-weight: 600;
  color: var(--color-danger-6);
}

/* Statistics */
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.stats-card {
  background: var(--color-bg-2);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
}

.stats-card.wide {
  grid-column: 1 / -1;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0 0 12px;
}

.ranking-list,
.summary-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ranking-item,
.summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  background: var(--color-fill-1);
}

.rank-number {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-fill-3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
}

.rank-number.top3 {
  background: var(--color-warning-light-2);
  color: var(--color-warning-6);
}

.rank-name,
.summary-name {
  flex: 1;
  font-size: 13px;
  color: var(--color-text-1);
}

.rank-score,
.summary-score {
  font-weight: 600;
  color: var(--color-danger-6);
  font-size: 14px;
}

.rank-count,
.summary-count {
  font-size: 12px;
  color: var(--color-text-3);
  min-width: 40px;
  text-align: right;
}

/* Form */
.code-input :deep(textarea) {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
}
</style>
