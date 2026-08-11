<template>
  <!--
    ===== TimesheetDateBar — 时间表日期导航栏 =====
    三个 Tab（人员/项目/工作群组）共用的顶部导航区：
    左侧显示当前日期范围和总工时，右侧提供上/下周(月)切换、周/月模式切换。
    人员 Tab 额外通过 #actions slot 插入「添加已花费时间」按钮。
  -->
  <div class="timesheet-datebar">
    <div class="date-info">
      <span class="date-range">{{ dateRangeLabel }}</span>
      <span class="total-time">{{ totalTimeLabel }}</span>
    </div>
    <div class="date-nav">
      <a-button size="small" @click="$emit('navigate', -1)">←</a-button>
      <a-button size="small" @click="$emit('goToday')">今天</a-button>
      <a-button size="small" @click="$emit('navigate', 1)">→</a-button>
      <a-radio-group
        :model-value="viewMode"
        type="button"
        size="small"
        @change="(v: string | number | boolean) => $emit('update:viewMode', v as 'week' | 'month')"
      >
        <a-radio value="week">周</a-radio>
        <a-radio value="month">月</a-radio>
      </a-radio-group>
      <!-- 额外操作按钮（如人员 Tab 的「添加工时」） -->
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * TimesheetDateBar — 时间表日期导航栏
 *
 * Props：
 *   dateRangeLabel  — 显示的日期范围文字（如 "2026/8/4 – 2026/8/10"）
 *   totalTimeLabel  — 当前范围总工时（如 "共 24h 30m"），空字符串则不显示
 *   viewMode        — 当前视图模式 'week' | 'month'
 *
 * Emits：
 *   navigate(delta)    — 前/后翻页，delta = -1 | 1
 *   goToday            — 跳转到今天
 *   update:viewMode    — 切换周/月模式（v-model:viewMode）
 *
 * Slots：
 *   #actions — 右侧额外按钮区（人员 Tab 用于放「添加已花费时间」）
 */
defineProps<{
  dateRangeLabel: string
  totalTimeLabel: string
  viewMode: 'week' | 'month'
}>()

defineEmits<{
  navigate: [delta: number]
  goToday: []
  'update:viewMode': [value: 'week' | 'month']
}>()
</script>

<style scoped>
.timesheet-datebar {
  padding: 8px 24px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.date-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.date-range {
  font-size: 15px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.total-time {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.date-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
