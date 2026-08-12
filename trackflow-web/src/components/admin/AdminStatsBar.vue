<template>
  <!--
    AdminStatsBar — 管理页面统计卡片栏
    横向排列若干统计指标卡片，每张卡片含图标、数值、标签。
    支持 loading 骨架态。
  -->
  <div class="stats-bar" :class="{ 'stats-bar--loading': loading }">
    <div
      v-for="(stat, i) in stats"
      :key="i"
      class="stat-card"
      :class="[`stat-card--${stat.color || 'blue'}`, { 'stat-card--clickable': !!stat.onClick }]"
      @click="stat.onClick?.call(null)"
    >
      <div class="stat-card__body">
        <div class="stat-card__info">
          <span class="stat-card__value">
            <template v-if="loading">
              <span class="stat-skeleton" />
            </template>
            <template v-else>{{ stat.value }}</template>
          </span>
          <span class="stat-card__label">{{ stat.label }}</span>
        </div>
        <div class="stat-card__icon" :class="`stat-card__icon--${stat.color || 'blue'}`">
          <component :is="stat.icon" :size="22" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

/**
 * 单条统计项
 */
export interface StatItem {
  /** 显示的数字或文字 */
  value: number | string
  /** 标签名称 */
  label: string
  /** Arco Design 图标组件 */
  icon: Component
  /** 色彩主题：blue / green / orange / purple / red / gray（默认 blue） */
  color?: 'blue' | 'green' | 'orange' | 'purple' | 'red' | 'gray'
  /** 可选点击回调（会显示 pointer 光标） */
  onClick?: () => void
}

defineProps<{
  stats: StatItem[]
  /** 是否显示加载骨架 */
  loading?: boolean
}>()
</script>

<style scoped>
/* ===== 容器 ===== */
.stats-bar {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  flex-shrink: 0;
  margin-bottom: 16px;
}

/* ===== 卡片 ===== */
.stat-card {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  padding: 16px;
  transition: border-color 150ms, background 150ms;
}

.stat-card--clickable {
  cursor: pointer;
}

.stat-card--clickable:hover {
  border-color: var(--tf-border);
  background: var(--tf-bg-elevated);
}

.stat-card__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stat-card__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  color: var(--tf-text-primary);
  line-height: 1.1;
  letter-spacing: -0.3px;
}

.stat-card__label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ===== 图标区 ===== */
.stat-card__icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card__icon--blue {
  background: var(--tf-accent-bg-light);
  color: var(--accent-blue);
}

.stat-card__icon--green {
  background: var(--tf-success-bg);
  color: var(--accent-green);
}

.stat-card__icon--orange {
  background: var(--tf-warning-bg);
  color: var(--tf-warning);
}

.stat-card__icon--purple {
  background: var(--tf-purple-bg, rgba(139,92,246,0.12));
  color: var(--accent-purple, #a78bfa);
}

.stat-card__icon--red {
  background: var(--tf-danger-bg);
  color: var(--accent-red);
}

.stat-card__icon--gray {
  background: var(--tf-bg-body);
  color: var(--tf-text-tertiary);
}

/* ===== 骨架态 ===== */
.stat-skeleton {
  display: inline-block;
  width: 48px;
  height: 28px;
  border-radius: 4px;
  background: linear-gradient(
    90deg,
    var(--tf-bg-body) 25%,
    var(--tf-bg-elevated) 50%,
    var(--tf-bg-body) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
}

@keyframes shimmer {
  0%   { background-position: 200% center; }
  100% { background-position: -200% center; }
}
</style>
