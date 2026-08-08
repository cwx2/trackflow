<template>
  <div class="general-settings">
    <div class="settings-hint">
      <p>配置看板的基本信息、访问权限和过滤行为。</p>
    </div>

    <!-- 看板名称 -->
    <div class="section">
      <div class="section-title">看板名称</div>
      <div class="section-desc">自定义看板的显示名称。留空时默认使用项目名称。</div>
      <a-input
        v-model="editableName"
        :placeholder="defaultBoardName"
        :max-length="100"
        allow-clear
        @input="emitName"
      />
    </div>

    <!-- 关联项目（跨项目看板） -->
    <div class="section">
      <div class="section-title">关联项目</div>
      <div class="section-desc">
        选择此看板关联的项目。默认只关联当前项目，选择多个项目后看板将展示所有关联项目的工单。
      </div>
      <a-select
        :model-value="editableLinkedProjectIds"
        multiple
        placeholder="仅当前项目（单击添加更多项目）"
        allow-clear
        :max-tag-count="3"
        style="width: 100%"
        :loading="false"
        @change="onLinkedProjectsChange"
      >
        <a-option
          v-for="project in filteredAvailableProjects"
          :key="project.id"
          :value="project.id"
        >
          <span class="project-option-key">{{ project.key }}</span>
          <span class="project-option-name">{{ project.name }}</span>
        </a-option>
      </a-select>
      <div v-if="editableLinkedProjectIds.length > 0" class="multi-project-hint">
        <icon-info-circle style="color: var(--tf-accent); margin-right: 4px;" />
        <span>
          跨项目看板将合并展示来自
          <strong>{{ editableLinkedProjectIds.length + 1 }}</strong>
          个项目的工单（当前项目 + {{ editableLinkedProjectIds.length }} 个关联项目）。
          卡片上会显示项目 Key 以区分来源。
        </span>
      </div>
    </div>

    <!-- Board Behavior -->
    <div class="section">
      <div class="section-title">Board Behavior</div>
      <div class="section-desc">配置看板显示哪些工单。持久化生效，所有团队成员看到相同结果。</div>

      <div class="behavior-options">
        <label
          class="behavior-option"
          :class="{ 'behavior-option--active': editableFilterMode === 'all' }"
          @click="setFilterMode('all')"
        >
          <a-radio :model-value="editableFilterMode === 'all'" @change="setFilterMode('all')" />
          <div class="behavior-option-content">
            <span class="behavior-option-label">显示所有工单</span>
            <span class="behavior-option-desc">项目中所有未删除的工单都显示在看板上</span>
          </div>
        </label>
        <label
          class="behavior-option"
          :class="{ 'behavior-option--active': editableFilterMode === 'active_sprint' }"
          @click="setFilterMode('active_sprint')"
        >
          <a-radio :model-value="editableFilterMode === 'active_sprint'" @change="setFilterMode('active_sprint')" />
          <div class="behavior-option-content">
            <span class="behavior-option-label">仅当前 Sprint 工单</span>
            <span class="behavior-option-desc">只显示属于当前活跃 Sprint 的工单，减少信息过载</span>
          </div>
        </label>
        <label
          class="behavior-option"
          :class="{ 'behavior-option--active': editableFilterMode === 'query' }"
          @click="setFilterMode('query')"
        >
          <a-radio :model-value="editableFilterMode === 'query'" @change="setFilterMode('query')" />
          <div class="behavior-option-content">
            <span class="behavior-option-label">按查询过滤</span>
            <span class="behavior-option-desc">通过自定义条件过滤看板显示的工单范围（支持负责人、优先级、标签等）</span>
          </div>
        </label>
      </div>

      <!-- Sprint 模式无活跃迭代警告 -->
      <div v-if="editableFilterMode === 'active_sprint' && !props.hasActiveSprint" class="sprint-warning">
        <span class="sprint-warning-icon">⚠️</span>
        <div class="sprint-warning-content">
          <span class="sprint-warning-title">当前项目无活跃迭代</span>
          <span class="sprint-warning-desc">选择此模式后看板将为空。请先在「迭代」页面激活一个 Sprint。</span>
        </div>
      </div>

      <!-- 查询过滤条件构建器 -->
      <div v-if="editableFilterMode === 'query'" class="query-filter-builder">
        <div class="query-builder-header">
          <span class="query-builder-title">过滤条件</span>
          <a-button size="mini" type="text" @click="addFilterRow">
            <template #icon><icon-plus /></template>
            添加条件
          </a-button>
        </div>
        <div v-if="filterRows.length === 0" class="query-builder-empty">
          <span>暂无过滤条件，点击"添加条件"开始配置</span>
        </div>
        <div v-else class="query-builder-rows">
          <div v-for="(row, index) in filterRows" :key="row._key" class="filter-row">
            <a-select
              v-model="row.field"
              placeholder="选择字段"
              size="small"
              style="width: 120px"
              @change="onFieldChange(index)"
            >
              <a-option v-for="f in availableFields" :key="f.value" :value="f.value">{{ f.label }}</a-option>
            </a-select>
            <a-select
              v-model="row.operator"
              placeholder="操作符"
              size="small"
              style="width: 100px"
            >
              <a-option v-for="op in getOperatorsForField(row.field)" :key="op.value" :value="op.value">{{ op.label }}</a-option>
            </a-select>
            <a-input
              v-if="!isEmptyOperator(row.operator)"
              v-model="row.value"
              placeholder="值（多个用逗号分隔）"
              size="small"
              style="flex: 1; min-width: 120px"
            />
            <span v-else class="filter-empty-placeholder" />
            <a-button
              size="mini"
              type="text"
              status="danger"
              @click="removeFilterRow(index)"
            >
              <template #icon><icon-delete /></template>
            </a-button>
          </div>
        </div>
        <div v-if="queryValidationError" class="query-validation-error">
          <icon-exclamation-circle-fill /> {{ queryValidationError }}
        </div>
        <div class="query-builder-hint">
          <span>支持的字段：负责人 ID、状态代码、优先级、类型、Sprint ID 等。多个值用英文逗号分隔。</span>
        </div>
      </div>

      <!-- 已完成工单保留天数 -->
      <div class="retention-config">
        <div class="retention-label">已完成工单保留天数</div>
        <div class="retention-desc">超过指定天数的已完成工单将从看板隐藏。留空表示不限制。</div>
        <a-input-number
          v-model="editableDoneRetentionDays"
          :min="1"
          :max="365"
          placeholder="不限制"
          style="width: 160px"
          allow-clear
          @change="emitDoneRetentionDays"
        >
          <template #suffix>天</template>
        </a-input-number>
      </div>
    </div>

    <!-- 列标识字段 -->
    <div class="section">
      <div class="section-title">列标识字段</div>
      <div class="section-desc">选择看板列使用哪个字段来标识。修改后看板将按新字段的值分列。</div>
      <a-select
        :model-value="editableColumnField"
        placeholder="选择列标识字段"
        style="width: 220px"
        @change="onColumnFieldChange"
      >
        <a-option value="status">状态（Status）</a-option>
        <a-option value="priority">优先级（Priority）</a-option>
      </a-select>
      <div v-if="editableColumnField === 'priority'" class="column-field-hint">
        <span>⚠️ 优先级模式下拖拽卡片将变更优先级，而非触发状态转换。</span>
      </div>
    </div>

    <!-- 查看权限 -->
    <div class="section">
      <div class="section-title">谁可以查看看板</div>
      <div class="section-desc">选择哪些项目角色可以查看看板内容。</div>
      <div class="role-list">
        <label
          v-for="role in availableRoles"
          :key="role.code"
          class="role-item"
          :class="{ 'role-item--checked': viewRolesSet.has(role.code) }"
        >
          <a-checkbox
            :model-value="viewRolesSet.has(role.code)"
            @change="(val: boolean) => toggleViewRole(role.code, val)"
          />
          <span class="role-label">{{ role.label }}</span>
          <span class="role-desc">{{ role.desc }}</span>
        </label>
      </div>
    </div>

    <!-- 编辑权限 -->
    <div class="section">
      <div class="section-title">谁可以编辑看板设置</div>
      <div class="section-desc">选择哪些项目角色可以修改看板配置（列设置、卡片、泳道等）。</div>
      <div class="role-list">
        <label
          v-for="role in availableRoles"
          :key="role.code"
          class="role-item"
          :class="{ 'role-item--checked': editRolesSet.has(role.code) }"
        >
          <a-checkbox
            :model-value="editRolesSet.has(role.code)"
            @change="(val: boolean) => toggleEditRole(role.code, val)"
          />
          <span class="role-label">{{ role.label }}</span>
          <span class="role-desc">{{ role.desc }}</span>
        </label>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

