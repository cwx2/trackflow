<template>
  <div class="comment-input" :class="{ focused }">
    <div class="editor-toolbar" v-if="editor">
      <button
        v-for="btn in toolbar"
        :key="btn.name"
        :class="['tb-btn', { active: btn.isActive?.() }]"
        :title="btn.title"
        @click="btn.action"
      >{{ btn.icon }}</button>
      <div class="toolbar-spacer"></div>
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
    </div>
    <div class="editor-area">
      <EditorContent :editor="editor" />
    </div>
    <div class="editor-footer">
      <div class="footer-actions">
        <span class="mention-hint">输入 @ 提及成员</span>
        <button v-if="showAddTime" class="btn-add-time" @click="emit('addTime')" title="添加花费的时间">⏱ 添加花费的时间</button>
        <button v-if="showAddTime && !timerRunning" class="btn-start-timer" @click="emit('startTimer')" title="开始计时">▶ 开始计时</button>
        <button v-if="showAddTime && timerRunning && timerIssueMatch" class="btn-stop-timer" @click="emit('stopTimer')" title="停止计时">⏹ 停止计时 ({{ timerElapsed }})</button>
        <span v-if="showAddTime && timerRunning && !timerIssueMatch" class="timer-elsewhere-hint" title="计时器正在其他工单运行">⏱ 计时中...</span>
      </div>
      <button class="btn-submit" :disabled="isEmpty" @click="submit">提交评论</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import Mention from '@tiptap/extension-mention'
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

const focused = ref(false)
const groups = ref<GroupSimpleVO[]>([])
const selectedGroupIds = ref<string[]>([])
const showVisibilityDropdown = ref(false)

// Mention suggestion 配置
const { suggestion, clearCache } = useMentionSuggestion(() => props.projectId)

// 监听 projectId 变化，清空成员缓存
watch(() => props.projectId, () => {
  clearCache()
})

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
  } catch { /* ignore */ }
})

const editor = useEditor({
  content: '',
  extensions: [
    StarterKit,
    Link.configure({ openOnClick: false }),
    Placeholder.configure({ placeholder: '添加评论... 支持 Markdown 语法，输入 @ 提及成员' }),
    Mention.configure({
      HTMLAttributes: {
        class: 'mention',
      },
      // 后端从 data-mention-id 属性提取 username 进行匹配
      // node.attrs.id = username（英文，供后端匹配）
      // node.attrs.label = displayName（可能是中文，供用户查看）
      renderHTML({ options, node }) {
        return [
          'span',
          { 
            class: 'mention', 
            'data-mention-id': node.attrs.id,  // username，后端从这里提取
            'data-mention-label': node.attrs.label,  // displayName
            title: `${node.attrs.label} (@${node.attrs.id})`  // hover 提示
          },
          // 显示 @displayName 给用户看
          `${options.suggestion.char}${node.attrs.label ?? node.attrs.id}`,
        ]
      },
      suggestion,
    }),
  ],
  editorProps: { attributes: { class: 'tiptap-comment' } },
  onFocus: () => { focused.value = true },
  onBlur: () => { focused.value = false },
})

const isEmpty = computed(() => !editor.value || editor.value.isEmpty)

const toolbar = ref([
  { name: 'bold', icon: 'B', title: '粗体', action: () => editor.value?.chain().focus().toggleBold().run(), isActive: () => editor.value?.isActive('bold') },
  { name: 'italic', icon: 'I', title: '斜体', action: () => editor.value?.chain().focus().toggleItalic().run(), isActive: () => editor.value?.isActive('italic') },
  { name: 'code', icon: '<>', title: '代码', action: () => editor.value?.chain().focus().toggleCode().run(), isActive: () => editor.value?.isActive('code') },
  { name: 'link', icon: '\u{1F517}', title: '链接', action: insertLink, isActive: () => editor.value?.isActive('link') },
  { name: 'bullet', icon: '\u2022', title: '列表', action: () => editor.value?.chain().focus().toggleBulletList().run(), isActive: () => editor.value?.isActive('bulletList') },
  { name: 'ordered', icon: '1.', title: '有序列表', action: () => editor.value?.chain().focus().toggleOrderedList().run(), isActive: () => editor.value?.isActive('orderedList') },
  { name: 'codeblock', icon: '{}', title: '代码块', action: () => editor.value?.chain().focus().toggleCodeBlock().run(), isActive: () => editor.value?.isActive('codeBlock') },
])

function insertLink() {
  const url = window.prompt('输入链接 URL:')
  if (url) editor.value?.chain().focus().setLink({ href: url }).run()
}

function submit() {
  if (!editor.value || editor.value.isEmpty) return
  const html = editor.value.getHTML()
  const visibleTo = selectedGroupIds.value.length > 0 ? [...selectedGroupIds.value] : undefined
  emit('submit', html, visibleTo)
  editor.value.commands.clearContent()
  selectedGroupIds.value = []
}

onBeforeUnmount(() => { editor.value?.destroy() })
</script>

<style scoped>
.comment-input {
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  overflow: hidden;
  margin-top: 16px;
  background: var(--tf-bg-body);
  transition: border-color 150ms;
}
.comment-input.focused { border-color: var(--tf-accent); }

.editor-toolbar {
  display: flex; align-items: center; gap: 4px;
  height: 36px; padding: 0 8px;
  background: var(--tf-bg-elevated);
  border-bottom: 1px solid var(--tf-border);
}
.toolbar-spacer { flex: 1; }
.tb-btn {
  font-size: 12px; padding: 4px 8px; border-radius: 3px;
  background: none; border: none; color: var(--tf-text-tertiary);
  cursor: pointer; font-weight: 600;
  transition: color 150ms, background 150ms;
}
.tb-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-code); }
.tb-btn.active { color: var(--tf-accent); background: var(--tf-bg-code); }

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

.editor-area {
  min-height: 80px; max-height: 200px; overflow-y: auto; padding: 12px;
}
.editor-area :deep(.tiptap-comment) {
  outline: none; font-size: 13px; line-height: 1.5; color: var(--tf-text-primary);
}
.editor-area :deep(.tiptap-comment p) { margin: 4px 0; }
.editor-area :deep(.tiptap-comment code) { background: var(--tf-bg-code); padding: 0 3px; border-radius: 2px; font-size: 12px; }
.editor-area :deep(.tiptap-comment .is-empty::before) {
  content: attr(data-placeholder); color: var(--tf-text-muted);
  pointer-events: none; float: left; height: 0;
}

/* Mention 样式 */
.editor-area :deep(.mention) {
  background: var(--tf-accent-subtle, rgba(88, 166, 255, 0.15));
  color: var(--tf-accent);
  border-radius: 3px;
  padding: 1px 4px;
  font-weight: 500;
  white-space: nowrap;
}

.editor-footer {
  display: flex; justify-content: space-between; align-items: center;
  height: 40px; padding: 0 8px;
  border-top: 1px solid var(--tf-border); background: var(--tf-bg-elevated);
}
.footer-actions {
  display: flex; align-items: center; gap: 4px;
}
.mention-hint {
  font-size: 11px;
  color: var(--tf-text-muted);
  padding: 0 8px;
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
