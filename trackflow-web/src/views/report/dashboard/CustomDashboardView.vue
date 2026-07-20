<template>
  <div class="custom-dashboard-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-right">
        <a-button type="primary" size="small" @click="showCreateModal = true">
          <template #icon><icon-plus /></template>
          创建仪表盘
        </a-button>
      </div>
    </div>

    <!-- 仪表盘切换器 -->
    <div class="dashboard-switcher" v-if="dashboards.length > 0">
      <div
        v-for="d in dashboards"
        :key="d.id"
        class="dashboard-tab"
        :class="{ active: activeDashboardId === d.id }"
        @click="selectDashboard(d.id)"
      >
        <span class="tab-name">{{ d.name }}</span>
        <span v-if="d.shared" class="tab-shared" title="已共享">
          <icon-share-alt :size="12" />
        </span>
        <span class="tab-count">{{ d.widgetCount }}</span>
      </div>
    </div>

    <!-- 主体内容 -->
    <div class="dashboard-body">
      <!-- 加载状态 -->
      <div v-if="loadingDetail" class="loading-state">
        <a-spin dot />
        <span class="loading-text">加载中...</span>
      </div>

      <!-- 空状态：无仪表盘 -->
      <div v-else-if="dashboards.length === 0 && !loadingList" class="empty-state">
        <div class="empty-icon">📋</div>
        <h3 class="empty-title">还没有自定义仪表盘</h3>
        <p class="empty-desc">创建您的第一个仪表盘，添加微件来跟踪项目进度和团队工作。</p>
        <a-button type="primary" @click="showCreateModal = true">
          <template #icon><icon-plus /></template>
          创建仪表盘
        </a-button>
      </div>

      <!-- 仪表盘内容 -->
      <template v-else-if="currentDashboard">
        <!-- 仪表盘工具栏 -->
        <div class="dashboard-toolbar">
          <div class="toolbar-left">
            <h2 class="dashboard-name">{{ currentDashboard.name }}</h2>
            <span v-if="currentDashboard.description" class="dashboard-desc">
              {{ currentDashboard.description }}
            </span>
            <span v-if="currentDashboard.shareCount && currentDashboard.shareCount > 0" class="share-badge" @click="isOwner && (showShareModal = true)">
              <icon-share-alt :size="12" />
              已共享给 {{ currentDashboard.shareCount }} 个对象
            </span>
          </div>
          <div class="toolbar-right" v-if="isOwner">
            <a-button size="small" @click="showAddWidgetModal = true">
              <template #icon><icon-plus /></template>
              添加微件
            </a-button>
            <a-dropdown trigger="click">
              <a-button size="small" type="text">
                <template #icon><icon-more /></template>
              </a-button>
              <template #content>
                <a-doption @click="showEditModal = true">
                  <template #icon><icon-edit /></template>
                  编辑仪表盘
                </a-doption>
                <a-doption @click="showShareModal = true">
                  <template #icon><icon-share-alt /></template>
                  共享设置
                </a-doption>
                <a-doption @click="toggleShared">
                  <template #icon><icon-share-alt /></template>
                  {{ currentDashboard.shared ? '取消全局共享' : '全局共享' }}
                </a-doption>
                <a-doption class="danger-option" @click="confirmDelete">
                  <template #icon><icon-delete /></template>
                  删除仪表盘
                </a-doption>
              </template>
            </a-dropdown>
          </div>
        </div>

        <!-- Widget 网格布局 -->
        <div v-if="currentDashboard.widgets.length > 0" class="widget-grid-container">
          <GridLayout
            v-model:layout="widgetLayout"
            :col-num="12"
            :row-height="80"
            :margin="[16, 16]"
            :is-draggable="isOwner"
            :is-resizable="isOwner"
            @layout-updated="onLayoutUpdated"
          >
            <GridItem
              v-for="item in widgetLayout"
              :key="item.i"
              :x="item.x"
              :y="item.y"
              :w="item.w"
              :h="item.h"
              :i="item.i"
              class="widget-grid-item"
            >
              <WidgetCard
                :widget="getWidgetById(item.i)"
                :is-owner="isOwner"
                @edit="editWidget"
                @delete="deleteWidget"
              />
            </GridItem>
          </GridLayout>
        </div>

        <!-- 空微件状态 -->
        <div v-else class="empty-widgets">
          <div class="empty-icon">📊</div>
          <template v-if="isOwner">
            <h3 class="empty-title">仪表盘还没有微件</h3>
            <p class="empty-desc">点击"添加微件"为仪表盘添加数据展示组件。</p>
            <a-button type="primary" size="small" @click="showAddWidgetModal = true">
              <template #icon><icon-plus /></template>
              添加微件
            </a-button>
          </template>
          <template v-else>
            <h3 class="empty-title">仪表盘暂无内容</h3>
            <p class="empty-desc">仪表盘创建者尚未添加微件。</p>
          </template>
        </div>
      </template>
    </div>

    <!-- 创建仪表盘弹窗 -->
    <a-modal
      v-model:visible="showCreateModal"
      title="创建仪表盘"
      :width="480"
      @ok="handleCreate"
      :ok-loading="creating"
      ok-text="创建"
      cancel-text="取消"
    >
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model="createForm.name" placeholder="输入仪表盘名称" :max-length="200" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model="createForm.description" placeholder="可选描述" :max-length="2000" :auto-size="{ minRows: 2, maxRows: 5 }" />
        </a-form-item>
        <a-form-item label="共享">
          <a-switch v-model="createForm.shared" />
          <span class="form-hint">共享后所有用户可查看此仪表盘</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑仪表盘弹窗 -->
    <a-modal
      v-model:visible="showEditModal"
      title="编辑仪表盘"
      :width="480"
      @ok="handleUpdate"
      :ok-loading="updating"
      ok-text="保存修改"
      cancel-text="取消"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model="editForm.name" placeholder="输入仪表盘名称" :max-length="200" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model="editForm.description" placeholder="可选描述" :max-length="2000" :auto-size="{ minRows: 2, maxRows: 5 }" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 添加微件弹窗 -->
    <a-modal
      v-model:visible="showAddWidgetModal"
      title="添加微件"
      :width="640"
      :footer="false"
    >
      <div class="widget-type-grid">
        <div
          v-for="wt in widgetTypes"
          :key="wt.type"
          class="widget-type-card"
          @click="addWidget(wt.type, wt.defaultTitle)"
        >
          <div class="wt-icon">{{ wt.icon }}</div>
          <div class="wt-info">
            <div class="wt-name">{{ wt.label }}</div>
            <div class="wt-desc">{{ wt.description }}</div>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- 微件配置弹窗 -->
    <a-modal
      v-model:visible="showWidgetConfigModal"
      title="编辑微件配置"
      :width="520"
      @ok="handleWidgetConfigSave"
      :ok-loading="savingWidgetConfig"
      ok-text="保存"
      cancel-text="取消"
    >
      <a-form :model="widgetConfigForm" layout="vertical">
        <a-form-item label="标题">
          <a-input v-model="widgetConfigForm.title" placeholder="微件标题" :max-length="100" />
        </a-form-item>

        <!-- number_card 配置 -->
        <template v-if="editingWidgetType === 'number_card'">
          <a-form-item label="数据来源">
            <a-select v-model="widgetConfigForm.queryType" placeholder="选择统计指标" allow-clear>
              <a-option value="total">工单总数</a-option>
              <a-option value="open">待处理工单数</a-option>
              <a-option value="closed">已完成工单数</a-option>
              <a-option value="unassigned">未分配工单数</a-option>
              <a-option value="overdue">已逾期工单数</a-option>
              <a-option value="completion_rate">完成率 (%)</a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="自定义数值（留空则自动从后端获取）">
            <a-input-number v-model="widgetConfigForm.staticValue" placeholder="留空=动态数据" :min="0" style="width: 100%" />
          </a-form-item>
          <a-form-item label="副标签">
            <a-input v-model="widgetConfigForm.label" placeholder="如「Open Bugs」" :max-length="50" />
          </a-form-item>
        </template>

        <!-- report_distribution / report 配置 -->
        <template v-if="editingWidgetType === 'report_distribution' || editingWidgetType === 'report'">
          <a-form-item label="关联报表">
            <a-select v-model="widgetConfigForm.reportId" placeholder="选择一个已保存的报表" allow-clear>
              <a-option v-for="r in availableReports" :key="r.id" :value="r.id">
                {{ r.name }}
              </a-option>
            </a-select>
            <span class="form-hint">关联后将自动展示该报表的图表数据</span>
          </a-form-item>
        </template>

        <!-- note 配置 -->
        <template v-if="editingWidgetType === 'note'">
          <a-form-item label="内容">
            <a-textarea
              v-model="widgetConfigForm.noteContent"
              placeholder="支持简单 HTML 标签"
              :auto-size="{ minRows: 3, maxRows: 8 }"
            />
          </a-form-item>
        </template>
      </a-form>
    </a-modal>

    <!-- 共享设置弹窗 -->
    <ShareDashboardModal
      v-model:visible="showShareModal"
      :dashboard-id="currentDashboard?.id || ''"
      @saved="onShareSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import {
  IconPlus, IconMore, IconEdit, IconDelete, IconShareAlt
} from '@arco-design/web-vue/es/icon'
import { customDashboardApi } from '@/api'
import { reportApi } from '@/api/report'
import type { DashboardListVO, DashboardDetailVO, DashboardWidgetVO } from '@/api/customDashboard'
import type { ReportDefinitionVO } from '@/api/report'
import WidgetCard from './WidgetCard.vue'
import ShareDashboardModal from './ShareDashboardModal.vue'

