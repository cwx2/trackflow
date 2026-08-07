<template>
  <div class="admin-page">
    <div class="page-header">
      <div class="header-left">
        <router-link to="/admin" class="back-link">← 返回管理</router-link>
        <h2 class="page-title">Webhook 管理</h2>
        <p class="page-desc">管理项目的 Webhook 通知，当事件发生时自动推送到外部系统。</p>
      </div>
    </div>

    <!-- 项目选择 -->
    <div class="filter-bar">
      <div class="filter-item">
        <label class="filter-label">项目</label>
        <a-select v-model="selectedProjectId" placeholder="请选择项目" size="small" style="min-width: 240px" allow-clear @change="loadWebhooks">
          <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }} ({{ p.key }})</a-option>
        </a-select>
      </div>
      <a-button v-if="selectedProjectId" type="primary" size="small" @click="openCreateDialog">
        <template #icon><icon-plus /></template>
        新建 Webhook
      </a-button>
    </div>

    <!-- 无项目选择提示 -->
    <div v-if="!selectedProjectId" class="empty-state">
      <div class="empty-icon">🔗</div>
      <p class="empty-title">选择项目查看 Webhook</p>
      <p class="empty-desc">Webhook 绑定在项目级别，请先选择一个项目</p>
    </div>

    <!-- Webhook 列表 -->
    <template v-else>
      <div v-if="loading" class="loading-state">
        <a-spin :size="20" />
        <span>加载中...</span>
      </div>

      <div v-else-if="webhooks.length === 0" class="empty-state">
        <div class="empty-icon">🪝</div>
        <p class="empty-title">暂无 Webhook</p>
        <p class="empty-desc">创建 Webhook 来接收项目事件通知</p>
        <a-button type="outline" size="small" style="margin-top: 12px" @click="openCreateDialog">
          <template #icon><icon-plus /></template>
          创建 Webhook
        </a-button>
      </div>

      <div v-else class="webhook-list">
        <div v-for="wh in webhooks" :key="wh.id" class="webhook-card">
          <div class="webhook-main">
            <div class="webhook-header">
              <span class="webhook-name">{{ wh.name }}</span>
              <span class="status-badge" :class="wh.active ? 'active' : 'inactive'">
                {{ wh.active ? '启用' : '禁用' }}
              </span>
            </div>
            <div class="webhook-url">{{ wh.url }}</div>
            <div class="webhook-meta">
              <span class="meta-item">
                <span class="meta-label">事件：</span>
                <span class="event-tags">
                  <span v-for="ev in parseEvents(wh.events)" :key="ev" class="event-tag">{{ ev }}</span>
                </span>
              </span>
              <span class="meta-item">
                <span class="meta-label">创建时间：</span>
                {{ formatDate(wh.createdAt) }}
              </span>
            </div>
          </div>
          <div class="webhook-actions">
            <a-button type="text" size="mini" @click="viewLogs(wh)" title="查看投递日志">
              <template #icon><icon-file /></template>
              日志
            </a-button>
            <a-button type="text" size="mini" @click="testWebhook(wh)" :disabled="testingId === wh.id" title="发送测试请求">
              <template #icon><icon-thunderbolt /></template>
              {{ testingId === wh.id ? '测试中...' : '测试' }}
            </a-button>
            <a-button type="text" size="mini" @click="openEditDialog(wh)" title="编辑">
              <template #icon><icon-edit /></template>
              编辑
            </a-button>
            <a-button type="text" size="mini" status="danger" @click="confirmDelete(wh)" title="删除">
              <template #icon><icon-delete /></template>
              删除
            </a-button>
          </div>
        </div>
      </div>
    </template>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="showFormDialog"
      :title="editingWebhook ? '编辑 Webhook' : '创建 Webhook'"
      :width="520"
      :ok-text="editingWebhook ? '保存修改' : '创建 Webhook'"
      cancel-text="取消"
      :ok-loading="submitting"
      :ok-button-props="{ disabled: !canSubmit }"
      @before-ok="submitForm"
    >
      <a-form :model="formData" layout="vertical" size="medium">
        <a-form-item label="名称" required>
          <a-input v-model="formData.name" placeholder="例如：CI/CD 通知" />
        </a-form-item>
        <a-form-item label="URL" required>
          <a-input v-model="formData.url" placeholder="https://example.com/webhook" />
        </a-form-item>
        <a-form-item label="Secret（可选）">
          <a-input-password v-model="formData.secret" placeholder="用于签名验证的密钥" />
        </a-form-item>
        <a-form-item label="触发事件" required>
          <a-checkbox-group v-model="formData.selectedEvents" direction="vertical">
            <div class="event-checkboxes">
              <a-checkbox v-for="ev in availableEvents" :key="ev.value" :value="ev.value">
                {{ ev.label }}
              </a-checkbox>
            </div>
          </a-checkbox-group>
        </a-form-item>
        <a-form-item label="启用状态">
          <a-switch v-model="formData.active" checked-text="启用" unchecked-text="禁用" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 投递日志弹窗 -->
    <a-modal
      v-model:visible="showLogDialog"
      :title="`投递日志 — ${logWebhookName}`"
      :width="700"
      :footer="false"
    >
      <div v-if="logsLoading" class="loading-state">
        <a-spin :size="20" />
        <span>加载中...</span>
      </div>
      <div v-else-if="logs.length === 0" class="empty-section">
        暂无投递记录
      </div>
      <div v-else class="log-list">
        <div v-for="log in logs" :key="log.id" class="log-item" @click="toggleLogDetail(log.id)">
          <div class="log-summary">
            <span class="log-status" :class="log.success ? 'success' : 'failed'">
              {{ log.success ? '✓' : '✗' }}
            </span>
            <span class="log-event">{{ log.event }}</span>
            <span class="log-code" v-if="log.responseStatus">HTTP {{ log.responseStatus }}</span>
            <span class="log-time">{{ formatDateTime(log.createdAt) }}</span>
          </div>
          <div v-if="expandedLogId === log.id" class="log-detail">
            <div class="log-detail-row" v-if="log.responseBody">
              <span class="detail-label">响应内容：</span>
              <pre class="detail-code">{{ log.responseBody }}</pre>
            </div>
          </div>
        </div>
        <!-- 分页 -->
        <div v-if="logPagination.total > logPagination.pageSize" class="log-pagination">
          <a-pagination
            :current="logPagination.page"
            :page-size="logPagination.pageSize"
            :total="logPagination.total"
            size="small"
            :show-total="true"
            @change="loadLogs"
          />
        </div>
      </div>
    </a-modal>

    <!-- 删除确认弹窗 -->
    <a-modal
      v-model:visible="showDeleteDialog"
      title="确认删除"
      :width="420"
      ok-text="删除 Webhook"
      cancel-text="取消"
      :ok-loading="deleting"
      :ok-button-props="{ status: 'danger' }"
      @before-ok="doDelete"
    >
      <p>确定要删除 Webhook <strong>{{ deletingWebhook?.name }}</strong> 吗？</p>
      <p class="warning-text">此操作不可撤销，投递日志也将被清除。</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { projectApi, webhookApi } from '@/api'
