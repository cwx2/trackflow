<template>
  <div class="mention-list">
    <template v-if="items.length > 0">
      <button
        v-for="(item, index) in items"
        :key="item.id"
        class="mention-item"
        :class="{ 'is-selected': index === selectedIndex }"
        @click="selectItem(index)"
        @mouseenter="selectedIndex = index"
      >
        <UserAvatar :name="item.displayName || item.username" :size="24" />

        <span class="mention-info">
          <span class="mention-name">{{ item.displayName || item.username }}</span>
          <span class="mention-username">@{{ item.username }}</span>
        </span>
      </button>
    </template>
    <div v-else class="mention-empty">
      无匹配成员
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { UserAvatar } from '@/components/base'

export interface MentionItem {
  id: string
  username: string
  displayName?: string
}

const props = defineProps<{
  items: MentionItem[]
  command: (item: { id: string; label: string }) => void
}>()

const selectedIndex = ref(0)

// 重置选中索引当列表变化
watch(() => props.items, () => {
  selectedIndex.value = 0
})

function selectItem(index: number) {
  const item = props.items[index]
  if (item) {
    // 使用 username 作为 id（后端通过 @username 匹配）
    props.command({ id: item.username, label: item.displayName || item.username })
  }
}

function onKeyDown({ event }: { event: KeyboardEvent }): boolean {
  if (event.key === 'ArrowUp') {
    upHandler()
    return true
  }
  if (event.key === 'ArrowDown') {
    downHandler()
    return true
  }
  if (event.key === 'Enter') {
    enterHandler()
    return true
  }
  return false
}

function upHandler() {
  selectedIndex.value = (selectedIndex.value + props.items.length - 1) % props.items.length
}

function downHandler() {
  selectedIndex.value = (selectedIndex.value + 1) % props.items.length
}

function enterHandler() {
  selectItem(selectedIndex.value)
}

// 暴露方法供 Tiptap Suggestion 调用
defineExpose({ onKeyDown })
</script>

<style scoped>
.mention-list {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  box-shadow: var(--tf-shadow-xl);
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
  min-width: 200px;
}

.mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: none;
  border-radius: 4px;
  background: none;
  color: var(--tf-text-primary);
  cursor: pointer;
  text-align: left;
  transition: background 100ms;
}

.mention-item:hover,
.mention-item.is-selected {
  background: var(--tf-bg-hover);
}

.mention-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.mention-name {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mention-username {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mention-empty {
  padding: 12px 8px;
  font-size: 12px;
  color: var(--tf-text-muted);
  text-align: center;
}
</style>
