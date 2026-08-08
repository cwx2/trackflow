<template>
  <div class="estimation-report">
    <!-- 筛选栏 -->
    <div class="report-filters">
      <a-select
        v-model="selectedProjectId"
        placeholder="全部项目"
        allow-clear
        size="small"
        style="width: 180px"
        @change="handleProjectChange"
      >
        <a-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-option>
      </a-select>
      <a-button size="small" type="outline" @click="exportCSV" :disabled="!reportData">
        <template #icon><icon-download /></template>
        导出 CSV
      </a-button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="report-loading">
      <a-skeleton :animation="true" v-for="i in 3" :key="i" style="margin-bottom: 16px">
        <a-skeleton-line :rows="3" :widths="['50%', '80%', '30%']" />
      </a-skeleton>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!reportData || reportData.pagination.total === 0" class="report-empty">
      <div class="empty-icon">📐</div>
      <h3 class="empty-title">暂无预估数据</h3>
      <p class="empty-desc">没有找到设置了预估工时的工单。请先为工单设置"预估工时"字段。</p>
    </div>

    <!-- 报表主体 -->
    <template v-else>
      <!-- 总体概览 -->
      <div class="overview-cards">
        <div class="stat-card">
          <div class="stat-value">{{ reportData.totalEstimatedHours.toFixed(1) }}h</div>
          <div class="stat-label">预估总工时</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ reportData.totalSpentHours.toFixed(1) }}h</div>
          <div class="stat-label">实际总工时</div>
        </div>
        <div class="stat-card" :class="deviationClass(reportData.overallDeviationRate)">
          <div class="stat-value">{{ formatDeviationRate(reportData.overallDeviationRate) }}</div>
          <div class="stat-label">总体偏差</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ reportData.pagination.total }}</div>
          <div class="stat-label">有预估的工单</div>
        </div>
      </div>

      <!-- 图表 -->
      <div class="chart-grid">
        <!-- 按项目汇总对比 -->
        <div class="chart-card chart-card-wide" v-if="reportData.byProject.length > 0">
          <div class="chart-card-header">
            <h3 class="chart-title">按项目对比</h3>
            <span class="chart-subtitle">每个项目的预估 vs 实际工时</span>
          </div>
          <div class="chart-body">
            <v-chart :option="projectComparisonOption" autoresize class="chart-instance" />
          </div>
        </div>

        <!-- 工单偏差明细表 -->
        <div class="chart-card chart-card-wide">
          <div class="chart-card-header">
            <h3 class="chart-title">工单预估偏差明细</h3>
            <span class="chart-subtitle">按偏差比率排序（高→低），正值=超时，负值=提前完成</span>
          </div>
          <div class="estimation-table">
            <div class="est-row est-header">
              <span class="est-cell est-key">工单</span>
              <span class="est-cell est-title-col">标题</span>
              <span class="est-cell est-assignee">负责人</span>
              <span class="est-cell est-num">预估</span>
              <span class="est-cell est-num">实际</span>
              <span class="est-cell est-num">偏差</span>
              <span class="est-cell est-status-col">状态</span>
            </div>
            <div v-for="item in reportData.items" :key="item.issueId" class="est-row">
              <span class="est-cell est-key">{{ item.issueKey }}</span>
              <span class="est-cell est-title-col" :title="item.issueTitle">{{ item.issueTitle }}</span>
              <span class="est-cell est-assignee">{{ item.assigneeName }}</span>
              <span class="est-cell est-num">{{ item.estimatedHours.toFixed(1) }}h</span>
              <span class="est-cell est-num">{{ item.spentHours.toFixed(1) }}h</span>
              <span class="est-cell est-num" :class="deviationClass(item.deviationRate)">
                {{ formatDeviationRate(item.deviationRate) }}
              </span>
              <span class="est-cell est-status-col">
                <span class="deviation-badge" :class="'badge-' + item.deviation">
                  {{ deviationLabel(item.deviation) }}
                </span>
              </span>
            </div>
          </div>
          <!-- 分页 -->
          <div class="estimation-pagination" v-if="reportData.pagination && reportData.pagination.totalPages > 1">
            <a-pagination
              :current="currentPage"
              :page-size="currentPageSize"
              :total="reportData.pagination.total"
              :page-size-options="[20, 50, 100]"
              show-total
              show-page-size
              size="small"
              @change="handlePageChange"
              @page-size-change="handlePageSizeChange"
            />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { IconDownload } from '@arco-design/web-vue/es/icon'
