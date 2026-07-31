<template>
  <div class="fields-in-projects">
    <!-- 顶部汇总 + 筛选 -->
    <div class="fip-toolbar">
      <div class="fip-summary" v-if="!loading">
        <span class="summary-text">
          {{ projectFieldsData.length }} 个项目 · {{ totalFieldCount }} 个字段配置
        </span>
        <span v-if="overrideCount > 0" class="summary-override">
          {{ overrideCount }} 个项目级覆盖
        </span>
      </div>
      <div class="fip-actions">
        <a-input
          v-model="keyword"
          placeholder="按项目名称筛选..."
          size="small"
          allow-clear
          style="width: 200px"
        >
          <template #prefix><icon-search /></template>
        </a-input>
        <a-radio-group v-model="groupBy" type="button" size="small">
          <a-radio value="project">按项目分组</a-radio>
          <a-radio value="field">按字段分组</a-radio>
        </a-radio-group>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <a-spin :size="24" />
    </div>

    <!-- 按项目分组视图 -->
    <template v-else-if="groupBy === 'project'">
      <div v-if="filteredByProject.length === 0" class="empty-state">
        <icon-folder />
        <span>没有匹配的项目</span>
      </div>
      <div v-else class="project-groups">
        <div
          v-for="project in filteredByProject"
          :key="project.projectId"
          class="project-group"
        >
          <div class="group-header" @click="toggleGroup(project.projectId)">
            <icon-right :class="{ 'expanded': expandedGroups.has(project.projectId) }" class="expand-icon" />
            <span class="project-name">{{ project.projectName }}</span>
            <span class="project-key">{{ project.projectKey }}</span>
            <span v-if="getProjectOverrideCount(project) > 0" class="override-badge">
              {{ getProjectOverrideCount(project) }} 覆盖
            </span>
            <span class="field-count">{{ project.fields.length }} 个字段</span>
          </div>
          <div v-if="expandedGroups.has(project.projectId)" class="group-fields">
            <div
              v-for="field in project.fields"
              :key="field.id"
              class="field-row"
              :class="{ 'field-row--editing': isEditingField(project.projectId, field.id) }"
              @click="openFieldEditor(project.projectId, field)"
            >
              <div class="field-row-main">
                <span class="field-name">{{ field.name }}</span>
                <a-tag size="small" class="field-type-tag">{{ formatType(field.fieldFormat) }}</a-tag>
                <span v-if="field.isForAll" class="badge badge-global">全局</span>
                <span v-else class="badge badge-project">项目级</span>
                <!-- 必填状态：显示有效值+覆盖指示 -->
                <template v-if="getEffectiveRequired(field)">
                  <span
                    class="badge badge-required"
                    :class="{ 'badge-overridden': field.projectIsRequired !== null }"
                  >
                    必填<template v-if="field.projectIsRequired !== null">(项目级)</template>
                  </span>
                </template>
                <template v-else-if="field.projectIsRequired === false">
                  <span class="badge badge-not-required badge-overridden">
                    非必填(项目级)
                  </span>
                </template>
                <!-- 默认值覆盖指示 -->
                <span v-if="field.projectDefaultValue" class="badge badge-default-override">
                  默认:{{ truncateValue(field.projectDefaultValue) }}
                </span>
                <span v-if="field.isMulti" class="badge badge-multi">多值</span>
                <div class="field-actions" @click.stop>
                  <a-button
                    v-if="!field.isForAll"
                    type="text"
                    size="mini"
                    status="danger"
                    @click="handleDetach(project.projectId, field.id, field.name, project.projectName)"
                  >
                    移除
                  </a-button>
                </div>
              </div>
              <!-- 内联编辑面板 -->
              <div
                v-if="isEditingField(project.projectId, field.id)"
                class="field-override-editor"
                @click.stop
              >
                <div class="editor-title">项目级属性覆盖</div>
                <div class="editor-row">
                  <span class="editor-label">必填性</span>
                  <a-select
                    v-model="editForm.isRequired"
                    size="small"
                    style="width: 160px"
                    placeholder="继承全局设置"
                    allow-clear
                  >
                    <a-option :value="true">必填</a-option>
                    <a-option :value="false">非必填</a-option>
                  </a-select>
                  <span class="editor-hint">
                    全局: {{ field.isRequired ? '必填' : '非必填' }}
                  </span>
                </div>
                <div class="editor-row">
                  <span class="editor-label">允许为空</span>
                  <a-select
                    v-model="editForm.canBeEmpty"
                    size="small"
                    style="width: 160px"
                    placeholder="继承全局设置(允许)"
                    allow-clear
                  >
                    <a-option :value="true">允许为空</a-option>
                    <a-option :value="false">不允许为空</a-option>
                  </a-select>
                  <a-tooltip content="设置为「不允许为空」+ 无默认值时，用户创建工单必须主动选择（参考 YouTrack "Set value"）" position="right">
                    <icon-info-circle class="hint-icon" />
                  </a-tooltip>
                </div>
                <div class="editor-row">
                  <span class="editor-label">默认值</span>
                  <a-input
                    v-model="editForm.defaultValue"
                    size="small"
                    style="width: 160px"
                    placeholder="继承全局设置"
                    allow-clear
                  />
                </div>
                <div class="editor-actions">
                  <a-button size="mini" @click.stop="closeFieldEditor">取消</a-button>
                  <a-button
                    size="mini"
                    type="primary"
                    :loading="saving"
                    @click.stop="saveOverride(project.projectId, field.id)"
                  >
                    保存覆盖
                  </a-button>
                  <a-button
                    v-if="field.hasOverride"
                    size="mini"
                    status="warning"
                    @click.stop="clearOverride(project.projectId, field.id)"
                  >
                    清除覆盖
                  </a-button>
                </div>
              </div>
            </div>
            <!-- 附加字段操作 -->
            <div class="attach-row">
              <a-button type="dashed" size="mini" @click="openAttachDialog(project)">
                <template #icon><icon-plus /></template>
                添加字段
              </a-button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 按字段分组视图 -->
    <template v-else>
      <div v-if="filteredByField.length === 0" class="empty-state">
        <icon-file />
        <span>没有自定义字段</span>
      </div>
      <div v-else class="field-groups">
        <div
          v-for="item in filteredByField"
          :key="item.fieldId"
          class="field-group"
        >
          <div class="group-header" @click="toggleGroup(item.fieldId)">
            <icon-right :class="{ 'expanded': expandedGroups.has(item.fieldId) }" class="expand-icon" />
            <span class="field-name">{{ item.fieldName }}</span>
            <a-tag size="small">{{ formatType(item.fieldFormat) }}</a-tag>
            <span v-if="item.isForAll" class="badge badge-global">全局（所有项目）</span>
            <span v-else class="field-count">{{ item.projects.length }} 个项目</span>
            <span v-if="item.overrideCount > 0" class="override-badge">
              {{ item.overrideCount }} 覆盖
            </span>
          </div>
          <div v-if="expandedGroups.has(item.fieldId)" class="group-fields">
            <template v-if="item.isForAll">
              <div class="info-note">此字段为全局字段，自动对所有项目可用。</div>
              <div
                v-for="proj in item.projects"
                :key="proj.projectId"
                class="field-row"
                :class="{ 'field-row--editing': isEditingField(proj.projectId, item.fieldId) }"
                @click="openFieldEditorByField(proj, item)"
              >
                <div class="field-row-main">
                  <span class="project-name">{{ proj.projectName }}</span>
                  <span class="project-key">{{ proj.projectKey }}</span>
                  <template v-if="proj.hasOverride">
                    <span v-if="proj.projectIsRequired !== null" class="badge badge-required badge-overridden">
                      {{ proj.projectIsRequired ? '必填' : '非必填' }}(项目级)
                    </span>
                    <span v-if="proj.projectDefaultValue" class="badge badge-default-override">
                      默认:{{ truncateValue(proj.projectDefaultValue) }}
                    </span>
                  </template>
                </div>
                <!-- 内联编辑面板 -->
                <div
                  v-if="isEditingField(proj.projectId, item.fieldId)"
                  class="field-override-editor"
                  @click.stop
                >
                  <div class="editor-title">项目级属性覆盖</div>
                  <div class="editor-row">
                    <span class="editor-label">必填性</span>
                    <a-select
                      v-model="editForm.isRequired"
                      size="small"
                      style="width: 160px"
                      placeholder="继承全局设置"
                      allow-clear
                    >
                      <a-option :value="true">必填</a-option>
                      <a-option :value="false">非必填</a-option>
                    </a-select>
                    <span class="editor-hint">
                      全局: {{ item.globalIsRequired ? '必填' : '非必填' }}
                    </span>
                  </div>
                  <div class="editor-row">
                    <span class="editor-label">允许为空</span>
                    <a-select
                      v-model="editForm.canBeEmpty"
                      size="small"
                      style="width: 160px"
                      placeholder="继承全局设置(允许)"
                      allow-clear
                    >
                      <a-option :value="true">允许为空</a-option>
                      <a-option :value="false">不允许为空</a-option>
                    </a-select>
                    <a-tooltip content="设置为「不允许为空」+ 无默认值时，用户创建工单必须主动选择（参考 YouTrack "Set value"）" position="right">
                      <icon-info-circle class="hint-icon" />
                    </a-tooltip>
                  </div>
                  <div class="editor-row">
                    <span class="editor-label">默认值</span>
                    <a-input
                      v-model="editForm.defaultValue"
                      size="small"
                      style="width: 160px"
                      placeholder="继承全局设置"
                      allow-clear
                    />
                  </div>
                  <div class="editor-actions">
                    <a-button size="mini" @click.stop="closeFieldEditor">取消</a-button>
                    <a-button
                      size="mini"
                      type="primary"
                      :loading="saving"
                      @click.stop="saveOverride(proj.projectId, item.fieldId)"
                    >
                      保存覆盖
                    </a-button>
                    <a-button
                      v-if="proj.hasOverride"
                      size="mini"
                      status="warning"
                      @click.stop="clearOverride(proj.projectId, item.fieldId)"
                    >
                      清除覆盖
                    </a-button>
                  </div>
                </div>
              </div>
            </template>
            <template v-else>
              <div
                v-for="proj in item.projects"
                :key="proj.projectId"
                class="field-row"
                :class="{ 'field-row--editing': isEditingField(proj.projectId, item.fieldId) }"
                @click="openFieldEditorByField(proj, item)"
              >
                <div class="field-row-main">
                  <span class="project-name">{{ proj.projectName }}</span>
                  <span class="project-key">{{ proj.projectKey }}</span>
                  <template v-if="proj.hasOverride">
                    <span v-if="proj.projectIsRequired !== null" class="badge badge-required badge-overridden">
                      {{ proj.projectIsRequired ? '必填' : '非必填' }}(项目级)
                    </span>
                    <span v-if="proj.projectDefaultValue" class="badge badge-default-override">
                      默认:{{ truncateValue(proj.projectDefaultValue) }}
                    </span>
                  </template>
                  <div class="field-actions" @click.stop>
                    <a-button
                      type="text"
                      size="mini"
                      status="danger"
                      @click="handleDetach(proj.projectId, item.fieldId, item.fieldName, proj.projectName)"
                    >
                      移除
                    </a-button>
                  </div>
                </div>
                <!-- 内联编辑面板 -->
                <div
                  v-if="isEditingField(proj.projectId, item.fieldId)"
                  class="field-override-editor"
                  @click.stop
                >
                  <div class="editor-title">项目级属性覆盖</div>
                  <div class="editor-row">
                    <span class="editor-label">必填性</span>
                    <a-select
                      v-model="editForm.isRequired"
                      size="small"
                      style="width: 160px"
                      placeholder="继承全局设置"
                      allow-clear
                    >
                      <a-option :value="true">必填</a-option>
                      <a-option :value="false">非必填</a-option>
                    </a-select>
                    <span class="editor-hint">
                      全局: {{ item.globalIsRequired ? '必填' : '非必填' }}
                    </span>
                  </div>
                  <div class="editor-row">
                    <span class="editor-label">允许为空</span>
                    <a-select
                      v-model="editForm.canBeEmpty"
                      size="small"
                      style="width: 160px"
                      placeholder="继承全局设置(允许)"
                      allow-clear
                    >
                      <a-option :value="true">允许为空</a-option>
                      <a-option :value="false">不允许为空</a-option>
                    </a-select>
                    <a-tooltip content="设置为「不允许为空」+ 无默认值时，用户创建工单必须主动选择（参考 YouTrack "Set value"）" position="right">
                      <icon-info-circle class="hint-icon" />
                    </a-tooltip>
                  </div>
                  <div class="editor-row">
                    <span class="editor-label">默认值</span>
                    <a-input
                      v-model="editForm.defaultValue"
                      size="small"
                      style="width: 160px"
                      placeholder="继承全局设置"
                      allow-clear
                    />
                  </div>
                  <div class="editor-actions">
                    <a-button size="mini" @click.stop="closeFieldEditor">取消</a-button>
                    <a-button
                      size="mini"
                      type="primary"
                      :loading="saving"
                      @click.stop="saveOverride(proj.projectId, item.fieldId)"
                    >
                      保存覆盖
                    </a-button>
                    <a-button
                      v-if="proj.hasOverride"
                      size="mini"
                      status="warning"
                      @click.stop="clearOverride(proj.projectId, item.fieldId)"
                    >
                      清除覆盖
                    </a-button>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </template>

    <!-- 附加字段弹窗（双视图：选择已有 / 新建字段） -->
    <a-modal
      v-model:visible="attachDialogVisible"
      :title="attachMode === 'select'
        ? `添加字段到「${attachTarget?.projectName || ''}」`
        : `新建字段并添加到「${attachTarget?.projectName || ''}」`"
      :width="attachMode === 'select' ? 420 : 520"
      :ok-loading="attaching || creatingField"
      :ok-text="attachMode === 'select' ? '添加' : '创建并添加'"
      @ok="attachMode === 'select' ? handleAttach() : handleCreateAndAttach()"
      @cancel="closeAttachDialog"
      unmount-on-close
    >
      <!-- 选择已有字段模式 -->
      <template v-if="attachMode === 'select'">
        <a-select
          v-model="selectedFieldToAttach"
          placeholder="选择要添加的字段"
          allow-search
          :loading="loadingAvailable"
        >
          <a-option v-for="f in availableFields" :key="f.id" :value="f.id">
            {{ f.name }}（{{ formatType(f.fieldFormat) }}）
          </a-option>
        </a-select>
        <div v-if="availableFields.length === 0 && !loadingAvailable" class="no-available-hint">
          <span>所有非全局字段已添加到此项目。</span>
          <a class="create-field-link" @click="switchToCreateMode">创建新字段</a>
        </div>
        <div v-else class="attach-footer-hint">
          <span>找不到需要的字段？</span>
          <a class="create-field-link" @click="switchToCreateMode">新建字段</a>
        </div>
      </template>

      <!-- 新建字段模式 -->
      <template v-else>
        <div class="create-mode-back" @click="switchToSelectMode">
          <icon-left /> 返回选择已有字段
        </div>
        <a-form :model="createForm" layout="vertical" size="small" class="create-field-form">
          <a-form-item label="字段名称" required>
            <a-input v-model="createForm.name" placeholder="例如: 到期版本" :max-length="256" />
          </a-form-item>
          <a-form-item label="字段类型" required>
            <a-select v-model="createForm.fieldFormat" placeholder="选择字段类型">
              <a-option v-for="t in fieldTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="必填">
            <a-switch v-model="createForm.isRequired" />
          </a-form-item>
          <!-- list 类型选项管理 -->
          <template v-if="createForm.fieldFormat === 'list'">
            <a-form-item label="多值选择">
              <a-switch v-model="createForm.isMulti" />
              <div class="form-help">开启后允许选择多个选项值</div>
            </a-form-item>
            <a-form-item label="选项列表">
              <div class="options-list">
                <div v-for="(opt, idx) in createForm.options" :key="idx" class="option-row">
                  <a-input v-model="opt.value" placeholder="选项值" size="mini" style="flex:1" />
                  <a-checkbox v-model="opt.isDefault" size="small">默认</a-checkbox>
                  <a-button type="text" size="mini" status="danger" @click="createForm.options.splice(idx, 1)">
                    <icon-delete />
                  </a-button>
                </div>
                <a-button type="dashed" size="mini" long @click="createForm.options.push({ value: '', isDefault: false })">
                  <template #icon><icon-plus /></template>
                  添加选项
                </a-button>
              </div>
            </a-form-item>
          </template>
          <div class="create-form-info">
            <icon-info-circle /> 字段将以「项目级」方式创建（非全局），自动关联到「{{ attachTarget?.projectName }}」项目。
          </div>
        </a-form>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { IconSearch, IconRight, IconPlus, IconFolder, IconFile, IconLeft, IconDelete, IconInfoCircle } from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { customFieldApi } from '@/api'
