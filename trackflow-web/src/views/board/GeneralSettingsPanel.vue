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

const props = defineProps<{
  name: string
  canViewRoles: string[]
  canEditRoles: string[]
  projectName: string
  filterMode: string
  doneRetentionDays: number | null
}>()

const emit = defineEmits<{
  'update:name': [value: string]
  'update:canViewRoles': [roles: string[]]
  'update:canEditRoles': [roles: string[]]
  'update:filterMode': [value: string]
  'update:doneRetentionDays': [value: number | null]
}>()

const availableRoles: RoleOption[] = [
  { code: 'project_admin', label: '项目管理员', desc: '项目的管理者' },
  { code: 'tech_lead', label: '技术负责人', desc: '技术方向决策者' },
  { code: 'developer', label: '开发人员', desc: '执行开发任务' },
  { code: 'product_manager', label: '产品经理', desc: '需求分析与产品规划' },
  { code: 'tester', label: '测试人员', desc: '质量保障' },
  { code: 'observer', label: '观察者', desc: '只读访问' }
]

const defaultBoardName = computed(() => props.projectName ? `${props.projectName} 看板` : '看板')

const editableName = ref(props.name)
const viewRolesSet = ref<Set<string>>(new Set(props.canViewRoles))
const editRolesSet = ref<Set<string>>(new Set(props.canEditRoles))
const editableFilterMode = ref(props.filterMode || 'all')
const editableDoneRetentionDays = ref<number | undefined>(props.doneRetentionDays ?? undefined)

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

watch(() => props.doneRetentionDays, (newDays) => {
  editableDoneRetentionDays.value = newDays ?? undefined
})

function emitName() {
  emit('update:name', editableName.value)
}

function setFilterMode(mode: string) {
  editableFilterMode.value = mode
  emit('update:filterMode', mode)
}

function emitDoneRetentionDays(val: number | undefined) {
  editableDoneRetentionDays.value = val
  emit('update:doneRetentionDays', val ?? null)
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
  background: rgba(var(--primary-6), 0.04);
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
  background: rgba(var(--primary-6), 0.04);
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
</style>