import type { WebhookVO, WebhookLogVO } from '@/api/webhook'

// === State ===
const projects = ref<{ id: string; name: string; key: string }[]>([])
const selectedProjectId = ref('')
const webhooks = ref<WebhookVO[]>([])
const loading = ref(false)

// Form
const showFormDialog = ref(false)
const editingWebhook = ref<WebhookVO | null>(null)
const submitting = ref(false)
const formData = ref({
  name: '',
  url: '',
  secret: '',
  selectedEvents: [] as string[],
  active: true
})

// Delete
const showDeleteDialog = ref(false)
const deletingWebhook = ref<WebhookVO | null>(null)
const deleting = ref(false)

// Test
const testingId = ref<string | null>(null)

// Logs
const showLogDialog = ref(false)
const logWebhookId = ref('')
const logWebhookName = ref('')
const logs = ref<WebhookLogVO[]>([])
const logsLoading = ref(false)
const expandedLogId = ref<string | null>(null)
const logPagination = ref({ page: 1, pageSize: 20, total: 0 })

// === Constants ===
const availableEvents = [
  { value: 'issue.created', label: '工单创建' },
  { value: 'issue.updated', label: '工单更新' },
  { value: 'issue.status_changed', label: '工单状态变更' },
  { value: 'issue.assigned', label: '工单分配' },
  { value: 'issue.commented', label: '工单评论' },
  { value: 'issue.deleted', label: '工单删除' },
  { value: 'sprint.started', label: 'Sprint 开始' },
  { value: 'sprint.completed', label: 'Sprint 完成' },
  { value: '*', label: '所有事件' }
]

