<template>
  <div class="project-page">
    <!-- 顶部标题栏 -->
    <div class="page-toolbar">
      <h1 class="page-title">项目</h1>
      <div class="toolbar-right">
        <a-input-search
          v-model="searchKeyword"
          placeholder="搜索项目名称..."
          style="width: 200px"
          allow-clear
        />
        <a-button v-if="canCreateProject" type="primary" @click="showCreateDialog = true">
          <template #icon><icon-plus /></template>
          新建项目
        </a-button>
      </div>
    </div>

    <!-- 项目列表 -->
    <div class="project-list">
      <!-- 收藏项目分区 -->
      <template v-if="favoriteProjects.length > 0 && !searchKeyword">
        <div class="section-header">
          <icon-star-fill class="section-icon favorite-icon" />
          <span class="section-title">收藏</span>
          <span class="section-count">{{ favoriteProjects.length }}</span>
        </div>
        <div
          v-for="project in favoriteProjects"
          :key="'fav-' + project.id"
          class="project-row"
          @click="goToProject(project)"
        >
          <!-- 收藏星标 -->
          <div class="favorite-btn favorited" @click.stop="toggleFavorite(project)">
            <icon-star-fill />
          </div>

          <!-- 项目图标 -->
          <div class="project-icon" :style="{ background: getProjectColor(project) }">
            <span class="icon-text">{{ getProjectAbbr(project) }}</span>
          </div>

          <!-- 项目信息 -->
          <div class="project-info">
            <div class="project-name-row">
              <span class="project-name">{{ project.name }}</span>
              <span v-if="project.visibility && project.visibility !== 'private'" class="visibility-tag" :class="'vis-' + project.visibility">
                {{ project.visibility === 'internal' ? '内部' : '公开' }}
              </span>
            </div>
            <span class="project-desc" v-if="project.description">{{ project.description }}</span>
          </div>

          <!-- 右侧：成员 + 操作 -->
          <div class="project-right">
            <div class="member-avatars">
              <UserAvatar
                v-for="(member, idx) in (project.topMembers || []).slice(0, 3)"
                :key="idx"
                :name="member"
                :size="24"
              />
              <span v-if="(project.memberCount || 0) > 3" class="member-overflow">
                +{{ (project.memberCount || 0) - 3 }}
              </span>
            </div>
            <span v-if="canManageProject(project)" class="dropdown-wrapper">
              <a-dropdown trigger="click" @click.stop>
                <a-button type="text" size="small" class="btn-more">
                  <icon-more />
                </a-button>
                <template #content>
                  <a-doption @click="goToProject(project)">项目概览</a-doption>
                  <a-doption @click="editProject(project)">编辑</a-doption>
                  <a-doption @click="manageMembers(project)">成员管理</a-doption>
                  <a-doption v-if="canCreateProject" @click="openCopyDialog(project)">复制项目</a-doption>
                  <a-doption class="danger-option" @click="archiveProject(project)">归档</a-doption>
                  <a-doption v-if="canDeleteProject(project)" class="danger-option" @click="confirmDeleteProject(project)">删除项目</a-doption>
                </template>
              </a-dropdown>
            </span>
          </div>
        </div>

        <!-- 分隔线 -->
        <div class="section-divider"></div>
      </template>

      <!-- 普通项目分区 -->
      <div
        v-for="project in nonFavoriteFilteredProjects"
        :key="project.id"
        class="project-row"
        @click="goToProject(project)"
      >
        <!-- 收藏星标 -->
        <div class="favorite-btn" :class="{ favorited: project.favorited }" @click.stop="toggleFavorite(project)">
          <icon-star-fill v-if="project.favorited" />
          <icon-star v-else />
        </div>

        <!-- 项目图标 -->
        <div class="project-icon" :style="{ background: getProjectColor(project) }">
          <span class="icon-text">{{ getProjectAbbr(project) }}</span>
        </div>

        <!-- 项目信息 -->
        <div class="project-info">
          <div class="project-name-row">
            <span class="project-name">{{ project.name }}</span>
            <span v-if="project.visibility && project.visibility !== 'private'" class="visibility-tag" :class="'vis-' + project.visibility">
              {{ project.visibility === 'internal' ? '内部' : '公开' }}
            </span>
          </div>
          <span class="project-desc" v-if="project.description">{{ project.description }}</span>
        </div>

        <!-- 右侧：成员 + 操作 -->
        <div class="project-right">
          <div class="member-avatars">
            <UserAvatar
              v-for="(member, idx) in (project.topMembers || []).slice(0, 3)"
              :key="idx"
              :name="member"
              :size="24"
            />
            <span v-if="(project.memberCount || 0) > 3" class="member-overflow">
              +{{ (project.memberCount || 0) - 3 }}
            </span>
          </div>
          <!-- 项目操作菜单（按项目级权限 project:edit / project:manage_members 控制显隐） -->
          <span v-if="canManageProject(project)" class="dropdown-wrapper">
            <a-dropdown trigger="click" @click.stop>
              <a-button type="text" size="small" class="btn-more">
                <icon-more />
              </a-button>
              <template #content>
                <a-doption @click="goToProject(project)">项目概览</a-doption>
                <a-doption @click="editProject(project)">编辑</a-doption>
                <a-doption @click="manageMembers(project)">成员管理</a-doption>
                <a-doption v-if="canCreateProject" @click="openCopyDialog(project)">复制项目</a-doption>
                <a-doption class="danger-option" @click="archiveProject(project)">归档</a-doption>
                <a-doption v-if="canDeleteProject(project)" class="danger-option" @click="confirmDeleteProject(project)">删除项目</a-doption>
              </template>
            </a-dropdown>
          </span>
        </div>
      </div>

      <!-- 空状态 -->
      <template v-if="filteredProjects.length === 0 && !loading">
        <EmptyState v-if="searchKeyword" icon="search" title="未找到匹配的项目" description="尝试更换搜索关键词" />
        <EmptyState v-else-if="canCreateProject" icon="folder" title="还没有项目" description="创建您的第一个项目，开始管理团队工作">
          <template #action>
            <a-button type="primary" @click="showCreateDialog = true">创建第一个项目</a-button>
          </template>
        </EmptyState>
        <EmptyState v-else icon="user" title="您还未被分配到任何项目" description="请联系项目管理员将您添加到相关项目中，或联系系统管理员分配权限" />
      </template>

      <!-- 加载更多 -->
      <div v-if="hasMore" class="load-more" @click="loadMore">
        <a-link>显示更多项目</a-link>
      </div>

      <!-- 已归档项目折叠区域（YouTrack 风格） -->
      <div v-if="archivedCount > 0" class="archived-section">
        <div class="archived-header" @click="toggleArchived">
          <span class="archived-toggle-icon" :class="{ expanded: showArchived }">
            <icon-right />
          </span>
          <span class="archived-title">已归档</span>
          <span class="archived-count">{{ archivedCount }}</span>
        </div>

        <div v-if="showArchived" class="archived-list">
          <div v-if="archivedLoading" class="archived-loading">
            <a-spin :size="16" />
            <span>加载中...</span>
          </div>
          <template v-else>
            <div
              v-for="project in archivedProjects"
              :key="project.id"
              class="project-row archived-row"
            >
              <!-- 项目图标 -->
              <div class="project-icon archived-icon" :style="{ background: getProjectColor(project) }">
                <span class="icon-text">{{ getProjectAbbr(project) }}</span>
              </div>

              <!-- 项目信息 -->
              <div class="project-info">
                <div class="project-name-row">
                  <span class="project-name archived-name">{{ project.name }}</span>
                  <span class="archived-badge">已归档</span>
                </div>
                <span class="project-desc" v-if="project.description">{{ project.description }}</span>
              </div>

              <!-- 恢复操作 -->
              <div class="project-right">
                <a-button
                  v-if="canManageProject(project)"
                  type="outline"
                  size="small"
                  @click.stop="restoreProject(project)"
                  :loading="restoringId === project.id"
                >
                  恢复项目
                </a-button>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 创建项目弹窗 -->
    <a-modal
      v-model:visible="showCreateDialog"
      title="新建项目"
      :width="520"
      :ok-text="'创建项目'"
      :cancel-text="'取消'"
      :ok-loading="creating"
      :ok-button-props="{ disabled: !createForm.name || !createForm.key }"
      @before-ok="handleCreateBeforeOk"
      @close="resetCreateForm"
    >
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="项目名称" required :validate-status="createNameError ? 'error' : undefined" :help="createNameError">
          <a-input v-model="createForm.name" placeholder="例如：后端开发" @input="createNameError = ''" />
        </a-form-item>
        <a-form-item label="项目标识" required :validate-status="createKeyError ? 'error' : undefined" :help="createKeyError" :extra="createKeyError ? undefined : '用于生成工单编号，如 BE-1, BE-2...'">
          <a-input
            v-model="createForm.key"
            placeholder="例如：BE（大写英文缩写）"
            :max-length="10"
            @input="handleCreateKeyInput"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea
            v-model="createForm.description"
            placeholder="可选，简要描述项目用途"
            :auto-size="{ minRows: 2, maxRows: 5 }"
          />
        </a-form-item>
        <a-form-item label="起始编号" extra="设置后第一个工单编号将从此值 +1 开始，例如设为 100 则第一个工单为 KEY-101">
          <a-input-number
            v-model="createForm.startingNumber"
            placeholder="可选，默认为 0"
            :min="0"
            :max="999999"
            :precision="0"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="项目模板">
          <a-radio-group v-model="createForm.template" direction="vertical">
            <a-radio value="default">
              <template #radio="{ checked }">
                <div class="template-option" :class="{ selected: checked }">
                  <div class="template-info">
                    <span class="template-name">默认</span>
                    <span class="template-desc">标准软件开发流程，包含常用状态和优先级</span>
                  </div>
                </div>
              </template>
            </a-radio>
            <a-radio value="scrum">
              <template #radio="{ checked }">
                <div class="template-option" :class="{ selected: checked }">
                  <div class="template-info">
                    <span class="template-name">Scrum</span>
                    <span class="template-desc">敏捷开发模式，配合迭代和看板使用</span>
                  </div>
                </div>
              </template>
            </a-radio>
            <a-radio value="kanban">
              <template #radio="{ checked }">
                <div class="template-option" :class="{ selected: checked }">
                  <div class="template-info">
                    <span class="template-name">看板</span>
                    <span class="template-desc">精益管理模式，按阶段流转任务</span>
                  </div>
                </div>
              </template>
            </a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑项目弹窗 -->
    <a-modal
      v-model:visible="showEditDialog"
      title="编辑项目"
      :width="480"
      ok-text="保存修改"
      cancel-text="取消"
      :ok-loading="editSaving"
      :ok-button-props="{ disabled: !editForm.name }"
      @before-ok="handleEditBeforeOk"
      @close="editMembers = []; editNameError = ''"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="项目名称" required :validate-status="editNameError ? 'error' : undefined" :help="editNameError">
          <a-input v-model="editForm.name" @input="editNameError = ''" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model="editForm.description" :auto-size="{ minRows: 2, maxRows: 5 }" />
        </a-form-item>
        <a-form-item label="项目负责人">
          <a-select
            v-model="editForm.leadId"
            placeholder="选择项目负责人..."
            allow-search
            :loading="editMembersLoading"
            @focus="loadEditMembers"
          >
            <a-option v-for="m in editMembers" :key="m.userId" :value="m.userId">
              {{ m.displayName || m.username }}
              <span v-if="m.email" style="color: var(--tf-text-tertiary); margin-left: 4px; font-size: 11px">{{ m.email }}</span>
            </a-option>
          </a-select>
          <template #extra>
            <span style="font-size: 11px; color: var(--tf-text-tertiary)">
              只能选择当前项目成员。变更后新负责人将自动升级为项目管理员。
            </span>
          </template>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 成员管理弹窗 -->
    <a-modal
      v-model:visible="showMembersDialog"
      :title="'成员管理 — ' + (currentProject?.name || '')"
      :width="700"
      :footer="false"
      class="members-modal"
    >
      <a-tabs default-active-key="members" @change="onMemberTabChange">
        <a-tab-pane key="members" title="成员列表">
          <!-- 添加成员 -->
          <div style="margin-bottom: 16px; display: flex; gap: 8px; align-items: center">
            <a-select
              v-model="addMemberForm.userId"
              placeholder="选择用户..."
              allow-search
              style="flex: 1"
              popup-container=".members-modal"
              @focus="loadAllUsers"
            >
              <a-option v-for="u in allUsers" :key="u.id" :value="u.id">
                {{ u.displayName || u.username }} ({{ u.email || '' }})
              </a-option>
            </a-select>
            <a-select
              v-model="addMemberForm.roleIds"
              placeholder="选择角色..."
              multiple
              style="width: 200px"
              popup-container=".members-modal"
              @focus="loadProjectRoles"
            >
              <a-option v-for="r in projectRoles" :key="r.id" :value="r.id">
                {{ r.name }}
              </a-option>
            </a-select>
            <a-button type="primary" size="small" @click="addMember" :disabled="!addMemberForm.userId || addMemberForm.roleIds.length === 0">
              添加
            </a-button>
          </div>

          <!-- 成员列表 -->
          <a-table :data="projectMembers" :pagination="false" size="small">
            <template #columns>
              <a-table-column title="用户" data-index="displayName">
                <template #cell="{ record }">
                  {{ record.displayName || record.username }}
                  <span style="color: var(--tf-text-tertiary); margin-left: 4px">{{ record.email }}</span>
                </template>
              </a-table-column>
              <a-table-column title="角色" :width="200">
                <template #cell="{ record }">
                  <a-select
                    :model-value="record.roleIds || [record.roleId]"
                    size="mini"
                    multiple
                    :max-tag-count="2"
                    popup-container=".members-modal"
                    @change="(val: any) => changeMemberRole(record.userId, val)"
                    @focus="loadProjectRoles"
                  >
                    <a-option v-for="r in projectRoles" :key="r.id" :value="r.id">
                      {{ r.name }}
                    </a-option>
                  </a-select>
                </template>
              </a-table-column>
              <a-table-column title="操作" :width="80">
                <template #cell="{ record }">
                  <a-button type="text" size="mini" status="danger" @click="confirmRemoveMember(record)">移除</a-button>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="activity" title="活动日志">
          <div v-if="activityLoading" style="text-align: center; padding: 32px">
            <a-spin />
          </div>
          <div v-else-if="activities.length === 0" class="activity-empty">
            <span style="font-size: 32px">📋</span>
            <p style="color: var(--tf-text-tertiary); margin-top: 8px">暂无活动记录</p>
            <p style="color: var(--tf-text-tertiary); font-size: 12px">成员变动操作将记录在此处</p>
          </div>
          <div v-else class="activity-list">
            <div v-for="act in activities" :key="act.id" class="activity-item">
              <div class="activity-icon">
                <span v-if="act.action === 'add_member' || act.action === 'member_added'">➕</span>
                <span v-else-if="act.action === 'remove_member' || act.action === 'member_removed'">➖</span>
                <span v-else-if="act.action === 'change_role' || act.action === 'member_role_changed'">🔄</span>
                <span v-else-if="act.action === 'change_lead' || act.action === 'lead_changed'">⭐</span>
                <span v-else-if="act.action === 'update_project' || act.action === 'project_updated'">✏️</span>
                <span v-else-if="act.action === 'change_visibility' || act.action === 'visibility_changed'">👁️</span>
                <span v-else>📝</span>
              </div>
              <div class="activity-content">
                <span class="activity-text">{{ formatActivityText(act) }}</span>
                <span class="activity-time">{{ formatRelativeTime(act.createdAt) }}</span>
              </div>
            </div>
            <div v-if="activityHasMore" style="text-align: center; margin-top: 12px">
              <a-button type="text" size="small" @click="loadMoreActivities">加载更多</a-button>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-modal>

    <!-- 删除项目确认弹窗 -->
    <a-modal
      v-model:visible="showDeleteDialog"
      title="删除项目"
      :ok-text="'永久删除'"
      :cancel-text="'取消'"
      :ok-loading="deleting"
      :ok-button-props="{ disabled: deleteConfirmKey !== deleteTarget?.projectKey, status: 'danger' }"
      @before-ok="handleDeleteBeforeOk"
    >
      <div class="delete-confirm-content">
        <div class="delete-warning">
          <icon-exclamation-circle-fill class="warning-icon" />
          <span>此操作不可撤销！项目及其所有数据将被永久删除。</span>
        </div>
        <div v-if="deleteTarget" class="delete-impact">
          <p class="impact-title">即将删除的数据：</p>
          <ul class="impact-list">
            <li>📋 {{ deleteTarget.issueCount }} 个工单<span v-if="deleteTarget.openIssueCount > 0" class="impact-warn">（其中 {{ deleteTarget.openIssueCount }} 个未关闭）</span></li>
            <li>🏃 {{ deleteTarget.sprintCount }} 个 Sprint</li>
            <li>👥 {{ deleteTarget.memberCount }} 名成员</li>
            <li v-if="(deleteTarget as any).timeEntryCount > 0">⏱️ {{ (deleteTarget as any).timeEntryCount }} 条工时记录</li>
          </ul>
        </div>
        <div class="delete-confirm-input">
          <p>请输入项目标识 <strong>{{ deleteTarget?.projectKey }}</strong> 确认删除：</p>
          <a-input v-model="deleteConfirmKey" placeholder="输入项目标识确认" />
        </div>
      </div>
    </a-modal>

    <!-- 复制项目弹窗 -->
    <a-modal
      v-model:visible="showCopyDialog"
      title="复制项目"
      :width="520"
      :ok-text="'复制项目'"
      :cancel-text="'取消'"
      :ok-loading="copying"
      :ok-button-props="{ disabled: !copyForm.name || !copyForm.key }"
      @before-ok="handleCopyBeforeOk"
      @close="resetCopyForm"
    >
      <!-- 源项目信息 -->
      <div class="copy-source-info" v-if="copySourceProject">
        <div class="copy-source-header">
          <div class="project-icon" :style="{ background: getProjectColor(copySourceProject), minWidth: '28px', height: '28px', width: 'auto', padding: '0 4px' }">
            <span class="icon-text" style="font-size: 9px">{{ getProjectAbbr(copySourceProject) }}</span>
          </div>
          <div class="copy-source-detail">
            <span class="copy-source-name">{{ copySourceProject.name }}</span>
            <span class="copy-source-key">{{ copySourceProject.key }}</span>
          </div>
        </div>
      </div>

      <a-form :model="copyForm" layout="vertical" style="margin-top: 16px">
        <a-form-item label="新项目名称" required :validate-status="copyNameError ? 'error' : undefined" :help="copyNameError">
          <a-input v-model="copyForm.name" placeholder="例如：后端开发 (副本)" @input="copyNameError = ''" />
        </a-form-item>
        <a-form-item label="项目标识" required :validate-status="copyKeyError ? 'error' : undefined" :help="copyKeyError" :extra="copyKeyError ? undefined : '用于生成工单编号，如 BE-1, BE-2...'">
          <a-input
            v-model="copyForm.key"
            placeholder="例如：BE2（大写英文缩写）"
            :max-length="10"
            @input="handleCopyKeyInput"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea
            v-model="copyForm.description"
            placeholder="可选，新项目的描述"
            :auto-size="{ minRows: 2, maxRows: 4 }"
          />
        </a-form-item>
        <a-form-item label="复制内容">
          <div class="copy-options-list">
            <a-checkbox v-model="copyForm.options.workflow">
              <span class="copy-option-text">工作流规则</span>
              <span class="copy-option-count" v-if="copySummary">{{ copySummary.workflow || 0 }} 条</span>
            </a-checkbox>
            <a-checkbox v-model="copyForm.options.tags">
              <span class="copy-option-text">标签</span>
              <span class="copy-option-count" v-if="copySummary">{{ copySummary.tags || 0 }} 个</span>
            </a-checkbox>
            <a-checkbox v-model="copyForm.options.custom_fields">
              <span class="copy-option-text">自定义字段绑定</span>
              <span class="copy-option-count" v-if="copySummary">{{ copySummary.customFields || 0 }} 个</span>
            </a-checkbox>
            <a-checkbox v-model="copyForm.options.board">
              <span class="copy-option-text">看板配置</span>
              <span class="copy-option-count" v-if="copySummary">{{ copySummary.board || 0 }} 列</span>
            </a-checkbox>
            <a-checkbox v-model="copyForm.options.actions">
              <span class="copy-option-text">转换动作</span>
              <span class="copy-option-count" v-if="copySummary">{{ copySummary.actions || 0 }} 个</span>
            </a-checkbox>
            <a-checkbox v-model="copyForm.options.members">
              <span class="copy-option-text">项目成员</span>
              <span class="copy-option-count" v-if="copySummary">{{ copySummary.members || 0 }} 人</span>
            </a-checkbox>
            <a-checkbox v-model="copyForm.options.queries">
              <span class="copy-option-text">共享查询</span>
              <span class="copy-option-count" v-if="copySummary">{{ copySummary.queries || 0 }} 个</span>
            </a-checkbox>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onActivated, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import {
  IconPlus,
  IconMore,
  IconRight,
  IconExclamationCircleFill,
  IconStar,
  IconStarFill
} from '@arco-design/web-vue/es/icon'
import { projectApi, userApi, workflowApi } from '@/api'
import { getProjectColor as getProjectColorFromPool } from '@/utils/uiColors'
import type { ProjectActivityVO, ProjectCopySummaryVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { loadProjectPermissions } from '@/composables/usePermission'
import { UserAvatar, EmptyState } from '@/components/base'

const router = useRouter()
const authStore = useAuthStore()

const canCreateProject = computed(() => authStore.hasGlobalPermission('project:create'))

// ========== 项目级权限（逐项目判断） ==========
const projectPermCache = ref<Record<string, Set<string>>>({})

/**
 * 批量加载所有已显示项目的权限
 * system:admin 跳过（自动有所有权限）
 */
async function loadProjectPermissionsForList() {
  if (authStore.hasGlobalPermission('system:admin')) return

  const projectIds = [...new Set(projects.value.map((p: any) => p.id as string))]
  const uncached = projectIds.filter(pid => !projectPermCache.value[pid])
  if (uncached.length === 0) return

  await Promise.all(uncached.map(async (pid) => {
    const perms = await loadProjectPermissions(pid)
    projectPermCache.value[pid] = perms
  }))
}

/**
 * 判断当前用户是否能管理指定项目（编辑/成员管理/归档）
 * system:admin 直接返回 true
 * 未加载权限时返回 false（悲观策略），避免无权用户看到闪烁的操作按钮
 */
function canManageProject(project: any): boolean {
  if (authStore.hasGlobalPermission('system:admin')) return true
  const perms = projectPermCache.value[project.id]
  if (!perms) return false // 未加载时隐藏操作按钮，避免闪烁
  return perms.has('project:edit') || perms.has('project:manage_members')
}

/**
 * 判断当前用户是否能删除指定项目（需要 project:delete 权限）
 */
function canDeleteProject(project: any): boolean {
  if (authStore.hasGlobalPermission('system:admin')) return true
  const perms = projectPermCache.value[project.id]
  if (!perms) return false // 删除是高危操作，未加载权限时默认不允许
  return perms.has('project:delete')
}

const projects = ref<any[]>([])
const loading = ref(false)
const creating = ref(false)
const searchKeyword = ref('')
const showCreateDialog = ref(false)
const createKeyError = ref('')
const createNameError = ref('')
const hasMore = ref(false)
const page = ref(1)
const pageSize = 20

const createForm = reactive({
  name: '',
  key: '',
  description: '',
  template: 'default',
  startingNumber: undefined as number | undefined,
  orgId: undefined as string | undefined
})

// 编辑项目
const showEditDialog = ref(false)
const editSaving = ref(false)
const editForm = reactive({
  id: '',
  key: '',
  name: '',
  description: '',
  leadId: '' as string | undefined
})
const editMembers = ref<any[]>([])
const editMembersLoading = ref(false)

// 成员管理
const showMembersDialog = ref(false)
const currentProject = ref<any>(null)

// 删除项目
const showDeleteDialog = ref(false)
const deleting = ref(false)
const deleteConfirmKey = ref('')
const deleteTarget = ref<{
  id: string
  key: string
  projectName: string
  projectKey: string
  issueCount: number
  sprintCount: number
  memberCount: number
  openIssueCount: number
} | null>(null)
const projectMembers = ref<any[]>([])
const allUsers = ref<any[]>([])
const projectRoles = ref<{ id: string; name: string }[]>([])
const addMemberForm = reactive({ userId: undefined as string | undefined, roleIds: [] as string[] })
const showAddMember = ref(false)

// 活动日志
const activities = ref<ProjectActivityVO[]>([])
const activityLoading = ref(false)
const activityPage = ref(1)
const activityHasMore = ref(false)

// 项目颜色池
function getProjectColor(project: any) {
  return getProjectColorFromPool(project.id || '0')
}

function getProjectAbbr(project: any) {
  return project.key || project.name?.charAt(0) || '?'
}

const filteredProjects = computed(() => {
  if (!searchKeyword.value) return projects.value
  const kw = searchKeyword.value.toLowerCase()
  return projects.value.filter(p =>
    p.name.toLowerCase().includes(kw) ||
    p.key.toLowerCase().includes(kw)
  )
})

/** 收藏的项目（搜索模式下不单独分区） */
const favoriteProjects = computed(() => {
  return projects.value.filter(p => p.favorited)
})

/** 非收藏的过滤后项目（搜索模式下显示全部匹配结果） */
const nonFavoriteFilteredProjects = computed(() => {
  if (searchKeyword.value) {
    // 搜索模式下不区分收藏/非收藏，直接返回搜索结果
    return filteredProjects.value
  }
  return projects.value.filter(p => !p.favorited)
})

async function loadProjects() {
  loading.value = true
  try {
    const res = await projectApi.list({ page: page.value, pageSize })
    const list = res.data?.list || []
    if (page.value === 1) {
      projects.value = list
    } else {
      projects.value.push(...list)
    }
    const total = res.data?.pagination?.total || list.length
    hasMore.value = projects.value.length < total
  } catch (e: any) {
    if (e.response?.status !== 401) {
      Message.error(e.response?.data?.message || '加载项目列表失败')
    }
    projects.value = []
  } finally {
    loading.value = false
  }
}

function loadMore() {
  page.value++
  loadProjects()
}

/** 切换项目收藏状态（乐观更新） */
async function toggleFavorite(project: any) {
  const previousState = project.favorited
  // 乐观更新
  project.favorited = !previousState
  try {
    const res = await projectApi.toggleFavorite(project.key)
    if (res.code === 0 && res.data) {
      project.favorited = res.data.favorited
    }
  } catch (e: any) {
    // 回滚
    project.favorited = previousState
    Message.error(e.response?.data?.message || '操作失败')
  }
}

function goToProject(project: any) {
  router.push({ path: `/projects/${project.key}` })
}

function editProject(project: any) {
  editForm.id = project.id
  editForm.key = project.key
  editForm.name = project.name
  editForm.description = project.description || ''
  editForm.leadId = project.leadId || undefined
  editMembers.value = []
  showEditDialog.value = true
  // 自动加载成员列表
  loadEditMembers()
}

async function loadEditMembers() {
  if (editMembers.value.length > 0 || !editForm.key) return
  editMembersLoading.value = true
  try {
    const res = await projectApi.listMembers(editForm.key)
    editMembers.value = res.data || []
  } catch {
    editMembers.value = []
  } finally {
    editMembersLoading.value = false
  }
}

function manageMembers(project: any) {
  currentProject.value = project
  activities.value = []
  activityPage.value = 1
  activityHasMore.value = false
  loadProjectMembers(project.key)
  loadProjectRoles()
  showMembersDialog.value = true
}

function archiveProject(project: any) {
  Modal.warning({
    title: '归档项目',
    content: `确定归档项目「${project.name}」？归档后可在项目列表底部查看并恢复。`,
    okText: '确定归档',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      try {
        await projectApi.archive(project.key)
        projects.value = projects.value.filter(p => p.id !== project.id)
        Message.success('项目已归档')
        // 刷新已归档计数
        loadArchivedCount()
        // 如果已归档区域已展开，重新加载列表
        if (showArchived.value) {
          loadArchivedProjects()
        }
      } catch (e: any) {
        Message.error(e.response?.data?.message || '归档失败')
      }
    }
  })
}

