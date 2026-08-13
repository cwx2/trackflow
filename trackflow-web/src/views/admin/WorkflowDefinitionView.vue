<template>
  <AdminPageLayout title="工作流">
    <template #actions>
      <a-button type="primary" aria-label="创建工作流" @click="showCreateModal = true">
        <template #icon><icon-plus /></template>
        创建工作流
      </a-button>
    </template>

    <!-- 筛选工具栏 -->
    <div class="filter-bar">
      <a-input
        v-model="searchKeyword"
        placeholder="搜索工作流名称..."
        allow-clear
        style="width: 240px"
      >
        <template #prefix><icon-search /></template>
      </a-input>
    </div>

    <!-- 主体：列表 + 侧边栏 -->
    <div class="main-content">
      <!-- 左侧列表 -->
      <div class="list-panel">
        <DataContainer
          :loading="loading"
          :is-empty="filteredDefinitions.length === 0"
          :empty-title="searchKeyword ? '没有匹配的工作流' : '暂无工作流'"
          :empty-description="searchKeyword ? '' : '创建第一个工作流来管理状态转换规则'"
          :create-action="searchKeyword ? undefined : '创建工作流'"
          @create="showCreateModal = true"
        >
        <div class="def-list">
          <div
            v-for="def in filteredDefinitions"
            :key="def.id"
            class="def-row"
            :class="{ selected: selectedDef?.id === def.id }"
            @click="selectDef(def)"
          >
            <div class="def-row-main">
              <div class="def-name-cell">
                <span class="def-icon">🔄</span>
                <span class="def-name">{{ def.name }}</span>
                <a-tag v-if="def.isDefault" color="arcoblue" size="small">默认</a-tag>
              </div>
            </div>
            <div class="def-row-meta">
              <span class="meta-item" :title="`关联 ${def.projectCount} 个项目`">
                <icon-apps class="meta-icon" />
                {{ def.projectCount > 0 ? def.projectCount + ' 个项目' : '未绑定' }}
              </span>
              <span class="meta-item" :title="'更新于 ' + formatDate(def.updatedAt)">
                {{ formatRelativeDate(def.updatedAt) }}
              </span>
            </div>
          </div>
        </div>
        </DataContainer>
      </div>

      <!-- 右侧详情侧边栏 -->
      <div v-if="selectedDef" class="detail-panel">
        <div class="detail-header">
          <div class="detail-title-row">
            <h2 class="detail-title">{{ selectedDef.name }}</h2>
            <a-dropdown trigger="click">
              <a-button type="text" size="small" class="more-btn">
                <template #icon><icon-more /></template>
              </a-button>
              <template #content>
                <a-doption @click="handleClone(selectedDef)">
                  <template #icon><icon-copy /></template>
                  克隆
                </a-doption>
                <a-doption
                  v-if="!selectedDef.isDefault"
                  class="danger-option"
                  @click="handleDelete(selectedDef)"
                >
                  <template #icon><icon-delete /></template>
                  删除
                </a-doption>
              </template>
            </a-dropdown>
          </div>
          <p v-if="selectedDef.description" class="detail-desc">{{ selectedDef.description }}</p>
          <div class="detail-meta">
            <span v-if="selectedDef.createdByName">创建者：{{ selectedDef.createdByName }}</span>
            <span>更新于 {{ formatDate(selectedDef.updatedAt) }}</span>
          </div>
          <div class="detail-actions">
            <a-button type="primary" size="small" @click="handleEditWorkflow(selectedDef)">
              <template #icon><icon-edit /></template>
              编辑工作流
            </a-button>
            <a-button size="small" @click="handleEditInfo(selectedDef)">
              <template #icon><icon-settings /></template>
              修改信息
            </a-button>
          </div>
        </div>

        <!-- Tabs: 规则 / 项目 -->
        <a-tabs v-model:active-key="detailTab" class="detail-tabs" size="small">
          <a-tab-pane key="rules" :title="`规则 ${selectedDef.transitionCount}`">
            <div class="tab-content">
              <div class="rules-summary">
                <div class="summary-stat">
                  <span class="stat-number">{{ selectedDef.transitionCount }}</span>
                  <span class="stat-label">条转换规则</span>
                </div>
              </div>
              <p class="tab-hint">
                点击"编辑工作流"按钮进入状态转换矩阵编辑器，查看和修改规则详情。
              </p>
            </div>
          </a-tab-pane>

          <a-tab-pane key="projects" :title="`项目 ${selectedDef.projectCount}`">
            <div class="tab-content">
              <!-- 项目列表 -->
              <div v-if="selectedDef.projects && selectedDef.projects.length > 0" class="project-list">
                <div
                  v-for="proj in selectedDef.projects"
                  :key="proj.id"
                  class="project-item"
                >
                  <span class="project-name">{{ proj.key }} - {{ proj.name }}</span>
                  <a-button
                    type="text"
                    size="mini"
                    class="detach-btn"
                    @click="handleDetach(selectedDef, proj)"
                  >
                    <template #icon><icon-close /></template>
                  </a-button>
                </div>
              </div>
              <div v-else class="no-projects">
                <span class="no-projects-text">未绑定到任何项目</span>
              </div>

              <!-- 管理项目按钮 -->
              <div class="project-actions">
                <a-button size="small" @click="handleAttach(selectedDef)">
                  <template #icon><icon-plus /></template>
                  附加到项目
                </a-button>
              </div>

              <!-- 自动附加开关 -->
              <div class="auto-attach-row">
                <span class="auto-attach-label">自动附加到新项目</span>
                <a-switch
                  :model-value="selectedDef.isDefault"
                  size="small"
                  @change="handleToggleDefault(selectedDef)"
                />
              </div>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>

      <!-- 未选中时的占位 -->
      <div v-else class="detail-placeholder">
        <div class="placeholder-content">
          <icon-share-alt class="placeholder-icon" />
          <p class="placeholder-text">选择一个工作流查看详情</p>
        </div>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="showCreateModal"
      :title="editingDef ? '修改工作流信息' : '创建工作流'"
      @ok="handleCreateOrUpdate"
      @cancel="resetForm"
      :ok-loading="saving"
      :ok-text="editingDef ? '保存' : '创建'"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model="form.name" placeholder="输入工作流名称" :max-length="100" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea
            v-model="form.description"
            placeholder="描述该工作流的用途"
            :max-length="500"
            :auto-size="{ minRows: 2, maxRows: 4 }"
          />
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
      ok-text="克隆"
    >
      <a-form :model="{}" layout="vertical">
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
      ok-text="绑定"
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
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/date'
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { workflowDefinitionApi } from '@/api/workflowDefinition'
import type { WorkflowDefinitionVO, BoundProject } from '@/api/workflowDefinition'
import { projectApi } from '@/api'
import AdminPageLayout from '@/components/admin/AdminPageLayout.vue'
import DataContainer from '@/components/base/DataContainer.vue'

