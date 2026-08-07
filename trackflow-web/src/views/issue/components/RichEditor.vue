<template>
  <div class="rich-editor" :class="{ focused, 'mode-inline': mode === 'inline' }">
    <div class="editor-toolbar" v-if="editor">
      <div class="paragraph-style-select" ref="paragraphDropdownRef">
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
      <div class="tb-divider"></div>
      <button
        v-for="btn in toolbar"
        :key="btn.name"
        :class="['tb-btn', { active: btn.isActive?.() }]"
        :title="btn.title"
        @click="btn.action"
      >{{ btn.icon }}</button>
      <div class="tb-spacer"></div>
      <button :class="['tb-btn mode-btn', { active: markdownMode }]" @click="toggleMarkdown">Markdown</button>
    </div>

    <div v-show="!markdownMode" class="editor-content">
      <EditorContent :editor="editor" />
    </div>

    <textarea v-show="markdownMode" v-model="mdSource" class="md-source" rows="10" @input="onMdInput"></textarea>

    <div v-if="mode === 'edit'" class="editor-footer">
      <button class="btn-save" @click="save">保存</button>
      <button class="btn-cancel" @click="$emit('cancel')">取消</button>
      <div class="footer-spacer"></div>
      <label class="attach-hint">
        📎 拖拽或点击上传附件
        <input type="file" class="hidden-file" @change="onFileSelect" />
      </label>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted, watch, nextTick } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import { htmlToMarkdown, markdownToEditorHtml } from '@/utils/markdown'

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  /** 'edit' = 显示保存/取消按钮（详情页编辑）；'inline' = 实时同步（创建表单） */
  mode?: 'edit' | 'inline'
}>(), {
  mode: 'edit'
})

const emit = defineEmits<{
  'update:modelValue': [val: string]
  save: [content: string]
  cancel: []
  upload: [file: File]
}>()

const focused = ref(false)
const markdownMode = ref(false)
const mdSource = ref(props.modelValue || '')
/** 防止 watch(modelValue) 和 onUpdate 相互触发的标志 */
const isUpdatingFromInside = ref(false)

// --- 段落样式下拉 ---
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

/** 当前光标所在的段落类型 */
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
    case 'paragraph':
      chain.setParagraph().run()
      break
    case 'heading1':
      chain.toggleHeading({ level: 1 }).run()
      break
    case 'heading2':
      chain.toggleHeading({ level: 2 }).run()
      break
    case 'heading3':
      chain.toggleHeading({ level: 3 }).run()
      break
    case 'blockquote':
      chain.toggleBlockquote().run()
      break
    case 'codeBlock':
      chain.toggleCodeBlock().run()
      break
  }
  paragraphDropdownOpen.value = false
  updateCurrentParagraph()
}

function handleClickOutside(e: MouseEvent) {
  if (paragraphDropdownRef.value && !paragraphDropdownRef.value.contains(e.target as Node)) {
    paragraphDropdownOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  editor.value?.destroy()
})

const editor = useEditor({
  content: markdownToEditorHtml(props.modelValue || ''),
  extensions: [
    StarterKit,
    Link.configure({ openOnClick: false }),
    Image,
    Placeholder.configure({ placeholder: props.placeholder || '输入内容...' }),
  ],
  editorProps: { attributes: { class: 'tiptap-body' } },
  onFocus: () => { focused.value = true },
  onBlur: () => { focused.value = false },
  onUpdate: ({ editor: ed }) => {
    updateCurrentParagraph()
    // 在 inline 模式下，每次编辑都实时同步到父组件
    if (props.mode === 'inline') {
      const content = htmlToMarkdown(ed.getHTML())
      isUpdatingFromInside.value = true
      emit('update:modelValue', content)
      // 使用 nextTick 重置标志，确保 watch 不会回写
      nextTick(() => { isUpdatingFromInside.value = false })
    }
  },
  onSelectionUpdate: () => {
    updateCurrentParagraph()
  },
})

const toolbar = ref([
  { name: 'bold', icon: 'B', title: '粗体', action: () => editor.value?.chain().focus().toggleBold().run(), isActive: () => editor.value?.isActive('bold') },
  { name: 'italic', icon: 'I', title: '斜体', action: () => editor.value?.chain().focus().toggleItalic().run(), isActive: () => editor.value?.isActive('italic') },
  { name: 'strike', icon: 'S', title: '删除线', action: () => editor.value?.chain().focus().toggleStrike().run(), isActive: () => editor.value?.isActive('strike') },
  { name: 'code', icon: '<>', title: '行内代码', action: () => editor.value?.chain().focus().toggleCode().run(), isActive: () => editor.value?.isActive('code') },
  { name: 'quote', icon: '\u275D', title: '引用', action: () => editor.value?.chain().focus().toggleBlockquote().run(), isActive: () => editor.value?.isActive('blockquote') },
  { name: 'bullet', icon: '\u2022', title: '无序列表', action: () => editor.value?.chain().focus().toggleBulletList().run(), isActive: () => editor.value?.isActive('bulletList') },
  { name: 'ordered', icon: '1.', title: '有序列表', action: () => editor.value?.chain().focus().toggleOrderedList().run(), isActive: () => editor.value?.isActive('orderedList') },
  { name: 'codeblock', icon: '{}', title: '代码块', action: () => editor.value?.chain().focus().toggleCodeBlock().run(), isActive: () => editor.value?.isActive('codeBlock') },
  { name: 'link', icon: '\u{1F517}', title: '链接', action: insertLink, isActive: () => editor.value?.isActive('link') },
])