// ─── 微件类型定义 ─────────────────────────────────────────

const widgetTypes = [
  { type: 'note', label: '快捷笔记', icon: '📝', description: '自由编辑 Markdown 内容', defaultTitle: '笔记' },
  { type: 'number_card', label: '数字卡片', icon: '🔢', description: '单数字大卡片（如"本月完成: 88"）', defaultTitle: '统计' },
  { type: 'report_distribution', label: '分布图表', icon: '📊', description: '按字段分组的条形图/饼图', defaultTitle: '分布报表' },
  { type: 'issue_list', label: 'Issue 列表', icon: '📋', description: '按条件展示工单列表', defaultTitle: 'Issue 列表' },
  { type: 'activity_feed', label: '活动流', icon: '🔔', description: '最近的 Issue 活动（评论/状态变更）', defaultTitle: '最近活动' },
  { type: 'report', label: '报表图表', icon: '📈', description: '关联已保存的报表定义', defaultTitle: '报表' },
  { type: 'sprint_progress', label: 'Sprint 进度', icon: '🏃', description: 'Sprint 完成进度条', defaultTitle: 'Sprint 进度' },
  { type: 'calendar', label: '到期日历', icon: '📅', description: 'Issue 到期日期日历视图', defaultTitle: '到期日历' }
]

