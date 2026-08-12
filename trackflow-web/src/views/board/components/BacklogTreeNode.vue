<template>
  <div class="backlog-tree-node">
    <!-- 当前节点卡片 -->
    <div
      class="backlog-card"
      :class="{
        'backlog-card--non-match': !node.isSearchMatch,
        'backlog-card--has-children': node.children.length > 0
      }"
      :style="{ marginLeft: `${depth * 16}px` }"
      :draggable="true"
      @dragstart="$emit('drag-start', $event, node.issue)"
      @dragend="$emit('drag-end')"
      @click="$emit('open-issue', node.issue)"
    >
      <!-- 展开/折叠子节点按钮（有子节点时显示） -->
      <button
        v-if="node.children.length > 0"
        class="backlog-tree-expand-btn"
        :aria-expanded="expanded"
        :title="expanded ? '收起子节点' : '展开子节点'"
        @click.stop="expanded = !expanded"
      >
        {{ expanded ? '▼' : '▶' }}
      </button>
      <span v-else class="backlog-tree-expand-placeholder" />

      <div class="backlog-card-content">
        <div class="backlog-card-header">
          <span class="backlog-card-key">{{ node.issue.issueKey }}</span>
          <span
            class="backlog-card-priority"
            :title="node.issue.priority"
          >
            <span class="priority-dot" :style="{ background: (node.issue as any).priorityColor || '#6b7280' }"></span>
          </span>
        </div>
        <div class="backlog-card-title">{{ node.issue.title }}</div>
        <div class="backlog-card-footer">
          <span class="backlog-card-type">{{ typeLabel(node.issue.issueType) }}</span>
          <span v-if="node.issue.assigneeName" class="backlog-card-assignee">
            {{ node.issue.assigneeName }}
          </span>
        </div>
      </div>
    </div>

    <!-- 子节点（递归） -->
    <template v-if="expanded && node.children.length > 0">
      <BacklogTreeNode
        v-for="child in node.children"
        :key="child.issue.id"
        :node="child"
        :depth="depth + 1"
        @drag-start="(evt: DragEvent, issue: IssueVO) => $emit('drag-start', evt, issue)"
        @drag-end="$emit('drag-end')"
        @open-issue="(issue: IssueVO) => $emit('open-issue', issue)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { IssueVO } from '@/api/types'
import type { BacklogTreeNodeData } from './BacklogPanel.vue'

const props = defineProps<{
  node: BacklogTreeNodeData
  depth: number
}>()

defineEmits<{
  'drag-start': [event: DragEvent, issue: IssueVO]
  'drag-end': []
  'open-issue': [issue: IssueVO]
}>()

// 初始展开：根节点（depth=0）默认展开，深层默认折叠
const expanded = ref(props.depth < 2)

function typeLabel(type: string): string {
  return type
}
</script>

<style scoped>
.backlog-tree-node {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.backlog-card {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 7px 10px;
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
  user-select: none;
}

.backlog-card:hover {
  border-color: var(--tf-accent);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
}

.backlog-card:active {
  cursor: grabbing;
}

/** 非搜索结果的父节点：灰色背景区分（YouTrack Tree view 行为） */
.backlog-card--non-match {
  background: var(--color-fill-1);
  opacity: 0.75;
  border-style: dashed;
}

.backlog-card--has-children {
  border-left: 2px solid var(--tf-accent-bg);
}

/* ===== 展开/折叠按钮 ===== */
.backlog-tree-expand-btn {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 9px;
  color: var(--color-text-3);
  padding: 0;
  margin-top: 2px;
  transition: color 0.15s;
}

.backlog-tree-expand-btn:hover {
  color: var(--color-text-1);
}

.backlog-tree-expand-placeholder {
  flex-shrink: 0;
  width: 16px;
  display: inline-block;
}

.backlog-card-content {
  flex: 1;
  min-width: 0;
}

.backlog-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.backlog-card-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
}

.backlog-card-priority {
  font-size: 10px;
  display: inline-flex;
  align-items: center;
}

.backlog-card-priority .priority-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}

.backlog-card-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-1);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.backlog-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.backlog-card-type {
  font-size: 10px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 1px 5px;
  border-radius: 3px;
}

.backlog-card-assignee {
  font-size: 10px;
  color: var(--color-text-2);
}
</style>