// ========== 已归档项目 ==========
const showArchived = ref(false)
const archivedProjects = ref<any[]>([])
const archivedCount = ref(0)
const archivedLoading = ref(false)
const restoringId = ref<string | null>(null)

/** 加载已归档项目计数 */
async function loadArchivedCount() {
  try {
    const res = await projectApi.list({ status: 'archived', page: 1, pageSize: 1 })
    archivedCount.value = res.data?.pagination?.total || 0
  } catch {
    archivedCount.value = 0
  }
}

/** 切换已归档区域展开/收起 */
function toggleArchived() {
  showArchived.value = !showArchived.value
  if (showArchived.value && archivedProjects.value.length === 0) {
    loadArchivedProjects()
  }
}

/** 加载已归档项目列表 */
async function loadArchivedProjects() {
  archivedLoading.value = true
  try {
    const res = await projectApi.list({ status: 'archived', page: 1, pageSize: 100 })
    archivedProjects.value = res.data?.list || []
  } catch {
    archivedProjects.value = []
  } finally {
    archivedLoading.value = false
  }
}

/** 恢复已归档项目 */
async function restoreProject(project: any) {
  restoringId.value = project.id
  try {
    await projectApi.restore(project.key)
    Message.success(`项目「${project.name}」已恢复`)
    // 从已归档列表移除
    archivedProjects.value = archivedProjects.value.filter(p => p.id !== project.id)
    archivedCount.value = Math.max(0, archivedCount.value - 1)
    // 刷新活跃项目列表
    page.value = 1
    loadProjects()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '恢复失败')
  } finally {
    restoringId.value = null
  }
}

