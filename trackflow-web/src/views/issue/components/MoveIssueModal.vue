<template>
  <a-modal
    :visible="visible"
    :title="`移动工单 ${issueKey} 到其他项目`"
    :width="480"
    :ok-text="'移动工单'"
    :ok-loading="submitting"
    :ok-button-props="{ disabled: !selectedProjectId }"
    @cancel="$emit('update:visible', false)"
    @ok="handleSubmit"
  >
    <div class="move-modal-body">
      <!-- 项目选择 -->
      <div class="field-group">
        <label class="field-label">目标项目</label>
        <a-select
          v-model="selectedProjectId"
          placeholder="选择目标项目"
          :loading="loadingProjects"
          allow-search
          :filter-option="filterProject"
        >
          <a-option
            v-for="proj in availableProjects"
            :key="proj.id"
            :value="proj.id"
            :label="proj.name"
          >
            <span class="project-option">
              <span class="project-key">{{ proj.key }}</span>
              <span class="project-name">{{ proj.name }}</span>
            </span>
          </a-option>
        </a-select>
      </div>

      <!-- 影响提示 -->
      <div class="impact-notice" v-if="selectedProjectId">
        <icon-info-circle class="notice-icon" />
        <div class="notice-content">
          <p class="notice-title">移动后将发生以下变化：</p>
          <ul class="notice-list">
            <li>工单将获得新的编号（目标项目前缀 + 递增序号）</li>
            <li>Sprint 字段将被清空（不同项目的迭代不通用）</li>
            <li>如果负责人不是目标项目成员，负责人将被清空</li>
            <li v-if="props.childCount && props.childCount > 0" class="notice-warning">
              该工单有 {{ props.childCount }} 个子任务，移动后子任务的父引用将被解除
            </li>
          </ul>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { IconInfoCircle } from '@arco-design/web-vue/es/icon'
import { projectApi } from '@/api'
import type { ProjectVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  issueKey: string
  currentProjectId: string
  childCount?: number
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  'confirm': [targetProjectId: string]
}>()

const selectedProjectId = ref('')
const loadingProjects = ref(false)
const submitting = ref(false)
const projects = ref<ProjectVO[]>([])

/** 排除当前项目，只显示活跃项目 */
const availableProjects = computed(() =>
  projects.value.filter(p => p.id !== props.currentProjectId && p.status === 'active')
)

function filterProject(inputValue: string, option: any) {
  const label = (option.label || '').toLowerCase()
  return label.includes(inputValue.toLowerCase())
}

/** 弹窗打开时加载项目列表 */
watch(() => props.visible, async (val) => {
  if (val) {
    selectedProjectId.value = ''
    await loadProjects()
  }
})

async function loadProjects() {
  loadingProjects.value = true
  try {
    // 加载所有项目（用户能看到的都加载，后端会做最终权限校验）
    const res = await projectApi.list({ pageSize: 200 })
    if (res.code === 0 && res.data) {
      projects.value = res.data.list || []
    }
  } catch {
    projects.value = []
  } finally {
    loadingProjects.value = false
  }
}

async function handleSubmit() {
  if (!selectedProjectId.value) return
  submitting.value = true
  emit('confirm', selectedProjectId.value)
}

/** 父组件调用此方法重置提交状态 */
function resetSubmitting() {
  submitting.value = false
}

defineExpose({ resetSubmitting })
</script>

<style scoped>
.move-modal-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.project-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 1px 5px;
  border-radius: 3px;
}

.project-name {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.impact-notice {
  display: flex;
  gap: 10px;
  padding: 12px;
  background: rgba(210, 153, 34, 0.06);
  border: 1px solid rgba(210, 153, 34, 0.2);
  border-radius: 6px;
}

.notice-icon {
  font-size: 16px;
  color: #d29922;
  flex-shrink: 0;
  margin-top: 1px;
}

.notice-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.notice-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  margin: 0;
}

.notice-list {
  margin: 0;
  padding-left: 16px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  line-height: 1.6;
}

.notice-list li {
  margin-bottom: 2px;
}

.notice-warning {
  color: #d29922;
  font-weight: 500;
}
</style>
