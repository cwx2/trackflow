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
      <span class="panel-modal-title">{{ cloneData ? '克隆工单' : parentId ? '创建子工单' : draftId ? '继续编辑草稿' : '创建工单' }}</span>
      <a-tooltip v-if="!isFullPage" content="在全屏页面中查看" position="bottom" mini>
        <span class="fullscreen-btn" @click.stop="goFullscreen">
          <icon-fullscreen />
        </span>
      </a-tooltip>
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

      <!-- 模板选择器 -->
      <div v-if="templates.length > 0 && !cloneData" class="template-bar">
        <span class="template-label">模板</span>
        <div class="template-chips">
          <div
            v-for="t in templates"
            :key="t.id"
            class="template-chip"
            :class="{ active: selectedTemplateId === t.id }"
            @click="applyTemplate(t)"
          >
            {{ t.name }}
          </div>
          <div
            v-if="selectedTemplateId"
            class="template-chip template-chip-clear"
            @click="clearTemplate"
          >
            ✕ 清除模板
          </div>
        </div>
      </div>

      <div class="create-body">
        <!-- 左侧：编辑区 -->
        <div class="editor-area">
          <RichEditor v-model="form.description" placeholder="在此处键入或粘贴描述" mode="inline" />

          <!-- 附件区域 -->
          <div class="attachment-area" ref="attachmentAreaRef">
            <a-upload
              :auto-upload="false"
              :show-file-list="false"
              multiple
              :accept="'image/*,video/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.tar,.gz,.7z'"
              @change="onUploadChange"
            >
              <template #upload-button>
                <div class="upload-trigger">
                  <icon-attachment />
                  <span>点击以<a-link>浏览</a-link>或将文件拖到此处，或 <kbd>Ctrl+V</kbd> 粘贴截图</span>
                </div>
              </template>
            </a-upload>
            <!-- 附件文件列表 -->
            <div v-if="attachmentFiles.length > 0" class="attachment-file-list">
              <div v-for="(file, idx) in attachmentFiles" :key="idx" class="attachment-file-item">
                <span class="attachment-file-icon">{{ getFileIcon(file.name) }}</span>
                <span class="attachment-file-name">{{ file.name }}</span>
                <span class="attachment-file-size">{{ formatFileSize(file.size) }}</span>
                <icon-close class="attachment-file-remove" @click="removeAttachment(idx)" />
              </div>
            </div>
            <!-- 粘贴提示（无文件时显示） -->
            <div v-if="pasteHint" class="paste-hint">{{ pasteHint }}</div>
          </div>

          <!-- 关联工单区域 -->
          <div class="link-issue-area">
            <div class="link-issue-header" @click="showLinkSection = !showLinkSection">
              <icon-link />
              <span class="link-issue-title">Link issue</span>
              <icon-down v-if="!showLinkSection" style="margin-left: auto; font-size: 12px" />
              <icon-up v-else style="margin-left: auto; font-size: 12px" />
            </div>
            <div v-if="showLinkSection" class="link-issue-body">
              <!-- 已添加的关联列表 -->
              <div v-if="linkedIssues.length > 0" class="linked-list">
                <div v-for="(link, idx) in linkedIssues" :key="idx" class="linked-item">
                  <span class="link-type-label">{{ getLinkTypeLabel(link.linkType) }}</span>
                  <span class="linked-issue-key">{{ link.issueKey }}</span>
                  <span class="linked-issue-title">{{ link.title }}</span>
                  <icon-close class="remove-link-btn" @click="removeLink(idx)" />
                </div>
              </div>
              <!-- 添加新关联 -->
              <div class="add-link-row">
                <a-select v-model="newLinkType" size="small" placeholder="关联类型" style="width: 140px">
                  <a-option value="relates_to">relates to</a-option>
                  <a-option value="blocks">blocks</a-option>
                  <a-option value="blocked_by">is blocked by</a-option>
                  <a-option value="duplicates">duplicates</a-option>
                  <a-option value="duplicated_by">is duplicated by</a-option>
                  <a-option value="parent_of">parent of</a-option>
                  <a-option value="child_of">subtask of</a-option>
                </a-select>
                <a-select
                  v-model="newLinkTargetId"
                  size="small"
                  placeholder="搜索工单..."
                  allow-search
                  :filter-option="false"
                  :loading="linkSearchLoading"
                  style="flex: 1"
                  @search="onLinkSearch"
                >
                  <a-option v-for="item in linkSearchResults" :key="item.id" :value="item.id">
                    {{ item.issueKey }} - {{ item.title }}
                  </a-option>
                </a-select>
                <a-button size="small" type="text" :disabled="!newLinkType || !newLinkTargetId" @click="addLink">
                  <icon-plus />
                </a-button>
              </div>
            </div>
          </div>

          <!-- 相似工单区域 -->
          <div v-if="similarIssues.length > 0 || similarLoading" class="similar-issues-area">
            <div class="similar-issues-header">
              <icon-search />
              <span class="similar-issues-title">相似工单</span>
              <a-spin v-if="similarLoading" :size="12" style="margin-left: 4px" />
              <span v-else class="similar-issues-count">{{ similarIssues.length }}</span>
            </div>
            <div class="similar-issues-list">
              <div
                v-for="item in similarIssues"
                :key="item.id"
                class="similar-issue-item"
                @click="openSimilarIssue(item)"
              >
                <span class="similar-issue-key">{{ item.issueKey }}</span>
                <span class="similar-issue-title">{{ item.title }}</span>
                <span
                  v-if="item.statusName"
                  class="similar-issue-status"
                  :style="{ backgroundColor: item.statusColor ? item.statusColor + '20' : undefined, color: item.statusColor || undefined }"
                >{{ item.statusName }}</span>
                <span v-if="item.assigneeName" class="similar-issue-assignee">{{ item.assigneeName }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：属性面板 -->
        <div class="props-panel">
          <div class="prop-row">
            <span class="prop-label">项目</span>
            <a-select v-model="form.projectId" placeholder="选择项目" allow-search size="small" :loading="projectLoadState === 'loading'" :disabled="lockSprint" @change="onProjectChange">
              <template v-if="projectLoadState === 'error'" #empty>
                <div class="select-error-state">
                  <span>加载失败</span>
                  <a-link @click.stop="loadProjects">重试</a-link>
                </div>
              </template>
              <a-option v-for="p in projects" :key="p.id" :value="p.id">
                <span v-if="p.favorited" style="color: #d29922; margin-right: 4px; font-size: 10px">★</span>{{ p.key }} - {{ p.name }}
              </a-option>
            </a-select>
          </div>
          <div class="prop-row">
            <span class="prop-label">类型</span>
            <a-select v-model="form.issueType" size="small">
              <a-option v-for="(label, value) in issueTypeLabelMap" :key="value" :value="value">{{ label }}</a-option>
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
            <span class="prop-label">状态</span>
            <a-select v-model="form.statusId" size="small" allow-clear placeholder="默认初始状态">
              <a-option v-for="s in statuses" :key="s.id" :value="s.id">
                <span class="status-dot" :style="{ backgroundColor: s.color || '#6b7280' }"></span>{{ s.name }}
              </a-option>
            </a-select>
          </div>
          <div class="prop-row">
            <span class="prop-label">Sprint</span>
            <a-select v-model="form.sprintId" :placeholder="form.projectId ? '未排期' : '请先选择项目'" size="small" allow-clear :disabled="!form.projectId || lockSprint">
              <a-option v-for="s in sprints" :key="s.id" :value="s.id">{{ s.name }}</a-option>
            </a-select>
          </div>
          <div class="prop-row">
            <span class="prop-label">负责人</span>
            <a-select v-model="form.assigneeId" :placeholder="form.projectId ? '未分配' : '请先选择项目'" size="small" allow-clear allow-search :disabled="!form.projectId">
              <a-option v-for="m in members" :key="m.userId" :value="m.userId">{{ m.displayName }}</a-option>
            </a-select>
          </div>
          <div class="prop-row">
            <span class="prop-label">标签</span>
            <a-select
              v-model="form.tagIds"
              :placeholder="form.projectId ? '选择标签（可多选）' : '请先选择项目'"
              size="small"
              multiple
              allow-clear
              allow-search
              :disabled="!form.projectId"
            >
              <a-option v-for="tag in projectTags" :key="tag.id" :value="tag.id">
                <span class="tag-color-dot" :style="{ backgroundColor: tag.color || '#808080' }"></span>{{ tag.name }}
              </a-option>
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
                <span v-if="cf.effectiveIsRequired ?? cf.isRequired" class="required-mark">*</span>
              </span>
              <!-- string -->
              <a-input
                v-if="cf.fieldFormat === 'string'"
                v-model="customFieldValues[cf.id]"
                size="small"
                :placeholder="getFieldPlaceholder(cf)"
                :class="{ 'field-error': cfValidationErrors[cf.id] }"
                allow-clear
                @input="clearFieldError(cf.id)"
              />
              <!-- text (多行/Markdown) -->
              <a-textarea
                v-else-if="cf.fieldFormat === 'text'"
                v-model="customFieldValues[cf.id]"
                size="small"
                :placeholder="getFieldPlaceholder(cf)"
                :class="{ 'field-error': cfValidationErrors[cf.id] }"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                allow-clear
                @input="clearFieldError(cf.id)"
              />
              <!-- int -->
              <a-input-number
                v-else-if="cf.fieldFormat === 'int'"
                :model-value="customFieldValues[cf.id] ? Number(customFieldValues[cf.id]) : undefined"
                @update:model-value="(v: any) => { customFieldValues[cf.id] = v != null ? String(v) : ''; clearFieldError(cf.id) }"
                size="small"
                :placeholder="getFieldPlaceholder(cf)"
                :class="{ 'field-error': cfValidationErrors[cf.id] }"
                :precision="0"
                hide-button
                style="width: 100%"
              />
              <!-- float -->
              <a-input-number
                v-else-if="cf.fieldFormat === 'float'"
                :model-value="customFieldValues[cf.id] ? Number(customFieldValues[cf.id]) : undefined"
                @update:model-value="(v: any) => { customFieldValues[cf.id] = v != null ? String(v) : ''; clearFieldError(cf.id) }"
                size="small"
                :placeholder="getFieldPlaceholder(cf)"
                :class="{ 'field-error': cfValidationErrors[cf.id] }"
                hide-button
                style="width: 100%"
              />
              <!-- date -->
              <a-date-picker
                v-else-if="cf.fieldFormat === 'date'"
                v-model="customFieldValues[cf.id]"
                size="small"
                style="width: 100%"
                :placeholder="getFieldPlaceholder(cf)"
                :class="{ 'field-error': cfValidationErrors[cf.id] }"
                @change="clearFieldError(cf.id)"
              />
              <!-- datetime -->
              <a-date-picker
                v-else-if="cf.fieldFormat === 'datetime'"
                v-model="customFieldValues[cf.id]"
                size="small"
                style="width: 100%"
                show-time
                format="YYYY-MM-DDTHH:mm:ss"
                :placeholder="getFieldPlaceholder(cf)"
                :class="{ 'field-error': cfValidationErrors[cf.id] }"
                @change="clearFieldError(cf.id)"
              />
              <!-- bool -->
              <a-switch
                v-else-if="cf.fieldFormat === 'bool'"
                :model-value="customFieldValues[cf.id] === 'true'"
                size="small"
                @change="(v: any) => { customFieldValues[cf.id] = String(v); clearFieldError(cf.id) }"
              />
              <!-- list (多值模式) -->
              <a-select
                v-else-if="cf.fieldFormat === 'list' && cf.isMulti"
                :model-value="customFieldValues[cf.id] ? customFieldValues[cf.id].split(',').filter((s: string) => s) : []"
                @update:model-value="(v: any) => { customFieldValues[cf.id] = (v as string[]).join(','); clearFieldError(cf.id) }"
                size="small"
                :placeholder="getFieldPlaceholder(cf)"
                :class="{ 'field-error': cfValidationErrors[cf.id] }"
                multiple
                allow-clear
              >
                <a-option v-for="opt in getFilteredOptionsForField(cf)" :key="opt.id" :value="opt.id">{{ opt.value }}</a-option>
                <template #footer v-if="canAddFieldOption">
                  <div class="select-add-option" v-if="addingOptionFieldId !== cf.id" @click.stop="startAddOptionInSelect(cf.id)">
                    <span class="add-icon">+</span> 添加新值
                  </div>
                  <div class="select-add-input" v-else @click.stop>
                    <input v-model="newOptionInput" class="add-opt-field" placeholder="输入新值" @keyup.enter="confirmAddOptionInSelect(cf)" @keyup.escape="cancelAddOptionInSelect()" />
                    <button class="add-opt-btn" :disabled="!newOptionInput.trim()" @click="confirmAddOptionInSelect(cf)">添加</button>
                  </div>
                </template>
              </a-select>
              <!-- list (单值模式) -->
              <a-select
                v-else-if="cf.fieldFormat === 'list'"
                v-model="customFieldValues[cf.id]"
                size="small"
                :placeholder="getFieldPlaceholder(cf)"
                :class="{ 'field-error': cfValidationErrors[cf.id] }"
                allow-clear
                @change="clearFieldError(cf.id)"
              >
                <a-option v-for="opt in getFilteredOptionsForField(cf)" :key="opt.id" :value="opt.id">{{ opt.value }}</a-option>
                <template #footer v-if="canAddFieldOption">
                  <div class="select-add-option" v-if="addingOptionFieldId !== cf.id" @click.stop="startAddOptionInSelect(cf.id)">
                    <span class="add-icon">+</span> 添加新值
                  </div>
                  <div class="select-add-input" v-else @click.stop>
                    <input v-model="newOptionInput" class="add-opt-field" placeholder="输入新值" @keyup.enter="confirmAddOptionInSelect(cf)" @keyup.escape="cancelAddOptionInSelect()" />
                    <button class="add-opt-btn" :disabled="!newOptionInput.trim()" @click="confirmAddOptionInSelect(cf)">添加</button>
                  </div>
                </template>
              </a-select>
              <!-- user -->
              <a-select
                v-else-if="cf.fieldFormat === 'user'"
                v-model="customFieldValues[cf.id]"
                size="small"
                :placeholder="form.projectId ? getFieldPlaceholder(cf) : '请先选择项目'"
                :class="{ 'field-error': cfValidationErrors[cf.id] }"
                :disabled="!form.projectId"
                allow-clear
                allow-search
                @change="clearFieldError(cf.id)"
              >
                <a-option v-for="m in members" :key="m.userId" :value="m.userId">{{ m.displayName }}</a-option>
              </a-select>
              <!-- inline error message -->
              <span v-if="cfValidationErrors[cf.id]" class="field-error-msg">{{ cfValidationErrors[cf.id] }}</span>
            </div>
          </template>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="panel-footer">
        <a-space>
          <div class="split-button">

            <a-button type="primary" :loading="submitting" :disabled="!canSubmit" class="split-main" @click="executeDefaultAction">
              {{ defaultActionLabel }}
            </a-button>
            <a-trigger trigger="click" position="br" :popup-visible="splitMenuVisible" @popup-visible-change="(v: boolean) => splitMenuVisible = v">
              <button type="button" class="split-arrow-trigger" :disabled="!canSubmit">
                <icon-down />
              </button>
              <template #content>
                <div class="split-menu">
                  <div class="split-menu-item" :class="{ active: defaultCreateMode === 'close' }" @click="onSplitSelect('close')">
                    <icon-check v-if="defaultCreateMode === 'close'" class="split-menu-check" />
                    创建工单
                  </div>
                  <div class="split-menu-item" :class="{ active: defaultCreateMode === 'continue' }" @click="onSplitSelect('continue')">
                    <icon-check v-if="defaultCreateMode === 'continue'" class="split-menu-check" />
                    创建并继续
                  </div>
                  <div class="split-menu-item" :class="{ active: defaultCreateMode === 'copy' }" @click="onSplitSelect('copy')">
                    <icon-check v-if="defaultCreateMode === 'copy'" class="split-menu-check" />
                    创建并复制
                  </div>
                </div>
              </template>
            </a-trigger>
          </div>
          <a-button @click="close">取消</a-button>
          <a-button v-if="isDirty" type="text" status="danger" @click="discardDraft">丢弃</a-button>
        </a-space>
        <span class="shortcut-hint">{{ isMac ? '⌘' : 'Ctrl' }}+Enter 快速创建</span>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { IconDown, IconAttachment, IconClose, IconPlus, IconUp, IconLink, IconSearch, IconCheck, IconFullscreen } from '@arco-design/web-vue/es/icon'
import { projectApi, issueApi, sprintApi, customFieldApi, issueTemplateApi, tagApi } from '@/api'
import { useProjectList } from '@/composables/useProjectList'
import { usePermission } from '@/composables/usePermission'
import { useCustomFieldForm } from './composables/useCustomFieldForm'
import { useDrafts } from './composables/useDrafts'
import { onSessionEvent, saveSessionRecoveryDraft } from '@/utils/sessionEvents'
import RichEditor from './components/RichEditor.vue'
import { issueTypeLabelMap } from '@/utils/fieldLabels'
import type { CustomFieldDefinitionVO, IssueTemplateVO, FilterRule, IssueStatusVO, IssueVO as SimilarIssue } from '@/api/types'

const props = defineProps<{
  visible: boolean
  projectId?: string
  sprintId?: string | null
  lockSprint?: boolean
  parentId?: string | null
  cloneData?: { projectId: string; title: string; description: string; issueType: string; priority: string }
  draftId?: string | null
  /** 是否以全屏页面模式运行（true 时隐藏全屏按钮） */
  isFullPage?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  created: []
  'cancel-with-data': [formData: any]
  /** 用户点击全屏按钮，携带当前表单数据 */
  'expand-to-fullscreen': [formData: any]
}>()

const router = useRouter()

const submitting = ref(false)
const splitMenuVisible = ref(false)

// ========== 附件管理 ==========
const attachmentAreaRef = ref<HTMLElement | null>(null)
const attachmentFiles = ref<File[]>([])
const pasteHint = ref<string>('')
let pasteHintTimer: any = null

/** 处理 a-upload change 事件（手动选择或拖拽） */
function onUploadChange(fileList: any, file: any) {
  // Arco Upload fileList 是 FileItem[]，从 raw 取 File 对象
  if (file?.raw instanceof File) {
    attachmentFiles.value.push(file.raw)
  }
}

/** 从附件列表删除 */
function removeAttachment(idx: number) {
  attachmentFiles.value.splice(idx, 1)
}

/** 格式化文件大小 */
function formatFileSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

/** 根据文件名返回 emoji 图标 */
function getFileIcon(name: string): string {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) return '🖼️'
  if (['mp4', 'mov', 'avi', 'mkv'].includes(ext)) return '🎬'
  if (ext === 'pdf') return '📄'
  if (['doc', 'docx'].includes(ext)) return '📝'
  if (['xls', 'xlsx'].includes(ext)) return '📊'
  if (['ppt', 'pptx'].includes(ext)) return '📋'
  if (['zip', 'tar', 'gz', '7z'].includes(ext)) return '📦'
  return '📎'
}

