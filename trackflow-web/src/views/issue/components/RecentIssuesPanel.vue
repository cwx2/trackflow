<template>
  <div v-if="isPanelVisible" class="recent-issues-panel">
    <div class="recent-issues-items">
      <div
        v-for="item in displayItems"
        :key="item.id"
        class="recent-item"
        :class="{ pinned: item.pinned }"
      >
        <router-link
          :to="{ name: 'IssueDetail', params: { id: item.issueKey } }"
          class="recent-item-link"
          :title="item.title"
        >
          <icon-pushpin v-if="item.pinned" class="pin-icon" />
          <span class="recent-item-key">{{ item.issueKey }}</span>
          <span class="recent-item-title">{{ item.title }}</span>
        </router-link>
        <a-dropdown trigger="hover" position="br" :popup-max-height="false">
          <a-button type="text" size="mini" class="recent-item-action">
            <template #icon><icon-more /></template>
          </a-button>
          <template #content>
            <a-doption v-if="!item.pinned" @click="pinItem(item.id)">
              <template #icon><icon-pushpin /></template>
              固定
            </a-doption>
            <a-doption v-else @click="unpinItem(item.id)">
              <template #icon><icon-pushpin /></template>
              取消固定
            </a-doption>
            <a-doption @click="removeItem(item.id)">
              <template #icon><icon-close /></template>
              移除
            </a-doption>
          </template>
        </a-dropdown>
      </div>
    </div>
    <div class="recent-issues-actions">
      <a-dropdown trigger="click" position="br" :popup-max-height="false">
        <a-button type="text" size="mini" class="recent-panel-btn">
          <template #icon><icon-more-vertical /></template>
        </a-button>
        <template #content>
          <a-doption v-if="hasMoreItems" @click="showMore = !showMore">
            <template #icon><icon-list /></template>
            {{ showMore ? '收起' : '显示更多最近项目' }}
          </a-doption>
          <a-doption @click="closeUnpinnedItems">
            <template #icon><icon-close-circle /></template>
            关闭未固定项
          </a-doption>
          <a-doption @click="hideAll">
            <template #icon><icon-eye-invisible /></template>
            隐藏全部
          </a-doption>
        </template>
      </a-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRecentIssues } from '../composables/useRecentIssues'

const {
  visibleItems,
  isPanelVisible,
  pinItem,
  unpinItem,
  removeItem,
  closeUnpinnedItems,
  hideAll,
} = useRecentIssues()

const showMore = ref(false)
const COLLAPSED_COUNT = 6

const hasMoreItems = computed(() => visibleItems.value.length > COLLAPSED_COUNT)

const displayItems = computed(() => {
  if (showMore.value) return visibleItems.value
  return visibleItems.value.slice(0, COLLAPSED_COUNT)
})
</script>

<style scoped>
.recent-issues-panel {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  margin-bottom: 8px;
  background: var(--tf-bg-surface, var(--color-fill-1));
  border-radius: 6px;
  border: 1px solid var(--tf-border-light, var(--color-border-1));
  overflow: hidden;
  min-height: 32px;
}

.recent-issues-items {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  flex: 1;
  overflow: hidden;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 2px;
  max-width: 220px;
  border-radius: 4px;
  padding: 2px 4px;
  transition: background 150ms;
}

.recent-item:hover {
  background: var(--tf-bg-hover, var(--color-fill-2));
}

.recent-item.pinned {
  background: var(--tf-bg-hover, var(--color-fill-2));
}

.recent-item-link {
  display: flex;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  color: var(--tf-text-secondary, var(--color-text-2));
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
}

.recent-item-link:hover {
  color: var(--tf-text-primary, var(--color-text-1));
}

.pin-icon {
  font-size: 10px;
  color: var(--tf-accent, var(--color-primary-6));
  flex-shrink: 0;
}

.recent-item-key {
  font-weight: 500;
  color: var(--tf-accent, var(--color-primary-6));
  flex-shrink: 0;
}

.recent-item-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-item-action {
  opacity: 0;
  transition: opacity 150ms;
  flex-shrink: 0;
}

.recent-item:hover .recent-item-action {
  opacity: 1;
}

.recent-issues-actions {
  flex-shrink: 0;
  margin-left: 4px;
}

.recent-panel-btn {
  color: var(--tf-text-tertiary, var(--color-text-3));
}
</style>
