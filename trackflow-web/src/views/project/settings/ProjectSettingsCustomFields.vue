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
          <p class="section-desc">管理本项目使用的自定义字段。点击字段可配置条件显示规则。</p>
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

      <!-- 主体区域：字段列表 + 详情面板 -->
      <!-- 主体区域：字段列表 + 详情面板 -->
      <div v-if="fieldList.length > 0" class="fields-body">
        <!-- 字段列表 -->
        <div class="field-list">
        <div
          v-for="(field, index) in fieldList"
          :key="field.id"
          class="field-item"
          :class="{
            'is-global': field.isForAll,
            'is-selected': selectedField?.id === field.id,
            'is-dragging': dragIndex === index,
            'is-drag-over': dragOverIndex === index
          }"
          :draggable="canManage && !isArchived"
          @click="selectField(field)"
          @dragstart="onDragStart($event, index)"
          @dragover.prevent="onDragOver($event, index)"
          @dragleave="onDragLeave"
          @drop.prevent="onDrop($event, index)"
          @dragend="onDragEnd"
        >
          <!-- 拖拽把手（管理员可见，全局字段不可拖拽） -->
          <div
            v-if="canManage && !isArchived"
            class="drag-handle"
            :title="field.isForAll ? '全局字段不可拖拽排序' : '拖拽调整顺序'"
            :style="{ cursor: field.isForAll ? 'not-allowed' : 'grab', opacity: field.isForAll ? 0.3 : 1 }"
            @click.stop
          >
            <svg width="12" height="16" viewBox="0 0 12 16" fill="currentColor">
              <circle cx="3" cy="3" r="1.5"/>
              <circle cx="9" cy="3" r="1.5"/>
              <circle cx="3" cy="8" r="1.5"/>
              <circle cx="9" cy="8" r="1.5"/>
              <circle cx="3" cy="13" r="1.5"/>
              <circle cx="9" cy="13" r="1.5"/>
            </svg>
          </div>
          <div class="field-main">
            <div class="field-info">
              <span class="field-name">{{ field.name }}</span>
              <span class="field-type-badge">{{ formatFieldType(field.fieldFormat) }}</span>
              <span v-if="field.effectiveIsRequired" class="field-required-badge">
                必填{{ field.projectIsRequired != null ? ' (项目)' : '' }}
              </span>
              <span v-if="field.isForAll" class="field-global-badge">全局</span>
              <span v-if="field.conditionFieldId" class="field-condition-badge">
                <icon-eye-invisible :size="11" /> 条件显示
              </span>
              <span v-if="field.visibleToRoles && field.visibleToRoles.length > 0" class="field-visibility-badge">
                <icon-lock :size="11" /> 受限可见
              </span>
            </div>
            <div class="field-meta">
              <span v-if="field.conditionFieldId" class="field-condition-hint">
                仅当「{{ getFieldName(field.conditionFieldId) }}」为指定值时显示
              </span>
              <span v-else-if="field.effectiveDefaultValue" class="field-default">
                默认值: {{ formatDefaultValue(field) }}{{ field.projectDefaultValue != null ? ' (项目)' : '' }}
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
              @click.stop="confirmDetach(field)"
            >
              移除
            </a-button>
          </div>
        </div>
      </div>

      <!-- 字段详情面板（条件配置） -->
      <div v-if="selectedField && canManage && !isArchived" class="field-detail-panel">
        <div class="detail-panel-header">
          <h4 class="detail-panel-title">{{ selectedField.name }}</h4>
          <a-button type="text" size="mini" @click="selectedField = null">
            <icon-close />
          </a-button>
        </div>

        <div class="detail-panel-body">
          <!-- 高级设置：条件显示 -->
          <div class="condition-section">
            <h5 class="condition-title">高级设置</h5>

            <div class="condition-form">
              <div class="condition-row">
                <label class="condition-label">Show only when</label>
                <a-select
                  v-model="conditionForm.conditionFieldId"
                  placeholder="选择条件字段..."
                  allow-clear
                  size="small"
                  @change="onConditionFieldChange"
                >
                  <a-option
                    v-for="cf in eligibleConditionFields"
                    :key="cf.id"
                    :value="cf.id"
                  >
                    {{ cf.name }}
                  </a-option>
                </a-select>
              </div>

              <div v-if="conditionForm.conditionFieldId" class="condition-row">
                <label class="condition-label">is set to</label>
                <a-select
                  v-model="conditionForm.conditionValues"
                  placeholder="选择触发值..."
                  multiple
                  size="small"
                  :max-tag-count="3"
                >
                  <a-option
                    v-for="opt in conditionFieldOptions"
                    :key="opt.id"
                    :value="opt.id"
                  >
                    {{ opt.value }}
                  </a-option>
                </a-select>
              </div>

              <div class="condition-actions">
                <a-button
                  type="primary"
                  size="small"
                  :loading="savingCondition"
                  :disabled="!isConditionDirty"
                  @click="saveCondition"
                >
                  保存条件
                </a-button>
                <a-button
                  v-if="selectedField.conditionFieldId"
                  type="text"
                  size="small"
                  @click="clearCondition"
                >
                  清除条件
                </a-button>
              </div>

              <!-- 清除隐藏值按钮 -->
              <div v-if="selectedField.conditionFieldId" class="clear-hidden-section">
                <a-divider :margin="12" />
                <p class="clear-hidden-hint">
                  如果修改了条件配置，已隐藏字段中的旧值不会自动删除。
                  使用此操作可清除所有条件不满足的 issue 中的字段值。
                </p>
                <a-button
                  type="outline"
                  size="small"
                  status="warning"
                  :loading="clearingValues"
                  @click="handleClearHiddenValues"
                >
                  清除隐藏值
                </a-button>
              </div>

              <!-- 项目级覆盖：必填性 + 默认值 -->
              <a-divider :margin="16" />
              <h5 class="condition-title">项目级覆盖</h5>

              <div class="condition-row">
                <label class="condition-label">必填性</label>
                <a-select
                  v-model="overrideForm.isRequiredMode"
                  size="small"
                  @change="onOverrideChange"
                >
                  <a-option value="inherit">继承全局设置 ({{ selectedField?.isRequired ? '必填' : '非必填' }})</a-option>
                  <a-option value="required">本项目必填</a-option>
                  <a-option value="optional">本项目非必填</a-option>
                </a-select>
              </div>

              <div class="condition-row">
                <label class="condition-label">默认值</label>
                <a-select
                  v-model="overrideForm.defaultValueMode"
                  size="small"
                  @change="onOverrideChange"
                >
                  <a-option value="inherit">继承全局设置{{ selectedField?.defaultValue ? ` (${formatDefaultValue(selectedField, true)})` : '' }}</a-option>
                  <a-option value="custom">本项目自定义</a-option>
                  <a-option value="none">本项目无默认值</a-option>
                </a-select>
              </div>

              <div v-if="overrideForm.defaultValueMode === 'custom'" class="condition-row">
                <label class="condition-label">自定义默认值</label>
                <a-input
                  v-if="selectedField && !['list', 'user', 'bool'].includes(selectedField.fieldFormat)"
                  v-model="overrideForm.defaultValue"
                  size="small"
                  placeholder="输入默认值..."
                  @input="onOverrideChange"
                />
                <a-select
                  v-else-if="selectedField?.fieldFormat === 'list'"
                  v-model="overrideForm.defaultValue"
                  size="small"
                  placeholder="选择默认选项..."
                  allow-clear
                  @change="onOverrideChange"
                >
                  <a-option
                    v-for="opt in activeOptions"
                    :key="opt.id"
                    :value="opt.id"
                  >
                    {{ opt.value }}
                  </a-option>
                </a-select>
                <a-select
                  v-else-if="selectedField?.fieldFormat === 'bool'"
                  v-model="overrideForm.defaultValue"
                  size="small"
                  placeholder="选择默认值..."
                  allow-clear
                  @change="onOverrideChange"
                >
                  <a-option value="true">是 (true)</a-option>
                  <a-option value="false">否 (false)</a-option>
                </a-select>
                <a-input
                  v-else
                  v-model="overrideForm.defaultValue"
                  size="small"
                  placeholder="输入默认值..."
                  @input="onOverrideChange"
                />
              </div>

              <div class="condition-actions">
                <a-button
                  type="primary"
                  size="small"
                  :loading="savingOverride"
                  :disabled="!isOverrideDirty"
                  @click="saveOverride"
                >
                  保存覆盖
                </a-button>
                <a-button
                  v-if="hasOverrideConfig"
                  type="text"
                  size="small"
                  @click="clearOverride"
                >
                  恢复全局
                </a-button>
              </div>

              <!-- 当前有效值提示 -->
              <div v-if="selectedField" class="override-effective-hint">
                <span class="effective-label">有效配置：</span>
                <span :class="['effective-value', { 'is-required': selectedField.effectiveIsRequired }]">
                  {{ selectedField.effectiveIsRequired ? '必填' : '非必填' }}
                </span>
                <span v-if="selectedField.effectiveDefaultValue" class="effective-default">
                  · 默认: {{ formatDefaultValue(selectedField) }}
                </span>
              </div>

              <!-- 角色可见性/可编辑性 -->
              <a-divider :margin="16" />
              <h5 class="condition-title">字段权限</h5>

              <div class="condition-row">
                <label class="condition-label">Visible to（对谁可见）</label>
                <a-select
                  v-model="visibilityForm.visibleToRoles"
                  placeholder="所有项目成员"
                  allow-clear
                  multiple
                  size="small"
                  :max-tag-count="2"
                >
                  <a-option
                    v-for="role in projectRoles"
                    :key="role.id"
                    :value="Number(role.id)"
                  >
                    {{ role.name }}
                  </a-option>
                </a-select>
                <span class="visibility-hint">留空 = 所有项目成员可见</span>
              </div>

              <div class="condition-row">
                <label class="condition-label">Updatable by（谁可编辑）</label>
                <a-select
                  v-model="visibilityForm.updatableByRoles"
                  placeholder="所有可见用户"
                  allow-clear
                  multiple
                  size="small"
                  :max-tag-count="2"
                >
                  <a-option
                    v-for="role in projectRoles"
                    :key="role.id"
                    :value="Number(role.id)"
                  >
                    {{ role.name }}
                  </a-option>
                </a-select>
                <span class="visibility-hint">留空 = 所有可见此字段的用户可编辑</span>
              </div>

              <div class="condition-actions">
                <a-button
                  type="primary"
                  size="small"
                  :loading="savingVisibility"
                  :disabled="!isVisibilityDirty"
                  @click="saveVisibility"
                >
                  保存权限
                </a-button>
                <a-button
                  v-if="hasVisibilityConfig"
                  type="text"
                  size="small"
                  @click="clearVisibility"
                >
                  清除限制
                </a-button>
              </div>
            </div>
          </div>
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
            <p class="dialog-empty-hint">
              如需创建新字段，请前往<router-link to="/admin/custom-fields" class="dialog-empty-link">全局管理后台</router-link>。
            </p>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import {
  IconLock,
  IconPlus,
  IconApps,
  IconCheckCircle,
  IconExclamationCircleFill,
  IconEyeInvisible,
  IconClose
} from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { customFieldApi } from '@/api'
import { workflowApi } from '@/api'
import type { ProjectDetailVO, CustomFieldDefinitionVO, CustomFieldOptionVO, RoleVO } from '@/api/types'