/** 粘贴事件处理：从剪贴板获取图片文件 */
function handlePaste(e: ClipboardEvent) {
  // 仅在弹窗可见时处理
  if (!props.visible) return

  const items = e.clipboardData?.items
  if (!items) return

  let hasImage = false
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (!file) continue
      hasImage = true
      // 生成自动文件名（时间戳格式，类似 YouTrack）
      const timestamp = new Date().toISOString().replace(/[-:T.]/g, '').slice(0, 14)
      const ext = item.type.split('/')[1] || 'png'
      const renamedFile = new File([file], `screenshot_${timestamp}.${ext}`, { type: item.type })
      attachmentFiles.value.push(renamedFile)
    }
  }

  if (hasImage) {
    // 阻止默认的 paste 行为（避免图片被粘贴到 RichEditor 或其他地方）
    // 注意：仅在焦点不在 RichEditor 时阻止
    const activeEl = document.activeElement
    const isInEditor = activeEl?.closest('.tiptap-body') != null
    if (!isInEditor) {
      e.preventDefault()
    }
    showPasteHint('✅ 截图已添加到附件列表')
  } else if (e.clipboardData?.items?.length && !Array.from(e.clipboardData.items).some(i => i.kind === 'string')) {
    // 剪贴板有内容但不是图片
    showPasteHint('⚠️ 仅支持粘贴图片文件')
  }
}

