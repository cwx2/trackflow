<template>
  <div class="rich-editor" :class="{ 'mode-inline': mode === 'inline' }">
    <TiptapEditor
      ref="tiptapRef"
      :model-value="modelValue"
      :placeholder="placeholder || '输入内容...'"
      :toolbar="true"
      :paragraph-styles="true"
      :image="true"
      :mode-switch="true"
      :min-height="mode === 'inline' ? 120 : 160"
      :max-height="mode === 'inline' ? undefined : 400"
      content-format="markdown"
      @update:model-value="onContentUpdate"
      @focus="focused = true"
      @blur="focused = false"
    >
      <template #footer v-if="mode === 'edit'">
        <div class="editor-footer">
          <button class="btn-save" @click="save">保存</button>
          <button class="btn-cancel" @click="$emit('cancel')">取消</button>
          <div class="footer-spacer"></div>
          <label class="attach-hint">
            📎 拖拽或点击上传附件
            <input type="file" class="hidden-file" @change="onFileSelect" />
          </label>
        </div>
      </template>
    </TiptapEditor>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { TiptapEditor } from '@/components/base'

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
const tiptapRef = ref<InstanceType<typeof TiptapEditor> | null>(null)

function onContentUpdate(val: string) {
  emit('update:modelValue', val)
}

function save() {
  const content = tiptapRef.value?.getMarkdown() || ''
  emit('save', content)
  emit('update:modelValue', content)
}

function onFileSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) emit('upload', file)
}
</script>

<style scoped>
.rich-editor { }
.rich-editor.mode-inline :deep(.tiptap-editor) { display: flex; flex-direction: column; }
.rich-editor.mode-inline :deep(.te-content) { flex: 1; max-height: none; }
.rich-editor.mode-inline :deep(.te-md-source) { flex: 1; max-height: none; }

.editor-footer {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px;
  border-top: 1px solid var(--tf-border); background: var(--tf-bg-elevated);
}
.btn-save { font-size: 12px; padding: 4px 14px; border-radius: 3px; border: none; background: var(--tf-accent); color: var(--tf-text-on-accent); font-weight: 500; cursor: pointer; }
.btn-save:hover { background: var(--tf-accent-hover); }
.btn-cancel { font-size: 12px; padding: 4px 14px; border-radius: 3px; background: var(--tf-bg-code); border: 1px solid var(--tf-border); color: var(--tf-text-secondary); cursor: pointer; }
.footer-spacer { flex: 1; }
.attach-hint { font-size: 11px; color: var(--tf-text-muted); cursor: pointer; }
.attach-hint:hover { color: var(--tf-text-secondary); }
.hidden-file { display: none; }
</style>
