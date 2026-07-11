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
    </div>
    <div class="editor-area">
      <EditorContent :editor="editor" />
    </div>
    <div class="editor-footer">
      <button class="btn-submit" :disabled="isEmpty" @click="submit">提交评论</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'

const emit = defineEmits<{
  submit: [content: string]
}>()

const focused = ref(false)

const editor = useEditor({
  content: '',
  extensions: [
    StarterKit,
    Link.configure({ openOnClick: false }),
    Placeholder.configure({ placeholder: '添加评论... 支持 Markdown 语法' }),
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
  emit('submit', html)
  editor.value.commands.clearContent()
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
.tb-btn {
  font-size: 12px; padding: 4px 8px; border-radius: 3px;
  background: none; border: none; color: var(--tf-text-tertiary);
  cursor: pointer; font-weight: 600;
  transition: color 150ms, background 150ms;
}
.tb-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-code); }
.tb-btn.active { color: var(--tf-accent); background: var(--tf-bg-code); }

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

.editor-footer {
  display: flex; justify-content: flex-end; align-items: center;
  height: 40px; padding: 0 8px;
  border-top: 1px solid var(--tf-border); background: var(--tf-bg-elevated);
}
.btn-submit {
  font-size: 12px; padding: 4px 16px; border-radius: 3px; border: none;
  background: var(--tf-accent); color: #fff; font-weight: 500; cursor: pointer;
  transition: background 150ms;
}
.btn-submit:disabled { opacity: 0.35; cursor: default; }
.btn-submit:hover:not(:disabled) { background: var(--tf-accent-hover); }
</style>
