<template>
  <div class="swimlane-settings">
    <!-- 泳道分组字段选择 -->
    <div class="settings-section">
      <div class="section-title">泳道分组</div>
      <div class="section-desc">选择一个字段作为泳道分组依据。所有项目成员将看到相同的泳道布局。</div>
      <a-radio-group
        :model-value="groupByField"
        direction="vertical"
        class="swimlane-radio-group"
        @change="onGroupByChange"
      >
        <a-radio value="none">
          <div class="radio-option">
            <span class="radio-label">无泳道</span>
            <span class="radio-desc">平面看板，不分组</span>
          </div>
        </a-radio>
        <a-radio value="assignee">
          <div class="radio-option">
            <span class="radio-label">按负责人</span>
            <span class="radio-desc">每个负责人一行泳道，未分配单独一行</span>
          </div>
        </a-radio>
        <a-radio value="priority">
          <div class="radio-option">
            <span class="radio-label">按优先级</span>
            <span class="radio-desc">Critical / High / Normal / Low 各一行</span>
          </div>
        </a-radio>
        <a-radio value="type">
          <div class="radio-option">
            <span class="radio-label">按类型</span>
            <span class="radio-desc">Bug / Task / Feature 等各一行</span>
          </div>
        </a-radio>
        <a-radio value="sprint">
          <div class="radio-option">
            <span class="radio-label">按迭代</span>
            <span class="radio-desc">每个 Sprint 一行泳道，未规划单独一行</span>
          </div>
        </a-radio>
        <a-radio value="tag">
          <div class="radio-option">
            <span class="radio-label">按标签</span>
            <span class="radio-desc">按工单标签分组（具有多标签的工单显示在第一个匹配泳道中）</span>
          </div>
        </a-radio>
        <a-radio value="parent">
          <div class="radio-option">
            <span class="radio-label">按父工单（Issues 模式）</span>
            <span class="radio-desc">选择一种高层级 Issue 类型作为泳道行，该类型的工单成为泳道标题，其子工单排列在泳道内</span>
          </div>
        </a-radio>
        <a-radio value="dueDate">
          <div class="radio-option">
            <span class="radio-label">按截止日期</span>
            <span class="radio-desc">自动按相对日期范围分组：已过期 / 今天 / 本周 / 下周 / 本月 / 更晚 / 无截止日期</span>
          </div>
        </a-radio>
      </a-radio-group>
    </div>

    <!-- Issues 模式：选择作为泳道行的 Issue 类型 -->
    <div v-if="groupByField === 'parent'" class="settings-section">
      <div class="section-title">泳道 Issue 类型</div>
      <div class="section-desc">
        选择哪种类型的工单作为泳道行标题。只有被选中类型的工单才会成为泳道行；
        其子工单（parent_id 指向该工单的工单）显示在对应泳道内。
      </div>
      <a-select
        :model-value="swimlaneIssueType ?? undefined"
        @update:model-value="$emit('update:swimlaneIssueType', $event as string ?? null)"
        placeholder="选择 Issue 类型（如 Epic、Feature）"
        allow-clear
        @change="onSwimlaneIssueTypeChange"
      >
        <a-option v-for="t in PARENT_ISSUE_TYPES" :key="t.value" :value="t.value">
          {{ t.label }}
        </a-option>
      </a-select>
      <div v-if="!swimlaneIssueType" class="section-desc" style="color: rgb(var(--warning-6))">
        ⚠️ 请选择一种 Issue 类型，否则泳道将无法正确分组
      </div>
    </div>

    <!-- 截止日期泳道说明 -->
    <div v-if="groupByField === 'dueDate'" class="settings-section">
      <div class="section-title">截止日期泳道说明</div>
      <div class="date-range-info">
        <div class="date-range-item">
          <span class="date-range-icon">⚠️</span>
          <div class="date-range-content">
            <span class="date-range-label">已过期</span>
            <span class="date-range-desc">截止日期早于今天的工单</span>
          </div>
        </div>
        <div class="date-range-item">
          <span class="date-range-icon">📅</span>
          <div class="date-range-content">
            <span class="date-range-label">今天</span>
            <span class="date-range-desc">截止日期为今天的工单</span>
          </div>
        </div>
        <div class="date-range-item">
          <span class="date-range-icon">📅</span>
          <div class="date-range-content">
            <span class="date-range-label">本周</span>
            <span class="date-range-desc">截止日期在本周内（周一到周日）</span>
          </div>
        </div>
        <div class="date-range-item">
          <span class="date-range-icon">📅</span>
          <div class="date-range-content">
            <span class="date-range-label">下周</span>
            <span class="date-range-desc">截止日期在下周内</span>
          </div>
        </div>
        <div class="date-range-item">
          <span class="date-range-icon">📅</span>
          <div class="date-range-content">
            <span class="date-range-label">本月</span>
            <span class="date-range-desc">截止日期在本月内（不含本周/下周已覆盖的范围）</span>
          </div>
        </div>
        <div class="date-range-item">
          <span class="date-range-icon">📅</span>
          <div class="date-range-content">
            <span class="date-range-label">更晚</span>
            <span class="date-range-desc">截止日期超出本月</span>
          </div>
        </div>
        <div class="date-range-item">
          <span class="date-range-icon">—</span>
          <div class="date-range-content">
            <span class="date-range-label">无截止日期</span>
            <span class="date-range-desc">未设置截止日期的工单</span>
          </div>
        </div>
      </div>
      <div class="section-desc" style="margin-top: 8px">
        💡 日期范围基于当前日期动态计算，每次加载看板时自动更新。只显示包含工单的泳道。
      </div>
      <div class="section-desc">
        ℹ️ 拖拽卡片到其他泳道不会更新截止日期（只变更状态）。
      </div>
    </div>

    <!-- 泳道值选择器（当选择了非 none 且非 dueDate 的分组字段时显示） -->
    <div v-if="groupByField !== 'none' && groupByField !== 'dueDate'" class="settings-section">
      <div class="section-title">泳道值选择</div>
      <div class="section-desc">
        选择要显示为泳道行的具体值。未选中值的工单将归入"未分类"泳道。
        不选择任何值或全选时，显示所有泳道。
      </div>

      <div class="value-selector">
        <div class="value-selector-actions">
          <a-button size="mini" type="text" @click="selectAll">全选</a-button>
          <a-button size="mini" type="text" @click="deselectAll">取消全选</a-button>
          <span v-if="!isAllSelected" class="selected-count">
            已选 {{ selectedValues?.length || 0 }} / {{ availableValues.length }}
          </span>
          <span v-else class="selected-count">全部显示</span>
        </div>

        <a-spin :loading="loadingValues" class="value-list-container">
          <div class="value-list">
            <div
              v-for="opt in availableValues"
              :key="opt.key"
              class="value-item"
              :class="{ 'value-item--selected': isAllSelected || selectedSet.has(opt.key) }"
              @click="toggleValue(opt.key)"
            >
              <a-checkbox
                :model-value="isAllSelected || selectedSet.has(opt.key)"
                @click.stop
                @change="toggleValue(opt.key)"
              />
              <span class="value-item-label">{{ opt.label }}</span>
            </div>
            <div v-if="!loadingValues && availableValues.length === 0" class="value-list-empty">
              暂无可选值
            </div>
          </div>
        </a-spin>
      </div>

      <!-- 未分类泳道控制 -->
      <div class="uncategorized-settings">
        <div class="uncategorized-toggle">
          <a-checkbox
            :model-value="showUncategorized"
            @change="onShowUncategorizedChange"
          >
            显示未分类泳道
          </a-checkbox>
          <span class="uncategorized-desc">将不匹配任何选中值的工单归入此泳道</span>
        </div>
        <div v-if="showUncategorized" class="uncategorized-position">
          <span class="position-label">位置：</span>
          <a-radio-group
            :model-value="uncategorizedPosition"
            size="small"
            @change="onUncategorizedPositionChange"
          >
            <a-radio value="top">顶部</a-radio>
            <a-radio value="bottom">底部</a-radio>
          </a-radio-group>
        </div>
      </div>
    </div>

    <!-- 列合并配置 -->
    <div class="settings-section">
      <div class="section-title">列合并</div>
      <div class="section-desc">
        将多个状态合并显示为同一列。例如将"待处理"和"重新打开"合并为"待办"列。
        拖入合并列时，工单将被设为该组中第一个状态。
      </div>

      <!-- 已有的合并组 -->
      <div v-if="mergeGroups.length > 0" class="merge-groups">
        <div
          v-for="(group, gIdx) in mergeGroups"
          :key="group.mergeGroupId"
          class="merge-group-card"
        >
          <div class="merge-group-header">
            <a-input
              v-model="group.mergeTitle"
              size="small"
              placeholder="合并列标题"
              class="merge-title-input"
              @change="emitMergeChange"
            />
            <a-button
              size="mini"
              status="danger"
              @click="removeMergeGroup(gIdx)"
            >删除</a-button>
          </div>
          <div class="merge-group-statuses">
            <div
              v-for="(statusId, sIdx) in group.statusIds"
              :key="statusId"
              class="merge-status-item"
            >
              <span
                class="merge-status-color"
                :style="{ backgroundColor: getStatusColor(statusId) }"
              ></span>
              <span class="merge-status-name">{{ getStatusName(statusId) }}</span>
              <a-button
                v-if="group.statusIds.length > 2"
                size="mini"
                type="text"
                class="merge-status-remove"
                @click="removeStatusFromGroup(gIdx, sIdx)"
              >×</a-button>
            </div>
            <!-- 添加状态到组 -->
            <a-select
              placeholder="+ 添加状态"
              size="mini"
              class="merge-add-status"
              allow-search
              :filter-option="filterStatusOption"
              @change="(val: any) => addStatusToGroup(gIdx, val as string)"
            >
              <a-option
                v-for="s in getAvailableStatusesForGroup(gIdx)"
                :key="s.statusId ?? ''"
                :value="s.statusId ?? ''"
              >
                {{ getStatusName(s.statusId ?? '') }}
              </a-option>
            </a-select>
          </div>
        </div>
      </div>

      <!-- 创建新合并组 -->
      <div class="add-merge-group">
        <a-button size="small" type="dashed" long @click="addMergeGroup">
          + 新建合并组
        </a-button>
      </div>

      <!-- 帮助说明 -->
      <div class="merge-help">
        <div class="merge-help-title">合并说明</div>
        <ul class="merge-help-list">
          <li>每个合并组至少包含 2 个状态</li>
          <li>同一个状态只能属于一个合并组</li>
          <li>卡片拖入合并列时，自动设为组内第一个状态</li>
          <li>合并列的 WIP 限制按组内所有卡片总数计算</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { BoardColumnVO } from '@/api/types'
