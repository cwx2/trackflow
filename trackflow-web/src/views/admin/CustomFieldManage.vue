<template>
  <div class="cf-manage">
    <div class="cf-header">
      <h2 class="page-title">自定义字段管理</h2>
      <a-button type="primary" size="small" @click="openCreate">
        <template #icon><icon-plus /></template>
        创建自定义字段
      </a-button>
    </div>

    <!-- 字段列表 -->
    <div class="cf-table">
      <a-table
        :data="fieldList"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        size="small"
        @page-change="onPageChange"
      >
        <template #columns>
          <a-table-column title="字段名称" data-index="name" />
          <a-table-column title="类型" data-index="fieldFormat" :width="100">
            <template #cell="{ record }">
              <a-tag size="small">{{ formatTypeLabel(record.fieldFormat) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="必填" data-index="isRequired" :width="60" align="center">
            <template #cell="{ record }">
              <icon-check v-if="record.isRequired" style="color: var(--tf-success)" />
            </template>
          </a-table-column>
          <a-table-column title="全局" data-index="isForAll" :width="60" align="center">
            <template #cell="{ record }">
              <icon-check v-if="record.isForAll" style="color: var(--tf-accent)" />
            </template>
          </a-table-column>
          <a-table-column title="适用项目" :width="120">
            <template #cell="{ record }">
              <span v-if="record.isForAll" class="text-muted">所有项目</span>
              <span v-else class="text-muted">{{ (record.projectIds || []).length }} 个项目</span>
            </template>
          </a-table-column>
          <a-table-column title="适用类型" :width="140">
            <template #cell="{ record }">
              <span v-if="!record.issueTypes || record.issueTypes.length === 0" class="text-muted">所有类型</span>
              <span v-else>{{ record.issueTypes.join(', ') }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="120" align="center">
            <template #cell="{ record }">
              <a-button type="text" size="mini" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm content="删除后所有关联数据将永久移除，确定删除？" @ok="handleDelete(record.id)">
                <a-button type="text" size="mini" status="danger">删除</a-button>
              </a-popconfirm>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </div>

    <!-- 创建/编辑抽屉 -->
    <a-drawer
      :visible="drawerVisible"
      :title="editingId ? '编辑自定义字段' : '创建自定义字段'"
      :width="480"
      @cancel="drawerVisible = false"
      @ok="handleSave"
      :ok-loading="saving"
      unmount-on-close
    >
      <a-form :model="form" layout="vertical" size="small">
        <a-form-item label="字段名称" required>
          <a-input v-model="form.name" placeholder="例如: 到期版本" :max-length="256" />
        </a-form-item>

        <a-form-item label="字段类型" required>
          <a-select v-model="form.fieldFormat" :disabled="!!editingId" placeholder="选择字段类型">
            <a-option v-for="t in fieldTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-option>
          </a-select>
        </a-form-item>

        <a-form-item label="必填">
          <a-switch v-model="form.isRequired" />
        </a-form-item>

        <a-form-item label="全局可用">
          <a-switch v-model="form.isForAll" />
          <div class="form-help">开启后所有项目均可使用此字段</div>
        </a-form-item>

        <a-form-item label="默认值">
          <a-input v-model="form.defaultValue" placeholder="可选" />
        </a-form-item>

        <!-- string 类型额外配置 -->
        <template v-if="form.fieldFormat === 'string'">
          <a-form-item label="最小长度">
            <a-input-number v-model="form.minLength" :min="0" />
          </a-form-item>
          <a-form-item label="最大长度">
            <a-input-number v-model="form.maxLength" :min="0" />
          </a-form-item>
          <a-form-item label="正则验证">
            <a-input v-model="form.regexp" placeholder="可选正则表达式" />
          </a-form-item>
        </template>

        <!-- list 类型选项管理 -->
        <template v-if="form.fieldFormat === 'list'">
          <a-form-item label="选项列表">
            <div class="options-list">
              <div v-for="(opt, idx) in form.options" :key="idx" class="option-row">
                <a-input v-model="opt.value" placeholder="选项值" size="mini" style="flex:1" />
                <a-checkbox v-model="opt.isDefault" size="small">默认</a-checkbox>
                <a-button type="text" size="mini" status="danger" @click="form.options.splice(idx, 1)">
                  <icon-delete />
                </a-button>
              </div>
              <a-button type="dashed" size="mini" long @click="form.options.push({ value: '', isDefault: false })">
                <template #icon><icon-plus /></template>
                添加选项
              </a-button>
            </div>
          </a-form-item>
        </template>

        <!-- 项目关联（非全局时） -->
        <a-form-item v-if="!form.isForAll" label="关联项目">
          <a-checkbox-group v-model="form.projectIds">
            <a-checkbox v-for="p in projectList" :key="p.id" :value="p.id">{{ p.name }}</a-checkbox>
          </a-checkbox-group>
        </a-form-item>

        <!-- Issue 类型关联 -->
        <a-form-item label="适用 Issue 类型">
          <a-checkbox-group v-model="form.issueTypes">
            <a-checkbox value="Task">任务</a-checkbox>
            <a-checkbox value="Bug">缺陷</a-checkbox>
            <a-checkbox value="Feature">需求</a-checkbox>
          </a-checkbox-group>
          <div class="form-help">不选则适用所有类型</div>
        </a-form-item>
      </a-form>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { IconPlus, IconDelete, IconCheck } from '@arco-design/web-vue/es/icon'
import { Message } from '@arco-design/web-vue'
import { customFieldApi, projectApi } from '@/api'
import type { CustomFieldDefinitionVO } from '@/api/types'

const fieldList = ref<CustomFieldDefinitionVO[]>([])
const loading = ref(false)
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })
const projectList = ref<any[]>([])

// Drawer state
const drawerVisible = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)

