<template>
  <!--
    ===== SidebarNavItem =====
    折叠时用 a-tooltip 包裹，内容体只写一份。
    利用 <component :is> 实现「条件包裹」：
      - collapsed → 外层是 a-tooltip，内层是导航项
      - 展开    → 外层是 <template>（透明包裹，不渲染 DOM），内层是导航项
    这样导航项本身的模板只存在一份。
  -->
  <component
    :is="collapsed ? 'a-tooltip' : virtualTag"
    v-bind="collapsed ? { content: label, position: 'right', mini: true } : {}"
  >
    <component
      :is="to ? 'router-link' : 'div'"
      v-bind="to ? { to } : {}"
      class="nav-item"
      :class="[{ active: isActive }, itemClass]"
      @click="!to ? $emit('click') : undefined"
    >
      <slot name="icon">
        <component :is="iconComponent" class="nav-icon" />
      </slot>
      <span class="nav-label">{{ label }}</span>
      <span
        v-if="badge && badge > 0"
        class="nav-badge"
        :title="badgeTitle || `${badge} 条`"
      >
        {{ badge > 99 ? '99+' : badge }}
      </span>
    </component>
  </component>
</template>

<script setup lang="ts">
/**
 * SidebarNavItem — 侧边栏单个导航项
 *
 * 职责：
 * - 折叠时：用 a-tooltip 包裹，只显示图标
 * - 展开时：显示图标 + 文字 + 可选 badge
 * - 支持 router-link（传 to）和按钮行为（不传 to，emit click）
 *
 * Props：
 *   collapsed      — 是否折叠，由父级 AppSidebar 传入
 *   to             — 路由目标，不传则渲染 div 并 emit click
 *   label          — 菜单文字，折叠时作为 tooltip 内容
 *   active         — 是否高亮，不传时根据 $route 自动判断
 *   badge          — 右侧数字角标
 *   badgeTitle     — badge 的 title 提示文字
 *   iconComponent  — 图标组件（与 #icon slot 二选一）
 *   itemClass      — 透传给导航项的额外 class（如 theme-switcher 的虚线边框）
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import type { Component } from 'vue'

// 透明包裹标签：渲染为 <template>（不生成 DOM），用于展开态不需要 tooltip 包裹时
// Vue 3 中可以用 defineComponent 定义一个只 render slot 的透明组件
// inheritAttrs: false 防止 Vue 尝试透传 attrs 到 fragment root 时产生警告
const virtualTag = {
  name: 'VirtualWrapper',
  inheritAttrs: false,
  render() {
    return (this as any).$slots.default?.()
  },
}

const props = withDefaults(defineProps<{
  collapsed: boolean
  to?: string
  label: string
  active?: boolean
  badge?: number
  badgeTitle?: string
  iconComponent?: Component
  itemClass?: string
}>(), {
  to: undefined,
  active: undefined,
  badge: 0,
  badgeTitle: '',
  iconComponent: undefined,
  itemClass: '',
})

defineEmits<{ click: [] }>()

const route = useRoute()

// 外部显式传 active 优先；否则根据 to 和当前路由自动判断
const isActive = computed(() => {
  if (props.active !== undefined) return props.active
  if (!props.to) return false
  // 精确匹配或前缀匹配（子路由也高亮父导航项）
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

/* 折叠时：图标居中，文字/badge 隐藏 */
:global(.sidebar.collapsed) .nav-item {
  padding: 0;
  justify-content: center;
  gap: 0;
}
:global(.sidebar.collapsed) .nav-label,
:global(.sidebar.collapsed) .nav-badge {
  display: none;
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