const props = defineProps<{
  project: ProjectDetailVO
  canManage: boolean
  isArchived: boolean
}>()

// State
const loading = ref(true)
const fieldList = ref<CustomFieldDefinitionVO[]>([])

// Drag-and-drop reorder state
const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)
const reordering = ref(false)

// Add dialog
const showAddDialog = ref(false)
const availableLoading = ref(false)
const availableFields = ref<CustomFieldDefinitionVO[]>([])

// Detach dialog
const showDetachDialog = ref(false)
const detaching = ref(false)
const detachTarget = ref<CustomFieldDefinitionVO | null>(null)

// Condition management
const selectedField = ref<CustomFieldDefinitionVO | null>(null)
const savingCondition = ref(false)
const clearingValues = ref(false)
const conditionForm = reactive({
  conditionFieldId: null as string | null,
  conditionValues: [] as string[]
})

// Visibility management
const projectRoles = ref<RoleVO[]>([])
const savingVisibility = ref(false)
const visibilityForm = reactive({
  visibleToRoles: [] as number[],
  updatableByRoles: [] as number[]
})

// Override management (project-level isRequired + defaultValue)
const savingOverride = ref(false)
const overrideForm = reactive({
  isRequiredMode: 'inherit' as 'inherit' | 'required' | 'optional',
  defaultValueMode: 'inherit' as 'inherit' | 'custom' | 'none',
  defaultValue: '' as string
})

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

