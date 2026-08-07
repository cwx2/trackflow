<template>
  <div class="integration-page">
    <div class="page-header">
      <div class="header-left">
        <router-link to="/admin" class="back-link">← 系统管理</router-link>
        <h1 class="page-title">第三方集成</h1>
        <p class="page-desc">管理外部系统适配器的启用状态、配置和事件日志</p>
      </div>
    </div>

    <a-tabs v-model:active-key="activeTab" class="integration-tabs">
      <!-- 适配器列表 Tab -->
      <a-tab-pane key="adapters" title="适配器">
        <div class="adapters-section">
          <a-spin :loading="adaptersLoading" class="full-spin">
            <div v-if="adapters.length === 0 && !adaptersLoading" class="empty-state">
              <div class="empty-icon">🔌</div>
              <h3 class="empty-title">暂无适配器</h3>
              <p class="empty-desc">系统尚未注册任何第三方集成适配器</p>
            </div>
            <div v-else class="adapter-grid">
              <div
                v-for="adapter in adapters"
                :key="adapter.adapterType"
                class="adapter-card"
                :class="{ 'adapter-card--enabled': adapter.enabled }"
              >
                <div class="adapter-header">
                  <div class="adapter-info">
                    <span class="adapter-icon">{{ getAdapterIcon(adapter.adapterType) }}</span>
                    <div>
                      <h3 class="adapter-name">{{ adapter.displayName }}</h3>
                      <span class="adapter-type">{{ adapter.adapterType }}</span>
                    </div>
                  </div>
                  <a-switch
                    :model-value="adapter.enabled"
                    :loading="togglingAdapter === adapter.adapterType"
                    @change="(val: boolean) => handleToggle(adapter.adapterType, val)"
                  />
                </div>

                <div class="adapter-stats">
                  <div class="stat-item">
                    <span class="stat-label">成功</span>
                    <span class="stat-value stat-value--success">{{ adapter.successCount }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">失败</span>
                    <span class="stat-value stat-value--failed">{{ adapter.failedCount }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">最近活动</span>
                    <span class="stat-value">{{ formatTime(adapter.lastActivityAt) }}</span>
                  </div>
                </div>

                <div class="adapter-events">
                  <span class="events-label">订阅事件：</span>
                  <a-tag
                    v-for="event in adapter.supportedEvents"
                    :key="event"
                    size="small"
                    class="event-tag"
                  >{{ formatEventType(event) }}</a-tag>
                  <span v-if="adapter.supportedEvents.length === 0" class="no-events">无（入站型）</span>
                </div>

                <div class="adapter-actions">
                  <a-button size="small" @click="openConfigDrawer(adapter.adapterType)">
                    配置
                  </a-button>
                </div>
              </div>
            </div>
          </a-spin>
        </div>
      </a-tab-pane>

      <!-- 日志 Tab -->
      <a-tab-pane key="logs" title="事件日志">
        <div class="logs-section">
          <div class="logs-filters">
            <a-select
              v-model="logFilters.adapterType"
              placeholder="适配器类型"
              allow-clear
              style="width: 160px"
              @change="fetchLogs"
            >
              <a-option v-for="adapter in adapters" :key="adapter.adapterType" :value="adapter.adapterType">
                {{ adapter.displayName }}
              </a-option>
            </a-select>
            <a-select
              v-model="logFilters.status"
              placeholder="状态"
              allow-clear
              style="width: 140px"
              @change="fetchLogs"
            >
              <a-option value="pending">待处理</a-option>
              <a-option value="processing">处理中</a-option>
              <a-option value="success">成功</a-option>
              <a-option value="failed">失败</a-option>
              <a-option value="cancelled">已取消</a-option>
            </a-select>
            <a-range-picker
              v-model="logFilters.dateRange"
              style="width: 280px"
              @change="fetchLogs"
            />
            <a-button @click="fetchLogs">刷新</a-button>
          </div>

          <a-table
            :data="logs"
            :loading="logsLoading"
            :pagination="logsPagination"
            row-key="id"
            @page-change="handleLogsPageChange"
            @page-size-change="handleLogsPageSizeChange"
          >
            <template #columns>
              <a-table-column title="适配器" data-index="adapterType" :width="100">
                <template #cell="{ record }">
                  <span class="adapter-type-badge">{{ record.adapterType }}</span>
                </template>
              </a-table-column>
              <a-table-column title="事件" data-index="eventType" :width="160">
                <template #cell="{ record }">
                  {{ formatEventType(record.eventType) }}
                </template>
              </a-table-column>
              <a-table-column title="方向" data-index="direction" :width="80">
                <template #cell="{ record }">
                  <span class="direction-badge" :class="`direction-badge--${record.direction}`">
                    {{ record.direction === 'outbound' ? '出站' : '入站' }}
                  </span>
                </template>
              </a-table-column>
              <a-table-column title="关联 ID" data-index="referenceId" :width="120" ellipsis />
              <a-table-column title="状态" data-index="status" :width="100">
                <template #cell="{ record }">
                  <a-tag :color="getStatusColor(record.status)" size="small">
                    {{ getStatusLabel(record.status) }}
                  </a-tag>
                </template>
              </a-table-column>
              <a-table-column title="重试" data-index="retryCount" :width="60" align="center" />
              <a-table-column title="错误信息" data-index="errorMessage" :width="200" ellipsis />
              <a-table-column title="时间" data-index="createdAt" :width="160">
                <template #cell="{ record }">
                  {{ formatDateTime(record.createdAt) }}
                </template>
              </a-table-column>
              <a-table-column title="操作" :width="80" fixed="right">
                <template #cell="{ record }">
                  <a-button
                    v-if="record.status === 'failed'"
                    type="text"
                    size="mini"
                    :loading="retryingId === record.id"
                    @click="handleRetry(record.id)"
                  >
                    重试
                  </a-button>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- 配置抽屉 -->
    <a-drawer
      v-model:visible="configDrawerVisible"
      :title="`${currentConfig?.displayName ?? ''} 配置`"
      :width="480"
      :footer="true"
    >
      <template v-if="currentConfig">
        <div class="config-status">
          <span class="config-label">状态</span>
          <a-switch
            :model-value="currentConfig.enabled"
            :loading="togglingAdapter === currentConfig.adapterType"
            @change="(val: boolean) => handleToggle(currentConfig!.adapterType, val)"
          />
          <span class="config-status-text">{{ currentConfig.enabled ? '已启用' : '已禁用' }}</span>
        </div>

        <a-divider />

        <div v-if="Object.keys(editingConfig).length > 0" class="config-form">
          <h4 class="config-section-title">适配器配置</h4>
          <a-form :model="editingConfig" layout="vertical">
            <a-form-item
              v-for="(value, key) in editingConfig"
              :key="key"
              :label="String(key)"
            >
              <a-input
                v-model="editingConfig[key]"
                :placeholder="`输入 ${String(key)}`"
              />
            </a-form-item>
          </a-form>
        </div>
        <div v-else class="config-empty">
          <p class="config-empty-text">此适配器尚无配置项。启用后系统将使用默认配置。</p>
          <p class="config-empty-hint">
            你可以手动添加配置（如 smtpHost、apiKey 等），保存后将存入系统设置。
          </p>
          <a-button size="small" @click="addConfigField">+ 添加配置项</a-button>
        </div>
      </template>

      <template #footer>
        <div class="drawer-footer">
          <a-button @click="configDrawerVisible = false">取消</a-button>
          <a-button type="primary" :loading="savingConfig" @click="handleSaveConfig">
            保存配置
          </a-button>
        </div>
      </template>
    </a-drawer>

    <!-- 添加配置字段弹窗 -->
    <a-modal v-model:visible="addFieldVisible" title="添加配置项" :width="360" @ok="confirmAddField">
      <a-form :model="newField" layout="vertical">
        <a-form-item label="配置键名">
          <a-input v-model="newField.key" placeholder="如 smtpHost, apiKey" />
        </a-form-item>
        <a-form-item label="配置值">
          <a-input v-model="newField.value" placeholder="配置值" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { integrationAdminApi } from '@/api'
import type { IntegrationAdapterVO, IntegrationConfigVO, IntegrationLogVO } from '@/api/integrationAdmin'

const activeTab = ref('adapters')

// ========== 适配器列表 ==========
const adapters = ref<IntegrationAdapterVO[]>([])
const adaptersLoading = ref(false)
const togglingAdapter = ref<string | null>(null)

async function fetchAdapters() {
  adaptersLoading.value = true
  try {
    const res = await integrationAdminApi.listAdapters()
    if (res.code === 0) {
      adapters.value = res.data
    }
  } catch (e) {
    Message.error('加载适配器列表失败')
  } finally {
    adaptersLoading.value = false
  }
}

async function handleToggle(adapterType: string, enabled: boolean) {
  togglingAdapter.value = adapterType
  try {
    const res = await integrationAdminApi.toggleAdapter(adapterType, enabled)
    if (res.code === 0) {
      Message.success(`适配器已${enabled ? '启用' : '禁用'}`)
      await fetchAdapters()
      // 同步配置抽屉状态
      if (currentConfig.value && currentConfig.value.adapterType === adapterType) {
        currentConfig.value.enabled = enabled
      }
    }
  } catch (e) {
    Message.error('操作失败')
  } finally {
    togglingAdapter.value = null
  }
}

// ========== 配置抽屉 ==========
const configDrawerVisible = ref(false)
const currentConfig = ref<IntegrationConfigVO | null>(null)
const editingConfig = reactive<Record<string, string>>({})
const savingConfig = ref(false)

async function openConfigDrawer(adapterType: string) {
  configDrawerVisible.value = true
  try {
    const res = await integrationAdminApi.getConfig(adapterType)
    if (res.code === 0) {
      currentConfig.value = res.data
      // 复制配置到编辑表单
      Object.keys(editingConfig).forEach(k => delete editingConfig[k])
      Object.entries(res.data.config).forEach(([k, v]) => {
        editingConfig[k] = v
      })
    }
  } catch (e) {
    Message.error('加载配置失败')
  }
}

async function handleSaveConfig() {
  if (!currentConfig.value) return
  savingConfig.value = true
  try {
    const res = await integrationAdminApi.updateConfig(currentConfig.value.adapterType, { ...editingConfig })
    if (res.code === 0) {
      Message.success('配置已保存')
      currentConfig.value = res.data
    }
  } catch (e) {
    Message.error('保存配置失败')
  } finally {
    savingConfig.value = false
  }
}

// 添加配置字段
const addFieldVisible = ref(false)
const newField = reactive({ key: '', value: '' })

function addConfigField() {
  newField.key = ''
  newField.value = ''
  addFieldVisible.value = true
}

function confirmAddField() {
  if (!newField.key.trim()) {
    Message.warning('键名不能为空')
    return
  }
  editingConfig[newField.key.trim()] = newField.value
  addFieldVisible.value = false
}

// ========== 日志列表 ==========
const logs = ref<IntegrationLogVO[]>([])
const logsLoading = ref(false)
const retryingId = ref<string | null>(null)
const logFilters = reactive({
  adapterType: undefined as string | undefined,
  status: undefined as string | undefined,
  dateRange: undefined as [string, string] | undefined
})
const logsPagination = reactive({
  total: 0,
  current: 1,
  pageSize: 20,
  showPageSize: true,
  pageSizeOptions: [20, 50, 100]
})

async function fetchLogs() {
  logsLoading.value = true
  try {
    const params: any = {
      page: logsPagination.current,
      pageSize: logsPagination.pageSize
    }
    if (logFilters.adapterType) params.adapterType = logFilters.adapterType
    if (logFilters.status) params.status = logFilters.status
    if (logFilters.dateRange && logFilters.dateRange[0]) {
      params.startTime = logFilters.dateRange[0] + 'T00:00:00'
      params.endTime = logFilters.dateRange[1] + 'T23:59:59'
    }
    const res = await integrationAdminApi.queryLogs(params)
    if (res.code === 0) {
      logs.value = res.data.list
      logsPagination.total = res.data.pagination.total
    }
  } catch (e) {
    Message.error('加载日志失败')
  } finally {
    logsLoading.value = false
  }
}

function handleLogsPageChange(page: number) {
  logsPagination.current = page
  fetchLogs()
}

function handleLogsPageSizeChange(size: number) {
  logsPagination.pageSize = size
  logsPagination.current = 1
  fetchLogs()
}

async function handleRetry(logId: string) {
  retryingId.value = logId
  try {
    const res = await integrationAdminApi.retryEvent(logId)
    if (res.code === 0) {
      Message.success(res.data.status === 'success' ? '重试成功' : '重试失败')
      await fetchLogs()
    }
  } catch (e) {
    Message.error('重试失败')
  } finally {
    retryingId.value = null
  }
}

// ========== 工具方法 ==========
function getAdapterIcon(type: string): string {
  const icons: Record<string, string> = {
    email: '📧',
    sug: '🔗',
    migration: '📦'
  }
  return icons[type] || '🔌'
}

function formatEventType(eventType: string): string {
  const names: Record<string, string> = {
    'issue.created': '工单创建',
    'issue.status_changed': '状态变更',
    'issue.cancelled': '工单取消',
    'issue.assigned': '工单分配',
    'comment.created': '评论创建',
    'apply.created': 'Apply 创建',
    'apply.approved': 'Apply 审批',
    'issue.priority_changed': '优先级变更',
    'issue.type_changed': '类型变更',
    'sprint.started': 'Sprint 开始',
    'sprint.completed': 'Sprint 完成',
    'project.member_added': '成员加入'
  }
  return names[eventType] || eventType
}

function formatTime(datetime: string | null): string {
  if (!datetime) return '无'
  const d = new Date(datetime)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return d.toLocaleDateString('zh-CN')
}

function formatDateTime(datetime: string): string {
  if (!datetime) return '-'
  const d = new Date(datetime)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function getStatusColor(status: string): string {
  const colors: Record<string, string> = {
    pending: 'orangered',
    processing: 'blue',
    success: 'green',
    failed: 'red',
    cancelled: 'gray'
  }
  return colors[status] || 'gray'
}

function getStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    pending: '待处理',
    processing: '处理中',
    success: '成功',
    failed: '失败',
    cancelled: '已取消'
  }
  return labels[status] || status
}

// ========== 初始化 ==========
onMounted(() => {
  fetchAdapters()
  fetchLogs()
})
</script>

<style scoped>
.integration-page {
  padding: 24px 32px;
  height: 100%;
  overflow-y: auto;
}

.page-header {
  margin-bottom: 24px;
}

.back-link {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  text-decoration: none;
  transition: color 0.15s;
}

.back-link:hover {
  color: var(--tf-accent);
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 8px 0 4px;
}

.page-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.integration-tabs {
  margin-top: 8px;
}

/* 适配器卡片 */
.full-spin {
  width: 100%;
}

.adapter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.adapter-card {
  padding: 20px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  transition: border-color 0.15s;
}

.adapter-card--enabled {
  border-color: var(--tf-accent);
}

.adapter-card:hover {
  border-color: var(--tf-border);
}

.adapter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.adapter-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.adapter-icon {
  font-size: 24px;
}

.adapter-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0;
}