import type { ProjectFieldsVO, FieldSummaryVO, CustomFieldDefinitionVO } from '@/api/types'

const loading = ref(false)
const keyword = ref('')
const groupBy = ref<'project' | 'field'>('project')
const expandedGroups = ref(new Set<string>())
const projectFieldsData = ref<ProjectFieldsVO[]>([])

// Inline editor state
const editingKey = ref<string | null>(null) // "projectId:fieldId"
const editForm = ref<{ isRequired: boolean | null; defaultValue: string | null; canBeEmpty: boolean | null }>({
  isRequired: null,
  defaultValue: null,
  canBeEmpty: null
})
const saving = ref(false)

// Attach dialog state
const attachDialogVisible = ref(false)
const attachTarget = ref<ProjectFieldsVO | null>(null)
const selectedFieldToAttach = ref<string | null>(null)
const availableFields = ref<CustomFieldDefinitionVO[]>([])
const loadingAvailable = ref(false)
const attaching = ref(false)
const attachMode = ref<'select' | 'create'>('select')

// Create field form state (for inline create in attach dialog)
const creatingField = ref(false)
const createForm = reactive({
  name: '',
  fieldFormat: 'string' as string,
  isRequired: false,
  isMulti: false,
  options: [] as Array<{ value: string; isDefault: boolean }>
})

