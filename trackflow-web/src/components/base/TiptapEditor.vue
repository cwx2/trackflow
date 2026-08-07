<template>
  <div class="tiptap-editor" :class="{ focused, 'tiptap-editor--readonly': readonly }">
    <!-- Toolbar -->
    <div class="te-toolbar" v-if="toolbar && editor && !readonly">
      <!-- Paragraph style dropdown (only for full toolbar) -->
      <div v-if="paragraphStyles" class="paragraph-style-select" ref="paragraphDropdownRef">
        <button class="paragraph-style-btn" @click="toggleParagraphDropdown">
          <span class="paragraph-style-label">{{ currentParagraphLabel }}</span>
          <span class="paragraph-style-arrow">▾</span>
        </button>
        <div class="paragraph-style-dropdown" v-show="paragraphDropdownOpen">
          <button
            v-for="opt in paragraphOptions"
            :key="opt.value"
            :class="['paragraph-option', { active: currentParagraph === opt.value }]"
            @click="applyParagraphStyle(opt.value)"
          >
            <span :class="['paragraph-option-label', `paragraph-preview-${opt.value}`]">{{ opt.label }}</span>
          </button>
        </div>
      </div>
      <div v-if="paragraphStyles" class="tb-divider"></div>
      <!-- Formatting buttons -->
      <button
        v-for="btn in toolbarButtons"
        :key="btn.name"
        :class="['tb-btn', { active: btn.isActive?.() }]"
        :title="btn.title"
        @click="btn.action"
      >{{ btn.icon }}</button>
      <div class="tb-spacer"></div>
      <!-- Mode switch (Markdown / Visual) -->
      <button v-if="modeSwitch" :class="['tb-btn mode-btn', { active: markdownMode }]" @click="toggleMarkdown">Markdown</button>
      <!-- Extra toolbar slot -->
      <slot name="toolbar-end" />
    </div>

    <!-- Visual editor content -->
    <div v-show="!markdownMode" class="te-content" :style="contentStyle">
      <EditorContent :editor="editor" />
    </div>

    <!-- Markdown source textarea -->
    <textarea
      v-if="modeSwitch"
      v-show="markdownMode"
      v-model="mdSource"
      class="te-md-source"
      :style="contentStyle"
      @input="onMdInput"
    ></textarea>

    <!-- Footer slot (for save/cancel, submit, etc.) -->
    <slot name="footer" :editor="editor" :isEmpty="isEmpty" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted, watch, nextTick } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import { Extension } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import Mention from '@tiptap/extension-mention'
import type { SuggestionOptions } from '@tiptap/suggestion'
import { htmlToMarkdown, markdownToEditorHtml } from '@/utils/markdown'

export interface MentionSuggestionConfig {
  suggestion: Omit<SuggestionOptions, 'editor'>
}

const props = withDefaults(defineProps<{
  /** 内容（支持 v-model，HTML 格式） */
  modelValue?: string
  /** 占位符文字 */
  placeholder?: string
  /** 是否显示格式工具栏（默认 true） */
  toolbar?: boolean
  /** 是否显示段落样式下拉（标题/引用/代码块）（默认 false） */
  paragraphStyles?: boolean
  /** 是否启用 @提及功能（默认 false） */
  mention?: boolean
  /** Mention suggestion 配置（mention=true 时必须传） */
  mentionSuggestion?: MentionSuggestionConfig
  /** 是否启用图片插入（默认 false） */
  image?: boolean
  /** 是否只读（默认 false） */
  readonly?: boolean
  /** 最小高度（px），默认 80 */
  minHeight?: number
  /** 最大高度（px），默认无限制 */
  maxHeight?: number
  /** 是否显示 Markdown/Visual 切换按钮（默认 false） */
  modeSwitch?: boolean
  /** 初始模式（默认 visual） */
  mode?: 'visual' | 'markdown'
  /** 自动聚焦（默认 false） */
  autofocus?: boolean
  /** 是否使用 Enter 提交（Shift+Enter 换行）（默认 false） */
  submitOnEnter?: boolean
  /** 是否使用自定义 ReplyBlockquote 替代标准 blockquote（默认 false） */
  replyBlockquote?: boolean
  /** 自定义 ReplyBlockquote 扩展（replyBlockquote=true 时从外部传入） */
  replyBlockquoteExtension?: any
  /** 内容格式：'html' 直接使用 HTML；'markdown' 使用 markdown↔html 双向转换（默认 'markdown'） */
  contentFormat?: 'html' | 'markdown'
}>(), {
  modelValue: '',
  placeholder: '输入内容...',
  toolbar: true,
  paragraphStyles: false,
  mention: false,
  image: false,
  readonly: false,
  minHeight: 80,
  maxHeight: undefined,
  modeSwitch: false,
  mode: 'visual',
  autofocus: false,
  submitOnEnter: false,
  replyBlockquote: false,
  contentFormat: 'markdown',
})

