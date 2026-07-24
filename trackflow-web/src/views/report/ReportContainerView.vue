<template>
  <div class="report-container">
    <!-- 页面头部（仅在非详情页时显示） -->
    <template v-if="!isDetailPage">
      <div class="report-header">
        <div class="header-left">
          <h1 class="page-title">报表</h1>
          <span class="page-desc">项目数据概览与可视化分析</span>
        </div>
      </div>

      <!-- 统一标签导航 -->
      <nav class="report-nav">
        <router-link
          v-for="tab in tabs"
          :key="tab.key"
          :to="tab.to"
          class="nav-tab"
          :class="{ active: isTabActive(tab.key) }"
        >
          <span class="tab-icon">{{ tab.icon }}</span>
          {{ tab.label }}
        </router-link>
      </nav>
    </template>

    <!-- 子路由出口 -->
    <div class="report-content">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const isDetailPage = computed(() => route.name === 'ReportDetail')

const tabs = [
  { key: 'overview', label: '概览', icon: '📊', to: '/reports' },
  { key: 'list', label: '报表列表', icon: '📋', to: '/reports/list' },
  { key: 'time', label: '时间报表', icon: '⏱️', to: '/reports/time' },
  { key: 'estimation', label: '预估对比', icon: '📐', to: '/reports/estimation' },
  { key: 'dashboards', label: '仪表盘', icon: '🖥️', to: '/reports/dashboards' }
]

function isTabActive(key: string): boolean {
  const name = route.name as string
  switch (key) {
    case 'overview':
      return name === 'ReportOverview'
    case 'list':
      return name === 'ReportList'
    case 'time':
      return name === 'TimeReport'
    case 'estimation':
      return name === 'EstimationReport'
    case 'dashboards':
      return name === 'CustomDashboards'
    default:
      return false
  }
}
</script>

<style scoped>
.report-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.report-header {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 24px 32px 0;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
  letter-spacing: -0.3px;
}

.page-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

/* 统一标签导航 */
.report-nav {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 16px 32px 0;
  border-bottom: 1px solid var(--tf-border-light);
}

.nav-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  text-decoration: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
  margin-bottom: -1px;
}

.nav-tab:hover {
  color: var(--tf-text-primary);
}

.nav-tab.active {
  color: var(--tf-accent);
  border-bottom-color: var(--tf-accent);
}

.tab-icon {
  font-size: 14px;
}

/* 内容区域 */
.report-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
</style>
