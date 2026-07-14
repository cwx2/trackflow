<template>
  <div class="report-page">
    <div class="report-header">
      <div class="header-left">
        <h1 class="page-title">报表</h1>
        <span class="page-desc">查看项目统计与进度报告</span>
      </div>
      <div class="header-right" v-if="canCreateReport">
        <a-button type="primary" size="small" @click="showCreateModal = true">
          <template #icon><span class="btn-icon">➕</span></template>
          创建报表
        </a-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="report-toolbar">
      <a-select
        v-model="selectedProjectId"
        placeholder="所有项目"
        allow-clear
        size="small"
        style="width: 200px"
        @change="loadReports"
      >
        <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
      </a-select>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="report-loading">
      <a-skeleton :animation="true" v-for="i in 3" :key="i" style="margin-bottom: 16px">
        <a-skeleton-line :rows="3" :widths="['50%', '80%', '30%']" />
      </a-skeleton>
    </div>

    <!-- 空状态 -->
    <div v-else-if="reports.length === 0" class="report-empty">
      <div class="empty-icon">📊</div>
      <h3 class="empty-title">暂无报表</h3>
      <p class="empty-desc">
        {{ canCreateReport ? '创建第一个报表来跟踪项目进度和工作统计。' : '还没有可查看的报表，请联系项目管理员创建。' }}
      </p>
      <a-button v-if="canCreateReport" type="primary" size="small" @click="showCreateModal = true">
        创建报表
      </a-button>
    </div>

    <!-- 报表列表 -->
    <div v-else class="report-grid">
      <div
        v-for="report in reports"
        :key="report.id"
        class="report-card"
        :class="{ 'is-loading': executingId === report.id }"
        @click="executeReport(report)"
      >
        <div class="card-header">
          <span class="card-type-badge" :class="'type-' + report.type">{{ reportTypeLabel(report.type) }}</span>
          <a-dropdown v-if="canCreateReport" trigger="click" @click.stop>
            <span class="card-menu-btn" @click.stop>⋯</span>
            <template #content>
              <a-doption @click="confirmDelete(report)">
                <span style="color: var(--tf-danger)">删除</span>
              </a-doption>
            </template>
          </a-dropdown>
        </div>
        <h3 class="card-title">{{ report.name }}</h3>
        <div class="card-meta">
          <span v-if="report.shared" class="meta-shared">🔗 已共享</span>
          <span class="meta-time">{{ formatTime(report.createdAt) }}</span>
        </div>

        <!-- 报表数据（展开后） -->
        <div v-if="reportData[report.id]" class="card-chart" @click.stop>
          <div class="chart-summary">
            <span class="chart-total">共 {{ reportData[report.id].total }} 个工单</span>
            <span class="chart-group">按 {{ groupByLabel(reportData[report.id].groupBy) }} 分组</span>
          </div>
          <div class="chart-bars">
            <div
              v-for="(label, idx) in reportData[report.id].labels"
              :key="idx"
              class="chart-bar-item"
            >
              <span class="bar-label">{{ label }}</span>
              <div class="bar-track">
                <div
                  class="bar-fill"
                  :style="{ width: barWidth(reportData[report.id].data[idx], reportData[report.id].total) }"
                ></div>
              </div>
              <span class="bar-value">{{ reportData[report.id].data[idx] }}</span>
            </div>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="executingId === report.id" class="card-executing">
          <a-spin :size="16" />
          <span>加载数据...</span>
        </div>
      </div>
    </div>

    <!-- 创建报表弹窗 -->
    <a-modal
      v-model:visible="showCreateModal"
      title="创建报表"
      :ok-text="'创建报表'"
      :cancel-text="'取消'"
      :ok-loading="creating"
      @ok="handleCreate"
      @cancel="resetForm"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="报表名称" required>
          <a-input v-model="form.name" placeholder="例如：本周 Bug 统计" :max-length="200" />
        </a-form-item>
        <a-form-item label="所属项目" required>
          <a-select v-model="form.projectId" placeholder="选择项目">
            <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="报表类型" required>
          <a-select v-model="form.type" placeholder="选择类型">
            <a-option value="issue_count">工单数量统计</a-option>
            <a-option value="by_status">按状态分布</a-option>
            <a-option value="by_assignee">按负责人分布</a-option>
            <a-option value="by_priority">按优先级分布</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分组依据">
          <a-select v-model="form.groupBy" placeholder="选择分组" :disabled="isGroupByLocked">
            <a-option value="status">状态</a-option>
            <a-option value="assignee">负责人</a-option>
            <a-option value="priority">优先级</a-option>
            <a-option value="type">工单类型</a-option>
          </a-select>
          <span v-if="isGroupByLocked" class="form-hint">已根据报表类型自动设置</span>
        </a-form-item>
        <a-form-item label="共享">
          <a-switch v-model="form.shared" />
          <span class="form-hint">共享后项目其他成员也可查看此报表</span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { reportApi } from '@/api/report'
import { projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { ReportDefinitionVO, ReportDataVO } from '@/api/report'
import type { ProjectVO } from '@/api/types'

const authStore = useAuthStore()

const loading = ref(true)
const reports = ref<ReportDefinitionVO[]>([])
const projects = ref<ProjectVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const reportData = ref<Record<string, ReportDataVO>>({})
const executingId = ref<string | null>(null)

// 创建相关
const showCreateModal = ref(false)
const creating = ref(false)
const form = reactive({
  name: '',
  projectId: '' as string,
  type: 'by_status',
  groupBy: 'status',
  shared: false
})

/** 是否有创建报表权限（system:admin 或 nav:report_create） */
const canCreateReport = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  return authStore.hasGlobalPermission('nav:report_create')
})

