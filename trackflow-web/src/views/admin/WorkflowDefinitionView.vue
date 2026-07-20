<template>
  <div class="workflow-def-page">
    <div class="page-header">
      <div class="header-left">
        <router-link to="/admin" class="back-link">← 系统管理</router-link>
        <h1 class="page-title">工作流定义</h1>
        <p class="page-desc">管理命名工作流定义，支持创建、克隆、附加到项目</p>
      </div>
      <div class="header-actions">
        <a-button type="primary" @click="showCreateModal = true">
          创建工作流
        </a-button>
      </div>
    </div>

    <!-- 工作流列表 -->
    <div v-if="loading" class="loading-state">
      <a-spin />
    </div>

    <div v-else-if="definitions.length === 0" class="empty-state">
      <div class="empty-icon">📋</div>
      <h3 class="empty-title">暂无工作流定义</h3>
      <p class="empty-desc">创建第一个工作流定义来开始管理状态转换规则</p>
      <a-button type="primary" @click="showCreateModal = true">创建工作流</a-button>
    </div>

    <div v-else class="def-list">
      <div
        v-for="def in definitions"
        :key="def.id"
        class="def-card"
      >
        <div class="def-card-header">
          <div class="def-name-row">
            <h3 class="def-name">{{ def.name }}</h3>
            <a-tag v-if="def.isDefault" color="blue" size="small">默认</a-tag>
          </div>
          <div class="def-actions">
            <a-dropdown trigger="click">
              <a-button type="text" size="small">
                <template #icon><span>⋯</span></template>
              </a-button>
              <template #content>
                <a-doption @click="handleEdit(def)">编辑</a-doption>
                <a-doption @click="handleClone(def)">克隆</a-doption>
                <a-doption @click="handleAttach(def)">附加到项目</a-doption>
                <a-doption
                  v-if="!def.isDefault"
                  class="danger-option"
                  @click="handleDelete(def)"
                >删除</a-doption>
              </template>
            </a-dropdown>
          </div>
        </div>

        <p v-if="def.description" class="def-desc">{{ def.description }}</p>

        <div class="def-stats">
          <span class="stat-item">
            <span class="stat-label">转换规则</span>
            <span class="stat-value">{{ def.transitionCount }}</span>
          </span>
          <span class="stat-item">
            <span class="stat-label">绑定项目</span>
            <span class="stat-value">{{ def.projectCount }}</span>
          </span>
        </div>

        <div v-if="def.projects && def.projects.length > 0" class="def-projects">
          <a-tag
            v-for="proj in def.projects"
            :key="proj.id"
            size="small"
            class="project-tag"
            closable
            @close="handleDetach(def, proj)"
          >
            {{ proj.key }} - {{ proj.name }}
          </a-tag>
        </div>

        <div class="def-meta">
          <span v-if="def.createdByName">创建者: {{ def.createdByName }}</span>
          <span>更新于 {{ formatDate(def.updatedAt) }}</span>
        </div>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="showCreateModal"
      :title="editingDef ? '编辑工作流' : '创建工作流'"
      @ok="handleCreateOrUpdate"
      @cancel="resetForm"
      :ok-loading="saving"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model="form.name" placeholder="输入工作流名称" :max-length="100" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model="form.description" placeholder="描述该工作流的用途" :max-length="500" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
        <a-form-item label="设为默认">
          <a-switch v-model="form.isDefault" />
          <span class="form-helper">新项目创建时自动绑定此工作流</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 克隆弹窗 -->
    <a-modal
      v-model:visible="showCloneModal"
      title="克隆工作流"
      @ok="handleCloneConfirm"
      :ok-loading="saving"
    >
      <a-form layout="vertical">
        <a-form-item label="源工作流">
          <a-input :model-value="cloneSource?.name" disabled />
        </a-form-item>
        <a-form-item label="新名称" required>
          <a-input v-model="cloneName" placeholder="输入克隆后的工作流名称" :max-length="100" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 附加到项目弹窗 -->
    <a-modal
      v-model:visible="showAttachModal"
      title="附加到项目"
      @ok="handleAttachConfirm"
      :ok-loading="saving"
    >
      <p class="attach-desc">选择要绑定「{{ attachSource?.name }}」工作流的项目：</p>
      <a-select
        v-model="selectedProjectId"
        placeholder="选择项目"
        :options="availableProjects"
        :field-names="{ value: 'id', label: 'displayName' }"
        allow-search
        style="width: 100%"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { workflowDefinitionApi } from '@/api/workflowDefinition'
import type { WorkflowDefinitionVO, BoundProject } from '@/api/workflowDefinition'
import { projectApi } from '@/api'

const loading = ref(true)
const saving = ref(false)
const definitions = ref<WorkflowDefinitionVO[]>([])

// 创建/编辑
const showCreateModal = ref(false)
const editingDef = ref<WorkflowDefinitionVO | null>(null)
const form = ref({ name: '', description: '', isDefault: false })

// 克隆
const showCloneModal = ref(false)
const cloneSource = ref<WorkflowDefinitionVO | null>(null)
const cloneName = ref('')