import { projectApi, sprintApi, tagApi, issueApi } from '@/api'

interface MergeGroupLocal {
  mergeGroupId: string
  mergeTitle: string
  statusIds: string[]
}

const props = defineProps<{
  groupByField: string
  selectedValues: string[] | null
  showUncategorized: boolean
  uncategorizedPosition: 'top' | 'bottom'
  mergeGroups: MergeGroupLocal[]
  columns: BoardColumnVO[]
  projectId: string
  swimlaneIssueType?: string | null
}>()

const emit = defineEmits<{
  'update:groupByField': [value: string]
  'update:selectedValues': [value: string[] | null]
  'update:showUncategorized': [value: boolean]
  'update:uncategorizedPosition': [value: 'top' | 'bottom']
  'update:mergeGroups': [value: MergeGroupLocal[]]
  'update:swimlaneIssueType': [value: string | null]
}>()

// ===== 可选值数据源 =====
interface ValueOption {
  key: string
  label: string
}

const availableValues = ref<ValueOption[]>([])
const loadingValues = ref(false)

// 预定义的优先级和类型列表（从 API 动态加载）
const PRIORITY_LABELS: Record<string, string> = { '紧急': '紧急', '高': '高', '普通': '普通', '低': '低' }
const TYPE_LABELS: Record<string, string> = { '任务': '任务', '缺陷': '缺陷', '需求': '需求', '史诗': '史诗', '故事': '故事' }