/** type → groupBy 自动映射 */
const typeToGroupByMap: Record<string, string> = {
  by_status: 'status',
  by_assignee: 'assignee',
  by_priority: 'priority'
}

/** 当 type 有固定的 groupBy 映射时，禁用 groupBy 选择 */
const isGroupByLocked = computed(() => form.type in typeToGroupByMap)

// type 变化时自动锁定 groupBy
watch(() => form.type, (newType) => {
  if (newType in typeToGroupByMap) {
    form.groupBy = typeToGroupByMap[newType]
  }
})

onMounted(async () => {
  await Promise.all([loadReports(), loadProjects()])
})

async function loadReports() {
  loading.value = true
  try {
    const res = await reportApi.list(selectedProjectId.value)
    reports.value = res.data || []
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载报表失败')
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    projects.value = res.data?.list || []
  } catch {
    // non-critical
  }
}

async function executeReport(report: ReportDefinitionVO) {
  // Toggle: 如果已有数据则折叠
  if (reportData.value[report.id]) {
    delete reportData.value[report.id]
    return
  }
  executingId.value = report.id
  try {
    const res = await reportApi.execute(report.id)
    reportData.value[report.id] = res.data
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行报表失败')
  } finally {
    executingId.value = null
  }
}

async function handleCreate() {
  if (!form.name.trim()) {
    Message.warning('请输入报表名称')
    return
  }
  if (!form.projectId) {
    Message.warning('请选择所属项目')
    return
  }
  if (!form.type) {
    Message.warning('请选择报表类型')
    return
  }

  creating.value = true
  try {
    const config = JSON.stringify({ groupBy: form.groupBy })
    await reportApi.create({
      name: form.name.trim(),
      projectId: form.projectId,
      type: form.type,
      config,
      shared: form.shared
    })
    Message.success('报表创建成功')
    showCreateModal.value = false
    resetForm()
    await loadReports()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建报表失败')
  } finally {
    creating.value = false
  }
}

function confirmDelete(report: ReportDefinitionVO) {
  Modal.warning({
    title: '确认删除',
    content: `确定要删除报表「${report.name}」吗？此操作不可恢复。`,
    okText: '删除报表',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      try {
        await reportApi.delete(report.id)
        Message.success('报表已删除')
        reports.value = reports.value.filter(r => r.id !== report.id)
        delete reportData.value[report.id]
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

function resetForm() {
  form.name = ''
  form.projectId = ''
  form.type = 'by_status'
  form.groupBy = 'status'
  form.shared = false
}

function reportTypeLabel(type: string) {
  const map: Record<string, string> = {
    issue_count: '数量统计',
    by_status: '状态分布',
    by_assignee: '负责人分布',
    by_priority: '优先级分布',
    burndown: '燃尽图',
    custom: '自定义'
  }
  return map[type] || type
}

function groupByLabel(groupBy: string) {
  const map: Record<string, string> = {
    status: '状态',
    assignee: '负责人',
    priority: '优先级',
    type: '工单类型'
  }
  return map[groupBy] || groupBy
}

function barWidth(value: number, total: number) {
  if (!total) return '0%'
  return Math.max(4, (value / total) * 100) + '%'
}

function formatTime(time: string) {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.report-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.report-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
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

.btn-icon {
  font-size: 12px;
  margin-right: 2px;
}

.report-toolbar {
  margin-bottom: 20px;
}

/* 加载状态 */
.report-loading {
  padding: 16px 0;
}

/* 空状态 */
.report-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
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
  margin: 0 0 24px;
  max-width: 320px;
  line-height: 1.5;
}

/* 报表网格 */
.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.report-card {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.report-card:hover {
  border-color: var(--tf-accent);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.report-card.is-loading {
  opacity: 0.7;
  pointer-events: none;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.card-type-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  font-weight: 500;
  background: var(--tf-bg-surface);
  color: var(--tf-text-secondary);
}

.card-type-badge.type-by_status { color: var(--tf-accent); background: rgba(88, 166, 255, 0.1); }
.card-type-badge.type-by_assignee { color: var(--tf-purple, #a371f7); background: rgba(163, 113, 247, 0.1); }
.card-type-badge.type-by_priority { color: var(--tf-warning); background: rgba(210, 153, 34, 0.1); }
.card-type-badge.type-issue_count { color: var(--tf-success); background: rgba(63, 185, 80, 0.1); }

.card-menu-btn {
  font-size: 16px;
  color: var(--tf-text-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.card-menu-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
  line-height: 1.3;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.meta-shared {
  color: var(--tf-accent);
}

/* 图表区域 */
.card-chart {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--tf-border-light);
}

.chart-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.chart-total {
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.chart-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chart-bar-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar-label {
  font-size: 11px;
  color: var(--tf-text-secondary);
  width: 72px;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-track {
  flex: 1;
  height: 6px;
  background: var(--tf-bg-surface);
  border-radius: 3px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: var(--tf-accent);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.bar-value {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-primary);
  width: 28px;
  text-align: right;
  flex-shrink: 0;
}

.card-executing {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* 表单提示 */
.form-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-left: 8px;
}
</style>
