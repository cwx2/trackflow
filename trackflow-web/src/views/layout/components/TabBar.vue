<template>
  <div class="global-tab-bar">
    <!-- 标签区域 -->
    <div class="tab-list">
      <div
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-item"
        :class="{ active: tab.id === activeTabId }"
        @click="handleTabClick(tab)"
        @mousedown.middle.prevent="tab.closable && closeTab(tab.id)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <span
          v-if="tab.closable"
          class="tab-close"
          @click.stop="closeTab(tab.id)"
        >&times;</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useTabStore } from '@/stores/tabs'

const router = useRouter()
const tabStore = useTabStore()
const { tabs, activeTabId } = storeToRefs(tabStore)
const { closeTab, setActive } = tabStore

function handleTabClick(tab: { id: string; path: string }) {
  setActive(tab.id)
  router.push(tab.path)
}
</script>

<style scoped>
.global-tab-bar {
  display: flex;
  align-items: center;
  height: 36px;
  background: var(--tf-bg-surface);
  border-bottom: 1px solid var(--tf-border);
  padding: 0 8px;
  flex-shrink: 0;
}

.tab-list {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.tab-list::-webkit-scrollbar { display: none; }

.tab-item {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--tf-text-secondary);
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
  max-width: 180px;
}
.tab-item:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}
.tab-item.active {
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
  font-weight: 500;
}

.tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-close {
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  font-size: 13px;
  line-height: 1;
  color: var(--tf-text-tertiary);
  flex-shrink: 0;
  transition: background 0.15s, color 0.15s;
}
.tab-close:hover {
  background: var(--tf-bg-active);
  color: var(--tf-text-primary);
}
</style>
