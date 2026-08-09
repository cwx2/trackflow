<template>
  <div class="sprint-no-active-state">
    <!-- 主提示区域 -->
    <div class="no-active-hero">
      <div class="no-active-icon">🏁</div>
      <h3 class="no-active-title">当前项目没有进行中的迭代</h3>
      <p class="no-active-desc">所有迭代均已完成。创建新迭代来规划下一阶段的团队工作。</p>
      <div class="no-active-actions">
        <a-button v-if="canCreate" type="primary" size="small" @click="$emit('create')">
          + 创建新迭代
        </a-button>
        <a-button size="small" @click="$emit('viewBacklog')">
          📋 查看 Backlog
        </a-button>
      </div>
    </div>

    <!-- 遗留工单警告 -->
    <div v-if="lingeringIssueCount > 0" class="lingering-warning">
      <span class="lingering-icon">⚠️</span>
      <span class="lingering-text">
        有 <strong>{{ lingeringIssueCount }}</strong> 个工单仍在已完成迭代中未处理
      </span>
      <a-button size="mini" type="text" @click="$emit('viewLingering')">
        查看详情 →
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  canCreate: boolean
  lingeringIssueCount: number
}>()

defineEmits<{
  create: []
  viewBacklog: []
  viewLingering: []
}>()
</script>

<style scoped>
.sprint-no-active-state {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.no-active-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 40px 24px 32px;
  background: var(--color-fill-1);
  border-radius: 8px;
  border: 1px dashed var(--color-border-2);
}

.no-active-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.no-active-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-1);
  margin: 0 0 8px 0;
}

.no-active-desc {
  font-size: 13px;
  color: var(--color-text-3);
  margin: 0 0 20px 0;
  max-width: 360px;
  line-height: 1.5;
}

.no-active-actions {
  display: flex;
  gap: 8px;
}

.lingering-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 6px;
  background: var(--tf-warning-bg, rgba(255, 180, 0, 0.08));
  border: 1px solid rgba(var(--warning-6), 0.25);
}

.lingering-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.lingering-text {
  flex: 1;
  font-size: 13px;
  color: var(--color-text-1);
  line-height: 1.4;
}

.lingering-text strong {
  color: var(--color-warning-light-4, var(--color-warning-6));
}
</style>
