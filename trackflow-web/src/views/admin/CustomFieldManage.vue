<template>
  <div class="cf-manage">
    <div class="cf-header">
      <h2 class="page-title">自定义字段管理</h2>
      <a-button type="primary" size="small" @click="openCreate">
        <template #icon><icon-plus /></template>
        创建自定义字段
      </a-button>
    </div>

    <!-- 字段列表 -->
    <div class="cf-table">
      <a-table
        :data="fieldList"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        size="small"
        @page-change="onPageChange"
      >
        <template #columns>
          <a-table-column title="字段名称" data-index="name" />
          <a-table-column title="类型" data-index="fieldFormat" :width="100">
            <template #cell="{ record }">
              <a-tag size="small">{{ formatTypeLabel(record.fieldFormat) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="必填" data-index="isRequired" :width="60" align="center">
            <template #cell="{ record }">
              <icon-check v-if="record.isRequired" style="color: var(--tf-success)" />
            </template>
          </a-table-column>
          <a-table-column title="全局" data-index="isForAll" :width="60" align="center">
            <template #cell="{ record }">
              <icon-check v-if="record.isForAll" style="color: var(--tf-accent)" />
            </template>
          </a-table-column>
          <a-table-column title="适用项目" :width="120">
            <template #cell="{ record }">
              <span v-if="record.isForAll" class="text-muted">所有项目</span>
              <span v-else class="text-muted">{{ (record.projectIds || []).length }} 个项目</span>
            </template>
          </a-table-column>
          <a-table-column title="适用类型" :width="140">
            <template #cell="{ record }">
              <span v-if="!record.issueTypes || record.issueTypes.length === 0" class="text-muted">所有类型</span>
              <span v-else>{{ record.issueTypes.map(t => localizeIssueType(t)).join(', ') }}</span>
            </template>
          </a-table-column>
          <a-table-column title="列表可见" :width="80" align="center">
            <template #cell="{ record }">
              <icon-eye v-if="!record.isHiddenInList" style="color: var(--tf-success)" />
              <icon-eye-invisible v-else style="color: var(--tf-text-quaternary)" />
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="120" align="center">
            <template #cell="{ record }">
              <a-button type="text" size="mini" @click="openEdit(record)">编辑</a-button>
              <a-button type="text" size="mini" status="danger" @click="confirmDelete(record)">删除</a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </div>

    <!-- 创建/编辑抽屉 -->
    <a-drawer
      :visible="drawerVisible"
      :title="editingId ? '编辑自定义字段' : '创建自定义字段'"
      :width="480"
      @cancel="drawerVisible = false"
      @ok="handleSave"
      :ok-loading="saving"
      unmount-on-close
    >
      <a-form :model="form" layout="vertical" size="small">
        <a-form-item label="字段名称" required>
          <a-input v-model="form.name" placeholder="例如: 到期版本" :max-length="256" />
        </a-form-item>

        <a-form-item label="字段类型" required>
          <a-select v-model="form.fieldFormat" :disabled="!!editingId" placeholder="选择字段类型">
            <a-option v-for="t in fieldTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-option>
          </a-select>
        </a-form-item>

        <a-form-item label="必填">
          <a-switch v-model="form.isRequired" />
        </a-form-item>

        <a-form-item label="全局可用">
          <a-switch v-model="form.isForAll" />
          <div class="form-help">开启后所有项目均可使用此字段</div>
        </a-form-item>

        <a-form-item label="隐藏于工单列表">
          <a-switch v-model="form.isHiddenInList" />
          <div class="form-help">开启后，此字段默认不出现在工单列表的列选择器中（用户仍可通过个人设置手动添加）</div>
        </a-form-item>

        <a-form-item label="默认值">
          <a-input v-model="form.defaultValue" placeholder="可选" />
        </a-form-item>

        <!-- string 类型额外配置 -->
        <template v-if="form.fieldFormat === 'string'">
          <a-form-item label="最小长度">
            <a-input-number v-model="form.minLength" :min="0" />
          </a-form-item>
          <a-form-item label="最大长度">
            <a-input-number v-model="form.maxLength" :min="0" />
          </a-form-item>
          <a-form-item label="正则验证">
            <a-input v-model="form.regexp" placeholder="可选正则表达式" />
          </a-form-item>
        </template>

        <!-- text 类型额外配置 -->
        <template v-if="form.fieldFormat === 'text'">
          <a-form-item label="最大长度">
            <a-input-number v-model="form.maxLength" :min="0" placeholder="0 表示不限制" />
            <div class="form-help">支持 Markdown 格式的多行文本</div>
          </a-form-item>
        </template>

        <!-- list 类型选项管理 -->
        <template v-if="form.fieldFormat === 'list'">
          <a-form-item label="多值选择">
            <a-switch v-model="form.isMulti" :disabled="isMultiDisabled" />
            <div class="form-help">
              <template v-if="isMultiDisabled">
                该字段已被工单使用，无法切换单选/多选模式
              </template>
              <template v-else>
                开启后允许选择多个选项值（如影响版本、标签等）
              </template>
            </div>
          </a-form-item>

          <!-- 值集来源选择（仅创建模式显示） -->
          <a-form-item v-if="!editingId" label="值集来源">
            <a-radio-group v-model="valueSetSource" type="button" size="small">
              <a-radio value="new">新建值集</a-radio>
              <a-radio value="copy">从已有字段复制</a-radio>
            </a-radio-group>
          </a-form-item>

          <!-- 从已有字段复制：选择源字段 -->
          <a-form-item v-if="valueSetSource === 'copy' && !editingId" label="选择源字段">
            <a-select
              v-model="form.copyOptionsFromFieldId"
              placeholder="选择一个枚举类型字段"
              allow-clear
              @change="onSourceFieldChange"
            >
              <a-option v-for="f in enumFieldList" :key="f.id" :value="f.id">
                {{ f.name }}（{{ (f.options || []).filter(o => !o.isArchived).length }} 个选项）
              </a-option>
            </a-select>
            <div class="form-help">选择后将复制该字段的所有选项作为独立副本，后续修改互不影响</div>
          </a-form-item>

          <!-- 编辑模式下的"从其他字段复制值"操作 -->
          <a-form-item v-if="editingId" label="从其他字段复制值">
            <div class="copy-from-row">
              <a-select
                v-model="copyFromFieldId"
                placeholder="选择源字段追加选项"
                allow-clear
                style="flex: 1"
              >
                <a-option v-for="f in enumFieldList.filter(x => x.id !== editingId)" :key="f.id" :value="f.id">
                  {{ f.name }}（{{ (f.options || []).filter(o => !o.isArchived).length }} 个选项）
                </a-option>
              </a-select>
              <a-button type="outline" size="small" :disabled="!copyFromFieldId" @click="handleCopyFrom">
                复制
              </a-button>
            </div>
            <div class="form-help">将源字段的选项追加到当前选项列表中（跳过同名选项）</div>
          </a-form-item>

          <!-- 选项预览（从字段复制后） -->
          <a-form-item v-if="valueSetSource === 'copy' && !editingId && previewOptions.length > 0" label="选项预览">
            <div class="options-preview">
              <a-tag v-for="opt in previewOptions" :key="opt.value" size="small" :color="opt.color || undefined">
                {{ opt.value }}
              </a-tag>
            </div>
          </a-form-item>

          <a-form-item :label="valueSetSource === 'copy' && !editingId ? '调整选项（可修改复制后的选项）' : '选项列表'">
            <div class="options-list">
              <div v-for="(opt, idx) in form.options" :key="idx" class="option-row">
                <a-input v-model="opt.value" placeholder="选项值" size="mini" style="flex:1" />
                <a-trigger trigger="click" :popup-translate="[0, 4]">
                  <span
                    class="color-swatch"
                    :style="{ background: opt.color || 'transparent', border: opt.color ? 'none' : '1px dashed var(--tf-border)' }"
                    title="设置颜色"
                  ></span>
                  <template #content>
                    <div class="color-palette">
                      <span
                        v-for="c in presetColors"
                        :key="c"
                        class="color-palette-item"
                        :class="{ active: opt.color === c }"
                        :style="{ background: c }"
                        @click="opt.color = c"
                      ></span>
                      <span
                        class="color-palette-item color-palette-clear"
                        :class="{ active: !opt.color }"
                        @click="opt.color = undefined"
                        title="无颜色"
                      >✕</span>
                    </div>
                  </template>
                </a-trigger>
                <a-checkbox v-model="opt.isDefault" size="small">默认</a-checkbox>
                <a-button type="text" size="mini" status="danger" @click="form.options.splice(idx, 1)">
                  <icon-delete />
                </a-button>
              </div>
              <a-button type="dashed" size="mini" long @click="form.options.push({ value: '', isDefault: false })">
                <template #icon><icon-plus /></template>
                添加选项
              </a-button>
            </div>
          </a-form-item>
        </template>

        <!-- 项目关联（非全局时） -->
        <a-form-item v-if="!form.isForAll" label="关联项目">
          <a-checkbox-group v-model="form.projectIds">
            <a-checkbox v-for="p in projectList" :key="p.id" :value="p.id">{{ p.name }}</a-checkbox>
          </a-checkbox-group>
        </a-form-item>

        <!-- Issue 类型关联 -->
        <a-form-item label="适用 Issue 类型">
          <a-checkbox-group v-model="form.issueTypes">
            <a-checkbox v-for="t in issueTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-checkbox>
          </a-checkbox-group>
          <div class="form-help">不选则适用所有类型</div>
        </a-form-item>
      </a-form>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { IconPlus, IconDelete, IconCheck, IconEye, IconEyeInvisible } from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { customFieldApi, projectApi, workflowApi } from '@/api'
import type { CustomFieldDefinitionVO } from '@/api/types'
import { localizeIssueType } from '@/utils/fieldLabels'

const fieldList = ref<CustomFieldDefinitionVO[]>([])
const loading = ref(false)
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })
const projectList = ref<any[]>([])
const issueTypeOptions = ref<Array<{ value: string; label: string }>>([])

