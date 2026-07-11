<template>
  <div class="dashboard">
    <h2>欢迎使用 TrackFlow</h2>
    <p>系统正在建设中...</p>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  min-height: 100%;
  text-align: center;
  color: var(--tf-text-secondary);
}

.dashboard h2 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.3px;
  color: var(--tf-text-primary);
  margin-bottom: 8px;
}

.dashboard p {
  font-size: 14px;
  color: var(--tf-text-tertiary);
  line-height: 1.5;
}
</style>
