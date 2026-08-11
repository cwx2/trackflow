<template>
  <!--
    ===== TimeEntryDialog — 添加/编辑工时弹窗 =====
    通过 defineExpose 的 open(date?) / openEdit(entry) 方法触发。
    保存或删除成功后 emit 'saved' / 'deleted' 通知父组件刷新。
  -->
  <a-modal
    v-model:visible="visible"
    :title="editingEntry ? '编辑工时' : '添加花费的时间'"
    :width="560"
    :footer="false"
    @cancel="close"
  >
    <div class="time-dialog">
      <!-- 问题 -->
      <div class="dialog-field">
        <label class="dialog-label">问题</label>
        <a-select
          v-model="form.issueId"
          placeholder="Select an option"
          allow-search
          :options="issueOptions"
          @search="searchIssues"
        />
      </div>

      <!-- 作者 -->
      <div class="dialog-field">
        <label class="dialog-label">作者</label>
        <template v-if="canLogForOthers && !editingEntry">
          <a-select
            v-model="form.forUserId"
            placeholder="选择用户（默认为自己）"
            allow-search
            allow-clear
            :filter-option="false"
            @search="searchUsersForDialog"
            @clear="form.forUserId = undefined"
          >
            <a-option
              v-for="u in dialogSelectableUsers"
              :key="u.id"
              :value="u.id"
              :label="u.displayName || u.username"
            >
              <div class="user-option">
                <UserAvatar :name="u.displayName || u.username" :size="20" />
                <span class="user-option-name">{{ u.displayName || u.username }}</span>
                <span v-if="u.id === currentUserId" class="user-option-self">(我)</span>
              </div>
            </a-option>
          </a-select>
        </template>
        <template v-else>
          <div class="author-display">
            <UserAvatar :name="currentUserName" :size="24" shape="square" />
            <span class="author-name">{{ currentUserName }}</span>
          </div>
        </template>
      </div>

      <!-- 单一日期 / 日期范围 切换 -->
      <div class="dialog-field">
        <div class="date-mode-toggle">
          <button class="date-mode-btn" :class="{ active: dateMode === 'single' }" @click="dateMode = 'single'">单一日期</button>
          <button class="date-mode-btn" :class="{ active: dateMode === 'range' }" @click="dateMode = 'range'">日期范围</button>
        </div>
      </div>

      <!-- 日期 + 实际用时 -->
      <div class="dialog-row">
        <div class="dialog-field flex-1">
          <label class="dialog-label">日期</label>
          <a-date-picker v-if="dateMode === 'single'" v-model="form.workDate" style="width: 100%" />
          <a-range-picker v-else v-model="form.dateRange" style="width: 100%" />
        </div>
        <div class="dialog-field flex-1">
          <label class="dialog-label">实际用时</label>
          <a-input v-model="form.durationText" placeholder="1周 1天 1时 1分">
            <template #prefix>⏱</template>
          </a-input>
        </div>
      </div>

      <!-- 添加另一个记录 -->
      <div v-if="!editingEntry" class="add-another">
        <a class="add-another-link" @click="addAnotherRecord">+ 添加另一个记录</a>
      </div>

      <!-- 额外记录列表 -->
      <div v-if="extraRecords.length > 0" class="extra-records">
        <div v-for="(rec, idx) in extraRecords" :key="idx" class="extra-record-row">
          <a-date-picker v-model="rec.workDate" style="width: 45%" size="small" />
          <a-input v-model="rec.durationText" placeholder="时长" style="width: 40%" size="small" />
          <button class="remove-record-btn" @click="extraRecords.splice(idx, 1)">✕</button>
        </div>
      </div>

      <!-- 工作项属性（动态加载） -->
      <div v-for="attr in projectAttributes" :key="attr.id" class="dialog-field">
        <label class="dialog-label">{{ attr.name }}</label>
        <a-select v-model="formAttributeValues[attr.id]" :placeholder="`选择${attr.name}`" allow-clear>
          <a-option v-for="val in attr.values" :key="val.id" :value="val.id">
            <span v-if="val.color" class="attr-value-dot" :style="{ background: val.color }"></span>
            {{ val.name }}
          </a-option>
        </a-select>
      </div>

      <!-- 描述 -->
      <div class="dialog-field">
        <label class="dialog-label">描述</label>
        <a-textarea
          v-model="form.description"
          placeholder="描述这段时间您做了什么"
          :auto-size="{ minRows: 3, maxRows: 6 }"
        />
      </div>

      <!-- 底部按钮 -->
      <div class="dialog-actions">
        <div class="actions-left">
          <a-button v-if="editingEntry" status="danger" :loading="deleting" @click="handleDelete">删除</a-button>
        </div>
        <div class="actions-right">
          <a-button @click="close">取消</a-button>
          <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