// Drawer state
const drawerVisible = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
/** 编辑模式下字段已有数据时禁止切换 isMulti */
const isMultiDisabled = ref(false)

// Value set source (for list type create mode)
const valueSetSource = ref<'new' | 'copy'>('new')
const enumFieldList = ref<CustomFieldDefinitionVO[]>([])
const previewOptions = ref<Array<{ value: string; color?: string }>>([])
const copyFromFieldId = ref<string | null>(null)

const form = reactive({
  name: '',
  fieldFormat: 'string' as string,
  isRequired: false,
  isForAll: false,
  isMulti: false,
  isHiddenInList: false,
  defaultValue: '',
  minLength: 0,
  maxLength: 0,
  regexp: '',
  options: [] as Array<{ id?: string; value: string; isDefault: boolean; color?: string }>,
  projectIds: [] as string[],
  issueTypes: [] as string[],
  copyOptionsFromFieldId: undefined as string | undefined
})

/** 预定义颜色方案（14 种） */
const presetColors = [
  '#4CAF50', '#2196F3', '#9C27B0', '#FF9800',
  '#F44336', '#00BCD4', '#607D8B', '#E91E63',
  '#8BC34A', '#3F51B5', '#FF5722', '#009688',
  '#795548', '#FFC107'
]

const fieldTypeOptions = [
  { value: 'string', label: '文本(单行)' },
  { value: 'text', label: '文本(多行/Markdown)' },
  { value: 'int', label: '整数' },
  { value: 'float', label: '小数' },
  { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
  { value: 'bool', label: '布尔' },
  { value: 'list', label: '列表(枚举)' },
  { value: 'user', label: '用户' }
]

function formatTypeLabel(format: string) {
  return fieldTypeOptions.find(t => t.value === format)?.label || format
}

async function loadList() {
  loading.value = true
  try {
    const res = await customFieldApi.list({ page: pagination.current, pageSize: pagination.pageSize })
    fieldList.value = res.data?.list || []
    pagination.total = res.data?.pagination?.total || 0
  } catch {
    fieldList.value = []
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    projectList.value = res.data?.list || []
  } catch {
    projectList.value = []
  }
}

async function loadIssueTypes() {
  try {
    const res = await workflowApi.listIssueTypes()
    const types = res.data || []
    issueTypeOptions.value = types.map(t => ({ value: t, label: localizeIssueType(t) }))
  } catch {
    // Fallback to basic types if API fails
    issueTypeOptions.value = [
      { value: 'Bug', label: '缺陷' },
      { value: 'Task', label: '任务' },
      { value: 'Feature', label: '需求' },
      { value: 'Epic', label: '史诗' },
    ]
  }
}

function onPageChange(page: number) {
  pagination.current = page
  loadList()
}

function resetForm() {
  form.name = ''
  form.fieldFormat = 'string'
  form.isRequired = false
  form.isForAll = false
  form.isMulti = false
  form.isHiddenInList = false
  form.defaultValue = ''
  form.minLength = 0
  form.maxLength = 0
  form.regexp = ''
  form.options = []
  form.projectIds = []
  form.issueTypes = []
  form.copyOptionsFromFieldId = undefined
  valueSetSource.value = 'new'
  previewOptions.value = []
  copyFromFieldId.value = null
}

async function loadEnumFields() {
  try {
    const res = await customFieldApi.listEnumFields()
    enumFieldList.value = res.data || []
  } catch {
    enumFieldList.value = []
  }
}

function onSourceFieldChange(fieldId: string | null) {
  if (!fieldId) {
    previewOptions.value = []
    form.options = []
    form.copyOptionsFromFieldId = undefined
    return
  }
  form.copyOptionsFromFieldId = fieldId
  const sourceField = enumFieldList.value.find(f => f.id === fieldId)
  if (sourceField && sourceField.options) {
    const activeOptions = sourceField.options.filter(o => !o.isArchived)
    previewOptions.value = activeOptions.map(o => ({ value: o.value, color: o.color }))
    // 预填充到 form.options 以便用户可以在保存前调整
    form.options = activeOptions.map(o => ({
      value: o.value,
      isDefault: o.isDefault || false,
      color: o.color || undefined
    }))
  }
}

function handleCopyFrom() {
  if (!copyFromFieldId.value) return
  const sourceField = enumFieldList.value.find(f => f.id === copyFromFieldId.value)
  if (!sourceField || !sourceField.options) return

  const existingValues = new Set(form.options.map(o => o.value))
  const activeOptions = sourceField.options.filter(o => !o.isArchived)
  let addedCount = 0
  for (const opt of activeOptions) {
    if (!existingValues.has(opt.value)) {
      form.options.push({
        value: opt.value,
        isDefault: false,
        color: opt.color || undefined
      })
      addedCount++
    }
  }
  if (addedCount > 0) {
    Message.success(`已追加 ${addedCount} 个选项`)
  } else {
    Message.info('所有选项已存在，无需追加')
  }
  copyFromFieldId.value = null
}

function openCreate() {
  editingId.value = null
  isMultiDisabled.value = false
  resetForm()
  loadEnumFields()
  drawerVisible.value = true
}

function openEdit(record: CustomFieldDefinitionVO) {
  editingId.value = record.id
  form.name = record.name
  form.fieldFormat = record.fieldFormat
  form.isRequired = record.isRequired
  form.isForAll = record.isForAll
  form.isMulti = record.isMulti || false
  form.isHiddenInList = record.isHiddenInList || false
  form.defaultValue = record.defaultValue || ''
  form.minLength = record.minLength
  form.maxLength = record.maxLength
  form.regexp = record.regexp || ''
  form.options = (record.options || [])
    .filter(o => !o.isArchived)
    .map(o => ({ id: o.id, value: o.value, isDefault: o.isDefault, color: o.color || undefined }))
  form.projectIds = record.projectIds || []
  form.issueTypes = record.issueTypes || []
  form.copyOptionsFromFieldId = undefined
  copyFromFieldId.value = null
  // 检查字段是否有数据——有则禁止切换 isMulti
  isMultiDisabled.value = false
  if (record.fieldFormat === 'list') {
    loadEnumFields()
    customFieldApi.getUsage(record.id).then(res => {
      if (res.data && res.data.valueCount > 0) {
        isMultiDisabled.value = true
      }
    }).catch(() => { /* 查询失败时允许操作，后端兜底 */ })
  }
  drawerVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    Message.warning('请输入字段名称')
    return
  }
  if (!form.fieldFormat) {
    Message.warning('请选择字段类型')
    return
  }

  saving.value = true
  try {
    if (editingId.value) {
      await customFieldApi.update(editingId.value, {
        name: form.name,
        isRequired: form.isRequired,
        isForAll: form.isForAll,
        isHiddenInList: form.isHiddenInList,
        defaultValue: form.defaultValue || undefined,
        minLength: form.minLength,
        maxLength: form.maxLength,
        regexp: form.regexp || undefined,
        isMulti: form.fieldFormat === 'list' ? form.isMulti : undefined,
        options: form.fieldFormat === 'list'
          ? form.options.map(o => ({ id: o.id, value: o.value, isDefault: o.isDefault, color: o.color || undefined }))
          : undefined,
        projectIds: form.isForAll ? [] : form.projectIds,
        issueTypes: form.issueTypes
      })
      Message.success('更新成功')
    } else {
      await customFieldApi.create({
        name: form.name,
        fieldFormat: form.fieldFormat,
        isRequired: form.isRequired,
        isForAll: form.isForAll,
        isHiddenInList: form.isHiddenInList,
        defaultValue: form.defaultValue || undefined,
        minLength: form.minLength,
        maxLength: form.maxLength,
        regexp: form.regexp || undefined,
        isMulti: form.fieldFormat === 'list' ? form.isMulti : undefined,
        options: form.fieldFormat === 'list'
          ? form.options.map(o => ({ value: o.value, isDefault: o.isDefault, color: o.color || undefined }))
          : undefined,
        projectIds: form.isForAll ? [] : form.projectIds,
        issueTypes: form.issueTypes
      })
      Message.success('创建成功')
    }
    drawerVisible.value = false
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function confirmDelete(record: CustomFieldDefinitionVO) {
  try {
    const res = await customFieldApi.getUsage(record.id)
    const usage = res.data
    if (!usage) return

    if (usage.issueCount === 0) {
      // 无引用——简单确认
      Modal.warning({
        title: '确认删除',
        content: `确定要删除自定义字段「${record.name}」？此操作不可撤销。`,
        okText: '删除字段',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => handleDelete(record.id)
      })
    } else {
      // 有工单引用——危险确认
      Modal.error({
        title: '⚠️ 删除将导致数据丢失',
        content: `字段「${record.name}」当前被 ${usage.issueCount} 个工单使用（共 ${usage.valueCount} 条值记录）。删除后这些数据将永久丢失且不可恢复。`,
        okText: `确认删除（影响 ${usage.issueCount} 个工单）`,
        cancelText: '取消',
        hideCancel: false,
        onOk: () => handleDelete(record.id)
      })
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取使用情况失败')
  }
}

async function handleDelete(id: string) {
  try {
    await customFieldApi.delete(id, true)
    Message.success('删除成功')
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

onMounted(() => {
  loadList()
  loadProjects()
  loadIssueTypes()
})
</script>

<style scoped>
.cf-manage {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.cf-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.cf-table {
  background: var(--tf-bg-surface);
  border-radius: 6px;
  border: 1px solid var(--tf-border);
}

.text-muted {
  color: var(--tf-text-tertiary);
  font-size: 12px;
}

.form-help {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-swatch {
  display: inline-block;
  width: 20px;
  height: 20px;
  border-radius: 3px;
  cursor: pointer;
  flex-shrink: 0;
  transition: transform 150ms;
}
.color-swatch:hover {
  transform: scale(1.15);
}

.color-palette {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
  padding: 8px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.color-palette-item {
  width: 24px;
  height: 24px;
  border-radius: 3px;
  cursor: pointer;
  transition: transform 100ms;
  position: relative;
}
.color-palette-item:hover {
  transform: scale(1.2);
}
.color-palette-item.active::after {
  content: '✓';
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.3);
}
.color-palette-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  border: 1px dashed var(--tf-border);
}
.color-palette-clear.active {
  border-color: var(--tf-accent);
  color: var(--tf-accent);
}

.copy-from-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.options-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  background: var(--tf-bg-body);
  border-radius: 4px;
  border: 1px solid var(--tf-border);
}
</style>
