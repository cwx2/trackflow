<template>
  <div ref="rootRef" class="comment-input" :class="{ 'comment-input--expanded': isExpanded }">
    <!-- Collapsed state: single-line placeholder -->
    <div v-if="!isExpanded" class="comment-collapsed" @click="expand">
      <UserAvatar :name="currentUserName" :size="28" />
      <span class="comment-collapsed-placeholder">添加评论... 支持 Markdown 语法，输入 @ 提及成员</span>
    </div>

    <!-- Expanded state: full TiptapEditor -->
    <Transition name="comment-expand">
      <div v-if="isExpanded" class="comment-expanded">
        <TiptapEditor
          ref="tiptapRef"
          model-value=""
          placeholder="添加评论... 支持 Markdown 语法，输入 @ 提及成员"
          :toolbar="true"
          :mention="true"
          :mention-suggestion="mentionConfig"
          :submit-on-enter="true"
          :reply-blockquote="true"
          :reply-blockquote-extension="ReplyBlockquote"
          :min-height="80"
          :max-height="200"
          content-format="html"
          @submit="submit"
        >
          <template #toolbar-end>
            <!-- Visible to selector -->
            <div class="visibility-selector" v-if="groups.length > 0 && canSetVisibility">
              <button class="visibility-btn" :class="{ restricted: selectedGroupIds.length > 0 }" @click="showVisibilityDropdown = !showVisibilityDropdown" title="设置评论可见范围">
                <span class="lock-icon">{{ selectedGroupIds.length > 0 ? '🔒' : '👁' }}</span>
                <span class="visibility-label">{{ visibilityLabel }}</span>
                <span class="dropdown-arrow">▾</span>
              </button>
              <div v-if="showVisibilityDropdown" class="visibility-dropdown" @mouseleave="showVisibilityDropdown = false">
                <div class="dropdown-header">可见范围</div>
                <div class="dropdown-option" :class="{ selected: selectedGroupIds.length === 0 }" @click="clearVisibility">
                  <span class="check">{{ selectedGroupIds.length === 0 ? '✓' : '' }}</span>
                  全部成员可见
                </div>
                <div class="dropdown-divider"></div>
                <div
                  v-for="group in groups"
                  :key="group.id"
                  class="dropdown-option"
                  :class="{ selected: selectedGroupIds.includes(group.id) }"
                  @click="toggleGroup(group.id)"
                >
                  <span class="check">{{ selectedGroupIds.includes(group.id) ? '✓' : '' }}</span>
                  {{ group.name }}
                </div>
              </div>
            </div>
          </template>
          <template #footer="{ isEmpty: editorEmpty }">
            <div class="editor-footer">
              <div class="footer-actions">
                <button class="btn-cancel" :disabled="submitting" @click="collapse">取消</button>
                <button v-if="showAddTime" class="btn-add-time" @click="emit('addTime')" title="添加花费的时间">⏱ 记录工时</button>
                <button v-if="showAddTime && !timerRunning" class="btn-start-timer" @click="emit('startTimer')" title="开始计时">▶ 开始计时</button>
                <button v-if="showAddTime && timerRunning && timerIssueMatch" class="btn-stop-timer" @click="emit('stopTimer')" title="停止计时">⏹ 停止计时 ({{ timerElapsed }})</button>
                <span v-if="showAddTime && timerRunning && !timerIssueMatch" class="timer-elsewhere-hint" title="计时器正在其他工单运行">⏱ 计时中...</span>
              </div>
              <button class="btn-submit" :disabled="editorEmpty || submitting || (tiptapRef?.isComposing ?? false)" @click="submit">
                <span v-if="submitting" class="submit-loading">发布中...</span>
                <span v-else>发布评论</span>
              </button>
            </div>
          </template>
        </TiptapEditor>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { TiptapEditor, UserAvatar } from '@/components/base'
import { ReplyBlockquote } from '../extensions/ReplyBlockquote'
import { groupApi } from '@/api'
import type { GroupSimpleVO } from '@/api/types'
import { useMentionSuggestion } from '../composables/useMentionSuggestion'
import { useAuthStore } from '@/stores/auth'

const props = withDefaults(defineProps<{
  projectId?: string
  showAddTime?: boolean
  timerRunning?: boolean
  timerIssueMatch?: boolean
  timerElapsed?: string
  canSetVisibility?: boolean
  /** Async callback for submitting comment. Must throw on failure so the editor retains content. */
  onSubmit?: (content: string, visibleToGroupIds?: string[]) => Promise<void>
}>(), {
  showAddTime: true,
  timerRunning: false,
  timerIssueMatch: false,
  timerElapsed: '0:00',
  canSetVisibility: false,
  onSubmit: undefined
})

const emit = defineEmits<{
  addTime: []
  startTimer: []
  stopTimer: []
}>()


const authStore = useAuthStore()
const currentUserName = computed(() => authStore.user?.displayName || authStore.user?.username || '?')

