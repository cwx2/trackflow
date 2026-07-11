<template>
  <div class="rich-editor" :class="{ focused }">
    <div class="editor-toolbar" v-if="editor">
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

    <textarea v-show="markdownMode" v-model="mdSource" class="md-source" rows="10"></textarea>

    <div class="editor-footer">
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
import { ref, onBeforeUnmount, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'

const props = defineProps<{
  modelValue: string
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [val: string]
  save: [content: string]
  cancel: []
  upload: [file: File]
}>()

const focused = ref(false)
const markdownMode = ref(false)
const mdSource = ref(props.modelValue || '')

const editor = useEditor({
  content: markdownToHtml(props.modelValue || ''),
  extensions: [
    StarterKit,
    Link.configure({ openOnClick: false }),
    Image,
    Placeholder.configure({ placeholder: props.placeholder || '输入内容...' }),
  ],
  editorProps: { attributes: { class: 'tiptap-body' } },
  onFocus: () => { focused.value = true },
  onBlur: () => { focused.value = false },
})

const toolbar = ref([
  { name: 'bold', icon: 'B', title: '粗体', action: () => editor.value?.chain().focus().toggleBold().run(), isActive: () => editor.value?.isActive('bold') },
  { name: 'italic', icon: 'I', title: '斜体', action: () => editor.value?.chain().focus().toggleItalic().run(), isActive: () => editor.value?.isActive('italic') },
  { name: 'strike', icon: 'S', title: '删除线', action: () => editor.value?.chain().focus().toggleStrike().run(), isActive: () => editor.value?.isActive('strike') },
  { name: 'code', icon: '<>', title: '行内代码', action: () => editor.value?.chain().focus().toggleCode().run(), isActive: () => editor.value?.isActive('code') },
  { name: 'h2', icon: 'H', title: '标题', action: () => editor.value?.chain().focus().toggleHeading({ level: 2 }).run(), isActive: () => editor.value?.isActive('heading', { level: 2 }) },
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
    editor.value?.commands.setContent(markdownToHtml(mdSource.value))
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

function htmlToMarkdown(html: string): string {
  let md = html
  md = md.replace(/<h1[^>]*>(.*?)<\/h1>/gi, '# $1\n')
  md = md.replace(/<h2[^>]*>(.*?)<\/h2>/gi, '## $1\n')
  md = md.replace(/<h3[^>]*>(.*?)<\/h3>/gi, '### $1\n')
  md = md.replace(/<strong>(.*?)<\/strong>/gi, '**$1**')
  md = md.replace(/<em>(.*?)<\/em>/gi, '*$1*')
  md = md.replace(/<s>(.*?)<\/s>/gi, '~~$1~~')
  md = md.replace(/<code>(.*?)<\/code>/gi, '`$1`')
  md = md.replace(/<a[^>]*href="([^"]*)"[^>]*>(.*?)<\/a>/gi, '[$2]($1)')
  md = md.replace(/<blockquote[^>]*>(.*?)<\/blockquote>/gis, (_, c) => c.replace(/<p[^>]*>(.*?)<\/p>/gi, '> $1\n'))
  md = md.replace(/<li[^>]*>(.*?)<\/li>/gi, '- $1\n')
  md = md.replace(/<\/?[uo]l[^>]*>/gi, '')
  md = md.replace(/<pre[^>]*><code[^>]*>(.*?)<\/code><\/pre>/gis, '```\n$1\n```\n')
  md = md.replace(/<p[^>]*>(.*?)<\/p>/gi, '$1\n\n')
  md = md.replace(/<br\s*\/?>/gi, '\n')
  md = md.replace(/<[^>]+>/g, '')
  md = md.replace(/\n{3,}/g, '\n\n')
  return md.trim()
}

function markdownToHtml(md: string): string {
  if (!md) return '<p></p>'
  let html = md
  html = html.replace(/^### (.*$)/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.*$)/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.*$)/gm, '<h1>$1</h1>')
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>')
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
  html = html.replace(/^> (.*$)/gm, '<blockquote><p>$1</p></blockquote>')
  html = html.replace(/^- (.*$)/gm, '<li>$1</li>')
  html = html.split('\n\n').map(p => {
    if (p.startsWith('<h') || p.startsWith('<pre') || p.startsWith('<blockquote') || p.startsWith('<li')) return p
    if (p.trim()) return `<p>${p.replace(/\n/g, '<br>')}</p>`
    return ''
  }).join('')
  html = html.replace(/((<li>.*?<\/li>\s*)+)/g, '<ul>$1</ul>')
  return html || '<p></p>'
}

watch(() => props.modelValue, (val) => {
  if (markdownMode.value) { mdSource.value = val }
  else {
    const current = htmlToMarkdown(editor.value?.getHTML() || '')
    if (current !== val) editor.value?.commands.setContent(markdownToHtml(val))
  }
})

onBeforeUnmount(() => { editor.value?.destroy() })
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

.editor-content { min-height: 160px; max-height: 400px; overflow-y: auto; padding: 12px 14px; }
.editor-content :deep(.tiptap-body) { outline: none; font-size: 13px; line-height: 1.6; color: var(--tf-text-primary); }
.editor-content :deep(.tiptap-body p) { margin: 4px 0; }
.editor-content :deep(.tiptap-body h2) { font-size: 1.2em; margin: 10px 0 4px; }
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