async function confirmDeleteProject(project: any) {
  // 调用预检查接口获取受影响数据
  try {
    const res = await projectApi.deletePreCheck(project.key)
    if (res.code === 0 && res.data) {
      deleteTarget.value = { id: project.id, key: project.key, ...res.data }
      deleteConfirmKey.value = ''
      showDeleteDialog.value = true
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '无法获取项目信息')
  }
}

async function handleDeleteBeforeOk(done: (closed: boolean) => void) {
  if (!deleteTarget.value) { done(false); return }
  if (deleteConfirmKey.value !== deleteTarget.value.projectKey) { done(false); return }
  deleting.value = true
  try {
    await projectApi.delete(deleteTarget.value.key, deleteConfirmKey.value)
    projects.value = projects.value.filter(p => p.id !== deleteTarget.value!.id)
    Message.success('项目已永久删除')
    deleteTarget.value = null
    done(true)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
    done(false)
  } finally {
    deleting.value = false
  }
}

async function handleCreateBeforeOk(done: (closed: boolean) => void) {
  if (!createForm.name || !createForm.key) {
    done(false)
    return
  }
  creating.value = true
  try {
    await projectApi.create({
      name: createForm.name,
      key: createForm.key,
      description: createForm.description || undefined,
      template: createForm.template || 'default',
      startingNumber: createForm.startingNumber != null ? createForm.startingNumber : undefined,
      orgId: createForm.orgId || undefined
    })
    resetCreateForm()
    Message.success('项目创建成功')
    page.value = 1
    loadProjects()
    done(true)
  } catch (e: any) {
    const code = e.response?.data?.code
    const message = e.response?.data?.message || '创建失败'
    if (code === 40905) {
      // 项目标识已存在
      createKeyError.value = '该项目标识已被占用，请使用其他标识'
    } else if (code === 40900 && message.includes('名称')) {
      createNameError.value = message
    } else {
      Message.error(message)
    }
    done(false)
  } finally {
    creating.value = false
  }
}