const emit = defineEmits<{
  'update:modelValue': [val: string]
  /** Ctrl+Enter / Enter 提交（submitOnEnter=true 时触发） */
  submit: []
  focus: []
  blur: []
}>()

// --- State ---
const focused = ref(false)
const markdownMode = ref(props.mode === 'markdown')
const mdSource = ref(props.modelValue || '')
const isUpdatingFromInside = ref(false)
const isComposing = ref(false)

// --- Content style ---
const contentStyle = computed(() => ({
  minHeight: `${props.minHeight}px`,
  ...(props.maxHeight ? { maxHeight: `${props.maxHeight}px` } : {}),
}))

// --- Paragraph style dropdown ---
const paragraphDropdownOpen = ref(false)
const paragraphDropdownRef = ref<HTMLElement | null>(null)

interface ParagraphOption {
  value: string
  label: string
}

const paragraphOptions: ParagraphOption[] = [
  { value: 'paragraph', label: '普通文本' },
  { value: 'heading1', label: '标题 1' },
  { value: 'heading2', label: '标题 2' },
  { value: 'heading3', label: '标题 3' },
  { value: 'blockquote', label: '引用' },
  { value: 'codeBlock', label: '代码块' },
]

const currentParagraph = ref('paragraph')

const currentParagraphLabel = computed(() => {
  const found = paragraphOptions.find(o => o.value === currentParagraph.value)
  return found?.label ?? '普通文本'
})

function updateCurrentParagraph() {
  if (!editor.value) return
  if (editor.value.isActive('heading', { level: 1 })) currentParagraph.value = 'heading1'
  else if (editor.value.isActive('heading', { level: 2 })) currentParagraph.value = 'heading2'
  else if (editor.value.isActive('heading', { level: 3 })) currentParagraph.value = 'heading3'
  else if (editor.value.isActive('blockquote')) currentParagraph.value = 'blockquote'
  else if (editor.value.isActive('codeBlock')) currentParagraph.value = 'codeBlock'
  else currentParagraph.value = 'paragraph'
}

function toggleParagraphDropdown() {
  paragraphDropdownOpen.value = !paragraphDropdownOpen.value
}

function applyParagraphStyle(value: string) {
  if (!editor.value) return
  const chain = editor.value.chain().focus()
  switch (value) {
    case 'paragraph': chain.setParagraph().run(); break
    case 'heading1': chain.toggleHeading({ level: 1 }).run(); break
    case 'heading2': chain.toggleHeading({ level: 2 }).run(); break
    case 'heading3': chain.toggleHeading({ level: 3 }).run(); break
    case 'blockquote': chain.toggleBlockquote().run(); break
    case 'codeBlock': chain.toggleCodeBlock().run(); break
  }
  paragraphDropdownOpen.value = false
  updateCurrentParagraph()
}

function handleClickOutside(e: MouseEvent) {
  if (paragraphDropdownRef.value && !paragraphDropdownRef.value.contains(e.target as Node)) {
    paragraphDropdownOpen.value = false
  }
}