const rootRef = ref<HTMLElement | null>(null)
const tiptapRef = ref<InstanceType<typeof TiptapEditor> | null>(null)
const groups = ref<GroupSimpleVO[]>([])
const selectedGroupIds = ref<string[]>([])
const showVisibilityDropdown = ref(false)
const isExpanded = ref(false)
const submitting = ref(false)

// Mention suggestion config
const { suggestion } = useMentionSuggestion(() => props.projectId)
const mentionConfig = { suggestion }

const visibilityLabel = computed(() => {
  if (selectedGroupIds.value.length === 0) return '全部可见'
  if (selectedGroupIds.value.length === 1) {
    const group = groups.value.find(g => g.id === selectedGroupIds.value[0])
    return group ? group.name : '1 个组'
  }
  return `${selectedGroupIds.value.length} 个组`
})

function toggleGroup(groupId: string) {
  const index = selectedGroupIds.value.indexOf(groupId)
  if (index >= 0) {
    selectedGroupIds.value.splice(index, 1)
  } else {
    selectedGroupIds.value.push(groupId)
  }
}

function clearVisibility() {
  selectedGroupIds.value = []
  showVisibilityDropdown.value = false
}

/** Expand the comment input to full editor */
function expand() {
  isExpanded.value = true
  nextTick(() => {
    tiptapRef.value?.editor?.commands.focus()
  })
}

/** Collapse the comment input back to single-line placeholder */
function collapse() {
  const editor = tiptapRef.value?.editor
  if (editor) {
    editor.commands.clearContent()
  }
  selectedGroupIds.value = []
  showVisibilityDropdown.value = false
  isExpanded.value = false
}

/** Handle click outside: only collapse if editor is empty */
function handleDocumentClick(e: MouseEvent) {
  if (!isExpanded.value) return
  if (!rootRef.value) return
  // If click is inside the component, ignore
  if (rootRef.value.contains(e.target as Node)) return
  // Also check tippy popups (mentions, dropdowns) which may be outside our root
  const target = e.target as HTMLElement
  if (target.closest('.tippy-box') || target.closest('.arco-trigger-popup')) return

  const editor = tiptapRef.value?.editor
  if (editor && editor.isEmpty) {
    isExpanded.value = false
  }
  // If editor has content, stay expanded to prevent accidental data loss
}

onMounted(async () => {
  document.addEventListener('mousedown', handleDocumentClick, true)
  try {
    const res = await groupApi.listSimple()
    if (res.code === 0 && res.data) {
      groups.value = res.data
    }
  } catch (e) {
    console.error('[CommentInput] 加载用户组列表失败:', e)
  }
})

onUnmounted(() => {
  document.removeEventListener('mousedown', handleDocumentClick, true)
})

async function submit() {
  const editorInstance = tiptapRef.value
  if (!editorInstance || editorInstance.isEmpty) return
  if (editorInstance.isComposing) return
  if (submitting.value) return

  const html = editorInstance.getHTML()
  const visibleTo = selectedGroupIds.value.length > 0 ? [...selectedGroupIds.value] : undefined

  if (props.onSubmit) {
    // Async mode: wait for API response before clearing
    submitting.value = true
    try {
      await props.onSubmit(html, visibleTo)
      // Success: clear content and collapse
      editorInstance.clearContent()
      selectedGroupIds.value = []
      isExpanded.value = false
    } catch (_e) {
      // Failure: keep editor expanded with content intact
      // Error toast is already shown by handleApiError in the parent
    } finally {
      submitting.value = false
    }
  } else {
    // Fallback: fire-and-forget (legacy, should not happen in normal use)
    editorInstance.clearContent()
    selectedGroupIds.value = []
    isExpanded.value = false
  }
}

/**
 * Insert a reply quote into the editor.
 * If collapsed, expand first.
 */
function insertReplyQuote(displayName: string, content: string, commentId?: string) {
  // Auto-expand when replying
  if (!isExpanded.value) {
    isExpanded.value = true
  }

  nextTick(() => {
    const editorInstance = tiptapRef.value
    if (!editorInstance) return
    const editor = editorInstance.editor
    if (!editor) return

    const truncated = content.length > 100 ? content.slice(0, 100) + '...' : content
    const replyAttr = commentId ? ` data-reply-to-comment-id="${commentId}"` : ''
    const quoteHtml = `<blockquote${replyAttr}><p>@${displayName}：${truncated}</p></blockquote><p></p>`

    const isCurrentEmpty = editor.isEmpty

    if (isCurrentEmpty) {
      editor.commands.setContent(quoteHtml)
    } else {
      const currentContent = editor.getHTML()
      editor.commands.setContent(quoteHtml + currentContent)
    }
    editor.commands.focus('end')

    nextTick(() => {
      const el = document.querySelector('.comment-input')
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    })
  })
}

defineExpose({ insertReplyQuote })
</script>

<style scoped>
.comment-input {
  margin-top: 0;
}