function showPasteHint(msg: string) {
  pasteHint.value = msg
  if (pasteHintTimer) clearTimeout(pasteHintTimer)
  pasteHintTimer = setTimeout(() => {
    pasteHint.value = ''
  }, 3000)
}

// 创建模式记忆（localStorage 持久化）
type CreateMode = 'close' | 'continue' | 'copy'
const CREATE_MODE_KEY = 'trackflow:create-mode'
const defaultCreateMode = ref<CreateMode>(
  (localStorage.getItem(CREATE_MODE_KEY) as CreateMode) || 'close'
)

const defaultActionLabel = computed(() => {
  switch (defaultCreateMode.value) {
    case 'continue': return '创建并继续'
    case 'copy': return '创建并复制'
    default: return '创建工单'
  }
})

// Link issue 状态
const showLinkSection = ref(false)
const newLinkType = ref<string>('relates_to')
const newLinkTargetId = ref<string | undefined>(undefined)
const linkSearchLoading = ref(false)
const linkSearchResults = ref<any[]>([])
const linkedIssues = ref<Array<{ targetIssueId: string; linkType: string; issueKey: string; title: string }>>([])

const linkTypeLabels: Record<string, string> = {
  relates_to: 'relates to',
  blocks: 'blocks',
  blocked_by: 'is blocked by',
  duplicates: 'duplicates',
  duplicated_by: 'is duplicated by',
  parent_of: 'parent of',
  child_of: 'subtask of'
}

