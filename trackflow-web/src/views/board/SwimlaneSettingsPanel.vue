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
      </a-radio-group>
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
                :key="s.statusId"
                :value="s.statusId"
              >
                {{ getStatusName(s.statusId) }}
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
import { ref, computed } from 'vue'
import type { BoardColumnVO, BoardColumnMergeGroupVO } from '@/api/types'
import { localizeStatusName } from '@/utils/fieldLabels'

interface MergeGroupLocal {
  mergeGroupId: string
  mergeTitle: string
  statusIds: string[]
}

const props = defineProps<{
  groupByField: string
  mergeGroups: MergeGroupLocal[]
  columns: BoardColumnVO[]
}>()

const emit = defineEmits<{
  'update:groupByField': [value: string]
  'update:mergeGroups': [value: MergeGroupLocal[]]
}>()

function onGroupByChange(val: string | number | boolean) {
  emit('update:groupByField', val as string)
}

function emitMergeChange() {
  emit('update:mergeGroups', [...props.mergeGroups])
}

function getStatusName(statusId: string): string {
  const col = props.columns.find(c => c.statusId === statusId)
  return col ? localizeStatusName(col.statusName) : statusId
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
  return props.columns.filter(c => !usedIds.has(c.statusId))
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
</style>
