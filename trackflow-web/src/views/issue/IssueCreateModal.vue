<template>
  <a-modal
    :visible="true"
    title="创建工单"
    :width="640"
    ok-text="创建"
    cancel-text="取消"
    :ok-loading="submitting"
    :ok-button-props="{ disabled: !form.projectId || !form.title }"
    @ok="submit"
    @cancel="$emit('close')"
  >
    <a-form :model="form" layout="vertical" :style="{ maxHeight: '60vh', overflow: 'auto' }">
      <!-- 项目选择 -->
      <a-form-item label="项目" required>
        <a-select
          v-model="form.projectId"
          placeholder="选择项目..."
          allow-search
        >
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
      </a-form-item>

      <!-- 标题 -->
      <a-form-item label="标题" required>
        <a-input v-model="form.title" placeholder="工单概要..." allow-clear />
      </a-form-item>

      <!-- 描述 -->
      <a-form-item label="描述">
        <a-textarea
          v-model="form.description"
          placeholder="描述问题详情..."
          :auto-size="{ minRows: 3, maxRows: 8 }"
        />
      </a-form-item>

      <!-- 类型 + 优先级 -->
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="类型">
            <a-select v-model="form.issueType">
              <a-option value="Task">任务</a-option>
              <a-option value="Bug">缺陷</a-option>
              <a-option value="Feature">需求</a-option>
              <a-option value="Story">故事</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="优先级">
            <a-select v-model="form.priority">
              <a-option value="Critical">
                <span style="color: #ef4444">●</span>紧急
              </a-option>
              <a-option value="High">
                <span style="color: #f59e0b">●</span>高
              </a-option>
              <a-option value="Normal">
                <span style="color: #6366f1">●</span>普通
              </a-option>
              <a-option value="Low">
                <span style="color: #64748b">●</span>低
              </a-option>
            </a-select>
          </a-form-item>
        </a-col>
      </a-row>

      <!-- 负责人 + 截止日期 -->
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="负责人">
            <a-select
              v-model="form.assigneeId"
              placeholder="选择负责人..."
              allow-clear
              allow-search
            >
              <a-option v-for="m in members" :key="m.userId" :value="m.userId">
                {{ m.displayName || m.username || ('用户 ' + m.userId) }}
              </a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="截止日期">
            <a-date-picker v-model="form.dueDate" style="width: 100%" placeholder="选择日期" />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { projectApi, issueApi } from '@/api'

const emit = defineEmits(['close', 'created'])

const projects = ref<any[]>([])
const members = ref<any[]>([])
const submitting = ref(false)

const form = reactive({
  projectId: undefined as string | undefined,
  title: '',
  description: '',
  issueType: 'Task',
  priority: 'Normal',
  assigneeId: undefined as string | undefined,
  dueDate: ''
})

// 加载项目列表
async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    projects.value = res.data?.list || []
  } catch (e) {
    projects.value = []
  }
}

// 项目选择后加载成员列表
watch(() => form.projectId, async (projectId) => {
  if (!projectId) {
    members.value = []
    return
  }
  try {
    const res = await projectApi.listMembers(projectId)
    members.value = res.data || []
  } catch (e) {
    members.value = []
  }
})

async function submit() {
  if (!form.projectId || !form.title) return
  submitting.value = true
  try {
    const payload: any = {
      projectId: form.projectId,
      title: form.title,
      description: form.description || undefined,
      issueType: form.issueType,
      priority: form.priority,
      dueDate: form.dueDate || undefined
    }
    if (form.assigneeId) payload.assigneeId = form.assigneeId

    await issueApi.create(payload)
    Message.success('工单创建成功')
    emit('created')
    emit('close')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadProjects)
</script>
