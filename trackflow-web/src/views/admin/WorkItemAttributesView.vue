<template>
  <div class="wia-page">
    <!-- Header -->
    <div class="wia-header">
      <router-link to="/admin" class="back-link">← 返回管理</router-link>
      <div class="header-row">
        <div>
          <h1 class="page-title">工作项属性</h1>
          <p class="page-desc">管理工时记录的分类属性。每个属性定义一组可选值，可分配到指定项目使用。</p>
        </div>
        <a-button type="primary" @click="showCreateDialog = true">
          新建属性
        </a-button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="wia-loading">
      <a-spin :size="20" />
      <span>加载中...</span>
    </div>

    <!-- List -->
    <div v-else class="wia-list">
      <div v-if="attributes.length === 0" class="wia-empty">
        <div class="empty-icon">🏷️</div>
        <h3 class="empty-title">暂无工作项属性</h3>
        <p class="empty-desc">创建属性来对工时记录进行分类，例如"工作类型"、"计费类别"等。</p>
        <a-button type="primary" @click="showCreateDialog = true">创建第一个属性</a-button>
      </div>

      <div v-else class="attr-cards">
        <div
          v-for="attr in attributes"
          :key="attr.id"
          class="attr-card"
          :class="{ selected: selectedId === attr.id }"
          @click="selectAttribute(attr)"
        >
          <div class="attr-card-header">
            <span class="attr-name">{{ attr.name }}</span>
            <span v-if="attr.isBuiltin" class="attr-badge builtin">内置</span>
          </div>
          <div class="attr-meta">
            <span class="meta-item">{{ attr.values.length }} 个值</span>
            <span class="meta-sep">·</span>
            <span class="meta-item">{{ attr.projectIds?.length || 0 }} 个项目</span>
            <span v-if="attr.usageCount" class="meta-sep">·</span>
            <span v-if="attr.usageCount" class="meta-item">{{ attr.usageCount }} 次使用</span>
          </div>
          <div class="attr-values-preview">
            <span
              v-for="val in attr.values.slice(0, 5)"
              :key="val.id"
              class="value-chip"
              :style="val.color ? { borderColor: val.color, color: val.color } : {}"
            >{{ val.name }}</span>
            <span v-if="attr.values.length > 5" class="value-more">+{{ attr.values.length - 5 }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Detail Panel (right side) -->
    <a-drawer
      v-model:visible="showDetail"
      :title="selectedAttr?.name || '属性详情'"
      :width="480"
      :footer="false"
      @close="selectedId = ''"
    >
      <template v-if="selectedAttr">
        <!-- Rename -->
        <div class="detail-section">
          <label class="detail-label">属性名称</label>
          <div class="rename-row">
            <a-input v-model="editName" :disabled="saving" />
            <a-button
              type="primary"
              size="small"
              :loading="saving"
              :disabled="editName === selectedAttr.name || !editName.trim()"
              @click="renameAttribute"
            >保存</a-button>
          </div>
        </div>

        <!-- Values -->
        <div class="detail-section">
          <div class="section-title-row">
            <label class="detail-label">值列表</label>
            <a-button size="mini" @click="addNewValue">+ 添加</a-button>
          </div>
          <div class="values-list">
            <div v-for="(val, idx) in editValues" :key="idx" class="value-row">
              <input
                type="color"
                :value="val.color || '#6e7681'"
                class="color-picker"
                @input="val.color = ($event.target as HTMLInputElement).value"
              />
              <a-input v-model="val.name" size="small" class="value-input" />
              <a-button
                size="mini"
                type="text"
                status="danger"
                @click="removeValue(idx)"
              >删除</a-button>
            </div>
          </div>
          <a-button
            type="primary"
            size="small"
            :loading="savingValues"
            :disabled="!valuesChanged"
            @click="saveValues"
            style="margin-top: 12px"
          >保存值列表</a-button>
        </div>

        <!-- Projects -->
        <div class="detail-section">
          <div class="section-title-row">
            <label class="detail-label">分配的项目</label>
            <a-button size="mini" @click="showProjectDialog = true">管理项目</a-button>
          </div>
          <div v-if="selectedAttr.projects && selectedAttr.projects.length > 0" class="projects-list">
            <span
              v-for="proj in selectedAttr.projects"
              :key="proj.id"
              class="project-chip"
              :class="{ 'project-deleted': proj.deleted }"
            >
              <template v-if="!proj.deleted">{{ proj.key }} - {{ proj.name }}</template>
              <template v-else>{{ proj.name }}</template>
            </span>
          </div>
          <div v-else-if="selectedAttr.projectIds && selectedAttr.projectIds.length > 0" class="projects-list">
            <!-- Fallback: 后端未返回 projects 时（兼容旧数据） -->
            <span v-for="pid in selectedAttr.projectIds" :key="pid" class="project-chip">
              {{ projectNameMap[pid] || pid }}
            </span>
          </div>
          <div v-else class="no-projects">
            <span class="no-projects-text">未分配到任何项目</span>
          </div>
        </div>

        <!-- Delete -->
        <div v-if="!selectedAttr.isBuiltin" class="detail-section danger-zone">
          <label class="detail-label">危险操作</label>
          <a-button
            status="danger"
            size="small"
            @click="confirmDelete"
          >删除此属性</a-button>
          <p class="danger-hint">删除后，所有使用此属性的工时记录将丢失相关数据，且不可恢复。</p>
        </div>
      </template>
    </a-drawer>

    <!-- Create Dialog -->
    <a-modal
      v-model:visible="showCreateDialog"
      title="新建工作项属性"
      :ok-loading="creating"
      @ok="createAttribute"
      @cancel="resetCreateForm"
    >
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="属性名称" required>
          <a-input v-model="createForm.name" placeholder="例如：计费类别" :max-length="100" />
        </a-form-item>
        <a-form-item label="初始值（可选）">
          <div v-for="(val, idx) in createForm.values" :key="idx" class="create-value-row">
            <a-input v-model="val.name" placeholder="值名称" size="small" />
            <input type="color" :value="val.color || '#6e7681'" class="color-picker" @input="val.color = ($event.target as HTMLInputElement).value" />
            <a-button size="mini" type="text" @click="createForm.values.splice(idx, 1)">×</a-button>
          </div>
          <a-button size="mini" type="dashed" @click="createForm.values.push({ name: '', color: '#6e7681' })" style="margin-top: 8px">
            + 添加值
          </a-button>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Project Assignment Dialog -->
    <a-modal
      v-model:visible="showProjectDialog"
      title="管理项目分配"
      :ok-loading="savingProjects"
      @ok="saveProjects"
    >
      <p class="dialog-hint">选择要启用此属性的项目：</p>
      <div class="project-checkboxes">
        <a-checkbox
          v-for="project in allProjects"
          :key="project.id"
          :model-value="selectedProjectIds.includes(project.id)"
          @change="(checked: boolean) => toggleProject(project.id, checked)"
          class="project-checkbox-item"
        >
          <span class="project-key">{{ project.key }}</span>
          <span class="project-name-text">{{ project.name }}</span>
        </a-checkbox>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { workItemAttributeApi } from '@/api/timeEntry'
import type { WorkItemAttributeVO, AttributeValueVO, AttributeProjectVO } from '@/api/timeEntry'
import { projectApi } from '@/api'

const loading = ref(true)
const attributes = ref<WorkItemAttributeVO[]>([])
const selectedId = ref('')
const showDetail = ref(false)
const showCreateDialog = ref(false)
const showProjectDialog = ref(false)
const saving = ref(false)
const savingValues = ref(false)
const savingProjects = ref(false)
const creating = ref(false)

// Edit state
const editName = ref('')
const editValues = ref<{ id?: string; name: string; color?: string }[]>([])
const selectedProjectIds = ref<string[]>([])

// Projects for assignment
const allProjects = ref<{ id: string; key: string; name: string }[]>([])
const projectNameMap = computed(() => {
  const map: Record<string, string> = {}
  allProjects.value.forEach(p => { map[p.id] = `${p.key} - ${p.name}` })
  return map
})

// Create form
const createForm = reactive({
  name: '',
  values: [] as { name: string; color?: string }[]
})

const selectedAttr = computed(() => attributes.value.find(a => a.id === selectedId.value))

const valuesChanged = computed(() => {
  if (!selectedAttr.value) return false
  const original = selectedAttr.value.values
  if (editValues.value.length !== original.length) return true
  return editValues.value.some((v, i) =>
    v.name !== original[i]?.name || v.color !== original[i]?.color
  )
})

// Watchers
watch(selectedAttr, (attr) => {
  if (attr) {
    editName.value = attr.name
    editValues.value = attr.values.map(v => ({ id: v.id, name: v.name, color: v.color }))
    selectedProjectIds.value = [...(attr.projectIds || [])]
  }
})

function selectAttribute(attr: WorkItemAttributeVO) {
  selectedId.value = attr.id
  showDetail.value = true
}

function addNewValue() {
  editValues.value.push({ name: '', color: '#6e7681' })
}

function removeValue(idx: number) {
  editValues.value.splice(idx, 1)
}

function toggleProject(id: string, checked: boolean) {
  if (checked) {
    if (!selectedProjectIds.value.includes(id)) selectedProjectIds.value.push(id)
  } else {
    selectedProjectIds.value = selectedProjectIds.value.filter(p => p !== id)
  }
}

async function loadAttributes() {
  loading.value = true
  try {
    const res = await workItemAttributeApi.list()
    if (res.code === 0 && res.data) {
      attributes.value = res.data
    }
  } catch {
    Message.error('加载工作项属性失败')
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 200 })
    if (res.code === 0 && res.data?.list) {
      allProjects.value = res.data.list.map((p: any) => ({ id: p.id, key: p.key, name: p.name }))
    }
  } catch {
    // ignore
  }
}

