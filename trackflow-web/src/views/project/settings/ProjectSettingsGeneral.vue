<template>
  <div class="settings-general">
    <!-- 归档提示 -->
    <div v-if="isArchived" class="archived-notice">
      <icon-lock class="notice-icon" />
      <span>项目已归档，设置为只读状态</span>
    </div>

    <!-- 基本信息 Section -->
    <div class="settings-section">
      <h3 class="section-title">基本信息</h3>
      <div class="section-content">
        <a-form :model="form" layout="vertical" class="settings-form">
          <a-form-item label="项目名称" required>
            <a-input
              v-model="form.name"
              placeholder="输入项目名称"
              :disabled="!canEdit || isArchived"
              :max-length="100"
            />
          </a-form-item>

          <a-form-item label="项目标识">
            <a-input
              :model-value="project.key"
              disabled
            />
            <template #extra>
              <span class="field-hint">项目标识创建后不可修改，用于工单编号前缀（如 DE4-123）</span>
            </template>
          </a-form-item>

          <a-form-item label="项目描述">
            <a-textarea
              v-model="form.description"
              placeholder="简要描述项目用途和目标"
              :auto-size="{ minRows: 3, maxRows: 6 }"
              :disabled="!canEdit || isArchived"
              :max-length="500"
              show-word-limit
            />
          </a-form-item>

          <a-form-item label="项目负责人">
            <a-select
              v-model="form.leadId"
              placeholder="选择项目负责人..."
              allow-search
              allow-clear
              :disabled="!canEdit || isArchived"
              :loading="membersLoading"
              @focus="loadMembersIfNeeded"
            >
              <a-option v-for="m in memberList" :key="m.userId" :value="m.userId">
                {{ m.displayName || m.username }}
                <span v-if="m.email" class="option-hint">{{ m.email }}</span>
              </a-option>
            </a-select>
            <template #extra>
              <span class="field-hint">只能选择当前项目成员。变更后新负责人将自动升级为项目管理员。</span>
            </template>
          </a-form-item>
        </a-form>

        <!-- 保存按钮 -->
        <div v-if="canEdit && !isArchived" class="form-actions">
          <a-button
            type="primary"
            :loading="saving"
            :disabled="!hasChanges"
            @click="saveBasicInfo"
          >
            保存修改
          </a-button>
          <a-button v-if="hasChanges" @click="resetForm">
            重置
          </a-button>
        </div>
      </div>
    </div>

    <!-- 可见性设置 Section -->
    <div class="settings-section">
      <h3 class="section-title">可见性设置</h3>
      <p class="section-desc">控制哪些用户可以访问此项目</p>
      <div class="section-content">
        <div class="visibility-options">
          <div
            v-for="opt in visibilityOptions"
            :key="opt.value"
            class="visibility-option"
            :class="{
              active: currentVisibility === opt.value,
              disabled: !canEdit || isArchived
            }"
            @click="canEdit && !isArchived && changeVisibility(opt.value)"
          >
            <div class="visibility-option-header">
              <component :is="opt.icon" class="visibility-option-icon" />
              <span class="visibility-option-label">{{ opt.label }}</span>
              <icon-check v-if="currentVisibility === opt.value" class="check-icon" />
            </div>
            <p class="visibility-option-desc">{{ opt.desc }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 危险区域 Section -->
    <div v-if="canEdit" class="settings-section danger-section">
      <h3 class="section-title danger-title">危险区域</h3>
      <div class="section-content">
        <!-- 归档 -->
        <div class="danger-item">
          <div class="danger-info">
            <span class="danger-item-title">{{ isArchived ? '恢复项目' : '归档项目' }}</span>
            <span class="danger-item-desc">
              {{ isArchived
                ? '恢复后项目将重新允许创建和修改工单'
                : '归档后项目变为只读，无法创建或修改工单' }}
            </span>
          </div>
          <a-button
            :type="isArchived ? 'primary' : 'outline'"
            size="small"
            @click="isArchived ? handleRestore() : handleArchive()"
          >
            {{ isArchived ? '恢复项目' : '归档项目' }}
          </a-button>
        </div>

        <!-- 删除 -->
        <div v-if="canDelete" class="danger-item">
          <div class="danger-info">
            <span class="danger-item-title">删除项目</span>
            <span class="danger-item-desc">永久删除项目及其所有数据（工单、Sprint、成员），此操作不可撤销</span>
          </div>
          <a-button type="outline" status="danger" size="small" @click="confirmDelete">
            删除项目
          </a-button>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <a-modal
      v-model:visible="showDeleteDialog"
      title="删除项目"
      ok-text="永久删除"
      cancel-text="取消"
      :ok-loading="deleting"
      :ok-button-props="{ disabled: deleteConfirmKey !== project.key, status: 'danger' }"
      @ok="submitDelete"
    >
      <div class="delete-confirm-content">
        <div class="delete-warning">
          <icon-exclamation-circle-fill class="warning-icon" />
          <span>此操作不可撤销！项目及其所有数据将被永久删除。</span>
        </div>
        <div v-if="deletePreCheckData" class="delete-impact">
          <p class="impact-title">即将删除的数据：</p>
          <ul class="impact-list">
            <li>📋 {{ deletePreCheckData.issueCount }} 个工单
              <span v-if="deletePreCheckData.openIssueCount > 0" class="impact-warn">
                （其中 {{ deletePreCheckData.openIssueCount }} 个未关闭）
              </span>
            </li>
            <li>🏃 {{ deletePreCheckData.sprintCount }} 个 Sprint</li>
            <li>👥 {{ deletePreCheckData.memberCount }} 名成员</li>
            <li v-if="deletePreCheckData.timeEntryCount > 0">⏱️ {{ deletePreCheckData.timeEntryCount }} 条工时记录</li>
          </ul>
        </div>
        <div class="delete-confirm-input">
          <p>请输入项目标识 <strong>{{ project.key }}</strong> 确认删除：</p>
          <a-input v-model="deleteConfirmKey" placeholder="输入项目标识确认" />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  IconLock,
  IconEye,
  IconEyeInvisible,
  IconCheck,
  IconExclamationCircleFill
} from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { ProjectDetailVO, ProjectMemberVO } from '@/api/types'

