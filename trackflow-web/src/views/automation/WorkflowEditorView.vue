<template>
  <div class="workflow-editor-view">
    <!-- 顶部工具栏 -->
    <EditorTopbar
      v-model:name="workflowName"
      :saving="saving"
      :publishing="publishing"
      :status="workflowStatus"
      :runtime-enabled="workflowRuntimeEnabled"
      :runtime-changing="runtimeChanging"
      @back="goBack"
      @save="handleSave"
      @settings="settingsOpen = true"
      @publish="handlePublish"
      @start="handleStartRuntime"
      @stop="handleStopRuntime"
    />

    <!-- 编辑器主体：画布 + 悬浮面板 -->
    <div class="editor-content">
      <!-- 世界坐标水印：复用 LogicFlow 的变换矩阵，跟随画布平移和缩放 -->
      <div
        v-if="watermarkReady"
        class="canvas-world-layer"
        :style="{ transform: canvasWorldTransform }"
        aria-hidden="true"
      >
        <div
          class="canvas-world-watermark-anchor"
          :style="{
            left: `${watermarkCanvasPosition.x}px`,
            top: `${watermarkCanvasPosition.y}px`,
            transform: `scale(${watermarkCounterScale})`
          }"
        >
          <div class="canvas-world-watermark">TrackFlow</div>
        </div>
      </div>
      <!-- 画布（全屏） -->
      <div ref="containerRef" class="canvas-container"></div>
      <!-- 左侧悬浮：节点面板 -->
      <div class="node-panel" :class="{ collapsed: !leftPanelOpen }">
        <!-- 收起/展开 tab -->
        <button class="panel-toggle panel-toggle-left" @click="leftPanelOpen = !leftPanelOpen">
          <span>{{ leftPanelOpen ? '◀' : '▶' }}</span>
        </button>

        <div class="panel-inner">
          <!-- 搜索框 -->
          <div class="node-search-wrap">
            <a-input
              v-model="nodeSearchKeyword"
              placeholder="搜索节点..."
              size="small"
              allow-clear
              class="node-search-input"
            >
              <template #prefix><span class="search-icon">🔍</span></template>
            </a-input>
          </div>

          <!-- 按分类展示 -->
          <div class="panel-scroll">
            <template v-if="nodeSearchKeyword">
              <!-- 搜索结果 -->
              <div class="panel-section">
                <div v-if="filteredNodes.length === 0" class="no-search-result">无匹配节点</div>
                <div
                  v-for="node in filteredNodes"
                  :key="node.type"
                  class="node-item"
                  :style="{ '--node-color': node.color }"
                  @mousedown="(e) => onDragStart(e, node)"
                >
                  <div class="node-item-icon">{{ node.icon }}</div>
                  <div class="node-item-body">
                    <div class="node-item-name">{{ node.label }}</div>
                    <div class="node-item-desc">{{ node.desc }}</div>
                  </div>
                </div>
              </div>
            </template>
            <template v-else>
              <!-- 按分类分组 -->
              <div
                v-for="category in nodeCategories"
                :key="category.name"
                class="panel-section"
              >
                <div
                  class="section-title section-title-clickable"
                  @click="toggleCategory(category.name)"
                >
                  <span>{{ category.name }}</span>
                  <span class="category-arrow">{{ collapsedCategories.has(category.name) ? '▶' : '▼' }}</span>
                </div>
                <template v-if="!collapsedCategories.has(category.name)">
                  <div
                    v-for="node in category.nodes"
                    :key="node.type"
                    class="node-item"
                    :style="{ '--node-color': node.color }"
                    @mousedown="(e) => onDragStart(e, node)"
                  >
                    <div class="node-item-icon">{{ node.icon }}</div>
                    <div class="node-item-body">
                      <div class="node-item-name">{{ node.label }}</div>
                      <div class="node-item-desc">{{ node.desc }}</div>
                    </div>
                  </div>
                </template>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 右侧悬浮：配置面板 -->
      <div class="config-panel" :class="{ collapsed: !rightPanelOpen }">
        <!-- 收起/展开 tab -->
        <button class="panel-toggle panel-toggle-right" @click="rightPanelOpen = !rightPanelOpen">
          <span>{{ rightPanelOpen ? '▶' : '◀' }}</span>
        </button>

        <div class="panel-inner">
          <template v-if="selectedNode">
            <div class="panel-header">
              <span class="panel-title">{{ getNodeTitle(selectedNode.properties?.nodeType) }}</span>
              <a-button type="text" size="small" status="danger" @click="deleteSelectedNode">删除</a-button>
            </div>
            <CliAgentConfig v-if="selectedNode.properties?.nodeType === 'cli-agent'" v-model:data="selectedNode.properties" />
            <VariablesConfig v-else-if="selectedNode.properties?.nodeType === 'variables'" v-model:data="selectedNode.properties" />
            <ConditionConfig v-else-if="selectedNode.properties?.nodeType === 'condition'" v-model:data="selectedNode.properties" />
            <LoopConfig v-else-if="selectedNode.properties?.nodeType === 'loop'" v-model:data="selectedNode.properties" />
            <FileInputConfig v-else-if="selectedNode.properties?.nodeType === 'file-input'" v-model:data="selectedNode.properties" />
            <DelayConfig v-else-if="selectedNode.properties?.nodeType === 'delay'" v-model:data="selectedNode.properties" />
            <CodeConfig v-else-if="selectedNode.properties?.nodeType === 'code'" v-model:data="selectedNode.properties" />
            <HttpRequestConfig v-else-if="selectedNode.properties?.nodeType === 'http-request'" v-model:data="selectedNode.properties" />
            <SubWorkflowConfig v-else-if="selectedNode.properties?.nodeType === 'sub-workflow'" v-model:data="selectedNode.properties" />
            <GenericNodeConfig
              v-else
              v-model:data="selectedNode.properties"
              :definition="getNodeDefinition(selectedNode.properties?.nodeType)"
            />
          </template>
          <template v-else>
            <div class="panel-header">
              <span class="panel-title">全局变量</span>
            </div>
            <GlobalVariablesConfig v-model:variables="globalVariables" />
          </template>
        </div>
      </div>

      <!-- 底部工具条 -->
      <BottomToolbar
        :zoom-percent="zoomPercent"
        :minimap-open="minimapOpen"
        :debug-mode="debugMode"
        :is-running="isRunning"
        @zoom-in="zoomIn"
        @zoom-out="zoomOut"
        @fit="fitCanvas"
        @zoom-to="zoomTo"
        @add-comment="addCommentNode"
        @auto-layout="autoLayout"
        @export-image="exportImage"
        @toggle-minimap="toggleMinimap"
        @toggle-node-panel="toggleAddNodePanel"
        @toggle-debug="toggleDebugMode"
        @run="handleRun"
        @cancel="handleCancelRun"
      />

      <!-- 执行日志浮层（可折叠） -->
      <div v-if="executionPanelOpen" class="execution-overlay">
        <ExecutionPanel
          :node-status-map="nodeStatusMap"
          :streaming-output="streamingOutput"
          :is-running="isRunning"
          @close="executionPanelOpen = false"
        />
      </div>
    </div>

    <a-modal v-model:visible="settingsOpen" title="自动运行设置" :width="520" @ok="saveSettings">
      <a-alert type="info" class="runtime-settings-alert">
        保存和发布都不会启动自动化。发布成功后，请在顶部点击“启动”；停止后不再接收新触发，已有任务会继续完成。
      </a-alert>
      <a-form :model="settingsModel" layout="vertical">
        <a-form-item label="所属项目 ID">
          <a-input-number v-model="workflowProjectId" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="执行身份（用户 ID）">
          <a-input-number v-model="workflowActorUserId" :min="1" style="width: 100%" />
          <div class="settings-hint">所有 TrackFlow 写操作都按该用户的真实权限与状态机执行。</div>
        </a-form-item>
        <a-form-item label="触发方式">
          <a-select v-model="workflowTriggerType">
            <a-option value="manual">手动</a-option>
            <a-option value="schedule">定时</a-option>
            <a-option value="issue_created">需求创建</a-option>
            <a-option value="issue_changed">需求字段变化</a-option>
            <a-option value="webhook">Webhook</a-option>
          </a-select>
        </a-form-item>
        <template v-if="workflowTriggerType === 'schedule'">
          <a-form-item label="Cron 表达式">
            <a-input v-model="triggerCron" placeholder="0 0 9 * * * 或 hourly/daily/weekly" />
          </a-form-item>
          <a-form-item label="时区">
            <a-input v-model="triggerTimezone" placeholder="Asia/Shanghai" />
          </a-form-item>
        </template>
        <a-form-item v-if="workflowTriggerType === 'issue_changed'" label="监听字段">
          <a-input v-model="triggerFields" placeholder="status_id,priority,assignee" />
          <div class="settings-hint">多个字段用英文逗号分隔；留空表示监听所有字段。</div>
        </a-form-item>
        <a-form-item v-if="workflowTriggerType === 'issue_changed'" label="允许自动化再次触发">
          <a-switch v-model="allowAutomationEvents" />
          <div class="settings-hint">默认关闭，防止状态变更工作流递归触发自身。</div>
        </a-form-item>
        <a-form-item v-if="workflowTriggerType === 'webhook'" label="Token SHA-256">
          <a-input v-model="triggerWebhookTokenSha256" placeholder="64 位十六进制 SHA-256" />
          <div class="settings-hint">调用方传原始 Token 到 X-TrackFlow-Webhook-Token，并提供 X-Idempotency-Key。</div>
        </a-form-item>
        <a-form-item label="并发策略">
          <a-select v-model="workflowConcurrencyMode">
            <a-option value="queue">排队</a-option>
            <a-option value="skip">已有执行时跳过</a-option>
            <a-option value="parallel">允许并行</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="最大并发数">
          <a-input-number v-model="workflowMaxConcurrent" :min="1" :max="50" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { pauseTracking, resetTracking } from '@vue/reactivity'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import LogicFlow from '@logicflow/core'