function getFieldName(fieldId: string): string {
  const field = fieldList.value.find(f => f.id === fieldId)
  return field?.name || '未知字段'
}

/**
 * 格式化默认值显示。
 * 对于列表类型字段，将选项 ID 转换为选项名称。
 * 对于布尔类型字段，将 true/false 转换为 是/否。
 * @param field 字段定义
 * @param useGlobalDefault 是否使用全局默认值（而非有效默认值）
 * @returns 格式化后的默认值显示文本
 */
function formatDefaultValue(field: CustomFieldDefinitionVO, useGlobalDefault = false): string {
  const defaultValue = useGlobalDefault ? field.defaultValue : field.effectiveDefaultValue
  if (!defaultValue) return ''

  // 列表类型：从 options 中查找选项名称
  if (field.fieldFormat === 'list' && field.options && field.options.length > 0) {
    const option = field.options.find(opt => opt.id === defaultValue)
    if (option) {
      return option.value
    }
    // 如果找不到对应选项，返回原始值（可能是旧数据或已删除的选项）
    return defaultValue
  }

  // 布尔类型：转换为中文
  if (field.fieldFormat === 'bool') {
    return defaultValue === 'true' ? '是' : '否'
  }

  // 其他类型：直接返回
  return defaultValue
}

/**
 * 可用作条件源的字段列表：
 * - 必须是 list 类型
 * - 必须是单值（非 multi）
 * - 不能是当前选中的字段本身
 * - 不能自身已有条件（禁止链式依赖）
 */
