<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <div class="header-actions">
        <div class="header-filters">
          <input v-model="filters.keyword" class="filter-input" placeholder="搜索用户名/姓名/邮箱..." @input="debounceLoad" />
          <select v-model="filters.status" class="filter-select" @change="loadUsers">
            <option value="">全部状态</option>
            <option value="active">启用</option>
            <option value="disabled">禁用</option>
          </select>
        </div>
        <button class="btn-primary" @click="showCreateDialog = true">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" style="margin-right: 4px">
            <path d="M8 1a.75.75 0 01.75.75v5.5h5.5a.75.75 0 010 1.5h-5.5v5.5a.75.75 0 01-1.5 0v-5.5h-5.5a.75.75 0 010-1.5h5.5v-5.5A.75.75 0 018 1z"/>
          </svg>
          新建用户
        </button>
      </div>
    </div>

    <!-- 用户列表 -->
    <div class="data-table">
      <div class="table-header">
        <div class="col" style="width:60px">ID</div>
        <div class="col" style="width:140px">用户名</div>
        <div class="col" style="flex:1">显示名称</div>
        <div class="col" style="width:200px">邮箱</div>
        <div class="col" style="width:100px">状态</div>
        <div class="col" style="width:160px">最近登录</div>
        <div class="col" style="width:140px">操作</div>
      </div>
      <div class="table-body">
        <div v-for="user in users" :key="user.id" class="table-row">
          <div class="col" style="width:60px">{{ user.id }}</div>
          <div class="col" style="width:140px">
            <router-link :to="`/admin/users/${user.id}`" class="username-link">{{ user.username }}</router-link>
          </div>
          <div class="col" style="flex:1">{{ user.displayName }}</div>
          <div class="col" style="width:200px">{{ user.email || '—' }}</div>
          <div class="col" style="width:100px">
            <span class="status-tag" :class="user.status">{{ user.status === 'active' ? '启用' : '禁用' }}</span>
          </div>
          <div class="col" style="width:160px">
            <span class="time-text">{{ formatDate(user.lastLoginAt) }}</span>
          </div>
          <div class="col" style="width:140px">
            <button v-if="user.status === 'active'" class="btn-sm danger" @click="disableUser(user.id)">禁用</button>
            <button v-else class="btn-sm" @click="enableUser(user.id)">启用</button>
            <button class="btn-sm" @click="openRoleDialog(user)">角色</button>
          </div>
        </div>
        <div v-if="users.length === 0 && !loading" class="empty-state">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4-4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M22 21v-2a4 4 0 00-3-3.87"/>
            <path d="M16 3.13a4 4 0 010 7.75"/>
          </svg>
          <h3>暂无用户</h3>
          <p>点击"新建用户"按钮添加第一个用户</p>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="total > 0">
      <span>共 {{ total }} 条</span>
      <div class="page-btns">
        <button class="btn-page" :disabled="page <= 1" @click="page--; loadUsers()">‹</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn-page" :disabled="page >= totalPages" @click="page++; loadUsers()">›</button>
      </div>
    </div>

    <!-- 新建用户弹窗 -->
    <div class="modal-overlay" v-if="showCreateDialog" @click.self="showCreateDialog = false">
      <div class="modal-md">
        <div class="modal-header">
          <h3>新建用户</h3>
          <button class="btn-close" @click="closeCreateDialog">×</button>
        </div>
        <div class="modal-body">
          <form @submit.prevent="handleCreateUser">
            <div class="form-group">
              <label class="form-label">显示名称 <span class="required">*</span></label>
              <input
                v-model="createForm.displayName"
                class="form-input"
                placeholder="例如：张伟、John Smith"
                autocomplete="off"
              />
              <span class="form-hint">用户的全名，将显示在系统各处</span>
            </div>
            <div class="form-group">
              <label class="form-label">用户名 <span class="required">*</span></label>
              <input
                v-model="createForm.username"
                class="form-input"
                placeholder="字母开头，只含字母/数字/下划线/连字符"
                autocomplete="off"
              />
              <span class="form-hint">登录凭据，创建后不可修改</span>
            </div>
            <div class="form-group">
              <label class="form-label">邮箱 <span class="required">*</span></label>
              <input
                v-model="createForm.email"
                class="form-input"
                type="email"
                placeholder="user@company.com"
                autocomplete="off"
              />
            </div>
            <div class="form-group">
              <label class="form-label">临时密码 <span class="required">*</span></label>
              <input
                v-model="createForm.password"
                class="form-input"
                type="password"
                placeholder="至少 6 个字符，首次登录需修改"
                autocomplete="new-password"
              />
              <span class="form-hint">用户首次登录时将被要求修改密码</span>
            </div>
            <div v-if="createError" class="form-error">
              <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 1.5a6.5 6.5 0 100 13 6.5 6.5 0 000-13zM0 8a8 8 0 1116 0A8 8 0 010 8zm9-3a1 1 0 11-2 0 1 1 0 012 0zM7 7.75a.75.75 0 011.5 0v3.5a.75.75 0 01-1.5 0v-3.5z"/>
              </svg>
              {{ createError }}
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-secondary" @click="closeCreateDialog">取消</button>
              <button type="submit" class="btn-primary" :disabled="creating">
                {{ creating ? '创建中...' : '创建用户' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- 角色分配弹窗 -->
    <div class="modal-overlay" v-if="showRoleDialog" @click.self="showRoleDialog = false">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>管理角色 — {{ selectedUser?.displayName }}</h3>
          <button class="btn-close" @click="showRoleDialog = false">×</button>
        </div>
        <div class="modal-body">
          <div class="role-list">
            <div v-for="role in globalRoles" :key="role.id" class="role-item">
              <label class="role-check">
                <input type="checkbox" :checked="userRoleIds.includes(String(role.id))" @change="toggleRole(role.id)" />
                <span class="role-name">{{ role.name }}</span>
                <span class="role-code">{{ role.code }}</span>
              </label>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { userApi } from '@/api'
import request from '@/api/request'

const users = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const totalPages = computed(() => Math.ceil(total.value / pageSize))
const loading = ref(false)

const filters = reactive({ keyword: '', status: '' })

// 创建用户
const showCreateDialog = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({
  username: '',
  email: '',
  displayName: '',
  password: ''
})

// 角色管理
const showRoleDialog = ref(false)
const selectedUser = ref<any>(null)
const userRoleIds = ref<string[]>([])
const globalRoles = ref<any[]>([])

let debounceTimer: any = null
function debounceLoad() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => { page.value = 1; loadUsers() }, 300)
}

