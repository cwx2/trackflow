<template>
  <a-modal
    :visible="visible"
    :footer="false"
    :closable="true"
    :mask-closable="false"
    unmount-on-close
    :width="960"
    modal-class="issue-create-panel-modal"
    @cancel="close"
  >
    <template #title>
      <span class="panel-modal-title">{{ cloneData ? '克隆工单' : '创建工单' }}</span>
    </template>

    <div class="create-panel">
      <!-- 标题输入 -->
      <div class="title-bar">
        <a-input
          v-model="form.title"
          class="title-input"
          placeholder="输入标题"
          :bordered="false"
          size="large"
        />
      </div>

      <div class="create-body">
        <!-- 左侧：编辑区 -->
        <div class="editor-area">
          <RichEditor v-model="form.description" placeholder="在此处键入或粘贴描述" mode="inline" />

          <!-- 附件区域 -->
          <div class="attachment-area">
            <a-upload :auto-upload="false" :show-file-list="true" multiple>
              <template #upload-button>
                <div class="upload-trigger">
                  <icon-attachment />
                  <span>点击以<a-link>浏览</a-link>或将文件拖到此处</span>
                </div>
              </template>
            </a-upload>
          </div>
        </div>

        <!-- 右侧：属性面板 -->
        <div class="props-panel">
          <div class="prop-row">
            <span class="prop-label">项目</span>
            <a-select v-model="form.projectId" placeholder="选择项目" allow-search size="small" :loading="projectLoadState === 'loading'" @change="onProjectChange">
              <template v-if="projectLoadState === 'error'" #empty>
                <div class="select-error-state">
                  <span>加载失败</span>
                  <a-link @click.stop="loadProjects">重试</a-link>
                </div>
              </template>
              <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.key }} - {{ p.name }}</a-option>
            </a-select>
          </div>
          <div class="prop-row">
            <span class="prop-label">类型</span>
            <a-select v-model="form.issueType" size="small">
              <a-option value="Task">任务</a-option>
              <a-option value="Bug">缺陷</a-option>
              <a-option value="Feature">需求</a-option>
              <a-option value="Story">故事</a-option>
            </a-select>
          </div>
          <div class="prop-row">
            <span class="prop-label">优先级</span>
            <a-select v-model="form.priority" size="small">
              <a-option value="Critical"><span class="priority-dot critical"></span>紧急</a-option>
              <a-option value="High"><span class="priority-dot high"></span>高</a-option>
              <a-option value="Normal"><span class="priority-dot normal"></span>普通</a-option>
              <a-option value="Low"><span class="priority-dot low"></span>低</a-option>
            </a-select>
          </div>
          <div class="prop-row">
            <span class="prop-label">Sprint</span>
            <a-select v-model="form.sprintId" placeholder="未排期" size="small" allow-clear>
              <a-option v-for="s in sprints" :key="s.id" :value="s.id">{{ s.name }}</a-option>
            </a-select>
          </div>
          <div class="prop-row">
            <span class="prop-label">负责人</span>
            <a-select v-model="form.assigneeId" placeholder="未分配" size="small" allow-clear allow-search>
              <a-option v-for="m in members" :key="m.userId" :value="m.userId">{{ m.displayName }}</a-option>
            </a-select>
          </div>
          <div class="prop-row">
            <span class="prop-label">截止日期</span>
            <a-date-picker v-model="form.dueDate" size="small" style="width: 100%" placeholder="无" />
          </div>
          <div class="prop-row">
            <span class="prop-label">预估工时</span>
            <a-input-number v-model="form.estimatedHours" size="small" placeholder="0" :min="0" :precision="1" hide-button style="width: 100%">
              <template #suffix>小时</template>
            </a-input-number>
          </div>

          <!-- 自定义字段 -->
          <template v-if="customFields.length > 0">
            <div class="prop-section-divider"></div>
            <div v-for="cf in customFields" :key="cf.id" class="prop-row">
              <span class="prop-label">
                {{ cf.name }}
                <span v-if="cf.isRequired" class="required-mark">*</span>
              </span>
              <!-- string -->
              <a-input
                v-if="cf.fieldFormat === 'string'"
                v-model="customFieldValues[cf.id]"
                size="small"
                :placeholder="cf.defaultValue || ''"
                allow-clear
              />
              <!-- int -->
              <a-input-number
                v-else-if="cf.fieldFormat === 'int'"
                :model-value="customFieldValues[cf.id] ? Number(customFieldValues[cf.id]) : undefined"
                @update:model-value="(v: any) => customFieldValues[cf.id] = v != null ? String(v) : ''"
                size="small"
                :precision="0"
                hide-button
                style="width: 100%"
              />
              <!-- float -->
              <a-input-number
                v-else-if="cf.fieldFormat === 'float'"
                :model-value="customFieldValues[cf.id] ? Number(customFieldValues[cf.id]) : undefined"
                @update:model-value="(v: any) => customFieldValues[cf.id] = v != null ? String(v) : ''"
                size="small"
                hide-button
                style="width: 100%"
              />
              <!-- date -->
              <a-date-picker
                v-else-if="cf.fieldFormat === 'date'"
                v-model="customFieldValues[cf.id]"
                size="small"
                style="width: 100%"
                placeholder="选择日期"
              />
              <!-- bool -->
              <a-switch
                v-else-if="cf.fieldFormat === 'bool'"
                :model-value="customFieldValues[cf.id] === 'true'"
                size="small"
                @change="(v: boolean) => customFieldValues[cf.id] = String(v)"
              />
              <!-- list -->
              <a-select
                v-else-if="cf.fieldFormat === 'list'"
                v-model="customFieldValues[cf.id]"
                size="small"
                placeholder="选择"
                allow-clear
              >
                <a-option v-for="opt in cf.options" :key="opt.id" :value="opt.id">{{ opt.value }}</a-option>
              </a-select>
              <!-- user -->
              <a-select
                v-else-if="cf.fieldFormat === 'user'"
                v-model="customFieldValues[cf.id]"
                size="small"
                placeholder="选择用户"
                allow-clear
                allow-search
              >
                <a-option v-for="m in members" :key="m.userId" :value="m.userId">{{ m.displayName }}</a-option>
              </a-select>
            </div>
          </template>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="panel-footer">
        <a-space>
          <div class="split-button">
            <a-button type="primary" :loading="submitting" :disabled="!canSubmit" class="split-main" @click="submitAndClose">
              创建工单
            </a-button>
            <a-trigger trigger="click" position="br" :popup-visible="splitMenuVisible" @popup-visible-change="(v: boolean) => splitMenuVisible = v">
              <button type="button" class="split-arrow-trigger" :disabled="!canSubmit">
                <icon-down />
              </button>
              <template #content>
                <div class="split-menu">
                  <div class="split-menu-item" @click="onSplitSelect('close')">创建并关闭</div>
                  <div class="split-menu-item" @click="onSplitSelect('continue')">创建并继续</div>
                </div>
              </template>
            </a-trigger>
          </div>
          <a-button @click="close">取消</a-button>
        </a-space>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconDown, IconAttachment } from '@arco-design/web-vue/es/icon'