function getLinkTypeLabel(type: string): string {
  return linkTypeLabels[type] || type
}

let linkSearchTimer: any = null
async function onLinkSearch(keyword: string) {
  if (!keyword || keyword.length < 2) {
    linkSearchResults.value = []
    return
  }
  if (linkSearchTimer) clearTimeout(linkSearchTimer)
  linkSearchTimer = setTimeout(async () => {
    linkSearchLoading.value = true
    try {
      const res = await issueApi.list({ keyword, pageSize: 10, projectId: form.projectId })
      linkSearchResults.value = res.data?.list || []
    } catch {
      linkSearchResults.value = []
    } finally {
      linkSearchLoading.value = false
    }
  }, 300)
}

function addLink() {
  if (!newLinkType.value || !newLinkTargetId.value) return
  // 避免重复添加
  const exists = linkedIssues.value.some(l => l.targetIssueId === newLinkTargetId.value && l.linkType === newLinkType.value)
  if (exists) {
    Message.warning('该关联已添加')
    return
  }
  // 从搜索结果中找到对应工单信息
  const target = linkSearchResults.value.find(i => i.id === newLinkTargetId.value)
  if (target) {
    linkedIssues.value.push({
      targetIssueId: newLinkTargetId.value,
      linkType: newLinkType.value,
      issueKey: target.issueKey,
      title: target.title
    })
  }
  newLinkTargetId.value = undefined
}

function removeLink(idx: number) {
  linkedIssues.value.splice(idx, 1)
}

// ========== 相似工单搜索 ==========
const similarIssues = ref<SimilarIssue[]>([])
const similarLoading = ref(false)
let similarSearchTimer: any = null

function searchSimilarIssues(title: string) {
  if (similarSearchTimer) clearTimeout(similarSearchTimer)

  if (!title || title.trim().length < 3) {
    similarIssues.value = []
    similarLoading.value = false
    return
  }

  similarLoading.value = true
  similarSearchTimer = setTimeout(async () => {
    try {
      const res = await issueApi.findSimilar({
        keyword: title.trim(),
        projectId: form.projectId || undefined,
        limit: 5
      })
      // Response is PageResult<IssueVO>, extract list
      const list = res.data?.list || []
      similarIssues.value = list
    } catch {
      similarIssues.value = []
    } finally {
      similarLoading.value = false
    }
  }, 300)
}

function openSimilarIssue(issue: SimilarIssue) {
  // Open in new tab
  window.open(`/issues/${issue.issueKey}`, '_blank')
}

const { projects, projectLoadState, loadProjects } = useProjectList()
const members = ref<any[]>([])
const sprints = ref<any[]>([])
const statuses = ref<IssueStatusVO[]>([])
const projectTags = ref<any[]>([])

// 工单模板
const templates = ref<IssueTemplateVO[]>([])
const selectedTemplateId = ref<string | null>(null)
const form = reactive({
  projectId: undefined as string | undefined,
  title: '',
  description: '',
  issueType: 'Task',
  priority: 'Normal',
  statusId: undefined as string | undefined,
  assigneeId: undefined as string | undefined,
  sprintId: undefined as string | undefined,
  tagIds: [] as string[],
  dueDate: '',
  estimatedHours: undefined as number | undefined
})

// Watch title changes to trigger similar issue search
watch(() => form.title, (newTitle) => {
  searchSimilarIssues(newTitle)
})

// 自定义字段集成
const projectIdRef = computed(() => form.projectId)
const issueTypeRef = computed(() => form.issueType)
const { fields: customFields, values: customFieldValues, loading: cfLoading, validateRequired: validateCustomFields, getPayload: getCustomFieldPayload, fetchFields: resetCustomFields } = useCustomFieldForm(
  projectIdRef,
  issueTypeRef
)

// 自定义字段校验错误（inline 显示）
const cfValidationErrors = ref<Record<string, string>>({})

// 内联添加选项功能
const { hasPermission: hasProjectPerm } = usePermission(() => form.projectId)
const canAddFieldOption = computed(() => hasProjectPerm('project:manage_custom_fields'))
const addingOptionFieldId = ref<string | null>(null)
const newOptionInput = ref('')

function startAddOptionInSelect(fieldId: string) {
  addingOptionFieldId.value = fieldId
  newOptionInput.value = ''
}

function cancelAddOptionInSelect() {
  addingOptionFieldId.value = null
  newOptionInput.value = ''
}

async function confirmAddOptionInSelect(cf: CustomFieldDefinitionVO) {
  const val = newOptionInput.value.trim()
  if (!val || !form.projectId) return
  try {
    await customFieldApi.addOption(form.projectId, cf.id, { value: val })
    Message.success(`已添加选项"${val}"`)
    // Reload custom fields to get the new option
    await resetCustomFields()
    cancelAddOptionInSelect()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加选项失败')
  }
}

/**
 * 获取自定义字段（list 类型）经过 filterRules 过滤后的可选项列表。
 * 实现 YouTrack "Filter values based on" 功能：
 * - 如果字段配置了 filterFieldId 和 filterRules，根据源字段的当前值过滤选项
 * - 如果未配置或源字段无值，则仅过滤归档选项
 */
