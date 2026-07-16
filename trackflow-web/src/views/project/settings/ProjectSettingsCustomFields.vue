<template>
  <div class="settings-custom-fields">
    <!-- 归档提示 -->
    <div v-if="isArchived" class="archived-notice">
      <icon-lock class="notice-icon" />
      <span>项目已归档，自定义字段设置为只读状态</span>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <a-spin :size="24" />
    </div>

    <template v-else>
      <!-- 顶部说明 + 添加按钮 -->
      <div class="section-header">
        <div class="section-info">
          <h3 class="section-title">自定义字段</h3>
          <p class="section-desc">管理本项目使用的自定义字段。全局字段自动可用，项目字段需手动添加。</p>
        </div>
        <a-button
          v-if="canManage && !isArchived"
          type="primary"
          size="small"
          @click="showAddDialog = true"
        >
          <template #icon><icon-plus /></template>
          添加字段
        </a-button>
      </div>

      <!-- 字段列表 -->
      <div v-if="fieldList.length > 0" class="field-list">
        <div
          v-for="field in fieldList"
          :key="field.id"
          class="field-item"
          :class="{ 'is-global': field.isForAll }"
        >
          <div class="field-main">
            <div class="field-info">
              <span class="field-name">{{ field.name }}</span>
              <span class="field-type-badge">{{ formatFieldType(field.fieldFormat) }}</span>
              <span v-if="field.isRequired" class="field-required-badge">必填</span>
              <span v-if="field.isForAll" class="field-global-badge">全局</span>
            </div>
            <div class="field-meta">
              <span v-if="field.defaultValue" class="field-default">
                默认值: {{ field.defaultValue }}
              </span>
              <span v-if="field.options && field.options.length > 0" class="field-options-count">
                {{ field.options.length }} 个选项
              </span>
            </div>
          </div>
          <div class="field-actions">
            <a-button
              v-if="canManage && !isArchived && !field.isForAll"
              type="text"
              size="mini"
              status="danger"
              @click="confirmDetach(field)"
            >
              移除
            </a-button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <icon-apps class="empty-icon" />
        <h4 class="empty-title">暂无自定义字段</h4>
        <p class="empty-desc">项目尚未附加任何自定义字段。点击"添加字段"从全局字段池中选择或创建新字段。</p>
        <a-button
          v-if="canManage && !isArchived"
          type="primary"
          size="small"
          @click="showAddDialog = true"
        >
          添加字段
        </a-button>
      </div>
    </template>

    <!-- 添加字段弹窗 -->
    <a-modal
      v-model:visible="showAddDialog"
      title="添加自定义字段"
      :footer="false"
      :width="520"
      @close="handleAddDialogClose"
    >
      <div class="add-field-dialog">
        <!-- 可附加的字段列表 -->
        <div v-if="availableLoading" class="dialog-loading">
          <a-spin :size="20" />
          <span>加载可用字段...</span>
        </div>

        <template v-else>
          <div v-if="availableFields.length > 0" class="available-field-list">
            <p class="dialog-hint">选择要附加到本项目的字段：</p>
            <div
              v-for="field in availableFields"
              :key="field.id"
              class="available-field-item"
              @click="handleAttach(field)"
            >
              <div class="available-field-info">
                <span class="field-name">{{ field.name }}</span>
                <span class="field-type-badge">{{ formatFieldType(field.fieldFormat) }}</span>
                <span v-if="field.isRequired" class="field-required-badge">必填</span>
              </div>
              <a-button type="text" size="mini">
                <template #icon><icon-plus /></template>
                附加
              </a-button>
            </div>
          </div>

          <div v-else class="dialog-empty">
            <icon-check-circle class="dialog-empty-icon" />
            <p>所有可用字段已附加到本项目，或当前没有项目级字段。</p>
            <p class="dialog-empty-hint">如需创建新字段，请前往全局管理后台。</p>
          </div>
        </template>
      </div>
    </a-modal>

    <!-- 移除确认弹窗 -->
    <a-modal
      v-model:visible="showDetachDialog"
      title="移除自定义字段"
      ok-text="确认移除"
      cancel-text="取消"
      :ok-loading="detaching"
      :ok-button-props="{ status: 'danger' }"
      @ok="submitDetach"
    >
      <div class="detach-confirm">
        <div class="detach-warning">
          <icon-exclamation-circle-fill class="warning-icon" />
          <span>移除字段后，该字段在本项目所有工单中的值将被清除。此操作不可撤销。</span>
        </div>
        <p class="detach-field-name">
          字段名称：<strong>{{ detachTarget?.name }}</strong>
        </p>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  IconLock,
  IconPlus,
  IconApps,
  IconCheckCircle,
  IconExclamationCircleFill
} from '@arco-design/web-vue/es/icon'
import { Message } from '@arco-design/web-vue'
import { customFieldApi } from '@/api'
import type { ProjectDetailVO, CustomFieldDefinitionVO } from '@/api/types'

const props = defineProps<{
  project: ProjectDetailVO
  canManage: boolean
  isArchived: boolean
}>()

// State
const loading = ref(true)
const fieldList = ref<CustomFieldDefinitionVO[]>([])