import { reportStatisticsApi } from '@/api/reportStatistics'
import type { EstimationReportData } from '@/api/reportStatistics'
import type { ProjectVO } from '@/api/types'
import { readChartThemeColors } from '@/utils/chartColors'

use([CanvasRenderer, BarChart, TooltipComponent, GridComponent, LegendComponent])

const props = defineProps<{
  projects: ProjectVO[]
}>()

const loading = ref(false)
const reportData = ref<EstimationReportData | null>(null)
const selectedProjectId = ref<string | undefined>(undefined)
const currentPage = ref(1)
const currentPageSize = ref(50)

const projects = computed(() => props.projects)

// ─── 主题色 ─────────────────────────────

const chartColors = computed(() => readChartThemeColors())

const projectComparisonOption = computed(() => {
  if (!reportData.value || !reportData.value.byProject.length) return {}
  const items = reportData.value.byProject
  const c = chartColors.value
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText },
      formatter: (params: any) => {
        const idx = params[0]?.dataIndex
        if (idx == null) return ''
        const item = items[idx]
        return `<strong>${item.projectName}</strong><br/>` +
          `预估: ${item.estimatedHours.toFixed(1)}h<br/>` +
          `实际: ${item.spentHours.toFixed(1)}h<br/>` +
          `偏差: ${formatDeviationRate(item.deviationRate)}<br/>` +
          `工单数: ${item.issueCount}`
      }
    },
    legend: {
      data: ['预估工时', '实际工时'],
      right: 20,
      top: 0,
      textStyle: { color: c.textColor, fontSize: 11 },
      itemWidth: 14,
      itemHeight: 10
    },
    grid: { left: 100, right: 30, top: 36, bottom: 20 },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: c.textColor, fontSize: 11, formatter: (v: number) => `${v}h` },
      splitLine: { lineStyle: { color: c.axisColor, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: items.map(i => i.projectName),
      axisLine: { lineStyle: { color: c.axisColor } },
      axisLabel: { color: c.textColor, fontSize: 11, width: 90, overflow: 'truncate' },
      axisTick: { show: false }
    },
    series: [
      {
        name: '预估工时',
        type: 'bar',
        barWidth: '35%',
        barGap: '10%',
        data: items.map(i => i.estimatedHours),
        itemStyle: { color: '#58a6ff', borderRadius: [0, 3, 3, 0] }
      },
      {
        name: '实际工时',
        type: 'bar',
        barWidth: '35%',
        data: items.map(i => i.spentHours),
        itemStyle: { color: '#3fb950', borderRadius: [0, 3, 3, 0] }
      }
    ]
  }
})

// ─── 工具方法 ─────────────────────────────

function formatDeviationRate(rate: number): string {
  const pct = Math.round(rate * 100)
  if (pct > 0) return `+${pct}%`
  return `${pct}%`
}

function deviationClass(rate: number): string {
  if (Math.abs(rate) <= 0.1) return 'deviation-ok'
  if (rate > 0) return 'deviation-over'
  return 'deviation-under'
}

function deviationLabel(d: string): string {
  if (d === 'over') return '超时'
  if (d === 'under') return '提前'
  return '正常'
}

// ─── 数据加载 ─────────────────────────────

