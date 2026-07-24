<template>
  <div
    class="col-header"
    :class="{ 'is-dragging': isDragging, 'drag-over': isDragOver, 'is-fixed': fixed }"
    :draggable="!fixed"
    @dragstart="onDragStart"
    @dragend="onDragEnd"
    @dragover.prevent="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <!-- Drag handle -->
    <span v-if="!fixed" class="drag-handle" title="拖拽调整列顺序">⠿</span>

    <!-- Column name + sort -->
    <span class="col-label" :class="{ sortable }" @click="sortable ? $emit('sort', columnKey) : undefined">
      {{ label }}
      <span v-if="sortDir" class="sort-arrow active">{{ sortDir === 'asc' ? '↑' : '↓' }}</span>
      <span v-else-if="sortable" class="sort-arrow hint">↕</span>
    </span>

    <!-- Remove button -->
    <span v-if="!fixed" class="col-remove" title="移除列" @click.stop="$emit('remove', columnKey)">
      <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
        <path d="M5.5 5.5A.5.5 0 016 6v6a.5.5 0 01-1 0V6a.5.5 0 01.5-.5zm2.5 0a.5.5 0 01.5.5v6a.5.5 0 01-1 0V6a.5.5 0 01.5-.5zm3 .5a.5.5 0 00-1 0v6a.5.5 0 001 0V6z"/>
        <path fill-rule="evenodd" d="M14.5 3a1 1 0 01-1 1H13v9a2 2 0 01-2 2H5a2 2 0 01-2-2V4h-.5a1 1 0 010-2H6a1 1 0 011-1h2a1 1 0 011 1h3.5a1 1 0 011 1zM4.118 4L4 4.059V13a1 1 0 001 1h6a1 1 0 001-1V4.059L11.882 4H4.118zM2.5 3a.5.5 0 000 1h11a.5.5 0 000-1h-11z"/>
      </svg>
    </span>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  columnKey: string
  label: string
  sortable: boolean
  sortDir: 'asc' | 'desc' | null
  fixed: boolean
}>()

const emit = defineEmits<{
  sort: [key: string]
  remove: [key: string]
  dragStart: [key: string]
  dragDrop: [fromKey: string, toKey: string]
}>()

const isDragging = ref(false)
const isDragOver = ref(false)

function onDragStart(e: DragEvent) {
  if (props.fixed) return
  isDragging.value = true
  e.dataTransfer!.effectAllowed = 'move'
  e.dataTransfer!.setData('text/plain', props.columnKey)
  emit('dragStart', props.columnKey)
}

function onDragEnd() {
  isDragging.value = false
}

function onDragOver(e: DragEvent) {
  if (props.fixed) return
  isDragOver.value = true
  e.dataTransfer!.dropEffect = 'move'
}

function onDragLeave() {
  isDragOver.value = false
}

function onDrop(e: DragEvent) {
  isDragOver.value = false
  const fromKey = e.dataTransfer!.getData('text/plain')
  if (fromKey && fromKey !== props.columnKey) {
    emit('dragDrop', fromKey, props.columnKey)
  }
}
</script>

<style scoped>
.col-header {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  user-select: none;
  padding: 2px 0;
}
.col-header.is-dragging { opacity: 0.4; }
.col-header.drag-over { background: var(--tf-accent-bg, rgba(88,166,255,0.1)); border-radius: 3px; }

.drag-handle {
  cursor: grab;
  color: var(--tf-text-quaternary, #4b5563);
  font-size: 12px;
  line-height: 1;
  letter-spacing: -1px;
  opacity: 0;
  transition: opacity 0.15s;
  flex-shrink: 0;
}
.col-header:hover .drag-handle { opacity: 1; }
.drag-handle:active { cursor: grabbing; }

.col-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary, #6b7280);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.col-label.sortable { cursor: pointer; }
.col-label.sortable:hover { color: var(--tf-text-primary, #e6edf3); }

.sort-arrow {
  font-size: 10px;
  margin-left: 2px;
  transition: opacity 0.15s;
}
.sort-arrow.active {
  color: var(--tf-accent, #58a6ff);
  opacity: 1;
}
.sort-arrow.hint {
  color: var(--tf-text-quaternary, #4b5563);
  opacity: 0;
}
.col-header:hover .sort-arrow.hint {
  opacity: 1;
}

.col-remove {
  cursor: pointer;
  color: var(--tf-text-quaternary, #4b5563);
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
.col-header:hover .col-remove { opacity: 1; }
.col-remove:hover { color: var(--tf-danger, #f85149); }

.col-header.is-fixed .drag-handle,
.col-header.is-fixed .col-remove { display: none; }
</style>