interface RoleOption {
  code: string
  label: string
  desc: string
}

interface FilterRow {
  _key: number
  field: string
  operator: string
  value: string
}

let filterRowKeySeq = 0

interface FieldOption {
  value: string
  label: string
}

interface OperatorOption {
  value: string
  label: string
}

interface ProjectOption {
  id: string
  name: string
  key: string
}

const props = defineProps<{
  name: string
  canViewRoles: string[]
  canEditRoles: string[]
  projectName: string
  projectId: string
  filterMode: string
  filterQuery: string | null
  doneRetentionDays: number | null
  columnField: string
  hasActiveSprint: boolean
  linkedProjectIds: string[]
  availableProjects: ProjectOption[]
}>()

const emit = defineEmits<{
  'update:name': [value: string]
  'update:canViewRoles': [roles: string[]]
  'update:canEditRoles': [roles: string[]]
  'update:filterMode': [value: string]
  'update:filterQuery': [value: string | null]
  'update:doneRetentionDays': [value: number | null]
  'update:columnField': [value: string]
  'update:linkedProjectIds': [value: string[]]
}>()

const availableRoles: RoleOption[] = [
  { code: 'project_admin', label: '项目管理员', desc: '项目的管理者' },
  { code: 'tech_lead', label: '技术负责人', desc: '技术方向决策者' },
  { code: 'developer', label: '开发人员', desc: '执行开发任务' },
  { code: 'product_manager', label: '产品经理', desc: '需求分析与产品规划' },
  { code: 'tester', label: '测试人员', desc: '质量保障' },
  { code: 'observer', label: '观察者', desc: '只读访问' }
]

