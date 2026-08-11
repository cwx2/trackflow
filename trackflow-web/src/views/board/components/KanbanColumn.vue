<template>
  <!-- 展开状态的列 -->
  <div
    v-if="!collapsed"
    class="board-column"
    :data-column-id="column.id"
    :class="{
      'board-column--expanded-empty': issues.length === 0,
      'board-column--drop-target': isDropTarget && !dragOverSwimlaneKey,
      'board-column--drop-forbidden': isDropTarget && !dragOverSwimlaneKey && !dropAllowed
    }"
    @dragover="$emit('dragover', $event, dropTargetStatusId)"
    @dragleave="$emit('dragleave', $event)"
    @drop="$emit('drop', $event, dropTargetStatusId)"
  >
    <div
      class="column-header column-header--clickable"
      :style="{ borderTopColor: column.color }"
      role="button"
      tabindex="0"
      :aria-label="`折叠 ${column.name} 列`"
      title="点击折叠此列"
      @click="$emit('toggle-collapse', column.id)"
      @keydown.enter="$emit('toggle-collapse', column.id)"
    >
      <span class="column-title">{{ column.name }}</span>
      <span
        class="column-count"
        :class="wipClass"
        :title="column.isMerged ? undefined : wipTooltip"
      >{{ issues.length }}<template v-if="wipMax !== null">/{{ wipMax }}</template></span>
      <span v-if="estimation" class="column-estimation" :title="'预估工时总计: ' + estimation + 'h'">⏱ {{ estimation }}h</span>
      <span v-if="wipWarning" class="wip-warning" :class="wipWarning">
        {{ wipWarning === 'wip-over' ? '⚠' : '▽' }}
      </span>
      <!-- 在工单列表中打开 -->
      <a
        class="column-open-in-list"
        :href="openInListUrl"
        target="_blank"
        rel="noopener noreferrer"
        :title="`在工单列表中打开「${column.name}」`"
        :aria-label="`在工单列表中打开「${column.name}」`"
        @click.stop
      >
        <icon-launch width="13" height="13" aria-hidden="true" />
      </a>
    </div>
    <div class="column-body">
      <slot name="cards" :issues="issues" />

      <!-- 空状态 -->
      <div
        v-if="issues.length === 0"
        class="column-empty-state"
        :class="{ 'column-empty-state--drop-hint': isDragging && dropAllowed }"
      >
        <template v-if="isDragging && dropAllowed">
          <EmptyState icon-emoji="📥" title="释放以移动到此状态" :compact="true" />
        </template>
        <template v-else-if="isDragging && !dropAllowed">
          <EmptyState icon-emoji="🚫" title="不允许转换到此状态" :compact="true" />
        </template>
        <template v-else>
          <EmptyState
            icon-emoji="📭"
            title="该状态下暂无工单"
            :description="isClosed ? '拖拽工单到此列' : '拖拽工单到此列或创建新工单'"
            :compact="true"
          />
        </template>
      </div>

      <!-- 内联快速创建卡片 -->
      <slot name="add-card" />
    </div>
  </div>

  <!-- 折叠状态的列（窄条） -->
  <div
    v-else
    class="board-column-collapsed"
    :data-column-id="column.id"
    :class="{
      'board-column-collapsed--drop-target': isDropTarget && dropAllowed,
      'board-column-collapsed--drop-forbidden': isDropTarget && !dropAllowed
    }"
    role="button"
    tabindex="0"
    :aria-label="`${column.name}，${issues.length} 个工单，点击展开`"
    :title="`${column.name} (${issues.length} 工单) - ${isDragging ? '释放以移动' : '点击展开'}`"
    @click="!isDragging && $emit('toggle-collapse', column.id)"
    @keydown.enter="$emit('toggle-collapse', column.id)"
    @dragover="$emit('dragover', $event, dropTargetStatusId)"
    @dragleave="$emit('dragleave', $event)"
    @drop="$emit('drop', $event, dropTargetStatusId)"
  >
    <div class="collapsed-indicator" :style="{ backgroundColor: column.color || 'var(--color-border)' }"></div>
    <span class="collapsed-name">{{ column.name }}</span>
    <span class="collapsed-count">{{ issues.length }}</span>
  </div>
</template>

<script setup lang="ts">
/**
 * KanbanColumn — 看板单列渲染组件。
 *
 * 职责：
 * - 渲染列头（标题、工单计数、WIP 限制、操作按钮）
 * - 展示列体（通过 slot 渲染卡片列表）
 * - 折叠/展开状态切换
 * - 拖放目标区域指示
 * - 空状态显示
 *
 * 不负责：
 * - 卡片渲染（通过 cards slot 委托）
 * - 拖拽业务逻辑（通过事件冒泡到父组件）
 * - 快速创建卡片表单（通过 add-card slot 委托）
 */
import type { IssueVO, BoardCardVO } from '@/api/types'
import { EmptyState } from '@/components/base'

type BoardIssue = IssueVO | BoardCardVO

export interface EffectiveColumn {
  id: string
  name: string
  color: string
  statusIds: string[]
  isMerged: boolean
}

defineProps<{
  /** 列配置（id, name, color, statusIds, isMerged） */
  column: EffectiveColumn
  /** 该列包含的工单列表 */
  issues: BoardIssue[]
  /** 是否已折叠 */
  collapsed: boolean
  /** 是否为当前拖放目标 */
  isDropTarget: boolean
  /** 是否允许放置到此列 */
  dropAllowed: boolean
  /** 全局是否有工单在拖拽中 */
  isDragging: boolean
  /** 当前拖拽悬停的泳道 key（非泳道场景为 null） */
  dragOverSwimlaneKey?: string | null
  /** 该列是否为已关闭状态 */
  isClosed: boolean
  /** 拖放目标 statusId（合并列用第一个 statusId） */
  dropTargetStatusId: string
  /** WIP 限制最大值（null 表示无限制） */
  wipMax: number | null
  /** WIP CSS 类名 */
  wipClass?: string
  /** WIP tooltip */
  wipTooltip?: string
  /** WIP 警告（'wip-over' | 'wip-near'） */
  wipWarning?: string
  /** 预估工时汇总 */
  estimation?: string
  /** 在工单列表中打开的 URL */
  openInListUrl: string
}>()

defineEmits<{
  'toggle-collapse': [columnId: string]
  'dragover': [event: DragEvent, statusId: string]
  'dragleave': [event: DragEvent]
  'drop': [event: DragEvent, statusId: string]
}>()
</script>