// ─── 状态 ─────────────────────────────────────────────

const loadingList = ref(false)
const loadingDetail = ref(false)
const creating = ref(false)
const updating = ref(false)

const dashboards = ref<DashboardListVO[]>([])
const activeDashboardId = ref<string | null>(null)
const currentDashboard = ref<DashboardDetailVO | null>(null)

const showCreateModal = ref(false)
const showEditModal = ref(false)
const showAddWidgetModal = ref(false)
const showWidgetConfigModal = ref(false)
const showShareModal = ref(false)

const createForm = ref({ name: '', description: '', shared: false })
const editForm = ref({ name: '', description: '' })

// Widget config editing state
const editingWidget = ref<DashboardWidgetVO | null>(null)
const editingWidgetType = ref<string>('')
const savingWidgetConfig = ref(false)
const availableReports = ref<ReportDefinitionVO[]>([])
const widgetConfigForm = ref<{
  title: string
  queryType?: string
  staticValue?: number
  label?: string
  reportId?: string
  noteContent?: string
}>({
  title: '',
  queryType: undefined,
  staticValue: undefined,
  label: '',
  reportId: undefined,
  noteContent: ''
})

// ─── 计算属性 ─────────────────────────────────────────

const currentUserId = computed(() => {
  const user = localStorage.getItem('tf_user')
  if (!user) return ''
  try {
    const parsed = JSON.parse(user)
    // Use database userId (matches backend VO ownerId), not Keycloak sub UUID
    return parsed.userId || parsed.id || ''
  } catch { return '' }
})

