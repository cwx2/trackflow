<template>
  <!-- 创建 Sprint 弹窗 -->
  <a-modal v-model:visible="createVisible" title="新建迭代" :width="480" @ok="handleCreate" :ok-loading="creating">
    <a-form :model="createForm" layout="vertical">
      <a-form-item label="名称" required>
        <a-input v-model="createForm.name" placeholder="如：Sprint 25" />
      </a-form-item>
      <a-form-item label="目标">
        <a-textarea v-model="createForm.goal" placeholder="本迭代目标（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
      </a-form-item>
      <a-form-item label="开始日期">
        <a-date-picker v-model="createForm.startDate" style="width: 100%" />
      </a-form-item>
      <a-form-item label="结束日期">
        <a-date-picker v-model="createForm.endDate" style="width: 100%" />
      </a-form-item>

      <div class="create-options-section" v-if="creationPreview">
        <div class="create-option-item" v-if="creationPreview.activeSprintId && creationPreview.unresolvedIssueCount > 0">
          <a-checkbox v-model="createForm.moveUnresolvedIssues">
            <span class="option-label">添加当前 Sprint 未完成工单</span>
          </a-checkbox>
          <span class="option-desc">
            将 <strong>{{ creationPreview.activeSprintName }}</strong> 中的
            {{ creationPreview.unresolvedIssueCount }} 个未完成工单移入新迭代
          </span>
        </div>
        <div class="create-option-item">
          <a-checkbox v-model="createForm.setAsDefault">
            <span class="option-label">设为默认 Sprint</span>
          </a-checkbox>
          <span class="option-desc">
            <template v-if="creationPreview.hasDefaultSprint">
              当前默认为 <strong>{{ creationPreview.defaultSprintName }}</strong>，替换后新建工单将自动归属此迭代
            </template>
            <template v-else>
              启用后，该项目新创建的工单将自动分配到此迭代
            </template>
          </span>
        </div>
      </div>
    </a-form>
  </a-modal>

  <!-- 编辑 Sprint 弹窗 -->
  <a-modal v-model:visible="editVisible" title="编辑迭代" :width="480" @ok="handleUpdate" :ok-loading="updating" ok-text="保存修改">
    <a-form :model="editForm" layout="vertical">
      <a-form-item label="名称" required>
        <a-input v-model="editForm.name" placeholder="迭代名称" />
      </a-form-item>
      <a-form-item label="目标">
        <a-textarea v-model="editForm.goal" placeholder="迭代目标（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
      </a-form-item>
      <a-form-item label="开始日期">
        <a-date-picker v-model="editForm.startDate" style="width: 100%" />
      </a-form-item>
      <a-form-item label="结束日期">
        <a-date-picker v-model="editForm.endDate" style="width: 100%" />
      </a-form-item>
    </a-form>
    <template #footer>
      <div class="edit-modal-footer">
        <div class="edit-modal-footer-left">
          <a-button v-if="canArchive" size="small" type="secondary" @click="$emit('archive', editingSprint!)">归档</a-button>
          <a-button v-if="canRestore" size="small" type="secondary" @click="$emit('restore', editingSprint!)">恢复</a-button>
          <a-button v-if="canDelete" size="small" status="danger" type="secondary" @click="$emit('delete', editingSprint!)">删除</a-button>
        </div>
        <div class="edit-modal-footer-right">
          <a-button size="small" @click="editVisible = false">取消</a-button>
          <a-button size="small" type="primary" :loading="updating" @click="handleUpdate">保存修改</a-button>
        </div>
      </div>
    </template>
  </a-modal>

  <!-- 日期重叠确认弹窗 -->
  <a-modal
    v-model:visible="showOverlapConfirm"
    title="日期重叠警告"
    :width="520"
    ok-text="确认继续"
    cancel-text="取消"
    @ok="confirmOverlapAndProceed"
    @cancel="showOverlapConfirm = false"
  >
    <div class="overlap-warning-content">
      <div class="overlap-warning-header">
        <span class="overlap-warning-icon">⚠️</span>
        <span class="overlap-warning-title">
          {{ overlapContext === 'create' ? '新建迭代' : '修改后的迭代' }}日期与以下已有迭代存在重叠：
        </span>
      </div>
      <div class="overlap-sprint-list" v-if="overlapWarning">
        <div v-for="(sprint, index) in overlapWarning.overlappingSprints" :key="index" class="overlap-sprint-item">
          <span class="overlap-sprint-name">{{ sprint.name }}</span>
          <span class="overlap-sprint-dates">{{ sprint.startDate }} ~ {{ sprint.endDate }}</span>
          <span class="overlap-sprint-status" :class="sprint.status">
            {{ sprint.status === 'active' ? '进行中' : '计划中' }}
          </span>
        </div>
      </div>
      <div class="overlap-warning-hint">
        <p>重叠的迭代可能影响"当前 Sprint"的自动检测和工单归属。</p>
        <p>如果确定要继续，请点击"确认继续"。</p>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { sprintApi } from '@/api'