const fieldTypeOptions = [
  { value: 'string', label: '文本(单行)' },
  { value: 'text', label: '文本(多行/Markdown)' },
  { value: 'int', label: '整数' },
  { value: 'float', label: '小数' },
  { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
  { value: 'bool', label: '布尔' },
  { value: 'list', label: '列表(枚举)' },
  { value: 'user', label: '用户' },
  { value: 'period', label: '时间周期' }
]

const fieldTypeLabels: Record<string, string> = {
  string: '文本(单行)',
  text: '文本(多行)',
  int: '整数',
  float: '小数',
  date: '日期',
  datetime: '日期时间',
  bool: '布尔',
  list: '列表(枚举)',
  user: '用户',
  period: '时间周期'
}

// === 汇总统计 ===
const totalFieldCount = computed(() => {
  return projectFieldsData.value.reduce((sum, p) => sum + p.fields.length, 0)
})

const overrideCount = computed(() => {
  let count = 0
  for (const p of projectFieldsData.value) {
    for (const f of p.fields) {
      if (f.hasOverride) count++
    }
  }
  return count
})

function getProjectOverrideCount(project: ProjectFieldsVO): number {
  return project.fields.filter(f => f.hasOverride).length
}

function getEffectiveRequired(field: FieldSummaryVO): boolean {
  if (field.projectIsRequired !== null) return field.projectIsRequired
  return field.isRequired
}

function truncateValue(value: string): string {
  return value.length > 10 ? value.substring(0, 10) + '...' : value
}

function formatType(format: string) {
  return fieldTypeLabels[format] || format
}

function toggleGroup(id: string) {
  if (expandedGroups.value.has(id)) {
    expandedGroups.value.delete(id)
  } else {
    expandedGroups.value.add(id)
  }
}

// === Inline editor ===
function isEditingField(projectId: string, fieldId: string): boolean {
  return editingKey.value === `${projectId}:${fieldId}`
}

function openFieldEditor(projectId: string, field: FieldSummaryVO) {
  const key = `${projectId}:${field.id}`
  if (editingKey.value === key) {
    closeFieldEditor()
    return
  }
  editingKey.value = key
  editForm.value = {
    isRequired: field.projectIsRequired ?? null,
    defaultValue: field.projectDefaultValue ?? null,
    canBeEmpty: field.projectCanBeEmpty ?? null
  }
}

function openFieldEditorByField(
  proj: { projectId: string; projectName: string; projectKey: string; hasOverride: boolean; projectIsRequired: boolean | null; projectDefaultValue: string | null; projectCanBeEmpty?: boolean | null },
  item: { fieldId: string; globalIsRequired: boolean }
) {
  const key = `${proj.projectId}:${item.fieldId}`
  if (editingKey.value === key) {
    closeFieldEditor()
    return
  }
  editingKey.value = key
  editForm.value = {
    isRequired: proj.projectIsRequired ?? null,
    defaultValue: proj.projectDefaultValue ?? null,
    canBeEmpty: proj.projectCanBeEmpty ?? null
  }
}

function closeFieldEditor() {
  editingKey.value = null
  editForm.value = { isRequired: null, defaultValue: null, canBeEmpty: null }
}

async function saveOverride(projectId: string, fieldId: string) {
  saving.value = true
  try {
    await customFieldApi.setFieldProjectOverride(projectId, fieldId, {
      isRequired: editForm.value.isRequired,
      defaultValue: editForm.value.defaultValue || null,
      canBeEmpty: editForm.value.canBeEmpty
    })
    Message.success('项目级覆盖已保存')
    closeFieldEditor()
    loadData()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function clearOverride(projectId: string, fieldId: string) {
  saving.value = true
  try {
    await customFieldApi.setFieldProjectOverride(projectId, fieldId, {
      isRequired: null,
      defaultValue: null,
      canBeEmpty: null
    })
    Message.success('已清除项目级覆盖，恢复全局设置')
    closeFieldEditor()
    loadData()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '清除失败')
  } finally {
    saving.value = false
  }
}

// === 按项目分组视图（过滤） ===
const filteredByProject = computed(() => {
  if (!keyword.value.trim()) return projectFieldsData.value
  const kw = keyword.value.trim().toLowerCase()
  return projectFieldsData.value.filter(p =>
    p.projectName.toLowerCase().includes(kw) || p.projectKey.toLowerCase().includes(kw)
  )
})

// === 按字段分组视图 ===
interface FieldGroupProjectItem {
  projectId: string
  projectName: string
  projectKey: string
  hasOverride: boolean
  projectIsRequired: boolean | null
  projectDefaultValue: string | null
  projectCanBeEmpty: boolean | null
}

interface FieldGroupItem {
  fieldId: string
  fieldName: string
  fieldFormat: string
  isForAll: boolean
  globalIsRequired: boolean
  projects: FieldGroupProjectItem[]
  overrideCount: number
}

const filteredByField = computed<FieldGroupItem[]>(() => {
  const fieldMap = new Map<string, FieldGroupItem>()

  for (const project of projectFieldsData.value) {
    for (const field of project.fields) {
      if (!fieldMap.has(field.id)) {
        fieldMap.set(field.id, {
          fieldId: field.id,
          fieldName: field.name,
          fieldFormat: field.fieldFormat,
          isForAll: field.isForAll,
          globalIsRequired: field.isRequired,
          projects: [],
          overrideCount: 0
        })
      }
      const item = fieldMap.get(field.id)!
      item.projects.push({
        projectId: project.projectId,
        projectName: project.projectName,
        projectKey: project.projectKey,
        hasOverride: field.hasOverride,
        projectIsRequired: field.projectIsRequired,
        projectDefaultValue: field.projectDefaultValue,
        projectCanBeEmpty: field.projectCanBeEmpty ?? null
      })
      if (field.hasOverride) item.overrideCount++
    }
  }

  let items = Array.from(fieldMap.values())
  if (keyword.value.trim()) {
    const kw = keyword.value.trim().toLowerCase()
    items = items.filter(f => f.fieldName.toLowerCase().includes(kw))
  }
  return items.sort((a, b) => a.fieldName.localeCompare(b.fieldName))
})

// === Data loading ===
async function loadData() {
  loading.value = true
  try {
    const res = await customFieldApi.fieldsInProjects()
    projectFieldsData.value = res.data || []
    // 默认展开第一个项目
    if (projectFieldsData.value.length > 0 && expandedGroups.value.size === 0) {
      expandedGroups.value.add(projectFieldsData.value[0].projectId)
    }
  } catch {
    projectFieldsData.value = []
  } finally {
    loading.value = false
  }
}

// === Attach/Detach ===
async function openAttachDialog(project: ProjectFieldsVO) {
  attachTarget.value = project
  selectedFieldToAttach.value = null
  attachMode.value = 'select'
  resetCreateForm()
  attachDialogVisible.value = true
  loadingAvailable.value = true
  try {
    const res = await customFieldApi.listAvailableForProject(project.projectId)
    availableFields.value = res.data || []
  } catch {
    availableFields.value = []
  } finally {
    loadingAvailable.value = false
  }
}

function closeAttachDialog() {
  attachDialogVisible.value = false
  attachMode.value = 'select'
  resetCreateForm()
}

function switchToCreateMode() {
  attachMode.value = 'create'
  resetCreateForm()
}

function switchToSelectMode() {
  attachMode.value = 'select'
}

function resetCreateForm() {
  createForm.name = ''
  createForm.fieldFormat = 'string'
  createForm.isRequired = false
  createForm.isMulti = false
  createForm.options = []
}

async function handleAttach() {
  if (!attachTarget.value || !selectedFieldToAttach.value) {
    Message.warning('请选择要添加的字段')
    return
  }
  attaching.value = true
  try {
    await customFieldApi.attachToProject(attachTarget.value.projectId, selectedFieldToAttach.value)
    Message.success('添加成功')
    attachDialogVisible.value = false
    loadData()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加失败')
  } finally {
    attaching.value = false
  }
}

async function handleCreateAndAttach() {
  if (!attachTarget.value) return
  if (!createForm.name.trim()) {
    Message.warning('请输入字段名称')
    return
  }
  if (!createForm.fieldFormat) {
    Message.warning('请选择字段类型')
    return
  }
  if (createForm.fieldFormat === 'list') {
    const validOptions = createForm.options.filter(o => o.value.trim())
    if (validOptions.length === 0) {
      Message.warning('列表类型字段至少需要一个选项')
      return
    }
  }

  creatingField.value = true
  try {
    // Create field with isForAll=false and pre-select the current project
    const createPayload: any = {
      name: createForm.name.trim(),
      fieldFormat: createForm.fieldFormat,
      isRequired: createForm.isRequired,
      isForAll: false,
      projectIds: [attachTarget.value.projectId]
    }
    if (createForm.fieldFormat === 'list') {
      createPayload.isMulti = createForm.isMulti
      createPayload.options = createForm.options
        .filter(o => o.value.trim())
        .map(o => ({ value: o.value.trim(), isDefault: o.isDefault }))
    }

    await customFieldApi.create(createPayload)
    Message.success('字段创建成功并已添加到项目')
    attachDialogVisible.value = false
    attachMode.value = 'select'
    resetCreateForm()
    loadData()
    emit('field-created')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    creatingField.value = false
  }
}

function handleDetach(projectId: string, fieldId: string, fieldName: string, projectName: string) {
  Modal.warning({
    title: '确认移除',
    content: `确定要从项目「${projectName}」中移除字段「${fieldName}」？移除后该项目将不再使用此字段。`,
    okText: '移除',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      try {
        await customFieldApi.detachFromProject(projectId, fieldId)
        Message.success('已移除')
        loadData()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '移除失败')
      }
    }
  })
}