import { Control, MiniMap, Snapshot } from '@logicflow/extension'
import { automationApi, type WorkflowDefinition, type NodeType, type GlobalVariable } from '@/api'
import { DRAGGABLE_NODES, getNodeDefinition } from './node-definitions'
import { FlowEdge } from './graph/edges/FlowEdge'
import { registerAllNodes } from './graph/nodes/index'
import CliAgentConfig from './components/CliAgentConfig.vue'
import VariablesConfig from './components/VariablesConfig.vue'
import ConditionConfig from './components/ConditionConfig.vue'
import LoopConfig from './components/LoopConfig.vue'
import FileInputConfig from './components/FileInputConfig.vue'
import DelayConfig from './components/DelayConfig.vue'
import CodeConfig from './components/CodeConfig.vue'
import HttpRequestConfig from './components/HttpRequestConfig.vue'
import SubWorkflowConfig from './components/SubWorkflowConfig.vue'
import GenericNodeConfig from './components/GenericNodeConfig.vue'
import GlobalVariablesConfig from './components/GlobalVariablesConfig.vue'
import ExecutionPanel from './components/ExecutionPanel.vue'
import BottomToolbar from './components/BottomToolbar.vue'
import EditorTopbar from './components/EditorTopbar.vue'

// LogicFlow 样式
import '@logicflow/core/dist/index.css'
import '@logicflow/extension/lib/style/index.css'

