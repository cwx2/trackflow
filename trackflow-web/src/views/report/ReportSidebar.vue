<template>
  <aside class="report-sidebar" :class="{ collapsed: isCollapsed }">
    <!-- Toggle button -->
    <button class="sidebar-toggle" @click="isCollapsed = !isCollapsed" :title="isCollapsed ? '展开面板' : '收起面板'">
      <span class="toggle-icon">{{ isCollapsed ? '▶' : '◀' }}</span>
    </button>

    <template v-if="!isCollapsed">
      <!-- Sidebar header -->
      <div class="sidebar-header">
        <span class="sidebar-title">报表导航</span>
      </div>

      <!-- Search -->
      <div class="sidebar-search">
        <input
          v-model="searchKeyword"
          type="text"
          class="search-input"
          placeholder="搜索报表..."
        />
      </div>

      <!-- Scrollable content -->
      <div class="sidebar-content">
        <!-- Favorites section -->
        <div v-if="favoriteReports.length > 0" class="sidebar-section">
          <div class="section-header">
            <span class="section-icon">★</span>
            <span class="section-title">收藏</span>
            <span class="section-count">{{ favoriteReports.length }}</span>
          </div>
          <div class="section-items">
            <div
              v-for="report in favoriteReports"
              :key="report.id"
              class="sidebar-item"
              :class="{ active: activeReportId === report.id }"
              @click="navigateToReport(report)"
              @mouseenter="hoveredReportId = report.id"
              @mouseleave="hoveredReportId = null"
            >
              <span class="item-type-dot" :class="'dot-' + getCategoryClass(report.type)"></span>
              <span class="item-name" :title="report.name">{{ report.name }}</span>
              <button
                v-show="hoveredReportId === report.id"
                class="item-action unfavorite-btn"
                title="取消收藏"
                @click.stop="handleToggleFavorite(report)"
              >
                ★
              </button>
            </div>
          </div>
        </div>

        <!-- By type groups -->
        <div
          v-for="group in typeGroups"
          :key="group.category"
          class="sidebar-section"
        >
          <div
            class="section-header clickable"
            @click="toggleGroupCollapse(group.category)"
          >
            <span class="section-icon">{{ group.icon }}</span>
            <span class="section-title">{{ group.label }}</span>
            <span class="section-count">{{ group.reports.length }}</span>
            <span class="collapse-icon" :class="{ collapsed: collapsedGroups.has(group.category) }">▾</span>
          </div>
          <div v-show="!collapsedGroups.has(group.category)" class="section-items">
            <div
              v-for="report in group.reports"
              :key="report.id"
              class="sidebar-item"
              :class="{ active: activeReportId === report.id }"
              @click="navigateToReport(report)"
              @mouseenter="hoveredReportId = report.id"
              @mouseleave="hoveredReportId = null"
            >
              <span class="item-type-dot" :class="'dot-' + getCategoryClass(report.type)"></span>
              <span class="item-name" :title="report.name">{{ report.name }}</span>
              <button
                v-show="hoveredReportId === report.id && !report.favorited"
                class="item-action favorite-btn"
                title="收藏"
                @click.stop="handleToggleFavorite(report)"
              >
                ☆
              </button>
              <button
                v-show="hoveredReportId === report.id && report.favorited"
                class="item-action unfavorite-btn"
                title="取消收藏"
                @click.stop="handleToggleFavorite(report)"
              >
                ★
              </button>
            </div>
          </div>
        </div>

        <!-- Empty state when search yields no results -->
        <div v-if="filteredReports.length === 0 && searchKeyword" class="sidebar-empty">
          <span class="empty-text">未找到匹配的报表</span>
        </div>

        <!-- Empty state when no reports at all -->
        <div v-if="allReports.length === 0 && !loading" class="sidebar-empty">
          <span class="empty-icon">📊</span>
          <span class="empty-text">暂无可访问的报表</span>
        </div>
      </div>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { reportApi } from '@/api/report'
import type { ReportDefinitionVO } from '@/api/report'

const props = defineProps<{
  reports: ReportDefinitionVO[]
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'favorite-changed', report: ReportDefinitionVO, favorited: boolean): void
}>()

const router = useRouter()
const route = useRoute()

// ─── State ───────────────────────────────────────────────

const isCollapsed = ref(false)
const searchKeyword = ref('')
const hoveredReportId = ref<string | null>(null)
const collapsedGroups = ref<Set<string>>(new Set())

// ─── Computed ────────────────────────────────────────────

const allReports = computed(() => props.reports)

const activeReportId = computed(() => {
  if (route.name === 'ReportDetail') {
    return route.params.id as string
  }
  return null
})

/** Search-filtered reports */
const filteredReports = computed(() => {
  if (!searchKeyword.value.trim()) return allReports.value
  const keyword = searchKeyword.value.trim().toLowerCase()
  return allReports.value.filter(r => r.name?.toLowerCase().includes(keyword))
})

/** Favorite reports (search-filtered) */
const favoriteReports = computed(() => {
  return filteredReports.value
    .filter(r => r.favorited)
    .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
})

/** Type category mapping */
const typeCategories: Record<string, string> = {
  issue_count: 'issue_distribution',
  by_status: 'issue_distribution',
  by_assignee: 'issue_distribution',
  by_priority: 'issue_distribution',
  by_type: 'issue_distribution',
  burndown: 'timeline',
  burndown_chart: 'timeline',
  cumulative_flow: 'timeline',
  resolution_time: 'timeline',
  average_issue_age: 'timeline',
  fixed_vs_reported: 'timeline',
  verified_vs_reopened: 'timeline',
  resolved_vs_new: 'timeline',
  state_transition: 'state_transition',
  time_report: 'time_management',
  estimation_report: 'time_management',
  custom: 'other'
}