import { projectApi, issueApi, sprintApi } from '@/api'
import { useProjectList } from '@/composables/useProjectList'
import { useCustomFieldForm } from './composables/useCustomFieldForm'
import RichEditor from './components/RichEditor.vue'

const props = defineProps<{
  visible: boolean
  projectId?: string
  cloneData?: { projectId: string; title: string; description: string; issueType: string; priority: string }
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  created: []
}>()

const submitting = ref(false)
const splitMenuVisible = ref(false)

const { projects, projectLoadState, loadProjects } = useProjectList()
const members = ref<any[]>([])
const sprints = ref<any[]>([])

const form = reactive({
  projectId: undefined as string | undefined,
  title: '',
  description: '',
  issueType: 'Task',
  priority: 'Normal',
  assigneeId: undefined as string | undefined,
  sprintId: undefined as string | undefined,
  dueDate: '',
  estimatedHours: undefined as number | undefined
})

// 自定义字段集成
const projectIdRef = computed(() => form.projectId)
const issueTypeRef = computed(() => form.issueType)
const { fields: customFields, values: customFieldValues, loading: cfLoading, validateRequired: validateCustomFields, getPayload: getCustomFieldPayload, fetchFields: resetCustomFields } = useCustomFieldForm(
  projectIdRef,
  issueTypeRef
)

const canSubmit = computed(() => !!form.projectId && !!form.title.trim())

// 接收外部传入的 projectId
watch(() => props.projectId, (val) => {
  if (val) {
    form.projectId = val
    // 仅在面板可见时加载数据（避免隐藏状态下触发无权限的 API 调用）
    if (props.visible) {
      onProjectChange(val)
    }
  }
}, { immediate: true })

watch(() => props.visible, (val) => {
  if (val) {
    loadProjects()
    // Pre-fill form if clone data is provided
    if (props.cloneData) {
      form.projectId = props.cloneData.projectId
      form.title = props.cloneData.title
      form.description = props.cloneData.description
      form.issueType = props.cloneData.issueType
      form.priority = props.cloneData.priority
      onProjectChange(props.cloneData.projectId)
    }
  }
})