// --- SubmitOnEnter extension ---
const SubmitOnEnter = Extension.create({
  name: 'submitOnEnter',
  addKeyboardShortcuts() {
    return {
      'Enter': () => {
        if (isComposing.value || this.editor.view.composing) return false
        if (this.editor.isEmpty) return true
        emit('submit')
        return true
      },
      'Shift-Enter': ({ editor: ed }) => {
        ed.commands.setHardBreak()
        return true
      },
      'Ctrl-Enter': ({ editor: ed }) => {
        ed.commands.setHardBreak()
        return true
      },
    }
  },
})

// --- Build extensions ---
function buildExtensions() {
  const extensions: any[] = []

  // StarterKit (conditionally disable blockquote if using ReplyBlockquote)
  if (props.replyBlockquote && props.replyBlockquoteExtension) {
    extensions.push(StarterKit.configure({ blockquote: false }))
    extensions.push(props.replyBlockquoteExtension)
  } else {
    extensions.push(StarterKit)
  }

  // Link
  extensions.push(Link.configure({ openOnClick: false }))

  // Placeholder
  extensions.push(Placeholder.configure({ placeholder: props.placeholder }))

  // Image (optional)
  if (props.image) {
    extensions.push(Image)
  }

  // Mention (optional)
  if (props.mention && props.mentionSuggestion) {
    extensions.push(Mention.configure({
      HTMLAttributes: { class: 'mention' },
      renderHTML({ options, node }) {
        return [
          'span',
          {
            class: 'mention',
            'data-mention-id': node.attrs.id,
            'data-mention-label': node.attrs.label,
            title: `${node.attrs.label} (@${node.attrs.id})`
          },
          `${options.suggestion.char}${node.attrs.label ?? node.attrs.id}`,
        ]
      },
      suggestion: props.mentionSuggestion.suggestion,
    }))
  }

  // SubmitOnEnter (optional)
  if (props.submitOnEnter) {
    extensions.push(SubmitOnEnter)
  }

  return extensions
}

// --- Initialize editor ---
function getInitialContent(): string {
  if (props.contentFormat === 'html') {
    return props.modelValue || ''
  }
  return markdownToEditorHtml(props.modelValue || '')
}

const editor = useEditor({
  content: getInitialContent(),
  extensions: buildExtensions(),
  editable: !props.readonly,
  autofocus: props.autofocus ? 'end' : false,
  editorProps: {
    attributes: { class: 'tiptap-body' },
    handleDOMEvents: {
      compositionstart: () => { isComposing.value = true; return false },
      compositionend: () => { isComposing.value = false; return false },
    },
  },
  onFocus: () => {
    focused.value = true
    emit('focus')
  },
  onBlur: () => {
    focused.value = false
    isComposing.value = false
    emit('blur')
  },
  onUpdate: ({ editor: ed }) => {
    updateCurrentParagraph()
    const content = props.contentFormat === 'html' ? ed.getHTML() : htmlToMarkdown(ed.getHTML())
    isUpdatingFromInside.value = true
    emit('update:modelValue', content)
    nextTick(() => { isUpdatingFromInside.value = false })
  },
  onSelectionUpdate: () => {
    updateCurrentParagraph()
  },
})

// --- Toolbar buttons ---
interface ToolbarButton {
  name: string
  icon: string
  title: string
  action: () => void
  isActive?: () => boolean | undefined
}

function insertLink() {
  const url = window.prompt('输入链接 URL:')
  if (url) editor.value?.chain().focus().setLink({ href: url }).run()
}

