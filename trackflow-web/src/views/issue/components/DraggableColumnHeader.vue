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
      <icon-delete :size="14" />
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
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.col-label.sortable { cursor: pointer; }
.col-label.sortable:hover { color: var(--tf-text-primary); }

.sort-arrow {
  font-size: 10px;
  margin-left: 2px;
  transition: opacity 0.15s;
}
.sort-arrow.active {
  color: var(--tf-accent);
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
.col-remove:hover { color: var(--tf-danger); }

.col-header.is-fixed .drag-handle,
.col-header.is-fixed .col-remove { display: none; }
</style>