/**
 * TimeEntryDialog — 添加/编辑工时弹窗
 *
 * 职责：
 * - 添加工时：支持单日/日期范围模式，按工作日平均分配
 * - 编辑工时：回显现有记录，支持修改和删除
 * - 动态加载工单所属项目的自定义属性
 *
 * 对外接口：
 * - defineExpose: open(date?) 打开新建弹窗，openEdit(entry) 打开编辑弹窗
 * - Emits: 'saved' 保存成功后通知父组件刷新，'deleted' 删除成功后通知
 *
 * Props（运行时依赖）：
 *   currentUserId    — 当前登录用户 ID，用于判断是否为本人记录
 *   currentUserName  — 当前用户展示名
 *   canLogForOthers  — 是否可以代他人录入
 *   canEditOthers    — 是否可以编辑他人记录
 *   minutesPerDay    — 每工作日分钟数（来自系统设置，用于解析 "1d"）
 */
import { ref, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { timeEntryApi, issueApi } from '@/api'
import { workItemAttributeApi } from '@/api/timeEntry'
import type { TimeEntryVO, WorkItemAttributeVO } from '@/api/timeEntry'
import { toDateKey, getWorkingDaysInRange, parseDuration, parseTimeToMinutes, formatDurationCompact } from '@/utils/timesheet'
import { UserAvatar } from '@/components/base'

const props = defineProps<{
  currentUserId: string | undefined
  currentUserName: string
  canLogForOthers: boolean
  canEditOthers: boolean
  minutesPerDay: number
}>()

const emit = defineEmits<{
  saved: []
  deleted: []
}>()

// ===== 弹窗状态 =====
const visible = ref(false)
const saving = ref(false)
const deleting = ref(false)
const editingEntry = ref<TimeEntryVO | null>(null)

// ===== 表单状态 =====
const dateMode = ref<'single' | 'range'>('single')
const extraRecords = ref<{ workDate: string; durationText: string }[]>([])
const form = ref({
  issueId:      undefined as string | undefined,
  workDate:     '',
  dateRange:    undefined as [string, string] | undefined,
  durationText: '',
  startTimeStr: undefined as string | undefined,
  description:  '',
  forUserId:    undefined as string | undefined,
})

// ===== 工单搜索 =====
const issueOptions = ref<{ value: string; label: string }[]>([])

async function searchIssues(keyword: string) {
  try {
    const params: Record<string, any> = { pageSize: 15 }
    if (keyword?.length >= 1) params.keyword = keyword
    const res = await issueApi.list(params)
    if (res.code === 0 && res.data) {
      issueOptions.value = res.data.list.map(i => ({
        value: i.id,
        label: `${i.issueKey} - ${i.title}`,
      }))
    }
  } catch (e) {
    console.error('[TimeEntryDialog] 搜索工单失败:', e)
  }
}

// ===== 代他人录入 - 用户选择 =====
const dialogSelectableUsers = ref<{ id: string; username: string; displayName?: string }[]>([])

async function searchUsersForDialog(keyword: string) {
  try {
    const res = await (await import('@/api/user')).userApi.list({ keyword, pageSize: 20 })
    if (res.code === 0 && res.data) {
      dialogSelectableUsers.value = res.data.list || res.data as any
    }
  } catch (e) {
    console.error('[TimeEntryDialog] 搜索用户失败:', e)
  }
}

// ===== 工作项属性 =====
const projectAttributes = ref<WorkItemAttributeVO[]>([])
const formAttributeValues = ref<Record<string, string>>({})
const loadedAttributeProjectId = ref<string | null>(null)

async function loadAttributesForIssue(issueId: string) {
  try {
    const issueRes = await issueApi.getById(issueId)
    if (issueRes.code === 0 && issueRes.data?.projectId) {
      const projectId = issueRes.data.projectId
      if (loadedAttributeProjectId.value === projectId) return
      loadedAttributeProjectId.value = projectId
      const attrRes = await workItemAttributeApi.listByProject(projectId)
      if (attrRes.code === 0 && attrRes.data) {
        projectAttributes.value = attrRes.data
      }
    }
  } catch (e) {
    console.error('[TimeEntryDialog] 加载工单属性失败:', e)
  }
}

// 工单切换时自动加载属性
watch(() => form.value.issueId, (newId) => {
  if (newId) {
    loadAttributesForIssue(newId)
  } else {
    projectAttributes.value = []
    formAttributeValues.value = {}
    loadedAttributeProjectId.value = null
  }
})

// ===== 表单重置 =====
function resetForm(date?: string) {
  editingEntry.value = null
  dateMode.value = 'single'
  extraRecords.value = []
  formAttributeValues.value = {}
  loadedAttributeProjectId.value = null
  projectAttributes.value = []
  form.value = {
    issueId:      undefined,
    workDate:     date || toDateKey(new Date()),
    dateRange:    undefined,
    durationText: '',
    startTimeStr: undefined,
    description:  '',
    forUserId:    undefined,
  }
}

// ===== 公开方法 =====
function open(date?: string) {
  resetForm(date)
  searchIssues('')
  if (props.canLogForOthers && dialogSelectableUsers.value.length === 0) {
    searchUsersForDialog('')
  }
  visible.value = true
}

function openEdit(entry: TimeEntryVO) {
  const isOwnEntry = entry.userId === props.currentUserId
  if (!isOwnEntry && !props.canEditOthers) {
    Message.warning('无权编辑他人工时记录')
    return
  }
  resetForm()
  editingEntry.value = entry
  dateMode.value = 'single'

  // 回显属性值
  if (entry.attributeValues) {
    for (const av of entry.attributeValues) {
      formAttributeValues.value[av.attributeId] = av.valueId
    }
  }

  // 回显表单
  form.value = {
    issueId:      entry.issueId,
    workDate:     entry.workDate,
    dateRange:    undefined,
    durationText: formatDurationCompact(entry.duration ?? 0),
    startTimeStr: entry.startTime != null
      ? `${String(Math.floor(entry.startTime / 60)).padStart(2, '0')}:${String(entry.startTime % 60).padStart(2, '0')}`
      : undefined,
    description:  entry.description || '',
    forUserId:    undefined,
  }

  // 确保工单在下拉选项里
  if (entry.issueKey) {
    const existing = issueOptions.value.find(o => o.value === entry.issueId)
    if (!existing) {
      const label = entry.issueDeleted
        ? `[已删除] ${entry.issueKey} - ${entry.issueTitle || ''}`
        : `${entry.issueKey} - ${entry.issueTitle || ''}`
      issueOptions.value = [{ value: entry.issueId, label }, ...issueOptions.value]
    }
  } else if (entry.issueDeleted) {
    const existing = issueOptions.value.find(o => o.value === entry.issueId)
    if (!existing) {
      issueOptions.value = [{ value: entry.issueId, label: '[已删除工单]' }, ...issueOptions.value]
    }
  }

  loadAttributesForIssue(entry.issueId)
  visible.value = true
}

function close() {
  visible.value = false
  editingEntry.value = null
}

function addAnotherRecord() {
  extraRecords.value.push({ workDate: form.value.workDate, durationText: '' })
}

// ===== 保存 =====
async function handleSave() {
  // 校验
  if (dateMode.value === 'single') {
    if (!form.value.issueId || !form.value.workDate || !form.value.durationText) {
      Message.warning('请填写工单、日期和时长')
      return
    }
  } else {
    if (!form.value.issueId || !form.value.dateRange?.[0] || !form.value.dateRange?.[1] || !form.value.durationText) {
      Message.warning('请填写工单、日期范围和时长')
      return
    }
  }

  const totalDuration = parseDuration(form.value.durationText, props.minutesPerDay)
  if (!totalDuration || totalDuration <= 0) {
    Message.warning('时长格式无效，请使用如 2h30m, 1h, 45m')
    return
  }

  const startTime = form.value.startTimeStr ? parseTimeToMinutes(form.value.startTimeStr) : undefined
  const attrValues = Object.keys(formAttributeValues.value).length > 0
    ? Object.fromEntries(Object.entries(formAttributeValues.value).filter(([, v]) => v))
    : undefined

  saving.value = true
  try {
    if (dateMode.value === 'single') {
      if (editingEntry.value) {
        await timeEntryApi.update(editingEntry.value.id, {
          issueId: form.value.issueId,
          workDate: form.value.workDate,
          duration: totalDuration,
          startTime,
          description: form.value.description || undefined,
          attributeValues: attrValues,
        })
        Message.success('工时已更新')
      } else {
        await timeEntryApi.create({
          issueId: form.value.issueId,
          workDate: form.value.workDate,
          duration: totalDuration,
          startTime,
          description: form.value.description || undefined,
          forUserId: form.value.forUserId || undefined,
          attributeValues: attrValues,
        })
        Message.success('工时已添加')
      }
    } else {
      // 日期范围模式：按工作日平均分配
      const workingDays = getWorkingDaysInRange(form.value.dateRange![0], form.value.dateRange![1])
      if (workingDays.length === 0) {
        Message.warning('所选日期范围内没有工作日')
        return
      }
      const durationPerDay = Math.round(totalDuration / workingDays.length)
      if (durationPerDay <= 0) {
        Message.warning('每日分配时长过小，请增加总时长或缩小日期范围')
        return
      }
      for (const day of workingDays) {
        await timeEntryApi.create({
          issueId: form.value.issueId,
          workDate: day,
          duration: durationPerDay,
          startTime,
          description: form.value.description || undefined,
          forUserId: form.value.forUserId || undefined,
          attributeValues: attrValues,
        })
      }
      Message.success(`已为 ${workingDays.length} 个工作日分别创建工时记录`)
    }
    close()
    emit('saved')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

// ===== 删除 =====
async function handleDelete() {
  if (!editingEntry.value) return
  Modal.warning({
    title: '确认删除',
    content: '确定要删除这条工时记录吗？此操作不可撤销。',
    okText: '删除',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      deleting.value = true
      try {
        await timeEntryApi.delete(editingEntry.value!.id)
        Message.success('工时已删除')
        close()
        emit('deleted')
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      } finally {
        deleting.value = false
      }
    },
  })
}

defineExpose({ open, openEdit })
</script>

<style scoped>
.time-dialog { padding: 4px 0; }
.dialog-field { margin-bottom: 16px; }
.dialog-label { display: block; font-size: 12px; color: var(--tf-text-tertiary); margin-bottom: 6px; font-weight: 500; }
.dialog-row { display: flex; gap: 12px; }
.flex-1 { flex: 1; }

.author-display { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--tf-bg-surface); border: 1px solid var(--tf-border-light); border-radius: var(--tf-radius-md); }
.author-name { font-size: 13px; color: var(--tf-text-primary); }

.date-mode-toggle { display: inline-flex; border: 1px solid var(--tf-border); border-radius: var(--tf-radius-md); overflow: hidden; }
.date-mode-btn { padding: 6px 14px; font-size: 12px; border: none; background: transparent; color: var(--tf-text-secondary); cursor: pointer; transition: background 0.15s, color 0.15s; }
.date-mode-btn.active { background: var(--tf-bg-elevated); color: var(--tf-text-primary); font-weight: 500; }
.date-mode-btn + .date-mode-btn { border-left: 1px solid var(--tf-border); }

.add-another { margin-bottom: 16px; }
.add-another-link { font-size: 13px; color: var(--tf-accent); cursor: pointer; text-decoration: none; }
.add-another-link:hover { text-decoration: underline; }

.extra-records { margin-bottom: 16px; display: flex; flex-direction: column; gap: 8px; }
.extra-record-row { display: flex; align-items: center; gap: 8px; }
.remove-record-btn { width: 24px; height: 24px; border: none; background: transparent; color: var(--tf-text-tertiary); cursor: pointer; font-size: 14px; border-radius: 4px; display: flex; align-items: center; justify-content: center; }
.remove-record-btn:hover { background: var(--tf-bg-hover); color: var(--tf-text-primary); }

.dialog-actions { display: flex; justify-content: space-between; align-items: center; padding-top: 16px; border-top: 1px solid var(--tf-border-light); }
.actions-left { display: flex; gap: 8px; }
.actions-right { display: flex; gap: 8px; }

.user-option { display: flex; align-items: center; gap: 8px; }
.user-option-name { font-size: 13px; }
.user-option-self { font-size: 11px; color: var(--tf-text-tertiary); }

.attr-value-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; }
</style>