async function onProjectChange(val: any) {
  const pid = val ? String(val) : ''
  if (!pid) { members.value = []; sprints.value = []; return }
  try { const res = await projectApi.listMembers(pid); members.value = res.data || [] } catch { members.value = [] }
  try { const res = await sprintApi.listByProject(pid, { _silent403: true }); sprints.value = (res.data || []).filter((s: any) => s.status !== 'Completed') } catch { sprints.value = [] }
}

function close() {
  emit('update:visible', false)
}

function onSplitSelect(action: string) {
  splitMenuVisible.value = false
  if (action === 'close') submitAndClose()
  else if (action === 'continue') submitAndNew()
}

async function submitAndClose() {
  const success = await doSubmit()
  if (success) {
    emit('created')
    close()
  }
}

async function submitAndNew() {
  const success = await doSubmit()
  if (success) {
    // 保留项目，清空其他字段以便继续创建
    form.title = ''
    form.description = ''
    form.assigneeId = undefined
    form.dueDate = ''
    form.estimatedHours = undefined
    // 重置自定义字段值到默认值
    resetCustomFields()
  }
}

async function doSubmit(): Promise<boolean> {
  if (!canSubmit.value) return false

  // 自定义字段必填校验
  const cfErrors = validateCustomFields()
  if (cfErrors.length > 0) {
    Message.warning(cfErrors[0])
    return false
  }

  submitting.value = true
  try {
    await issueApi.create({
      projectId: form.projectId!,
      title: form.title.trim(),
      description: form.description || undefined,
      issueType: form.issueType,
      priority: form.priority,
      dueDate: form.dueDate || undefined,
      estimatedHours: form.estimatedHours || undefined,
      sprintId: form.sprintId || undefined,
      assigneeId: form.assigneeId || undefined,
      customFields: getCustomFieldPayload()
    })
    Message.success('工单创建成功')
    return true
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
    return false
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (props.visible) loadProjects()
})
</script>

<style scoped>
.create-panel { display: flex; flex-direction: column; height: 70vh; }

.title-bar { padding: 8px 0; border-bottom: 1px solid var(--color-border); flex-shrink: 0; }
.title-input { font-size: 18px; font-weight: 500; }
.title-input :deep(.arco-input) { font-size: 18px; font-weight: 500; }

.create-body { flex: 1; display: flex; overflow: hidden; }

.editor-area { flex: 1; display: flex; flex-direction: column; overflow-y: auto; border-right: 1px solid var(--color-border); }

.attachment-area { padding: 10px 16px; border-top: 1px solid var(--color-border); }
.upload-trigger { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--color-text-3); cursor: pointer; }

.props-panel { width: 250px; flex-shrink: 0; padding: 16px; overflow-y: auto; }
.prop-row { margin-bottom: 14px; }
.prop-label { display: block; font-size: 12px; color: var(--color-text-3); margin-bottom: 4px; }

.priority-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }
.priority-dot.critical { background: #ef4444; }
.priority-dot.high { background: #f59e0b; }
.priority-dot.normal { background: #6366f1; }
.priority-dot.low { background: #64748b; }

.prop-section-divider { height: 1px; background: var(--color-border); margin: 8px 0 12px; }
.required-mark { color: #f85149; margin-left: 2px; }

.panel-footer { display: flex; align-items: center; padding: 10px 0; border-top: 1px solid var(--color-border); flex-shrink: 0; }

.split-button { display: inline-flex; }
.split-button .split-main { border-top-right-radius: 0; border-bottom-right-radius: 0; }
.split-arrow-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  height: 32px;
  border: none;
  border-top-right-radius: var(--border-radius-small, 4px);
  border-bottom-right-radius: var(--border-radius-small, 4px);
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
  border-left: 1px solid rgba(255, 255, 255, 0.3);
  background: rgb(var(--primary-6, 22, 93, 255));
  color: #fff;
  cursor: pointer;
  transition: background-color 100ms;
  font-size: 12px;
}
.split-arrow-trigger:hover { background: rgb(var(--primary-5, 14, 66, 210)); }
.split-arrow-trigger:active { background: rgb(var(--primary-7, 14, 66, 210)); }
.split-arrow-trigger:disabled { opacity: 0.4; cursor: not-allowed; }
.split-arrow-trigger :deep(.arco-icon) { font-size: 12px; }

.split-menu {
  background: var(--color-bg-popup, #fff);
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  padding: 4px 0;
  min-width: 120px;
}
.split-menu-item {
  padding: 6px 12px;
  font-size: 13px;
  cursor: pointer;
  color: var(--color-text-1);
  transition: background-color 100ms;
}
.split-menu-item:hover { background: var(--color-fill-2, #f2f3f5); }
</style>

<style>
/* Global style for the modal (unscoped to target modal wrapper) */
.issue-create-panel-modal .arco-modal-body { padding: 0 20px 16px; }
</style>