/** Issues 模式下可作为泳道行的 Issue 类型（层级较高的类型） */
const PARENT_ISSUE_TYPES = [
  { value: 'Epic', label: '史诗 (Epic)' },
  { value: 'Feature', label: '需求 (Feature)' },
  { value: 'Story', label: '用户故事 (Story)' },
  { value: 'Task', label: '任务 (Task)' },
]

/** 当分组字段变化时，加载该字段的可选值 */
async function loadAvailableValues() {
  const field = props.groupByField
  if (field === 'none') {
    availableValues.value = []
    return
  }

  loadingValues.value = true
  try {
    switch (field) {
      case 'assignee': {
        const res = await projectApi.listMembers(props.projectId)
        if (res.data) {
          availableValues.value = res.data.map((m: any) => ({
            key: m.userId,
            label: m.displayName || m.username
          }))
        }
        break
      }
      case 'priority': {
        try {
          const res = await issueApi.getPriorityOptions(props.projectId)
          if (res.code === 0 && res.data) {
            availableValues.value = res.data.map((opt: any) => ({
              key: opt.value,
              label: PRIORITY_LABELS[opt.value] || opt.value
            }))
          }
        } catch {
          availableValues.value = []
        }
        break
      }
      case 'type': {
        try {
          const res = await issueApi.getIssueTypeOptions(props.projectId)
          if (res.code === 0 && res.data) {
            availableValues.value = res.data.map((opt: any) => ({
              key: opt.value,
              label: TYPE_LABELS[opt.value] || opt.value
            }))
          }
        } catch {
          availableValues.value = []
        }
        break
      }
      case 'sprint': {
        const res = await sprintApi.listAll({ projectId: props.projectId })
        if (res.data) {
          availableValues.value = (res.data.list || []).map((s: any) => ({
            key: s.id,
            label: s.name
          }))
        }
        break
      }
      case 'tag': {
        const res = await tagApi.listProjectTags(props.projectId)
        if (res.data) {
          availableValues.value = (res.data as any[]).map((t: any) => ({
            key: t.id,
            label: t.name
          }))
        }
        break
      }
      default:
        availableValues.value = []
    }
  } catch {
    availableValues.value = []
  } finally {
    loadingValues.value = false
  }
}