const route = useRoute()
const router = useRouter()

// DOM 引用
const containerRef = ref<HTMLElement | null>(null)

// 水印使用画布世界坐标，并与 LogicFlow 共用同一组平移/缩放矩阵
const watermarkReady = ref(false)
const watermarkCanvasPosition = ref({ x: 0, y: 0 })
const canvasWorldTransform = ref('matrix(1, 0, 0, 1, 0, 0)')
const watermarkCounterScale = ref(1)

// LogicFlow 实例
let lf: LogicFlow | null = null

// 工作流数据
const workflowId = ref('')
const workflowName = ref('加载中...')
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const runtimeChanging = ref(false)
const workflowStatus = ref<'draft' | 'published' | 'disabled'>('draft')
const workflowRuntimeEnabled = ref(false)
const workflowVersion = ref(1)
const workflowProjectId = ref<number>()
const workflowActorUserId = ref<number>()
const workflowTriggerType = ref('manual')
const workflowConcurrencyMode = ref<'queue' | 'skip' | 'parallel'>('queue')
const workflowMaxConcurrent = ref(1)
const triggerCron = ref('0 0 9 * * *')
const triggerTimezone = ref('Asia/Shanghai')
const triggerFields = ref('')
const allowAutomationEvents = ref(false)
const triggerWebhookTokenSha256 = ref('')
const settingsOpen = ref(false)
const settingsModel = computed(() => ({
  projectId: workflowProjectId.value,
  actorUserId: workflowActorUserId.value,
  triggerType: workflowTriggerType.value,
  concurrencyMode: workflowConcurrencyMode.value,
  maxConcurrent: workflowMaxConcurrent.value,
}))

// 全局变量和选中节点
const globalVariables = ref<Record<string, GlobalVariable>>({})
const selectedNode = ref<any>(null)

watch(selectedNode, value => {
  if (!lf || !value?.id || !value.properties) return
  lf.setProperties(value.id, JSON.parse(JSON.stringify(value.properties)))
}, { deep: true })

// 面板开关
const leftPanelOpen = ref(true)
const rightPanelOpen = ref(false)  // 默认收起，点击节点时自动打开

// 执行状态
type CanvasNodeStatus = 'idle' | 'running' | 'success' | 'failed' | 'skipped' | 'cancelled'
const nodeStatusMap = ref<Record<string, CanvasNodeStatus>>({})
const streamingOutput = ref<Record<string, string>>({})
const isRunning = ref(false)
const currentExecutionId = ref<string | null>(null)

// 底部工具栏
const executionPanelOpen = ref(false)
const zoomPercent = ref(100)

// 新增：minimap / 调试 / 添加节点面板 状态
const minimapOpen = ref(false)
const debugMode = ref(false)

function syncCanvasWorldTransform() {
  const transform = (lf as any)?.graphModel?.transformModel
  if (!transform) return

  canvasWorldTransform.value = `matrix(${[
    transform.SCALE_X,
    transform.SKEW_Y,
    transform.SKEW_X,
    transform.SCALE_Y,
    transform.TRANSLATE_X,
    transform.TRANSLATE_Y
  ].join(', ')})`

  // 抵消大部分画布缩放，只保留轻微的动态增减（约 82%～118%）
  const graphScale = Math.max(transform.SCALE_X, 0.01)
  const adaptiveVisualScale = Math.min(1.18, Math.max(0.82, Math.pow(graphScale, 0.12)))
  watermarkCounterScale.value = adaptiveVisualScale / graphScale
}

function fitCanvas() {
  lf?.fitView()
  nextTick(() => {
    const transform = (lf as any)?.graphModel?.transformModel
    if (transform) {
      zoomPercent.value = Math.round(transform.SCALE_X * 100)
    }
  })
}

function zoomIn() {
  lf?.zoom(true)
}

function zoomOut() {
  lf?.zoom(false)
}

function zoomTo(percent: number) {
  const scale = percent / 100
  lf?.zoom(scale)
  zoomPercent.value = percent
}

// ── 工具条功能函数 ─────────────────────────────────────────

/** 2. 添加注释节点（放置在画布中央可见区域） */
function addCommentNode() {
  if (!lf) return
  const graphModel = (lf as any).graphModel
  // 获取当前视口中心
  const { width, height } = graphModel
  const transform = graphModel.transformModel
  const centerX = (width / 2 - transform.translateX) / transform.SCALE_X
  const centerY = (height / 2 - transform.translateY) / transform.SCALE_Y

  lf.addNode({
    type: 'comment',
    x: centerX,
    y: centerY,
    properties: {
      nodeType: 'comment',
      text: '在这里写注释...',
    },
  })
  Message.success('注释节点已添加')
}

