<template>
  <AdminPageLayout title="组织管理">
    <template #actions>
      <a-button type="primary" size="small" @click="openCreateDialog">
        <template #icon><icon-plus /></template>
        新建组织
      </a-button>
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
      @row-click="navigateToOrg"
    >
      <template #columns>
        <a-table-column title="编码" :width="120" data-index="code">
          <template #cell="{ record }">
            <code class="code-tag">{{ record.code }}</code>
          </template>
        </a-table-column>
        <a-table-column title="名称" :width="200" data-index="name">
          <template #cell="{ record }">
            <span class="org-name-link">{{ record.name }}</span>
          </template>
        </a-table-column>
        <a-table-column title="项目数" :width="80" data-index="projectCount" align="center">
          <template #cell="{ record }">
            <span class="project-count">{{ record.projectCount ?? 0 }}</span>
          </template>
        </a-table-column>
        <a-table-column title="描述" data-index="description" ellipsis>
          <template #cell="{ record }">
            <span class="description-text">{{ record.description || '—' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="创建时间" :width="150" data-index="createdAt">
          <template #cell="{ record }">
            <span class="time-text">{{ formatDate(record.createdAt) }}</span>
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="140" align="right">
          <template #cell="{ record }">
            <a-button type="text" size="mini" @click.stop="editOrg(record)">编辑</a-button>
            <a-button type="text" size="mini" status="danger" @click.stop="deleteOrg(record)">删除</a-button>
          </template>
        </a-table-column>
      </template>
    </AdminDataTable>

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
import { formatDate } from '@/utils/date'
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { organizationApi } from '@/api'
import type { OrgVO, OrgProjectVO } from '@/api/organization'
import { AdminPageLayout, AdminDataTable } from '@/components/admin'

const router = useRouter()

const organizations = ref<OrgVO[]>([])
const showDialog = ref(false)
const editing = ref<OrgVO | null>(null)
const form = reactive({ name: '', code: '', description: '', projectIds: [] as string[] })
const unassignedProjects = ref<OrgProjectVO[]>([])

const unassignedProjectOptions = computed(() =>
  unassignedProjects.value.map(p => ({ value: p.id, label: `${p.key} - ${p.name}` }))
)

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



onMounted(loadOrgs)
</script>

<style scoped>
.org-name-link { color: var(--accent-blue); font-weight: 500; cursor: pointer; transition: color 150ms; }
.org-name-link:hover { text-decoration: underline; }
.code-tag { font-size: var(--font-size-xs); background: var(--bg-tertiary); padding: 2px 6px; border-radius: var(--radius-sm); color: var(--accent-blue); }
.project-count { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.description-text { color: var(--text-secondary); }
.time-text { font-size: var(--font-size-xs); color: var(--text-secondary); }
.empty-state-hint { color: var(--text-tertiary); margin-bottom: 12px; font-size: 13px; }
</style>
