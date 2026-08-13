<template>
  <AdminPageLayout title="Webhook 管理" subtitle="管理项目的 Webhook 通知，当事件发生时自动推送到外部系统。">
    <template #actions>
      <a-button v-if="selectedProjectId" type="primary" size="small" aria-label="新建 Webhook" @click="openCreateDialog">
        <template #icon><icon-plus /></template>
        新建 Webhook
      </a-button>
    </template>

    <!-- 项目选择 -->
    <div class="filter-bar">
      <div class="filter-item">
        <label class="filter-label">项目</label>
        <a-select v-model="selectedProjectId" placeholder="请选择项目" size="small" style="min-width: 240px" allow-clear @change="loadWebhooks">
          <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }} ({{ p.key }})</a-option>
        </a-select>
      </div>
    </div>

    <!-- 无项目选择提示 -->
    <EmptyState
      v-if="!selectedProjectId"
      icon="link"
      title="选择项目查看 Webhook"
      description="Webhook 绑定在项目级别，请先选择一个项目"
    />

    <!-- Webhook 列表 -->
    <template v-else>
      <AdminDataTable
        :show-toolbar="false"
        :data="webhooks"
        :loading="loading"
        :total="webhooks.length"
        :current="1"
        :page-size="webhooks.length || 20"
        :columns="tableColumns"
        empty-title="暂无 Webhook"
        empty-description="创建 Webhook 来接收项目事件通知"
      />
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
      <DataContainer
        :loading="logsLoading"
        :is-empty="logs.length === 0"
        empty-title="暂无投递记录"
        empty-description="Webhook 触发后，投递日志将在此显示"
      >
      <div class="log-list">
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
            show-page-size
            :page-size-options="[20, 50, 100]"
            @change="loadLogs"
            @page-size-change="onLogPageSizeChange"
          />
        </div>
      </div>
      </DataContainer>
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
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { formatDateTime } from '@/utils/date'
import { ref, computed, onMounted, h } from 'vue'
import { Message } from '@arco-design/web-vue'
import { projectApi, webhookApi } from '@/api'
import type { WebhookVO, WebhookLogVO } from '@/api/webhook'
import AdminPageLayout from '@/components/admin/AdminPageLayout.vue'
import DataContainer from '@/components/base/DataContainer.vue'
import { AdminDataTable } from '@/components/admin'
import type { ColumnDef } from '@/components/admin'
import { EmptyState } from '@/components/base'

// === Table Columns ===
const tableColumns: ColumnDef[] = [
  {
    type: 'render', title: '名称 / URL', width: 280,
    render: (record: any) => h('div', { style: 'display:flex;flex-direction:column;gap:3px' }, [
      h('span', { style: 'font-size:13px;font-weight:500;color:var(--tf-text-primary)' }, record.name),
      h('span', { style: 'font-size:11px;color:var(--tf-text-tertiary);font-family:monospace;word-break:break-all' }, record.url),
    ]),
  },
  {
    type: 'status', title: '状态', key: 'active', width: 90,
    activeValue: (r: any) => r.active,
    activeLabel: '启用', inactiveLabel: '禁用',
  },
  {
    type: 'render', title: '订阅事件', ellipsis: true,
    render: (record: any) => {
      const events = parseEvents(record.events)
      return h('div', { style: 'display:flex;flex-wrap:wrap;gap:3px' },
        events.map((ev: string) => h('span', {
          style: 'font-size:10px;padding:1px 6px;background:var(--tf-bg-body);border:1px solid var(--tf-border-light);border-radius:3px;color:var(--tf-text-secondary)'
        }, ev))
      )
    },
  },
  { type: 'date', title: '创建时间', key: 'createdAt', width: 150, format: 'datetime' },
  {
    type: 'actions', width: 200,
    actions: (record: any) => [
      { label: '日志', onClick: (r) => viewLogs(r) },
      { label: testingId.value === record.id ? '测试中...' : '测试', disabled: testingId.value === record.id, onClick: (r) => testWebhook(r) },
      { label: '编辑', onClick: (r) => openEditDialog(r) },
      { label: '删除', danger: true, onClick: (r) => confirmDelete(r) },
    ],
  },
]

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

function onLogPageSizeChange(size: number) {
  logPagination.value.pageSize = size
  loadLogs(1)
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
/* Filter bar */
.filter-bar { display: flex; align-items: flex-end; gap: 16px; margin-bottom: 24px; flex-shrink: 0; }
.filter-item { display: flex; flex-direction: column; gap: 4px; }
.filter-label { font-size: 12px; color: var(--tf-text-tertiary); font-weight: 500; }

/* Form events grid */
.event-checkboxes { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }

/* Delete dialog */
.warning-text { font-size: 12px; color: var(--tf-text-tertiary); margin-top: 8px; }

/* Log list */
.log-list { display: flex; flex-direction: column; gap: 2px; }
.log-item { padding: 10px 12px; border-radius: 4px; cursor: pointer; transition: background 0.15s; }
.log-item:hover { background: var(--tf-bg-hover); }
.log-summary { display: flex; align-items: center; gap: 10px; }
.log-status { width: 18px; height: 18px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 600; flex-shrink: 0; }
.log-status.success { background: var(--tf-success-bg); color: var(--tf-success); }
.log-status.failed { background: var(--tf-danger-medium); color: var(--tf-danger); }
.log-event { font-size: 12px; font-weight: 500; color: var(--tf-text-primary); flex: 1; }
.log-code { font-size: 11px; font-family: monospace; color: var(--tf-text-secondary); padding: 2px 6px; background: var(--tf-bg-body); border-radius: 3px; }
.log-time { font-size: 11px; color: var(--tf-text-tertiary); white-space: nowrap; }
.log-detail { margin-top: 8px; padding: 10px 12px; background: var(--tf-bg-body); border-radius: 4px; border: 1px solid var(--tf-border-light); }
.log-detail-row { display: flex; flex-direction: column; gap: 4px; }
.detail-label { font-size: 11px; color: var(--tf-text-tertiary); font-weight: 500; }
.detail-code { font-size: 11px; font-family: monospace; color: var(--tf-text-secondary); white-space: pre-wrap; word-break: break-all; margin: 0; max-height: 120px; overflow-y: auto; }
.log-pagination { display: flex; align-items: center; justify-content: center; padding-top: 12px; border-top: 1px solid var(--tf-border-light); margin-top: 12px; }
</style>