// === Computed ===
const canSubmit = computed(() => {
  return formData.value.name.trim() && formData.value.url.trim() && formData.value.selectedEvents.length > 0
})

// === Methods ===
function parseEvents(eventsJson: string): string[] {
  try {
    const events = JSON.parse(eventsJson)
    return Array.isArray(events) ? events : []
  } catch {
    return []
  }
}

function formatDate(dateStr: string): string {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function formatDateTime(dateStr: string): string {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    if (res.code === 0 && res.data) {
      projects.value = res.data.list
    }
  } catch (e) {
    console.error('[Webhook] 加载项目列表失败:', e)
  }
}

async function loadWebhooks() {
  if (!selectedProjectId.value) {
    webhooks.value = []
    return
  }
  loading.value = true
  try {
    const res = await webhookApi.list(selectedProjectId.value)
    if (res.code === 0) {
      webhooks.value = res.data || []
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载 Webhook 列表失败')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingWebhook.value = null
  formData.value = { name: '', url: '', secret: '', selectedEvents: [], active: true }
  showFormDialog.value = true
}

function openEditDialog(wh: WebhookVO) {
  editingWebhook.value = wh
  formData.value = {
    name: wh.name,
    url: wh.url,
    secret: '',
    selectedEvents: parseEvents(wh.events),
    active: wh.active
  }
  showFormDialog.value = true
}

async function submitForm(done: (closed: boolean) => void) {
  if (!canSubmit.value) {
    done(false)
    return
  }
  submitting.value = true
  const eventsJson = JSON.stringify(formData.value.selectedEvents)

  try {
    if (editingWebhook.value) {
      await webhookApi.update(editingWebhook.value.id, {
        name: formData.value.name,
        url: formData.value.url,
        secret: formData.value.secret || undefined,
        events: eventsJson,
        active: formData.value.active
      })
      Message.success('Webhook 已更新')
    } else {
      await webhookApi.create({
        projectId: selectedProjectId.value,
        name: formData.value.name,
        url: formData.value.url,
        secret: formData.value.secret || undefined,
        events: eventsJson,
        active: formData.value.active
      })
      Message.success('Webhook 已创建')
    }
    done(true)
    await loadWebhooks()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
    done(false)
  } finally {
    submitting.value = false
  }
}

function confirmDelete(wh: WebhookVO) {
  deletingWebhook.value = wh
  showDeleteDialog.value = true
}

async function doDelete(done: (closed: boolean) => void) {
  if (!deletingWebhook.value) {
    done(false)
    return
  }
  deleting.value = true
  try {
    await webhookApi.delete(deletingWebhook.value.id)
    Message.success('Webhook 已删除')
    done(true)
    await loadWebhooks()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
    done(false)
  } finally {
    deleting.value = false
  }
}

async function testWebhook(wh: WebhookVO) {
  testingId.value = wh.id
  try {
    const res = await webhookApi.testTrigger(wh.id)
    if (res.code === 0 && res.data) {
      if (res.data.success) {
        Message.success(`测试成功，HTTP ${res.data.responseStatus}`)
      } else {
        Message.warning(`测试失败：HTTP ${res.data.responseStatus || '连接失败'}`)
      }
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '测试请求失败')
  } finally {
    testingId.value = null
  }
}

async function viewLogs(wh: WebhookVO) {
  logWebhookId.value = wh.id
  logWebhookName.value = wh.name
  expandedLogId.value = null
  showLogDialog.value = true
  await loadLogs(1)
}

async function loadLogs(page: number) {
  logsLoading.value = true
  try {
    const res = await webhookApi.getDeliveryLogs(logWebhookId.value, page, logPagination.value.pageSize)
    if (res.code === 0 && res.data) {
      logs.value = res.data.list
      logPagination.value = {
        page: res.data.pagination.page,
        pageSize: res.data.pagination.pageSize || 20,
        total: res.data.pagination.total
      }
    }
  } catch (e) {
    console.error('[Webhook] 加载投递日志失败:', e)
  } finally {
    logsLoading.value = false
  }
}

function toggleLogDetail(id: string) {
  expandedLogId.value = expandedLogId.value === id ? null : id
}

// === Lifecycle ===
onMounted(async () => {
  await loadProjects()
})
</script>

<style scoped>
.admin-page {
  padding: 32px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-header {
  margin-bottom: 24px;
  flex-shrink: 0;
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
  margin: 8px 0 4px 0;
}

.page-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

/* Filter bar */
.filter-bar {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 24px;
  flex-shrink: 0;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.filter-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  font-weight: 500;
}

/* Empty & Loading */
.empty-state, .loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.loading-state {
  flex-direction: row;
  gap: 8px;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.empty-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 6px 0;
}

.empty-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.empty-section {
  padding: 24px;
  text-align: center;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}

/* Webhook list */
.webhook-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.webhook-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px 20px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  transition: border-color 0.15s;
}
.webhook-card:hover {
  border-color: var(--tf-border);
}

.webhook-main {
  flex: 1;
  min-width: 0;
}

.webhook-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.webhook-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  font-weight: 500;
}
.status-badge.active {
  background: rgba(63, 185, 80, 0.15);
  color: var(--tf-success, #3fb950);
}
.status-badge.inactive {
  background: rgba(139, 148, 158, 0.15);
  color: var(--tf-text-tertiary);
}

.webhook-url {
  font-size: 12px;
  color: var(--tf-text-secondary);
  font-family: monospace;
  margin-bottom: 8px;
  word-break: break-all;
}

.webhook-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.meta-item {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-label {
  color: var(--tf-text-muted, var(--tf-text-tertiary));
}

.event-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
}

.event-tag {
  font-size: 10px;
  padding: 1px 6px;
  background: var(--tf-bg-body);
  border: 1px solid var(--tf-border-light);
  border-radius: 3px;
  color: var(--tf-text-secondary);
}

.webhook-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  margin-left: 16px;
}

/* Form events grid */
.event-checkboxes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

/* Warning text in delete dialog */
.warning-text {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-top: 8px;
}

/* Log list */
.log-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.log-item {
  padding: 10px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}
.log-item:hover {
  background: var(--tf-bg-hover);
}

.log-summary {
  display: flex;
  align-items: center;
  gap: 10px;
}

.log-status {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
.log-status.success {
  background: rgba(63, 185, 80, 0.15);
  color: var(--tf-success, #3fb950);
}
.log-status.failed {
  background: rgba(248, 81, 73, 0.15);
  color: var(--tf-error, #f85149);
}

.log-event {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  flex: 1;
}

.log-code {
  font-size: 11px;
  font-family: monospace;
  color: var(--tf-text-secondary);
  padding: 2px 6px;
  background: var(--tf-bg-body);
  border-radius: 3px;
}

.log-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  white-space: nowrap;
}

.log-detail {
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--tf-bg-body);
  border-radius: 4px;
  border: 1px solid var(--tf-border-light);
}

.log-detail-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  font-weight: 500;
}

.detail-code {
  font-size: 11px;
  font-family: monospace;
  color: var(--tf-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 120px;
  overflow-y: auto;
}

.log-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 12px;
  border-top: 1px solid var(--tf-border-light);
  margin-top: 12px;
}
</style>