async function loadData() {
  loading.value = true
  reportData.value = null
  try {
    const res = await reportStatisticsApi.estimationReport(selectedProjectId.value, currentPage.value, currentPageSize.value)
    reportData.value = res.data
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载预估报表失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function handlePageSizeChange(pageSize: number) {
  currentPageSize.value = pageSize
  currentPage.value = 1
  loadData()
}

function handleProjectChange() {
  currentPage.value = 1
  loadData()
}

// ─── 导出 CSV ─────────────────────────────

function exportCSV() {
  if (!reportData.value) return

  const data = reportData.value
  const lines: string[] = []
  const BOM = '\uFEFF'

  lines.push('=== 预估对比报表 ===')
  lines.push(`预估总工时,${data.totalEstimatedHours.toFixed(1)}h`)
  lines.push(`实际总工时,${data.totalSpentHours.toFixed(1)}h`)
  lines.push(`总体偏差,${formatDeviationRate(data.overallDeviationRate)}`)
  lines.push('')

  lines.push('=== 按项目汇总 ===')
  lines.push('项目,预估(h),实际(h),偏差,工单数')
  data.byProject.forEach(item => {
    lines.push(`${item.projectName},${item.estimatedHours.toFixed(1)},${item.spentHours.toFixed(1)},${formatDeviationRate(item.deviationRate)},${item.issueCount}`)
  })
  lines.push('')

  lines.push('=== 工单明细 ===')
  lines.push('工单Key,标题,负责人,预估(h),实际(h),偏差,状态')
  data.items.forEach(item => {
    lines.push(`${item.issueKey},"${item.issueTitle}",${item.assigneeName},${item.estimatedHours.toFixed(1)},${item.spentHours.toFixed(1)},${formatDeviationRate(item.deviationRate)},${deviationLabel(item.deviation)}`)
  })

  const csvContent = BOM + lines.join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `TrackFlow_预估对比报表_${new Date().toISOString().substring(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  Message.success('CSV 导出成功')
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData })
</script>

<style scoped>
.estimation-report {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.report-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.report-loading,
.report-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--tf-text-secondary);
  margin: 0;
  max-width: 320px;
  line-height: 1.5;
}

/* 概览卡片 */
.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.stat-card {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--tf-text-primary);
  line-height: 1.2;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.deviation-ok .stat-value { color: var(--tf-success); }
.deviation-over .stat-value { color: var(--tf-danger); }
.deviation-under .stat-value { color: var(--tf-accent); }

/* 图表 */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.chart-card {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.chart-card-wide {
  grid-column: 1 / -1;
}

.chart-card-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}

.chart-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0;
}

.chart-subtitle {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.chart-body {
  flex: 1;
  min-height: 0;
}

.chart-instance {
  width: 100%;
  height: 240px;
}

/* 估算表格 */
.estimation-table {
  display: flex;
  flex-direction: column;
  max-height: 400px;
  overflow-y: auto;
}

.estimation-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--tf-border-light);
  margin-top: 8px;
}

.est-row {
  display: grid;
  grid-template-columns: 80px 1fr 90px 60px 60px 60px 60px;
  padding: 7px 8px;
  font-size: 12px;
  color: var(--tf-text-primary);
  border-radius: 4px;
  align-items: center;
}

.est-row:hover:not(.est-header) {
  background: var(--tf-bg-hover);
}

.est-header {
  color: var(--tf-text-tertiary);
  font-weight: 500;
  font-size: 11px;
  border-bottom: 1px solid var(--tf-border-light);
  margin-bottom: 2px;
}

.est-key {
  font-weight: 500;
  color: var(--tf-accent);
}

.est-title-col {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.est-assignee {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.est-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.est-status-col {
  text-align: center;
}

.deviation-badge {
  display: inline-block;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
}

.badge-over {
  background: var(--tf-danger-bg);
  color: var(--tf-danger);
}

.badge-under {
  background: var(--tf-accent-bg-light);
  color: var(--tf-accent);
}

.badge-on_track {
  background: var(--tf-success-bg);
  color: var(--tf-success);
}

.deviation-ok .est-num { color: var(--tf-success); }
.deviation-over .est-num { color: var(--tf-danger); }
.deviation-under .est-num { color: var(--tf-accent); }
</style>
