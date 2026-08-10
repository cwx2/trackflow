<template>
  <Teleport to="body">
    <div class="toast-container">
      <TransitionGroup name="toast" tag="div" class="toast-list">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="toast-item"
          :class="`toast-${toast.type}`"
          :role="toast.type === 'error' ? 'alert' : 'status'"
          @click="handleClick(toast)"
        >
          <!-- 左侧类型色条 -->
          <div class="toast-accent" />

          <!-- 图标 -->
          <component :is="iconMap[toast.type]" class="toast-icon" :size="16" />

          <!-- 文字 -->
          <span class="toast-message">{{ toast.message }}</span>

          <!-- 关闭按钮 -->
          <button class="toast-close" title="关闭" @click.stop="dismiss(toast.id)">
            <icon-close :size="12" />
          </button>

          <!-- 进度条（duration > 0 时显示） -->
          <div
            v-if="toast.duration > 0"
            class="toast-progress"
            :style="{ animationDuration: `${toast.duration}ms` }"
          />
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { onUnmounted } from 'vue'
import {
  IconClose,
  IconCheckCircle,
  IconInfoCircle,
  IconExclamationCircle,
  IconCloseCircle
} from '@arco-design/web-vue/es/icon'
import { useToast, type ToastItem } from '@/composables/useToast'

const { toasts, dismiss } = useToast()

const iconMap = {
  info:    IconInfoCircle,
  success: IconCheckCircle,
  warning: IconExclamationCircle,
  error:   IconCloseCircle,
}

// 自动消失定时器
const timers = new Map<number, ReturnType<typeof setTimeout>>()

function scheduleClose(toast: ToastItem) {
  if (toast.duration <= 0) return
  if (timers.has(toast.id)) return
  const t = setTimeout(() => {
    dismiss(toast.id)
    timers.delete(toast.id)
  }, toast.duration)
  timers.set(toast.id, t)
}

// 监听新增 toast，启动定时器
// deep: true 确保数组 push 时能检测到变化
import { watch } from 'vue'
watch(
  toasts,
  (list) => {
    list.forEach(scheduleClose)
    // 清理已不存在 toast 的定时器
    const ids = new Set(list.map(t => t.id))
    timers.forEach((_, id) => {
      if (!ids.has(id)) {
        clearTimeout(timers.get(id))
        timers.delete(id)
      }
    })
  },
  { deep: true, immediate: true }
)

onUnmounted(() => {
  timers.forEach(clearTimeout)
  timers.clear()
})

function handleClick(toast: ToastItem) {
  if (toast.onClick) {
    toast.onClick()
    dismiss(toast.id)
  }
}
</script>

<style scoped>
/* 容器：固定在右上角 */
.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 9999;
  pointer-events: none;
}

.toast-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
}

/* 单条 toast */
.toast-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 320px;
  padding: 12px 14px 14px 0;  /* 底部多留 2px 给进度条视觉空间 */
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-subtle);
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.18);
  overflow: hidden;
  pointer-events: all;
  cursor: default;
}

.toast-item.toast-info    { border-left: none; }
.toast-item.toast-success { border-left: none; }
.toast-item.toast-warning { border-left: none; }
.toast-item.toast-error   { border-left: none; }

/* 左侧色条 */
.toast-accent {
  width: 4px;
  align-self: stretch;
  flex-shrink: 0;
  border-radius: 0;
}

.toast-info    .toast-accent { background: var(--tf-accent); }
.toast-success .toast-accent { background: var(--tf-success, #3fb950); }
.toast-warning .toast-accent { background: var(--tf-warning, #d29922); }
.toast-error   .toast-accent { background: var(--tf-error, #f85149); }

/* 图标 */
.toast-icon {
  flex-shrink: 0;
}

.toast-info    .toast-icon { color: var(--tf-accent); }
.toast-success .toast-icon { color: var(--tf-success, #3fb950); }
.toast-warning .toast-icon { color: var(--tf-warning, #d29922); }
.toast-error   .toast-icon { color: var(--tf-error, #f85149); }

/* 文字 */
.toast-message {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  line-height: 1.4;
}

/* 有 onClick 时显示手形 */
.toast-item:has(.toast-message[data-clickable]) {
  cursor: pointer;
}

/* 关闭按钮 */
.toast-close {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: none;
  border: none;
  border-radius: 4px;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  transition: color 150ms, background 150ms;
}

.toast-close:hover {
  color: var(--tf-text-primary);
  background: var(--tf-bg-hover);
}

/* 底部进度条 */
.toast-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--tf-border-subtle);
  transform-origin: left;
  animation: toast-progress linear forwards;
}

.toast-info    .toast-progress { background: var(--tf-accent); }
.toast-success .toast-progress { background: var(--tf-success, #3fb950); }
.toast-warning .toast-progress { background: var(--tf-warning, #d29922); }
.toast-error   .toast-progress { background: var(--tf-error, #f85149); }

@keyframes toast-progress {
  from { transform: scaleX(1); }
  to   { transform: scaleX(0); }
}

/* 滑入从右侧 / 滑出向右 */
.toast-enter-active {
  transition: transform 280ms cubic-bezier(0.34, 1.56, 0.64, 1), opacity 220ms ease;
}
.toast-leave-active {
  transition: transform 200ms ease-in, opacity 180ms ease;
}
.toast-enter-from {
  transform: translateX(calc(100% + 20px));
  opacity: 0;
}
.toast-leave-to {
  transform: translateX(calc(100% + 20px));
  opacity: 0;
}

/* 其他条目位移补偿 */
.toast-move {
  transition: transform 250ms ease;
}
</style>
