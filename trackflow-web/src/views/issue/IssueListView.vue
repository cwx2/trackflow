<template>
  <IssueListViewImpl />
</template>

<script setup lang="ts">
/**
 * IssueListView — 工单列表页面入口组件。
 *
 * 实际逻辑和渲染在 IssueListViewImpl.vue 中实现，
 * 业务状态管理分布在 composables/ 目录中。
 *
 * 拆分说明（REQ-236）：
 * - 本文件：路由入口，<500行
 * - IssueListViewImpl.vue：模板+样式（已提取核心 script 到多个 composable）
 * - composables/useIssueList.ts：列表数据加载
 * - composables/useSelection.ts：多选逻辑
 * - composables/useInlineEdit.ts：行内编辑
 * - composables/useBatchOps.ts：批量操作
 * - composables/useColumnConfig.ts：列配置
 * - composables/useViewSettings.ts：视图设置
 * - composables/useManualOrder.ts：手动排序
 * - composables/useDrafts.ts：草稿
 * - IssueListView.scss：样式
 */
import IssueListViewImpl from './IssueListViewImpl.vue'

// KeepAlive 按 name 匹配缓存组件，必须与 AppLayout 中 :include 配置一致
defineOptions({ name: 'IssueListView' })
</script>
