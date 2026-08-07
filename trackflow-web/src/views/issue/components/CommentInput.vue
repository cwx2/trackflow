<template>
  <div class="comment-input">
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
            <button v-if="showAddTime" class="btn-add-time" @click="emit('addTime')" title="添加花费的时间">⏱ 添加花费的时间</button>
            <button v-if="showAddTime && !timerRunning" class="btn-start-timer" @click="emit('startTimer')" title="开始计时">▶ 开始计时</button>
            <button v-if="showAddTime && timerRunning && timerIssueMatch" class="btn-stop-timer" @click="emit('stopTimer')" title="停止计时">⏹ 停止计时 ({{ timerElapsed }})</button>
            <span v-if="showAddTime && timerRunning && !timerIssueMatch" class="timer-elsewhere-hint" title="计时器正在其他工单运行">⏱ 计时中...</span>
          </div>
          <button class="btn-submit" :disabled="editorEmpty || (tiptapRef?.isComposing ?? false)" @click="submit">提交评论</button>
        </div>
      </template>
    </TiptapEditor>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { TiptapEditor } from '@/components/base'
import { ReplyBlockquote } from '../extensions/ReplyBlockquote'
import { groupApi } from '@/api'
import type { GroupSimpleVO } from '@/api/types'
import { useMentionSuggestion } from '../composables/useMentionSuggestion'

const props = withDefaults(defineProps<{
  projectId?: string
  showAddTime?: boolean
  timerRunning?: boolean
  timerIssueMatch?: boolean
  timerElapsed?: string
  canSetVisibility?: boolean
}>(), {
  showAddTime: true,
  timerRunning: false,
  timerIssueMatch: false,
  timerElapsed: '0:00',
  canSetVisibility: false
})

const emit = defineEmits<{
  submit: [content: string, visibleToGroupIds?: string[]]
  addTime: []
  startTimer: []
  stopTimer: []
}>()

const tiptapRef = ref<InstanceType<typeof TiptapEditor> | null>(null)
const groups = ref<GroupSimpleVO[]>([])
const selectedGroupIds = ref<string[]>([])
const showVisibilityDropdown = ref(false)

// Mention suggestion 配置
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

onMounted(async () => {
  try {
    const res = await groupApi.listSimple()
    if (res.code === 0 && res.data) {
      groups.value = res.data
    }
  } catch (e) {
    console.error('[CommentInput] 加载用户组列表失败:', e)
  }
})

function submit() {
  const editorInstance = tiptapRef.value
  if (!editorInstance || editorInstance.isEmpty) return
  if (editorInstance.isComposing) return
  const html = editorInstance.getHTML()
  const visibleTo = selectedGroupIds.value.length > 0 ? [...selectedGroupIds.value] : undefined
  emit('submit', html, visibleTo)
  editorInstance.clearContent()
  selectedGroupIds.value = []
}

/**
 * 在编辑器中插入回复引用块。
 * @param displayName 被回复者的显示名
 * @param content 被引用的原评论纯文本（已截取前 100 字）
 * @param commentId 被引用的原评论 ID（用于点击引用跳转）
 */
function insertReplyQuote(displayName: string, content: string, commentId?: string) {
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
}

defineExpose({ insertReplyQuote })
</script>

<style scoped>
.comment-input {
  margin-top: 16px;
}
.comment-input :deep(.tiptap-editor) {
  border-radius: 6px;
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
  color: var(--tf-warning, #d29922);
  border-color: var(--tf-warning, #d29922);
  background: rgba(210, 153, 34, 0.08);
}
.lock-icon { font-size: 12px; }
.visibility-label { max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dropdown-arrow { font-size: 10px; opacity: 0.6; }

.visibility-dropdown {
  position: absolute; top: 100%; right: 0; z-index: 20;
  margin-top: 4px; min-width: 180px;
  background: var(--tf-bg-elevated); border: 1px solid var(--tf-border);
  border-radius: 6px; padding: 4px 0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
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
.btn-start-timer:hover { color: var(--tf-success, #3fb950); background: var(--tf-bg-hover); }
.btn-stop-timer {
  font-size: 12px; padding: 4px 12px; border-radius: 3px; border: none;
  background: none; color: var(--tf-success, #3fb950); cursor: pointer;
  font-variant-numeric: tabular-nums;
  transition: color 150ms, background 150ms;
}
.btn-stop-timer:hover { color: var(--tf-danger, #f85149); background: var(--tf-bg-hover); }
.timer-elsewhere-hint {
  font-size: 11px; color: var(--tf-text-muted); padding: 4px 8px;
}
.btn-submit {
  font-size: 12px; padding: 4px 16px; border-radius: 3px; border: none;
  background: var(--tf-accent); color: #fff; font-weight: 500; cursor: pointer;
  transition: background 150ms;
}
.btn-submit:disabled { opacity: 0.35; cursor: default; }
.btn-submit:hover:not(:disabled) { background: var(--tf-accent-hover); }
</style>

<style>
/* Tippy.js mention 主题（全局样式） */
.tippy-box[data-theme~='mention'] {
  background: transparent;
  border: none;
  box-shadow: none;
}
.tippy-box[data-theme~='mention'] .tippy-content {
  padding: 0;
}
</style>