const eligibleConditionFields = computed(() => {
  if (!selectedField.value) return []
  return fieldList.value.filter(f =>
    f.id !== selectedField.value!.id &&
    f.fieldFormat === 'list' &&
    !f.isMulti &&
    !f.conditionFieldId // 禁止链式
  )
})

/**
 * 当前选中条件源字段的选项列表
 */
const conditionFieldOptions = computed<CustomFieldOptionVO[]>(() => {
  if (!conditionForm.conditionFieldId) return []
  const field = fieldList.value.find(f => f.id === conditionForm.conditionFieldId)
  return (field?.options || []).filter(o => !o.isArchived)
})

/**
 * 条件是否有修改
 */
const isConditionDirty = computed(() => {
  if (!selectedField.value) return false
  const origFieldId = selectedField.value.conditionFieldId || null
  const origValues = selectedField.value.conditionValues || []
  const currentFieldId = conditionForm.conditionFieldId || null
  const currentValues = conditionForm.conditionValues || []

  if (origFieldId !== currentFieldId) return true
  if (origValues.length !== currentValues.length) return true
  return !origValues.every(v => currentValues.includes(v))
})

// ===== Drag-and-drop reorder =====

function onDragStart(event: DragEvent, index: number) {
  const field = fieldList.value[index]
  // 全局字段不允许拖拽
  if (field.isForAll) {
    event.preventDefault()
    return
  }
  dragIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
  }
}

