<template>
  <div class="settings-page">
    <h2 class="page-title">账号安全</h2>

    <!-- API Key / Permanent Token Section -->
    <div class="settings-section">
      <div class="section-header">
        <div>
          <h3 class="section-title">API Key / 永久令牌</h3>
          <p class="section-desc">
            创建永久访问令牌，用于 API 调用和第三方集成。令牌拥有与您相同的权限。
          </p>
        </div>
        <a-button type="primary" size="small" @click="showCreateDialog = true">
          <template #icon><icon-plus /></template>
          创建令牌
        </a-button>
      </div>

      <!-- Loading State -->
      <a-spin v-if="loading" :loading="true" style="width: 100%; padding: 48px 0" />

      <!-- Empty State -->
      <EmptyState v-else-if="tokens.length === 0" title="暂无访问令牌" description="创建一个永久访问令牌，用于 API 调用、CI/CD 集成或脚本自动化。">
        <template #icon><icon-lock style="font-size: 48px;" /></template>
        <template #action>
          <a-button type="primary" size="small" @click="showCreateDialog = true">创建第一个令牌</a-button>
        </template>
      </EmptyState>
      </div>

      <!-- Token List Table -->
      <div v-else class="token-table-wrapper">
        <a-table
          :data="tokens"
          :pagination="false"
          :bordered="false"
          row-key="id"
          size="small"
        >
          <template #columns>
            <a-table-column title="名称" data-index="name" :width="180">
              <template #cell="{ record }">
                <div class="token-name-cell">
                  <icon-key class="token-icon" />
                  <span class="token-name">{{ record.name }}</span>
                </div>
              </template>
            </a-table-column>
            <a-table-column title="前缀" data-index="prefix" :width="120">
              <template #cell="{ record }">
                <code class="token-prefix">{{ record.prefix }}...</code>
              </template>
            </a-table-column>
            <a-table-column title="权限范围" data-index="permissions" :width="160">
              <template #cell="{ record }">
                <span class="token-permissions">
                  {{ record.permissions || '全部权限' }}
                </span>
              </template>
            </a-table-column>
            <a-table-column title="创建时间" data-index="createdAt" :width="160">
              <template #cell="{ record }">
                <span class="token-time">{{ formatTime(record.createdAt) }}</span>
              </template>
            </a-table-column>
            <a-table-column title="最近使用" data-index="lastUsedAt" :width="160">
              <template #cell="{ record }">
                <span class="token-time">
                  {{ record.lastUsedAt ? formatTime(record.lastUsedAt) : '从未使用' }}
                </span>
              </template>
            </a-table-column>
            <a-table-column title="过期时间" :width="140">
              <template #cell="{ record }">
                <span :class="['token-time', { 'token-expired': isExpired(record.expiresAt) }]">
                  {{ record.expiresAt ? formatTime(record.expiresAt) : '永不过期' }}
                </span>
              </template>
            </a-table-column>
            <a-table-column title="操作" :width="80" align="center">
              <template #cell="{ record }">
                <a-button
                  type="text"
                  status="danger"
                  size="mini"
                  @click="handleRevoke(record)"
                >
                  撤销
                </a-button>
              </template>
            </a-table-column>
          </template>
        </a-table>
      </div>
    </div>

    <!-- Change Password Section -->
    <div class="settings-section">
      <div class="section-header">
        <div>
          <h3 class="section-title">修改密码</h3>
          <p class="section-desc">
            密码由公司统一身份系统 (Keycloak) 管理。如需修改密码，请前往 Keycloak 账户中心操作。
          </p>
        </div>
      </div>
      <div class="password-action">
        <a
          :href="keycloakPasswordUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="keycloak-link"
        >
          <icon-export class="link-icon" />
          前往 Keycloak 账户中心修改密码
        </a>
      </div>
    </div>

    <!-- Security Tips -->
    <div class="settings-section">
      <h3 class="section-title">安全提示</h3>
      <ul class="security-tips">
        <li>令牌拥有与您账号相同的权限，请妥善保管</li>
        <li>令牌创建后仅显示一次，无法再次查看完整内容</li>
        <li>如果令牌泄露，请立即撤销并创建新令牌</li>
        <li>建议为不同用途创建独立令牌，便于管理和撤销</li>
      </ul>
    </div>

    <!-- Navigation -->
    <div class="settings-footer">
      <router-link to="/settings/profile" class="footer-link">
        ← 个人设置
      </router-link>
    </div>

    <!-- Create Token Dialog -->
    <a-modal
      v-model:visible="showCreateDialog"
      title="创建访问令牌"
      :width="480"
      :mask-closable="false"
      :footer="createdToken ? false : undefined"
      @cancel="resetCreateForm"
      @ok="handleCreate"
      :ok-loading="creating"
      ok-text="创建令牌"
    >
      <!-- Creation Form -->
      <template v-if="!createdToken">
        <a-form :model="createForm" layout="vertical">
          <a-form-item label="令牌名称" required>
            <a-input
              v-model="createForm.name"
              placeholder="例如：CI/CD Pipeline、本地开发"
              :max-length="100"
              allow-clear
            />
            <template #extra>
              <span class="form-extra">为令牌取一个有意义的名称，方便识别用途</span>
            </template>
          </a-form-item>
          <a-form-item label="过期时间">
            <a-select v-model="createForm.expiresOption" placeholder="选择过期时间">
              <a-option value="never">永不过期</a-option>
              <a-option value="7d">7 天</a-option>
              <a-option value="30d">30 天</a-option>
              <a-option value="90d">90 天</a-option>
              <a-option value="180d">180 天</a-option>
              <a-option value="365d">1 年</a-option>
            </a-select>
            <template #extra>
              <span class="form-extra">建议设置过期时间以增强安全性</span>
            </template>
          </a-form-item>
        </a-form>
      </template>

      <!-- Token Created Success -->
      <template v-else>
        <div class="token-created-result">
          <div class="success-header">
            <icon-check-circle-fill class="success-icon" />
            <span class="success-title">令牌创建成功</span>
          </div>
          <a-alert type="warning" class="token-warning">
            请立即复制令牌。关闭此对话框后将无法再次查看完整令牌内容。
          </a-alert>
          <div class="token-display">
            <label class="token-display-label">您的访问令牌</label>
            <div class="token-value-row">
              <code class="token-value">{{ createdToken.key }}</code>
              <a-button size="mini" type="outline" @click="copyToken">
                <template #icon><icon-copy /></template>
                {{ copied ? '已复制' : '复制' }}
              </a-button>
            </div>
          </div>
          <div class="token-meta">
            <div class="meta-item">
              <span class="meta-label">名称</span>
              <span class="meta-value">{{ createdToken.name }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">前缀</span>
              <code class="meta-value">{{ createdToken.prefix }}</code>
            </div>
            <div class="meta-item">
              <span class="meta-label">过期时间</span>
              <span class="meta-value">{{ createdToken.expiresAt ? formatTime(createdToken.expiresAt) : '永不过期' }}</span>
            </div>
          </div>
          <div class="dialog-close-actions">
            <a-button type="primary" @click="closeCreateDialog">我已保存令牌</a-button>
          </div>
        </div>
      </template>
    </a-modal>

    <!-- Revoke Confirmation Dialog -->
    <a-modal
      v-model:visible="showRevokeDialog"
      title="撤销访问令牌"
      :width="420"
      :ok-loading="revoking"
      ok-text="确认撤销"
      :ok-button-props="{ status: 'danger' }"
      @ok="confirmRevoke"
      @cancel="revokeTarget = null"
    >
      <div class="revoke-content">
        <p class="revoke-warning">
          撤销令牌后，使用该令牌的所有 API 调用将立即失效。此操作不可撤销。
        </p>
        <div class="revoke-target" v-if="revokeTarget">
          <span class="revoke-label">令牌名称：</span>
          <strong>{{ revokeTarget.name }}</strong>
        </div>
        <div class="revoke-target" v-if="revokeTarget">
          <span class="revoke-label">令牌前缀：</span>
          <code>{{ revokeTarget.prefix }}...</code>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { apiKeyApi } from '@/api'
import { EmptyState } from '@/components/base'
import { KEYCLOAK_CONFIG } from '@/utils/keycloak'
import type { ApiKeyVO, ApiKeyCreatedVO } from '@/api/types'

// State
const loading = ref(false)
const tokens = ref<ApiKeyVO[]>([])

// Keycloak password change URL
const keycloakPasswordUrl = computed(
  () => `${KEYCLOAK_CONFIG.authority}/account/#/security/signingin`
)

// Create dialog
const showCreateDialog = ref(false)
const creating = ref(false)
const createdToken = ref<ApiKeyCreatedVO | null>(null)
const copied = ref(false)
const createForm = ref({
  name: '',
  expiresOption: 'never'
})

// Revoke dialog
const showRevokeDialog = ref(false)
const revoking = ref(false)
const revokeTarget = ref<ApiKeyVO | null>(null)

// Load tokens
async function loadTokens() {
  loading.value = true
  try {
    const res = await apiKeyApi.list()
    if (res.code === 0) {
      tokens.value = res.data || []
    }
  } catch {
    Message.error('加载令牌列表失败')
  } finally {
    loading.value = false
  }
}

// Create token
async function handleCreate() {
  if (!createForm.value.name.trim()) {
    Message.warning('请输入令牌名称')
    return
  }

  creating.value = true
  try {
    const expiresAt = computeExpiresAt(createForm.value.expiresOption)
    const res = await apiKeyApi.create({
      name: createForm.value.name.trim(),
      expiresAt: expiresAt || undefined
    })
    if (res.code === 0) {
      createdToken.value = res.data
      // Reload list in background
      loadTokens()
    } else {
      Message.error(res.message || '创建失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建令牌失败')
  } finally {
    creating.value = false
  }
}

function computeExpiresAt(option: string): string | null {
  if (option === 'never') return null
  const days = parseInt(option)
  if (isNaN(days)) return null
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().replace('Z', '')
}

function resetCreateForm() {
  if (!createdToken.value) {
    createForm.value = { name: '', expiresOption: 'never' }
  }
}

function closeCreateDialog() {
  showCreateDialog.value = false
  createdToken.value = null
  createForm.value = { name: '', expiresOption: 'never' }
  copied.value = false
}

// Copy token
async function copyToken() {
  if (!createdToken.value) return
  try {
    await navigator.clipboard.writeText(createdToken.value.key)
    copied.value = true
    Message.success('令牌已复制到剪贴板')
    setTimeout(() => { copied.value = false }, 3000)
  } catch {
    Message.error('复制失败，请手动选择复制')
  }
}

// Revoke token
function handleRevoke(token: ApiKeyVO) {
  revokeTarget.value = token
  showRevokeDialog.value = true
}

async function confirmRevoke() {
  if (!revokeTarget.value) return
  revoking.value = true
  try {
    const res = await apiKeyApi.revoke(revokeTarget.value.id)
    if (res.code === 0) {
      Message.success('令牌已撤销')
      tokens.value = tokens.value.filter(t => t.id !== revokeTarget.value!.id)
      showRevokeDialog.value = false
      revokeTarget.value = null
    } else {
      Message.error(res.message || '撤销失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '撤销令牌失败')
  } finally {
    revoking.value = false
  }
}

// Format time
function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function isExpired(expiresAt?: string): boolean {
  if (!expiresAt) return false
  return new Date(expiresAt) < new Date()
}

onMounted(() => {
  loadTokens()
})
</script>

<style scoped>
.settings-page {
  padding: 32px;
  max-width: 900px;
  overflow-y: auto;
  height: 100%;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin-bottom: 24px;
}

.settings-section {
  margin-bottom: 32px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
}

.section-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  line-height: 1.4;
  margin: 0;
}

/* Empty State */
/* Token Table */
.token-table-wrapper {
  border: 1px solid var(--tf-border-light);
  border-radius: 6px;
  overflow: hidden;
}

.token-table-wrapper :deep(.arco-table) {
  background: transparent;
}

.token-table-wrapper :deep(.arco-table-th) {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.token-table-wrapper :deep(.arco-table-td) {
  font-size: 12px;
  border-color: var(--tf-border-light);
}

.token-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.token-icon {
  color: var(--tf-text-tertiary);
  font-size: 14px;
  flex-shrink: 0;
}

.token-name {
  font-weight: 500;
  color: var(--tf-text-primary);
}

.token-prefix {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  color: var(--tf-text-secondary);
  background: var(--tf-bg-surface);
  padding: 2px 6px;
  border-radius: 3px;
}

.token-permissions {
  font-size: 11px;
  color: var(--tf-text-secondary);
}

.token-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.token-expired {
  color: var(--tf-danger);
}

/* Security Tips */
.security-tips {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.security-tips li {
  font-size: 12px;
  color: var(--tf-text-secondary);
  padding-left: 16px;
  position: relative;
  line-height: 1.5;
}

.security-tips li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: var(--tf-text-tertiary);
}

/* Password Section */
.password-action {
  padding: 16px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: 6px;
}

.keycloak-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--tf-text-accent);
  text-decoration: none;
  transition: opacity 150ms;
}

.keycloak-link:hover {
  opacity: 0.8;
  text-decoration: underline;
}

.link-icon {
  font-size: 14px;
}

/* Settings Footer */
.settings-footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--tf-border-light);
}

.footer-link {
  font-size: 12px;
  color: var(--tf-text-accent);
  text-decoration: none;
  transition: opacity 150ms;
}

.footer-link:hover {
  opacity: 0.8;
}

/* Create Dialog - Form */
.form-extra {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* Create Dialog - Success Result */
.token-created-result {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.success-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.success-icon {
  color: var(--tf-success);
  font-size: 20px;
}

.success-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.token-warning {
  margin: 0;
}

.token-display {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.token-display-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.token-value-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.token-value {
  flex: 1;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: var(--tf-text-primary);
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: 4px;
  padding: 8px 12px;
  word-break: break-all;
  line-height: 1.4;
}

.token-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--tf-bg-surface);
  border-radius: 6px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.meta-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  width: 60px;
  flex-shrink: 0;
}

.meta-value {
  font-size: 12px;
  color: var(--tf-text-primary);
}

.dialog-close-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}

/* Revoke Dialog */
.revoke-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.revoke-warning {
  font-size: 13px;
  color: var(--tf-text-secondary);
  line-height: 1.5;
  margin: 0;
}

.revoke-target {
  font-size: 12px;
  color: var(--tf-text-primary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.revoke-label {
  color: var(--tf-text-tertiary);
}
</style>