/* Collapsed state */
.comment-collapsed {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  cursor: text;
  transition: border-color 150ms, background 150ms;
}
.comment-collapsed:hover {
  border-color: var(--tf-border-hover, var(--tf-text-muted));
  background: var(--tf-bg-hover);
}
.comment-collapsed-placeholder {
  font-size: 13px;
  color: var(--tf-text-muted);
  user-select: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Expanded state */
.comment-expanded {
  animation: comment-expand-in 200ms ease-out;
}
.comment-expanded :deep(.tiptap-editor) {
  border-radius: 6px;
}

/* Expand animation */
@keyframes comment-expand-in {
  from {
    opacity: 0.6;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Transition for v-if switch */
.comment-expand-enter-active {
  animation: comment-expand-in 200ms ease-out;
}
.comment-expand-leave-active {
  animation: comment-expand-in 150ms ease-in reverse;
}

/* Visibility selector */
.visibility-selector { position: relative; }
.visibility-btn {
  display: flex; align-items: center; gap: 4px;
  font-size: 11px; padding: 3px 8px; border-radius: 3px;
  background: none; border: 1px solid transparent;
  color: var(--tf-text-tertiary); cursor: pointer;
  transition: all 150ms;
}
.visibility-btn:hover { color: var(--tf-text-primary); border-color: var(--tf-border); }
.visibility-btn.restricted {
  color: var(--tf-warning);
  border-color: var(--tf-warning);
  background: var(--tf-warning-bg);
}
.lock-icon { font-size: 12px; }
.visibility-label { max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dropdown-arrow { font-size: 10px; opacity: 0.6; }

.visibility-dropdown {
  position: absolute; top: 100%; right: 0; z-index: 20;
  margin-top: 4px; min-width: 180px;
  background: var(--tf-bg-elevated); border: 1px solid var(--tf-border);
  border-radius: 6px; padding: 4px 0;
  box-shadow: var(--tf-shadow-xl);
}
.dropdown-header {
  padding: 6px 12px; font-size: 11px; font-weight: 600;
  color: var(--tf-text-tertiary); text-transform: uppercase;
  letter-spacing: 0.5px;
}
.dropdown-option {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 12px; font-size: 12px; cursor: pointer;
  color: var(--tf-text-secondary);
  transition: background 100ms;
}
.dropdown-option:hover { background: var(--tf-bg-hover); }
.dropdown-option.selected { color: var(--tf-text-primary); font-weight: 500; }
.dropdown-option .check { width: 14px; font-size: 12px; color: var(--tf-accent); }
.dropdown-divider { height: 1px; margin: 4px 8px; background: var(--tf-border); }

/* Footer */
.editor-footer {
  display: flex; justify-content: space-between; align-items: center;
  height: 40px; padding: 0 8px;
  border-top: 1px solid var(--tf-border); background: var(--tf-bg-elevated);
}
.footer-actions {
  display: flex; align-items: center; gap: 4px;
}
.btn-cancel {
  font-size: 12px; padding: 4px 12px; border-radius: 3px; border: none;
  background: none; color: var(--tf-text-secondary); cursor: pointer;
  transition: color 150ms, background 150ms;
}
.btn-cancel:hover { color: var(--tf-text-primary); background: var(--tf-bg-hover); }
.btn-add-time {
  font-size: 12px; padding: 4px 12px; border-radius: 3px; border: none;
  background: none; color: var(--tf-text-tertiary); cursor: pointer;
  transition: color 150ms, background 150ms;
}
.btn-add-time:hover { color: var(--tf-accent); background: var(--tf-bg-hover); }
.btn-start-timer {
  font-size: 12px; padding: 4px 12px; border-radius: 3px; border: none;
  background: none; color: var(--tf-text-tertiary); cursor: pointer;
  transition: color 150ms, background 150ms;
}
.btn-start-timer:hover { color: var(--tf-success); background: var(--tf-bg-hover); }
.btn-stop-timer {
  font-size: 12px; padding: 4px 12px; border-radius: 3px; border: none;
  background: none; color: var(--tf-success); cursor: pointer;
  font-variant-numeric: tabular-nums;
  transition: color 150ms, background 150ms;
}
.btn-stop-timer:hover { color: var(--tf-danger); background: var(--tf-bg-hover); }
.timer-elsewhere-hint {
  font-size: 11px; color: var(--tf-text-muted); padding: 4px 8px;
}
.btn-submit {
  font-size: 12px; padding: 4px 16px; border-radius: 3px; border: none;
  background: var(--tf-accent); color: var(--tf-text-on-accent); font-weight: 500; cursor: pointer;
  transition: background 150ms;
}
.btn-submit:disabled { opacity: 0.35; cursor: default; }
.btn-submit:hover:not(:disabled) { background: var(--tf-accent-hover); }
.submit-loading { opacity: 0.8; }
</style>

<style>
/* Tippy.js mention theme (global style) */
.tippy-box[data-theme~='mention'] {
  background: transparent;
  border: none;
  box-shadow: none;
}
.tippy-box[data-theme~='mention'] .tippy-content {
  padding: 0;
}
</style>