async function loadUsers() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize }
    if (filters.keyword) params.username = filters.keyword
    if (filters.status) params.status = filters.status
    const res = await userApi.list(params)
    users.value = res.data?.list || []
    total.value = res.data?.pagination?.total || 0
  } catch (e) { users.value = []; total.value = 0 }
  finally { loading.value = false }
}

async function disableUser(id: string) {
  if (!confirm('确定禁用该用户？禁用后用户将无法登录系统。')) return
  await userApi.disable(id)
  loadUsers()
}

async function enableUser(id: string) {
  await userApi.enable(id)
  loadUsers()
}

function closeCreateDialog() {
  showCreateDialog.value = false
  createError.value = ''
  createForm.username = ''
  createForm.email = ''
  createForm.displayName = ''
  createForm.password = ''
}

async function handleCreateUser() {
  createError.value = ''

  // 前端校验
  if (!createForm.displayName.trim()) {
    createError.value = '请输入显示名称'
    return
  }
  if (!createForm.username.trim()) {
    createError.value = '请输入用户名'
    return
  }
  if (!/^[a-zA-Z][a-zA-Z0-9_-]*$/.test(createForm.username)) {
    createError.value = '用户名必须以字母开头，只能包含字母、数字、下划线和连字符'
    return
  }
  if (createForm.username.length < 3) {
    createError.value = '用户名长度至少 3 个字符'
    return
  }
  if (!createForm.email.trim()) {
    createError.value = '请输入邮箱'
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(createForm.email)) {
    createError.value = '邮箱格式无效'
    return
  }
  if (!createForm.password) {
    createError.value = '请输入临时密码'
    return
  }
  if (createForm.password.length < 6) {
    createError.value = '密码长度至少 6 个字符'
    return
  }

  creating.value = true
  try {
    await userApi.create({
      username: createForm.username.trim(),
      email: createForm.email.trim(),
      displayName: createForm.displayName.trim(),
      password: createForm.password
    })
    closeCreateDialog()
    loadUsers()
  } catch (e: any) {
    createError.value = e.response?.data?.message || '创建用户失败，请重试'
  } finally {
    creating.value = false
  }
}

async function openRoleDialog(user: any) {
  selectedUser.value = user
  showRoleDialog.value = true
  try {
    const res = await userApi.getById(user.id)
    userRoleIds.value = res.data?.roleIds || []
  } catch (e) { userRoleIds.value = [] }
}

async function toggleRole(roleId: number) {
  const userId = selectedUser.value?.id
  if (!userId) return
  const roleIdStr = String(roleId)
  if (userRoleIds.value.includes(roleIdStr)) {
    await userApi.removeRole(userId, roleIdStr)
    userRoleIds.value = userRoleIds.value.filter(id => id !== roleIdStr)
  } else {
    await userApi.assignRole(userId, roleIdStr)
    userRoleIds.value.push(roleIdStr)
  }
}

async function loadGlobalRoles() {
  try {
    const res: any = await request.get('/roles', { params: { roleType: 'global', pageSize: 50 } })
    globalRoles.value = res.data?.list || []
  } catch (e) { globalRoles.value = [] }
}

