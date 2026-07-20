<template>
  <div class="fields-in-projects">
    <!-- 筛选 -->
    <div class="fip-toolbar">
      <a-input
        v-model="keyword"
        placeholder="按项目名称筛选..."
        size="small"
        allow-clear
        style="width: 240px"
      >
        <template #prefix><icon-search /></template>
      </a-input>
      <a-radio-group v-model="groupBy" type="button" size="small">
        <a-radio value="project">按项目分组</a-radio>
        <a-radio value="field">按字段分组</a-radio>
      </a-radio-group>
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
            <span class="field-count">{{ project.fields.length }} 个字段</span>
          </div>
          <div v-if="expandedGroups.has(project.projectId)" class="group-fields">
            <div v-for="field in project.fields" :key="field.id" class="field-row">
              <span class="field-name">{{ field.name }}</span>
              <a-tag size="small" class="field-type-tag">{{ formatType(field.fieldFormat) }}</a-tag>
              <span v-if="field.isForAll" class="badge badge-global">全局</span>
              <span v-else class="badge badge-project">项目级</span>
              <span v-if="field.isRequired" class="badge badge-required">必填</span>
              <span v-if="field.isMulti" class="badge badge-multi">多值</span>
              <div class="field-actions">
                <a-button
                  v-if="!field.isForAll"
                  type="text"
                  size="mini"
                  status="danger"
                  @click.stop="handleDetach(project.projectId, field.id, field.name, project.projectName)"
                >
                  移除
                </a-button>
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
          </div>
          <div v-if="expandedGroups.has(item.fieldId)" class="group-fields">
            <template v-if="item.isForAll">
              <div class="info-note">此字段为全局字段，自动对所有项目可用。</div>
            </template>
            <template v-else>
              <div v-for="proj in item.projects" :key="proj.projectId" class="field-row">
                <span class="project-name">{{ proj.projectName }}</span>
                <span class="project-key">{{ proj.projectKey }}</span>
                <div class="field-actions">
                  <a-button
                    type="text"
                    size="mini"
                    status="danger"
                    @click.stop="handleDetach(proj.projectId, item.fieldId, item.fieldName, proj.projectName)"
                  >
                    移除
                  </a-button>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </template>

    <!-- 附加字段弹窗 -->
    <a-modal
      v-model:visible="attachDialogVisible"
      :title="`添加字段到「${attachTarget?.projectName || ''}」`"
      :width="420"
      :ok-loading="attaching"
      ok-text="添加"
      @ok="handleAttach"
      @cancel="attachDialogVisible = false"
    >
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
        所有非全局字段已添加到此项目
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { IconSearch, IconRight, IconPlus, IconFolder, IconFile } from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { customFieldApi } from '@/api'
import type { ProjectFieldsVO, CustomFieldDefinitionVO } from '@/api/types'

const loading = ref(false)
const keyword = ref('')
const groupBy = ref<'project' | 'field'>('project')
const expandedGroups = ref(new Set<string>())
const projectFieldsData = ref<ProjectFieldsVO[]>([])

// Attach dialog state
const attachDialogVisible = ref(false)
const attachTarget = ref<ProjectFieldsVO | null>(null)
const selectedFieldToAttach = ref<string | null>(null)
const availableFields = ref<CustomFieldDefinitionVO[]>([])
const loadingAvailable = ref(false)
const attaching = ref(false)

const fieldTypeLabels: Record<string, string> = {
  string: '文本(单行)',
  text: '文本(多行)',
  int: '整数',
  float: '小数',
  date: '日期',
  datetime: '日期时间',
  bool: '布尔',
  list: '列表(枚举)',
  user: '用户'
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

// 按项目分组视图（过滤）
const filteredByProject = computed(() => {
  if (!keyword.value.trim()) return projectFieldsData.value
  const kw = keyword.value.trim().toLowerCase()
  return projectFieldsData.value.filter(p =>
    p.projectName.toLowerCase().includes(kw) || p.projectKey.toLowerCase().includes(kw)
  )
})

// 按字段分组视图
interface FieldGroupItem {
  fieldId: string
  fieldName: string
  fieldFormat: string
  isForAll: boolean
  projects: Array<{ projectId: string; projectName: string; projectKey: string }>
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
          projects: []
        })
      }
      const item = fieldMap.get(field.id)!
      if (!field.isForAll) {
        item.projects.push({
          projectId: project.projectId,
          projectName: project.projectName,
          projectKey: project.projectKey
        })
      }
    }
  }

  let items = Array.from(fieldMap.values())
  if (keyword.value.trim()) {
    const kw = keyword.value.trim().toLowerCase()
    items = items.filter(f => f.fieldName.toLowerCase().includes(kw))
  }
  return items.sort((a, b) => a.fieldName.localeCompare(b.fieldName))
})

async function loadData() {
  loading.value = true
  try {
    const res = await customFieldApi.fieldsInProjects()
    projectFieldsData.value = res.data || []
    // 默认展开第一个项目
    if (projectFieldsData.value.length > 0) {
      expandedGroups.value.add(projectFieldsData.value[0].projectId)
    }
  } catch {
    projectFieldsData.value = []
  } finally {
    loading.value = false
  }
}

async function openAttachDialog(project: ProjectFieldsVO) {
  attachTarget.value = project
  selectedFieldToAttach.value = null
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

.group-fields {
  padding: 4px 12px 8px 32px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  transition: background 100ms;
}
.field-row:hover {
  background: var(--tf-bg-hover);
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
.badge-multi {
  background: var(--color-purple-light-1, rgba(156, 39, 176, 0.15));
  color: #b388ff;
}

.field-actions {
  margin-left: auto;
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
  margin-top: 8px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}
</style>
