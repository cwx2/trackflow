<template>
  <div class="lt-page">
    <!-- Header -->
    <div class="lt-header">
      <router-link to="/admin" class="back-link">← 返回管理</router-link>
      <div class="header-row">
        <div>
          <h1 class="page-title">关联类型</h1>
          <p class="page-desc">管理工单关联类型。删除关联类型时，所有使用该类型的工单关联记录将被自动删除。</p>
        </div>
        <a-button type="primary" @click="openCreateDialog">
          新建关联类型
        </a-button>
      </div>
    </div>

    <!-- List with DataContainer for loading / empty states -->
    <DataContainer
      :loading="loading"
      :error="error"
      :is-empty="linkTypes.length === 0"
      :retry="loadLinkTypes"
      empty-title="暂无自定义关联类型"
      empty-description="系统内置了 7 种标准关联类型。你可以创建自定义类型以满足特殊业务需求。"
      create-action="创建第一个关联类型"
      @create="openCreateDialog"
    >
      <a-table
        :data="linkTypes"
        :pagination="false"
        :bordered="false"
        row-key="id"
        class="lt-table"
      >
        <template #columns>
          <a-table-column title="名称（内部标识）" data-index="name" :width="160">
            <template #cell="{ record }">
              <span class="type-name">{{ record.name }}</span>
              <span v-if="record.isSystem" class="system-badge">系统</span>
            </template>
          </a-table-column>
          <a-table-column title="正向显示名（outward）" data-index="outwardName" :width="180" />
          <a-table-column title="反向显示名（inward）" data-index="inwardName" :width="180" />
          <a-table-column title="方向" data-index="direction" :width="120">
            <template #cell="{ record }">
              <span class="direction-badge" :class="directionClass(record.direction)">
                {{ directionLabel(record.direction) }}
              </span>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="120" align="right">
            <template #cell="{ record }">
              <a-space>
                <a-button
                  size="small"
                  type="text"
                  :disabled="record.isSystem"
                  @click="openEditDialog(record)"
                >编辑</a-button>
                <a-button
                  size="small"
                  type="text"
                  status="danger"
                  :disabled="record.isSystem"
                  @click="confirmDelete(record)"
                >删除</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </DataContainer>

    <!-- Create / Edit Dialog -->
    <a-modal
      v-model:visible="showFormDialog"
      :title="editingId ? '编辑关联类型' : '新建关联类型'"
      :ok-loading="saving"
      ok-text="保存"
      @ok="submitForm"
      @cancel="resetForm"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item v-if="!editingId" label="内部名称" required>
          <a-input
            v-model="form.name"
            placeholder="例如：depends_on（仅字母、数字和下划线）"
            :max-length="50"
          />
          <div class="field-hint">内部标识符，创建后不可修改。用于工单关联时的类型选择。</div>
        </a-form-item>
        <a-form-item label="正向显示名（outward）" required>
          <a-input
            v-model="form.outwardName"
            placeholder="例如：depends on"
            :max-length="100"
          />
          <div class="field-hint">从源工单视角描述关系，如「A depends on B」中的「depends on」</div>
        </a-form-item>
        <a-form-item label="反向显示名（inward）" required>
          <a-input
            v-model="form.inwardName"
            placeholder="例如：is depended on by"
            :max-length="100"
          />
          <div class="field-hint">从目标工单视角描述关系，如「B is depended on by A」中的「is depended on by」</div>
        </a-form-item>
        <a-form-item label="方向类型" required>
          <a-select v-model="form.direction" placeholder="选择方向类型">
            <a-option value="DIRECTED">有向（Directed）— 正向/反向含义不同</a-option>
            <a-option value="UNDIRECTED">无向（Undirected）— 双向含义相同</a-option>
            <a-option value="AGGREGATION">聚合（Aggregation）— 父子层级关系</a-option>
          </a-select>
          <div class="field-hint">有向适合「blocks」、「depends_on」；无向适合「relates_to」；聚合适合「parent_of」</div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Delete Confirm Dialog -->
    <a-modal
      v-model:visible="showDeleteDialog"
      title="确认删除关联类型"
      :ok-loading="deleting"
      ok-text="确认删除"
      :ok-button-props="{ status: 'danger' }"
      cancel-text="取消"
      @ok="executeDelete"
      @cancel="cancelDelete"
    >
      <div class="delete-content">
        <div class="delete-warning">
          <span class="warning-icon">⚠️</span>
          <div>
            <p class="warning-title">即将删除关联类型「{{ deletingItem?.outwardName }}」（{{ deletingItem?.name }}）</p>
            <p v-if="deleteUsageCount === null" class="warning-loading">正在检查使用情况...</p>
            <p v-else-if="deleteUsageCount > 0" class="warning-impact">
              此操作将同时删除 <strong>{{ deleteUsageCount }}</strong> 条使用该类型的工单关联记录。
            </p>
            <p v-else class="warning-safe">当前没有工单使用此关联类型，可以安全删除。</p>
          </div>
        </div>
        <p class="delete-irreversible">此操作不可撤销。</p>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { linkTypeApi } from '@/api'
import type { IssueLinkTypeVO } from '@/api/types'
import { useRequest } from '@/composables/useRequest'
import DataContainer from '@/components/base/DataContainer.vue'

const saving = ref(false)
const deleting = ref(false)

const linkTypes = ref<IssueLinkTypeVO[]>([])
const showFormDialog = ref(false)
const showDeleteDialog = ref(false)
const editingId = ref<string | null>(null)
const deletingItem = ref<IssueLinkTypeVO | null>(null)
const deleteUsageCount = ref<number | null>(null)

