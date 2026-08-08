<template>
  <div class="editor-toolbar">
    <div class="toolbar-left">
      <a-button type="text" @click="emit('back')">
        <span class="back-icon">←</span> 返回
      </a-button>
    </div>

    <div class="toolbar-center">
      <div class="workflow-name-wrap" @click="startEdit">
        <span v-if="!editing" class="workflow-name">{{ name }}</span>
        <a-input
          v-else
          ref="inputRef"
          :model-value="name"
          size="small"
          class="name-input"
          @update:model-value="emit('update:name', $event)"
          @blur="finishEdit"
          @keyup.enter="finishEdit"
        />
        <span v-if="!editing" class="edit-hint">✏️</span>
      </div>
    </div>

    <div class="toolbar-right">
      <a-tag :color="status === 'published' ? 'green' : status === 'disabled' ? 'gray' : 'orange'">
        {{ status === 'published' ? '已发布' : status === 'disabled' ? '已停用' : '草稿' }}
      </a-tag>
      <a-tag :color="runtimeEnabled ? 'arcoblue' : 'gray'">
        {{ runtimeEnabled ? '运行中' : '未启动' }}
      </a-tag>
      <a-button :disabled="runtimeEnabled" @click="emit('settings')">运行设置</a-button>
      <a-button :loading="publishing" :disabled="runtimeEnabled" @click="emit('publish')">
        {{ status === 'published' ? '重新发布' : '发布' }}
      </a-button>
      <a-button
        v-if="status === 'published' && runtimeEnabled"
        status="warning"
        :loading="runtimeChanging"
        @click="emit('stop')"
      >停止</a-button>
      <a-button
        v-else-if="status === 'published'"
        type="primary"
        :loading="runtimeChanging"
        @click="emit('start')"
      >启动</a-button>
      <a-dropdown trigger="hover" @select="handleMoreAction">
        <a-button type="primary" :loading="saving" :disabled="runtimeEnabled">
          保存 <span class="dropdown-arrow">▾</span>
        </a-button>
        <template #content>
          <a-doption value="save">保存</a-doption>
          <a-doption value="saveAsTemplate">另存为模板</a-doption>
        </template>
      </a-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

const props = defineProps<{
  name: string
  saving: boolean
  publishing: boolean
  status: 'draft' | 'published' | 'disabled'
  runtimeEnabled: boolean
  runtimeChanging: boolean
}>()

const emit = defineEmits<{
  'back': []
  'save': []
  'update:name': [value: string]
  'settings': []
  'publish': []
  'start': []
  'stop': []
  'saveAsTemplate': []
}>()

const editing = ref(false)
const inputRef = ref<InstanceType<typeof import('@arco-design/web-vue').Input> | null>(null)

function handleMoreAction(value: string | number | Record<string, any> | undefined) {
  if (value === 'save') {
    emit('save')
  } else if (value === 'saveAsTemplate') {
    emit('saveAsTemplate')
  }
}

const editing = ref(false)
const inputRef = ref<InstanceType<typeof import('@arco-design/web-vue').Input> | null>(null)

function startEdit() {
  if (props.runtimeEnabled) return
  editing.value = true
  nextTick(() => {
    ;(inputRef.value as any)?.focus()
  })
}

function finishEdit() {
  editing.value = false
}
</script>

<style scoped>
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  background: var(--tf-bg-surface);
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.back-icon {
  margin-right: 4px;
}

.workflow-name-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}

.workflow-name-wrap:hover {
  background: var(--tf-bg-hover);
}

.workflow-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.edit-hint {
  font-size: 12px;
  opacity: 0.5;
}

.name-input {
  width: 240px;
  text-align: center;
}

.dropdown-arrow {
  margin-left: 4px;
  font-size: 12px;
}
</style>
