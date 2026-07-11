<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <div class="header-filters">
        <input v-model="filters.keyword" class="filter-input" placeholder="搜索用户名/姓名/邮箱..." @input="debounceLoad" />
        <select v-model="filters.status" class="filter-select" @change="loadUsers">
          <option value="">全部状态</option>
          <option value="active">启用</option>
          <option value="disabled">禁用</option>
        </select>
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
            <span class="username">{{ user.username }}</span>
          </div>
          <div class="col" style="flex:1">{{ user.displayName }}</div>
          <div class="col" style="width:200px">{{ user.email || '—' }}</div>
          <div class="col" style="width:100px">
            <span class="status-tag" :class="user.status">{{ user.status }}</span>
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
        <div v-if="users.length === 0" class="empty-row">暂无用户数据</div>
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

const filters = reactive({ keyword: '', status: '' })

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
  try {
    const params: any = { page: page.value, pageSize }
    if (filters.keyword) params.username = filters.keyword
    if (filters.status) params.status = filters.status
    const res = await userApi.list(params)
    users.value = res.data?.list || []
    total.value = res.data?.pagination?.total || 0
  } catch (e) { users.value = []; total.value = 0 }
}

async function disableUser(id: string) {
  if (!confirm('确定禁用该用户？')) return
  await userApi.disable(id)
  loadUsers()
}

async function enableUser(id: string) {
  await userApi.enable(id)
  loadUsers()
}

async function openRoleDialog(user: any) {
  selectedUser.value = user
  showRoleDialog.value = true
  // 加载用户的全局角色
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
.header-filters { display: flex; gap: 8px; }
.filter-input { height: 32px; width: 260px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-sm); outline: none; }
.filter-input:focus { border-color: var(--accent-blue); }
.filter-select { height: 32px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-sm); }

.data-table { border: 1px solid var(--border-color); border-radius: 6px; overflow: hidden; }
.table-header { display: flex; padding: 8px 12px; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-color); font-size: var(--font-size-xs); color: var(--text-secondary); text-transform: uppercase; }
.table-row { display: flex; padding: 10px 12px; border-bottom: 1px solid var(--border-light); align-items: center; }
.table-row:hover { background: var(--bg-hover); }
.col { padding: 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--font-size-sm); }

.username { color: var(--accent-blue); font-weight: 500; }
.status-tag { font-size: var(--font-size-xs); padding: 2px 8px; border-radius: var(--radius-sm); }
.status-tag.active { background: rgba(76,175,80,0.15); color: var(--accent-green); }
.status-tag.disabled { background: rgba(244,67,54,0.15); color: var(--accent-red); }
.time-text { font-size: var(--font-size-xs); color: var(--text-secondary); }

.btn-sm { height: 24px; padding: 0 8px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-sm); color: var(--text-primary); font-size: var(--font-size-xs); cursor: pointer; margin-right: 4px; }
.btn-sm:hover { background: var(--bg-hover); }
.btn-sm.danger { color: var(--accent-red); border-color: var(--accent-red); }

.empty-row { padding: 32px; text-align: center; color: var(--text-muted); }

.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; font-size: var(--font-size-sm); color: var(--text-secondary); }
.page-btns { display: flex; align-items: center; gap: 8px; }
.btn-page { width: 28px; height: 28px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); color: var(--text-primary); cursor: pointer; }
.btn-page:disabled { opacity: 0.3; cursor: not-allowed; }

/* Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-sm { width: 420px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 8px; }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; border-bottom: 1px solid var(--border-color); }
.modal-header h3 { font-size: 15px; color: var(--text-bright); font-weight: 500; }
.btn-close { background: none; border: none; color: var(--text-secondary); font-size: 16px; cursor: pointer; }
.modal-body { padding: 16px 18px; max-height: 300px; overflow-y: auto; }

.role-item { margin-bottom: 8px; }
.role-check { display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: var(--font-size-sm); color: var(--text-primary); }
.role-check input { accent-color: var(--accent-blue); }
.role-code { font-size: var(--font-size-xs); color: var(--text-muted); margin-left: auto; }
</style>
