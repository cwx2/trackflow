<template>
  <a-drawer
    :visible="visible"
    :width="500"
    :footer="false"
    unmount-on-close
    @cancel="handleClose"
  >
    <template #title>
      <div class="panel-title">
        <span>守卫条件配置</span>
        <span class="transition-path">{{ oldStatusName }} → {{ newStatusName }}</span>
      </div>
    </template>

    <a-spin :loading="loading" style="width: 100%">
      <!-- 说明文字 -->
      <a-alert class="guard-info" type="info" :show-icon="true">
        <template #title>什么是守卫条件？</template>
        守卫条件对应 YouTrack 的 Workflow Guard Conditions：当工单满足所有条件时，此状态转换才对用户可见。
        多个条件之间是 <strong>AND</strong> 关系。条件为空时转换无限制。
      </a-alert>

      <!-- 当前守卫条件列表 -->
      <div class="conditions-header">
        <span class="conditions-title">条件列表（{{ conditions.length }} 个）</span>
        <a-tag v-if="conditions.length === 0" color="green" size="small">无限制</a-tag>
        <a-tag v-else color="orange" size="small">AND</a-tag>
      </div>

      <div v-if="conditions.length > 0" class="conditions-list">
        <div
          v-for="(cond, index) in conditions"
          :key="index"
          class="condition-item"
        >
          <!-- 条件行 -->
          <div class="condition-row">
            <a-select
              v-model="cond.field"
              placeholder="选择字段"
              style="width: 140px; flex-shrink: 0"
              :options="fieldOptions"
              @change="() => { if (!needsValue(cond.operator)) cond.value = '' }"
            />
            <a-select
              v-model="cond.operator"
              placeholder="操作符"
              style="width: 140px; flex-shrink: 0"
              :options="getOperatorOptions(cond.field)"
              @change="() => { if (!needsValue(cond.operator)) cond.value = '' }"
            />
            <a-input
              v-if="needsValue(cond.operator)"
              v-model="cond.value"
              placeholder="期望值"
              style="flex: 1"
            />
            <span v-else style="flex: 1; color: var(--text-muted); font-size: 12px; padding: 0 4px;">（无需值）</span>
            <a-button
              type="text"
              size="mini"
              status="danger"
              @click="removeCondition(index)"
            >
              <template #icon><icon-delete /></template>
            </a-button>
          </div>
          <!-- AND 连接符（非最后一项） -->
          <div v-if="index < conditions.length - 1" class="and-separator">
            <span class="and-badge">AND</span>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <icon-check-circle :size="36" style="color: var(--color-success-6)" />
        <p class="empty-title">无守卫条件</p>
        <p class="empty-desc">此转换对满足角色权限的用户始终可见</p>
      </div>

      <!-- 添加条件按钮 -->
      <a-button type="dashed" long style="margin-top: 16px" @click="addCondition">
        <template #icon><icon-plus /></template>
        添加条件
      </a-button>

      <!-- 字段说明 -->
      <div class="field-hint" v-if="conditions.length > 0">
        <span class="field-hint-title">支持的字段说明：</span>
        <ul class="field-hint-list">
          <li>assignee_id — 工单负责人 ID（is_not_empty 检查是否已分配）</li>
          <li>priority — 优先级（equals "High"/"Critical"/"Normal"/"Low"）</li>
          <li>issue_type — 工单类型（equals "Bug"/"Task"/"Feature"）</li>
          <li>sprint_id — 所属 Sprint ID（is_not_empty 检查是否在 Sprint 中）</li>
          <li>due_date — 截止日期（is_not_empty 检查是否已设置）</li>
        </ul>
      </div>
    </a-spin>

    <!-- 底部操作 -->
    <div class="panel-footer">
      <a-space>
        <a-button @click="handleClose">取消</a-button>
        <a-button
          v-if="conditions.length > 0"
          status="danger"
          @click="clearConditions"
        >
          清除全部条件
        </a-button>
        <a-button type="primary" :loading="saving" @click="handleSave">
          保存守卫条件
        </a-button>
      </a-space>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconPlus, IconDelete, IconCheckCircle } from '@arco-design/web-vue/es/icon'
import { workflowApi } from '@/api'
import type { TransitionConditionItem } from '@/api/workflow'

const props = defineProps<{
  visible: boolean
  transitionId: string
  oldStatusName: string
  newStatusName: string
  /** 当前守卫条件 JSON 字符串（来自 WorkflowTransitionVO.conditions） */
  currentConditions?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'saved'): void
}>()

const loading = ref(false)
const saving = ref(false)
const conditions = ref<TransitionConditionItem[]>([])

// 当面板打开时解析当前条件
watch(() => props.visible, (val) => {
  if (val) {
    parseCurrentConditions()
  }
})

function parseCurrentConditions() {
  if (!props.currentConditions || props.currentConditions === '{}' || props.currentConditions === 'null') {
    conditions.value = []
    return
  }
  try {
    const parsed = JSON.parse(props.currentConditions)
    conditions.value = (parsed.conditions || []).map((c: any) => ({
      field: c.field || '',
      operator: c.operator || 'is_not_empty',
      value: c.value || ''
    }))
  } catch {
    conditions.value = []
  }
}