import { ERROR_CODES } from '@/api/error-codes'
import type { SprintVO, CreationPreviewVO, SprintOverlapWarning } from '@/api/types'

const props = defineProps<{
  showCreate: boolean
  showEdit: boolean
  editingSprint: SprintVO | null
  projectId: string | null | undefined
  canEditFn: (sprint: SprintVO) => boolean
  canDeleteFn: (sprint: SprintVO) => boolean
}>()

const emit = defineEmits<{
  'update:showCreate': [val: boolean]
  'update:showEdit': [val: boolean]
  created: []
  updated: []
  archive: [sprint: SprintVO]
  restore: [sprint: SprintVO]
  delete: [sprint: SprintVO]
}>()

const createVisible = computed({
  get: () => props.showCreate,
  set: (val) => emit('update:showCreate', val)
})

const editVisible = computed({
  get: () => props.showEdit,
  set: (val) => emit('update:showEdit', val)
})

const creating = ref(false)
const updating = ref(false)
const creationPreview = ref<CreationPreviewVO | null>(null)

const createForm = reactive({
  name: '',
  goal: '',
  startDate: '',
  endDate: '',
  moveUnresolvedIssues: false,
  setAsDefault: false
})

const editForm = reactive({
  name: '',
  goal: '',
  startDate: '',
  endDate: ''
})

// Overlap
const showOverlapConfirm = ref(false)
const overlapWarning = ref<SprintOverlapWarning | null>(null)
const overlapContext = ref<'create' | 'edit'>('create')

// Edit modal footer permissions
const canArchive = computed(() => props.editingSprint && props.canEditFn(props.editingSprint) && props.editingSprint.status !== 'archived')
const canRestore = computed(() => props.editingSprint && props.canEditFn(props.editingSprint) && props.editingSprint.status === 'archived')
const canDelete = computed(() => props.editingSprint && props.canDeleteFn(props.editingSprint))

// Reset create form when modal opens
watch(() => props.showCreate, async (val) => {
  if (val) {
    createForm.name = ''
    createForm.goal = ''
    createForm.startDate = ''
    createForm.endDate = ''
    createForm.moveUnresolvedIssues = false
    createForm.setAsDefault = false
    creationPreview.value = null
    if (props.projectId) {
      try {
        const res = await sprintApi.creationPreview(props.projectId)
        creationPreview.value = res.data
      } catch { creationPreview.value = null }
    }
  }
})

// Populate edit form when modal opens
watch(() => props.showEdit, (val) => {
  if (val && props.editingSprint) {
    editForm.name = props.editingSprint.name
    editForm.goal = props.editingSprint.goal || ''
    editForm.startDate = props.editingSprint.startDate || ''
    editForm.endDate = props.editingSprint.endDate || ''
  }
})

async function handleCreate() {
  if (!createForm.name.trim()) { Message.warning('请输入迭代名称'); return }
  if (createForm.startDate && createForm.endDate && createForm.startDate >= createForm.endDate) {
    Message.warning('开始日期必须早于结束日期'); return
  }
  await doCreate(false)
}