function formatDate(dt: string) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('zh-CN')
}

onMounted(() => {
  loadUsers()
  loadGlobalRoles()
})
</script>

<style scoped>
.admin-page { padding: 24px; height: 100%; overflow-y: auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.page-title { font-size: 18px; font-weight: 600; color: var(--text-bright); }
.header-actions { display: flex; align-items: center; gap: 12px; }
.header-filters { display: flex; gap: 8px; }
.filter-input { height: 32px; width: 260px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-sm); outline: none; }
.filter-input:focus { border-color: var(--accent-blue); }
.filter-select { height: 32px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-sm); }

.btn-primary { display: flex; align-items: center; height: 32px; padding: 0 14px; background: var(--accent-blue); border: none; border-radius: var(--radius-md); color: #fff; font-size: var(--font-size-sm); font-weight: 500; cursor: pointer; white-space: nowrap; transition: background 150ms; }
.btn-primary:hover { background: var(--accent-blue-hover, #4a9af5); }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-secondary { height: 32px; padding: 0 14px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); color: var(--text-primary); font-size: var(--font-size-sm); cursor: pointer; transition: background 150ms; }
.btn-secondary:hover { background: var(--bg-hover); }

.data-table { border: 1px solid var(--border-color); border-radius: 6px; overflow: hidden; }
.table-header { display: flex; padding: 8px 12px; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-color); font-size: var(--font-size-xs); color: var(--text-secondary); text-transform: uppercase; }
.table-row { display: flex; padding: 10px 12px; border-bottom: 1px solid var(--border-light); align-items: center; }
.table-row:hover { background: var(--bg-hover); }
.table-row:last-child { border-bottom: none; }
.col { padding: 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--font-size-sm); }

.username-link { color: var(--accent-blue); font-weight: 500; text-decoration: none; }
.username-link:hover { text-decoration: underline; }
.status-tag { font-size: var(--font-size-xs); padding: 2px 8px; border-radius: var(--radius-sm); }
.status-tag.active { background: rgba(76,175,80,0.15); color: var(--accent-green); }
.status-tag.disabled { background: rgba(244,67,54,0.15); color: var(--accent-red); }
.time-text { font-size: var(--font-size-xs); color: var(--text-secondary); }

.btn-sm { height: 24px; padding: 0 8px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-sm); color: var(--text-primary); font-size: var(--font-size-xs); cursor: pointer; margin-right: 4px; }
.btn-sm:hover { background: var(--bg-hover); }
.btn-sm.danger { color: var(--accent-red); border-color: var(--accent-red); }

.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; color: var(--text-muted); }
.empty-state svg { opacity: 0.4; margin-bottom: 12px; }
.empty-state h3 { font-size: 14px; font-weight: 500; color: var(--text-secondary); margin: 0 0 6px; }
.empty-state p { font-size: 13px; color: var(--text-muted); margin: 0; }

.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; font-size: var(--font-size-sm); color: var(--text-secondary); }
.page-btns { display: flex; align-items: center; gap: 8px; }
.btn-page { width: 28px; height: 28px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); color: var(--text-primary); cursor: pointer; }
.btn-page:disabled { opacity: 0.3; cursor: not-allowed; }

/* Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-sm { width: 420px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 8px; }
.modal-md { width: 480px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 8px; }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; border-bottom: 1px solid var(--border-color); }
.modal-header h3 { font-size: 15px; color: var(--text-bright); font-weight: 500; }
.btn-close { background: none; border: none; color: var(--text-secondary); font-size: 18px; cursor: pointer; padding: 0 4px; }
.btn-close:hover { color: var(--text-primary); }
.modal-body { padding: 16px 18px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--border-light); }

/* Form */
.form-group { margin-bottom: 16px; }
.form-label { display: block; font-size: var(--font-size-xs); font-weight: 500; color: var(--text-secondary); margin-bottom: 6px; }
.required { color: var(--accent-red); }
.form-input { width: 100%; height: 36px; background: var(--bg-primary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-sm); outline: none; box-sizing: border-box; transition: border-color 150ms; }
.form-input:focus { border-color: var(--accent-blue); }
.form-hint { display: block; font-size: 11px; color: var(--text-muted); margin-top: 4px; }
.form-error { display: flex; align-items: center; gap: 6px; padding: 8px 12px; background: rgba(244,67,54,0.1); border: 1px solid rgba(244,67,54,0.3); border-radius: var(--radius-md); color: var(--accent-red); font-size: var(--font-size-xs); margin-top: 12px; }

.role-item { margin-bottom: 8px; }
.role-check { display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: var(--font-size-sm); color: var(--text-primary); }
.role-check input { accent-color: var(--accent-blue); }
.role-code { font-size: var(--font-size-xs); color: var(--text-muted); margin-left: auto; }
</style>