// 当分组字段变化时重新加载可选值
watch(() => props.groupByField, () => {
  loadAvailableValues()
}, { immediate: true })

/** 已选中的值集合（用于 UI 展示） */
const selectedSet = computed(() => new Set(props.selectedValues || []))

/** 是否全选（null 或空数组时为全选） */
const isAllSelected = computed(() => !props.selectedValues || props.selectedValues.length === 0)

function onGroupByChange(val: string | number | boolean) {
  emit('update:groupByField', val as string)
  // 切换分组维度时重置选中值
  emit('update:selectedValues', null)
  emit('update:showUncategorized', true)
  emit('update:uncategorizedPosition', 'bottom')
  // 切换离开 parent 模式时清空 swimlaneIssueType
  if (val !== 'parent') {
    emit('update:swimlaneIssueType', null)
  }
}

function onSwimlaneIssueTypeChange(val: string | number | boolean | Record<string, any> | (string | number | boolean | Record<string, any>)[] | undefined) {
  emit('update:swimlaneIssueType', (val as string) || null)
}

function toggleValue(key: string) {
  const current = props.selectedValues ? [...props.selectedValues] : []
  const idx = current.indexOf(key)
  if (idx >= 0) {
    current.splice(idx, 1)
  } else {
    current.push(key)
  }
  // 如果全部选中，设为 null（全选模式）
  if (current.length === availableValues.value.length) {
    emit('update:selectedValues', null)
  } else {
    emit('update:selectedValues', current.length > 0 ? current : null)
  }
}

function selectAll() {
  emit('update:selectedValues', null)
}

function deselectAll() {
  emit('update:selectedValues', [])
}

function onShowUncategorizedChange(val: boolean | (string | number | boolean)[]) {
  emit('update:showUncategorized', val as boolean)
}

function onUncategorizedPositionChange(val: string | number | boolean) {
  emit('update:uncategorizedPosition', val as 'top' | 'bottom')
}

function emitMergeChange() {
  emit('update:mergeGroups', [...props.mergeGroups])
}

function getStatusName(statusId: string): string {
  const col = props.columns.find(c => c.statusId === statusId)
  return col ? col.statusName : statusId
}

function getStatusColor(statusId: string): string {
  const col = props.columns.find(c => c.statusId === statusId)
  return col?.statusColor || 'var(--color-border)'
}

/** 获取可以添加到指定合并组的状态（排除已在任何组中的） */
function getAvailableStatusesForGroup(groupIdx: number): BoardColumnVO[] {
  const usedIds = new Set<string>()
  for (const group of props.mergeGroups) {
    for (const sid of group.statusIds) {
      usedIds.add(sid)
    }
  }
  // 当前组内的状态不算"已用"（允许在同组内查看）
  for (const sid of props.mergeGroups[groupIdx].statusIds) {
    usedIds.delete(sid)
  }
  return props.columns.filter(c => c.statusId != null && !usedIds.has(c.statusId))
}

