<template>
  <div class="settings-modules">
    <!-- 归档提示 -->
    <div v-if="isArchived" class="archived-notice">
      <icon-lock class="notice-icon" />
      <span>项目已归档，设置为只读状态</span>
    </div>

    <!-- 三态容器：loading / error / 内容 -->
    <DataContainer
      :loading="loading"
      :error="loadError"
      :retry="loadModules"
    >
      <!-- 说明 -->
      <div class="settings-section">
        <h3 class="section-title">功能模块</h3>
        <p class="section-desc">
          配置项目启用的功能模块。禁用的模块在该项目中不可用：对应的导航入口会隐藏，相关权限也不生效。
        </p>
      </div>

      <!-- 模块列表 -->
      <div class="module-list">
        <div
          v-for="mod in moduleList"
          :key="mod.name"
          class="module-item"
          :class="{ disabled: !mod.enabled, core: mod.isCore }"
        >
          <div class="module-info">
            <div class="module-header">
              <component :is="mod.icon" class="module-icon" />
              <span class="module-name">{{ mod.label }}</span>
              <a-tag v-if="mod.isCore" size="small" color="blue" class="core-tag">核心</a-tag>
            </div>
            <span class="module-desc">{{ mod.description }}</span>
          </div>
          <a-switch
            :model-value="mod.enabled"
            :disabled="mod.isCore || !canManage || isArchived || saving"
            @change="(val: any) => handleModuleToggle(mod.name, val as boolean)"
          />
        </div>
      </div>

      <!-- 保存提示 -->
      <div v-if="hasChanges" class="save-bar">
        <span class="save-hint">模块配置已修改，保存后生效</span>
        <div class="save-actions">
          <a-button @click="resetChanges">取消</a-button>
          <a-button type="primary" :loading="saving" @click="saveChanges">保存变更</a-button>
        </div>
      </div>
    </DataContainer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import {
  IconLock,
  IconFile,
  IconLoop,
  IconClockCircle,
  IconBarChart,
  IconThunderbolt,
  IconSearch,
  IconSettings
} from '@arco-design/web-vue/es/icon'
import { projectApi } from '@/api'
import type { ProjectDetailVO } from '@/api/types'
import { DataContainer } from '@/components/base'

const props = defineProps<{
  project: ProjectDetailVO
  canManage: boolean
  isArchived: boolean
}>()

// 模块元数据（名称、图标、描述）
const MODULE_META: Record<string, { label: string; icon: any; description: string }> = {
  issue: {
    label: '工单追踪',
    icon: IconFile,
    description: '工单的创建、编辑、分配、状态流转和搜索'
  },
  project: {
    label: '项目管理',
    icon: IconSettings,
    description: '项目设置、成员管理、自定义字段和工作流配置'
  },
  sprint: {
    label: 'Sprint 迭代',
    icon: IconLoop,
    description: '敏捷迭代管理，包括 Sprint 创建、计划和完成'
  },
  time_tracking: {
    label: '时间追踪',
    icon: IconClockCircle,
    description: '工时记录和计时器功能，支持预估和实际工时对比'
  },
  report: {
    label: '报表统计',
    icon: IconBarChart,
    description: '项目报表、燃尽图、工作量统计和状态分布'
  },
  integration: {
    label: '集成与 Webhook',
    icon: IconThunderbolt,
    description: 'Webhook 管理和外部系统集成'
  },
  query: {
    label: '保存查询',
    icon: IconSearch,
    description: '自定义查询条件保存和共享'
  }
}

const loading = ref(true)
const loadError = ref<string | null>(null)
const saving = ref(false)
const allModules = ref<string[]>([])
const coreModules = ref<string[]>([])
const enabledModules = ref<Set<string>>(new Set())
const originalEnabled = ref<Set<string>>(new Set())

const hasChanges = computed(() => {
  if (enabledModules.value.size !== originalEnabled.value.size) return true
  for (const m of enabledModules.value) {
    if (!originalEnabled.value.has(m)) return true
  }
  return false
})

const moduleList = computed(() => {
  return allModules.value.map(name => ({
    name,
    label: MODULE_META[name]?.label || name,
    icon: MODULE_META[name]?.icon || IconFile,
    description: MODULE_META[name]?.description || '',
    isCore: coreModules.value.includes(name),
    enabled: enabledModules.value.has(name)
  }))
})

function handleModuleToggle(moduleName: string, enabled: boolean) {
  if (enabled) {
    enabledModules.value.add(moduleName)
  } else {
    enabledModules.value.delete(moduleName)
  }
  // Trigger reactivity
  enabledModules.value = new Set(enabledModules.value)
}

function resetChanges() {
  enabledModules.value = new Set(originalEnabled.value)
}

async function saveChanges() {
  saving.value = true
  try {
    const res = await projectApi.updateEnabledModules(props.project.id, {
      enabledModules: Array.from(enabledModules.value)
    })
    if (res.code === 0) {
      originalEnabled.value = new Set(res.data.enabledModules)
      enabledModules.value = new Set(res.data.enabledModules)
      Message.success('模块配置已保存')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function loadModules() {
  loading.value = true
  loadError.value = null
  try {
    const res = await projectApi.getEnabledModules(props.project.id)
    if (res.code === 0) {
      allModules.value = res.data.allModules
      coreModules.value = res.data.coreModules
      enabledModules.value = new Set(res.data.enabledModules)
      originalEnabled.value = new Set(res.data.enabledModules)
    }
  } catch (e: any) {
    loadError.value = e.response?.data?.message || '加载模块配置失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadModules()
})
</script>

<style scoped>
.settings-modules {
  padding: 0;
}

.archived-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--tf-bg-warning-subtle, rgba(217, 153, 34, 0.1));
  border-radius: 6px;
  margin-bottom: 24px;
  color: var(--tf-text-secondary);
  font-size: 13px;
}

.archived-notice .notice-icon {
  color: var(--color-warning-6);
  font-size: 16px;
}

.settings-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px 0;
}

.section-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
  line-height: 1.5;
}

.module-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.module-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-radius: 8px;
  transition: background 150ms;
}

.module-item:hover {
  background: var(--tf-bg-hover);
}

.module-item.disabled {
  opacity: 0.7;
}

.module-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.module-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.module-icon {
  font-size: 16px;
  color: var(--tf-text-secondary);
}

.module-item.disabled .module-icon {
  color: var(--tf-text-quaternary);
}

.module-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.module-item.disabled .module-name {
  color: var(--tf-text-tertiary);
}

.core-tag {
  font-size: 11px;
}

.module-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  padding-left: 24px;
}

.save-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-top: 24px;
  background: var(--tf-bg-elevated);
  border-radius: 8px;
  border: 1px solid var(--tf-border-default);
}

.save-hint {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.save-actions {
  display: flex;
  gap: 8px;
}
</style>