const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const definitions = ref<WorkflowDefinitionVO[]>([])
const searchKeyword = ref('')
const selectedDef = ref<WorkflowDefinitionVO | null>(null)
const detailTab = ref('rules')

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

const filteredDefinitions = computed(() => {
  if (!searchKeyword.value.trim()) return definitions.value
  const kw = searchKeyword.value.toLowerCase()
  return definitions.value.filter(d => d.name.toLowerCase().includes(kw))
})

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
      // 如果之前有选中的项，更新引用
      if (selectedDef.value) {
        const updated = definitions.value.find(d => d.id === selectedDef.value!.id)
        selectedDef.value = updated || null
      }
    }
  } catch {
    Message.error('加载工作流列表失败')
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

function selectDef(def: WorkflowDefinitionVO) {
  selectedDef.value = def
  detailTab.value = 'rules'
}

function handleEditWorkflow(def: WorkflowDefinitionVO) {
  router.push({ path: '/admin/workflow', query: { definitionId: def.id } })
}

function handleEditInfo(def: WorkflowDefinitionVO) {
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
  const { confirmDelete } = useConfirmDelete()
  confirmDelete({
    itemName: `项目「${proj.name}」与工作流「${def.name}」的绑定`,
    confirmText: '解绑',
    onConfirm: async () => {
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
  const { confirmDangerDelete } = useConfirmDelete()
  confirmDangerDelete({
    itemName: `工作流「${def.name}」`,
    impactDescription: '关联的转换规则也将被删除',
    confirmText: '确认删除',
    onConfirm: async () => {
      try {
        await workflowDefinitionApi.delete(def.id)
        Message.success('删除成功')
        if (selectedDef.value?.id === def.id) {
          selectedDef.value = null
        }
        await loadDefinitions()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

async function handleToggleDefault(def: WorkflowDefinitionVO) {
  try {
    await workflowDefinitionApi.update(def.id, { isDefault: !def.isDefault })
    Message.success(def.isDefault ? '已取消默认' : '已设为默认')
    await loadDefinitions()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}



function formatRelativeDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  if (diffDays < 7) return `${diffDays} 天前`
  if (diffDays < 30) return `${Math.floor(diffDays / 7)} 周前`
  if (diffDays < 365) return `${Math.floor(diffDays / 30)} 个月前`
  return formatDate(dateStr)
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.main-content {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 0;
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 24px;
}

/* 左侧列表 */
.list-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  border-right: 1px solid var(--tf-border-light);
}

/* 左侧列表 */.def-list {
  display: flex;
  flex-direction: column;
}

.def-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--tf-border-light);
  transition: background 150ms;
}
.def-row:hover {
  background: var(--tf-bg-hover);
}
.def-row.selected {
  background: var(--tf-bg-active);
  border-left: 3px solid var(--tf-accent);
  padding-left: 13px;
}

.def-row-main {
  display: flex;
  align-items: center;
}

.def-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.def-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.def-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.def-row-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-left: 22px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.meta-icon {
  font-size: 12px;
}

/* 右侧详情面板 */
.detail-panel {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  background: var(--tf-bg-surface);
}

.detail-header {
  padding: 16px;
  border-bottom: 1px solid var(--tf-border-light);
}

.detail-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 4px;
}

.detail-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
  word-break: break-word;
}

.more-btn {
  flex-shrink: 0;
}

.detail-desc {
  font-size: 12px;
  color: var(--tf-text-secondary);
  margin: 4px 0 8px;
  line-height: 1.4;
}

.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-bottom: 12px;
}

.detail-actions {
  display: flex;
  gap: 8px;
}

.detail-tabs {
  flex: 1;
  overflow: hidden;
}

.detail-tabs :deep(.arco-tabs-content) {
  overflow-y: auto;
}

.detail-tabs :deep(.arco-tabs-nav) {
  padding: 0 16px;
}

.tab-content {
  padding: 16px;
}

.rules-summary {
  margin-bottom: 12px;
}

.summary-stat {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.stat-number {
  font-size: 24px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.tab-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  line-height: 1.5;
}

.project-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.project-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: 4px;
  transition: background 150ms;
}
.project-item:hover {
  background: var(--tf-bg-hover);
}

.project-name {
  font-size: 12px;
  color: var(--tf-text-primary);
}

.detach-btn {
  opacity: 0;
  transition: opacity 150ms;
}
.project-item:hover .detach-btn {
  opacity: 1;
}

.no-projects {
  padding: 12px 0;
}
.no-projects-text {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.project-actions {
  margin: 12px 0;
}

.auto-attach-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-top: 1px solid var(--tf-border-light);
  margin-top: 12px;
}

.auto-attach-label {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

/* 未选中时的占位 */
.detail-placeholder {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--tf-bg-surface);
}

.placeholder-content {
  text-align: center;
}

.placeholder-icon {
  font-size: 32px;
  color: var(--tf-text-muted);
  margin-bottom: 8px;
}

.placeholder-text {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

/* 弹窗辅助 */
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
