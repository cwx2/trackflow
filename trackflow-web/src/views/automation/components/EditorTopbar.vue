<template>
  <div class="editor-toolbar">
    <!-- 左侧：返回 -->
    <div class="toolbar-left">
      <a-button type="text" class="back-btn" @click="emit('back')">
        <template #icon><icon-left /></template>
        返回
      </a-button>
    </div>

    <!-- 中间：工作流名称 -->
    <div class="toolbar-center">
      <div class="workflow-name-wrap" @click="startEdit">
        <template v-if="!editing">
          <span class="workflow-name">{{ name }}</span>
          <icon-edit
            v-if="!runtimeEnabled"
            class="edit-icon"
          />
        </template>
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
      </div>
    </div>

    <!-- 右侧：状态 + 操作按钮 -->
    <div class="toolbar-right">
      <!-- 状态徽标 -->
      <div class="status-badges">
        <a-tag
          :color="status === 'published' ? 'green' : status === 'disabled' ? 'gray' : 'orange'"
          class="status-tag"
        >
          <template v-if="status === 'published'">
            <icon-check-circle-fill class="tag-icon" /> 已发布
          </template>
          <template v-else-if="status === 'disabled'">
            <icon-close-circle-fill class="tag-icon" /> 已停用
          </template>
          <template v-else>
            <icon-file class="tag-icon" /> 草稿
          </template>
        </a-tag>

        <a-tag
          :color="runtimeEnabled ? 'arcoblue' : 'gray'"
          class="status-tag"
        >
          <template v-if="runtimeEnabled">
            <icon-play-arrow-fill class="tag-icon" /> 运行中
          </template>
          <template v-else>
            <icon-poweroff class="tag-icon" /> 未启动
          </template>
        </a-tag>
      </div>

      <div class="toolbar-divider" />

      <!-- 运行设置 -->
      <a-tooltip content="运行设置" position="bottom" mini>
        <a-button
          type="text"
          class="icon-btn"
          :disabled="runtimeEnabled"
          @click="emit('settings')"
        >
          <template #icon><icon-settings /></template>
          运行设置
        </a-button>
      </a-tooltip>

      <!-- 发布 -->
      <a-button
        :loading="publishing"
        :disabled="runtimeEnabled"
        class="publish-btn"
        @click="emit('publish')"
      >
        <template #icon><icon-send /></template>
        {{ status === 'published' ? '重新发布' : '发布' }}
      </a-button>

      <!-- 停止 / 启动 -->
      <a-button
        v-if="status === 'published' && runtimeEnabled"
        status="warning"
        :loading="runtimeChanging"
        @click="emit('stop')"
      >
        <template #icon><icon-pause /></template>
        停止
      </a-button>
      <a-button
        v-else-if="status === 'published'"
        type="primary"
        :loading="runtimeChanging"
        @click="emit('start')"
      >
        <template #icon><icon-play-arrow /></template>
        启动
      </a-button>

      <!-- 主操作必须直接生效；更多保存操作收进独立的箭头菜单。 -->
      <a-button-group class="save-actions">
        <a-button type="primary" :loading="saving" :disabled="runtimeEnabled" @click="emit('save')">
          <template #icon><icon-save /></template>
          保存
        </a-button>
        <a-dropdown trigger="click" @select="handleMoreAction">
          <a-button type="primary" aria-label="更多保存操作" :disabled="runtimeEnabled">
            <template #icon><icon-down class="dropdown-caret" /></template>
          </a-button>
          <template #content>
            <a-doption value="saveAsTemplate">
              <template #icon><icon-copy /></template>
              另存为模板
            </a-doption>
          </template>
        </a-dropdown>
      </a-button-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import {
  IconLeft,
  IconEdit,
  IconFile,
  IconCheckCircleFill,
  IconCloseCircleFill,
  IconPlayArrowFill,
  IconPoweroff,
  IconSettings,
  IconSend,
  IconPause,
  IconPlayArrow,
  IconSave,
  IconCopy,
  IconDown,
} from '@arco-design/web-vue/es/icon'

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
  if (value === 'save') emit('save')
  else if (value === 'saveAsTemplate') emit('saveAsTemplate')
}

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
/* ── 整体工具栏 ── */
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 12px;
  background: var(--tf-bg-surface);
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
  gap: 8px;
}

/* ── 左侧 ── */
.toolbar-left {
  flex-shrink: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--tf-text-secondary);
  padding: 0 8px;
  border-radius: 6px;
  transition: background 0.15s, color 0.15s;
}
.back-btn:hover {
  color: var(--tf-text-primary);
  background: var(--tf-bg-hover);
}

/* ── 中间：工作流名称 ── */
.toolbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
  min-width: 0;
}

.workflow-name-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 6px;
  max-width: 480px;
  transition: background 0.15s;
}
.workflow-name-wrap:hover {
  background: var(--tf-bg-hover);
}

.workflow-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--tf-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.edit-icon {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}
.workflow-name-wrap:hover .edit-icon {
  opacity: 1;
}

.name-input {
  width: 260px;
  text-align: center;
}

/* ── 右侧 ── */
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.toolbar-divider {
  width: 1px;
  height: 18px;
  background: var(--tf-border);
  margin: 0 2px;
}

/* 状态徽标组 */
.status-badges {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  white-space: nowrap;
}

.tag-icon {
  font-size: 11px;
}

/* 图标+文字按钮 */
.icon-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--tf-text-secondary);
  padding: 0 10px;
  border-radius: 6px;
}
.icon-btn:not(:disabled):hover {
  color: var(--tf-text-primary);
  background: var(--tf-bg-hover);
}

.publish-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* 保存按钮 caret */
.dropdown-caret {
  font-size: 11px;
  opacity: 0.8;
}
.save-actions { display: inline-flex; }
</style>