const isOwner = computed(() => {
  if (!currentDashboard.value) return false
  return currentDashboard.value.ownerId === currentUserId.value
})

// ─── Grid Layout ─────────────────────────────────────────
// NOTE: Variable named "widgetLayout" (not "gridLayout") to avoid name collision
// with the <grid-layout> component tag. Vue SFC compiler resolves <grid-layout>
// to camelCase "gridLayout" which would conflict with a ref of that name.

const widgetLayout = ref<Array<{ i: string; x: number; y: number; w: number; h: number }>>([])

function buildGridLayout(widgets: DashboardWidgetVO[]) {
  widgetLayout.value = widgets.map(w => ({
    i: w.id,
    x: w.positionX,
    y: w.positionY,
    w: w.width,
    h: w.height
  }))
}

function getWidgetById(id: string): DashboardWidgetVO | undefined {
  return currentDashboard.value?.widgets.find(w => w.id === id)
}

let layoutSaveTimer: ReturnType<typeof setTimeout> | null = null

function onLayoutUpdated(layout: Array<{ i: string; x: number; y: number; w: number; h: number }>) {
  // 防抖保存
  if (layoutSaveTimer) clearTimeout(layoutSaveTimer)
  layoutSaveTimer = setTimeout(() => {
    saveLayout(layout)
  }, 1000)
}

async function saveLayout(layout: Array<{ i: string; x: number; y: number; w: number; h: number }>) {
  if (!currentDashboard.value || !isOwner.value) return
  try {
    const items = layout.map(item => ({
      widgetId: item.i,
      positionX: item.x,
      positionY: item.y,
      width: item.w,
      height: item.h
    }))
    const version = currentDashboard.value.layoutVersion ?? 0
    await customDashboardApi.updateLayout(currentDashboard.value.id, items, version)
    // 乐观更新本地版本号
    currentDashboard.value.layoutVersion = version + 1
  } catch (e: any) {
    if (e.response?.status === 409) {
      Message.warning('布局已被其他操作修改，正在刷新...')
      // 重新加载仪表盘详情以获取最新版本
      await selectDashboard(currentDashboard.value!.id)
    }
    // 其他错误静默处理
  }
}

// ─── 数据加载 ─────────────────────────────────────────

onMounted(async () => {
  await loadDashboards()
})