// ===== 查询过滤器字段定义 =====
const availableFields: FieldOption[] = [
  { value: 'status', label: '状态' },
  { value: 'priority', label: '优先级' },
  { value: 'assignee', label: '负责人' },
  { value: 'reporter', label: '报告人' },
  { value: 'type', label: '类型' },
  { value: 'sprint', label: 'Sprint' },
  { value: 'keyword', label: '关键词' }
]

const operatorsByType: Record<string, OperatorOption[]> = {
  default: [
    { value: 'eq', label: '等于' },
    { value: 'neq', label: '不等于' },
    { value: 'in', label: '包含' },
    { value: 'not_in', label: '不包含' },
    { value: 'is_empty', label: '为空' },
    { value: 'is_not_empty', label: '不为空' }
  ],
  status: [
    { value: 'in', label: '包含' },
    { value: 'not_in', label: '不包含' },
    { value: 'open', label: '所有打开' },
    { value: 'closed', label: '所有关闭' }
  ],
  keyword: [
    { value: 'contains', label: '包含' }
  ]
}

function getOperatorsForField(field: string): OperatorOption[] {
  return operatorsByType[field] || operatorsByType.default
}

function isEmptyOperator(op: string): boolean {
  return op === 'is_empty' || op === 'is_not_empty' || op === 'open' || op === 'closed'
}

const defaultBoardName = computed(() => props.projectName ? `${props.projectName} 看板` : '看板')

const editableName = ref(props.name)
const viewRolesSet = ref<Set<string>>(new Set(props.canViewRoles))
const editRolesSet = ref<Set<string>>(new Set(props.canEditRoles))
const editableFilterMode = ref(props.filterMode || 'all')
const editableDoneRetentionDays = ref<number | undefined>(props.doneRetentionDays ?? undefined)
const editableColumnField = ref(props.columnField || 'status')

// ===== 关联项目 =====
const editableLinkedProjectIds = ref<string[]>([...props.linkedProjectIds])

/** 过滤掉当前主项目，只显示其他可选项目 */
const filteredAvailableProjects = computed(() =>
  props.availableProjects.filter(p => p.id !== props.projectId)
)

