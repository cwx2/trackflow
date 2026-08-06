<template>
  <div class="sprint-progress-placeholder">
    <span class="placeholder-icon">🏃</span>
    <span class="placeholder-text">点击「编辑配置」选择 Sprint</span>
  </div>
</template>

<script setup lang="ts">
// Sprint Progress 暂无独立渲染逻辑，当前显示引导提示。
// 后续接入 Sprint 数据后替换为真实进度条。
</script>

<style scoped>
.sprint-progress-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
}

.placeholder-icon {
  font-size: 32px;
  opacity: 0.4;
}

.placeholder-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: center;
}
</style>