/** 3. 优化布局：对齐 + 均匀间距（从左到右拓扑排序） */
function autoLayout() {
  if (!lf) return
  const graphData = lf.getGraphData() as { nodes: any[]; edges: any[] }
  if (!graphData.nodes.length) {
    Message.warning('画布中没有节点')
    return
  }

  // 构建邻接表
  const inDegree = new Map<string, number>()
  const adj = new Map<string, string[]>()
  for (const n of graphData.nodes) {
    inDegree.set(n.id, 0)
    adj.set(n.id, [])
  }
  for (const e of graphData.edges) {
    adj.get(e.sourceNodeId)?.push(e.targetNodeId)
    inDegree.set(e.targetNodeId, (inDegree.get(e.targetNodeId) || 0) + 1)
  }

  // Kahn 拓扑排序 -> 分层
  const queue: string[] = []
  for (const [id, deg] of inDegree) {
    if (deg === 0) queue.push(id)
  }
  const layers: string[][] = []
  while (queue.length) {
    layers.push([...queue])
    const next: string[] = []
    for (const id of queue) {
      for (const nb of (adj.get(id) || [])) {
        const deg = (inDegree.get(nb) || 1) - 1
        inDegree.set(nb, deg)
        if (deg === 0) next.push(nb)
      }
    }
    queue.length = 0
    queue.push(...next)
  }

  // 有环节点放最后一层
  const placed = new Set(layers.flat())
  const remaining = graphData.nodes.map(n => n.id).filter(id => !placed.has(id))
  if (remaining.length) layers.push(remaining)

  // 布局：水平分层，每层内垂直居中
  const COL_GAP = 280   // 列间距
  const ROW_GAP = 140   // 行间距
  const START_X = 100
  const START_Y = 100

  const updates: { id: string; x: number; y: number }[] = []
  layers.forEach((layer, li) => {
    const totalH = layer.length * ROW_GAP
    layer.forEach((id, ri) => {
      updates.push({
        id,
        x: START_X + li * COL_GAP,
        y: START_Y + ri * ROW_GAP - totalH / 2 + ROW_GAP / 2,
      })
    })
  })

  // 批量移动节点
  for (const { id, x, y } of updates) {
    lf.moveNode(id, x, y)
  }

  lf.fitView()
  Message.success('布局已优化')
}

/** 4. 导出为图片（PNG）*/
function exportImage() {
  if (!lf) return
  const snapshot = (lf as any).extension?.snapshot
  if (!snapshot) {
    Message.error('截图插件未初始化')
    return
  }
  Message.loading({ content: '正在生成图片...', duration: 2000 })
  snapshot.getSnapshot(`workflow-${workflowId.value || 'export'}`, {
    fileType: 'png',
    backgroundColor: '#131623',
    padding: 40,
  })
}

/** 5. 缩略图 toggle */
function toggleMinimap() {
  if (!lf) return
  const minimap = (lf as any).extension?.miniMap
  if (!minimap) return
  minimapOpen.value = !minimapOpen.value
  if (minimapOpen.value) {
    minimap.show()
  } else {
    minimap.hide()
  }
}

/** 6. + 添加节点面板（复用左侧面板） */
function toggleAddNodePanel() {
  leftPanelOpen.value = !leftPanelOpen.value
}

/** 7. 调试模式 */
function toggleDebugMode() {
  debugMode.value = !debugMode.value
  if (debugMode.value) {
    executionPanelOpen.value = true
    Message.info('调试模式已开启，可逐步查看节点执行日志')
  } else {
    Message.info('调试模式已关闭')
  }
}

// 节点面板：从注册表驱动，不再硬编码
const basicNodes = DRAGGABLE_NODES.map(def => ({
  type: def.type,
  label: def.meta.title,
  icon: def.meta.icon,
  color: def.meta.color,
  desc: def.meta.description,
  category: def.meta.category,
}))

// 节点搜索
const nodeSearchKeyword = ref('')
const filteredNodes = computed(() => {
  const kw = nodeSearchKeyword.value.trim().toLowerCase()
  if (!kw) return basicNodes
  return basicNodes.filter(n =>
    n.label.toLowerCase().includes(kw) ||
    n.desc.toLowerCase().includes(kw) ||
    n.category.toLowerCase().includes(kw)
  )
})

// 按分类分组
const nodeCategories = computed(() => {
  const map = new Map<string, typeof basicNodes>()
  for (const node of basicNodes) {
    const cat = node.category || '其他'
    if (!map.has(cat)) map.set(cat, [])
    map.get(cat)!.push(node)
  }
  return Array.from(map.entries()).map(([name, nodes]) => ({ name, nodes }))
})

// 折叠的分类 set
const collapsedCategories = ref(new Set<string>())
function toggleCategory(name: string) {
  if (collapsedCategories.value.has(name)) {
    collapsedCategories.value.delete(name)
  } else {
    collapsedCategories.value.add(name)
  }
  // 触发响应式更新
  collapsedCategories.value = new Set(collapsedCategories.value)
}

