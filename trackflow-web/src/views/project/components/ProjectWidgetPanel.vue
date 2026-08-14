<template>
  <div class="section widget-overview-section">
    <div class="section-header-row">
      <h2 class="section-title">监控视图</h2>
      <a-button
        v-if="canEdit && !isArchived"
        size="small"
        type="outline"
        @click="showAddWidgetModal = true"
      >
        <template #icon><icon-plus /></template>
        添加 Widget
      </a-button>
    </div>

    <!-- Widget 加载中 -->
    <div v-if="loading" class="widget-loading-state">
      <a-spin :size="20" />
      <span class="widget-loading-text">加载 Widget...</span>
    </div>

    <!-- Widget Grid -->
    <template v-else-if="overviewDashboard && overviewDashboard.widgets && overviewDashboard.widgets.length > 0">
      <GridLayout
        v-model:layout="widgetGridLayout"
        :col-num="12"
        :row-height="80"
        :margin="[12, 12]"
        :is-draggable="canEdit && !isArchived"
        :is-resizable="canEdit && !isArchived"
        @layout-updated="onWidgetLayoutUpdated"
      >
        <GridItem
          v-for="item in widgetGridLayout"
          :key="item.i"
          :x="item.x"
          :y="item.y"
          :w="item.w"
          :h="item.h"
          :i="item.i"
          class="overview-widget-grid-item"
        >
          <WidgetCard
            :widget="getOverviewWidgetById(item.i)"
            :is-owner="canEdit && !isArchived"
            :dashboard-project-id="overviewDashboard?.projectId"
            @edit="editOverviewWidget"
            @delete="deleteOverviewWidget"
            @move="() => {}"
          />
        </GridItem>
      </GridLayout>
    </template>

    <!-- 空状态 -->
    <template v-else>
      <EmptyState
        v-if="canEdit && !isArchived"
        icon="bar-chart"
        title="尚未配置监控视图"
        description="点击「添加 Widget」开始配置项目监控视图，跟踪工单进度、Sprint 状态等数据"
      >
        <template #action>
          <a-button type="primary" size="small" @click="showAddWidgetModal = true">
            <template #icon><icon-plus /></template>
            添加 Widget
          </a-button>
        </template>
      </EmptyState>
      <EmptyState
        v-else
        icon="bar-chart"
        title="暂无监控视图"
      />
    </template>

    <!-- 添加 Widget 弹窗 -->
    <a-modal
      v-model:visible="showAddWidgetModal"
      title="添加 Widget"
      :width="640"
      :footer="false"
    >
      <div class="widget-type-grid">
        <div
          v-for="wt in widgetTypeList"
          :key="wt.type"
          class="widget-type-card"
          @click="addOverviewWidget(wt.type, wt.defaultTitle)"
        >
          <div class="wt-icon">{{ wt.icon }}</div>
          <div class="wt-info">
            <div class="wt-name">{{ wt.label }}</div>
            <div class="wt-desc">{{ wt.description }}</div>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- Widget 配置弹窗 -->
    <a-modal
      v-model:visible="showWidgetConfigModal"
      title="编辑 Widget 配置"
      :width="520"
      @ok="handleWidgetConfigSave"
      :ok-loading="savingWidgetConfig"
      ok-text="保存"
      cancel-text="取消"
    >
      <a-form :model="widgetConfigForm" layout="vertical">
        <a-form-item label="标题">
          <a-input v-model="widgetConfigForm.title" placeholder="Widget 标题" :max-length="100" />
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
        </template>
        <!-- note 配置 -->
        <template v-if="editingWidgetType === 'note'">
          <a-form-item label="内容">
            <a-textarea v-model="widgetConfigForm.noteContent" placeholder="笔记内容" :auto-size="{ minRows: 3, maxRows: 8 }" />
          </a-form-item>
        </template>
        <!-- agile_chart 配置 -->
        <template v-if="editingWidgetType === 'agile_chart'">
          <a-form-item label="图表类型">
            <a-select v-model="widgetConfigForm.chartType" placeholder="选择图表类型">
              <a-option value="burndown">燃尽图</a-option>
              <a-option value="cumulative_flow">累积流图</a-option>
            </a-select>
          </a-form-item>
        </template>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import { IconPlus } from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { handleApiError } from '@/utils/errorHandler'
