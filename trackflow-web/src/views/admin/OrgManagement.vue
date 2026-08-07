<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">组织管理</h2>
      <a-button type="primary" size="small" @click="openCreateDialog">
        <template #icon><icon-plus /></template>
        新建组织
      </a-button>
    </div>

    <!-- 组织列表 -->
    <a-table
      :columns="columns"
      :data="organizations"
      :pagination="false"
      :bordered="false"
      row-key="id"
      size="medium"
    >
      <template #code="{ record }">
        <code class="code-tag">{{ record.code }}</code>
      </template>
      <template #name="{ record }">
        <span class="org-name">{{ record.name }}</span>
      </template>
      <template #description="{ record }">
        {{ record.description || '—' }}
      </template>
      <template #createdAt="{ record }">
        <span class="time-text">{{ formatDate(record.createdAt) }}</span>
      </template>
      <template #actions="{ record }">
        <a-button type="text" size="mini" @click="editOrg(record)">编辑</a-button>
        <a-button type="text" size="mini" status="danger" @click="deleteOrg(record)">删除</a-button>
      </template>
      <template #empty>
        <a-empty description="暂无组织数据" />
      </template>
    </a-table>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="showDialog"
      :title="editing ? '编辑组织' : '创建组织'"
      :width="420"
      @before-ok="submitOrg"
      @cancel="showDialog = false"
      :ok-text="editing ? '更新' : '创建'"
      :ok-button-props="{ disabled: !form.name }"
    >
      <a-form :model="form" layout="vertical" size="small">
        <a-form-item label="名称" required>
          <a-input v-model="form.name" placeholder="组织名称" />
        </a-form-item>
        <a-form-item v-if="!editing" label="编码" required>
          <a-input v-model="form.code" placeholder="唯一编码（例如 TECH）" />
        </a-form-item>
        <a-form-item label="描述">
          <a-input v-model="form.description" placeholder="可选描述" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Modal, Message } from '@arco-design/web-vue'
import type { TableColumnData } from '@arco-design/web-vue'
import { organizationApi } from '@/api'
import type { OrgVO } from '@/api/organization'

const columns: TableColumnData[] = [
  { title: 'ID', dataIndex: 'id', width: 60 },
  { title: '编码', slotName: 'code', width: 120 },
  { title: '名称', slotName: 'name', width: 200 },
  { title: '描述', slotName: 'description' },
  { title: '创建时间', slotName: 'createdAt', width: 150 },
  { title: '操作', slotName: 'actions', width: 140 },
]

const organizations = ref<OrgVO[]>([])
const showDialog = ref(false)
const editing = ref<OrgVO | null>(null)
const form = reactive({ name: '', code: '', description: '' })

async function loadOrgs() {
  try {
    const res = await organizationApi.list({ pageSize: 100 })
    organizations.value = res.data?.list || []
  } catch (e) { organizations.value = [] }
}

function openCreateDialog() {
  editing.value = null
  form.name = ''; form.code = ''; form.description = ''
  showDialog.value = true
}

function editOrg(org: OrgVO) {
  editing.value = org
  form.name = org.name; form.code = org.code; form.description = org.description || ''
  showDialog.value = true
}

async function submitOrg(done?: (closed: boolean) => void) {
  try {
    if (editing.value) {
      await organizationApi.update(editing.value.id, { name: form.name, description: form.description })
    } else {
      await organizationApi.create({ name: form.name, code: form.code, description: form.description })
    }
    Message.success(editing.value ? '组织已更新' : '组织已创建')
    if (done) done(true)
    loadOrgs()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
    if (done) done(false)
  }
}

async function deleteOrg(org: OrgVO) {
  Modal.confirm({
    title: '确认删除组织',
    content: `确定要删除组织"${org.name}"吗？此操作不可撤销。`,
    okText: '删除组织',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    async onOk() {
      try {
        await organizationApi.delete(org.id)
        Message.success('组织已删除')
        loadOrgs()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

function formatDate(dt: string) {
  if (!dt) return '—'
  return new Date(dt).toLocaleDateString('zh-CN')
}

onMounted(loadOrgs)
</script>

<style scoped>
.admin-page { padding: 24px; height: 100%; overflow-y: auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.page-title { font-size: 18px; font-weight: 600; color: var(--text-bright); }

.org-name { color: var(--text-bright); font-weight: 500; }
.code-tag { font-size: var(--font-size-xs); background: var(--bg-tertiary); padding: 2px 6px; border-radius: var(--radius-sm); color: var(--accent-blue); }
.time-text { font-size: var(--font-size-xs); color: var(--text-secondary); }
</style>
