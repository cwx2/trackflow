<template>
  <KanbanBoardViewImpl />
</template>

<script setup lang="ts">
/**
 * KanbanBoardView — 看板页面入口组件。
 *
 * 实际逻辑和渲染在 KanbanBoardViewImpl.vue 中实现，
 * 业务状态管理在 composables/useKanbanBoard.ts 中。
 *
 * 拆分说明（REQ-236）：
 * - 本文件：路由入口，<500行
 * - KanbanBoardViewImpl.vue：模板+样式（已提取 script 到 composable）
 * - composables/useKanbanBoard.ts：所有业务逻辑（3900+ 行）
 * - KanbanBoardView.scss：样式（1770+ 行）
 * - components/KanbanCard.vue：卡片渲染组件
 */
import KanbanBoardViewImpl from './KanbanBoardViewImpl.vue'
</script>
