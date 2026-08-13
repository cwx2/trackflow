<template>
  <AdminPageLayout title="组织管理" subtitle="管理组织结构，对项目和团队进行分组">
    <template #actions>
      <a-button type="primary" size="small" aria-label="新建组织" @click="openCreateDialog">
        <template #icon><icon-plus /></template>
        新建组织
      </a-button>
    </template>

    <!-- 统计卡片 -->
    <template #stats>
      <AdminStatsBar :stats="statsItems" :loading="statsLoading" />
    </template>

    <!-- 组织列表 -->
    <AdminDataTable
      :show-toolbar="false"
      :data="organizations"
      :total="organizations.length"
      :current="1"
      :page-size="organizations.length || 20"
      empty-title="暂无组织"
      empty-description="组织用于对项目和团队进行分组管理"
      :columns="tableColumns"
      @row-click="navigateToOrg"
    />

  <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="showDialog"
      :title="editing ? '编辑组织' : '创建组织'"
      :width="480"
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
        <a-form-item v-if="!editing" label="初始项目">
          <a-select
            v-model="form.projectIds"
            :options="unassignedProjectOptions"
            placeholder="选择要加入此组织的项目（可选）"
            multiple
            allow-clear
            :max-tag-count="3"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, h } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { organizationApi } from '@/api'
import type { OrgVO, OrgProjectVO } from '@/api/organization'
import { AdminPageLayout, AdminDataTable, AdminStatsBar } from '@/components/admin'
import type { ColumnDef } from '@/components/admin'
import type { StatItem } from '@/components/admin'
import { IconHome, IconFile, IconUserGroup } from '@arco-design/web-vue/es/icon'

const router = useRouter()

// ===== 统计卡片 =====
const statsLoading = ref(false)
const statsItems = ref<StatItem[]>([
  { label: '组织总数', value: '—', icon: IconHome, color: 'blue' },
  { label: '关联项目', value: '—', icon: IconFile, color: 'green' },
  { label: '有项目的组织', value: '—', icon: IconUserGroup, color: 'purple' },
])

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await organizationApi.stats()
    if (res.code === 0 && res.data) {
      const d = res.data
      statsItems.value = [
        { label: '组织总数', value: d.total, icon: IconHome, color: 'blue' },
        { label: '关联项目', value: d.totalProjects, icon: IconFile, color: 'green' },
        { label: '有项目的组织', value: d.orgsWithProjects, icon: IconUserGroup, color: 'purple' },
      ]
    }
  } finally {
    statsLoading.value = false
  }
}

const organizations = ref<OrgVO[]>([])
const showDialog = ref(false)
const editing = ref<OrgVO | null>(null)
const form = reactive({ name: '', code: '', description: '', projectIds: [] as string[] })
const unassignedProjects = ref<OrgProjectVO[]>([])

const unassignedProjectOptions = computed(() =>
  unassignedProjects.value.map(p => ({ value: p.id, label: `${p.key} - ${p.name}` }))
)

// ===== 表格列配置 =====
const tableColumns: ColumnDef[] = [
  { type: 'code', title: '编码', key: 'code', width: 120 },
  {
    type: 'render',
    title: '名称',
    key: 'name',
    width: 200,
    render: (record: any) => h('span', {
      style: 'color:var(--tf-accent);font-weight:500;cursor:pointer'
    }, record.name),
  },
  { type: 'count', title: '项目数', key: 'projectCount', width: 80, align: 'center' },
  { type: 'text', title: '描述', key: 'description', ellipsis: true },
  { type: 'date', title: '创建时间', key: 'createdAt', width: 150, format: 'datetime' },
  {
    type: 'actions',
    width: 140,
    actions: () => [
      { label: '编辑', onClick: (r) => editOrg(r) },
      { label: '删除', danger: true, onClick: (r) => deleteOrg(r) },
    ],
  },
]

async function loadOrgs() {
  try {
    const res = await organizationApi.list({ pageSize: 100 })
    organizations.value = res.data?.list || []
  } catch (e) { organizations.value = [] }
}

async function loadUnassignedProjects() {
  try {
    const res = await organizationApi.getUnassignedProjects()
    unassignedProjects.value = res.data || []
  } catch (e) { unassignedProjects.value = [] }
}

function openCreateDialog() {
  editing.value = null
  form.name = ''; form.code = ''; form.description = ''; form.projectIds = []
  loadUnassignedProjects()
  showDialog.value = true
}

function navigateToOrg(record: any) {
  router.push({ name: 'OrgDetail', params: { id: record.id } })
}

function editOrg(org: OrgVO) {
  editing.value = org
  form.name = org.name; form.code = org.code; form.description = org.description || ''; form.projectIds = []
  showDialog.value = true
}

async function submitOrg(done?: (closed: boolean) => void) {
  try {
    if (editing.value) {
      await organizationApi.update(editing.value.id, { name: form.name, description: form.description })
    } else {
      const projectIds = form.projectIds.length > 0 ? form.projectIds : undefined
      await organizationApi.create({ name: form.name, code: form.code, description: form.description, projectIds })
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
  const { confirmDelete } = useConfirmDelete()
  confirmDelete({
    itemName: `组织「${org.name}」`,
    confirmText: '删除组织',
    onConfirm: async () => {
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



onMounted(() => {
  loadStats()
  loadOrgs()
})
</script>

<style scoped>
/* 无页面专属样式 —— 列样式统一由 AdminDataTable 的 adt-* 类处理 */
</style>