function addCondition() {
  conditions.value.push({ field: 'assignee_id', operator: 'is_not_empty', value: '' })
}

function removeCondition(index: number) {
  conditions.value.splice(index, 1)
}

function clearConditions() {
  conditions.value = []
}

function handleClose() {
  emit('update:visible', false)
}

async function handleSave() {
  // 验证条件完整性
  for (const cond of conditions.value) {
    if (!cond.field || !cond.operator) {
      Message.error('请完整填写所有条件的字段和操作符')
      return
    }
    if (needsValue(cond.operator) && !cond.value) {
      Message.error(`操作符 "${cond.operator}" 需要填写期望值`)
      return
    }
  }

  saving.value = true
  try {
    // 过滤掉 value 为空的字段（is_empty/is_not_empty 不需要 value）
    const cleanConditions = conditions.value.map(c => {
      const item: TransitionConditionItem = { field: c.field, operator: c.operator }
      if (needsValue(c.operator) && c.value) {
        item.value = c.value
      }
      return item
    })
    await workflowApi.updateTransitionConditions(props.transitionId, { conditions: cleanConditions })
    Message.success('守卫条件已保存')
    emit('saved')
    emit('update:visible', false)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// === 字段/操作符配置 ===

const fieldOptions = [
  { label: 'assignee_id（负责人）', value: 'assignee_id' },
  { label: 'priority（优先级）', value: 'priority' },
  { label: 'issue_type（工单类型）', value: 'issue_type' },
  { label: 'reporter_id（报告人）', value: 'reporter_id' },
  { label: 'sprint_id（Sprint）', value: 'sprint_id' },
  { label: 'due_date（截止日期）', value: 'due_date' },
  { label: 'title（标题）', value: 'title' },
  { label: 'status_id（当前状态）', value: 'status_id' },
]

const allOperators = [
  { label: '不为空 (is_not_empty)', value: 'is_not_empty' },
  { label: '为空 (is_empty)', value: 'is_empty' },
  { label: '等于 (equals)', value: 'equals' },
  { label: '不等于 (not_equals)', value: 'not_equals' },
  { label: '包含 (contains)', value: 'contains' },
  { label: '在列表中 (in)', value: 'in' },
]

function getOperatorOptions(field: string) {
  // ID 类字段主要用 is_empty / is_not_empty / equals / in
  if (['assignee_id', 'reporter_id', 'sprint_id', 'status_id'].includes(field)) {
    return allOperators.filter(o => ['is_empty', 'is_not_empty', 'equals', 'not_equals', 'in'].includes(o.value))
  }
  // 日期类字段
  if (field === 'due_date') {
    return allOperators.filter(o => ['is_empty', 'is_not_empty'].includes(o.value))
  }
  // 枚举类字段（priority、issue_type）
  if (['priority', 'issue_type'].includes(field)) {
    return allOperators.filter(o => ['equals', 'not_equals', 'in', 'is_empty', 'is_not_empty'].includes(o.value))
  }
  return allOperators
}

function needsValue(operator: string): boolean {
  return !['is_empty', 'is_not_empty'].includes(operator)
}
</script>

<style scoped>
.panel-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.panel-title span:first-child {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-bright, var(--color-text-1));
}

.transition-path {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary, var(--color-text-3));
}

.guard-info {
  margin-bottom: 16px;
}

.conditions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.conditions-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, var(--color-text-1));
}

.conditions-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.condition-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: var(--bg-secondary, var(--color-fill-2));
  border-radius: 6px;
  border: 1px solid var(--border-color, var(--color-border-2));
}

.and-separator {
  display: flex;
  align-items: center;
  padding: 0 10px;
  height: 20px;
}

.and-badge {
  font-size: 10px;
  font-weight: 600;
  color: var(--color-warning-6, #ff7d00);
  background: rgba(var(--orange-6), 0.1);
  padding: 1px 6px;
  border-radius: 3px;
  letter-spacing: 0.5px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 24px;
  text-align: center;
}

.empty-title {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary, var(--color-text-2));
}

.empty-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted, var(--color-text-4));
}

.field-hint {
  margin-top: 16px;
  padding: 10px 12px;
  background: var(--bg-secondary, var(--color-fill-2));
  border-radius: 6px;
  font-size: 11px;
  color: var(--text-muted, var(--color-text-4));
}

.field-hint-title {
  font-weight: 500;
  color: var(--text-secondary, var(--color-text-2));
  display: block;
  margin-bottom: 6px;
}

.field-hint-list {
  margin: 0;
  padding-left: 16px;
  line-height: 1.8;
}

.panel-footer {
  position: sticky;
  bottom: 0;
  padding: 16px 0;
  background: var(--bg-primary, var(--color-bg-2));
  border-top: 1px solid var(--border-color, var(--color-border-2));
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