// 初始化 LogicFlow
function initLogicFlow() {
  if (!containerRef.value) return
  
  // 使用插件
  LogicFlow.use(Control)
  LogicFlow.use(MiniMap)
  LogicFlow.use(Snapshot)
  
  // LogicFlow 初始化时暂停 Vue 响应式追踪
  pauseTracking()
  lf = new LogicFlow({
    container: containerRef.value,
    grid: {
      size: 20,
      visible: true,
      type: 'dot',
      config: {
        color: 'var(--wf-grid-dot)',
        thickness: 2
      }
    },
    keyboard: {
      enabled: true
    },
    style: {
      rect: {
        fill: 'var(--tf-bg-elevated)',
        stroke: 'var(--tf-border)',
        strokeWidth: 1,
        radius: 6
      },
      nodeText: {
        color: 'var(--tf-text-primary)',
        fontSize: 13
      },
      edgeText: {
        textWidth: 100,
        color: 'var(--tf-text-secondary)',
        fontSize: 12
      },
      bezier: {
        stroke: '#3b82f6',
        strokeWidth: 2,
      },
      anchor: {
        fill: '#3b82f6',
        stroke: 'var(--tf-bg-surface)',
        strokeWidth: 2,
        r: 5
      }
    }
  })
  resetTracking()

  // 初始时把水印刻在当前画布世界坐标的中心；后续随画布矩阵一起移动和缩放
  watermarkCanvasPosition.value = {
    x: containerRef.value.clientWidth / 2,
    y: containerRef.value.clientHeight / 2
  }
  syncCanvasWorldTransform()
  watermarkReady.value = true

  // ── 注册所有节点和边（新架构：graph/ 目录） ──────────────────────────
  lf.register(FlowEdge)
  registerAllNodes(lf)

  // 监听节点点击
  lf.on('node:click', ({ data }) => {
    // 深拷贝避免直接引用 LogicFlow 内部对象导致的递归更新
    selectedNode.value = JSON.parse(JSON.stringify(data))
    rightPanelOpen.value = true  // 点击节点自动展开右侧面板
  })

  // 监听空白点击
  lf.on('blank:click', () => {
    selectedNode.value = null
  })

  // 监听节点删除
  lf.on('node:delete', () => {
    selectedNode.value = null
  })

  // 同步缩放比例到底部工具条
  lf.on('graph:transform', () => {
    const transform = (lf as any).graphModel?.transformModel
    if (transform) {
      zoomPercent.value = Math.round(transform.SCALE_X * 100)
    }
    syncCanvasWorldTransform()
  })

  // 加载数据
  loadWorkflow()
}

