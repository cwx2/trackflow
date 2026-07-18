<template>
  <Teleport to="body">
    <Transition name="cmd-fade">
      <div v-if="visible" class="cmd-dialog-overlay" @click.self="close" @keydown.esc="close">
        <div class="cmd-dialog" @keydown.esc="close">
          <div class="cmd-header">
            <span class="cmd-title">应用命令</span>
            <span class="cmd-subtitle">{{ selectedCount }} 个工单</span>
          </div>

          <div class="cmd-body">
            <!-- Command input -->
            <div class="cmd-input-wrapper">
              <input
                ref="inputRef"
                v-model="commandText"
                class="cmd-input"
                placeholder="输入命令（如：状态 进行中 负责人 张伟）"
                autocomplete="off"
                spellcheck="false"
                @input="onInput"
                @keydown="onKeydown"
              />
              <div v-if="contextLoading" class="cmd-loading">
                <a-spin :size="14" />
              </div>
            </div>

            <!-- Autocomplete suggestions -->
            <div v-if="showSuggestions && suggestions.length > 0" class="cmd-suggestions">
              <div
                v-for="(suggestion, idx) in suggestions"
                :key="idx"
                class="cmd-suggestion-item"
                :class="{ active: idx === activeSuggestionIndex }"
                @click="applySuggestion(suggestion)"
                @mouseenter="activeSuggestionIndex = idx"
              >
                <span class="suggestion-label">{{ suggestion.label }}</span>
                <span v-if="suggestion.description" class="suggestion-desc">{{ suggestion.description }}</span>
              </div>
            </div>

            <!-- Parsed commands preview -->
            <div v-if="parsedCommands.length > 0" class="cmd-preview">
              <div
                v-for="(cmd, idx) in parsedCommands"
                :key="idx"
                class="cmd-preview-item"
                :class="{ 'has-error': cmd.error }"
              >
                <span class="cmd-field">{{ cmd.fieldLabel }}</span>
                <span class="cmd-arrow">→</span>
                <span class="cmd-value" :class="{ error: cmd.error }">
                  {{ cmd.error || cmd.value }}
                </span>
              </div>
            </div>

            <!-- Comment input -->
            <div class="cmd-comment-section">
              <input
                v-model="commentText"
                class="cmd-comment-input"
                placeholder="添加评论（可选）"
              />
            </div>
          </div>

          <div class="cmd-footer">
            <div class="cmd-hints">
              <span class="hint-item">Ctrl+Enter 应用</span>
              <span class="hint-item">Ctrl+Shift+Enter 静默应用</span>
              <span class="hint-item">Esc 取消</span>
            </div>
            <div class="cmd-actions">
              <button
                class="cmd-btn cmd-btn-secondary"
                :disabled="!canExecute || executing"
                @click="execute(true)"
              >
                静默应用
              </button>
              <button
                class="cmd-btn cmd-btn-primary"
                :disabled="!canExecute || executing"
                @click="execute(false)"
              >
                <a-spin v-if="executing" :size="12" style="margin-right: 4px" />
                应用
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { Message } from '@arco-design/web-vue'
import type { IssueVO } from '@/api/types'
import { useApplyCommand, type ParsedCommand, type CommandSuggestion } from '../composables/useApplyCommand'