const emit = defineEmits<{
  (e: 'field-created'): void
}>()

onMounted(loadData)

defineExpose({ refresh: loadData })
</script>

<style scoped>
.fields-in-projects {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fip-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.fip-summary {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-text {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

.summary-override {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--color-warning-light-1, rgba(210, 153, 34, 0.15));
  color: var(--tf-warning);
  font-weight: 500;
}

.fip-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 0;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}
.empty-state svg {
  font-size: 32px;
  opacity: 0.4;
}

.project-groups,
.field-groups {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.project-group,
.field-group {
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  overflow: hidden;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--tf-bg-surface);
  cursor: pointer;
  transition: background 100ms;
}
.group-header:hover {
  background: var(--tf-bg-hover);
}

.expand-icon {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  transition: transform 150ms;
  flex-shrink: 0;
}
.expand-icon.expanded {
  transform: rotate(90deg);
}

.project-name {
  font-weight: 500;
  font-size: 13px;
  color: var(--tf-text-primary);
}

.project-key {
  font-size: 11px;
  color: var(--tf-text-quaternary);
  font-family: monospace;
}

.field-name {
  font-weight: 500;
  font-size: 13px;
  color: var(--tf-text-primary);
}

.field-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-left: auto;
}

.override-badge {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--color-warning-light-1, rgba(210, 153, 34, 0.15));
  color: var(--tf-warning);
  font-weight: 500;
}