const props = defineProps<{
  project: ProjectDetailVO
  canEdit: boolean
  isArchived: boolean
}>()

const emit = defineEmits<{
  updated: [project: ProjectDetailVO]
}>()

const router = useRouter()
const authStore = useAuthStore()

// Form state
const form = reactive({
  name: '',
  description: '',
  leadId: undefined as string | undefined
})

const saving = ref(false)
const membersLoading = ref(false)
const memberList = ref<ProjectMemberVO[]>([])
const membersLoaded = ref(false)

// Visibility
const currentVisibility = ref(props.project.visibility)

const visibilityOptions = [
  { value: 'private', label: '私有', desc: '仅项目成员可访问', icon: IconEyeInvisible },
  { value: 'internal', label: '内部', desc: '所有登录用户可查看（非成员为只读）', icon: IconEye },
  { value: 'public', label: '公开', desc: '所有人可查看（包括未登录用户）', icon: IconEye }
]

// Delete
const canDelete = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  // project:delete permission would be needed
  return false
})

const showDeleteDialog = ref(false)
const deleting = ref(false)
const deleteConfirmKey = ref('')
const deletePreCheckData = ref<{
  issueCount: number
  sprintCount: number
  memberCount: number
  openIssueCount: number
  timeEntryCount: number
} | null>(null)

// Initialize form from project
function initForm() {
  form.name = props.project.name
  form.description = props.project.description || ''
  form.leadId = props.project.leadId || undefined
  currentVisibility.value = props.project.visibility
  // Load members for lead selector if project has a lead
  if (props.project.leadId) {
    loadMembersIfNeeded()
  }
}

// Watch project changes
watch(() => props.project, () => {
  initForm()
}, { immediate: true })

// Computed: has changes
const hasChanges = computed(() => {
  return form.name !== props.project.name
    || form.description !== (props.project.description || '')
    || form.leadId !== (props.project.leadId || undefined)
})

function resetForm() {
  initForm()
}

// Load members for lead selector
async function loadMembersIfNeeded() {
  if (membersLoaded.value) return
  membersLoading.value = true
  try {
    const res = await projectApi.listMembers(props.project.key)
    memberList.value = res.data || []
    membersLoaded.value = true
  } catch {
    memberList.value = []
  } finally {
    membersLoading.value = false
  }
}