/** Category definitions */
const categoryDefs = [
  { category: 'issue_distribution', label: 'Issue 分布', icon: '📊' },
  { category: 'timeline', label: '时间线趋势', icon: '📈' },
  { category: 'state_transition', label: '状态转换', icon: '🔄' },
  { category: 'time_management', label: '时间管理', icon: '⏱️' }
]

/** Non-favorite reports grouped by type */
const typeGroups = computed(() => {
  const nonFavorites = filteredReports.value.filter(r => !r.favorited)
  return categoryDefs
    .map(def => ({
      ...def,
      reports: nonFavorites
        .filter(r => typeCategories[r.type] === def.category)
        .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
    }))
    .filter(g => g.reports.length > 0)
})

// ─── Methods ─────────────────────────────────────────────

function getCategoryClass(type: string): string {
  return typeCategories[type] || 'other'
}

function toggleGroupCollapse(category: string) {
  const newSet = new Set(collapsedGroups.value)
  if (newSet.has(category)) {
    newSet.delete(category)
  } else {
    newSet.add(category)
  }
  collapsedGroups.value = newSet
}

function navigateToReport(report: ReportDefinitionVO) {
  router.push({ name: 'ReportDetail', params: { id: report.id } })
}

async function handleToggleFavorite(report: ReportDefinitionVO) {
  try {
    const res = await reportApi.toggleFavorite(report.id)
    const favorited = res.data
    report.favorited = favorited
    emit('favorite-changed', report, favorited)
    Message.success(favorited ? '已收藏' : '已取消收藏')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}
</script>

<style scoped>
.report-sidebar {
  position: relative;
  width: 240px;
  min-width: 240px;
  height: 100%;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--tf-border-light);
  background: var(--tf-bg-surface);
  transition: width 0.2s, min-width 0.2s;
  overflow: hidden;
}

.report-sidebar.collapsed {
  width: 28px;
  min-width: 28px;
}

/* Toggle button */
.sidebar-toggle {
  position: absolute;
  top: 8px;
  right: 4px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 10px;
  color: var(--tf-text-tertiary);
  transition: color 0.15s, background 0.15s;
  z-index: 2;
}

.sidebar-toggle:hover {
  color: var(--tf-text-primary);
  background: var(--tf-bg-hover);
}

.collapsed .sidebar-toggle {
  right: auto;
  left: 4px;
}

/* Header */
.sidebar-header {
  padding: 12px 16px 8px;
  display: flex;
  align-items: center;
}

.sidebar-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* Search */
.sidebar-search {
  padding: 0 12px 8px;
}

.search-input {
  width: 100%;
  height: 28px;
  padding: 0 8px;
  font-size: 12px;
  border: 1px solid var(--tf-border-light);
  border-radius: 4px;
  background: var(--tf-bg-body);
  color: var(--tf-text-primary);
  outline: none;
  transition: border-color 0.15s;
}

.search-input::placeholder {
  color: var(--tf-text-quaternary);
}

.search-input:focus {
  border-color: var(--tf-accent);
}

/* Scrollable content */
.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 16px;
}

/* Section */
.sidebar-section {
  margin-bottom: 4px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  user-select: none;
}

.section-header.clickable {
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}

.section-header.clickable:hover {
  background: var(--tf-bg-hover);
}

.section-icon {
  font-size: 12px;
  width: 16px;
  text-align: center;
}

.section-title {
  flex: 1;
}

.section-count {
  font-size: 10px;
  font-weight: 400;
  color: var(--tf-text-quaternary);
  background: var(--tf-bg-body);
  padding: 0 5px;
  border-radius: 8px;
  min-width: 16px;
  text-align: center;
}

.collapse-icon {
  font-size: 10px;
  color: var(--tf-text-quaternary);
  transition: transform 0.2s;
}

.collapse-icon.collapsed {
  transform: rotate(-90deg);
}

/* Items */
.section-items {
  padding: 0 6px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
}

.sidebar-item:hover {
  background: var(--tf-bg-hover);
}

.sidebar-item.active {
  background: var(--tf-bg-active);
}

.sidebar-item.active .item-name {
  color: var(--tf-accent);
  font-weight: 500;
}

.item-type-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.item-type-dot.dot-issue_distribution {
  background: var(--tf-accent);
}

.item-type-dot.dot-timeline {
  background: var(--tf-success);
}

.item-type-dot.dot-state_transition {
  background: var(--tf-purple);
}

.item-type-dot.dot-time_management {
  background: var(--tf-warning);
}

.item-type-dot.dot-other {
  background: var(--tf-text-tertiary);
}

.item-name {
  flex: 1;
  font-size: 12px;
  color: var(--tf-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.item-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
  transition: color 0.15s, background 0.15s, transform 0.15s;
}

.favorite-btn {
  color: var(--tf-text-tertiary);
}

.favorite-btn:hover {
  color: var(--tf-warning);
  transform: scale(1.15);
}

.unfavorite-btn {
  color: var(--tf-warning);
}

.unfavorite-btn:hover {
  color: var(--tf-text-tertiary);
  transform: scale(1.15);
}

/* Empty */
.sidebar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 24px 16px;
  text-align: center;
}

.sidebar-empty .empty-icon {
  font-size: 24px;
}

.sidebar-empty .empty-text {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}
</style>