const { loading, error, execute: loadLinkTypes } = useRequest(
  () => linkTypeApi.list(),
  {
    immediate: false,
    onSuccess: (data) => {
      linkTypes.value = data as IssueLinkTypeVO[]
    }
  }
)

const form = reactive({
  name: '',
  outwardName: '',
  inwardName: '',
  direction: 'DIRECTED'
})

function directionLabel(direction: string): string {
  const map: Record<string, string> = {
    DIRECTED: '有向',
    UNDIRECTED: '无向',
    AGGREGATION: '聚合'
  }
  return map[direction] || direction
}

function directionClass(direction: string): string {
  const map: Record<string, string> = {
    DIRECTED: 'directed',
    UNDIRECTED: 'undirected',
    AGGREGATION: 'aggregation'
  }
  return map[direction] || ''
}

function openCreateDialog() {
  editingId.value = null
  resetForm()
  showFormDialog.value = true
}

function openEditDialog(item: IssueLinkTypeVO) {
  editingId.value = item.id
  form.name = item.name
  form.outwardName = item.outwardName
  form.inwardName = item.inwardName
  form.direction = item.direction
  showFormDialog.value = true
}

function resetForm() {
  editingId.value = null
  form.name = ''
  form.outwardName = ''
  form.inwardName = ''
  form.direction = 'DIRECTED'
}

async function submitForm() {
  if (!editingId.value && !form.name.trim()) {
    Message.warning('请输入内部名称')
    return
  }
  if (!form.outwardName.trim() || !form.inwardName.trim()) {
    Message.warning('请填写正向和反向显示名')
    return
  }
  if (!form.direction) {
    Message.warning('请选择方向类型')
    return
  }

  saving.value = true
  try {
    if (editingId.value) {
      const res = await linkTypeApi.update(editingId.value, {
        outwardName: form.outwardName.trim(),
        inwardName: form.inwardName.trim(),
        direction: form.direction
      })
      if (res.code === 0) {
        Message.success('关联类型已更新')
        showFormDialog.value = false
        await loadLinkTypes()
      }
    } else {
      const res = await linkTypeApi.create({
        name: form.name.trim(),
        outwardName: form.outwardName.trim(),
        inwardName: form.inwardName.trim(),
        direction: form.direction
      })
      if (res.code === 0) {
        Message.success('关联类型已创建')
        showFormDialog.value = false
        resetForm()
        await loadLinkTypes()
      }
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function confirmDelete(item: IssueLinkTypeVO) {
  deletingItem.value = item
  deleteUsageCount.value = null
  showDeleteDialog.value = true

  // 异步加载使用数量
  try {
    const res = await linkTypeApi.getUsageCount(item.id)
    if (res.code === 0) {
      deleteUsageCount.value = res.data ?? 0
    }
  } catch {
    deleteUsageCount.value = 0
  }
}

function cancelDelete() {
  showDeleteDialog.value = false
  deletingItem.value = null
  deleteUsageCount.value = null
}

async function executeDelete() {
  if (!deletingItem.value) return
  deleting.value = true
  try {
    const res = await linkTypeApi.delete(deletingItem.value.id)
    if (res.code === 0) {
      const count = deleteUsageCount.value ?? 0
      const msg = count > 0
        ? `关联类型已删除，同时清除了 ${count} 条关联记录`
        : '关联类型已删除'
      Message.success(msg)
      showDeleteDialog.value = false
      deletingItem.value = null
      deleteUsageCount.value = null
      await loadLinkTypes()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

onMounted(() => loadLinkTypes())
</script>

<style scoped>
.lt-page {
  padding: 32px;
  max-width: 960px;
  height: 100%;
  overflow-y: auto;
}

.lt-header {
  margin-bottom: 24px;
}

.back-link {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  text-decoration: none;
  display: inline-block;
  margin-bottom: 12px;
  transition: color 0.15s;
}

.back-link:hover {
  color: var(--tf-accent);
}

.header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 4px 0;
}

.page-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.lt-table {
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  overflow: hidden;
}

.type-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  font-family: monospace;
}

.system-badge {
  margin-left: 6px;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
  font-weight: 500;
  font-family: sans-serif;
}

.direction-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  font-weight: 500;
}

.direction-badge.directed {
  background: rgba(88, 166, 255, 0.12);
  color: #58a6ff;
}

.direction-badge.undirected {
  background: rgba(63, 185, 80, 0.12);
  color: #3fb950;
}

.direction-badge.aggregation {
  background: rgba(210, 153, 34, 0.12);
  color: #d29922;
}

/* Form hints */
.field-hint {
  font-size: 11px;
  color: var(--tf-text-muted);
  margin-top: 4px;
  line-height: 1.4;
}

/* Delete dialog */
.delete-content {
  padding: 4px 0;
}

.delete-warning {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px;
  border-radius: 6px;
  background: var(--tf-bg-elevated);
  margin-bottom: 12px;
}

.warning-icon {
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 1px;
}

.warning-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 6px 0;
}

.warning-loading {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.warning-impact {
  font-size: 12px;
  color: var(--tf-warning, #d29922);
  margin: 0;
}

.warning-impact strong {
  font-weight: 600;
}

.warning-safe {
  font-size: 12px;
  color: var(--tf-success, #3fb950);
  margin: 0;
}

.delete-irreversible {
  font-size: 12px;
  color: var(--tf-text-muted);
  margin: 0;
}
</style>