function onLinkedProjectsChange(value: string | string[]) {
  const ids = Array.isArray(value) ? value : (value ? [value] : [])
  editableLinkedProjectIds.value = ids
  emit('update:linkedProjectIds', ids)
}

// Sync from props
watch(() => props.linkedProjectIds, (newIds) => {
  editableLinkedProjectIds.value = [...newIds]
})

function onColumnFieldChange(value: string) {
  editableColumnField.value = value
  emit('update:columnField', value)
}

// ===== 查询过滤条件行 =====
const filterRows = ref<FilterRow[]>([])
const queryValidationError = ref('')

/** 从 JSON 字符串解析过滤条件到行模型 */
function parseFilterQuery(json: string | null): FilterRow[] {
  if (!json) return []
  try {
    const filters = JSON.parse(json) as Array<{ field: string; operator: string; value?: string[] }>
    return filters.map(f => ({
      _key: ++filterRowKeySeq,
      field: f.field || '',
      operator: f.operator || 'eq',
      value: Array.isArray(f.value) ? f.value.join(', ') : ''
    }))
  } catch {
    return []
  }
}

/** 将行模型序列化为 JSON 字符串 */
function serializeFilterRows(): string | null {
  const validRows = filterRows.value.filter(r => r.field && r.operator)
  if (validRows.length === 0) return null
  const filters = validRows.map(r => {
    const entry: Record<string, any> = { field: r.field, operator: r.operator }
    if (!isEmptyOperator(r.operator)) {
      // 将逗号分隔的值拆分为数组
      const vals = r.value.split(/[,，]/).map(v => v.trim()).filter(v => v)
      entry.value = vals
    }
    return entry
  })
  return JSON.stringify(filters)
}

/** 校验当前过滤条件 */
function validateFilterRows(): string {
  if (editableFilterMode.value !== 'query') return ''
  const validRows = filterRows.value.filter(r => r.field && r.operator)
  if (validRows.length === 0) return '请至少添加一个过滤条件'
  for (const row of validRows) {
    if (!isEmptyOperator(row.operator) && !row.value.trim()) {
      const fieldLabel = availableFields.find(f => f.value === row.field)?.label || row.field
      return `"${fieldLabel}" 的值不能为空`
    }
  }
  return ''
}

// 初始化 filterRows
filterRows.value = parseFilterQuery(props.filterQuery)

// Sync from props when they change externally
watch(() => props.name, (newName) => {
  editableName.value = newName
})

watch(() => props.canViewRoles, (newRoles) => {
  viewRolesSet.value = new Set(newRoles)
})

watch(() => props.canEditRoles, (newRoles) => {
  editRolesSet.value = new Set(newRoles)
})

watch(() => props.filterMode, (newMode) => {
  editableFilterMode.value = newMode || 'all'
})

watch(() => props.filterQuery, (newQuery) => {
  filterRows.value = parseFilterQuery(newQuery)
})

watch(() => props.doneRetentionDays, (newDays) => {
  editableDoneRetentionDays.value = newDays ?? undefined
})

watch(filterRows, () => {
  queryValidationError.value = validateFilterRows()
  emitFilterQuery()
}, { deep: true })

function emitName() {
  emit('update:name', editableName.value)
}

function setFilterMode(mode: string) {
  editableFilterMode.value = mode
  emit('update:filterMode', mode)
  // 切换到 query 模式时如果没有行则自动添加一行
  if (mode === 'query' && filterRows.value.length === 0) {
    addFilterRow()
  }
  // 切换模式时重新发射 filterQuery
  emitFilterQuery()
}

function emitFilterQuery() {
  if (editableFilterMode.value === 'query') {
    emit('update:filterQuery', serializeFilterRows())
  } else {
    emit('update:filterQuery', null)
  }
}

function emitDoneRetentionDays(val: number | undefined) {
  editableDoneRetentionDays.value = val
  emit('update:doneRetentionDays', val ?? null)
}

function addFilterRow() {
  filterRows.value.push({ _key: ++filterRowKeySeq, field: '', operator: 'eq', value: '' })
}

function removeFilterRow(index: number) {
  filterRows.value.splice(index, 1)
}

