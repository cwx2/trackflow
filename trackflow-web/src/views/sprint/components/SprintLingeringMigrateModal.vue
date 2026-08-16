<template>
  <!-- ===== 遗留工单批量迁移弹窗：选择工单 + 目标 Sprint，一键批量迁移 ===== -->
  <a-modal
    :visible="visible"
    title="迁移遗留工单"
    :width="680"
    :mask-closable="false"
    :footer="false"
    @cancel="handleClose"
  >
    <!-- 加载状态 -->
    <a-spin v-if="loading" :loading="true" style="width: 100%; min-height: 200px; display: flex; align-items: center; justify-content: center" />

    <!-- 空状态 -->
    <div v-else-if="issues.length === 0" style="padding: 32px 0; text-align: center">
      <EmptyState icon="check-circle" title="没有遗留工单" description="所有已完成迭代中的工单均已关闭" />
    </div>

    <!-- 正常内容 -->
    <div v-else class="lingering-migrate-content">
      <!-- 工单列表 -->
      <div class="issue-list-section">
        <div class="section-header">
          <a-checkbox
            :model-value="allSelected"
            :indeterminate="indeterminate"
            @change="toggleAll"
          >
            全选（{{ selectedIds.length }}/{{ issues.length }}）
          </a-checkbox>
        </div>
        <div class="issue-list">
          <div
            v-for="issue in issues"
            :key="issue.id"
            class="issue-item"
          >
            <a-checkbox
              :model-value="selectedIds.includes(issue.id)"
              @change="(val: any) => toggleIssue(issue.id, val)"
            />
            <div class="issue-info">
              <span class="issue-key">{{ issue.issueKey }}</span>
              <span class="issue-title" :title="issue.title">{{ issue.title }}</span>
            </div>
            <div class="issue-meta">
              <span
                v-if="issue.statusName"
                class="issue-status"
                :style="{ '--status-color': issue.statusColor || 'var(--color-text-3)' }"
              >
                {{ issue.statusName }}
              </span>
              <span v-if="issue.sprintName" class="issue-sprint">{{ issue.sprintName }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 目标 Sprint 选择 -->
      <div class="target-section">
        <div class="target-label">移入目标：</div>
        <a-select
          v-if="targetSprints.length > 0"
          v-model="targetSprintId"
          placeholder="选择目标 Sprint"
          size="small"
          style="flex: 1"
        >
          <a-option v-for="s in targetSprints" :key="s.id" :value="s.id">
            {{ s.name }}
            <span class="target-status-badge">{{ s.status === 'active' ? '进行中' : '计划中' }}</span>
          </a-option>
        </a-select>
        <div v-else class="no-target-hint">
          <icon-exclamation-circle />
          <span>当前没有可用的目标 Sprint</span>
          <a-button size="mini" type="text" @click="$emit('createSprint')">创建新 Sprint</a-button>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-footer">
        <a-button size="small" @click="handleClose">取消</a-button>
        <a-button
          type="primary"
          size="small"
          :disabled="selectedIds.length === 0 || !targetSprintId"
          :loading="migrating"
          @click="handleMigrate"
        >
          移入（{{ selectedIds.length }} 个工单）
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
/**
 * SprintLingeringMigrateModal — 遗留工单批量迁移弹窗
 *
 * 职责：
 * - 加载并展示项目中所有已完成 Sprint 的未关闭工单
 * - 提供全选/部分选择机制
 * - 提供目标 Sprint 选择器（planned/active）
 * - 执行批量迁移操作（调用 issue batch API）
 *
 * 对外接口：
 * - Props：visible, projectId
 * - Emits：close（关闭弹窗）, migrated（迁移成功后通知父组件刷新）, createSprint（无可用目标时触发创建）
 */
import { ref, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconExclamationCircle } from '@arco-design/web-vue/es/icon'
import { sprintApi, issueApi } from '@/api'
import { EmptyState } from '@/components/base'
import type { LingeringIssueItem, LingeringTargetSprintItem } from '@/api/types'

const props = defineProps<{
  visible: boolean
  projectId: string
}>()

const emit = defineEmits<{
  close: []
  migrated: [count: number]
  createSprint: []
}>()

// ===== 数据状态 =====
const loading = ref(false)
const issues = ref<LingeringIssueItem[]>([])
const targetSprints = ref<LingeringTargetSprintItem[]>([])
const selectedIds = ref<string[]>([])
const targetSprintId = ref<string | undefined>(undefined)
const migrating = ref(false)

// ===== 选择逻辑 =====
const allSelected = computed(() => issues.value.length > 0 && selectedIds.value.length === issues.value.length)
const indeterminate = computed(() => selectedIds.value.length > 0 && selectedIds.value.length < issues.value.length)

function toggleAll(val: any) {
  if (val) {
    selectedIds.value = issues.value.map(i => i.id)
  } else {
    selectedIds.value = []
  }
}

function toggleIssue(id: string, val: any) {
  if (val) {
    if (!selectedIds.value.includes(id)) {
      selectedIds.value.push(id)
    }
  } else {
    selectedIds.value = selectedIds.value.filter(i => i !== id)
  }
}

// ===== 加载数据 =====
async function loadLingeringIssues() {
  if (!props.projectId) return
  loading.value = true
  try {
    const res = await sprintApi.lingeringIssues(props.projectId)
    if (res.code === 0 && res.data) {
      issues.value = res.data.issues || []
      targetSprints.value = res.data.targetSprints || []
      // 默认全选
      selectedIds.value = issues.value.map(i => i.id)
      // 默认选中第一个目标 Sprint（优先 active）
      const activeSprint = targetSprints.value.find(s => s.status === 'active')
      targetSprintId.value = activeSprint?.id || targetSprints.value[0]?.id || undefined
    }
  } catch (e) {
    Message.error('加载遗留工单失败')
  } finally {
    loading.value = false
  }
}

// ===== 执行迁移 =====
async function handleMigrate() {
  if (selectedIds.value.length === 0 || !targetSprintId.value) return
  migrating.value = true
  try {
    const res = await issueApi.batch({
      operation: 'sprint',
      issueIds: selectedIds.value,
      sprintId: targetSprintId.value
    })
    if (res.code === 0 && res.data) {
      const count = res.data.succeeded || selectedIds.value.length
      Message.success(`已将 ${count} 个工单移入目标 Sprint`)
      emit('migrated', count)
      handleClose()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '迁移操作失败')
  } finally {
    migrating.value = false
  }
}

// ===== 关闭弹窗 =====
function handleClose() {
  emit('close')
}

// ===== 弹窗打开时加载数据 =====
watch(() => props.visible, (val) => {
  if (val) {
    loadLingeringIssues()
  } else {
    // 重置状态
    issues.value = []
    targetSprints.value = []
    selectedIds.value = []
    targetSprintId.value = undefined
  }
})
</script>

<style scoped>
.lingering-migrate-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-header {
  padding: 0 0 8px 0;
  border-bottom: 1px solid var(--color-border-2);
  margin-bottom: 8px;
}

.issue-list {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  transition: background-color 0.15s;
}

.issue-item:hover {
  background: var(--color-fill-2);
}

.issue-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.issue-key {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-2);
  white-space: nowrap;
}

.issue-title {
  font-size: 13px;
  color: var(--color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.issue-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.issue-status {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--status-color) 15%, transparent);
  color: var(--status-color);
}

.issue-sprint {
  font-size: 11px;
  color: var(--color-text-3);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.target-section {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 6px;
  background: var(--color-fill-1);
}

.target-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  white-space: nowrap;
}

.target-status-badge {
  font-size: 11px;
  color: var(--color-text-3);
  margin-left: 4px;
}

.no-target-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-3);
}

.no-target-hint .arco-icon {
  color: var(--color-warning-6);
}

.action-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-2);
}
</style>