function handleCreateKeyInput() {
  createForm.key = createForm.key.toUpperCase()
  createKeyError.value = ''
}

function resetCreateForm() {
  createForm.name = ''
  createForm.key = ''
  createForm.description = ''
  createForm.template = 'default'
  createForm.startingNumber = undefined
  createForm.orgId = undefined
  createKeyError.value = ''
  createNameError.value = ''
}

// ========== 编辑项目 ==========
const editNameError = ref('')

async function handleEditBeforeOk(done: (closed: boolean) => void) {
  if (!editForm.name) {
    done(false)
    return
  }
  editSaving.value = true
  try {
    await projectApi.update(editForm.key, {
      name: editForm.name,
      description: editForm.description || undefined,
      leadId: editForm.leadId || undefined
    })
    Message.success('项目更新成功')
    page.value = 1
    loadProjects()
    done(true)
  } catch (e: any) {
    const message = e.response?.data?.message || '更新失败'
    if (e.response?.status === 409 || (message.includes('名称') && message.includes('已存在'))) {
      editNameError.value = message
    } else {
      Message.error(message)
    }
    done(false)
  } finally {
    editSaving.value = false
  }
}

// ========== 成员管理 ==========
async function loadProjectMembers(projectId: string) {
  try {
    const res = await projectApi.listMembers(projectId)
    projectMembers.value = res.data || []
  } catch (e) {
    projectMembers.value = []
  }
}