function filterStatusOption(inputValue: string, option: any) {
  const name = getStatusName(option.value)
  return name.toLowerCase().includes(inputValue.toLowerCase())
}

function addMergeGroup() {
  const newGroup: MergeGroupLocal = {
    mergeGroupId: generateId(),
    mergeTitle: '',
    statusIds: []
  }
  emit('update:mergeGroups', [...props.mergeGroups, newGroup])
}

function removeMergeGroup(idx: number) {
  const updated = [...props.mergeGroups]
  updated.splice(idx, 1)
  emit('update:mergeGroups', updated)
}

function addStatusToGroup(groupIdx: number, statusId: string) {
  const updated = [...props.mergeGroups]
  updated[groupIdx] = {
    ...updated[groupIdx],
    statusIds: [...updated[groupIdx].statusIds, statusId]
  }
  emit('update:mergeGroups', updated)
}

function removeStatusFromGroup(groupIdx: number, statusIdx: number) {
  const updated = [...props.mergeGroups]
  const newIds = [...updated[groupIdx].statusIds]
  newIds.splice(statusIdx, 1)
  updated[groupIdx] = { ...updated[groupIdx], statusIds: newIds }
  emit('update:mergeGroups', updated)
}

function generateId(): string {
  return 'mg_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8)
}
</script>

<style scoped>
.swimlane-settings {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.settings-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
}

.section-desc {
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.5;
}

.swimlane-radio-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.swimlane-radio-group :deep(.arco-radio) {
  padding: 8px 12px;
  border-radius: 6px;
  transition: background 0.15s;
}

.swimlane-radio-group :deep(.arco-radio:hover) {
  background: var(--color-fill-1);
}

.radio-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.radio-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.radio-desc {
  font-size: 11px;
  color: var(--color-text-3);
}

/* ===== 列合并 ===== */
.merge-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.merge-group-card {
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 12px;
  background: var(--color-fill-1);
}

.merge-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.merge-title-input {
  flex: 1;
}

.merge-group-statuses {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.merge-status-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: var(--color-fill-2);
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-text-2);
}

.merge-status-color {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}

.merge-status-name {
  white-space: nowrap;
}

.merge-status-remove {
  padding: 0 2px !important;
  height: 16px !important;
  font-size: 14px;
  color: var(--color-text-3);
}
.merge-status-remove:hover {
  color: rgb(var(--danger-6));
}

.merge-add-status {
  width: 130px;
}

.add-merge-group {
  margin-top: 4px;
}

/* ===== 泳道值选择器 ===== */
.value-selector {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.value-selector-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected-count {
  font-size: 11px;
  color: var(--color-text-3);
  margin-left: auto;
}

.value-list-container {
  width: 100%;
}

.value-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 4px;
}

.value-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.value-item:hover {
  background: var(--color-fill-1);
}

.value-item--selected {
  background: var(--color-fill-2);
}

.value-item-label {
  font-size: 13px;
  color: var(--color-text-1);
}

.value-list-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-3);
}

/* ===== 未分类泳道设置 ===== */
.uncategorized-settings {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
}

.uncategorized-toggle {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.uncategorized-desc {
  font-size: 11px;
  color: var(--color-text-3);
  margin-left: 24px;
}

.uncategorized-position {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 24px;
}

.position-label {
  font-size: 12px;
  color: var(--color-text-2);
}

/* ===== 帮助说明 ===== */
.merge-help {
  background: var(--color-fill-1);
  border-radius: 6px;
  padding: 12px 16px;
  margin-top: 4px;
}

.merge-help-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
  margin-bottom: 8px;
}

.merge-help-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.5;
}

.merge-help-list li::before {
  content: '•';
  margin-right: 6px;
  color: var(--color-text-4);
}

/* ===== 截止日期泳道说明 ===== */
.date-range-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
  border: 1px solid var(--color-border);
}

.date-range-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.date-range-icon {
  font-size: 14px;
  width: 20px;
  flex-shrink: 0;
  margin-top: 1px;
}

.date-range-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.date-range-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
}

.date-range-desc {
  font-size: 11px;
  color: var(--color-text-3);
}
</style>