function onDragOver(event: DragEvent, index: number) {
  if (dragIndex.value === null || dragIndex.value === index) return
  dragOverIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onDragLeave() {
  dragOverIndex.value = null
}

async function onDrop(event: DragEvent, targetIndex: number) {
  if (dragIndex.value === null || dragIndex.value === targetIndex) {
    onDragEnd()
    return
  }
  const fromIndex = dragIndex.value
  // 重排本地数组
  const newList = [...fieldList.value]
  const [moved] = newList.splice(fromIndex, 1)
  newList.splice(targetIndex, 0, moved)
  fieldList.value = newList

  // 提交给后端
  reordering.value = true
  try {
    const fieldIds = newList.map(f => f.id)
    await customFieldApi.reorderProjectFields(props.project.id, fieldIds)
    Message.success('字段顺序已保存')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存顺序失败')
    // 回滚失败时重新加载
    await loadFields()
  } finally {
    reordering.value = false
  }
  onDragEnd()
}

function onDragEnd() {
  dragIndex.value = null
  dragOverIndex.value = null
}

function selectField(field: CustomFieldDefinitionVO) {
  selectedField.value = field
  conditionForm.conditionFieldId = field.conditionFieldId || null
  conditionForm.conditionValues = field.conditionValues ? [...field.conditionValues] : []
  // Populate visibility form
  visibilityForm.visibleToRoles = field.visibleToRoles ? [...field.visibleToRoles] : []
  visibilityForm.updatableByRoles = field.updatableByRoles ? [...field.updatableByRoles] : []
  // Populate override form
  if (field.projectIsRequired === true) {
    overrideForm.isRequiredMode = 'required'
  } else if (field.projectIsRequired === false) {
    overrideForm.isRequiredMode = 'optional'
  } else {
    overrideForm.isRequiredMode = 'inherit'
  }
  if (field.projectDefaultValue === '') {
    overrideForm.defaultValueMode = 'none'
    overrideForm.defaultValue = ''
  } else if (field.projectDefaultValue != null) {
    overrideForm.defaultValueMode = 'custom'
    overrideForm.defaultValue = field.projectDefaultValue
  } else {
    overrideForm.defaultValueMode = 'inherit'
    overrideForm.defaultValue = ''
  }
}

function onConditionFieldChange() {
  // 切换条件源字段时清空选中的值
  conditionForm.conditionValues = []
}

async function saveCondition() {
  if (!selectedField.value) return
  savingCondition.value = true
  try {
    await customFieldApi.setFieldCondition(props.project.id, selectedField.value.id, {
      conditionFieldId: conditionForm.conditionFieldId || null,
      conditionValues: conditionForm.conditionFieldId ? conditionForm.conditionValues : null
    })
    Message.success('条件设置已保存')
    // 更新本地状态
    selectedField.value.conditionFieldId = conditionForm.conditionFieldId
    selectedField.value.conditionValues = conditionForm.conditionValues.length > 0 ? [...conditionForm.conditionValues] : null
    // 重新加载列表刷新显示
    await loadFields()
    // 重新选中
    const updated = fieldList.value.find(f => f.id === selectedField.value?.id)
    if (updated) selectField(updated)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存条件失败')
  } finally {
    savingCondition.value = false
  }
}

async function clearCondition() {
  if (!selectedField.value) return
  savingCondition.value = true
  try {
    await customFieldApi.setFieldCondition(props.project.id, selectedField.value.id, {
      conditionFieldId: null,
      conditionValues: null
    })
    Message.success('条件已清除')
    conditionForm.conditionFieldId = null
    conditionForm.conditionValues = []
    selectedField.value.conditionFieldId = null
    selectedField.value.conditionValues = null
    await loadFields()
    const updated = fieldList.value.find(f => f.id === selectedField.value?.id)
    if (updated) selectField(updated)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '清除条件失败')
  } finally {
    savingCondition.value = false
  }
}

async function handleClearHiddenValues() {
  if (!selectedField.value) return
  Modal.warning({
    title: '确认清除隐藏值',
    content: `将清除所有条件不满足的工单中「${selectedField.value.name}」字段的值。此操作不可撤销。`,
    okText: '确认清除',
    cancelText: '取消',
    onOk: async () => {
      clearingValues.value = true
      try {
        const res = await customFieldApi.clearHiddenValues(props.project.id, selectedField.value!.id)
        const count = res.data || 0
        Message.success(count > 0 ? `已清除 ${count} 条隐藏值` : '没有需要清除的隐藏值')
      } catch (e: any) {
        Message.error(e.response?.data?.message || '清除失败')
      } finally {
        clearingValues.value = false
      }
    }
  })
}

// ===== Visibility methods =====

const isVisibilityDirty = computed(() => {
  if (!selectedField.value) return false
  const origVisible = selectedField.value.visibleToRoles || []
  const origUpdatable = selectedField.value.updatableByRoles || []
  const currentVisible = visibilityForm.visibleToRoles || []
  const currentUpdatable = visibilityForm.updatableByRoles || []

  if (origVisible.length !== currentVisible.length) return true
  if (origUpdatable.length !== currentUpdatable.length) return true
  if (!origVisible.every(v => currentVisible.includes(v))) return true
  if (!origUpdatable.every(v => currentUpdatable.includes(v))) return true
  return false
})

const hasVisibilityConfig = computed(() => {
  if (!selectedField.value) return false
  return (selectedField.value.visibleToRoles && selectedField.value.visibleToRoles.length > 0) ||
    (selectedField.value.updatableByRoles && selectedField.value.updatableByRoles.length > 0)
})