// Add dialog
const showAddDialog = ref(false)
const availableLoading = ref(false)
const availableFields = ref<CustomFieldDefinitionVO[]>([])

// Detach dialog
const showDetachDialog = ref(false)
const detaching = ref(false)
const detachTarget = ref<CustomFieldDefinitionVO | null>(null)

// Field type display names
const fieldTypeMap: Record<string, string> = {
  string: '文本(单行)',
  text: '文本(多行)',
  int: '整数',
  float: '浮点数',
  date: '日期',
  datetime: '日期时间',
  bool: '布尔',
  list: '列表',
  user: '用户'
}

function formatFieldType(format: string): string {
  return fieldTypeMap[format] || format
}

// Load fields
async function loadFields() {
  loading.value = true
  try {
    const res = await customFieldApi.listProjectSettingsFields(props.project.id)
    fieldList.value = res.data || []
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载自定义字段失败')
    fieldList.value = []
  } finally {
    loading.value = false
  }
}

// Load available fields for attach
async function loadAvailableFields() {
  availableLoading.value = true
  try {
    const res = await customFieldApi.listAvailableForProject(props.project.id)
    availableFields.value = res.data || []
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载可用字段失败')
    availableFields.value = []
  } finally {
    availableLoading.value = false
  }
}

// Watch add dialog open
function handleAddDialogClose() {
  availableFields.value = []
}

// Attach field
async function handleAttach(field: CustomFieldDefinitionVO) {
  try {
    await customFieldApi.attachToProject(props.project.id, field.id)
    Message.success(`字段「${field.name}」已添加到项目`)
    // Remove from available list
    availableFields.value = availableFields.value.filter(f => f.id !== field.id)
    // Reload field list
    await loadFields()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加字段失败')
  }
}

// Confirm detach
function confirmDetach(field: CustomFieldDefinitionVO) {
  detachTarget.value = field
  showDetachDialog.value = true
}

// Submit detach
async function submitDetach() {
  if (!detachTarget.value) return
  detaching.value = true
  try {
    await customFieldApi.detachFromProject(props.project.id, detachTarget.value.id)
    Message.success(`字段「${detachTarget.value.name}」已从项目移除`)
    showDetachDialog.value = false
    detachTarget.value = null
    await loadFields()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移除字段失败')
  } finally {
    detaching.value = false
  }
}

// Watch showAddDialog to load available fields
import { watch } from 'vue'
watch(showAddDialog, (val) => {
  if (val) {
    loadAvailableFields()
  }
})

onMounted(() => {
  loadFields()
})
</script>

<style scoped>
.settings-custom-fields {
  max-width: 680px;
}

/* Archived notice */
.archived-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  margin-bottom: 24px;
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.notice-icon {
  font-size: 16px;
  color: var(--tf-text-tertiary);
}

/* Loading */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
}

/* Section header */
.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-info {
  flex: 1;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 4px;
}

.section-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

/* Field list */
.field-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  overflow: hidden;
}

.field-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--tf-bg-surface, var(--tf-bg-elevated));
  transition: background 0.15s;
}

.field-item:hover {
  background: var(--tf-bg-hover);
}

.field-item + .field-item {
  border-top: 1px solid var(--tf-border-light, var(--tf-border));
}

.field-main {
  flex: 1;
  min-width: 0;
}

.field-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.field-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.field-type-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--tf-bg-hover);
  color: var(--tf-text-secondary);
}

.field-required-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(245, 63, 63, 0.08);
  color: var(--color-danger-6, #f53f3f);
}

.field-global-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(88, 166, 255, 0.08);
  color: var(--tf-accent);
}

.field-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.field-default,
.field-options-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.field-actions {
  flex-shrink: 0;
  margin-left: 12px;
}

/* Empty state */
.empty-state {
  text-align: center;
  padding: 48px 24px;
  border: 1px dashed var(--tf-border);
  border-radius: 6px;
}

.empty-icon {
  font-size: 36px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  margin-bottom: 12px;
}

.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0 0 16px;
  max-width: 360px;
  margin-left: auto;
  margin-right: auto;
}

/* Add dialog */
.add-field-dialog {
  max-height: 400px;
  overflow-y: auto;
}

.dialog-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

.dialog-hint {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0 0 12px;
}

.available-field-list {
  display: flex;
  flex-direction: column;
}

.available-field-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}

.available-field-item:hover {
  background: var(--tf-bg-hover);
}

.available-field-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dialog-empty {
  text-align: center;
  padding: 32px 16px;
}

.dialog-empty-icon {
  font-size: 32px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  margin-bottom: 8px;
}

.dialog-empty p {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0 0 4px;
}

.dialog-empty-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary) !important;
}

/* Detach confirm */
.detach-confirm {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detach-warning {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  background: rgba(245, 63, 63, 0.06);
  border-radius: 6px;
  font-size: 13px;
  color: var(--tf-text-primary);
}

.warning-icon {
  font-size: 18px;
  color: var(--color-danger-6, #f53f3f);
  flex-shrink: 0;
  margin-top: 1px;
}

.detach-field-name {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0;
}
</style>