async function createAttribute() {
  if (!createForm.name.trim()) {
    Message.warning('请输入属性名称')
    return
  }
  creating.value = true
  try {
    const validValues = createForm.values.filter(v => v.name.trim())
    const res = await workItemAttributeApi.create({
      name: createForm.name.trim(),
      values: validValues.length > 0 ? validValues : undefined
    })
    if (res.code === 0) {
      Message.success('属性创建成功')
      showCreateDialog.value = false
      resetCreateForm()
      await loadAttributes()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

function resetCreateForm() {
  createForm.name = ''
  createForm.values = []
}

async function renameAttribute() {
  if (!selectedId.value || !editName.value.trim()) return
  saving.value = true
  try {
    const res = await workItemAttributeApi.update(selectedId.value, { name: editName.value.trim() })
    if (res.code === 0) {
      Message.success('已重命名')
      await loadAttributes()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '重命名失败')
  } finally {
    saving.value = false
  }
}

async function saveValues() {
  if (!selectedId.value) return
  const validValues = editValues.value.filter(v => v.name.trim())
  if (validValues.length === 0) {
    Message.warning('至少保留一个值')
    return
  }
  savingValues.value = true
  try {
    const res = await workItemAttributeApi.update(selectedId.value, { values: validValues })
    if (res.code === 0) {
      Message.success('值列表已更新')
      await loadAttributes()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
  } finally {
    savingValues.value = false
  }
}

async function saveProjects() {
  if (!selectedId.value) return
  if (selectedProjectIds.value.length === 0) {
    Message.warning('至少选择一个项目')
    return
  }
  savingProjects.value = true
  try {
    const res = await workItemAttributeApi.manageProjects(selectedId.value, selectedProjectIds.value)
    if (res.code === 0) {
      Message.success('项目分配已更新')
      showProjectDialog.value = false
      await loadAttributes()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
  } finally {
    savingProjects.value = false
  }
}

function confirmDelete() {
  if (!selectedAttr.value) return
  const usage = selectedAttr.value.usageCount || 0
  Modal.warning({
    title: '确认删除',
    content: usage > 0
      ? `此属性已被 ${usage} 条工时记录引用。删除后这些记录将丢失相关分类数据，且不可恢复。确定删除吗？`
      : '确定删除此属性吗？此操作不可恢复。',
    okText: '确认删除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    onOk: async () => {
      try {
        const res = await workItemAttributeApi.delete(selectedId.value)
        if (res.code === 0) {
          Message.success('属性已删除')
          showDetail.value = false
          selectedId.value = ''
          await loadAttributes()
        }
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

onMounted(() => {
  loadAttributes()
  loadProjects()
})
</script>

<style scoped>
.wia-page {
  padding: 32px;
  max-width: 900px;
  height: 100%;
  overflow-y: auto;
}

.wia-header {
  margin-bottom: 24px;
}

.back-link {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  text-decoration: none;
  display: inline-block;
  margin-bottom: 12px;
  transition: color 0.15s;
}

.back-link:hover {
  color: var(--tf-accent);
}

.header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 4px 0;
}

.page-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.wia-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}

.wia-empty {
  text-align: center;
  padding: 48px 24px;
}

.empty-icon { font-size: 32px; margin-bottom: 12px; }
.empty-title { font-size: 16px; font-weight: 600; color: var(--tf-text-primary); margin: 0 0 8px 0; }
.empty-desc { font-size: 13px; color: var(--tf-text-tertiary); margin: 0 0 16px 0; }

.attr-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.attr-card {
  padding: 16px;
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  background: var(--tf-bg-surface);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.attr-card:hover {
  border-color: var(--tf-border);
  background: var(--tf-bg-hover);
}

.attr-card.selected {
  border-color: var(--tf-accent);
}

.attr-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.attr-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.attr-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 500;
}

.attr-badge.builtin {
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
}

.attr-meta {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-bottom: 8px;
}

.meta-sep { margin: 0 4px; }

.attr-values-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.value-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  border: 1px solid var(--tf-border-light);
  color: var(--tf-text-secondary);
}

.value-more {
  font-size: 11px;
  color: var(--tf-text-muted);
  padding: 2px 6px;
}

/* Detail panel */
.detail-section {
  padding: 16px 0;
  border-bottom: 1px solid var(--tf-border-light);
}

.detail-section:last-child { border-bottom: none; }

.detail-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  margin-bottom: 8px;
}

.rename-row {
  display: flex;
  gap: 8px;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.values-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.value-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-picker {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  padding: 0;
}

.value-input {
  flex: 1;
}

.projects-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.project-chip {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 3px;
  background: var(--tf-bg-elevated);
  color: var(--tf-text-secondary);
  border: 1px solid var(--tf-border-light);
}

.project-chip.project-deleted {
  color: var(--tf-text-muted);
  font-style: italic;
  opacity: 0.7;
}

.no-projects {
  padding: 8px 0;
}

.no-projects-text {
  font-size: 12px;
  color: var(--tf-text-muted);
}

.danger-zone {
  border-bottom: none;
}

.danger-hint {
  font-size: 11px;
  color: var(--tf-text-muted);
  margin: 8px 0 0 0;
}

/* Create dialog */
.create-value-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

/* Project assignment dialog */
.dialog-hint {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0 0 12px 0;
}

.project-checkboxes {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.project-checkbox-item {
  padding: 6px 8px;
  border-radius: 4px;
  transition: background 0.1s;
}

.project-checkbox-item:hover {
  background: var(--tf-bg-hover);
}

.project-key {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  margin-right: 6px;
}

.project-name-text {
  font-size: 12px;
  color: var(--tf-text-primary);
}
</style>