async function saveVisibility() {
  if (!selectedField.value) return
  savingVisibility.value = true
  try {
    await customFieldApi.setFieldVisibility(props.project.id, selectedField.value.id, {
      visibleToRoles: visibilityForm.visibleToRoles.length > 0 ? visibilityForm.visibleToRoles : null,
      updatableByRoles: visibilityForm.updatableByRoles.length > 0 ? visibilityForm.updatableByRoles : null
    })
    Message.success('字段权限已保存')
    // Update local state
    selectedField.value.visibleToRoles = visibilityForm.visibleToRoles.length > 0 ? [...visibilityForm.visibleToRoles] : null
    selectedField.value.updatableByRoles = visibilityForm.updatableByRoles.length > 0 ? [...visibilityForm.updatableByRoles] : null
    await loadFields()
    const updated = fieldList.value.find(f => f.id === selectedField.value?.id)
    if (updated) selectField(updated)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存字段权限失败')
  } finally {
    savingVisibility.value = false
  }
}

async function clearVisibility() {
  if (!selectedField.value) return
  savingVisibility.value = true
  try {
    await customFieldApi.setFieldVisibility(props.project.id, selectedField.value.id, {
      visibleToRoles: null,
      updatableByRoles: null
    })
    Message.success('字段权限限制已清除')
    visibilityForm.visibleToRoles = []
    visibilityForm.updatableByRoles = []
    selectedField.value.visibleToRoles = null
    selectedField.value.updatableByRoles = null
    await loadFields()
    const updated = fieldList.value.find(f => f.id === selectedField.value?.id)
    if (updated) selectField(updated)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '清除权限失败')
  } finally {
    savingVisibility.value = false
  }
}

async function loadProjectRoles() {
  try {
    const res = await workflowApi.listProjectRoles()
    projectRoles.value = res.data || []
  } catch {
    projectRoles.value = []
  }
}

// ===== Override methods =====

/**
 * 非归档选项列表（用于 list 类型的默认值选择）
 */
const activeOptions = computed(() => {
  if (!selectedField.value?.options) return []
  return selectedField.value.options.filter(o => !o.isArchived)
})

const isOverrideDirty = computed(() => {
  if (!selectedField.value) return false
  // Compare current form with stored field values
  const origRequired = selectedField.value.projectIsRequired
  const origDefault = selectedField.value.projectDefaultValue

  let currentRequired: boolean | null
  if (overrideForm.isRequiredMode === 'inherit') currentRequired = null
  else if (overrideForm.isRequiredMode === 'required') currentRequired = true
  else currentRequired = false

  let currentDefault: string | null
  if (overrideForm.defaultValueMode === 'inherit') currentDefault = null
  else if (overrideForm.defaultValueMode === 'none') currentDefault = ''
  else currentDefault = overrideForm.defaultValue || ''

  if (currentRequired !== (origRequired ?? null)) return true
  if (currentDefault !== (origDefault ?? null)) return true
  return false
})

const hasOverrideConfig = computed(() => {
  if (!selectedField.value) return false
  return selectedField.value.projectIsRequired != null || selectedField.value.projectDefaultValue != null
})

function onOverrideChange() {
  // Trigger reactivity — no-op, computed handles the dirty check
}

async function saveOverride() {
  if (!selectedField.value) return
  savingOverride.value = true
  try {
    let isRequired: boolean | null = null
    if (overrideForm.isRequiredMode === 'required') isRequired = true
    else if (overrideForm.isRequiredMode === 'optional') isRequired = false

    let defaultValue: string | null = null
    if (overrideForm.defaultValueMode === 'custom') defaultValue = overrideForm.defaultValue || ''
    else if (overrideForm.defaultValueMode === 'none') defaultValue = ''

    await customFieldApi.setFieldProjectOverride(props.project.id, selectedField.value.id, {
      isRequired,
      defaultValue
    })
    Message.success('项目级覆盖已保存')
    // Refresh field list
    await loadFields()
    const updated = fieldList.value.find(f => f.id === selectedField.value?.id)
    if (updated) selectField(updated)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存项目级覆盖失败')
  } finally {
    savingOverride.value = false
  }
}

