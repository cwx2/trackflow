<template>
  <!--
    ===== 导航项区域 =====
    折叠时用 a-tooltip 包裹并隐藏文字，展开时直接渲染
    通过 inject 获取父级 AppSidebar 提供的折叠状态，不需要 prop 传递
  -->
  <a-tooltip
    v-if="collapsed"
    :content="label"
    position="right"
    :mini="true"
  >
    <component
      :is="to ? 'router-link' : 'div'"
      v-bind="to ? { to } : {}"
      class="nav-item"
      :class="{ active: isActive }"
      @click="!to ? $emit('click') : undefined"
    >
      <slot name="icon">
        <component :is="iconComponent" class="nav-icon" />
      </slot>
      <span class="nav-label">{{ label }}</span>
      <span v-if="badge && badge > 0" class="nav-badge">
        {{ badge > 99 ? '99+' : badge }}
      </span>
    </component>
  </a-tooltip>

  <component
    v-else
    :is="to ? 'router-link' : 'div'"
    v-bind="to ? { to } : {}"
    class="nav-item"
    :class="{ active: isActive }"
    @click="!to ? $emit('click') : undefined"
  >
    <slot name="icon">
      <component :is="iconComponent" class="nav-icon" />
    </slot>
    <span class="nav-label">{{ label }}</span>
    <span
      v-if="badge && badge > 0"
      class="nav-badge"
      :title="badgeTitle"
    >
      {{ badge > 99 ? '99+' : badge }}
    </span>
  </component>
</template>

<script setup lang="ts">
/**
 * SidebarNavItem — 侧边栏单个导航项
 *
 * 职责：
 * - 折叠时：用 a-tooltip 包裹，只显示图标
 * - 展开时：显示图标 + 文字 + 可选 badge
 * - 支持 router-link 跳转（传 to）和按钮行为（不传 to，emit click）
 *
 * Props：
 *   collapsed  — 是否折叠（从父级 AppSidebar 传入）
 *   to         — 路由目标，不传则渲染为 div 并 emit click
 *   label      — 菜单文字（折叠时作为 tooltip 内容）
 *   active     — 是否高亮（不传时由组件根据 $route 自动判断——仅 to 模式有效）
 *   badge      — 右侧数字角标
 *   badgeTitle — hover badge 时的 title 提示
 *   iconComponent — 图标组件（与 #icon slot 二选一）
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import type { Component } from 'vue'

const props = withDefaults(defineProps<{
  collapsed: boolean
  to?: string
  label: string
  active?: boolean
  badge?: number
  badgeTitle?: string
  iconComponent?: Component
}>(), {
  to: undefined,
  active: undefined,
  badge: 0,
  badgeTitle: '',
  iconComponent: undefined,
})

defineEmits<{ click: [] }>()

const route = useRoute()

// 如果外部显式传了 active，用外部的；否则根据 to 和当前路由自动判断
const isActive = computed(() => {
  if (props.active !== undefined) return props.active
  if (!props.to) return false
  return route.path === props.to || route.path.startsWith(props.to + '/')
})
</script>

<style scoped>
.nav-item {
  display: flex;
  align-items: center;
  height: 36px;
  gap: 10px;
  padding: 0 12px;
  border-radius: var(--tf-radius-md);
  color: var(--tf-sidebar-text);
  text-decoration: none;
  font-size: 13px;
  transition: background 0.15s, color 0.15s;
  cursor: pointer;
  overflow: hidden;
  white-space: nowrap;
}
.nav-item:hover {
  background: var(--tf-sidebar-hover);
  color: var(--tf-text-primary);
  text-decoration: none;
}
.nav-item.active {
  background: var(--tf-sidebar-active-bg);
  color: var(--tf-sidebar-active-text);
}

/* 折叠时图标居中 */
:global(.sidebar.collapsed) .nav-item {
  padding: 0;
  justify-content: center;
  gap: 0;
}

.nav-icon {
  font-size: 18px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
  color: inherit;
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 折叠时隐藏文字和 badge */
:global(.sidebar.collapsed) .nav-label,
:global(.sidebar.collapsed) .nav-badge {
  display: none;
}

.nav-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--tf-accent);
  color: var(--tf-text-on-accent);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  flex-shrink: 0;
}
</style>