const props = defineProps<{
  visible: boolean
  selectedCount: number
  selectedIssues: IssueVO[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'executed': []
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const commandText = ref('')
const commentText = ref('')
const executing = ref(false)
const showSuggestions = ref(false)
const activeSuggestionIndex = ref(0)

const selectedIssuesRef = computed(() => props.selectedIssues)
const {
  contextLoading,
  loadContext,
  parseCommand,
  getSuggestions,
  executeCommands,
} = useApplyCommand(selectedIssuesRef)

// Parse commands on text change
const parsedCommands = computed(() => {
  if (!commandText.value.trim()) return []
  return parseCommand(commandText.value)
})

const canExecute = computed(() => {
  return parsedCommands.value.length > 0 && parsedCommands.value.some(cmd => !cmd.error)
})

const suggestions = computed(() => {
  if (!showSuggestions.value) return []
  return getSuggestions(commandText.value)
})

// Focus input when dialog opens
watch(() => props.visible, async (val) => {
  if (val) {
    commandText.value = ''
    commentText.value = ''
    activeSuggestionIndex.value = 0
    showSuggestions.value = true
    await loadContext()
    await nextTick()
    inputRef.value?.focus()
  }
})

function close() {
  emit('update:visible', false)
}

function onInput() {
  showSuggestions.value = true
  activeSuggestionIndex.value = 0
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
    return
  }

  // Ctrl+Enter = Apply
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    if (e.shiftKey) {
      execute(true)  // Silent
    } else {
      execute(false) // Normal
    }
    return
  }

  // Tab to accept suggestion
  if (e.key === 'Tab' && showSuggestions.value && suggestions.value.length > 0) {
    e.preventDefault()
    applySuggestion(suggestions.value[activeSuggestionIndex.value])
    return
  }

  // Arrow keys for suggestion navigation
  if (e.key === 'ArrowDown' && showSuggestions.value) {
    e.preventDefault()
    activeSuggestionIndex.value = Math.min(activeSuggestionIndex.value + 1, suggestions.value.length - 1)
    return
  }
  if (e.key === 'ArrowUp' && showSuggestions.value) {
    e.preventDefault()
    activeSuggestionIndex.value = Math.max(activeSuggestionIndex.value - 1, 0)
    return
  }

  // Enter to accept selected suggestion (if suggestions visible)
  if (e.key === 'Enter' && !e.ctrlKey && !e.metaKey && showSuggestions.value && suggestions.value.length > 0) {
    e.preventDefault()
    applySuggestion(suggestions.value[activeSuggestionIndex.value])
    return
  }
}

function applySuggestion(suggestion: CommandSuggestion) {
  const tokens = commandText.value.trim().split(/\s+/)

  if (suggestion.type === 'field') {
    // Replace the last partial token (or append if empty)
    // Find where the user started typing the field
    commandText.value = commandText.value.trimEnd() + (commandText.value.endsWith(' ') ? '' : ' ') 
    // Actually, smarter: replace from the last field-boundary
    const lastSpace = commandText.value.lastIndexOf(' ')
    const prefix = lastSpace >= 0 ? commandText.value.slice(0, lastSpace + 1) : ''
    commandText.value = prefix + suggestion.text
  } else {
    // Value suggestion: find the last field name and replace value portion
    // Simple approach: append suggestion text after last space
    const lastFieldEnd = findLastFieldEndIndex(commandText.value)
    commandText.value = commandText.value.slice(0, lastFieldEnd) + suggestion.text + ' '
  }

  showSuggestions.value = false
  nextTick(() => {
    inputRef.value?.focus()
    showSuggestions.value = true
  })
}

function findLastFieldEndIndex(text: string): number {
  // Find the position after the last recognized field name
  const tokens = text.split(/\s+/)
  let pos = 0
  for (let i = 0; i < tokens.length; i++) {
    pos += tokens[i].length + 1
    const t = tokens[i].toLowerCase()
    // Check if this token (or this + next) is a field name
    if (i + 1 < tokens.length) {
      const twoWord = (tokens[i] + ' ' + tokens[i + 1]).toLowerCase()
      if (isFieldAlias(twoWord)) {
        pos += tokens[i + 1].length + 1
        return pos
      }
    }
    if (isFieldAlias(t)) {
      return pos
    }
  }
  // Fallback: after last space
  const lastSpace = text.lastIndexOf(' ')
  return lastSpace >= 0 ? lastSpace + 1 : text.length
}

function isFieldAlias(text: string): boolean {
  const aliases = ['状态', 'state', 'status', '负责人', '分配', 'assignee', 'assigned', 'for',
    '优先级', 'priority', 'sprint', '迭代', '标签', 'tag', '添加标签', 'add tag',
    '移除标签', 'remove tag', '删除标签']
  return aliases.includes(text)
}

async function execute(silent: boolean) {
  if (!canExecute.value || executing.value) return

  const validCommands = parsedCommands.value.filter(cmd => !cmd.error)
  if (validCommands.length === 0) return

  executing.value = true
  try {
    const result = await executeCommands(validCommands, silent)

    if (result.failed === 0) {
      Message.success({
        content: `命令执行成功，${result.succeeded} 个操作已应用到 ${props.selectedCount} 个工单${silent ? '（静默模式）' : ''}`,
        duration: 3000,
      })
    } else if (result.succeeded > 0) {
      Message.warning({
        content: `部分完成：${result.succeeded} 成功，${result.failed} 失败`,
        duration: 5000,
      })
    } else {
      Message.error({
        content: '命令执行失败',
        duration: 5000,
      })
    }

    close()
    emit('executed')
  } catch (e: any) {
    Message.error({ content: e.message || '命令执行失败', duration: 5000 })
  } finally {
    executing.value = false
  }
}
</script>