.adapter-type {
  font-size: 11px;
  color: var(--tf-text-muted);
  font-family: monospace;
}

.adapter-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 11px;
  color: var(--tf-text-muted);
}

.stat-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.stat-value--success {
  color: var(--tf-success);
}

.stat-value--failed {
  color: var(--tf-error);
}

.adapter-events {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 12px;
}

.events-label {
  font-size: 11px;
  color: var(--tf-text-muted);
  margin-right: 4px;
}

.event-tag {
  font-size: 11px;
}

.no-events {
  font-size: 11px;
  color: var(--tf-text-muted);
  font-style: italic;
}

.adapter-actions {
  display: flex;
  gap: 8px;
}

/* 日志 */
.logs-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.logs-filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.adapter-type-badge {
  font-size: 11px;
  font-family: monospace;
  color: var(--tf-text-secondary);
  background: var(--tf-bg-body);
  padding: 2px 6px;
  border-radius: 3px;
}

.direction-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 3px;
}

.direction-badge--outbound {
  color: var(--tf-accent);
  background: rgba(var(--tf-accent-rgb, 88, 166, 255), 0.1);
}

.direction-badge--inbound {
  color: var(--tf-success);
  background: rgba(var(--tf-success-rgb, 63, 185, 80), 0.1);
}

/* 配置抽屉 */
.config-status {
  display: flex;
  align-items: center;
  gap: 12px;
}

.config-label {
  font-size: 13px;
  color: var(--tf-text-secondary);
  font-weight: 500;
}

.config-status-text {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.config-section-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  margin: 0 0 12px 0;
}

.config-empty {
  text-align: center;
  padding: 24px 0;
}

.config-empty-text {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0 0 8px 0;
}

.config-empty-hint {
  font-size: 12px;
  color: var(--tf-text-muted);
  margin: 0 0 16px 0;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 0;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}
</style>
