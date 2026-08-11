<template>
  <div class="app-layout">
    <!-- 全局服务状态横幅 -->
    <ServiceStatusBanner />

    <!-- 左侧导航栏 -->
    <AppSidebar @open-create="openCreatePanel" />

    <!-- 右侧（标签栏 + 内容区） -->
    <div class="main-area">
      <TabBar v-if="showTabBar" />
      <div class="main-content">
        <!--
          KeepAlive 缓存高频页面，避免来回切换时重建组件
          :include 列表与各页面 defineOptions({ name }) 保持一致
          :max="5" LRU 上限，防止内存无限增长
        -->
        <router-view v-slot="{ Component }">
          <KeepAlive :include="['IssueListView', 'SprintView', 'ProjectListView']" :max="5">
            <component :is="Component" :key="$route.path" />
          </KeepAlive>
        </router-view>
      </div>
    </div>

    <!-- 通知面板 -->
    <NotificationPanel />

    <!-- 全局创建工单弹窗（从侧边栏触发，不跳转页面） -->
    <IssueCreatePanel
      v-if="canCreateIssue"
      :visible="showCreatePanel"
      :draft-id="createPanelDraftId"
      @update:visible="onCreatePanelVisibleChange"
      @created="onCreatePanelCreated"
      @cancel-with-data="onCreatePanelCancel"
      @expand-to-fullscreen="onCreatePanelExpand"
    />

    <!-- 全局 Toast 通知 -->
    <ToastNotification />
  </div>
</template>

<script setup lang="ts">
/**
 * AppLayout — 全局布局根组件
 *
 * 职责：
 * - 组装 AppSidebar、TabBar、内容区、NotificationPanel 等布局组件
 * - 管理「全局创建工单」弹窗的开/关状态（从侧边栏触发）
 * - 监听 WebSocket 状态，断线/恢复时提示用户
 *
 * 不在这里做的事：
 * - 侧边栏折叠逻辑 → AppSidebar
 * - 导航权限判断 → AppSidebar
 * - 计时器显示 → TimerPopover（内嵌于 AppSidebar）
 * - 通知铃铛 → AppSidebar + NotificationPanel
 */
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/composables/useWebSocket'
import { useToast } from '@/composables/useToast'
import AppSidebar from './AppSidebar.vue'
import TabBar from './TabBar.vue'
import NotificationPanel from './NotificationPanel.vue'
import ServiceStatusBanner from './ServiceStatusBanner.vue'
import IssueCreatePanel from '@/components/IssueCreatePanel.vue'
import { ToastNotification } from '@/components/base'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { status: wsStatus } = useWebSocket()
const toast = useToast()

// ===== WebSocket 断线/恢复提示 =====
let wsDisconnectToastId: number | null = null
watch(wsStatus, (status, prevStatus) => {
  if (status === 'error') {
    if (wsDisconnectToastId === null) {
      wsDisconnectToastId = toast.warning('实时更新连接已断开，数据可能不是最新', { duration: 0 })
    }
  } else if (status === 'connected' && prevStatus !== 'connected') {
    if (wsDisconnectToastId !== null) {
      toast.dismiss(wsDisconnectToastId)
      wsDisconnectToastId = null
      toast.success('实时更新已恢复', { duration: 3000 })
    }
  }
})

// ===== TabBar 仅在 Issue 相关路由显示 =====
const showTabBar = computed(() => {
  const name = route.name
  return name === 'Issues' || name === 'IssueDetail'
})

// ===== 权限（仅用于控制创建面板是否挂载） =====
const canCreateIssue = computed(() => {
  if (!authStore.permissionsLoaded) return false
  return authStore.hasGlobalPermission('system:manage_users')
    || authStore.hasGlobalPermission('system:manage_roles')
    || authStore.hasGlobalPermission('system:manage_orgs')
    || authStore.canCreateIssue
})

// ===== 全局创建工单弹窗 =====
const showCreatePanel = ref(false)
const createPanelDraftId = ref<string | null>(null)

function openCreatePanel() {
  createPanelDraftId.value = null
  showCreatePanel.value = true
}

function onCreatePanelVisibleChange(val: boolean) {
  showCreatePanel.value = val
  if (!val) createPanelDraftId.value = null
}

function onCreatePanelCreated() {
  showCreatePanel.value = false
  createPanelDraftId.value = null
}

function onCreatePanelCancel(_formData: any) {
  showCreatePanel.value = false
  createPanelDraftId.value = null
}

function onCreatePanelExpand(formData: any) {
  showCreatePanel.value = false
  createPanelDraftId.value = null
  if (formData && (formData.title?.trim() || formData.description?.trim())) {
    const key = 'trackflow:issue-drafts'
    try {
      const drafts = JSON.parse(localStorage.getItem(key) || '[]')
      const draftId = `draft_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
      drafts.unshift({ id: draftId, ...formData, updatedAt: Date.now() })
      if (drafts.length > 10) drafts.length = 10
      localStorage.setItem(key, JSON.stringify(drafts))
      router.push({ name: 'IssueCreate', query: { draftId } })
      return
    } catch { /* ignore */ }
  }
  router.push({ name: 'IssueCreate' })
}
</script>

<style scoped>
.app-layout {
  height: 100vh;
  display: flex;
  overflow: hidden;
}

.main-area {
  flex: 1;
  overflow: hidden;
  background: var(--tf-bg-body);
  transition: background-color 0.2s;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  overflow: hidden;
}
</style>
