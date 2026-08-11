<template>
  <div class="admin-page">
    <DataContainer
      :loading="loading"
      :is-empty="!hasAnyAdminPermission"
      empty-title="无访问权限"
      empty-description="你没有系统管理权限，无法访问此页面"
    >
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
          <div class="card-icon">
            <component :is="item.icon" />
          </div>
          <div class="card-content">
            <h3 class="card-title">{{ item.title }}</h3>
            <p class="card-desc">{{ item.description }}</p>
          </div>
          <icon-right class="card-arrow" />
        </router-link>
      </div>
    </DataContainer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  IconRight,
  IconUser,
  IconUserGroup,
  IconSafe,
  IconHome,
  IconEdit,
  IconFile,
  IconClockCircle,
  IconTag,
  IconNotification,
  IconThunderbolt,
  IconLink,
  IconSettings,
  IconShareAlt
} from '@arco-design/web-vue/es/icon'
import DataContainer from '@/components/base/DataContainer.vue'
import type { Component } from 'vue'

const authStore = useAuthStore()
const loading = ref(true)

const hasAnyAdminPermission = computed(() =>
  authStore.hasGlobalPermission('system:manage_users')
  || authStore.hasGlobalPermission('system:manage_roles')
  || authStore.hasGlobalPermission('system:manage_orgs')
  || authStore.hasGlobalPermission('system:manage_groups')
)

onMounted(async () => {
  if (!authStore.permissionsLoaded && authStore.isAuthenticated) {
    await authStore.loadGlobalPermissions()
  }
  loading.value = false
})

interface AdminMenuItem {
  path: string
  icon: Component
  title: string
  description: string
  permission: string
}

const menuItems: AdminMenuItem[] = [
  {
    path: '/admin/users',
    icon: IconUser,
    title: '用户管理',
    description: '查看、编辑、禁用用户，管理用户角色分配',
    permission: 'system:manage_users'
  },
  {
    path: '/admin/roles',
    icon: IconSafe,
    title: '角色管理',
    description: '定义角色及其权限，配置全局和项目级角色',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/organizations',
    icon: IconHome,
    title: '组织管理',
    description: '管理组织结构和组织信息',
    permission: 'system:manage_orgs'
  },
  {
    path: '/admin/groups',
    icon: IconUserGroup,
    title: '用户组',
    description: '创建用户组，通过组批量管理权限，简化团队配置',
    permission: 'system:manage_groups'
  },
  {
    path: '/admin/custom-fields',
    icon: IconEdit,
    title: '自定义字段',
    description: '定义和管理 Issue 自定义字段',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/audit-logs',
    icon: IconFile,
    title: '审计日志',
    description: '查看系统权限变更记录，追踪管理操作历史',
    permission: 'system:manage_users'
  },
  {
    path: '/admin/time-tracking',
    icon: IconClockCircle,
    title: '时间追踪',
    description: '配置每日工作时长和每周工作日',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/work-item-attributes',
    icon: IconTag,
    title: '工作项属性',
    description: '管理工时记录的分类属性和值列表',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/notifications',
    icon: IconNotification,
    title: '通知管理',
    description: '配置全局通知策略、默认偏好和保留策略',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/rules',
    icon: IconThunderbolt,
    title: '规则引擎',
    description: '配置自动化规则，实现工单计分与罚款统计',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/webhooks',
    icon: IconLink,
    title: 'Webhook',
    description: '管理项目 Webhook 通知，事件触发时推送到外部系统',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/action-rules',
    icon: IconSettings,
    title: 'Action 动作',
    description: '配置工单自定义动作按钮（一键延期、标记为重复、升级优先级等）',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/integrations',
    icon: IconShareAlt,
    title: '第三方集成',
    description: '管理外部系统适配器的启用/禁用、配置和事件日志',
    permission: 'system:manage_roles'
  },
  {
    path: '/admin/link-types',
    icon: IconLink,
    title: '关联类型',
    description: '管理工单关联类型，定义正向/反向显示名称和方向规则',
    permission: 'system:manage_settings'
  }
]

const visibleMenuItems = computed(() =>
  menuItems.filter(item => authStore.hasGlobalPermission(item.permission))
)
</script>

<style scoped>
.admin-page {
  padding: 32px;
  height: 100%;
  overflow-y: auto;
}

.admin-header { margin-bottom: 32px; }

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
  color: var(--tf-accent);
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