<style scoped>
.cmd-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 15vh;
  z-index: 1000;
}

.cmd-dialog {
  width: 560px;
  max-width: 90vw;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 12px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cmd-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--tf-border);
}

.cmd-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.cmd-subtitle {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 2px 8px;
  border-radius: 3px;
}

.cmd-body {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cmd-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.cmd-input {
  width: 100%;
  height: 36px;
  padding: 0 12px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  color: var(--tf-text-primary);
  font-size: 14px;
  font-family: 'SF Mono', 'Fira Code', 'JetBrains Mono', Consolas, monospace;
  outline: none;
  transition: border-color 0.15s;
}

.cmd-input:focus {
  border-color: var(--tf-accent);
}

.cmd-input::placeholder {
  color: var(--tf-text-quaternary);
  font-family: inherit;
}

.cmd-loading {
  position: absolute;
  right: 12px;
}

/* Suggestions dropdown */
.cmd-suggestions {
  max-height: 200px;
  overflow-y: auto;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
}

.cmd-suggestion-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.1s;
}

.cmd-suggestion-item:hover,
.cmd-suggestion-item.active {
  background: var(--tf-bg-hover);
}

.suggestion-label {
  color: var(--tf-text-primary);
  font-weight: 500;
}

.suggestion-desc {
  color: var(--tf-text-tertiary);
  font-size: 11px;
}

/* Parsed commands preview */
.cmd-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 0;
}

.cmd-preview-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: var(--tf-accent-bg, rgba(88, 166, 255, 0.1));
  border-radius: 4px;
  font-size: 12px;
}

.cmd-preview-item.has-error {
  background: rgba(248, 81, 73, 0.1);
}

.cmd-field {
  color: var(--tf-text-secondary);
  font-weight: 500;
}

.cmd-arrow {
  color: var(--tf-text-tertiary);
  font-size: 10px;
}

.cmd-value {
  color: var(--tf-accent);
  font-weight: 500;
}

.cmd-value.error {
  color: var(--tf-danger);
}

/* Comment section */
.cmd-comment-section {
  border-top: 1px solid var(--tf-border);
  padding-top: 8px;
}

.cmd-comment-input {
  width: 100%;
  height: 32px;
  padding: 0 12px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  color: var(--tf-text-primary);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}

.cmd-comment-input:focus {
  border-color: var(--tf-accent);
}

.cmd-comment-input::placeholder {
  color: var(--tf-text-quaternary);
}

/* Footer */
.cmd-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-top: 1px solid var(--tf-border);
}

.cmd-hints {
  display: flex;
  gap: 12px;
}

.hint-item {
  font-size: 11px;
  color: var(--tf-text-quaternary);
}

.cmd-actions {
  display: flex;
  gap: 8px;
}

.cmd-btn {
  height: 30px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.15s;
}

.cmd-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.cmd-btn-primary {
  background: var(--tf-accent);
  color: #fff;
  display: flex;
  align-items: center;
}

.cmd-btn-primary:hover:not(:disabled) {
  filter: brightness(1.1);
}

.cmd-btn-secondary {
  background: transparent;
  border: 1px solid var(--tf-border);
  color: var(--tf-text-secondary);
}

.cmd-btn-secondary:hover:not(:disabled) {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

/* Transition */
.cmd-fade-enter-active,
.cmd-fade-leave-active {
  transition: opacity 0.15s;
}
.cmd-fade-enter-active .cmd-dialog,
.cmd-fade-leave-active .cmd-dialog {
  transition: transform 0.15s, opacity 0.15s;
}
.cmd-fade-enter-from,
.cmd-fade-leave-to {
  opacity: 0;
}
.cmd-fade-enter-from .cmd-dialog,
.cmd-fade-leave-to .cmd-dialog {
  transform: scale(0.96);
  opacity: 0;
}
</style>