async function loadAllUsers() {
  if (allUsers.value.length > 0) return
  try {
    const res = await userApi.list({ pageSize: 200 })
    allUsers.value = res.data?.list || []
  } catch (e) {
    allUsers.value = []
  }
}

async function loadProjectRoles() {
  if (projectRoles.value.length > 0) return
  try {
    const res = await workflowApi.listProjectRoles()
    projectRoles.value = (res.data || []).map((r: any) => ({ id: String(r.id), name: r.name }))
  } catch (e) {
    projectRoles.value = []
  }
}

async function addMember() {
  if (!addMemberForm.userId || !currentProject.value || addMemberForm.roleIds.length === 0) return
  try {
    await projectApi.addMember(currentProject.value.key, {
      userId: addMemberForm.userId,
      roleIds: addMemberForm.roleIds.map(Number)
    })
    Message.success('成员添加成功')
    addMemberForm.userId = undefined
    addMemberForm.roleIds = []
    showAddMember.value = false
    loadProjectMembers(currentProject.value.key)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加失败')
  }
}

async function confirmRemoveMember(record: any) {
  if (!currentProject.value) return
  try {
    // 预检：查询该成员被分配的工单数量
    const res = await projectApi.getAssignedIssueCount(currentProject.value.key, record.userId)
    const count = res.data?.count || 0
    const memberName = record.displayName || record.username

    if (count > 0) {
      const { confirmDangerDelete } = useConfirmDelete()
      confirmDangerDelete({
        itemName: `成员「${memberName}」`,
        impactDescription: `当前负责 ${count} 个工单，移除后将自动取消这些工单的负责人分配`,
        confirmText: '确定移除',
        onConfirm: () => removeMember(record.userId)
      })
    } else {
      const { confirmDelete } = useConfirmDelete()
      confirmDelete({
        itemName: `成员「${memberName}」`,
        confirmText: '确定移除',
        onConfirm: () => removeMember(record.userId)
      })
    }
  } catch (e: any) {
    // 预检失败时退回简单确认
    const { confirmDelete } = useConfirmDelete()
    confirmDelete({
      itemName: `成员「${record.displayName || record.username}」`,
      confirmText: '确定移除',
      onConfirm: () => removeMember(record.userId)
    })
  }
}

