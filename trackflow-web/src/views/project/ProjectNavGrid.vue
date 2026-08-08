<template>
  <div class="section">
    <h2 class="section-title">功能入口</h2>
    <div class="nav-grid">
      <div class="nav-card" @click="$emit('navigate', 'issues')">
        <div class="nav-card-icon issues-icon">
          <icon-list />
        </div>
        <div class="nav-card-info">
          <span class="nav-card-title">问题列表</span>
          <span class="nav-card-desc">查看和管理项目工单</span>
        </div>
        <icon-right class="nav-card-arrow" />
      </div>

      <div class="nav-card" @click="$emit('navigate', 'board')">
        <div class="nav-card-icon board-icon">
          <icon-apps />
        </div>
        <div class="nav-card-info">
          <span class="nav-card-title">看板</span>
          <span class="nav-card-desc">可视化任务流转状态</span>
        </div>
        <icon-right class="nav-card-arrow" />
      </div>

      <div v-if="canViewSprints" class="nav-card" @click="$emit('navigate', 'sprints')">
        <div class="nav-card-icon sprint-icon">
          <icon-thunderbolt />
        </div>
        <div class="nav-card-info">
          <span class="nav-card-title">迭代</span>
          <span class="nav-card-desc">查看 Sprint 计划和进度</span>
        </div>
        <icon-right class="nav-card-arrow" />
      </div>

      <div v-if="canManageMembers && !isArchived" class="nav-card" @click="$emit('navigate', 'members')">
        <div class="nav-card-icon members-icon">
          <icon-user-group />
        </div>
        <div class="nav-card-info">
          <span class="nav-card-title">成员管理</span>
          <span class="nav-card-desc">管理项目成员和角色</span>
        </div>
        <icon-right class="nav-card-arrow" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  IconList,
  IconApps,
  IconThunderbolt,
  IconRight,
  IconUserGroup
} from '@arco-design/web-vue/es/icon'

defineProps<{
  canViewSprints: boolean
  canManageMembers: boolean
  isArchived: boolean
}>()

defineEmits<{
  navigate: [target: 'issues' | 'board' | 'sprints' | 'members']
}>()
</script>

<style scoped>
.section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 12px;
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.nav-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.nav-card:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-accent);
  box-shadow: var(--tf-shadow);
}

.nav-card-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
  color: var(--tf-text-on-accent);
}

.issues-icon { background: #3f51b5; }
.board-icon { background: #009688; }
.sprint-icon { background: #ff9800; }
.members-icon { background: #9c27b0; }

.nav-card-info {
  flex: 1;
  min-width: 0;
}

.nav-card-title {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.nav-card-desc {
  display: block;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-top: 2px;
}

.nav-card-arrow {
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  font-size: 14px;
}
</style>