import { projectApi } from '@/api'
import { customDashboardApi } from '@/api/customDashboard'
import type { DashboardDetailVO, DashboardWidgetVO } from '@/api/customDashboard'
import WidgetCard from '@/components/base/WidgetCard.vue'
import { EmptyState } from '@/components/base'

const props = defineProps<{
  projectId: string
  canEdit: boolean
  isArchived: boolean
}>()

const loading = ref(false)
const overviewDashboard = ref<DashboardDetailVO | null>(null)
const showAddWidgetModal = ref(false)
const showWidgetConfigModal = ref(false)
const editingWidget = ref<DashboardWidgetVO | null>(null)
const editingWidgetType = ref<string>('')
const savingWidgetConfig = ref(false)
const widgetGridLayout = ref<Array<{ i: string; x: number; y: number; w: number; h: number }>>([])

const widgetConfigForm = ref<{
  title: string
  queryType?: string
  noteContent?: string
  chartType?: string
}>({
  title: '',
  queryType: undefined,
  noteContent: '',
  chartType: 'burndown'
})

const widgetTypeList = [
  { type: 'note', label: '快捷笔记', icon: '📝', description: '自由编辑内容', defaultTitle: '笔记' },
  { type: 'number_card', label: '数字卡片', icon: '🔢', description: '单数字大卡片统计', defaultTitle: '统计' },
  { type: 'report_distribution', label: '分布图表', icon: '📊', description: '按字段分组的图表', defaultTitle: '分布报表' },
  { type: 'issue_list', label: 'Issue 列表', icon: '📋', description: '按条件展示工单列表', defaultTitle: 'Issue 列表' },
  { type: 'activity_feed', label: '活动流', icon: '🔔', description: '最近的工单活动', defaultTitle: '最近活动' },
  { type: 'sprint_progress', label: 'Sprint 进度', icon: '🏃', description: 'Sprint 完成进度', defaultTitle: 'Sprint 进度' },
  { type: 'agile_chart', label: '敏捷图表', icon: '📉', description: '燃尽图或累积流图', defaultTitle: '敏捷图表' },
  { type: 'agile_board_status', label: '看板状态', icon: '📊', description: 'Sprint 工单状态分布', defaultTitle: '看板状态' },
  { type: 'calendar', label: '到期日历', icon: '📅', description: 'Issue 到期日历视图', defaultTitle: '到期日历' }
]

function buildWidgetGridLayout(widgets: DashboardWidgetVO[]) {
  widgetGridLayout.value = widgets.map(w => ({
    i: w.id,
    x: w.positionX,
    y: w.positionY,
    w: w.width,
    h: w.height
  }))
}

function getOverviewWidgetById(id: string): DashboardWidgetVO | undefined {
  return overviewDashboard.value?.widgets.find(w => w.id === id)
}

let widgetLayoutSaveTimer: ReturnType<typeof setTimeout> | null = null

function onWidgetLayoutUpdated(layout: Array<{ i: string; x: number; y: number; w: number; h: number }>) {
  if (widgetLayoutSaveTimer) clearTimeout(widgetLayoutSaveTimer)
  widgetLayoutSaveTimer = setTimeout(() => saveWidgetLayout(layout), 1000)
}

async function saveWidgetLayout(layout: Array<{ i: string; x: number; y: number; w: number; h: number }>) {
  if (!overviewDashboard.value || !props.canEdit) return
  try {
    const items = layout.map(item => ({
      widgetId: item.i,
      positionX: item.x,
      positionY: item.y,
      width: item.w,
      height: item.h
    }))
    const version = overviewDashboard.value.layoutVersion ?? 0
    await customDashboardApi.updateLayout(overviewDashboard.value.id, items, version)
    overviewDashboard.value.layoutVersion = version + 1
  } catch {
    // 布局保存失败静默处理
  }
}