async function removeMember(userId: string) {
  if (!currentProject.value) return
  try {
    const res = await projectApi.removeMember(currentProject.value.key, userId)
    const affectedCount = res.data?.affectedIssueCount || 0
    if (affectedCount > 0) {
      Message.success(`成员已移除，${affectedCount} 个工单的负责人已自动取消分配`)
    } else {
      Message.success('成员已移除')
    }
    loadProjectMembers(currentProject.value.key)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移除失败')
  }
}

async function changeMemberRole(userId: string, roleIds: string[]) {
  if (!currentProject.value) return
  try {
    const res = await projectApi.updateMemberRole(currentProject.value.key, userId, roleIds.map(Number))
    const affectedCount = res.data?.affectedIssueCount || 0
    if (affectedCount > 0) {
      Message.success(`角色已更新，已清空 ${affectedCount} 个工单的负责人（因角色变更后该用户不再可被分配）`)
    } else {
      Message.success('角色已更新')
    }
    loadProjectMembers(currentProject.value.key)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
    // 刷新列表恢复正确状态
    loadProjectMembers(currentProject.value.key)
  }
}

// ========== 活动日志 ==========

function onMemberTabChange(key: string | number) {
  if (key === 'activity' && activities.value.length === 0) {
    loadActivities()
  }
}

