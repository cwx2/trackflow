<template>
  <!-- ===== 计时器 Badge（侧边栏底部常驻显示） ===== -->
  <template v-if="timerStore.isRunning">
    <a-tooltip
      v-if="collapsed"
      :content="`${timerStore.issueKey} — ${timerStore.elapsedDisplay}`"
      position="right"
      :mini="true"
    >
      <div
        class="footer-item timer-badge"
        @click="togglePopover"
      >
        <span class="nav-icon timer-icon-wrap">
          <icon-clock-circle />
          <span class="timer-pulse"></span>
        </span>
        <span class="nav-label timer-badge-label">
          <span class="timer-badge-key">{{ timerStore.issueKey }}</span>
          <span class="timer-elapsed">{{ timerStore.elapsedDisplay }}</span>
        </span>
      </div>
    </a-tooltip>

    <div
      v-else
      class="footer-item timer-badge"
      :title="`${timerStore.issueKey} ${timerStore.issueTitle} — ${timerStore.elapsedDisplay}`"
      @click="togglePopover"
    >
      <span class="nav-icon timer-icon-wrap">
        <icon-clock-circle />
        <span class="timer-pulse"></span>
      </span>
      <span class="nav-label timer-badge-label">
        <span class="timer-badge-key">{{ timerStore.issueKey }}</span>
        <span class="timer-elapsed">{{ timerStore.elapsedDisplay }}</span>
      </span>
    </div>

    <!-- ===== 计时器快捷面板（点击 badge 后展开） ===== -->
    <div v-if="visible" class="timer-popover">
      <div class="timer-popover-header">
        <icon-clock-circle class="timer-popover-icon" />
        <span class="timer-popover-title">正在计时</span>
      </div>
      <div class="timer-popover-body">
        <div class="timer-issue" @click="goToIssue">
          <span class="timer-issue-key">{{ timerStore.issueKey }}</span>
          <span class="timer-issue-title">{{ timerStore.issueTitle }}</span>
        </div>
        <div class="timer-elapsed-large">{{ timerStore.elapsedDisplay }}</div>
      </div>
      <div class="timer-popover-footer">
        <button
          class="timer-stop-btn"
          :disabled="timerStore.loading"
          @click="handleStop"
        >
          <icon-minus-circle /> 停止计时
        </button>
      </div>
    </div>
  </template>
</template>

<script setup lang="ts">
/**
 * TimerPopover — 计时器 Badge + 快捷面板
 *
 * 职责：
 * - 计时器运行时在侧边栏底部显示 Badge（工单号 + 已用时间）
 * - 点击 Badge 展开浮层，可跳转到工单或停止计时
 * - 折叠时 Badge 只显示图标，hover 展示 tooltip
 *
 * Props：
 *   collapsed — 侧边栏是否折叠（影响 badge 展示方式）
 *
 * Emits：
 *   close — 浮层关闭时通知父级（用于点击外部关闭）
 */
import { useRouter } from 'vue-router'
import { useTimerStore } from '@/stores/timer'

defineProps<{ collapsed: boolean }>()
defineEmits<{ close: [] }>()

const router = useRouter()
const timerStore = useTimerStore()

const visible = defineModel<boolean>('visible', { default: false })

function togglePopover() {
  visible.value = !visible.value
}

function goToIssue() {
  visible.value = false
  if (timerStore.issueKey) {
    router.push(`/issues/${timerStore.issueKey}`)
  } else if (timerStore.issueId) {
    router.push(`/issues/${timerStore.issueId}`)
  }
}

async function handleStop() {
  const result = await timerStore.stopTimer()
  if (result.success) {
    visible.value = false
  }
}
</script>

<style scoped>
/* ===== 复用 footer-item 基础样式（与 AppSidebar 对齐） ===== */
.footer-item {
  display: flex;
  align-items: center;
  height: 36px;
  gap: 10px;
  padding: 0 12px;
  border-radius: var(--tf-radius-md);
  color: var(--tf-sidebar-text);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.footer-item:hover {
  background: var(--tf-sidebar-hover);
  color: var(--tf-text-primary);
}

.nav-icon {
  font-size: 18px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
  color: inherit;
}

/* ===== 计时器 Badge ===== */
.timer-icon-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.timer-pulse {
  position: absolute;
  top: -2px;
  right: -4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tf-success);
  animation: timer-pulse-anim 1.5s ease-in-out infinite;
}

@keyframes timer-pulse-anim {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(1.3); }
}

.timer-badge-label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.timer-badge-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 80px;
}

.timer-elapsed {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-success);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  flex-shrink: 0;
}

/* 折叠时隐藏文字 */
:global(.sidebar.collapsed) .nav-label,
:global(.sidebar.collapsed) .timer-badge-label {
  display: none;
}
:global(.sidebar.collapsed) .footer-item {
  padding: 0;
  justify-content: center;
  gap: 0;
}

/* ===== 计时器浮层 ===== */
.timer-popover {
  position: absolute;
  bottom: 100%;
  left: 8px;
  margin-bottom: 8px;
  width: 220px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border, rgba(255,255,255,0.1));
  border-radius: 8px;
  padding: 12px;
  box-shadow: var(--tf-shadow-lg);
  z-index: 100;
}

.timer-popover-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.timer-popover-icon {
  font-size: 16px;
  color: inherit;
}

.timer-popover-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.timer-popover-body {
  margin-bottom: 12px;
}

.timer-issue {
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 4px;
  margin-bottom: 8px;
  transition: background 150ms;
}
.timer-issue:hover {
  background: var(--tf-bg-hover, rgba(255,255,255,0.06));
}

.timer-issue-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent);
  margin-right: 6px;
}

.timer-issue-title {
  font-size: 12px;
  color: var(--tf-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timer-elapsed-large {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  text-align: center;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.3px;
}

.timer-popover-footer {
  border-top: 1px solid var(--tf-border, rgba(255,255,255,0.1));
  padding-top: 10px;
}

.timer-stop-btn {
  width: 100%;
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  background: var(--tf-danger);
  color: var(--tf-text-on-accent);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 150ms;
}
.timer-stop-btn:hover { opacity: 0.9; }
.timer-stop-btn:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
