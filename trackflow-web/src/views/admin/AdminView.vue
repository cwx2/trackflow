<template>
  <div class="admin-page">
    <!-- 权限加载中 -->
    <div v-if="loading" class="admin-loading">
      <span class="loading-text">加载中...</span>
    </div>

    <!-- 无权限 -->
    <div v-else-if="!hasAnyAdminPermission" class="admin-forbidden">
      <div class="forbidden-icon">🔒</div>
      <h2 class="forbidden-title">无访问权限</h2>
      <p class="forbidden-desc">你没有系统管理权限，无法访问此页面</p>
      <router-link to="/" class="forbidden-btn">返回首页</router-link>
    </div>

    <!-- 正常内容 -->
    <template v-else>
      <div class="admin-header">
        <h1 class="admin-title">系统管理</h1>
        <p class="admin-desc">管理用户、角色、组织和系统配置</p>
      </div>

      <div class="admin-grid">
        <router-link
          v-for="item in visibleMenuItems"
          :key="item.path"
          :to="item.path"
          class="admin-card"
        >
          <div class="card-icon">{{ item.icon }}</div>
          <div class="card-content">
            <h3 class="card-title">{{ item.title }}</h3>
            <p class="card-desc">{{ item.description }}</p>
          </div>
          <span class="card-arrow">→</span>
        </router-link>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const loading = ref(true)

/** 是否有任一系统管理权限 */
const hasAnyAdminPermission = computed(() => {
  return authStore.hasGlobalPermission('system:manage_users')
    || authStore.hasGlobalPermission('system:manage_roles')
    || authStore.hasGlobalPermission('system:manage_orgs')
    || authStore.hasGlobalPermission('system:manage_groups')
})

onMounted(async () => {
  // 等待权限加载完毕（正常情况下路由守卫已 await，这里做兜底）
  if (!authStore.permissionsLoaded && authStore.isAuthenticated) {
    await authStore.loadGlobalPermissions()
  }
  loading.value = false
})

interface AdminMenuItem {
  path: string
  icon: string
  title: string
  description: string
  permission: string // 所需的细粒度权限
}

const menuItems: AdminMenuItem[] = [
  {
    path: '/admin/users',
    icon: '👥',
    title: '用户管理',
    description: '查看、编辑、禁用用户，管理用户角色分配',
    permission: 'system:manage_users'
  },
  {
    path: '/admin/roles',
    icon: '🛡️',
    title: '角色管理',
    description: '定义角色及其权限，配置全局和项目级角色',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/organizations',
    icon: '🏢',
    title: '组织管理',
    description: '管理组织结构和组织信息',
    permission: 'system:manage_orgs'
  },
  {
    path: '/admin/groups',
    icon: '👥',
    title: '用户组',
    description: '创建用户组，通过组批量管理权限，简化团队配置',
    permission: 'system:manage_groups'
  },
  {
    path: '/admin/workflow',
    icon: '🔄',
    title: '工作流',
    description: '配置 Issue 状态转换规则和工作流程',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/custom-fields',
    icon: '📝',
    title: '自定义字段',
    description: '定义和管理 Issue 自定义字段',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/audit-logs',
    icon: '📜',
    title: '审计日志',
    description: '查看系统权限变更记录，追踪管理操作历史',
    permission: 'system:manage_users'
  },
  {
    path: '/admin/time-tracking',
    icon: '⏱',
    title: '时间追踪',
    description: '配置每日工作时长和每周工作日',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/work-item-attributes',
    icon: '🏷️',
    title: '工作项属性',
    description: '管理工时记录的分类属性和值列表',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/notifications',
    icon: '🔔',
    title: '通知管理',
    description: '配置全局通知策略、默认偏好和保留策略',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/rules',
    icon: '⚡',
    title: '规则引擎',
    description: '配置自动化规则，实现工单计分与罚款统计',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/webhooks',
    icon: '🔗',
    title: 'Webhook',
    description: '管理项目 Webhook 通知，事件触发时推送到外部系统',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/action-rules',
    icon: '⚡',
    title: 'Action 动作',
    description: '配置工单自定义动作按钮（一键延期、标记为重复、升级优先级等）',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/integrations',
    icon: '🔌',
    title: '第三方集成',
    description: '管理外部系统适配器的启用/禁用、配置和事件日志',
    permission: 'system:manage_roles'
  }
]

/** 根据用户权限过滤可见的菜单项 */
const visibleMenuItems = computed(() => {
  return menuItems.filter(item => authStore.hasGlobalPermission(item.permission))
})
</script>

<style scoped>
.admin-page {
  padding: 32px;
  height: 100%;
  overflow-y: auto;
}

.admin-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

.loading-text {
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

.admin-forbidden {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  text-align: center;
}

.forbidden-icon {
  font-size: 40px;
  margin-bottom: 16px;
}

.forbidden-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px 0;
}

.forbidden-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0 0 24px 0;
}

.forbidden-btn {
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 16px;
  background: var(--tf-accent);
  color: #fff;
  border-radius: 6px;
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  transition: opacity 0.15s;
}

.forbidden-btn:hover {
  opacity: 0.9;
  text-decoration: none;
}

.admin-header {
  margin-bottom: 32px;
}

.admin-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px 0;
}

.admin-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.admin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.admin-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  text-decoration: none;
  transition: background 0.15s, border-color 0.15s, transform 0.1s;
  cursor: pointer;
}

.admin-card:hover {
  background: var(--tf-bg-elevated);
  border-color: var(--tf-border);
  text-decoration: none;
  transform: translateY(-1px);
}

.card-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--tf-bg-body);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.card-content {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 4px 0;
}

.card-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
  line-height: 1.4;
}

.card-arrow {
  color: var(--tf-text-muted);
  font-size: 16px;
  flex-shrink: 0;
  transition: color 0.15s, transform 0.15s;
}

.admin-card:hover .card-arrow {
  color: var(--tf-accent);
  transform: translateX(2px);
}
</style>