async function loadActivities() {
  if (!currentProject.value) return
  activityLoading.value = true
  activityPage.value = 1
  try {
    const res = await projectApi.listActivities(currentProject.value.key, { page: 1, pageSize: 20 })
    if (res.code === 0 && res.data) {
      activities.value = res.data.list || []
      activityHasMore.value = (res.data.pagination?.page ?? 1) < (res.data.pagination?.totalPages ?? 1)
    }
  } catch {
    activities.value = []
  } finally {
    activityLoading.value = false
  }
}

async function loadMoreActivities() {
  if (!currentProject.value) return
  activityPage.value++
  try {
    const res = await projectApi.listActivities(currentProject.value.key, { page: activityPage.value, pageSize: 20 })
    if (res.code === 0 && res.data) {
      activities.value.push(...(res.data.list || []))
      activityHasMore.value = (res.data.pagination?.page ?? 1) < (res.data.pagination?.totalPages ?? 1)
    }
  } catch {
    // 静默失败
  }
}

function formatActivityText(act: ProjectActivityVO): string {
  const operator = act.userName || '未知用户'
  const target = act.targetUserName || '未知用户'
  let detail: any = {}
  try { detail = act.detail ? JSON.parse(act.detail) : {} } catch { /* JSON 解析容错，降级为空对象 */ }

  switch (act.action) {
    case 'add_member':
    case 'member_added': {
      const roleName = detail.role_names || detail.role_name || '未知角色'
      return `${operator} 添加了成员 ${target}（角色：${roleName}）`
    }
    case 'remove_member':
    case 'member_removed':
      return `${operator} 移除了成员 ${target}`
    case 'change_role':
    case 'member_role_changed': {
      const oldRole = detail.old_role_names || detail.old_role_name || '未知角色'
      const newRole = detail.new_role_names || detail.new_role_name || '未知角色'
      return `${operator} 将 ${target} 的角色从「${oldRole}」变更为「${newRole}」`
    }
    case 'change_lead':
    case 'lead_changed':
      return `${operator} 将项目负责人从「${detail.old_lead_name || '未设置'}」变更为「${detail.new_lead_name || ''}」`
    case 'update_project':
    case 'project_updated': {
      const field = detail.field
      if (field === 'name') {
        return `${operator} 将项目名称从「${detail.old_value || ''}」变更为「${detail.new_value || ''}」`
      } else if (field === 'description') {
        return `${operator} 更新了项目描述`
      }
      return `${operator} 更新了项目设置`
    }
    case 'change_visibility':
    case 'visibility_changed': {
      const visibilityMap: Record<string, string> = { private: '私有', internal: '内部', public: '公开' }
      const oldVis = visibilityMap[detail.old_value] || detail.old_value || ''
      const newVis = visibilityMap[detail.new_value] || detail.new_value || ''
      return `${operator} 将项目可见性从「${oldVis}」变更为「${newVis}」`
    }
    case 'archive_project':
    case 'project_archived': {
      const sprintInfo = detail.suspended_sprint_count
        ? `（${detail.suspended_sprint_count} 个活跃 Sprint 已暂停）`
        : ''
      return `${operator} 归档了项目${sprintInfo}`
    }
    case 'restore_project':
    case 'project_restored':
      return `${operator} 恢复了项目`
    default:
      return `${operator} 执行了操作 ${act.action}`
  }
}

function formatRelativeTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return date.toLocaleDateString('zh-CN')
}

// ========== 复制项目 ==========
const showCopyDialog = ref(false)
const copying = ref(false)
const copySourceProject = ref<any>(null)
const copySummary = ref<ProjectCopySummaryVO | null>(null)
const copyNameError = ref('')
const copyKeyError = ref('')
const copyForm = reactive({
  name: '',
  key: '',
  description: '',
  options: {
    workflow: true,
    tags: true,
    custom_fields: true,
    board: true,
    actions: true,
    members: false,
    queries: false
  }
})

function openCopyDialog(project: any) {
  copySourceProject.value = project
  copyForm.name = project.name + ' (副本)'
  copyForm.key = ''
  copyForm.description = project.description || ''
  copyForm.options = {
    workflow: true,
    tags: true,
    custom_fields: true,
    board: true,
    actions: true,
    members: false,
    queries: false
  }
  copyNameError.value = ''
  copyKeyError.value = ''
  copySummary.value = null
  showCopyDialog.value = true
  // 加载源项目概要
  loadCopySummary(project.id)
}

async function loadCopySummary(projectId: string) {
  try {
    const res = await projectApi.getCopySummary(projectId)
    if (res.code === 0 && res.data) {
      copySummary.value = res.data
    }
  } catch {
    copySummary.value = null
  }
}

function handleCopyKeyInput() {
  copyForm.key = copyForm.key.toUpperCase()
  copyKeyError.value = ''
}

function resetCopyForm() {
  copyForm.name = ''
  copyForm.key = ''
  copyForm.description = ''
  copyNameError.value = ''
  copyKeyError.value = ''
  copySummary.value = null
  copySourceProject.value = null
}

async function handleCopyBeforeOk(done: (closed: boolean) => void) {
  if (!copyForm.name || !copyForm.key || !copySourceProject.value) {
    done(false)
    return
  }
  copying.value = true
  try {
    // 构建选中的 copyOptions 数组
    const copyOptions: string[] = []
    for (const [key, checked] of Object.entries(copyForm.options)) {
      if (checked) copyOptions.push(key)
    }

    await projectApi.copy({
      sourceProjectId: copySourceProject.value.id,
      name: copyForm.name,
      key: copyForm.key,
      description: copyForm.description || undefined,
      copyOptions
    })
    resetCopyForm()
    Message.success('项目复制成功')
    page.value = 1
    loadProjects()
    done(true)
  } catch (e: any) {
    const code = e.response?.data?.code
    const message = e.response?.data?.message || '复制失败'
    if (code === 40905 || message.includes('已存在')) {
      copyKeyError.value = '该项目标识已被占用，请使用其他标识'
    } else if (message.includes('名称')) {
      copyNameError.value = message
    } else {
      Message.error(message)
    }
    done(false)
  } finally {
    copying.value = false
  }
}