// 附加
const showAttachModal = ref(false)
const attachSource = ref<WorkflowDefinitionVO | null>(null)
const selectedProjectId = ref('')
const allProjects = ref<{ id: string; name: string; key: string }[]>([])

const availableProjects = computed(() => {
  if (!attachSource.value) return []
  const boundIds = new Set(attachSource.value.projects?.map(p => p.id) || [])
  return allProjects.value
    .filter(p => !boundIds.has(p.id))
    .map(p => ({ ...p, displayName: `${p.key} - ${p.name}` }))
})

onMounted(async () => {
  await loadDefinitions()
  await loadProjects()
})

async function loadDefinitions() {
  loading.value = true
  try {
    const res = await workflowDefinitionApi.list()
    if (res.code === 0 && res.data) {
      definitions.value = res.data
    }
  } catch (e: any) {
    Message.error('加载工作流定义失败')
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    if (res.code === 0 && res.data?.list) {
      allProjects.value = res.data.list.map((p: any) => ({
        id: p.id,
        name: p.name,
        key: p.key
      }))
    }
  } catch {
    // 非关键错误
  }
}

function handleEdit(def: WorkflowDefinitionVO) {
  editingDef.value = def
  form.value = {
    name: def.name,
    description: def.description || '',
    isDefault: def.isDefault
  }
  showCreateModal.value = true
}

async function handleCreateOrUpdate() {
  if (!form.value.name.trim()) {
    Message.warning('请输入工作流名称')
    return
  }

  saving.value = true
  try {
    if (editingDef.value) {
      await workflowDefinitionApi.update(editingDef.value.id, form.value)
      Message.success('更新成功')
    } else {
      await workflowDefinitionApi.create(form.value)
      Message.success('创建成功')
    }
    showCreateModal.value = false
    resetForm()
    await loadDefinitions()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

function resetForm() {
  editingDef.value = null
  form.value = { name: '', description: '', isDefault: false }
}

function handleClone(def: WorkflowDefinitionVO) {
  cloneSource.value = def
  cloneName.value = def.name + ' (副本)'
  showCloneModal.value = true
}

async function handleCloneConfirm() {
  if (!cloneName.value.trim()) {
    Message.warning('请输入新名称')
    return
  }

  saving.value = true
  try {
    await workflowDefinitionApi.clone(cloneSource.value!.id, cloneName.value)
    Message.success('克隆成功')
    showCloneModal.value = false
    await loadDefinitions()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '克隆失败')
  } finally {
    saving.value = false
  }
}

function handleAttach(def: WorkflowDefinitionVO) {
  attachSource.value = def
  selectedProjectId.value = ''
  showAttachModal.value = true
}

async function handleAttachConfirm() {
  if (!selectedProjectId.value) {
    Message.warning('请选择项目')
    return
  }

  saving.value = true
  try {
    await workflowDefinitionApi.attachToProject(selectedProjectId.value, attachSource.value!.id)
    Message.success('绑定成功')
    showAttachModal.value = false
    await loadDefinitions()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '绑定失败')
  } finally {
    saving.value = false
  }
}

async function handleDetach(def: WorkflowDefinitionVO, proj: BoundProject) {
  Modal.confirm({
    title: '确认解绑',
    content: `确定要将项目「${proj.name}」从工作流「${def.name}」中分离吗？`,
    async onOk() {
      try {
        await workflowDefinitionApi.detachFromProject(proj.id, def.id)
        Message.success('解绑成功')
        await loadDefinitions()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '解绑失败')
      }
    }
  })
}

function handleDelete(def: WorkflowDefinitionVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除工作流「${def.name}」吗？关联的转换规则也将被删除，此操作不可撤销。`,
    async onOk() {
      try {
        await workflowDefinitionApi.delete(def.id)
        Message.success('删除成功')
        await loadDefinitions()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}
</script>

<style scoped>
.workflow-def-page {
  padding: 24px 32px;
  max-width: 1200px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.back-link {
  color: var(--tf-text-tertiary);
  font-size: 12px;
  text-decoration: none;
  margin-bottom: 4px;
  display: inline-block;
}
.back-link:hover {
  color: var(--tf-text-primary);
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.page-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 48px;
}

.empty-state {
  text-align: center;
  padding: 64px 32px;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}
.empty-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0 0 16px;
}

.def-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}

.def-card {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-primary);
  border-radius: 8px;
  padding: 16px;
  transition: border-color 150ms;
}
.def-card:hover {
  border-color: var(--tf-accent);
}

.def-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.def-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.def-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.def-desc {
  font-size: 12px;
  color: var(--tf-text-secondary);
  margin: 0 0 12px;
  line-height: 1.4;
}

.def-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.stat-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.stat-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.def-projects {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 12px;
}

.project-tag {
  font-size: 11px;
}

.def-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.form-helper {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-left: 8px;
}

.attach-desc {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin-bottom: 12px;
}

.danger-option {
  color: var(--color-danger-6) !important;
}
</style>