function onFieldChange(index: number) {
  const row = filterRows.value[index]
  // 切换字段时重置操作符为该字段的第一个可用操作符
  const ops = getOperatorsForField(row.field)
  if (ops.length > 0 && !ops.some(op => op.value === row.operator)) {
    row.operator = ops[0].value
  }
  row.value = ''
}

function toggleViewRole(code: string, checked: boolean) {
  const newSet = new Set(viewRolesSet.value)
  if (checked) {
    newSet.add(code)
  } else {
    // Prevent deselecting all roles
    if (newSet.size <= 1) return
    newSet.delete(code)
  }
  viewRolesSet.value = newSet
  emit('update:canViewRoles', Array.from(newSet))
}

function toggleEditRole(code: string, checked: boolean) {
  const newSet = new Set(editRolesSet.value)
  if (checked) {
    newSet.add(code)
  } else {
    // Prevent deselecting all roles
    if (newSet.size <= 1) return
    newSet.delete(code)
  }
  editRolesSet.value = newSet
  emit('update:canEditRoles', Array.from(newSet))
}
</script>

<style scoped>
.general-settings {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.settings-hint p {
  font-size: 13px;
  color: var(--color-text-3);
  margin: 0;
  line-height: 1.5;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.section-desc {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 4px;
}

/* ===== Board Behavior ===== */
.behavior-options {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}

.behavior-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  transition: background 0.15s;
  user-select: none;
}

.behavior-option:hover {
  background: var(--color-fill-1);
}

.behavior-option--active {
  background: var(--tf-accent-subtle);
}

.behavior-option + .behavior-option {
  border-top: 1px solid var(--color-border-light, var(--color-border));
}

.behavior-option-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.behavior-option-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.behavior-option-desc {
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.4;
}

/* ===== Sprint 模式无活跃迭代警告 ===== */
.sprint-warning {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  background: var(--tf-warning-bg);
  border: 1px solid rgba(var(--warning-6), 0.3);
  border-radius: 6px;
  margin-top: 8px;
}

.sprint-warning-icon {
  font-size: 14px;
  flex-shrink: 0;
  line-height: 1.4;
}

.sprint-warning-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sprint-warning-title {
  font-size: 13px;
  font-weight: 600;
  color: rgb(var(--warning-6));
}

.sprint-warning-desc {
  font-size: 12px;
  color: var(--color-text-2);
  line-height: 1.4;
}

/* ===== Done Retention ===== */
.retention-config {
  margin-top: 12px;
  padding: 12px 14px;
  background: var(--color-fill-1);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.retention-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.retention-desc {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 4px;
}

/* ===== Query Filter Builder ===== */
.query-filter-builder {
  margin-top: 12px;
  padding: 12px 14px;
  background: var(--color-fill-1);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.query-builder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.query-builder-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.query-builder-empty {
  font-size: 12px;
  color: var(--color-text-3);
  padding: 12px 0;
  text-align: center;
}

.query-builder-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-empty-placeholder {
  flex: 1;
  min-width: 120px;
}

.query-validation-error {
  font-size: 12px;
  color: var(--tf-danger);
  display: flex;
  align-items: center;
  gap: 4px;
}

.query-builder-hint {
  font-size: 11px;
  color: var(--color-text-4);
  line-height: 1.4;
}

/* ===== 角色列表 ===== */
.role-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}

.role-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
  user-select: none;
}

.role-item:hover {
  background: var(--color-fill-1);
}

.role-item--checked {
  background: var(--tf-accent-subtle);
}

.role-item + .role-item {
  border-top: 1px solid var(--color-border-light, var(--color-border));
}

.role-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  min-width: 80px;
}

.role-desc {
  font-size: 12px;
  color: var(--color-text-3);
}

.column-field-hint {
  margin-top: 8px;
  font-size: 12px;
  color: rgb(var(--warning-6));
  line-height: 1.5;
}

/* ===== 关联项目多选 ===== */
.project-option-key {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-accent);
  margin-right: 6px;
  background: var(--tf-accent-light);
  padding: 1px 4px;
  border-radius: 3px;
}

.project-option-name {
  font-size: 13px;
  color: var(--color-text-1);
}

.multi-project-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-2);
  display: flex;
  align-items: flex-start;
  gap: 4px;
  line-height: 1.5;
}
</style>
