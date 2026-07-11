<template>
  <header class="detail-topbar">
    <div class="topbar-left">
      <button class="icon-btn" @click="$router.back()" title="返回">
        <icon-left :size="16" />
      </button>
      <nav class="breadcrumb">
        <a class="crumb" @click="$router.push('/')">{{ projectName }}</a>
        <span class="sep">/</span>
        <span class="crumb-current">{{ issueKey }}</span>
      </nav>
      <span class="meta">
        创建者 <b>{{ reporter }}</b> · {{ createdAgo }}
        &nbsp;·&nbsp; 更新 {{ updatedAgo }}
      </span>
    </div>
    <div class="topbar-right">
      <!-- 前后导航 -->
      <div class="nav-group" v-if="(total ?? 0) > 0">
        <button class="icon-btn" :disabled="(index ?? 0) <= 1" @click="$emit('prev')"><icon-left :size="12" /></button>
        <span class="nav-pos">{{ index }} / {{ total }}</span>
        <button class="icon-btn" :disabled="(index ?? 0) >= (total ?? 0)" @click="$emit('next')"><icon-right :size="12" /></button>
      </div>
      <button class="icon-btn" @click="$emit('copy')" title="复制"><icon-copy :size="14" /></button>
      <button class="icon-btn" @click="$emit('toggle-sidebar')" title="面板"><icon-menu :size="14" /></button>
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  projectName: string
  issueKey: string
  reporter: string
  createdAgo: string
  updatedAgo: string
  index?: number
  total?: number
}>()

defineEmits<{
  prev: []
  next: []
  copy: []
  'toggle-sidebar': []
}>()
</script>

<style scoped>
.detail-topbar {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
  background: var(--tf-bg-surface);
}

.topbar-left, .topbar-right { display: flex; align-items: center; gap: 8px; }
.topbar-right { gap: 4px; }

.icon-btn {
  background: none;
  border: none;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  padding: 4px;
  border-radius: 3px;
  display: inline-flex;
  align-items: center;
  transition: color 150ms, background 150ms;
}
.icon-btn:hover:not(:disabled) { background: var(--tf-bg-hover); color: var(--tf-text-primary); }
.icon-btn:disabled { opacity: 0.3; cursor: default; }

.breadcrumb { display: flex; align-items: center; gap: 4px; font-size: 13px; }
.crumb {
  color: var(--tf-text-secondary);
  cursor: pointer;
  transition: color 150ms;
}
.crumb:hover { color: var(--tf-accent); }
.sep { color: var(--tf-text-muted); font-size: 12px; }
.crumb-current { color: var(--tf-text-primary); font-weight: 500; }

.meta { font-size: 11px; color: var(--tf-text-muted); margin-left: 8px; }
.meta b { color: var(--tf-text-secondary); font-weight: 500; }

.nav-group { display: flex; align-items: center; gap: 4px; margin-right: 8px; }
.nav-pos { font-size: 11px; color: var(--tf-text-muted); min-width: 36px; text-align: center; }
</style>
