<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">角色管理</h2>
      <button class="btn-create" @click="openCreateDialog">+ 新建角色</button>
    </div>

    <!-- 角色列表 -->
    <div class="data-table">
      <div class="table-header">
        <div class="col" style="width:60px">ID</div>
        <div class="col" style="width:150px">名称</div>
        <div class="col" style="width:120px">编码</div>
        <div class="col" style="width:100px">类型</div>
        <div class="col" style="flex:1">描述</div>
        <div class="col" style="width:80px">内置</div>
        <div class="col" style="width:220px">操作</div>
      </div>
      <div class="table-body">
        <div v-for="role in roles" :key="role.id" class="table-row">
          <div class="col" style="width:60px">{{ role.id }}</div>
          <div class="col" style="width:150px">
            <span class="role-name">{{ role.name }}</span>
          </div>
          <div class="col" style="width:120px">
            <code class="code-tag">{{ role.code }}</code>
          </div>
          <div class="col" style="width:100px">
            <span class="type-badge" :class="role.roleType">{{ role.roleType }}</span>
          </div>
          <div class="col" style="flex:1">{{ role.description || '—' }}</div>
          <div class="col" style="width:80px">
            <span v-if="role.builtin" class="builtin-tag">是</span>
          </div>
          <div class="col" style="width:220px">
            <button class="btn-sm" @click="openPermDialog(role)">权限</button>
            <button class="btn-sm" @click="openCloneDialog(role)">克隆</button>
            <button class="btn-sm" @click="editRole(role)" :disabled="role.builtin">编辑</button>
            <button class="btn-sm danger" @click="deleteRole(role.id)" :disabled="role.builtin">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑角色弹窗 -->
    <div class="modal-overlay" v-if="showCreateDialog" @click.self="showCreateDialog = false">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>{{ editingRole ? '编辑角色' : '创建角色' }}</h3>
          <button class="btn-close" @click="showCreateDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label class="form-label">名称 *</label>
            <input v-model="roleForm.name" class="form-input" />
          </div>
          <div class="form-row" v-if="!editingRole">
            <label class="form-label">编码 *</label>
            <input v-model="roleForm.code" class="form-input" placeholder="例如 qa_lead" />
          </div>
          <div class="form-row" v-if="!editingRole">
            <label class="form-label">类型 *</label>
            <select v-model="roleForm.roleType" class="form-input">
              <option value="project">项目级</option>
              <option value="global">全局</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label">描述</label>
            <input v-model="roleForm.description" class="form-input" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="showCreateDialog = false">取消</button>
          <button class="btn-submit" @click="submitRole">{{ editingRole ? '更新' : '创建' }}</button>
        </div>
      </div>
    </div>

    <!-- 权限分配弹窗 -->
    <div class="modal-overlay" v-if="showPermDialog" @click.self="showPermDialog = false">
      <div class="modal-lg">
        <div class="modal-header">
          <h3>权限配置 — {{ permRole?.name }}</h3>
          <button class="btn-close" @click="showPermDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <div v-for="group in permissionGroups" :key="group.category" class="perm-group">
            <h4 class="perm-category">{{ CATEGORY_LABELS[group.category] || group.category }}</h4>
            <div class="perm-list">
              <label v-for="perm in group.permissions" :key="perm.code" class="perm-item">
                <input type="checkbox" :checked="rolePerms.includes(perm.code)" @change="togglePerm(perm.code)" />
                <span class="perm-name">{{ perm.name }}</span>
                <span class="perm-code">{{ perm.code }}</span>
              </label>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="showPermDialog = false">取消</button>
          <button class="btn-submit" @click="savePermissions">保存权限</button>
        </div>
      </div>
    </div>

    <!-- 克隆角色弹窗 -->
    <div class="modal-overlay" v-if="showCloneDialog" @click.self="showCloneDialog = false">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>克隆角色 — {{ cloneSource?.name }}</h3>
          <button class="btn-close" @click="showCloneDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="clone-hint">将创建一个新角色，自动继承原角色的全部权限配置。</div>
          <div class="form-row">
            <label class="form-label">新角色名称 *</label>
            <input v-model="cloneForm.name" class="form-input" />
          </div>
          <div class="form-row">
            <label class="form-label">新角色编码 *</label>
            <input v-model="cloneForm.code" class="form-input" placeholder="英文小写+下划线" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="showCloneDialog = false">取消</button>
          <button class="btn-submit" @click="submitClone" :disabled="cloneLoading">{{ cloneLoading ? '克隆中...' : '克隆角色' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import request from '@/api/request'

interface PermissionItem {
  code: string
  name: string
  description: string
  scope: string
}

interface PermissionGroup {
  category: string
  permissions: PermissionItem[]
}

const CATEGORY_LABELS: Record<string, string> = {
  system: '系统权限',
  project: '项目权限',
  issue: '工单权限',
  sprint: '迭代权限',
  query: '查询权限',
  report: '报表权限',
  integration: '集成权限',
}

const roles = ref<any[]>([])
const showCreateDialog = ref(false)
const editingRole = ref<any>(null)
const roleForm = reactive({ name: '', code: '', roleType: 'project', description: '' })

const showPermDialog = ref(false)
const permRole = ref<any>(null)
const rolePerms = ref<string[]>([])
const permissionGroups = ref<PermissionGroup[]>([])

const showCloneDialog = ref(false)
const cloneSource = ref<any>(null)
const cloneForm = reactive({ name: '', code: '' })
const cloneLoading = ref(false)

async function loadRoles() {
  try {
    const res: any = await request.get('/roles', { params: { pageSize: 100 } })
    roles.value = res.data?.list || []
  } catch (e) { roles.value = [] }
}

async function loadPermissionDefinitions() {
  try {
    const res: any = await request.get('/roles/permission-definitions')
    permissionGroups.value = res.data || []
  } catch (e) { permissionGroups.value = [] }
}

function openCreateDialog() {
  editingRole.value = null
  roleForm.name = ''; roleForm.code = ''; roleForm.roleType = 'project'; roleForm.description = ''
  showCreateDialog.value = true
}

function editRole(role: any) {
  editingRole.value = role
  roleForm.name = role.name; roleForm.code = role.code; roleForm.roleType = role.roleType; roleForm.description = role.description || ''
  showCreateDialog.value = true
}

async function submitRole() {
  if (editingRole.value) {
    await request.put(`/roles/${editingRole.value.id}`, { name: roleForm.name, description: roleForm.description })
  } else {
    await request.post('/roles', roleForm)
  }
  showCreateDialog.value = false
  loadRoles()
}

async function deleteRole(id: number) {
  if (!confirm('确定删除该角色？')) return
  try {
    await request.delete(`/roles/${id}`)
    loadRoles()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

function openCloneDialog(role: any) {
  cloneSource.value = role
  cloneForm.name = `${role.name} (副本)`
  cloneForm.code = `${role.code}_copy`
  showCloneDialog.value = true
}

async function submitClone() {
  if (!cloneForm.name.trim() || !cloneForm.code.trim()) {
    Message.warning('名称和编码不能为空')
    return
  }
  cloneLoading.value = true
  try {
    await request.post(`/roles/${cloneSource.value.id}/clone`, {
      name: cloneForm.name.trim(),
      code: cloneForm.code.trim()
    })
    showCloneDialog.value = false
    Message.success('角色克隆成功')
    loadRoles()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '克隆失败')
  } finally {
    cloneLoading.value = false
  }
}

async function openPermDialog(role: any) {
  permRole.value = role
  showPermDialog.value = true
  try {
    const res: any = await request.get(`/roles/${role.id}/permissions`)
    rolePerms.value = res.data || []
  } catch (e) { rolePerms.value = [] }
}

function togglePerm(perm: string) {
  if (rolePerms.value.includes(perm)) {
    rolePerms.value = rolePerms.value.filter(p => p !== perm)
  } else {
    rolePerms.value.push(perm)
  }
}

async function savePermissions() {
  await request.put(`/roles/${permRole.value.id}/permissions`, rolePerms.value)
  showPermDialog.value = false
  Message.success('权限保存成功')
}

onMounted(() => {
  loadRoles()
  loadPermissionDefinitions()
})
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

.role-name { color: var(--text-bright); font-weight: 500; }
.code-tag { font-size: var(--font-size-xs); background: var(--bg-tertiary); padding: 2px 6px; border-radius: var(--radius-sm); color: var(--accent-blue); }
.type-badge { font-size: var(--font-size-xs); padding: 2px 6px; border-radius: var(--radius-sm); }
.type-badge.global { background: rgba(156,39,176,0.15); color: var(--accent-purple); }
.type-badge.project { background: rgba(33,150,243,0.15); color: var(--accent-blue); }
.builtin-tag { font-size: var(--font-size-xs); color: var(--accent-orange); }

.btn-sm { height: 24px; padding: 0 8px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-sm); color: var(--text-primary); font-size: var(--font-size-xs); cursor: pointer; margin-right: 4px; }
.btn-sm:hover { background: var(--bg-hover); }
.btn-sm.danger { color: var(--accent-red); }
.btn-sm:disabled { opacity: 0.3; cursor: not-allowed; }

/* Modals */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-sm { width: 420px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 8px; }
.modal-lg { width: 600px; max-height: 80vh; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 8px; display: flex; flex-direction: column; }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; border-bottom: 1px solid var(--border-color); }
.modal-header h3 { font-size: 15px; color: var(--text-bright); font-weight: 500; }
.btn-close { background: none; border: none; color: var(--text-secondary); font-size: 16px; cursor: pointer; }
.modal-body { padding: 16px 18px; overflow-y: auto; flex: 1; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 18px; border-top: 1px solid var(--border-color); }

.form-row { margin-bottom: 14px; }
.form-label { display: block; font-size: var(--font-size-sm); color: var(--text-secondary); margin-bottom: 4px; }
.form-input { width: 100%; height: 32px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-md); outline: none; }
.form-input:focus { border-color: var(--accent-blue); }

.btn-cancel { height: 30px; padding: 0 14px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); color: var(--text-primary); font-size: var(--font-size-sm); cursor: pointer; }
.btn-submit { height: 30px; padding: 0 14px; background: var(--accent-blue); color: #fff; border: none; border-radius: var(--radius-md); font-size: var(--font-size-sm); font-weight: 500; cursor: pointer; }

/* Permission groups */
.perm-group { margin-bottom: 16px; }

/* Clone dialog */
.clone-hint { font-size: var(--font-size-sm); color: var(--text-secondary); margin-bottom: 14px; line-height: 1.5; }
.perm-category { font-size: var(--font-size-sm); color: var(--accent-blue); text-transform: capitalize; margin-bottom: 8px; font-weight: 500; }
.perm-list { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.perm-item { display: flex; align-items: center; gap: 6px; font-size: var(--font-size-sm); color: var(--text-primary); cursor: pointer; }
.perm-item input { accent-color: var(--accent-blue); }
.perm-name { color: var(--text-primary); }
.perm-code { font-size: var(--font-size-xs); color: var(--text-tertiary); margin-left: 2px; }
</style>