async function loadDashboards() {
  loadingList.value = true
  try {
    const res = await customDashboardApi.list()
    dashboards.value = res.data || []
    // 自动选中第一个（或上次选中的）
    if (dashboards.value.length > 0) {
      const targetId = activeDashboardId.value && dashboards.value.find(d => d.id === activeDashboardId.value)
        ? activeDashboardId.value
        : dashboards.value[0].id
      await selectDashboard(targetId)
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载仪表盘列表失败')
  } finally {
    loadingList.value = false
  }
}

async function selectDashboard(id: string) {
  activeDashboardId.value = id
  loadingDetail.value = true
  try {
    const res = await customDashboardApi.getDetail(id)
    currentDashboard.value = res.data || null
    if (currentDashboard.value) {
      buildGridLayout(currentDashboard.value.widgets)
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载仪表盘详情失败')
    currentDashboard.value = null
  } finally {
    loadingDetail.value = false
  }
}

// ─── CRUD 操作 ─────────────────────────────────────────

async function handleCreate() {
  if (!createForm.value.name.trim()) {
    Message.warning('请输入仪表盘名称')
    return
  }
  creating.value = true
  try {
    const res = await customDashboardApi.create({
      name: createForm.value.name.trim(),
      description: createForm.value.description.trim() || undefined,
      shared: createForm.value.shared
    })
    Message.success('仪表盘创建成功')
    showCreateModal.value = false
    createForm.value = { name: '', description: '', shared: false }
    await loadDashboards()
    if (res.data) {
      await selectDashboard(res.data.id)
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function handleUpdate() {
  if (!currentDashboard.value) return
  if (!editForm.value.name.trim()) {
    Message.warning('请输入仪表盘名称')
    return
  }
  updating.value = true
  try {
    await customDashboardApi.update(currentDashboard.value.id, {
      name: editForm.value.name.trim(),
      description: editForm.value.description.trim() || undefined
    })
    Message.success('已更新')
    showEditModal.value = false
    await loadDashboards()
    await selectDashboard(currentDashboard.value.id)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
  } finally {
    updating.value = false
  }
}

async function toggleShared() {
  if (!currentDashboard.value) return
  try {
    await customDashboardApi.update(currentDashboard.value.id, {
      shared: !currentDashboard.value.shared
    })
    Message.success(currentDashboard.value.shared ? '已取消全局共享' : '已设为全局共享')
    await loadDashboards()
    await selectDashboard(currentDashboard.value.id)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function onShareSaved() {
  // 共享设置保存后刷新仪表盘详情以更新 shareCount
  if (currentDashboard.value) {
    await selectDashboard(currentDashboard.value.id)
    await loadDashboards()
  }
}

function confirmDelete() {
  if (!currentDashboard.value) return
  Modal.warning({
    title: '删除仪表盘',
    content: `确定要删除「${currentDashboard.value.name}」吗？此操作不可撤销，所有微件将一并删除。`,
    okText: '删除',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      try {
        await customDashboardApi.delete(currentDashboard.value!.id)
        Message.success('已删除')
        activeDashboardId.value = null
        currentDashboard.value = null
        await loadDashboards()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

// ─── Widget 操作 ─────────────────────────────────────────

async function addWidget(widgetType: string, defaultTitle: string) {
  if (!currentDashboard.value) return
  try {
    // Default config based on widget type
    let defaultConfig = '{}'
    if (widgetType === 'number_card') {
      defaultConfig = JSON.stringify({ queryType: 'open', label: '待处理' })
    }

    await customDashboardApi.addWidget(currentDashboard.value.id, {
      widgetType,
      title: defaultTitle,
      config: defaultConfig,
      width: widgetType === 'number_card' ? 3 : 4,
      height: widgetType === 'number_card' ? 2 : 3
    })
    Message.success('微件已添加')
    showAddWidgetModal.value = false
    await selectDashboard(currentDashboard.value.id)
    // 更新列表中的 widget count
    await loadDashboards()
  } catch (e: any) {
    showAddWidgetModal.value = false
    const status = e.response?.status
    if (status === 403) {
      Message.error('只有仪表盘创建者可以添加微件')
    } else {
      Message.error(e.response?.data?.message || '添加失败')
    }
  }
}

function editWidget(widget: DashboardWidgetVO) {
  editingWidget.value = widget
  editingWidgetType.value = widget.widgetType

  // Parse current config
  let config: Record<string, any> = {}
  try {
    config = widget.config ? JSON.parse(widget.config) : {}
  } catch { /* empty */ }

  widgetConfigForm.value = {
    title: widget.title || '',
    queryType: config.queryType || undefined,
    staticValue: config.value ?? undefined,
    label: config.label || '',
    reportId: widget.reportId || undefined,
    noteContent: config.content || ''
  }

  // Load reports if needed for report widgets
  if (widget.widgetType === 'report_distribution' || widget.widgetType === 'report') {
    loadAvailableReports()
  }

  showWidgetConfigModal.value = true
}

async function loadAvailableReports() {
  try {
    const res = await reportApi.list()
    availableReports.value = res.data || []
  } catch {
    availableReports.value = []
  }
}

async function handleWidgetConfigSave() {
  if (!editingWidget.value || !currentDashboard.value) return
  savingWidgetConfig.value = true
  try {
    const widget = editingWidget.value
    const form = widgetConfigForm.value

    // Build config JSON
    let config: Record<string, any> = {}
    if (widget.widgetType === 'number_card') {
      if (form.queryType) config.queryType = form.queryType
      if (form.staticValue != null) config.value = form.staticValue
      if (form.label) config.label = form.label
    } else if (widget.widgetType === 'note') {
      if (form.noteContent) config.content = form.noteContent
    }
    // report_distribution / report: reportId is saved separately

    const updateData: Record<string, any> = {
      title: form.title || undefined,
      config: JSON.stringify(config)
    }

    // Handle reportId for report widgets
    if (widget.widgetType === 'report_distribution' || widget.widgetType === 'report') {
      if (form.reportId) {
        updateData.reportId = form.reportId
      } else {
        updateData.clearReportId = true
      }
    }

    await customDashboardApi.updateWidget(currentDashboard.value.id, widget.id, updateData)
    Message.success('微件配置已保存')
    showWidgetConfigModal.value = false
    // Reload dashboard detail to get updated widgets
    await selectDashboard(currentDashboard.value.id)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    savingWidgetConfig.value = false
  }
}

async function deleteWidget(widget: DashboardWidgetVO) {
  if (!currentDashboard.value) return
  Modal.warning({
    title: '删除微件',
    content: `确定要删除「${widget.title || widget.widgetType}」吗？`,
    okText: '删除',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      try {
        await customDashboardApi.deleteWidget(currentDashboard.value!.id, widget.id)
        Message.success('微件已删除')
        await selectDashboard(currentDashboard.value!.id)
        await loadDashboards()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

// 打开编辑弹窗时填充表单
watch(showEditModal, (val) => {
  if (val && currentDashboard.value) {
    editForm.value = {
      name: currentDashboard.value.name,
      description: currentDashboard.value.description || ''
    }
  }
})
</script>

<style scoped>
.custom-dashboard-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 16px;
}

/* 仪表盘切换器 */
.dashboard-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--tf-border-light);
  padding-bottom: 0;
  overflow-x: auto;
}

.dashboard-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: color 0.15s, border-color 0.15s;
  white-space: nowrap;
  margin-bottom: -1px;
}

.dashboard-tab:hover {
  color: var(--tf-text-primary);
}

.dashboard-tab.active {
  color: var(--tf-accent);
  border-bottom-color: var(--tf-accent);
}

.tab-shared {
  color: var(--tf-text-tertiary);
}

.tab-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-elevated);
  padding: 1px 6px;
  border-radius: 10px;
}

/* 仪表盘工具栏 */
.dashboard-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.dashboard-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.dashboard-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--tf-accent);
  background: color-mix(in srgb, var(--tf-accent) 10%, transparent);
  padding: 2px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.15s;
  white-space: nowrap;
}

.share-badge:hover {
  background: color-mix(in srgb, var(--tf-accent) 18%, transparent);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* Grid 容器 */
.widget-grid-container {
  min-height: 300px;
}

.widget-grid-item {
  border-radius: 8px;
  overflow: hidden;
}

/* 空状态 */
.empty-state,
.empty-widgets {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 56px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0 0 20px;
  max-width: 360px;
  line-height: 1.5;
}

/* 加载状态 */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px;
}

.loading-text {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

/* 添加微件弹窗 */
.widget-type-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.widget-type-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}

.widget-type-card:hover {
  border-color: var(--tf-accent);
  background: var(--tf-bg-hover);
}

.wt-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.wt-info {
  min-width: 0;
}

.wt-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 2px;
}

.wt-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  line-height: 1.4;
}

.form-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-left: 8px;
}

.danger-option {
  color: var(--tf-danger) !important;
}
</style>