const toolbarButtons = computed<ToolbarButton[]>(() => {
  const buttons: ToolbarButton[] = [
    { name: 'bold', icon: 'B', title: '粗体', action: () => editor.value?.chain().focus().toggleBold().run(), isActive: () => editor.value?.isActive('bold') },
    { name: 'italic', icon: 'I', title: '斜体', action: () => editor.value?.chain().focus().toggleItalic().run(), isActive: () => editor.value?.isActive('italic') },
    { name: 'strike', icon: 'S', title: '删除线', action: () => editor.value?.chain().focus().toggleStrike().run(), isActive: () => editor.value?.isActive('strike') },
    { name: 'code', icon: '<>', title: '行内代码', action: () => editor.value?.chain().focus().toggleCode().run(), isActive: () => editor.value?.isActive('code') },
    { name: 'link', icon: '\u{1F517}', title: '链接', action: insertLink, isActive: () => editor.value?.isActive('link') },
    { name: 'bullet', icon: '\u2022', title: '无序列表', action: () => editor.value?.chain().focus().toggleBulletList().run(), isActive: () => editor.value?.isActive('bulletList') },
    { name: 'ordered', icon: '1.', title: '有序列表', action: () => editor.value?.chain().focus().toggleOrderedList().run(), isActive: () => editor.value?.isActive('orderedList') },
    { name: 'codeblock', icon: '{}', title: '代码块', action: () => editor.value?.chain().focus().toggleCodeBlock().run(), isActive: () => editor.value?.isActive('codeBlock') },
  ]
  // Add blockquote button if paragraph styles not shown (since it's in the paragraph dropdown)
  if (!props.paragraphStyles) {
    buttons.splice(4, 0, {
      name: 'quote', icon: '\u275D', title: '引用',
      action: () => editor.value?.chain().focus().toggleBlockquote().run(),
      isActive: () => editor.value?.isActive('blockquote'),
    })
  }
  return buttons
})

// --- Markdown mode toggle ---
function toggleMarkdown() {
  if (!markdownMode.value) {
    mdSource.value = htmlToMarkdown(editor.value?.getHTML() || '')
    markdownMode.value = true
  } else {
    editor.value?.commands.setContent(markdownToEditorHtml(mdSource.value))
    markdownMode.value = false
  }
}

function onMdInput() {
  isUpdatingFromInside.value = true
  emit('update:modelValue', mdSource.value)
  nextTick(() => { isUpdatingFromInside.value = false })
}

// --- Watch modelValue for external updates ---
watch(() => props.modelValue, (val) => {
  if (isUpdatingFromInside.value) return
  if (markdownMode.value) {
    mdSource.value = val || ''
  } else {
    if (props.contentFormat === 'html') {
      const current = editor.value?.getHTML() || ''
      if (current !== val) {
        editor.value?.commands.setContent(val || '')
      }
    } else {
      const current = htmlToMarkdown(editor.value?.getHTML() || '')
      if (current !== val) {
        editor.value?.commands.setContent(markdownToEditorHtml(val || ''))
      }
    }
  }
})

// --- isEmpty computed ---
const isEmpty = computed(() => !editor.value || editor.value.isEmpty)

// --- Lifecycle ---
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  editor.value?.destroy()
})

// --- Expose for parent components ---
defineExpose({
  /** The underlying tiptap editor instance */
  editor,
  /** Whether the editor is empty */
  isEmpty,
  /** Whether the editor is in IME composing state */
  isComposing,
  /** Clear editor content */
  clearContent() {
    editor.value?.commands.clearContent()
  },
  /** Set editor content (HTML) */
  setContent(html: string) {
    editor.value?.commands.setContent(html)
  },
  /** Focus the editor */
  focus(position: 'start' | 'end' | 'all' = 'end') {
    editor.value?.commands.focus(position)
  },
  /** Get the current HTML content */
  getHTML() {
    return editor.value?.getHTML() || ''
  },
  /** Get content as markdown */
  getMarkdown() {
    return htmlToMarkdown(editor.value?.getHTML() || '')
  },
})
</script>

<style scoped>
.tiptap-editor {
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--tf-bg-body);
  transition: border-color 150ms;
}
.tiptap-editor.focused { border-color: var(--tf-accent); }
.tiptap-editor--readonly { pointer-events: none; opacity: 0.7; }

/* --- Toolbar --- */
.te-toolbar {
  display: flex; align-items: center; gap: 2px; padding: 4px 8px;
  background: var(--tf-bg-elevated); border-bottom: 1px solid var(--tf-border); flex-wrap: wrap;
}
.tb-btn {
  font-size: 12px; padding: 3px 7px; border-radius: 3px;
  background: none; border: none; color: var(--tf-text-tertiary); cursor: pointer; font-weight: 600;
  transition: color 150ms, background 150ms;
}
.tb-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-code); }
.tb-btn.active { color: var(--tf-accent); background: var(--tf-bg-code); }
.mode-btn { font-weight: 400; font-size: 11px; padding: 2px 8px; border: 1px solid var(--tf-border); }
.tb-spacer { flex: 1; }
.tb-divider { width: 1px; height: 18px; background: var(--tf-border); margin: 0 4px; }

