<template>
  <!-- ===== 计时器 Badge + 快捷面板 ===== -->
  <template v-if="timerStore.isRunning">
    <!--
      折叠/展开条件包裹：内容体只写一份。
      展开时外层是透明 VirtualWrapper，折叠时外层是 a-tooltip。
    -->
    <component
      :is="collapsed ? 'a-tooltip' : virtualTag"
      :content="collapsed ? `${timerStore.issueKey} — ${timerStore.elapsedDisplay}` : undefined"
      position="right"
      :mini="true"
    >
      <div
        class="footer-item timer-badge"
        :title="!collapsed ? `${timerStore.issueKey} ${timerStore.issueTitle} — ${timerStore.elapsedDisplay}` : undefined"
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
    </component>

    <!-- 快捷面板（点击 badge 后展开） -->
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
 * - 折叠时 Badge 只显示图标，hover 显示 tooltip
 *
 * CSS 说明：
 * - .footer-item / .nav-icon / .nav-label / 折叠隐藏规则 均由 AppSidebar 的全局样式提供
 * - 本文件只写计时器专属样式（timer-*）
 */
import { useRouter } from 'vue-router'
import { useTimerStore } from '@/stores/timer'

// 透明包裹：展开态不需要 tooltip，用此组件替代 a-tooltip 做无 DOM 的透传
const virtualTag = {
  name: 'VirtualWrapper',
  render() { return (this as any).$slots.default?.() },
}

defineProps<{ collapsed: boolean }>()

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
  if (result.success) visible.value = false
}
</script>

<style scoped>
/* ===== 计时器专属样式（.footer-item / .nav-icon 由 AppSidebar 提供） ===== */
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
