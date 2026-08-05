<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">组织管理</h2>
      <button class="btn-create" @click="openCreateDialog">+ 新建组织</button>
    </div>

    <!-- 组织列表 -->
    <div class="data-table">
      <div class="table-header">
        <div class="col" style="width:60px">ID</div>
        <div class="col" style="width:120px">编码</div>
        <div class="col" style="width:200px">名称</div>
        <div class="col" style="flex:1">描述</div>
        <div class="col" style="width:150px">创建时间</div>
        <div class="col" style="width:140px">操作</div>
      </div>
      <div class="table-body">
        <div v-for="org in organizations" :key="org.id" class="table-row">
          <div class="col" style="width:60px">{{ org.id }}</div>
          <div class="col" style="width:120px">
            <code class="code-tag">{{ org.code }}</code>
          </div>
          <div class="col" style="width:200px">
            <span class="org-name">{{ org.name }}</span>
          </div>
          <div class="col" style="flex:1">{{ org.description || '—' }}</div>
          <div class="col" style="width:150px">
            <span class="time-text">{{ formatDate(org.createdAt) }}</span>
          </div>
          <div class="col" style="width:140px">
            <button class="btn-sm" @click="editOrg(org)">编辑</button>
            <button class="btn-sm danger" @click="deleteOrg(org)">删除</button>
          </div>
        </div>
        <div v-if="organizations.length === 0" class="empty-row">暂无组织数据</div>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <div class="modal-overlay" v-if="showDialog" @click.self="showDialog = false">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>{{ editing ? '编辑组织' : '创建组织' }}</h3>
          <button class="btn-close" @click="showDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label class="form-label">名称 *</label>
            <input v-model="form.name" class="form-input" placeholder="组织名称" />
          </div>
          <div class="form-row" v-if="!editing">
            <label class="form-label">编码 *</label>
            <input v-model="form.code" class="form-input" placeholder="唯一编码（例如 TECH）" />
          </div>
          <div class="form-row">
            <label class="form-label">描述</label>
            <input v-model="form.description" class="form-input" placeholder="可选描述" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="showDialog = false">取消</button>
          <button class="btn-submit" @click="submitOrg" :disabled="!form.name">
            {{ editing ? '更新' : '创建' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Modal, Message } from '@arco-design/web-vue'
import { organizationApi } from '@/api'
import type { OrgVO } from '@/api/organization'

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

async function submitOrg() {
  if (editing.value) {
    await organizationApi.update(editing.value.id, { name: form.name, description: form.description })
  } else {
    await organizationApi.create({ name: form.name, code: form.code, description: form.description })
  }
  showDialog.value = false
  loadOrgs()
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
.btn-create { height: 32px; padding: 0 16px; background: var(--accent-blue); color: #fff; border: none; border-radius: var(--radius-md); font-size: var(--font-size-sm); font-weight: 500; cursor: pointer; }

.data-table { border: 1px solid var(--border-color); border-radius: 6px; overflow: hidden; }
.table-header { display: flex; padding: 8px 12px; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-color); font-size: var(--font-size-xs); color: var(--text-secondary); text-transform: uppercase; }
.table-row { display: flex; padding: 10px 12px; border-bottom: 1px solid var(--border-light); align-items: center; }
.table-row:hover { background: var(--bg-hover); }
.col { padding: 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--font-size-sm); }

.org-name { color: var(--text-bright); font-weight: 500; }
.code-tag { font-size: var(--font-size-xs); background: var(--bg-tertiary); padding: 2px 6px; border-radius: var(--radius-sm); color: var(--accent-blue); }
.time-text { font-size: var(--font-size-xs); color: var(--text-secondary); }

.btn-sm { height: 24px; padding: 0 8px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-sm); color: var(--text-primary); font-size: var(--font-size-xs); cursor: pointer; margin-right: 4px; }
.btn-sm:hover { background: var(--bg-hover); }
.btn-sm.danger { color: var(--accent-red); }
.empty-row { padding: 32px; text-align: center; color: var(--text-muted); }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-sm { width: 420px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 8px; }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; border-bottom: 1px solid var(--border-color); }
.modal-header h3 { font-size: 15px; color: var(--text-bright); font-weight: 500; }
.btn-close { background: none; border: none; color: var(--text-secondary); font-size: 16px; cursor: pointer; }
.modal-body { padding: 16px 18px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 18px; border-top: 1px solid var(--border-color); }

.form-row { margin-bottom: 14px; }
.form-label { display: block; font-size: var(--font-size-sm); color: var(--text-secondary); margin-bottom: 4px; }
.form-input { width: 100%; height: 32px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-md); outline: none; }
.form-input:focus { border-color: var(--accent-blue); }
.btn-cancel { height: 30px; padding: 0 14px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); color: var(--text-primary); font-size: var(--font-size-sm); cursor: pointer; }
.btn-submit { height: 30px; padding: 0 14px; background: var(--accent-blue); color: #fff; border: none; border-radius: var(--radius-md); font-size: var(--font-size-sm); font-weight: 500; cursor: pointer; }
.btn-submit:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