const form = reactive({
  name: '',
  fieldFormat: 'string' as string,
  isRequired: false,
  isForAll: false,
  defaultValue: '',
  minLength: 0,
  maxLength: 0,
  regexp: '',
  options: [] as Array<{ value: string; isDefault: boolean }>,
  projectIds: [] as string[],
  issueTypes: [] as string[]
})

const fieldTypeOptions = [
  { value: 'string', label: '文本' },
  { value: 'int', label: '整数' },
  { value: 'float', label: '小数' },
  { value: 'date', label: '日期' },
  { value: 'bool', label: '布尔' },
  { value: 'list', label: '列表(枚举)' },
  { value: 'user', label: '用户' }
]

function formatTypeLabel(format: string) {
  return fieldTypeOptions.find(t => t.value === format)?.label || format
}

async function loadList() {
  loading.value = true
  try {
    const res = await customFieldApi.list({ page: pagination.current, pageSize: pagination.pageSize })
    fieldList.value = res.data?.list || []
    pagination.total = res.data?.pagination?.total || 0
  } catch {
    fieldList.value = []
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    projectList.value = res.data?.list || []
  } catch {
    projectList.value = []
  }
}

function onPageChange(page: number) {
  pagination.current = page
  loadList()
}

function resetForm() {
  form.name = ''
  form.fieldFormat = 'string'
  form.isRequired = false
  form.isForAll = false
  form.defaultValue = ''
  form.minLength = 0
  form.maxLength = 0
  form.regexp = ''
  form.options = []
  form.projectIds = []
  form.issueTypes = []
}

function openCreate() {
  editingId.value = null
  resetForm()
  drawerVisible.value = true
}

function openEdit(record: CustomFieldDefinitionVO) {
  editingId.value = record.id
  form.name = record.name
  form.fieldFormat = record.fieldFormat
  form.isRequired = record.isRequired
  form.isForAll = record.isForAll
  form.defaultValue = record.defaultValue || ''
  form.minLength = record.minLength
  form.maxLength = record.maxLength
  form.regexp = record.regexp || ''
  form.options = (record.options || []).map(o => ({ value: o.value, isDefault: o.isDefault }))
  form.projectIds = record.projectIds || []
  form.issueTypes = record.issueTypes || []
  drawerVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    Message.warning('请输入字段名称')
    return
  }
  if (!form.fieldFormat) {
    Message.warning('请选择字段类型')
    return
  }

  saving.value = true
  try {
    if (editingId.value) {
      await customFieldApi.update(editingId.value, {
        name: form.name,
        isRequired: form.isRequired,
        isForAll: form.isForAll,
        defaultValue: form.defaultValue || undefined,
        minLength: form.minLength,
        maxLength: form.maxLength,
        regexp: form.regexp || undefined,
        options: form.fieldFormat === 'list' ? form.options : undefined,
        projectIds: form.isForAll ? [] : form.projectIds,
        issueTypes: form.issueTypes
      })
      Message.success('更新成功')
    } else {
      await customFieldApi.create({
        name: form.name,
        fieldFormat: form.fieldFormat,
        isRequired: form.isRequired,
        isForAll: form.isForAll,
        defaultValue: form.defaultValue || undefined,
        minLength: form.minLength,
        maxLength: form.maxLength,
        regexp: form.regexp || undefined,
        options: form.fieldFormat === 'list' ? form.options : undefined,
        projectIds: form.isForAll ? [] : form.projectIds,
        issueTypes: form.issueTypes
      })
      Message.success('创建成功')
    }
    drawerVisible.value = false
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  try {
    await customFieldApi.delete(id)
    Message.success('删除成功')
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

onMounted(() => {
  loadList()
  loadProjects()
})
</script>

<style scoped>
.cf-manage {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.cf-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.cf-table {
  background: var(--tf-bg-surface);
  border-radius: 6px;
  border: 1px solid var(--tf-border);
}

.text-muted {
  color: var(--tf-text-tertiary);
  font-size: 12px;
}

.form-help {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