/* --- Paragraph style dropdown --- */
.paragraph-style-select { position: relative; }
.paragraph-style-btn {
  display: flex; align-items: center; gap: 4px;
  font-size: 12px; padding: 3px 8px; border-radius: 3px;
  background: none; border: 1px solid var(--tf-border); color: var(--tf-text-secondary);
  cursor: pointer; white-space: nowrap; min-width: 80px;
}
.paragraph-style-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-code); }
.paragraph-style-label { flex: 1; text-align: left; }
.paragraph-style-arrow { font-size: 10px; color: var(--tf-text-tertiary); }

.paragraph-style-dropdown {
  position: absolute; top: calc(100% + 4px); left: 0; z-index: 10;
  min-width: 140px; padding: 4px;
  background: var(--tf-bg-elevated); border: 1px solid var(--tf-border);
  border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.paragraph-option {
  display: block; width: 100%; text-align: left;
  padding: 6px 10px; border-radius: 4px;
  background: none; border: none; color: var(--tf-text-secondary);
  cursor: pointer; font-size: 12px;
}
.paragraph-option:hover { background: var(--tf-bg-hover); color: var(--tf-text-primary); }
.paragraph-option.active { color: var(--tf-accent); background: var(--tf-bg-code); }
.paragraph-preview-heading1 { font-size: 16px; font-weight: 700; }
.paragraph-preview-heading2 { font-size: 14px; font-weight: 600; }
.paragraph-preview-heading3 { font-size: 13px; font-weight: 600; }
.paragraph-preview-blockquote { font-style: italic; color: var(--tf-text-tertiary); }
.paragraph-preview-codeBlock { font-family: 'JetBrains Mono', monospace; font-size: 11px; }

/* --- Content area --- */
.te-content {
  overflow-y: auto; padding: 12px 14px;
}
.te-content :deep(.tiptap-body) { outline: none; font-size: 13px; line-height: 1.6; color: var(--tf-text-primary); }
.te-content :deep(.tiptap-body p) { margin: 4px 0; }
.te-content :deep(.tiptap-body h1) { font-size: 1.5em; font-weight: 700; margin: 12px 0 6px; }
.te-content :deep(.tiptap-body h2) { font-size: 1.2em; margin: 10px 0 4px; }
.te-content :deep(.tiptap-body h3) { font-size: 1.05em; font-weight: 600; margin: 8px 0 4px; }
.te-content :deep(.tiptap-body code) { background: var(--tf-bg-code); padding: 1px 4px; border-radius: 2px; font-size: 12px; }
.te-content :deep(.tiptap-body pre) { background: var(--tf-bg-code); padding: 10px 12px; border-radius: 4px; overflow-x: auto; border: 1px solid var(--tf-border); margin: 8px 0; }
.te-content :deep(.tiptap-body pre code) { background: none; padding: 0; }
.te-content :deep(.tiptap-body blockquote) { border-left: 3px solid var(--tf-accent); padding-left: 10px; color: var(--tf-text-tertiary); margin: 6px 0; }
.te-content :deep(.tiptap-body a) { color: var(--tf-text-link); }
.te-content :deep(.tiptap-body .is-empty::before) {
  content: attr(data-placeholder); color: var(--tf-text-muted); pointer-events: none; float: left; height: 0;
}
/* Mention styling */
.te-content :deep(.mention) {
  background: var(--tf-accent-subtle, rgba(88, 166, 255, 0.15));
  color: var(--tf-accent);
  border-radius: 3px;
  padding: 1px 4px;
  font-weight: 500;
  white-space: nowrap;
}

/* --- Markdown source --- */
.te-md-source {
  width: 100%; padding: 12px 14px;
  border: none; outline: none; resize: vertical;
  font-family: 'JetBrains Mono', monospace; font-size: 13px;
  color: var(--tf-text-primary); background: var(--tf-bg-body);
}
</style>
