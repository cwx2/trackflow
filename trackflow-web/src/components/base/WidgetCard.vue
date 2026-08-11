<template>
  <div class="widget-card" v-if="widget">
    <!-- Widget 头部 -->
    <div class="widget-header">
      <div class="widget-header-left">
        <span class="widget-type-icon">{{ widgetIcon }}</span>
        <span
          class="widget-title"
          :class="{ 'widget-title-clickable': isIssueListWidget }"
          :title="isIssueListWidget ? '点击查看完整工单列表' : undefined"
          @click="handleTitleClick"
        >{{ widget.title || widgetTypeLabel }}</span>
      </div>
      <div class="widget-header-right">
        <button
          v-if="dataLoaded && !loading"
          class="widget-action-btn"
          title="刷新数据"
          @click.stop="refreshData"
        >
          <icon-refresh :spin="refreshing" :size="14" />
        </button>
        <a-dropdown v-if="isOwner" trigger="click" :popup-max-height="false">
          <button class="widget-menu-btn" @click.stop>
            <icon-more />
          </button>
          <template #content>
            <a-doption @click="$emit('edit', widget)">
              <template #icon><icon-edit /></template>
              编辑配置
            </a-doption>
            <a-doption @click="handleCopyLink">
              <template #icon><icon-link /></template>
              复制链接
            </a-doption>
            <a-doption @click="$emit('move', widget)">
              <template #icon><icon-swap /></template>
              移动到其他仪表盘
            </a-doption>
            <a-doption class="danger-option" @click="$emit('delete', widget)">
              <template #icon><icon-delete /></template>
              删除微件
            </a-doption>
          </template>
        </a-dropdown>
      </div>
    </div>

    <!-- Widget 内容区 -->
    <div class="widget-body">
      <!-- 加载中遮罩层（overlay，不卸载子组件） -->
      <div v-if="loading" class="widget-loading-overlay">
        <a-spin dot />
      </div>

      <!-- 权限不足状态（优雅降级，仿 YouTrack 空 Widget 风格） -->
      <div v-if="permissionDenied && !loading" class="widget-permission-denied">
        <icon-lock :size="24" class="permission-icon" />
        <span class="permission-text">暂无权限查看此数据</span>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error && !loading" class="widget-error">
        <icon-exclamation-circle-fill :size="24" class="error-icon" />
        <span class="error-text">{{ error }}</span>
        <a-button size="mini" type="text" @click="refreshData">
          <template #icon><icon-refresh /></template>
          重试
        </a-button>
      </div>

      <!-- 动态 Widget 内容（始终保持挂载，用 v-show 控制可见性） -->
      <component
        v-if="widgetComponent"
        v-show="!loading && !error && !permissionDenied"
        :is="widgetComponent"
        ref="widgetRef"
        v-bind="widgetProps"
        @loaded="onWidgetLoaded"
        @error="onWidgetError"
        @permission-denied="onWidgetPermissionDenied"
      />

      <!-- 未知类型（注册表中无对应 Widget） -->
      <template v-else>
        <div class="widget-configure-hint">
          <icon-question-circle :size="32" class="hint-icon" />
          <span class="hint-text">未知微件类型: {{ widget.widgetType }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { copyToClipboard } from '@/utils/clipboard'
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import {
  IconMore, IconEdit, IconDelete, IconRefresh, IconLink, IconSwap,
  IconExclamationCircleFill, IconQuestionCircle, IconLock
} from '@arco-design/web-vue/es/icon'
import type { DashboardWidgetVO } from '@/api/customDashboard'
import { getWidget } from '@/widgets'

const props = defineProps<{
  widget: DashboardWidgetVO | undefined
  isOwner: boolean
}>()

defineEmits<{
  edit: [widget: DashboardWidgetVO]
  delete: [widget: DashboardWidgetVO]
  move: [widget: DashboardWidgetVO]
}>()

// ─── 状态 ─────────────────────────────────────────────

const loading = ref(false)
const refreshing = ref(false)
const error = ref<string | null>(null)
const permissionDenied = ref(false)
const dataLoaded = ref(false)
const widgetRef = ref<{ loadData?: (force?: boolean) => Promise<void>; navigateToFilteredList?: () => void } | null>(null)

// ─── 加载超时兜底 ──────────────────────────────────────

const LOADING_TIMEOUT_MS = 5000
let loadingTimeoutId: ReturnType<typeof setTimeout> | null = null

function clearLoadingTimeout() {
  if (loadingTimeoutId) {
    clearTimeout(loadingTimeoutId)
    loadingTimeoutId = null
  }
}

function startLoadingTimeout() {
  clearLoadingTimeout()
  loadingTimeoutId = setTimeout(() => {
    if (loading.value) {
      loading.value = false
      error.value = '数据加载超时，请检查网络或点击重试'
    }
  }, LOADING_TIMEOUT_MS)
}

// ─── Widget 类型映射（从注册表动态读取） ──────────────────────────────────────

const widgetIcon = computed(() => {
  if (!props.widget) return '❓'
  const def = getWidget(props.widget.widgetType)
  return def?.icon || '❓'
})

const widgetTypeLabel = computed(() => {
  if (!props.widget) return ''
  const def = getWidget(props.widget.widgetType)
  return def?.label || props.widget.widgetType
})

const isIssueListWidget = computed(() => {
  return props.widget?.widgetType === 'issue_list'
})

function handleTitleClick() {
  if (!isIssueListWidget.value) return
  // Delegate to IssueListWidget's navigateToFilteredList
  if (widgetRef.value && 'navigateToFilteredList' in widgetRef.value) {
    ;(widgetRef.value as any).navigateToFilteredList()
  }
}

// ─── 动态组件路由（从注册表读取） ──────────────────────────────────────

const widgetComponent = computed(() => {
  if (!props.widget) return null
  const def = getWidget(props.widget.widgetType)
  return def?.component || null
})

const parsedConfig = computed(() => {
  if (!props.widget?.config) return {} as Record<string, any>
  try {
    return JSON.parse(props.widget.config) as Record<string, any>
  } catch {
    return {} as Record<string, any>
  }
})

const widgetProps = computed(() => {
  if (!props.widget) return {}
  const widgetType = props.widget.widgetType
  const config = parsedConfig.value

  // ReportChartWidget needs extra props
  if (widgetType === 'report' || widgetType === 'report_distribution') {
    return {
      config,
      reportId: props.widget.reportId,
      widgetType
    }
  }

  return { config }
})

// ─── 事件处理 ─────────────────────────────────────────

function onWidgetLoaded() {
  clearLoadingTimeout()
  loading.value = false
  error.value = null
  permissionDenied.value = false
  dataLoaded.value = true
}

function onWidgetError(message: string) {
  clearLoadingTimeout()
  loading.value = false
  error.value = message
}

function onWidgetPermissionDenied() {
  clearLoadingTimeout()
  loading.value = false
  error.value = null
  permissionDenied.value = true
  dataLoaded.value = true
}

// ─── 数据加载与刷新 ──────────────────────────────────────

async function loadData() {
  if (!props.widget) return
  const { widgetType } = props.widget

  // note 类型不需要异步加载
  if (widgetType === 'note') {
    dataLoaded.value = true
    return
  }

  loading.value = true
  error.value = null
  // 启动超时兜底：子组件 10 秒内未触发 @loaded/@error 则强制终止
  startLoadingTimeout()
  // 子组件在 onMounted 中自动加载数据，通过 @loaded/@error 回调通知
}

async function refreshData() {
  refreshing.value = true
  error.value = null
  permissionDenied.value = false
  dataLoaded.value = false
  loading.value = true
  startLoadingTimeout()

  if (widgetRef.value?.loadData) {
    try {
      await widgetRef.value.loadData(true)
    } catch {
      // 子组件内部已通过 emit('error') 处理，这里只做兜底
    }
  }
  refreshing.value = false
}

async function handleCopyLink() {
  if (!props.widget) return
  const baseUrl = window.location.origin
  const link = `${baseUrl}/dashboard?id=${props.widget.dashboardId}&widget=${props.widget.id}`
  await copyToClipboard(link, { successMessage: '链接已复制到剪贴板' })
}

// ─── 自动刷新 ──────────────────────────────────────────

let autoRefreshTimer: ReturnType<typeof setInterval> | null = null

function setupAutoRefreshTimer() {
  clearAutoRefreshTimer()
  const config = parsedConfig.value
  const interval = config.refreshInterval
  if (!interval || interval <= 0) return
  autoRefreshTimer = setInterval(() => {
    refreshData()
  }, interval * 1000)
}

function clearAutoRefreshTimer() {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

// ─── Lifecycle ────────────────────────────────────────

onMounted(() => {
  loadData()
  // Timer will be set after widget reports loaded
  setupAutoRefreshTimer()
})

watch(() => props.widget?.config, () => {
  refreshData().then(() => {
    setupAutoRefreshTimer()
  })
}, { deep: true })

watch(() => props.widget?.reportId, () => {
  refreshData()
})

onBeforeUnmount(() => {
  clearAutoRefreshTimer()
  clearLoadingTimeout()
})
</script>

<style scoped>
.widget-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  overflow: hidden;
}

.widget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--tf-border-light);
  flex-shrink: 0;
}

.widget-header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.widget-type-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.widget-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.widget-title-clickable {
  cursor: pointer;
  transition: color 0.15s;
}

.widget-title-clickable:hover {
  color: var(--tf-accent);
}

.widget-header-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

.widget-action-btn,
.widget-menu-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  color: var(--tf-text-tertiary);
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.widget-action-btn:hover,
.widget-menu-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.widget-body {
  flex: 1;
  padding: 12px;
  overflow: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.widget-loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--tf-bg-elevated);
  z-index: 1;
}

.widget-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  text-align: center;
}

.error-icon {
  color: var(--tf-danger, #f85149);
  opacity: 0.7;
}

.widget-permission-denied {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  padding: 12px;
}

.permission-icon {
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  opacity: 0.5;
}

.permission-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: center;
  line-height: 1.4;
}
.error-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  line-height: 1.4;
  max-width: 160px;
}

.widget-configure-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  padding: 12px;
}

.hint-icon {
  color: var(--tf-text-tertiary);
  opacity: 0.4;
}

.hint-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: center;
  line-height: 1.4;
}

.danger-option {
  color: var(--tf-danger) !important;
}
</style>