async function clearOverride() {
  if (!selectedField.value) return
  savingOverride.value = true
  try {
    await customFieldApi.setFieldProjectOverride(props.project.id, selectedField.value.id, {
      isRequired: null,
      defaultValue: null
    })
    Message.success('已恢复全局设置')
    overrideForm.isRequiredMode = 'inherit'
    overrideForm.defaultValueMode = 'inherit'
    overrideForm.defaultValue = ''
    await loadFields()
    const updated = fieldList.value.find(f => f.id === selectedField.value?.id)
    if (updated) selectField(updated)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '恢复全局设置失败')
  } finally {
    savingOverride.value = false
  }
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

function handleAddDialogClose() {
  availableFields.value = []
}

async function handleAttach(field: CustomFieldDefinitionVO) {
  try {
    await customFieldApi.attachToProject(props.project.id, field.id)
    Message.success(`字段「${field.name}」已添加到项目`)
    availableFields.value = availableFields.value.filter(f => f.id !== field.id)
    await loadFields()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加字段失败')
  }
}

function confirmDetach(field: CustomFieldDefinitionVO) {
  detachTarget.value = field
  showDetachDialog.value = true
}

async function submitDetach() {
  if (!detachTarget.value) return
  detaching.value = true
  try {
    await customFieldApi.detachFromProject(props.project.id, detachTarget.value.id)
    Message.success(`字段「${detachTarget.value.name}」已从项目移除`)
    showDetachDialog.value = false
    detachTarget.value = null
    if (selectedField.value?.id === detachTarget.value?.id) {
      selectedField.value = null
    }
    await loadFields()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移除字段失败')
  } finally {
    detaching.value = false
  }
}

watch(showAddDialog, (val) => {
  if (val) loadAvailableFields()
})

onMounted(() => {
  loadFields()
  loadProjectRoles()
})
</script>

<style scoped>
.settings-custom-fields {
  max-width: 900px;
}

.fields-body {
  display: flex;
  gap: 24px;
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
  flex: 1;
  min-width: 0;
}

.field-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--tf-bg-surface, var(--tf-bg-elevated));
  transition: background 0.15s;
  cursor: pointer;
}

.field-item:hover {
  background: var(--tf-bg-hover);
}

.field-item.is-selected {
  background: var(--tf-bg-active, var(--tf-bg-hover));
  border-left: 3px solid var(--tf-accent);
  padding-left: 13px;
}

.field-item.is-dragging {
  opacity: 0.5;
  background: var(--tf-bg-hover);
}

.field-item.is-drag-over {
  border-top: 2px solid var(--tf-accent);
}

/* 拖拽把手 */
.drag-handle {
  flex-shrink: 0;
  margin-right: 10px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  line-height: 1;
  user-select: none;
}

.field-item:hover .drag-handle {
  color: var(--tf-text-secondary);
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
  flex-wrap: wrap;
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

.field-condition-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(210, 153, 34, 0.1);
  color: var(--tf-warning, #d29922);
}

.field-visibility-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(130, 80, 223, 0.1);
  color: var(--tf-info, #8250df);
}

.field-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.field-default,
.field-options-count,
.field-condition-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.field-condition-hint {
  font-style: italic;
}

.field-actions {
  flex-shrink: 0;
  margin-left: 12px;
}

/* Detail panel */
.field-detail-panel {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  background: var(--tf-bg-surface, var(--tf-bg-elevated));
  align-self: flex-start;
  position: sticky;
  top: 16px;
}

.detail-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--tf-border-light, var(--tf-border));
}

.detail-panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.detail-panel-body {
  padding: 16px;
}

.condition-section {
  /* no extra margin needed */
}

.condition-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-secondary);
  margin: 0 0 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.condition-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.condition-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.condition-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  font-weight: 500;
}

.condition-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
}

.clear-hidden-section {
  margin-top: 8px;
}

.clear-hidden-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin: 0 0 8px;
  line-height: 1.4;
}

.visibility-hint {
  font-size: 11px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  margin-top: 2px;
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

.dialog-empty-link {
  color: var(--tf-accent);
  text-decoration: none;
  font-weight: 500;
  
  &:hover {
    text-decoration: underline;
  }
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

/* Override section */
.override-effective-hint {
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--tf-bg-hover);
  border-radius: 4px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.effective-label {
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.effective-value {
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--tf-bg-surface);
}

.effective-value.is-required {
  background: rgba(245, 63, 63, 0.08);
  color: var(--color-danger-6, #f53f3f);
}

.effective-default {
  color: var(--tf-text-tertiary);
}
</style>