function getFilteredOptionsForField(cf: CustomFieldDefinitionVO) {
  // 基础过滤：排除归档选项
  let activeOptions = (cf.options || []).filter(o => !o.isArchived)

  // 值依赖过滤
  if (cf.filterFieldId && cf.filterRules) {
    try {
      const rules: FilterRule[] = JSON.parse(cf.filterRules)
      if (rules && rules.length > 0) {
        // 获取源字段当前值
        const sourceValue = customFieldValues[cf.filterFieldId] || ''
        if (sourceValue) {
          const matchedRule = rules.find(r => r.whenValue === sourceValue)
          if (matchedRule && matchedRule.showOnly && matchedRule.showOnly.length > 0) {
            activeOptions = activeOptions.filter(o => matchedRule.showOnly.includes(o.id))
          }
        }
      }
    } catch {
      // 解析失败时回退到显示所有非归档选项
    }
  }

  return activeOptions
}

/**
 * 根据字段类型和配置生成占位文字
 * 优先使用 effectiveDefaultValue 作为引导提示（类似 YouTrack Empty Value Name）
 */
function getFieldPlaceholder(cf: CustomFieldDefinitionVO): string {
  // 如果有项目级/全局默认值配置且字段无值，用作占位提示
  const hint = cf.effectiveDefaultValue ?? cf.defaultValue
  if (hint && cf.fieldFormat !== 'bool') {
    // 对于 list 类型，defaultValue 是 option ID，不适合作为 placeholder 文本
    if (cf.fieldFormat !== 'list' && cf.fieldFormat !== 'user') {
      return hint
    }
  }

  const isRequired = cf.effectiveIsRequired ?? cf.isRequired

  switch (cf.fieldFormat) {
    case 'string':
    case 'text':
      return isRequired ? `请输入${cf.name}` : `输入${cf.name}`
    case 'int':
    case 'float':
      return isRequired ? `请输入${cf.name}` : `输入数值`
    case 'date':
      return isRequired ? `请选择日期` : `选择日期`
    case 'datetime':
      return isRequired ? `请选择日期和时间` : `选择日期和时间`
    case 'list':
      if (cf.isMulti) return isRequired ? `请选择${cf.name}` : `选择（可多选）`
      return isRequired ? `请选择${cf.name}` : `选择`
    case 'user':
      return isRequired ? `请选择` : `选择用户`
    default:
      return ''
  }
}

/**
 * 清除指定字段的验证错误
 */
function clearFieldError(fieldId: string) {
  if (cfValidationErrors.value[fieldId]) {
    delete cfValidationErrors.value[fieldId]
  }
}

/**
 * 解析后端返回的自定义字段验证错误消息，映射到具体字段的内联错误。
 * 后端格式：`自定义字段验证失败: 字段名: 错误消息` 或 `自定义字段验证失败: 字段名1: 错误1; 字段名2: 错误2`
 * @returns true 如果成功映射到至少一个字段
 */
function parseCustomFieldError(message: string): boolean {
  cfValidationErrors.value = {}
  // 移除前缀
  const prefix = '自定义字段验证失败:'
  const idx = message.indexOf(prefix)
  if (idx === -1) return false

  const detail = message.slice(idx + prefix.length).trim()
  if (!detail) return false

  // 分割多个字段错误（以 "; " 或 "；" 分隔）
  const parts = detail.split(/[;；]\s*/)
  let mapped = false

  for (const part of parts) {
    // 格式："字段名: 错误消息" 或 "字段名：错误消息"
    const colonIdx = part.indexOf(':')
    const cnColonIdx = part.indexOf('：')
    const splitIdx = colonIdx >= 0 ? (cnColonIdx >= 0 ? Math.min(colonIdx, cnColonIdx) : colonIdx) : cnColonIdx
    if (splitIdx < 1) continue

    const fieldName = part.slice(0, splitIdx).trim()
    const errorText = part.slice(splitIdx + 1).trim() || '验证失败'

    // 按字段名查找匹配的自定义字段
    const field = customFields.value.find(f => f.name === fieldName)
    if (field) {
      cfValidationErrors.value[field.id] = errorText
      mapped = true
    }
  }

  return mapped
}

const canSubmit = computed(() => !!form.projectId && !!form.title.trim())

/**
 * 表单脏数据检测
 * 当标题、描述或任何属性被修改时，认为表单有未保存的数据
 */
const isDirty = computed(() => {
  if (form.title.trim()) return true
  if (form.description && form.description.trim()) return true
  if (form.assigneeId) return true
  // When sprintId is pre-set via props (lockSprint), it's not user-entered data
  if (form.sprintId && !(props.lockSprint && form.sprintId === props.sprintId)) return true
  if (form.dueDate) return true
  if (form.estimatedHours != null && form.estimatedHours > 0) return true
  // 检查自定义字段是否有值
  if (Object.values(customFieldValues.value).some(v => v && String(v).trim())) return true
  return false
})

// 暴露 isDirty 供父组件路由守卫使用
defineExpose({ isDirty })

// 检测 macOS 以显示正确的修饰键提示
const isMac = navigator.platform.toUpperCase().includes('MAC')

// beforeunload 监听：浏览器关闭/刷新时提示
function handleBeforeUnload(e: BeforeUnloadEvent) {
  if (props.visible && isDirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

// Ctrl+Enter / Cmd+Enter 快捷键处理
function handleKeyDown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    // 阻止默认行为（如换行）
    e.preventDefault()
    e.stopPropagation()
    if (canSubmit.value && !submitting.value) {
      executeDefaultAction()
    }
  }
}

/**
 * 会话过期时自动保存表单数据到 sessionStorage
 * 登录后可恢复继续编辑，避免用户长时间填写的数据丢失
 */
let unsubscribeSessionEvent: (() => void) | null = null