.group-fields {
  padding: 4px 12px 8px 32px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-row {
  display: flex;
  flex-direction: column;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 100ms;
}
.field-row:hover {
  background: var(--tf-bg-hover);
}
.field-row--editing {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
}

.field-row-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-type-tag {
  font-size: 11px;
}

.badge {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 500;
}
.badge-global {
  background: var(--color-primary-light-1, rgba(88, 166, 255, 0.15));
  color: var(--tf-accent);
}
.badge-project {
  background: var(--color-success-light-1, rgba(63, 185, 80, 0.15));
  color: var(--tf-success);
}
.badge-required {
  background: var(--color-warning-light-1, rgba(210, 153, 34, 0.15));
  color: var(--tf-warning);
}
.badge-not-required {
  background: var(--color-fill-2, rgba(128, 128, 128, 0.1));
  color: var(--tf-text-secondary);
}
.badge-overridden {
  border: 1px dashed var(--tf-warning);
}
.badge-default-override {
  background: var(--color-primary-light-1, rgba(88, 166, 255, 0.1));
  color: var(--tf-accent);
  border: 1px dashed var(--tf-accent);
}
.badge-multi {
  background: var(--color-purple-light-1, rgba(156, 39, 176, 0.15));
  color: #b388ff;
}

.field-actions {
  margin-left: auto;
}

/* Inline override editor */
.field-override-editor {
  margin-top: 8px;
  padding: 12px;
  background: var(--tf-bg-elevated, var(--tf-bg-surface));
  border-radius: 4px;
  border: 1px solid var(--tf-border);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.editor-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.editor-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor-label {
  font-size: 12px;
  color: var(--tf-text-secondary);
  width: 56px;
  flex-shrink: 0;
}

.editor-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.editor-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.attach-row {
  padding: 6px 8px;
}

.info-note {
  padding: 8px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  font-style: italic;
}

.no-available-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 0;
}

.attach-footer-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.create-field-link {
  color: var(--tf-accent);
  cursor: pointer;
  font-weight: 500;
  text-decoration: none;
}
.create-field-link:hover {
  text-decoration: underline;
}

.create-mode-back {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--tf-accent);
  cursor: pointer;
  margin-bottom: 16px;
  font-weight: 500;
}
.create-mode-back:hover {
  text-decoration: underline;
}

.create-field-form {
  margin-top: 0;
}

.create-field-form .form-help {
  margin-top: 4px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.create-field-form .options-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.create-field-form .option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.create-form-info {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  background: var(--color-primary-light-1, rgba(88, 166, 255, 0.08));
  border-radius: 4px;
  font-size: 12px;
  color: var(--tf-text-secondary);
  line-height: 1.5;
}
.create-form-info svg {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--tf-accent);
}
</style>