// Save basic info
async function saveBasicInfo() {
  if (!form.name.trim()) {
    Message.warning('项目名称不能为空')
    return
  }
  saving.value = true
  try {
    const updateData: any = {}
    if (form.name !== props.project.name) updateData.name = form.name
    if (form.description !== (props.project.description || '')) updateData.description = form.description || undefined
    if (form.leadId !== (props.project.leadId || undefined)) updateData.leadId = form.leadId || undefined

    await projectApi.update(props.project.key, updateData)
    Message.success('项目设置已保存')

    // Reload project detail to get updated data
    const detailRes = await projectApi.getDetail(props.project.key)
    emit('updated', detailRes.data)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// Visibility change
async function changeVisibility(value: string) {
  if (currentVisibility.value === value) return

  // Confirm when making more restrictive
  if (value === 'private' && currentVisibility.value !== 'private') {
    Modal.warning({
      title: '确认将项目设为私有',
      content: '设为私有后，所有非项目成员将立即无法访问此项目。确定继续？',
      okText: '确认设为私有',
      cancelText: '取消',
      onOk: async () => {
        await doVisibilityUpdate(value)
      }
    })
    return
  }

  await doVisibilityUpdate(value)
}

async function doVisibilityUpdate(value: string) {
  try {
    await projectApi.update(props.project.key, { visibility: value })
    currentVisibility.value = value as any
    Message.success('可见性已更新')
    // Reload project
    const detailRes = await projectApi.getDetail(props.project.key)
    emit('updated', detailRes.data)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新可见性失败')
  }
}

// Archive / Restore
async function handleArchive() {
  Modal.warning({
    title: '归档项目',
    content: `确定要归档项目「${props.project.name}」？归档后项目变为只读状态，无法创建或修改工单。`,
    okText: '确认归档',
    cancelText: '取消',
    onOk: async () => {
      try {
        await projectApi.archive(props.project.id)
        Message.success('项目已归档')
        const detailRes = await projectApi.getDetail(props.project.id)
        emit('updated', detailRes.data)
      } catch (e: any) {
        Message.error(e.response?.data?.message || '归档失败')
      }
    }
  })
}

async function handleRestore() {
  Modal.warning({
    title: '恢复项目',
    content: `确定要将项目「${props.project.name}」恢复为活跃状态？恢复后项目将重新允许创建和修改工单。`,
    okText: '确认恢复',
    cancelText: '取消',
    onOk: async () => {
      try {
        await projectApi.restore(props.project.id)
        Message.success('项目已恢复为活跃状态')
        const detailRes = await projectApi.getDetail(props.project.id)
        emit('updated', detailRes.data)
      } catch (e: any) {
        Message.error(e.response?.data?.message || '恢复失败')
      }
    }
  })
}

// Delete
async function confirmDelete() {
  try {
    const res = await projectApi.deletePreCheck(props.project.id)
    if (res.code === 0 && res.data) {
      deletePreCheckData.value = res.data
      deleteConfirmKey.value = ''
      showDeleteDialog.value = true
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '无法获取项目信息')
  }
}

async function submitDelete() {
  if (deleteConfirmKey.value !== props.project.key) return
  deleting.value = true
  try {
    await projectApi.delete(props.project.id, deleteConfirmKey.value)
    showDeleteDialog.value = false
    Message.success('项目已永久删除')
    router.push('/projects')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.settings-general {
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

/* Section */
.settings-section {
  margin-bottom: 32px;
  padding-bottom: 32px;
  border-bottom: 1px solid var(--tf-border-light, var(--tf-border));
}

.settings-section:last-child {
  border-bottom: none;
  margin-bottom: 0;
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
  margin: 0 0 16px;
}

.section-content {
  margin-top: 16px;
}

/* Form */
.settings-form :deep(.arco-form-item) {
  margin-bottom: 20px;
}

.settings-form :deep(.arco-form-item-label) {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.field-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.option-hint {
  color: var(--tf-text-tertiary);
  margin-left: 4px;
  font-size: 11px;
}

.form-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

/* Visibility */
.visibility-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.visibility-option {
  padding: 12px 16px;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.visibility-option:hover:not(.disabled) {
  border-color: var(--tf-accent);
  background: var(--tf-bg-hover, rgba(88, 166, 255, 0.04));
}

.visibility-option.active {
  border-color: var(--tf-accent);
  background: var(--tf-bg-hover, rgba(88, 166, 255, 0.06));
}

.visibility-option.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.visibility-option-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.visibility-option-icon {
  font-size: 16px;
  color: var(--tf-text-secondary);
}

.visibility-option-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.check-icon {
  margin-left: auto;
  font-size: 14px;
  color: var(--tf-accent);
}

.visibility-option-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 4px 0 0 24px;
}

/* Danger zone */
.danger-section {
  border: 1px solid var(--color-danger-light-3, #f76560);
  border-radius: 6px;
  padding: 20px;
  margin-top: 16px;
}

.danger-title {
  color: var(--color-danger-6, #f53f3f);
}

.danger-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}

.danger-item + .danger-item {
  border-top: 1px solid var(--tf-border-light, var(--tf-border));
}

.danger-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.danger-item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.danger-item-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* Delete dialog */
.delete-confirm-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.delete-warning {
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

.impact-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.impact-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: var(--tf-text-secondary);
  list-style: none;
}

.impact-list li {
  margin-bottom: 4px;
}

.impact-warn {
  color: var(--color-danger-6, #f53f3f);
  font-size: 12px;
}

.delete-confirm-input p {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0 0 8px;
}
</style>