async function loadOverviewDashboard(projectId: string) {
  loading.value = true
  try {
    const res = await projectApi.getOverviewDashboard(projectId)
    overviewDashboard.value = res.data || null
    if (overviewDashboard.value?.widgets) {
      buildWidgetGridLayout(overviewDashboard.value.widgets)
    }
  } catch {
    overviewDashboard.value = null
  } finally {
    loading.value = false
  }
}

async function addOverviewWidget(widgetType: string, defaultTitle: string) {
  if (!overviewDashboard.value) return
  showAddWidgetModal.value = false
  try {
    const res = await customDashboardApi.addWidget(overviewDashboard.value.id, {
      widgetType,
      title: defaultTitle,
      config: '{}'
    })
    if (res.data && overviewDashboard.value) {
      overviewDashboard.value.widgets = [...(overviewDashboard.value.widgets || []), res.data]
      buildWidgetGridLayout(overviewDashboard.value.widgets)
    }
    Message.success('Widget 已添加')
  } catch (e) {
    handleApiError(e, '添加 Widget 失败')
  }
}

function editOverviewWidget(widget: DashboardWidgetVO) {
  editingWidget.value = widget
  editingWidgetType.value = widget.widgetType
  const config = widget.config ? JSON.parse(widget.config) : {}
  widgetConfigForm.value = {
    title: widget.title || '',
    queryType: config.queryType,
    noteContent: config.content || '',
    chartType: config.chartType || 'burndown'
  }
  showWidgetConfigModal.value = true
}

async function handleWidgetConfigSave() {
  if (!editingWidget.value || !overviewDashboard.value) return
  savingWidgetConfig.value = true
  try {
    const config: Record<string, any> = {}
    if (editingWidgetType.value === 'number_card' && widgetConfigForm.value.queryType) {
      config.queryType = widgetConfigForm.value.queryType
    }
    if (editingWidgetType.value === 'note') {
      config.content = widgetConfigForm.value.noteContent
    }
    if (editingWidgetType.value === 'agile_chart') {
      config.chartType = widgetConfigForm.value.chartType
    }
    await customDashboardApi.updateWidget(
      overviewDashboard.value.id,
      editingWidget.value.id,
      {
        title: widgetConfigForm.value.title || undefined,
        config: JSON.stringify(config)
      }
    )
    showWidgetConfigModal.value = false
    await loadOverviewDashboard(props.projectId)
    Message.success('Widget 配置已保存')
  } catch (e) {
    handleApiError(e, '保存失败')
  } finally {
    savingWidgetConfig.value = false
  }
}

async function deleteOverviewWidget(widget: DashboardWidgetVO) {
  if (!overviewDashboard.value) return
  Modal.warning({
    title: '删除 Widget',
    content: `确定要删除「${widget.title || widget.widgetType}」Widget？`,
    okText: '删除',
    cancelText: '取消',
    onOk: async () => {
      try {
        await customDashboardApi.deleteWidget(overviewDashboard.value!.id, widget.id)
        if (overviewDashboard.value) {
          overviewDashboard.value.widgets = overviewDashboard.value.widgets.filter(w => w.id !== widget.id)
          buildWidgetGridLayout(overviewDashboard.value.widgets)
        }
        Message.success('Widget 已删除')
      } catch (e) {
        handleApiError(e, '删除失败')
      }
    }
  })
}

watch(() => props.projectId, (newId) => {
  if (newId) loadOverviewDashboard(newId)
}, { immediate: true })
</script>

<style scoped>
.section {
  margin-top: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text-1);
}

.section-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.widget-loading-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: var(--color-text-3);
}

.widget-loading-text {
  font-size: 13px;
}

.overview-widget-grid-item {
  border-radius: 8px;
  overflow: hidden;
}

.widget-empty-state {
  text-align: center;
  padding: 32px 0;
}

.widget-empty-icon {
  font-size: 36px;
  margin-bottom: 8px;
}

.widget-empty-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-2);
}

.widget-empty-desc {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--color-text-4);
}

.widget-type-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.widget-type-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.widget-type-card:hover {
  border-color: var(--tf-accent);
  background: var(--color-fill-1);
}

.wt-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.wt-info {
  flex: 1;
}

.wt-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.wt-desc {
  font-size: 11px;
  color: var(--color-text-3);
  margin-top: 2px;
}
</style>