function handleSessionExpiring() {
  // 仅在面板可见且有实质内容时保存
  if (!props.visible || !isDirty.value) return

  saveSessionRecoveryDraft({
    fromPath: window.location.pathname,
    formData: {
      title: form.title,
      description: form.description,
      projectId: form.projectId || '',
      issueType: form.issueType,
      priority: form.priority,
      statusId: form.statusId || '',
      sprintId: form.sprintId || '',
      assigneeId: form.assigneeId || '',
      tagIds: [...form.tagIds],
      dueDate: form.dueDate || '',
      estimatedHours: form.estimatedHours ?? null,
      customFieldValues: { ...customFieldValues.value }
    },
    savedAt: Date.now()
  })
}

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('keydown', handleKeyDown, true)
  window.removeEventListener('paste', handlePaste)
  if (pasteHintTimer) clearTimeout(pasteHintTimer)
  if (unsubscribeSessionEvent) {
    unsubscribeSessionEvent()
    unsubscribeSessionEvent = null
  }
})

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
    window.addEventListener('beforeunload', handleBeforeUnload)
    window.addEventListener('keydown', handleKeyDown, true)
    window.addEventListener('paste', handlePaste)
    // 监听会话过期事件，自动保存表单数据
    if (!unsubscribeSessionEvent) {
      unsubscribeSessionEvent = onSessionEvent('session:expiring', handleSessionExpiring)
    }
    loadProjects()
    loadStatuses()
    // Pre-fill form if clone data is provided
    if (props.cloneData) {
      form.projectId = props.cloneData.projectId
      form.title = props.cloneData.title
      form.description = props.cloneData.description
      form.issueType = props.cloneData.issueType
      form.priority = props.cloneData.priority
      onProjectChange(props.cloneData.projectId)
    }
    // Restore draft data if draftId is provided
    else if (props.draftId) {
      loadDraftData(props.draftId)
    }
    // When opened with pre-set projectId (e.g. from sprint planning), load project data
    else if (props.projectId && form.projectId) {
      onProjectChange(form.projectId)
    }
  } else {
    window.removeEventListener('beforeunload', handleBeforeUnload)
    window.removeEventListener('keydown', handleKeyDown, true)
    window.removeEventListener('paste', handlePaste)
    if (unsubscribeSessionEvent) {
      unsubscribeSessionEvent()
      unsubscribeSessionEvent = null
    }
  }
})

/** 加载系统状态列表 */
async function loadStatuses() {
  try {
    const res = await issueApi.listStatuses()
    statuses.value = res.data || []
  } catch {
    statuses.value = []
  }
}

async function onProjectChange(val: any) {
  const pid = val ? String(val) : ''
  if (!pid) { members.value = []; sprints.value = []; templates.value = []; selectedTemplateId.value = null; projectTags.value = []; return }
  try { const res = await projectApi.listAssignableMembers(pid); members.value = res.data || [] } catch { members.value = [] }
  try { const res = await sprintApi.listByProject(pid, { _silent403: true }); sprints.value = (res.data?.list || []).filter((s: any) => s.status !== 'Completed' && s.status !== 'completed' && s.status !== 'Archived' && s.status !== 'archived') } catch { sprints.value = [] }
  // 加载项目标签
  try { const res = await tagApi.listProjectTags(pid, { _silent403: true }); projectTags.value = res.data || [] } catch { projectTags.value = [] }
  // 加载项目模板
  try { const res = await issueTemplateApi.list(pid); templates.value = res.data || [] } catch { templates.value = [] }
  selectedTemplateId.value = null
  // Apply sprintId prop after sprints are loaded (ensures select shows correct label)
  if (props.sprintId !== undefined && props.sprintId !== null && props.lockSprint) {
    form.sprintId = props.sprintId || undefined
  }
}

/**
 * 应用模板到表单
 */
function applyTemplate(template: IssueTemplateVO) {
  if (selectedTemplateId.value === template.id) {
    // 取消选择
    clearTemplate()
    return
  }
  selectedTemplateId.value = template.id
  // 填充描述（仅当描述为空或仍是上一个模板内容时）
  form.description = template.description || ''
  // 填充类型和优先级
  if (template.issueType) form.issueType = template.issueType
  if (template.priority) form.priority = template.priority
}

/**
 * 清除模板选择，重置表单内容
 */
function clearTemplate() {
  selectedTemplateId.value = null
  form.description = ''
  form.issueType = 'Task'
  form.priority = 'Normal'
}

function close() {
  if (isDirty.value) {
    Modal.confirm({
      title: '保存为草稿？',
      content: '当前表单中有未保存的内容。是否保存为草稿？',
      okText: '保存草稿',
      cancelText: '放弃更改',
      simple: false,
      onOk: () => {
        // 发送表单数据给父组件保存为草稿
        emit('cancel-with-data', {
          title: form.title,
          description: form.description,
          projectId: form.projectId || '',
          issueType: form.issueType,
          priority: form.priority,
          statusId: form.statusId || '',
          sprintId: form.sprintId || '',
          assigneeId: form.assigneeId || '',
          tagIds: [...form.tagIds],
          dueDate: form.dueDate || '',
          estimatedHours: form.estimatedHours ?? null,
          customFieldValues: { ...customFieldValues.value }
        })
        doClose()
      },
      onCancel: () => {
        doClose()
      }
    })
  } else {
    doClose()
  }
}

/** 真正关闭面板并重置表单 */
function doClose() {
  resetForm()
  emit('update:visible', false)
}

/** 重置表单到初始状态 */
function resetForm() {
  form.title = ''
  form.description = ''
  form.statusId = undefined
  form.assigneeId = undefined
  form.sprintId = undefined
  form.tagIds = []
  form.dueDate = ''
  form.estimatedHours = undefined
  selectedTemplateId.value = null
  cfValidationErrors.value = {}
  // 重置附件
  attachmentFiles.value = []
  pasteHint.value = ''
  // 重置 link 状态
  linkedIssues.value = []
  newLinkType.value = 'relates_to'
  newLinkTargetId.value = undefined
  showLinkSection.value = false
  linkSearchResults.value = []
  // 重置相似工单
  similarIssues.value = []
  similarLoading.value = false
}

/** 从 localStorage 加载草稿数据填充表单 */
function loadDraftData(draftId: string) {
  const { getDraft } = useDrafts()
  const draft = getDraft(draftId)
  if (!draft) return

  form.title = draft.title || ''
  form.description = draft.description || ''
  form.issueType = draft.issueType || 'Task'
  form.priority = draft.priority || 'Normal'
  form.statusId = draft.statusId || undefined
  form.tagIds = draft.tagIds || []
  form.dueDate = draft.dueDate || ''
  form.estimatedHours = draft.estimatedHours ?? undefined

  if (draft.projectId) {
    form.projectId = draft.projectId
    onProjectChange(draft.projectId).then(() => {
      // 项目数据加载完成后再设置关联字段
      if (draft.assigneeId) form.assigneeId = draft.assigneeId
      if (draft.sprintId) form.sprintId = draft.sprintId
      // 恢复自定义字段值
      if (draft.customFieldValues) {
        Object.entries(draft.customFieldValues).forEach(([key, value]) => {
          if (value) customFieldValues.value[key] = value
        })
      }
    })
  }
}

/**
 * 全屏按钮点击：将当前表单数据通过 emit 传递给父组件，由父组件保存草稿并跳转全屏页面
 */
function goFullscreen() {
  const formData = {
    title: form.title,
    description: form.description,
    projectId: form.projectId || '',
    issueType: form.issueType,
    priority: form.priority,
    statusId: form.statusId || '',
    sprintId: form.sprintId || '',
    assigneeId: form.assigneeId || '',
    tagIds: [...form.tagIds],
    dueDate: form.dueDate || '',
    estimatedHours: form.estimatedHours ?? null,
    customFieldValues: { ...customFieldValues.value }
  }
  emit('expand-to-fullscreen', formData)
}