function insertLink() {
  const url = window.prompt('输入链接 URL:')
  if (url) editor.value?.chain().focus().setLink({ href: url }).run()
}

function toggleMarkdown() {
  if (!markdownMode.value) {
    mdSource.value = htmlToMarkdown(editor.value?.getHTML() || '')
    markdownMode.value = true
  } else {
    editor.value?.commands.setContent(markdownToEditorHtml(mdSource.value))
    markdownMode.value = false
  }
}

function save() {
  const content = markdownMode.value ? mdSource.value : htmlToMarkdown(editor.value?.getHTML() || '')
  emit('save', content)
  emit('update:modelValue', content)
}

function onFileSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) emit('upload', file)
}

/** Markdown 编辑模式下实时同步内容到父组件 */
function onMdInput() {
  if (props.mode === 'inline') {
    isUpdatingFromInside.value = true
    emit('update:modelValue', mdSource.value)
    nextTick(() => { isUpdatingFromInside.value = false })
  }
}


watch(() => props.modelValue, (val) => {
  // 如果是组件内部编辑触发的更新，不回写到编辑器（避免光标跳动）
  if (isUpdatingFromInside.value) return
  if (markdownMode.value) { mdSource.value = val }
  else {
    const current = htmlToMarkdown(editor.value?.getHTML() || '')
    if (current !== val) editor.value?.commands.setContent(markdownToEditorHtml(val))
  }
})


</script>

<style scoped>
.rich-editor { border: 1px solid var(--tf-border); border-radius: 4px; overflow: hidden; background: var(--tf-bg-body); }
.rich-editor.focused { border-color: var(--tf-accent); }

.editor-toolbar {
  display: flex; align-items: center; gap: 2px; padding: 4px 8px;
  background: var(--tf-bg-elevated); border-bottom: 1px solid var(--tf-border); flex-wrap: wrap;
}
.tb-btn {
  font-size: 12px; padding: 3px 7px; border-radius: 3px;
  background: none; border: none; color: var(--tf-text-tertiary); cursor: pointer; font-weight: 600;
}
.tb-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-code); }
.tb-btn.active { color: var(--tf-accent); background: var(--tf-bg-code); }
.mode-btn { font-weight: 400; font-size: 11px; padding: 2px 8px; border: 1px solid var(--tf-border); }
.tb-spacer { flex: 1; }
.tb-divider { width: 1px; height: 18px; background: var(--tf-border); margin: 0 4px; }

/* 段落样式下拉 */
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

.editor-content { min-height: 160px; max-height: 400px; overflow-y: auto; padding: 12px 14px; }
.rich-editor.mode-inline .editor-content { flex: 1; max-height: none; }
.rich-editor.mode-inline { display: flex; flex-direction: column; }
.rich-editor.mode-inline .md-source { flex: 1; max-height: none; }
.editor-content :deep(.tiptap-body) { outline: none; font-size: 13px; line-height: 1.6; color: var(--tf-text-primary); }
.editor-content :deep(.tiptap-body p) { margin: 4px 0; }
.editor-content :deep(.tiptap-body h1) { font-size: 1.5em; font-weight: 700; margin: 12px 0 6px; }
.editor-content :deep(.tiptap-body h2) { font-size: 1.2em; margin: 10px 0 4px; }
.editor-content :deep(.tiptap-body h3) { font-size: 1.05em; font-weight: 600; margin: 8px 0 4px; }
.editor-content :deep(.tiptap-body code) { background: var(--tf-bg-code); padding: 1px 4px; border-radius: 2px; font-size: 12px; }
.editor-content :deep(.tiptap-body pre) { background: var(--tf-bg-code); padding: 10px 12px; border-radius: 4px; overflow-x: auto; border: 1px solid var(--tf-border); margin: 8px 0; }
.editor-content :deep(.tiptap-body pre code) { background: none; padding: 0; }
.editor-content :deep(.tiptap-body blockquote) { border-left: 3px solid var(--tf-accent); padding-left: 10px; color: var(--tf-text-tertiary); margin: 6px 0; }
.editor-content :deep(.tiptap-body a) { color: var(--tf-text-link); }
.editor-content :deep(.tiptap-body .is-empty::before) {
  content: attr(data-placeholder); color: var(--tf-text-muted); pointer-events: none; float: left; height: 0;
}

.md-source {
  width: 100%; min-height: 160px; max-height: 400px; padding: 12px 14px;
  border: none; outline: none; resize: vertical;
  font-family: 'JetBrains Mono', monospace; font-size: 13px;
  color: var(--tf-text-primary); background: var(--tf-bg-body);
}

.editor-footer {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px;
  border-top: 1px solid var(--tf-border); background: var(--tf-bg-elevated);
}
.btn-save { font-size: 12px; padding: 4px 14px; border-radius: 3px; border: none; background: var(--tf-accent); color: #fff; font-weight: 500; cursor: pointer; }
.btn-save:hover { background: var(--tf-accent-hover); }
.btn-cancel { font-size: 12px; padding: 4px 14px; border-radius: 3px; background: var(--tf-bg-code); border: 1px solid var(--tf-border); color: var(--tf-text-secondary); cursor: pointer; }
.footer-spacer { flex: 1; }
.attach-hint { font-size: 11px; color: var(--tf-text-muted); cursor: pointer; }
.attach-hint:hover { color: var(--tf-text-secondary); }
.hidden-file { display: none; }
</style>