async function doCreate(confirmOverlap: boolean) {
  creating.value = true
  try {
    await sprintApi.create(props.projectId!, {
      name: createForm.name.trim(),
      goal: createForm.goal || undefined,
      startDate: createForm.startDate || undefined,
      endDate: createForm.endDate || undefined,
      moveUnresolvedIssues: createForm.moveUnresolvedIssues || undefined,
      setAsDefault: createForm.setAsDefault || undefined,
      confirmOverlap: confirmOverlap || undefined
    })
    Message.success('迭代创建成功')
    createVisible.value = false
    emit('created')
  } catch (e: any) {
    const code = e.response?.data?.code
    if (code === ERROR_CODES.SPRINT_DATE_OVERLAP) {
      overlapWarning.value = e.response.data.data as SprintOverlapWarning
      overlapContext.value = 'create'
      showOverlapConfirm.value = true
    } else {
      Message.error(e.response?.data?.message || '创建失败')
    }
  } finally {
    creating.value = false
  }
}

async function handleUpdate() {
  if (!editForm.name.trim()) { Message.warning('请输入迭代名称'); return }
  if (editForm.startDate && editForm.endDate && editForm.startDate >= editForm.endDate) {
    Message.warning('开始日期必须早于结束日期'); return
  }
  await doUpdate(false)
}

async function doUpdate(confirmOverlap: boolean) {
  if (!props.editingSprint) return
  updating.value = true
  try {
    const data: Record<string, any> = { name: editForm.name.trim(), goal: editForm.goal || '' }

    if (!editForm.startDate) {
      if (props.editingSprint.startDate) data.clearStartDate = true
    } else {
      data.startDate = editForm.startDate
    }

    if (!editForm.endDate) {
      if (props.editingSprint.endDate) data.clearEndDate = true
    } else {
      data.endDate = editForm.endDate
    }

    if (confirmOverlap) data.confirmOverlap = true

    await sprintApi.update(props.editingSprint.id, data)
    Message.success('迭代更新成功')
    editVisible.value = false
    emit('updated')
  } catch (e: any) {
    const code = e.response?.data?.code
    if (code === ERROR_CODES.SPRINT_DATE_OVERLAP) {
      overlapWarning.value = e.response.data.data as SprintOverlapWarning
      overlapContext.value = 'edit'
      showOverlapConfirm.value = true
    } else {
      Message.error(e.response?.data?.message || '更新失败')
    }
  } finally {
    updating.value = false
  }
}

function confirmOverlapAndProceed() {
  showOverlapConfirm.value = false
  if (overlapContext.value === 'create') doCreate(true)
  else doUpdate(true)
}
</script>

<style scoped>
.create-options-section { display: flex; flex-direction: column; gap: 12px; margin-top: 4px; padding-top: 16px; border-top: 1px solid var(--color-border); }
.create-option-item { display: flex; flex-direction: column; gap: 4px; }
.create-option-item .option-label { font-size: 13px; font-weight: 500; color: var(--color-text-1); }
.create-option-item .option-desc { font-size: 12px; color: var(--color-text-3); margin-left: 24px; line-height: 1.5; }

.edit-modal-footer { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.edit-modal-footer-left { display: flex; gap: 8px; }
.edit-modal-footer-right { display: flex; gap: 8px; }

.overlap-warning-content { display: flex; flex-direction: column; gap: 16px; }
.overlap-warning-header { display: flex; align-items: flex-start; gap: 8px; }
.overlap-warning-icon { font-size: 18px; line-height: 1.4; }
.overlap-warning-title { font-size: 13px; color: var(--color-text-1); line-height: 1.5; }
.overlap-sprint-list { display: flex; flex-direction: column; gap: 8px; padding: 12px; background: var(--color-fill-1); border-radius: 6px; }
.overlap-sprint-item { display: flex; align-items: center; gap: 12px; padding: 6px 8px; background: var(--color-bg-2); border-radius: 4px; }
.overlap-sprint-name { font-size: 13px; font-weight: 500; color: var(--color-text-1); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.overlap-sprint-dates { font-size: 12px; color: var(--color-text-3); white-space: nowrap; }
.overlap-sprint-status { font-size: 11px; padding: 1px 6px; border-radius: 3px; white-space: nowrap; }
.overlap-sprint-status.active { color: var(--color-success-6); background: var(--color-success-1); }
.overlap-sprint-status.planned { color: var(--color-primary-6); background: var(--color-primary-1); }
.overlap-warning-hint { font-size: 12px; color: var(--color-text-3); line-height: 1.6; }
.overlap-warning-hint p { margin: 0; }
</style>