function onSplitSelect(action: CreateMode) {
  splitMenuVisible.value = false
  // 记忆用户选择的模式
  defaultCreateMode.value = action
  localStorage.setItem(CREATE_MODE_KEY, action)
  executeDefaultAction()
}

/** 执行当前默认创建模式对应的操作 */
function executeDefaultAction() {
  switch (defaultCreateMode.value) {
    case 'close': submitAndClose(); break
    case 'continue': submitAndContinue(); break
    case 'copy': submitAndCopy(); break
  }
}

/** 丢弃草稿：不保存直接关闭 */
function discardDraft() {
  Modal.confirm({
    title: '丢弃草稿',
    content: '确定要丢弃当前内容吗？此操作不可恢复。',
    okText: '丢弃',
    cancelText: '返回编辑',
    okButtonProps: { status: 'danger' },
    simple: false,
    onOk: () => {
      resetForm()
      emit('update:visible', false)
    }
  })
}

async function submitAndClose() {
  const success = await doSubmit()
  if (success) {
    resetForm()  // 先重置表单，确保 isDirty 为 false
    emit('created')
    emit('update:visible', false)
  }
}

async function submitAndContinue() {
  const success = await doSubmit()
  if (success) {
    emit('created')
    // 保留项目/类型/优先级/Sprint，清空标题和描述等输入内容
    form.title = ''
    form.description = ''
    form.statusId = undefined
    form.assigneeId = undefined
    form.tagIds = []
    form.dueDate = ''
    form.estimatedHours = undefined
    selectedTemplateId.value = null
    // 重置关联工单
    linkedIssues.value = []
    newLinkType.value = 'relates_to'
    newLinkTargetId.value = undefined
    linkSearchResults.value = []
    // 重置相似工单
    similarIssues.value = []
    // 重置自定义字段值到默认值
    resetCustomFields()
  }
}

async function submitAndCopy() {
  const success = await doSubmit()
  if (success) {
    emit('created')
    // 保留所有字段内容（标题、描述、项目、类型、优先级等），方便创建相似工单
    // 只清空关联和相似工单
    linkedIssues.value = []
    newLinkType.value = 'relates_to'
    newLinkTargetId.value = undefined
    linkSearchResults.value = []
    similarIssues.value = []
  }
}

async function doSubmit(): Promise<boolean> {
  if (!canSubmit.value) return false

  // 自定义字段必填校验（inline 显示错误）
  cfValidationErrors.value = {}
  const cfErrors = validateCustomFields()
  if (cfErrors.length > 0) {
    // 填充 inline 错误
    for (const field of customFields.value) {
      const isRequired = field.effectiveIsRequired ?? field.isRequired
      if (isRequired && (!customFieldValues.value[field.id] || customFieldValues.value[field.id].trim() === '')) {
        cfValidationErrors.value[field.id] = '此字段为必填项'
      }
    }
    Message.warning(cfErrors[0])
    return false
  }

  submitting.value = true
  try {
    const res = await issueApi.create({
      projectId: form.projectId!,
      title: form.title.trim(),
      description: form.description || undefined,
      issueType: form.issueType,
      priority: form.priority,
      statusId: form.statusId || undefined,
      dueDate: form.dueDate || undefined,
      estimatedHours: form.estimatedHours || undefined,
      sprintId: form.sprintId || undefined,
      assigneeId: form.assigneeId || undefined,
      parentId: props.parentId || undefined,
      tagIds: form.tagIds.length > 0 ? form.tagIds : undefined,
      customFields: getCustomFieldPayload(),
      links: linkedIssues.value.length > 0
        ? linkedIssues.value.map(l => ({ targetIssueId: l.targetIssueId, linkType: l.linkType }))
        : undefined
    })

    // 上传附件（创建工单成功后批量上传）
    if (attachmentFiles.value.length > 0 && res.data?.id) {
      const issueId = res.data.id
      const uploadResults = await Promise.allSettled(
        attachmentFiles.value.map(file => issueApi.uploadAttachment(issueId, file))
      )
      const failed = uploadResults.filter(r => r.status === 'rejected')
      if (failed.length > 0) {
        Message.warning(`工单已创建，但 ${failed.length} 个附件上传失败`)
      } else {
        Message.success('工单创建成功')
      }
    } else {
      Message.success('工单创建成功')
    }

    // Remember last used project for quick create
    localStorage.setItem('trackflow:quick-create-project', form.projectId!)
    return true
  } catch (e: any) {
    const errorMsg: string = e.response?.data?.message || '创建失败'
    // 解析后端自定义字段验证错误，映射到具体字段的内联提示
    if (errorMsg.includes('自定义字段验证失败')) {
      const mapped = parseCustomFieldError(errorMsg)
      if (mapped) {
        Message.warning(errorMsg)
      } else {
        Message.error(errorMsg)
      }
    } else {
      Message.error(errorMsg)
    }
    return false
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (props.visible) {
    loadProjects()
    loadStatuses()
  }
})
</script>

<style scoped>
.create-panel { display: flex; flex-direction: column; height: 70vh; }

.panel-modal-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.fullscreen-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-text-3);
  font-size: 14px;
  transition: color 120ms, background 120ms;
  vertical-align: middle;
  margin-left: 4px;
}
.fullscreen-btn:hover {
  color: var(--color-text-1);
  background: var(--color-fill-2, var(--tf-bg-hover));
}

.title-bar { padding: 8px 0; border-bottom: 1px solid var(--color-border); flex-shrink: 0; }
.title-input { font-size: 18px; font-weight: 500; }
.title-input :deep(.arco-input) { font-size: 18px; font-weight: 500; }

.template-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.template-label {
  font-size: 12px;
  color: var(--color-text-3);
  flex-shrink: 0;
}
.template-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.template-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  font-size: 12px;
  border-radius: 12px;
  cursor: pointer;
  border: 1px solid var(--color-border-2, var(--tf-border-light));
  color: var(--color-text-2);
  background: var(--color-fill-1, var(--tf-bg-surface));
  transition: all 150ms;
  user-select: none;
}
.template-chip:hover {
  border-color: var(--tf-accent, rgb(var(--primary-6)));
  color: var(--tf-accent, rgb(var(--primary-6)));
  background: var(--color-primary-light-1, rgba(var(--primary-6), 0.06));
}
.template-chip.active {
  border-color: var(--tf-accent, rgb(var(--primary-6)));
  background: var(--tf-accent, rgb(var(--primary-6)));
  color: #fff;
}
.template-chip-clear {
  border-style: dashed;
  color: var(--color-text-3);
}
.template-chip-clear:hover {
  border-color: var(--color-text-3);
  color: var(--color-text-2);
  background: var(--color-fill-2, var(--tf-bg-hover));
}

