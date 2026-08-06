<template>
  <div class="settings-section">
    <h3 class="section-title">已静音的工单</h3>
    <p class="section-desc">你不会收到这些工单的通知（@提及除外）。取消静音后恢复正常通知推送。</p>

    <div v-if="loading" class="muted-loading">
      <a-spin size="small" />
      <span>加载中...</span>
    </div>
    <div v-else-if="!threads || threads.length === 0" class="muted-empty">
      <span class="muted-empty-icon">🔔</span>
      <span class="muted-empty-text">暂无静音的工单</span>
    </div>
    <div v-else class="muted-list">
      <div v-for="thread in threads" :key="thread.id" class="muted-item">
        <div class="muted-item-info">
          <span class="muted-item-title">{{ thread.resourceTitle }}</span>
          <span class="muted-item-time">{{ formatMutedTime(thread.createdAt) }}</span>
        </div>
        <button class="muted-item-unmute" @click="handleUnmute(thread)">取消静音</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { notificationApi } from '@/api'
import type { MutedThreadVO } from '@/api/notification'
import { useRequest } from '@/composables/useRequest'

const { data: threads, loading, execute: reload } = useRequest(
  () => notificationApi.listMutedThreads(),
  { immediate: true }
)

async function handleUnmute(thread: MutedThreadVO) {
  try {
    const res = await notificationApi.unmuteThread(thread.resourceType, thread.resourceId)
    if (res.code === 0) {
      // Optimistically remove from local list
      if (threads.value) {
        threads.value = threads.value.filter(t => t.id !== thread.id)
      }
      Message.success('已取消静音')
    }
  } catch {
    Message.error('操作失败')
  }
}

function formatMutedTime(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) + ' 静音'
}

defineExpose({ reload })
</script>

<style scoped>
.settings-section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
}

.section-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0 0 16px 0;
}

.muted-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.muted-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.muted-empty-icon {
  font-size: 16px;
  opacity: 0.5;
}

.muted-empty-text {
  color: var(--tf-text-tertiary);
}

.muted-list {
  display: flex;
  flex-direction: column;
}

.muted-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-radius: 6px;
  transition: background 0.15s;
}

.muted-item:hover {
  background: var(--tf-bg-hover);
}

.muted-item + .muted-item {
  border-top: 1px solid var(--tf-border-subtle, rgba(255,255,255,0.04));
}

.muted-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.muted-item-title {
  font-size: 13px;
  color: var(--tf-text-primary);
  font-weight: 500;
}

.muted-item-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.muted-item-unmute {
  padding: 4px 10px;
  border: 1px solid var(--tf-border);
  background: transparent;
  border-radius: 4px;
  font-size: 11px;
  color: var(--tf-text-secondary);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.muted-item-unmute:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-accent);
  color: var(--tf-accent);
}
</style>
