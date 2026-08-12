<template>
  <AdminPageLayout title="关联类型" subtitle="管理工单关联类型。删除关联类型时，所有使用该类型的工单关联记录将被自动删除。">
    <template #actions>
      <a-button type="primary" @click="openCreateDialog">
        新建关联类型
      </a-button>
    </template>

    <AdminDataTable
      :show-toolbar="false"
      :data="linkTypes"
      :loading="loading"
      :total="linkTypes.length"
      :current="1"
      :page-size="linkTypes.length || 20"
      :columns="tableColumns"
      empty-title="暂无自定义关联类型"
      empty-description="系统内置了 7 种标准关联类型。你可以创建自定义类型以满足特殊业务需求。"
    />

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
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { Message } from '@arco-design/web-vue'
import { linkTypeApi } from '@/api'
import type { IssueLinkTypeVO } from '@/api/types'
import { useRequest } from '@/composables/useRequest'
import AdminPageLayout from '@/components/admin/AdminPageLayout.vue'
import { AdminDataTable } from '@/components/admin'
import type { ColumnDef } from '@/components/admin'

const saving = ref(false)
const deleting = ref(false)

const linkTypes = ref<IssueLinkTypeVO[]>([])
const showFormDialog = ref(false)
const showDeleteDialog = ref(false)
const editingId = ref<string | null>(null)
const deletingItem = ref<IssueLinkTypeVO | null>(null)
const deleteUsageCount = ref<number | null>(null)

const { loading, execute: loadLinkTypes } = useRequest(
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

// ===== 表格列配置 =====
const tableColumns: ColumnDef[] = [
  {
    type: 'render', title: '名称（内部标识）', key: 'name', width: 160,
    render: (record: any) => h('span', { style: 'display:flex;align-items:center;gap:6px' }, [
      h('code', { style: 'font-size:12px;font-family:monospace;font-weight:500;color:var(--tf-text-primary)' }, record.name),
      record.isSystem ? h('span', {
        style: 'font-size:10px;padding:2px 6px;border-radius:3px;background:var(--tf-accent-light);color:var(--tf-accent);font-weight:500'
      }, '系统') : null,
    ]),
  },
  { type: 'text', title: '正向显示名（outward）', key: 'outwardName', width: 180 },
  { type: 'text', title: '反向显示名（inward）', key: 'inwardName', width: 180 },
  {
    type: 'badge', title: '方向', key: 'direction', width: 120,
    labelMap: { DIRECTED: '有向', UNDIRECTED: '无向', AGGREGATION: '聚合' },
    colorMap: { DIRECTED: 'blue', UNDIRECTED: 'green', AGGREGATION: 'orange' },
  },
  {
    type: 'actions', width: 140,
    actions: (record: any) => [
      { label: '编辑', disabled: record.isSystem, onClick: (r) => openEditDialog(r) },
      { label: '删除', danger: true, disabled: record.isSystem, onClick: (r) => confirmDelete(r) },
    ],
  },
]

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
/* Form hints */
.field-hint {
  font-size: 11px;
  color: var(--tf-text-muted);
  margin-top: 4px;
  line-height: 1.4;
}

/* Delete dialog */
.delete-content { padding: 4px 0; }
.delete-warning { display: flex; gap: 12px; align-items: flex-start; padding: 12px; border-radius: 6px; background: var(--tf-bg-elevated); margin-bottom: 12px; }
.warning-icon { font-size: 18px; flex-shrink: 0; margin-top: 1px; }
.warning-title { font-size: 13px; font-weight: 500; color: var(--tf-text-primary); margin: 0 0 6px 0; }
.warning-loading { font-size: 12px; color: var(--tf-text-tertiary); margin: 0; }
.warning-impact { font-size: 12px; color: var(--tf-warning); margin: 0; }
.warning-impact strong { font-weight: 600; }
.warning-safe { font-size: 12px; color: var(--tf-success); margin: 0; }
.delete-irreversible { font-size: 12px; color: var(--tf-text-muted); margin: 0; }
</style>