.create-body { flex: 1; display: flex; overflow: hidden; }

.editor-area { flex: 1; display: flex; flex-direction: column; overflow-y: auto; border-right: 1px solid var(--color-border); }

.attachment-area { padding: 10px 16px; border-top: 1px solid var(--color-border); }
.upload-trigger { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--color-text-3); cursor: pointer; }
.upload-trigger kbd {
  display: inline-block;
  padding: 1px 5px;
  font-size: 11px;
  font-family: monospace;
  border: 1px solid var(--color-border-2, var(--tf-border-light));
  border-radius: 3px;
  background: var(--color-fill-1, var(--tf-bg-surface));
  color: var(--color-text-2);
  line-height: 1.4;
}

/* 附件文件列表 */
.attachment-file-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.attachment-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--color-fill-1, var(--tf-bg-surface));
  font-size: 12px;
  transition: background 100ms;
}
.attachment-file-item:hover { background: var(--color-fill-2, var(--tf-bg-hover)); }
.attachment-file-icon { font-size: 14px; flex-shrink: 0; }
.attachment-file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-1);
}
.attachment-file-size {
  color: var(--color-text-3);
  font-size: 11px;
  flex-shrink: 0;
}
.attachment-file-remove {
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-3);
  flex-shrink: 0;
  transition: color 100ms;
}
.attachment-file-remove:hover { color: var(--color-danger-light-4, #f53f3f); }

/* 粘贴提示 */
.paste-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-2);
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--color-fill-2, var(--tf-bg-hover));
  display: inline-block;
}

.props-panel { width: 250px; flex-shrink: 0; padding: 16px; overflow-y: auto; }
.prop-row { margin-bottom: 14px; }
.prop-label { display: block; font-size: 12px; color: var(--color-text-3); margin-bottom: 4px; }

.priority-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }
.priority-dot.critical { background: #ef4444; }
.priority-dot.high { background: #f59e0b; }
.priority-dot.normal { background: #6366f1; }
.priority-dot.low { background: #64748b; }

.status-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; flex-shrink: 0; }

.prop-section-divider { height: 1px; background: var(--color-border); margin: 8px 0 12px; }
.required-mark { color: #f85149; margin-left: 2px; }
.tag-color-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; flex-shrink: 0; vertical-align: middle; }
.field-error :deep(.arco-input-wrapper),
.field-error :deep(.arco-select-view),
.field-error :deep(.arco-picker) { border-color: #f85149 !important; }
.field-error-msg { display: block; font-size: 11px; color: #f85149; margin-top: 2px; line-height: 1.3; }

.panel-footer { display: flex; align-items: center; padding: 10px 0; border-top: 1px solid var(--color-border); flex-shrink: 0; }

.shortcut-hint {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-4, var(--tf-text-tertiary));
  user-select: none;
}

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
  display: flex;
  align-items: center;
  gap: 6px;
}
.split-menu-item:hover { background: var(--color-fill-2, #f2f3f5); }
.split-menu-item.active { color: var(--tf-accent, rgb(var(--primary-6))); font-weight: 500; }
.split-menu-check { font-size: 12px; }

/* Inline add option in select footer */
.select-add-option {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
  color: var(--tf-accent, rgb(var(--primary-6)));
  border-top: 1px solid var(--color-border-2, var(--tf-border-light));
  transition: background 120ms;
}
.select-add-option:hover { background: var(--color-fill-2, var(--tf-bg-hover)); }
.select-add-option .add-icon { font-size: 14px; font-weight: 600; }
.select-add-input {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-top: 1px solid var(--color-border-2, var(--tf-border-light));
}
.add-opt-field {
  flex: 1;
  padding: 3px 8px;
  border: 1px solid var(--color-border, var(--tf-border));
  border-radius: 4px;
  background: var(--color-bg-2, var(--tf-bg-body));
  color: var(--color-text-1, var(--tf-text-primary));
  font-size: 12px;
  outline: none;
}
.add-opt-field:focus { border-color: var(--tf-accent, rgb(var(--primary-6))); }
.add-opt-btn {
  padding: 3px 8px;
  border: none;
  border-radius: 4px;
  background: var(--tf-accent, rgb(var(--primary-6)));
  color: #fff;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
}
.add-opt-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* Link issue area styles */
.link-issue-area {
  margin-top: 12px;
  border: 1px solid var(--color-border-2, var(--tf-border-light));
  border-radius: 6px;
  overflow: hidden;
}
.link-issue-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-text-2);
  transition: background 120ms;
}
.link-issue-header:hover { background: var(--color-fill-2, var(--tf-bg-hover)); }
.link-issue-title { font-weight: 500; }
.link-issue-body { padding: 8px 12px; }
.linked-list { margin-bottom: 8px; }
.linked-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
  color: var(--color-text-1);
}
.link-type-label {
  color: var(--color-text-3);
  font-size: 11px;
  min-width: 80px;
}
.linked-issue-key {
  font-weight: 500;
  color: var(--tf-accent, rgb(var(--primary-6)));
}
.linked-issue-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.remove-link-btn {
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-3);
  transition: color 120ms;
}
.remove-link-btn:hover { color: var(--color-danger-light-4, #f53f3f); }
.add-link-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Similar issues area */
.similar-issues-area {
  margin-top: 12px;
  border: 1px solid var(--color-border-2, var(--tf-border-light));
  border-radius: 6px;
  overflow: hidden;
}
.similar-issues-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-text-2);
  background: var(--color-fill-1, var(--tf-bg-surface));
  border-bottom: 1px solid var(--color-border-2, var(--tf-border-light));
}
.similar-issues-title { font-weight: 500; }
.similar-issues-count {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  border-radius: 10px;
  padding: 0 6px;
  min-width: 18px;
  text-align: center;
  line-height: 18px;
}
.similar-issues-list {
  max-height: 200px;
  overflow-y: auto;
}
.similar-issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 120ms;
  border-bottom: 1px solid var(--color-border-1, var(--tf-border-subtle));
}
.similar-issue-item:last-child { border-bottom: none; }
.similar-issue-item:hover { background: var(--color-fill-2, var(--tf-bg-hover)); }
.similar-issue-key {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-accent, rgb(var(--primary-6)));
  flex-shrink: 0;
}
.similar-issue-title {
  font-size: 12px;
  color: var(--color-text-1);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.similar-issue-status {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  flex-shrink: 0;
  font-weight: 500;
}
.similar-issue-assignee {
  font-size: 11px;
  color: var(--color-text-3);
  flex-shrink: 0;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

<style>
/* Global style for the modal (unscoped to target modal wrapper) */
.issue-create-panel-modal .arco-modal-body { padding: 0 20px 16px; }
</style>
