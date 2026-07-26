<template>
  <a-modal
    :visible="visible"
    :footer="false"
    :width="520"
    title="键盘快捷键"
    :mask-closable="true"
    @update:visible="$emit('update:visible', $event)"
    @cancel="$emit('update:visible', false)"
    class="shortcuts-modal"
  >
    <div class="shortcuts-content">
      <!-- Issue List section -->
      <div class="shortcut-section">
        <div class="section-title">工单列表</div>
        <div class="shortcut-grid">
          <div class="shortcut-row" v-for="item in listShortcuts" :key="item.key">
            <div class="shortcut-keys">
              <kbd v-for="k in item.keys" :key="k" class="kbd">{{ k }}</kbd>
            </div>
            <div class="shortcut-desc">{{ item.desc }}</div>
          </div>
        </div>
      </div>

      <!-- Global section -->
      <div class="shortcut-section">
        <div class="section-title">全局</div>
        <div class="shortcut-grid">
          <div class="shortcut-row" v-for="item in globalShortcuts" :key="item.key">
            <div class="shortcut-keys">
              <kbd v-for="k in item.keys" :key="k" class="kbd">{{ k }}</kbd>
            </div>
            <div class="shortcut-desc">{{ item.desc }}</div>
          </div>
        </div>
      </div>

      <!-- Note -->
      <div class="shortcuts-note">
        <icon-info-circle :size="12" />
        <span>快捷键在文本输入框中不会触发。</span>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { IconInfoCircle } from '@arco-design/web-vue/es/icon'

defineProps<{
  visible: boolean
}>()

defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const listShortcuts = [
  { key: 'n', keys: ['N'], desc: '创建新工单' },
  { key: 'j', keys: ['J', '↓'], desc: '移动到下一个工单' },
  { key: 'k', keys: ['K', '↑'], desc: '移动到上一个工单' },
  { key: 'enter', keys: ['Enter'], desc: '打开选中工单（详情或预览）' },
  { key: 'space', keys: ['Space'], desc: '选中 / 取消选中当前工单' },
  { key: 'ctrl-a', keys: ['Ctrl', 'A'], desc: '全选当前页工单' },
  { key: 'ctrl-alt-j', keys: ['Ctrl', 'Alt', 'J'], desc: '打开 Apply Command 对话框' },
]

const globalShortcuts = [
  { key: 'question', keys: ['?'], desc: '显示 / 隐藏此帮助面板' },
  { key: 'esc', keys: ['Esc'], desc: '关闭当前弹窗 / 面板' },
]
</script>

<style scoped>
.shortcuts-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.shortcut-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--tf-border);
}

.shortcut-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.shortcut-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.shortcut-keys {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 140px;
  flex-shrink: 0;
}

.kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 22px;
  padding: 0 6px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-bottom-width: 2px;
  border-radius: 4px;
  font-size: 11px;
  font-family: ui-monospace, 'SFMono-Regular', Menlo, monospace;
  color: var(--tf-text-secondary);
  font-style: normal;
  white-space: nowrap;
  line-height: 1;
}

.shortcut-desc {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.shortcuts-note {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  padding: 8px 12px;
  background: var(--tf-bg-surface);
  border-radius: 6px;
}
</style>