// KeepAlive 按 name 匹配缓存组件
defineOptions({ name: 'ProjectListView' })

onMounted(() => {
  loadProjects()
  loadArchivedCount()
})

// KeepAlive 激活时刷新项目列表（保留搜索条件，只刷新数据）
onActivated(() => {
  loadProjects()
})

// 项目列表变化时，加载对应项目的权限
watch(projects, () => {
  loadProjectPermissionsForList()
}, { flush: 'post' })
</script>

<style scoped>
.project-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 24px 16px;
  flex-shrink: 0;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 项目列表 */
.project-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px 24px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 收藏分区标题 */
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px 4px;
}

.section-icon {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.section-icon.favorite-icon {
  color: var(--tf-warning);
}

.section-title {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.section-count {
  font-size: 10px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 0 5px;
  border-radius: 3px;
  font-weight: 500;
}

.section-divider {
  height: 1px;
  background: var(--tf-border);
  margin: 12px 16px;
  opacity: 0.5;
}

/* 收藏星标按钮 */
.favorite-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  flex-shrink: 0;
  margin-right: 8px;
  cursor: pointer;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  opacity: 0;
  transition: opacity 0.15s, color 0.15s, background 0.15s;
  font-size: 14px;
}

.favorite-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-warning);
}

.favorite-btn.favorited {
  opacity: 1;
  color: var(--tf-warning);
}

.project-row:hover .favorite-btn {
  opacity: 1;
}

.project-row {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: var(--tf-bg-surface);
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.project-row:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-border);
  box-shadow: var(--tf-shadow);
}

.project-icon {
  min-width: 36px;
  height: 36px;
  padding: 0 8px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 16px;
}
.icon-text {
  font-size: 11px;
  font-weight: 700;
  color: var(--tf-text-on-accent);
  text-transform: uppercase;
  white-space: nowrap;
}

.project-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.project-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.project-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
}
.visibility-tag {
  font-size: 10px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 3px;
  white-space: nowrap;
}
.visibility-tag.vis-internal {
  background: var(--tf-accent-bg-light);
  color: var(--tf-accent);
}
.visibility-tag.vis-public {
  background: var(--tf-success-bg);
  color: var(--tf-success);
}
.project-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.member-avatars {
  display: flex;
  align-items: center;
  gap: 4px;
}

.member-overflow {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  font-weight: 500;
}

.btn-more {
  color: var(--tf-text-tertiary);
  transition: color 0.15s;
}
.btn-more:hover {
  color: var(--tf-text-primary);
}

.dropdown-wrapper {
  display: inline-flex;
}

.load-more {
  text-align: center;
  padding: 16px 0 32px;
}

/* 模板选项样式 */
.template-option {
  padding: 8px 12px;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: border-color 0.15s, background 0.15s;
}
.template-option:hover {
  border-color: var(--tf-accent);
}
.template-option.selected {
  border-color: var(--tf-accent);
  background: var(--tf-accent-bg);
}
.template-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.template-name {
  font-size: 13px;
  color: var(--tf-text-primary);
  font-weight: 500;
}
.template-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* Arco Design 暗色主题适配 */
:deep(.arco-dropdown-option.danger-option) {
  color: var(--tf-danger);
}

/* ===== 活动日志 ===== */
.activity-empty {
  text-align: center;
  padding: 48px 16px;
}

.activity-list {
  max-height: 400px;
  overflow-y: auto;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-1);
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-icon {
  font-size: 16px;
  flex-shrink: 0;
  width: 24px;
  text-align: center;
}

.activity-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.activity-text {
  font-size: 13px;
  color: var(--tf-text-primary);
  line-height: 1.5;
}

.activity-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* ===== 已归档项目区域 ===== */
.archived-section {
  margin-top: 24px;
  border-top: 1px solid var(--tf-border);
  padding-top: 16px;
}

.archived-header {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 6px;
  transition: background 0.15s;
  user-select: none;
}

.archived-header:hover {
  background: var(--tf-bg-hover);
}

.archived-toggle-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  color: var(--tf-text-tertiary);
  transition: transform 0.2s;
  font-size: 12px;
}

.archived-toggle-icon.expanded {
  transform: rotate(90deg);
}

.archived-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.archived-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
}

.archived-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.archived-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  color: var(--tf-text-tertiary);
  font-size: 12px;
}

.archived-row {
  opacity: 0.75;
  cursor: default;
}

.archived-row:hover {
  opacity: 1;
}

.archived-icon {
  opacity: 0.6;
}

.archived-name {
  color: var(--tf-text-secondary);
}

.archived-badge {
  font-size: 10px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--tf-muted-bg);
  color: var(--tf-text-tertiary);
  white-space: nowrap;
}

/* 删除项目确认弹窗 */
.delete-confirm-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.delete-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--tf-danger-bg);
  border: 1px solid var(--tf-danger-medium);
  border-radius: var(--tf-radius-md);
  font-size: 13px;
  color: var(--tf-danger);
}

.delete-warning .warning-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.delete-impact {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.impact-title {
  margin: 0 0 8px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.impact-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.impact-list li {
  font-size: 13px;
}

.impact-warn {
  color: var(--tf-danger);
  font-weight: 500;
}

.delete-confirm-input {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.delete-confirm-input p {
  margin: 0 0 8px;
}

.delete-confirm-input strong {
  color: var(--tf-text-primary);
  font-weight: 600;
}

/* 复制项目弹窗 */
.copy-source-info {
  padding: 12px 16px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
}

.copy-source-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.copy-source-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.copy-source-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.copy-source-key {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.copy-options-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.copy-options-list :deep(.arco-checkbox) {
  width: 100%;
}

.copy-option-text {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.copy-option-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-left: 8px;
}
</style>