// 加载工作流
async function loadWorkflow() {
  const id = route.params.id as string
  if (!id) return
  workflowId.value = id
  loading.value = true
  
  try {
    const res = await automationApi.getById(id)
    if (res.code === 0) {
      workflowName.value = res.data.name
      workflowStatus.value = res.data.status || 'draft'
      workflowRuntimeEnabled.value = Boolean(res.data.runtimeEnabled)
      workflowVersion.value = res.data.version || 1
      workflowProjectId.value = res.data.projectId
      workflowActorUserId.value = res.data.actorUserId
      workflowTriggerType.value = res.data.triggerType || 'manual'
      workflowConcurrencyMode.value = res.data.concurrencyMode || 'queue'
      workflowMaxConcurrent.value = res.data.maxConcurrent || 1
      try {
        const trigger = JSON.parse(res.data.triggerConfig || '{}')
        triggerCron.value = trigger.cron || '0 0 9 * * *'
        triggerTimezone.value = trigger.timezone || 'Asia/Shanghai'
        triggerFields.value = Array.isArray(trigger.fields) ? trigger.fields.join(',') : ''
        allowAutomationEvents.value = Boolean(trigger.allowAutomationEvents)
        triggerWebhookTokenSha256.value = trigger.tokenSha256 || ''
      } catch { /* 服务端发布时会再次校验 */ }
      const raw = JSON.parse(res.data.definition || '{}')

      // ── 兼容旧格式（variables/nodes[].data/edges[].source）和新格式（globalVariables/nodes[].inputs/edges[].sourceNodeId）
      const def: WorkflowDefinition = migrateDefinition(raw)
      globalVariables.value = def.globalVariables || {}
      
      // 转换为 LogicFlow 数据格式
      const graphData = {
        nodes: (def.nodes || []).map(n => ({
          id: n.id,
          type: n.type,
          x: n.position.x + 100,
          y: n.position.y + 30,
          text: n.nodeMeta?.title || n.type,
          properties: { ...n.config, config: n.config || {}, nodeType: n.type, inputs: n.inputs, outputs: n.outputs, nodeMeta: n.nodeMeta }
        })),
        edges: (def.edges || []).map(e => ({
          id: e.id,
          type: 'bezier',
          sourceNodeId: e.sourceNodeId,
          targetNodeId: e.targetNodeId,
          properties: { sourcePortName: e.sourcePortName, targetPortName: e.targetPortName }
        }))
      }
      
      pauseTracking()
      lf?.render(graphData)
      resetTracking()
    } else {
      Message.error(res.message || '加载失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 兼容旧格式迁移：
 * 旧格式: { variables: {}, nodes: [{..., label, data:{}}], edges: [{source, target}] }
 * 新格式: { globalVariables: {}, nodes: [{..., nodeMeta, inputs, outputs, config}], edges: [{sourceNodeId, sourcePortName, ...}] }
 */
function migrateDefinition(raw: any): WorkflowDefinition {
  // 已经是新格式（有 globalVariables 或 nodes[0].nodeMeta）
  if (raw.globalVariables !== undefined) return raw as WorkflowDefinition

  // 旧格式迁移
  return {
    globalVariables: Object.fromEntries(
      Object.entries(raw.variables || {}).map(([k, v]) => [k, { type: 'string' as const, defaultValue: v }])
    ),
    nodes: (raw.nodes || []).map((n: any) => {
      const def = getNodeDefinition(n.type)
      return {
        id: n.id,
        type: n.type,
        position: n.position,
        nodeMeta: {
          title: n.label || def?.meta.title || n.type,
          icon: def?.meta.icon || '⬡',
          description: def?.meta.description || '',
          color: def?.meta.color || '#6366f1',
        },
        inputs: (def?.inputPorts || []).map(p => ({
          name: p.name,
          valueType: p.valueType,
          required: p.required,
          description: p.description,
          value: n.data?.[p.name] != null
            ? { type: 'literal' as const, value: n.data[p.name] }
            : null,
        })),
        outputs: def?.outputPorts || [],
        config: n.data || {},
      }
    }),
    edges: (raw.edges || []).map((e: any) => ({
      id: e.id,
      sourceNodeId: e.source || e.sourceNodeId,
      sourcePortName: e.sourceHandle || e.sourcePortName || 'output',
      targetNodeId: e.target || e.targetNodeId,
      targetPortName: e.targetHandle || e.targetPortName || 'input',
    })),
  }
}

// 保存工作流
async function handleSave() {
  if (!lf) return false
  saving.value = true
  
  try {
    const graphData = lf.getGraphData() as { nodes: any[]; edges: any[] }
    
    const definition: WorkflowDefinition = {
      globalVariables: globalVariables.value,
      nodes: graphData.nodes.map((n: any) => ({
        id: n.id,
        type: (n.properties?.nodeType || n.type) as NodeType,
        position: { x: n.x - 100, y: n.y - 30 },
        nodeMeta: n.properties?.nodeMeta || {
          title: n.text?.value || n.text || getNodeTitle(n.properties?.nodeType),
          icon: '⬡',
          description: '',
          color: '#6366f1',
        },
        inputs:  n.properties?.inputs  || [],
        outputs: n.properties?.outputs || [],
        config:  extractNodeConfig(n.properties || {}),
      })),
      edges: graphData.edges.map((e: any) => ({
        id: e.id,
        sourceNodeId:  e.sourceNodeId,
        sourcePortName: e.properties?.sourcePortName || 'output',
        targetNodeId:  e.targetNodeId,
        targetPortName: e.properties?.targetPortName || 'input',
      }))
    }
    
    const res = await automationApi.update(workflowId.value, {
      name: workflowName.value,
      definition: JSON.stringify(definition),
      version: workflowVersion.value,
      projectId: workflowProjectId.value,
      actorUserId: workflowActorUserId.value,
      triggerType: workflowTriggerType.value,
      triggerConfig: JSON.stringify(buildTriggerConfig()),
      concurrencyMode: workflowConcurrencyMode.value,
      maxConcurrent: workflowMaxConcurrent.value,
    })
    
    if (res.code === 0) {
      workflowVersion.value = res.data.version || workflowVersion.value + 1
      Message.success('保存成功')
      return true
    } else {
      Message.error(res.message || '保存失败')
      return false
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
    return false
  } finally {
    saving.value = false
  }
}

function buildTriggerConfig() {
  if (workflowTriggerType.value === 'schedule') {
    return { cron: triggerCron.value, timezone: triggerTimezone.value }
  }
  if (workflowTriggerType.value === 'issue_changed') {
    return {
      fields: triggerFields.value.split(',').map(value => value.trim()).filter(Boolean),
      allowAutomationEvents: allowAutomationEvents.value,
    }
  }
  if (workflowTriggerType.value === 'webhook') {
    return { tokenSha256: triggerWebhookTokenSha256.value }
  }
  return {}
}

async function saveSettings() {
  settingsOpen.value = false
  await handleSave()
}

async function handlePublish() {
  if (!(await handleSave())) return
  publishing.value = true
  try {
    const res = await automationApi.publish(workflowId.value)
    if (res.code === 0) {
      workflowStatus.value = 'published'
      workflowVersion.value = res.data.version || workflowVersion.value + 1
      workflowRuntimeEnabled.value = false
      Message.success('工作流已发布，请确认配置后点击“启动”')
    } else Message.error(res.message || '发布失败')
  } catch (error: any) {
    Message.error(error.response?.data?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

async function handleStartRuntime() {
  runtimeChanging.value = true
  try {
    const res = await automationApi.start(workflowId.value)
    if (res.code === 0) {
      workflowRuntimeEnabled.value = true
      workflowVersion.value = res.data.version || workflowVersion.value + 1
      Message.success('自动化已启动，将按当前触发配置运行')
    }
  } catch (error: any) {
    Message.error(error.response?.data?.message || '启动失败')
  } finally {
    runtimeChanging.value = false
  }
}

async function handleStopRuntime() {
  runtimeChanging.value = true
  try {
    const res = await automationApi.stop(workflowId.value)
    if (res.code === 0) {
      workflowRuntimeEnabled.value = false
      workflowVersion.value = res.data.version || workflowVersion.value + 1
      Message.success('自动化已停止接收新触发，已有任务将继续完成')
    }
  } catch (error: any) {
    Message.error(error.response?.data?.message || '停止失败')
  } finally {
    runtimeChanging.value = false
  }
}

function extractNodeConfig(properties: Record<string, any>) {
  const reserved = new Set(['nodeType', 'nodeMeta', 'inputs', 'outputs', 'runStatus', 'label'])
  const legacyConfig = Object.fromEntries(
    Object.entries(properties).filter(([key]) => !reserved.has(key) && key !== 'config')
  )
  return { ...legacyConfig, ...(properties.config || {}) }
}

// 返回列表
function goBack() {
  router.push('/automation')
}

// 拖拽添加节点
function onDragStart(_e: MouseEvent, node: { type: string; label: string; icon: string; color: string }) {
  if (!lf) return
  
  const panelInners = document.querySelectorAll('.panel-inner, .panel-toggle')
  panelInners.forEach(el => (el as HTMLElement).style.pointerEvents = 'none')
  const onMouseUp = () => {
    panelInners.forEach(el => (el as HTMLElement).style.pointerEvents = '')
    document.removeEventListener('mouseup', onMouseUp)
  }
  document.addEventListener('mouseup', onMouseUp)

  const def = getNodeDefinition(node.type)
  lf.dnd.startDrag({
    type: node.type,
    text: node.label,
    properties: {
      nodeType: node.type,
      nodeMeta: { title: node.label, icon: node.icon, color: node.color, description: def?.meta.description || '' },
      inputs:  (def?.inputPorts  || []).map(p => ({ ...p, value: p.defaultValue ?? null })),
      outputs: def?.outputPorts  || [],
      config:  Object.fromEntries((def?.configFields || []).map(f => [f.key, f.defaultValue ?? ''])),
    }
  })
}

// ── 试运行 ────────────────────────────────────────────────
async function handleRun() {
  if (isRunning.value || !lf) return
  if (!(await handleSave())) return
  isRunning.value = true
  nodeStatusMap.value = {}
  streamingOutput.value = {}

  try {
    const res = await automationApi.execute(workflowId.value, {})
    if (res.code !== 0) {
      Message.error(res.message || '触发执行失败')
      isRunning.value = false
      return
    }
    currentExecutionId.value = res.data.executionId

    // SSE 监听
    const evtSource = new EventSource(
      `/api/v1/executions/${res.data.executionId}/stream`,
      { withCredentials: true }
    )

    evtSource.addEventListener('message', (e) => {
      try {
        const event = JSON.parse(e.data)
        if (event.type === 'node_running') {
          nodeStatusMap.value = { ...nodeStatusMap.value, [event.nodeId]: 'running' }
          lf?.setProperties(event.nodeId, { runStatus: 'running' })
        } else if (event.type === 'node_success') {
          nodeStatusMap.value = { ...nodeStatusMap.value, [event.nodeId]: 'success' }
          lf?.setProperties(event.nodeId, { runStatus: 'success' })
        } else if (event.type === 'node_failed') {
          nodeStatusMap.value = { ...nodeStatusMap.value, [event.nodeId]: 'failed' }
          lf?.setProperties(event.nodeId, { runStatus: 'failed' })
          Message.error(`节点 ${event.nodeId} 执行失败: ${event.error}`)
        } else if (event.type === 'node_skipped') {
          nodeStatusMap.value = { ...nodeStatusMap.value, [event.nodeId]: 'skipped' }
          lf?.setProperties(event.nodeId, { runStatus: 'skipped' })
        } else if (event.type === 'node_streaming_output') {
          streamingOutput.value = {
            ...streamingOutput.value,
            [event.nodeId]: (streamingOutput.value[event.nodeId] || '') + event.chunk,
          }
        } else if (event.type === 'workflow_success') {
          Message.success('工作流执行成功')
          evtSource.close()
          isRunning.value = false
        } else if (event.type === 'workflow_failed') {
          Message.error(`工作流执行失败: ${event.error}`)
          evtSource.close()
          isRunning.value = false
        } else if (event.type === 'workflow_cancelled') {
          Message.info('工作流执行已取消')
          evtSource.close()
          isRunning.value = false
        }
      } catch (_) {}
    })

    evtSource.onerror = () => {
      evtSource.close()
      isRunning.value = false
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行失败')
    isRunning.value = false
  }
}

async function handleCancelRun() {
  if (!currentExecutionId.value || !isRunning.value) return
  try {
    await automationApi.cancelExecution(currentExecutionId.value)
    Message.info('正在取消工作流执行...')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '取消执行失败')
  }
}

// 删除选中节点
function deleteSelectedNode() {
  if (!selectedNode.value || !lf) return
  lf.deleteNode(selectedNode.value.id)
  selectedNode.value = null
}

// 获取节点标题
function getNodeTitle(type: string): string {
  switch (type) {
    case 'cli-agent': return 'CLI Agent'
    case 'variables': return '变量设置'
    case 'condition': return '条件判断'
    case 'loop': return '重试循环'
    case 'file-input': return '文件输入'
    case 'delay': return '延时等待'
    default: return '节点'
  }
}

onMounted(() => {
  setTimeout(() => {
    initLogicFlow()
  }, 0)
})

onUnmounted(() => {
  lf = null
})
</script>

<style scoped>
.workflow-editor-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--tf-bg-body);
}

/* 编辑器主体：画布全屏 + 悬浮面板 */
.editor-content {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: var(--wf-canvas-bg);
}

/* 画布全屏 */
.canvas-container {
  width: 100%;
  height: 100%;
  background-color: transparent;
  position: relative;
  z-index: 1;
}

/* 世界坐标背景层：矩阵与 LogicFlow 节点层完全同步 */
.canvas-world-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
  transform-origin: 0 0;
  pointer-events: none;
  user-select: none;
  will-change: transform;
}

.canvas-world-watermark-anchor {
  position: absolute;
  transform-origin: 0 0;
}

.canvas-world-watermark {
  transform: translate(-50%, -50%);
  white-space: nowrap;
  font-size: clamp(160px, 12vw, 220px);
  line-height: 1;
  font-weight: 900;
  letter-spacing: 0.08em;
  /* 字体主体与画布同色，只让槽口边缘显形 */
  color: var(--wf-canvas-bg);
  -webkit-text-stroke: 1.25px var(--wf-canvas-watermark-edge);
  text-shadow:
    -3px -3px 2px var(--wf-canvas-watermark-shadow),
    3px 3px 2px var(--wf-canvas-watermark-highlight),
    -1px -1px 0 var(--wf-canvas-watermark-shadow),
    1px 1px 0 var(--wf-canvas-watermark-highlight);
}

/*
 * ────────────────────────────────────────────────────────────
 *  工作流编辑器专用深色调色板
 *  所有编辑器内部颜色统一在此声明，不允许在其他地方硬编码
 * ────────────────────────────────────────────────────────────
 */
.editor-content {
  /*
   * 不再在这里硬编码颜色——所有 --wf-* 变量已在 styles/variables.css 里
   * 按 dark/light/green 三套主题定义，会自动跟随全局 data-theme 切换。
   *
   * 只保留工具条的局部别名（复用节点卡片变量）以及语义固定色：
   */
  --wf-toolbar-bg:          var(--wf-node-bg);
  --wf-toolbar-border:      var(--wf-node-border);
  --wf-toolbar-text:        var(--wf-node-title);
  --wf-toolbar-muted:       var(--wf-node-subtitle);
  --wf-toolbar-hover:       var(--wf-node-bg-hover);
  --wf-toolbar-active:      rgba(56, 139, 253, 0.15);
  --wf-toolbar-active-text: var(--wf-edge-color);
  --wf-card-bg:             var(--wf-node-bg);
  --wf-card-border:         var(--wf-node-border);
  /* 试运行按钮（语义色，与主题无关） */
  --wf-run-bg:              #16a34a;
  --wf-run-hover:           #15803d;
  --wf-run-running:         #2563eb;
}

/* 执行日志浮层 */
.execution-overlay {
  position: absolute;
  bottom: 72px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 25;
  width: min(640px, 90%);
  background: #1a1d28;
  border: 1px solid var(--wf-card-border);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.5);
  overflow: hidden;
  pointer-events: all;
  max-height: 360px;
  display: flex;
  flex-direction: column;
}

/* 悬浮面板公共样式 */
.node-panel,
.config-panel {
  position: absolute;
  top: 12px;
  bottom: 12px;
  z-index: 10;
  display: flex;
  flex-direction: row;
  transition: transform 0.2s ease;
  pointer-events: none; /* 面板容器本身穿透，只有子元素响应 */
}

.node-panel > *,
.config-panel > * {
  pointer-events: auto;
}

/* 左侧节点面板 */
.node-panel {
  left: 12px;
  flex-direction: row;
}

.node-panel.collapsed {
  transform: translateX(calc(-100% + 28px));
}

/* 右侧配置面板 */
.config-panel {
  right: 12px;
  flex-direction: row-reverse;
}

.config-panel.collapsed {
  transform: translateX(calc(100% - 28px));
}

/* 面板内容区 */
.panel-inner {
  width: 220px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
}

.config-panel .panel-inner {
  width: 280px;
}

/* 收起/展开按钮 */
.panel-toggle {
  width: 20px;
  align-self: center;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px 0;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 4px;
  cursor: pointer;
  color: var(--tf-text-secondary);
  font-size: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}

.panel-toggle:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.panel-toggle-left {
  margin-left: 4px;
  order: 1;
}

.panel-toggle-right {
  margin-right: 4px;
  order: 1;
}

/* 节点面板内部 */
.panel-section {
  padding: 10px 8px;
}

.section-title {
  font-size: 10px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  margin-bottom: 6px;
  padding: 0 6px;
}

/* 搜索框 */
.node-search-wrap {
  padding: 10px 10px 6px;
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
}

.search-icon { font-size: 11px; }

/* 滚动区 */
.panel-scroll {
  overflow-y: auto;
  flex: 1;
}

/* 分类标题可点击 */
.section-title-clickable {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  transition: background 150ms;
}
.section-title-clickable:hover { background: var(--tf-bg-hover); }
.category-arrow { font-size: 9px; color: var(--tf-text-tertiary); }

/* 无搜索结果 */
.no-search-result {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  padding: 20px 10px;
  text-align: center;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: grab;
  transition: background 0.15s, transform 0.1s;
  margin-bottom: 4px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  user-select: none;
  position: relative;
  overflow: hidden;
}

/* 左侧彩色条 */
.node-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--node-color, #6366f1);
  border-radius: 8px 0 0 8px;
}

.node-item:hover {
  background: var(--tf-bg-hover);
  transform: translateX(2px);
}

.node-item:active {
  cursor: grabbing;
  transform: scale(0.97);
}

.node-item-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  background: color-mix(in srgb, var(--node-color, #6366f1) 15%, transparent);
  flex-shrink: 0;
}

.node-item-body {
  flex: 1;
  min-width: 0;
}

.node-item-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  line-height: 1.3;
}

.node-item-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  line-height: 1.3;
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 配置面板头部 */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--tf-border);
  position: sticky;
  top: 0;
  background: var(--tf-bg-surface);
  z-index: 1;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

/* LogicFlow 主题覆盖 */
:deep(.lf-graph) {
  background: transparent !important;
  position: relative;
  overflow: hidden;
}

:deep(.lf-control) {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  box-shadow: none;
}

:deep(.lf-mini-map) {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
}
</style>
