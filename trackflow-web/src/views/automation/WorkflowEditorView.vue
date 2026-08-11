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
      @save-as-template="openSaveAsTemplateModal"
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

      <!-- 右侧悬浮：配置面板 -->
      <div class="config-panel" :class="{ collapsed: !rightPanelOpen }">
        <!-- 收起/展开 tab -->
        <button class="panel-toggle panel-toggle-right" @click="rightPanelOpen = !rightPanelOpen">
          <span>{{ rightPanelOpen ? '▶' : '◀' }}</span>
        </button>

        <div class="panel-inner">
          <template v-if="selectedNode">
            <div class="panel-header inspector-header">
              <div>
                <span class="panel-title">{{ getWorkflowNodeTitle(selectedNode.properties?.nodeType) }}</span>
                <div class="inspector-tabs">
                  <button :class="{ active: inspectorTab === 'config' }" @click="inspectorTab = 'config'">配置</button>
                  <button :class="{ active: inspectorTab === 'debug' }" @click="inspectorTab = 'debug'">调试</button>
                </div>
              </div>
              <a-button v-if="inspectorTab === 'config'" type="text" size="small" status="danger" @click="deleteSelectedNode">删除</a-button>
            </div>
            <NodeDebugInspector
              v-if="inspectorTab === 'debug'"
              :node-name="selectedNode.properties?.nodeMeta?.title || getWorkflowNodeTitle(selectedNode.properties?.nodeType)"
              :node-type="selectedNode.properties?.nodeType"
              :status="nodeStatusMap[selectedNode.id] || 'idle'"
              :detail="nodeExecutionDetails[selectedNode.id]"
              @rerun="rerunLastNodeTest"
              @clear="clearNodeDebugSession"
              @copied="Message.success('调试结果已复制')"
            />
            <template v-else>
              <NodeContractSummary
                :contract="selectedNodeContract"
                :inputs="selectedNode.properties?.inputs || []"
                @disconnect="disconnectSelectedInput"
              />
              <CliAgentConfig v-if="selectedNode.properties?.nodeType === 'cli-agent'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <VariablesConfig v-else-if="selectedNode.properties?.nodeType === 'variables'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <ConditionConfig v-else-if="selectedNode.properties?.nodeType === 'condition'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <LoopConfig v-else-if="selectedNode.properties?.nodeType === 'loop'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <BatchConfig v-else-if="selectedNode.properties?.nodeType === 'batch'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <FileInputConfig v-else-if="selectedNode.properties?.nodeType === 'file-input'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <DelayConfig v-else-if="selectedNode.properties?.nodeType === 'delay'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <CodeConfig v-else-if="selectedNode.properties?.nodeType === 'code'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <HttpRequestConfig v-else-if="selectedNode.properties?.nodeType === 'http-request'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <SubWorkflowConfig v-else-if="selectedNode.properties?.nodeType === 'sub-workflow'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <IssueSearchConfig v-else-if="selectedNode.properties?.nodeType === 'trackflow-issue-search'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <IssueTransitionConfig v-else-if="selectedNode.properties?.nodeType === 'trackflow-issue-transition'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <IssueContextConfig v-else-if="selectedNode.properties?.nodeType === 'trackflow-issue-context'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <IssueUpdateConfig v-else-if="selectedNode.properties?.nodeType === 'trackflow-issue-update'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <RoleAgentConfig v-else-if="selectedNode.properties?.nodeType === 'role-agent'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <StartConfig v-else-if="selectedNode.properties?.nodeType === 'start'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <EndConfig v-else-if="selectedNode.properties?.nodeType === 'end'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <ApprovalConfig v-else-if="selectedNode.properties?.nodeType === 'approval'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <IssueGetConfig v-else-if="selectedNode.properties?.nodeType === 'trackflow-issue-get'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <IssueCommentConfig v-else-if="selectedNode.properties?.nodeType === 'trackflow-issue-comment'" :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" />
              <GenericNodeConfig v-else :data="selectedNode.properties" @update:data="updateSelectedNodeProperties" :definition="getNodeDefinition(selectedNode.properties?.nodeType)" />
            </template>
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
        :node-categories="nodeCategories"
        :all-nodes="basicNodes"
        @zoom-in="zoomIn"
        @zoom-out="zoomOut"
        @fit="fitCanvas"
        @zoom-to="zoomTo"
        @add-comment="addCommentNode"
        @auto-layout="autoLayout"
        @export-image="exportImage"
        @toggle-minimap="toggleMinimap"
        @drag-start="onDragStart"
        @quick-add="quickAddNode"
        @toggle-debug="toggleDebugMode"
        @run="handleRun"
        @cancel="handleCancelRun"
      />

      <!-- 整体运行才使用底部追踪抽屉；节点调试在右侧检查器中完成。 -->
      <div v-if="executionPanelOpen && executionPanelMode === 'workflow'" class="execution-dock" :class="{ 'right-panel-open': rightPanelOpen, 'is-collapsed': executionPanelCollapsed }">
        <ExecutionPanel
          :node-status-map="nodeStatusMap"
          :node-execution-details="nodeExecutionDetails"
          :streaming-output="streamingOutput"
          :is-running="isRunning || nodeTestLoading"
          :runtime-enabled="workflowRuntimeEnabled"
          :mode="executionPanelMode"
          :collapsed="executionPanelCollapsed"
          @close="executionPanelOpen = false; executionPanelCollapsed = false"
          @collapse-change="executionPanelCollapsed = $event"
          @go-history="router.push(`/automation/${workflowId}/executions`)"
          @rerun-debug="rerunLastNodeTest"
          @clear-debug="clearNodeDebugSession"
          @copy-debug-result="Message.success('调试结果已复制')"
        />
      </div>
    </div>

    <a-modal v-model:visible="settingsOpen" title="自动运行设置" :width="520" @ok="saveSettings">
      <a-alert type="info" style="margin-bottom: 16px">
        保存和发布都不会启动自动化。发布成功后，请在顶部点击“启动”；停止后不再接收新触发，已有任务会继续完成。
      </a-alert>
      <a-form :model="settingsModel" layout="vertical">
        <a-form-item label="所属项目 ID">
          <a-input-number v-model="workflowProjectId" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="执行身份（用户 ID）">
          <template #extra>
            <span class="settings-hint">所有 TrackFlow 写操作都按该用户的真实权限与状态机执行。</span>
          </template>
          <a-input-number v-model="workflowActorUserId" :min="1" style="width: 100%" />
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
          <template #extra>
            <span class="settings-hint">多个字段用英文逗号分隔；留空表示监听所有字段。</span>
          </template>
          <a-input v-model="triggerFields" placeholder="status_id,priority,assignee" />
        </a-form-item>
        <a-form-item v-if="workflowTriggerType === 'issue_changed'" label="允许自动化再次触发">
          <template #extra>
            <span class="settings-hint">默认关闭，防止状态变更工作流递归触发自身。</span>
          </template>
          <a-switch v-model="allowAutomationEvents" />
        </a-form-item>
        <a-form-item v-if="workflowTriggerType === 'webhook'" label="Token SHA-256">
          <template #extra>
            <span class="settings-hint">调用方传原始 Token 到 X-TrackFlow-Webhook-Token，并提供 X-Idempotency-Key。</span>
          </template>
          <a-input v-model="triggerWebhookTokenSha256" placeholder="64 位十六进制 SHA-256" />
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

    <!-- 另存为模板弹窗 -->
    <a-modal
      v-model:visible="showSaveAsTemplateModal"
      title="另存为模板"
      :ok-loading="saveAsTemplateLoading"
      ok-text="保存为模板"
      @ok="handleSaveAsTemplate"
      @cancel="resetSaveAsTemplateForm"
    >
      <a-form :model="saveAsTemplateForm" layout="vertical">
        <a-form-item label="模板名称" field="name" :rules="[{ required: true, message: '请输入模板名称' }]">
          <a-input v-model="saveAsTemplateForm.name" placeholder="模板名称" :max-length="100" />
        </a-form-item>
        <a-form-item label="描述" field="description">
          <a-textarea v-model="saveAsTemplateForm.description" placeholder="模板的用途说明" :max-length="2000" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
        <a-form-item label="分类" field="category">
          <a-select v-model="saveAsTemplateForm.category" placeholder="选择分类">
            <a-option value="ai_task">🤖 AI 任务</a-option>
            <a-option value="notification">🔔 通知</a-option>
            <a-option value="issue_management">📋 工单管理</a-option>
            <a-option value="custom">📦 自定义</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="图标" field="icon">
          <a-input v-model="saveAsTemplateForm.icon" placeholder="Emoji 图标，如 🔧" :max-length="20" style="width: 120px" />
        </a-form-item>
      </a-form>
    </a-modal>

    <WorkflowRunModal
      v-model:visible="showRunInputModal"
      v-model:input-text="runInputText"
      :workflow-name="workflowName"
      :loading="isRunning"
      :requirements="runInputRequirements"
      :guide-title="runInputGuideTitle"
      :guide-description="runInputGuideDescription"
      :placeholder="runInputPlaceholder"
      @run="confirmRun"
    />

    <NodeTestModal
      v-model:visible="showNodeTestModal"
      v-model:input-text="nodeTestInputText"
      v-model:confirm-side-effects="nodeTestConfirmSideEffects"
      :node-name="nodeTestNode?.properties?.nodeMeta?.title || nodeTestNode?.properties?.nodeType || ''"
      :loading="nodeTestLoading"
      :has-side-effects="nodeTestHasSideEffects"
      :is-simulation="nodeTestIsSimulation"
      :input-fields="nodeTestInputFields"
      :input-example="nodeTestInputExample"
      @fill-example="nodeTestInputText = nodeTestInputExample"
      @run="confirmNodeTest"
    />

    <NodeActionModals
      v-model:rename-visible="showRenameNodeModal"
      v-model:rename-title="renameNodeTitle"
      v-model:delete-visible="showDeleteNodeModal"
      v-model:help-visible="showNodeHelpModal"
      :target-title="nodeActionTarget?.properties?.nodeMeta?.title || ''"
      :target-description="nodeActionTarget?.properties?.nodeMeta?.description"
      :definition="nodeHelpDefinition"
      @rename="confirmRenameNode"
      @delete="confirmDeleteNode"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { pauseTracking, resetTracking } from '@vue/reactivity'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import LogicFlow from '@logicflow/core'
import { Control, MiniMap, Snapshot } from '@logicflow/extension'
import { automationApi, type WorkflowDefinition, type GlobalVariable, type ExecutionDetailVO, type AutomationNodeDefinitionVO } from '@/api'
import { DRAGGABLE_NODES, findNodeContractDrift, getNodeDefinition } from './node-definitions'
import { validateExecutableWorkflow } from './workflow-validator'
import { buildWorkflowDefinition, createInitialWorkflowDefinition, getWorkflowNodeTitle, migrateWorkflowDefinition, normalizeCanvasNode } from './workflow-definition'
import { FlowEdge } from './graph/edges/FlowEdge'
import { ExecutionFlowAnimator, type WorkflowCanvasEdge } from './graph/edges/ExecutionFlowAnimator'
import { registerAllNodes } from './graph/nodes/index'
import { PORT_CONNECT_HIT_RADIUS } from './graph/nodes/base/BaseNodeModel'
import CliAgentConfig from './components/config/CliAgentConfig.vue'
import VariablesConfig from './components/config/VariablesConfig.vue'
import ConditionConfig from './components/config/ConditionConfig.vue'
import LoopConfig from './components/config/LoopConfig.vue'
import BatchConfig from './components/config/BatchConfig.vue'
import FileInputConfig from './components/config/FileInputConfig.vue'
import DelayConfig from './components/config/DelayConfig.vue'
import CodeConfig from './components/config/CodeConfig.vue'
import HttpRequestConfig from './components/config/HttpRequestConfig.vue'
import SubWorkflowConfig from './components/config/SubWorkflowConfig.vue'
import IssueSearchConfig from './components/config/IssueSearchConfig.vue'
import IssueTransitionConfig from './components/config/IssueTransitionConfig.vue'
import IssueContextConfig from './components/config/IssueContextConfig.vue'
import IssueUpdateConfig from './components/config/IssueUpdateConfig.vue'
import RoleAgentConfig from './components/config/RoleAgentConfig.vue'
import StartConfig from './components/config/StartConfig.vue'
import EndConfig from './components/config/EndConfig.vue'
import ApprovalConfig from './components/config/ApprovalConfig.vue'
import IssueGetConfig from './components/config/IssueGetConfig.vue'
import IssueCommentConfig from './components/config/IssueCommentConfig.vue'
import GenericNodeConfig from './components/config/GenericNodeConfig.vue'
import GlobalVariablesConfig from './components/config/GlobalVariablesConfig.vue'
import ExecutionPanel from './components/ExecutionPanel.vue'
import WorkflowRunModal, { type WorkflowRunInputRequirement } from './components/WorkflowRunModal.vue'
import NodeTestModal, { type NodeTestInputField } from './components/NodeTestModal.vue'
import NodeActionModals from './components/NodeActionModals.vue'
import NodeDebugInspector from './components/NodeDebugInspector.vue'
import NodeContractSummary from './components/NodeContractSummary.vue'
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
const showSaveAsTemplateModal = ref(false)
const saveAsTemplateLoading = ref(false)
const saveAsTemplateForm = ref({
  name: '',
  description: '',
  category: 'custom',
  icon: '📋',
})
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
const inspectorTab = ref<'config' | 'debug'>('config')
const nodeActionTarget = ref<any>(null)
const showRenameNodeModal = ref(false)
const showDeleteNodeModal = ref(false)
const showNodeHelpModal = ref(false)
const renameNodeTitle = ref('')
const nodeHelpDefinition = computed(() => getNodeDefinition(
  nodeActionTarget.value?.properties?.nodeType || nodeActionTarget.value?.type || ''
))

function handleNodeMenuAction(event: { action: string; nodeId: string; data?: any }) {
  if (!lf) return
  const graphNode = (lf.getGraphData() as { nodes: any[] }).nodes
    .find(node => node.id === event.nodeId)
  const target = graphNode || event.data
  if (!target) return
  nodeActionTarget.value = JSON.parse(JSON.stringify(target))

  if (event.action === 'rename') {
    renameNodeTitle.value = target.properties?.nodeMeta?.title || target.text?.value || target.type
    showRenameNodeModal.value = true
    return
  }
  if (event.action === 'duplicate') {
    duplicateNode(target)
    return
  }
  if (event.action === 'delete') {
    showDeleteNodeModal.value = true
    return
  }
  if (event.action === 'help') showNodeHelpModal.value = true
}

function confirmRenameNode() {
  const target = nodeActionTarget.value
  const title = renameNodeTitle.value.trim()
  if (!lf || !target?.id || !title) {
    Message.warning('请输入节点名称')
    return
  }
  const properties = {
    ...(target.properties || {}),
    nodeMeta: { ...(target.properties?.nodeMeta || {}), title },
  }
  lf.setProperties(target.id, properties)
  if (selectedNode.value?.id === target.id) {
    selectedNode.value = { ...selectedNode.value, properties }
  }
  showRenameNodeModal.value = false
  Message.success('节点已重命名')
}

function duplicateNode(source: any) {
  if (!lf) return
  const copy = lf.addNode({
    type: source.type,
    x: source.x + 36,
    y: source.y + 36,
    properties: JSON.parse(JSON.stringify(source.properties || {})),
  }) as any
  if (copy?.id) {
    selectedNode.value = JSON.parse(JSON.stringify(copy))
    rightPanelOpen.value = true
  }
  Message.success('已创建节点副本')
}

function confirmDeleteNode() {
  const target = nodeActionTarget.value
  if (!lf || !target?.id) return
  lf.deleteNode(target.id)
  if (selectedNode.value?.id === target.id) selectedNode.value = null
  showDeleteNodeModal.value = false
  Message.success('节点已删除')
}

/**
 * 配置面板是唯一允许回写画布的入口。不能 watch selectedNode 再回写 LogicFlow，
 * 因为 LogicFlow 的重新渲染会再次触发 node:click，形成 Vue 的递归更新。
 */
function updateSelectedNodeProperties(properties: Record<string, unknown>) {
  const current = selectedNode.value
  if (!lf || !current?.id || !properties) return
  const nextProperties = JSON.parse(JSON.stringify(properties))
  if (JSON.stringify(current.properties) === JSON.stringify(nextProperties)) return
  lf.setProperties(current.id, nextProperties)
  selectedNode.value = { ...current, properties: nextProperties }
}

/** 从具名锚点提取端口。锚点格式由 BaseNodeModel 统一生成，不能在各节点各自猜测。 */
function getAnchorPortName(anchorId: unknown, nodeId: string, direction: 'in' | 'out'): string | null {
  const prefix = `${nodeId}-${direction}-`
  return typeof anchorId === 'string' && anchorId.startsWith(prefix)
    ? anchorId.slice(prefix.length) || null
    : null
}

/**
 * 画布边不仅是视觉连线，也是运行时数据绑定：source.output -> target.input。
 * 把这一步放在编辑器基座，避免每个节点配置面板各自实现一次且遗漏保存/试运行。
 */
function synchronizeEdgeBinding(edge: any) {
  if (!lf) return
  const sourcePortName = getAnchorPortName(edge.sourceAnchorId, edge.sourceNodeId, 'out')
  const targetPortName = getAnchorPortName(edge.targetAnchorId, edge.targetNodeId, 'in')
  if (!sourcePortName || !targetPortName) {
    // 旧草稿没有具名 anchor 时不得伪造默认端口；保存校验会明确指出问题。
    return
  }
  const target = lf.getNodeModelById(edge.targetNodeId) as any
  if (!target) return
  const inputs = (target.properties?.inputs || []).map((input: any) => input.name === targetPortName
    ? { ...input, value: { type: 'ref', nodeId: edge.sourceNodeId, outputName: sourcePortName } }
    : input)
  if (!inputs.some((input: any) => input.name === targetPortName)) return

  // 每个输入槽只能有一个数据来源。保留同一端口的多条线会让“当前实际使用哪条”不可解释。
  const graphData = lf.getGraphData() as { edges: any[] }
  graphData.edges
    .filter(candidate => candidate.id !== edge.id
      && candidate.targetNodeId === edge.targetNodeId
      && (candidate.properties?.targetPortName === targetPortName || candidate.targetAnchorId === edge.targetAnchorId))
    .forEach(candidate => lf?.deleteEdge(candidate.id))

  const properties = { ...(target.properties || {}), inputs }
  lf.setProperties(edge.targetNodeId, properties)
  lf.setProperties(edge.id, { ...(edge.properties || {}), sourcePortName, targetPortName })
  if (selectedNode.value?.id === edge.targetNodeId) {
    selectedNode.value = { ...selectedNode.value, properties }
  }
}

/** 删除边时仅解除由该边建立的引用，避免误清除用户后来改成的其他输入。 */
function clearEdgeBinding(edge: any) {
  if (!lf) return
  const sourcePortName = edge.properties?.sourcePortName
    || getAnchorPortName(edge.sourceAnchorId, edge.sourceNodeId, 'out')
  const targetPortName = edge.properties?.targetPortName
    || getAnchorPortName(edge.targetAnchorId, edge.targetNodeId, 'in')
  if (!sourcePortName || !targetPortName) return
  const target = lf.getNodeModelById(edge.targetNodeId) as any
  if (!target) return
  const inputs = (target.properties?.inputs || []).map((input: any) => {
    const value = input.value
    return input.name === targetPortName && value?.type === 'ref'
      && value.nodeId === edge.sourceNodeId && value.outputName === sourcePortName
      ? { ...input, value: null }
      : input
  })
  const properties = { ...(target.properties || {}), inputs }
  lf.setProperties(edge.targetNodeId, properties)
  if (selectedNode.value?.id === edge.targetNodeId) {
    selectedNode.value = { ...selectedNode.value, properties }
  }
}

/** 由统一契约面板断开数据来源，始终通过删除边来保持画布和运行时输入一致。 */
function disconnectSelectedInput(portName: string) {
  if (!lf || !selectedNode.value?.id) return
  const nodeId = selectedNode.value.id
  const graphData = lf.getGraphData() as { edges: any[] }
  const connectedEdges = graphData.edges.filter(edge => edge.targetNodeId === nodeId
    && (edge.properties?.targetPortName === portName || edge.targetAnchorId === `${nodeId}-in-${portName}`))
  if (connectedEdges.length) {
    connectedEdges.forEach(edge => lf?.deleteEdge(edge.id))
    return
  }
  const inputs = (selectedNode.value.properties?.inputs || []).map((input: any) => input.name === portName
    ? { ...input, value: null } : input)
  updateSelectedNodeProperties({ ...(selectedNode.value.properties || {}), inputs })
}

async function openNodeTest(node: any) {
  if (!workflowId.value || !lf) return
  // 单节点试运行必须与整张草稿保存隔离：画布里其他旧边或未完成节点不能阻断当前节点调试。
  nodeTestNode.value = JSON.parse(JSON.stringify(node))
  nodeTestInputText.value = '{}'
  nodeTestConfirmSideEffects.value = false
  showNodeTestModal.value = true
}

async function confirmNodeTest() {
  if (!nodeTestNode.value?.id) return
  let inputOverrides: Record<string, unknown>
  try {
    const parsed = JSON.parse(nodeTestInputText.value || '{}')
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      Message.error('节点试运行输入必须是 JSON 对象')
      return
    }
    inputOverrides = parsed as Record<string, unknown>
  } catch {
    Message.error('节点试运行输入不是合法 JSON')
    return
  }
  if (nodeTestHasSideEffects.value && !nodeTestConfirmSideEffects.value) {
    Message.warning('请先确认允许本次试运行产生真实副作用')
    return
  }
  const testedNode = JSON.parse(JSON.stringify(nodeTestNode.value))
  const nodeName = testedNode.properties?.nodeMeta?.title || testedNode.properties?.nodeType || testedNode.id
  selectedNode.value = testedNode
  rightPanelOpen.value = true
  inspectorTab.value = 'debug'
  executionPanelMode.value = 'node-debug'
  nodeStatusMap.value = { [testedNode.id]: 'running' }
  nodeExecutionDetails.value = {
    [testedNode.id]: { input: inputOverrides, nodeName, startedAt: new Date().toISOString() },
  }
  streamingOutput.value = {}
  executionPanelOpen.value = true
  lastNodeTest.value = {
    node: testedNode,
    inputText: nodeTestInputText.value,
    confirmSideEffects: nodeTestConfirmSideEffects.value,
  }
  lf?.setProperties(testedNode.id, { runStatus: 'running' })
  showNodeTestModal.value = false
  const debugStartedAt = performance.now()
  nodeTestLoading.value = true
  try {
    const res = await automationApi.testNode(workflowId.value, nodeTestNode.value.id, {
      inputOverrides,
      confirmSideEffects: nodeTestConfirmSideEffects.value,
      node: normalizeCanvasNode(nodeTestNode.value),
    })
    if (res.code === 0) {
      const runStatus: CanvasNodeStatus = res.data.status === 'failed' ? 'failed' : 'success'
      const panelStatus: CanvasNodeStatus = res.data.status === 'failed' ? 'failed' : 'success'
      nodeStatusMap.value = { [testedNode.id]: panelStatus }
      nodeExecutionDetails.value = {
        [testedNode.id]: {
          input: res.data.input,
          output: res.data.output,
          errorInfo: res.data.error,
          message: res.data.message,
          mode: res.data.status === 'simulated' ? 'simulated' : 'executed',
          durationMs: res.data.durationMs,
          nodeName,
          startedAt: nodeExecutionDetails.value[testedNode.id]?.startedAt,
        },
      }
      lf?.setProperties(testedNode.id, { runStatus })
      if (res.data.status === 'success') Message.success('节点试运行成功')
      else if (res.data.status === 'simulated') Message.info('节点预演完成')
    } else {
      recordNodeDebugFailure(testedNode, nodeName, res.message || '节点试运行失败', debugStartedAt)
    }
  } catch (error: any) {
    recordNodeDebugFailure(testedNode, nodeName, error.response?.data?.message || '节点试运行失败', debugStartedAt)
  } finally {
    nodeTestLoading.value = false
  }
}

function recordNodeDebugFailure(node: any, nodeName: string, error: string, startedAt: number) {
  nodeStatusMap.value = { [node.id]: 'failed' }
  nodeExecutionDetails.value = {
    [node.id]: {
      ...nodeExecutionDetails.value[node.id],
      nodeName,
      errorInfo: error,
      mode: 'executed',
      durationMs: Math.round(performance.now() - startedAt),
    },
  }
  lf?.setProperties(node.id, { runStatus: 'failed' })
  Message.error(error)
}

function rerunLastNodeTest() {
  const previous = lastNodeTest.value
  if (!previous) return
  nodeTestNode.value = JSON.parse(JSON.stringify(previous.node))
  nodeTestInputText.value = previous.inputText
  nodeTestConfirmSideEffects.value = previous.confirmSideEffects
  showNodeTestModal.value = true
}

function clearNodeDebugSession() {
  if (executionPanelMode.value !== 'node-debug') return
  for (const nodeId of Object.keys(nodeStatusMap.value)) {
    lf?.setProperties(nodeId, { runStatus: 'idle' })
  }
  nodeStatusMap.value = {}
  nodeExecutionDetails.value = {}
  streamingOutput.value = {}
  lastNodeTest.value = null
  executionPanelOpen.value = false
  inspectorTab.value = 'config'
}

// 面板开关
const rightPanelOpen = ref(false)  // 默认收起，点击节点时自动打开

// 执行状态
type CanvasNodeStatus = 'idle' | 'running' | 'success' | 'failed' | 'skipped' | 'cancelled'
type ExecutionPanelMode = 'workflow' | 'node-debug'
type NodeDebugRecord = {
  node: any
  inputText: string
  confirmSideEffects: boolean
}
const nodeStatusMap = ref<Record<string, CanvasNodeStatus>>({})
const nodeExecutionDetails = ref<Record<string, {
  input?: unknown
  output?: unknown
  errorInfo?: string
  message?: string
  mode?: 'executed' | 'simulated'
  startedAt?: string
  durationMs?: number
  nodeName?: string
}>>({})
const streamingOutput = ref<Record<string, string>>({})
const executionPanelMode = ref<ExecutionPanelMode>('workflow')
const lastNodeTest = ref<NodeDebugRecord | null>(null)
const isRunning = ref(false)
const currentExecutionId = ref<string | null>(null)
const showRunInputModal = ref(false)
const runInputText = ref('{}')
const runInputRequirements = ref<WorkflowRunInputRequirement[]>([])
const runInputGuideTitle = computed(() => {
  if (runInputRequirements.value.length === 0) return '此流程无需额外输入，可直接开始试运行。'
  return runInputRequirements.value.every(field => !field.required)
    ? '此流程已具备默认行为；下方字段仅用于缩小本次试运行范围。'
    : '请提供开始节点需要的触发数据，再开始试运行。'
})
const runInputGuideDescription = computed(() => {
  if (runInputRequirements.value.length === 0) return '系统会使用当前登录用户作为执行身份，并按流程中的默认配置运行。'
  return runInputRequirements.value.every(field => !field.required)
    ? '保持 {} 会使用默认范围；例如待办工单检测会查询当前用户有权限访问的工单。'
    : '字段名和说明已列在下方；填写的 JSON 只影响本次试运行，不会改动流程配置。'
})
const runInputPlaceholder = computed(() => runInputRequirements.value.length
  ? createRunInputExample(runInputRequirements.value)
  : '{}')
const showNodeTestModal = ref(false)
const nodeTestNode = ref<any>(null)
const nodeTestInputText = ref('{}')
const nodeTestConfirmSideEffects = ref(false)
const nodeTestLoading = ref(false)
let activeEvtSource: EventSource | null = null
let executionPollTimer: ReturnType<typeof setInterval> | null = null
let executionFlowAnimator: ExecutionFlowAnimator | null = null
const serverNodeContracts = ref<Record<string, AutomationNodeDefinitionVO>>({})
const selectedNodeContract = computed(() => {
  const type = selectedNode.value?.properties?.nodeType || selectedNode.value?.type
  return serverNodeContracts.value[type]
})
const currentNodeTestMode = computed(() => {
  const type = nodeTestNode.value?.properties?.nodeType || nodeTestNode.value?.type
  return serverNodeContracts.value[type]?.runtime?.testMode || 'safe'
})
const nodeTestHasSideEffects = computed(() => currentNodeTestMode.value === 'confirm')
const nodeTestIsSimulation = computed(() => currentNodeTestMode.value === 'simulated')
const nodeTestInputFields = computed<NodeTestInputField[]>(() => {
  const node = nodeTestNode.value
  const type = node?.properties?.nodeType || node?.type
  const definition = getNodeDefinition(type)
  const configuredInputs = new Map((node?.properties?.inputs || []).map((input: any) => [input.name, input]))
  return (definition?.inputPorts || []).map(port => {
    const configured = configuredInputs.get(port.name) as any
    return {
      name: port.name,
      label: port.label || port.name,
      valueType: port.valueType,
      description: port.description,
      required: configured?.required === true || port.required === true,
      requiresMock: configured?.value?.type === 'ref',
      source: configured?.value?.type === 'ref'
        ? `${configured.value.nodeId}.${configured.value.outputName}${configured.value.path ? `.${configured.value.path}` : ''}`
        : undefined,
    }
  })
})
const nodeTestInputExample = computed(() => createNodeTestInputExample(nodeTestInputFields.value))

// 底部工具栏
const executionPanelOpen = ref(false)
const executionPanelCollapsed = ref(false)
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
    backgroundColor: getComputedStyle(document.documentElement).getPropertyValue('--tf-bg-body').trim() || '#131623',
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

/** 7. 调试模式 */
function toggleDebugMode() {
  debugMode.value = !debugMode.value
  if (debugMode.value) {
    executionPanelOpen.value = true
    executionPanelCollapsed.value = false
    Message.info('调试模式已开启，可逐步查看节点执行日志')
  } else {
    Message.info('调试模式已关闭')
  }
}

// 节点面板：开始节点由系统唯一维护；结束节点可按分支需要新增。
const endNodeDefinition = getNodeDefinition('end')
const paletteNodeDefinitions = [
  ...DRAGGABLE_NODES,
  ...(endNodeDefinition ? [endNodeDefinition] : []),
]
const basicNodes = paletteNodeDefinitions.map(def => ({
  type: def.type,
  label: def.type === 'end' ? '结束（分支终点）' : def.meta.title,
  icon: def.meta.icon,
  color: def.meta.color,
  desc: def.type === 'end' ? '为当前分支添加一个流程终点' : def.meta.description,
  category: def.meta.category,
}))

// 按分类分组（传给 BottomToolbar）
const nodeCategories = computed(() => {
  const map = new Map<string, typeof basicNodes>()
  for (const node of basicNodes) {
    const cat = node.category || '其他'
    if (!map.has(cat)) map.set(cat, [])
    map.get(cat)!.push(node)
  }
  return Array.from(map.entries()).map(([name, nodes]) => ({ name, nodes }))
})

// 初始化 LogicFlow
async function initLogicFlow() {
  if (!containerRef.value) return
  try {
    const definitionRes = await automationApi.getNodeDefinitions()
    if (definitionRes.code === 0) {
      serverNodeContracts.value = Object.fromEntries((definitionRes.data || []).map(definition => [definition.type, definition]))
    }
    const drift = definitionRes.code === 0 ? findNodeContractDrift(definitionRes.data || [])
      : ['无法读取后端节点执行目录']
    if (drift.length > 0) {
      Message.error(`节点契约未同步：${drift[0]}`)
      return
    }
  } catch (error: any) {
    Message.error(error.response?.data?.message || '无法校验后端节点执行目录')
    return
  }
  
  // 使用插件
  LogicFlow.use(Control)
  LogicFlow.use(MiniMap)
  LogicFlow.use(Snapshot)
  
  // LogicFlow 初始化时暂停 Vue 响应式追踪
  pauseTracking()
  lf = new LogicFlow({
    container: containerRef.value,
    edgeType: 'flow-edge',
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
        stroke: 'var(--wf-edge-color, var(--tf-accent))',
        strokeWidth: 2,
      },
      anchor: {
        // 无形的连接热区大于视觉端口；在密集端口行中也能稳定拖拽。
        fill: 'rgba(0, 0, 0, 0.001)',
        stroke: 'transparent',
        strokeWidth: 0,
        r: PORT_CONNECT_HIT_RADIUS,
        hover: {
          fill: 'rgba(0, 0, 0, 0.001)',
          stroke: 'transparent',
          strokeWidth: 0,
          r: PORT_CONNECT_HIT_RADIUS,
        }
      }
    }
  })
  resetTracking()

  executionFlowAnimator = new ExecutionFlowAnimator(
    () => {
      if (!lf) return []
      return (lf.getGraphData() as { edges: WorkflowCanvasEdge[] }).edges
    },
    (edgeId, properties) => lf?.setProperties(edgeId, properties),
  )

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
    inspectorTab.value = 'config'
  })

  lf.on('node:test', ({ data }) => {
    void openNodeTest(data)
  })

  lf.on('node:menu-action', (event: { action: string; nodeId: string; data?: any }) => {
    handleNodeMenuAction(event)
  })

  // 监听空白点击
  lf.on('blank:click', () => {
    selectedNode.value = null
  })

  // 监听节点删除
  lf.on('node:delete', () => {
    selectedNode.value = null
  })

  lf.on('edge:add', ({ data }: any) => {
    synchronizeEdgeBinding(data)
  })

  lf.on('edge:delete', ({ data }: any) => {
    clearEdgeBinding(data)
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
  await loadWorkflow()
}

// 加载工作流
async function loadWorkflow() {
  const id = route.params.id as string
  if (!id) return
  workflowId.value = id
  
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
      } catch { /* JSON 解析容错，使用默认触发器配置 */ }
      const raw = JSON.parse(res.data.definition || '{}')

      // ── 兼容旧格式（variables/nodes[].data/edges[].source）和新格式（globalVariables/nodes[].inputs/edges[].sourceNodeId）
      let def: WorkflowDefinition = migrateWorkflowDefinition(raw)
      // 兼容旧版本创建出的空画布。开始/结束是系统节点，不能要求用户自己添加。
      if (def.nodes.length === 0) {
        def = createInitialWorkflowDefinition()
        Message.info('已为该空白工作流补齐开始和结束节点，保存后将作为流程骨架保留。')
      }
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
          type: 'flow-edge',
          sourceNodeId: e.sourceNodeId,
          targetNodeId: e.targetNodeId,
          // 使用具名锚点 ID，让边精准连接到对应端口
          sourceAnchorId: e.sourcePortName ? `${e.sourceNodeId}-out-${e.sourcePortName}` : undefined,
          targetAnchorId: e.targetPortName ? `${e.targetNodeId}-in-${e.targetPortName}` : undefined,
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
  }
}

// 保存工作流
async function handleSave() {
  if (!lf) return false
  saving.value = true
  
  try {
    const graphData = lf.getGraphData() as { nodes: any[]; edges: any[] }
    
    const definition = buildWorkflowDefinition(globalVariables.value, graphData)
    
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

// 另存为模板
function openSaveAsTemplateModal() {
  saveAsTemplateForm.value.name = workflowName.value
  showSaveAsTemplateModal.value = true
}

async function handleSaveAsTemplate() {
  if (!saveAsTemplateForm.value.name.trim()) {
    Message.warning('请输入模板名称')
    return
  }
  saveAsTemplateLoading.value = true
  try {
    const res = await automationApi.saveAsTemplate({
      workflowId: workflowId.value,
      name: saveAsTemplateForm.value.name,
      description: saveAsTemplateForm.value.description || undefined,
      category: saveAsTemplateForm.value.category || undefined,
      icon: saveAsTemplateForm.value.icon || undefined,
    })
    if (res.code === 0) {
      Message.success('已保存为模板')
      showSaveAsTemplateModal.value = false
      resetSaveAsTemplateForm()
    } else {
      Message.error(res.message || '保存失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存为模板失败')
  } finally {
    saveAsTemplateLoading.value = false
  }
}

function resetSaveAsTemplateForm() {
  saveAsTemplateForm.value = {
    name: workflowName.value,
    description: '',
    category: 'custom',
    icon: '📋',
  }
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

// 返回列表
function goBack() {
  router.push('/automation')
}

type PaletteNode = { type: string; label: string; icon: string; color: string }

function createNodeProperties(node: PaletteNode) {
  const definition = getNodeDefinition(node.type)
  return {
    nodeType: node.type,
    nodeMeta: { title: node.label, icon: node.icon, color: node.color,
      description: definition?.meta.description || '' },
    inputs: (definition?.inputPorts || []).map(port => ({ ...port, value: port.defaultValue ?? null })),
    outputs: definition?.outputPorts || [],
    config: Object.fromEntries((definition?.configFields || []).map(field => [field.key, field.defaultValue ?? ''])),
  }
}

// 拖拽添加节点
function onDragStart(_e: MouseEvent, node: PaletteNode) {
  if (!lf) return
  
  const panelInners = document.querySelectorAll('.panel-inner, .panel-toggle')
  panelInners.forEach(el => (el as HTMLElement).style.pointerEvents = 'none')
  const onMouseUp = () => {
    panelInners.forEach(el => (el as HTMLElement).style.pointerEvents = '')
    document.removeEventListener('mouseup', onMouseUp)
  }
  document.addEventListener('mouseup', onMouseUp)

  lf.dnd.startDrag({
    type: node.type,
    text: node.label,
    properties: createNodeProperties(node)
  })
}

/** 点击节点库时放入当前视口中心：无需先学习拖拽，也不会落到看不见的位置。 */
function quickAddNode(node: PaletteNode) {
  if (!lf || !containerRef.value) return
  const rect = containerRef.value.getBoundingClientRect()
  const point = lf.getPointByClient(rect.left + rect.width / 2, rect.top + rect.height / 2)
  const created = lf.addNode({
    type: node.type,
    x: point.canvasOverlayPosition.x,
    y: point.canvasOverlayPosition.y,
    properties: createNodeProperties(node),
  }) as any
  if (!created?.id) return
  selectedNode.value = JSON.parse(JSON.stringify(created.getData?.() || created))
  rightPanelOpen.value = true
  inspectorTab.value = 'config'
  Message.success(`已添加「${node.label}」，可继续配置或拖动调整位置`)
}

// ── 试运行 ────────────────────────────────────────────────
function getRunInputRequirements(definition: WorkflowDefinition): WorkflowRunInputRequirement[] {
  const startNode = definition.nodes.find(node => node.type === 'start')
  if (!startNode) return []

  const fields = new Map<string, WorkflowRunInputRequirement>()
  for (const node of definition.nodes) {
    for (const input of node.inputs) {
      const value = input.value
      if (value?.type !== 'ref' || value.nodeId !== startNode.id
        || value.outputName !== 'trigger' || !value.path) continue

      const existing = fields.get(value.path)
      fields.set(value.path, {
        path: value.path,
        required: Boolean(existing?.required || input.required),
        valueType: input.valueType,
        description: input.description || existing?.description || '',
      })
    }
  }
  return [...fields.values()]
}

function createRunInputExample(fields: WorkflowRunInputRequirement[]): string {
  const example: Record<string, unknown> = {}
  for (const field of fields) {
    const segments = field.path.split('.').filter(Boolean)
    if (segments.length === 0) continue
    let target = example
    for (const segment of segments.slice(0, -1)) {
      const current = target[segment]
      if (!current || typeof current !== 'object' || Array.isArray(current)) target[segment] = {}
      target = target[segment] as Record<string, unknown>
    }
    const name = segments[segments.length - 1]
    target[name] = field.valueType === 'number' ? 1
      : field.valueType === 'boolean' ? true
      : field.valueType === 'array' ? []
      : field.valueType === 'object' ? {}
      : ''
  }
  return JSON.stringify(example, null, 2)
}

/** 为单节点临时覆盖提供可直接粘贴的示例，避免要求用户猜测 JSON 字段名。 */
function createNodeTestInputExample(fields: NodeTestInputField[]): string {
  if (fields.length === 0) return '{}'
  const example = Object.fromEntries(fields.map(field => [field.name,
    field.valueType === 'number' ? 1
      : field.valueType === 'boolean' ? true
      : field.valueType === 'array' ? []
      : field.valueType === 'object' ? {}
      : '',
  ]))
  return JSON.stringify(example, null, 2)
}

async function handleRun() {
  if (isRunning.value || !lf) return
  const graphData = lf.getGraphData() as { nodes: any[]; edges: any[] }
  const runDefinition = buildWorkflowDefinition(globalVariables.value, graphData)
  const validationError = validateExecutableWorkflow(runDefinition)
  if (validationError) {
    Message.error(`无法试运行：${validationError}`)
    return
  }
  if (!(await handleSave())) return
  runInputRequirements.value = getRunInputRequirements(runDefinition)
  runInputText.value = '{}'
  showRunInputModal.value = true
}

async function confirmRun() {
  let runInputs: Record<string, unknown>
  try {
    const parsed = JSON.parse(runInputText.value || '{}')
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      Message.error('试运行输入必须是 JSON 对象')
      return
    }
    runInputs = parsed as Record<string, unknown>
  } catch {
    Message.error('试运行输入不是合法 JSON')
    return
  }
  isRunning.value = true
  executionPanelMode.value = 'workflow'
  lastNodeTest.value = null
  nodeStatusMap.value = {}
  nodeExecutionDetails.value = {}
  streamingOutput.value = {}
  executionPanelOpen.value = true
  executionPanelCollapsed.value = false
  executionFlowAnimator?.begin()

  try {
    const res = await automationApi.execute(workflowId.value, runInputs)
    if (res.code !== 0) {
      Message.error(res.message || '触发执行失败')
      isRunning.value = false
      return
    }
    showRunInputModal.value = false
    currentExecutionId.value = res.data.executionId

    // SSE 监听
    const evtSource = new EventSource(
      `/api/v1/automation/executions/${res.data.executionId}/stream`,
      { withCredentials: true }
    )
    activeEvtSource = evtSource

    evtSource.addEventListener('message', (e) => {
      try {
        const event = JSON.parse(e.data)
        if (event.type === 'node_running') {
          // 下游节点真正开始时，才表示这条数据路径被实际选中并抵达。
          executionFlowAnimator?.flowIntoNode(event.nodeId)
          nodeStatusMap.value = { ...nodeStatusMap.value, [event.nodeId]: 'running' }
          lf?.setProperties(event.nodeId, { runStatus: 'running' })
        } else if (event.type === 'node_success') {
          nodeStatusMap.value = { ...nodeStatusMap.value, [event.nodeId]: 'success' }
          nodeExecutionDetails.value = {
            ...nodeExecutionDetails.value,
            [event.nodeId]: { output: event.outputs, durationMs: event.durationMs },
          }
          lf?.setProperties(event.nodeId, { runStatus: 'success' })
        } else if (event.type === 'node_failed') {
          executionFlowAnimator?.failIntoNode(event.nodeId)
          nodeStatusMap.value = { ...nodeStatusMap.value, [event.nodeId]: 'failed' }
          nodeExecutionDetails.value = {
            ...nodeExecutionDetails.value,
            [event.nodeId]: { errorInfo: event.error, durationMs: event.durationMs },
          }
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
          finishExecutionTracking('success')
        } else if (event.type === 'workflow_failed') {
          Message.error(`工作流执行失败: ${event.error}`)
          finishExecutionTracking('failed')
        } else if (event.type === 'workflow_cancelled') {
          Message.info('工作流执行已取消')
          finishExecutionTracking('cancelled')
        }
      } catch (e) {
        console.error('[WorkflowEditor] SSE 事件解析失败:', e)
      }
    })

    evtSource.onerror = () => {
      evtSource.close()
      activeEvtSource = null
    }
    startExecutionPolling(res.data.executionId)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '执行失败')
    isRunning.value = false
  }
}

function toCanvasNodeStatus(status: string): CanvasNodeStatus {
  if (status === 'success' || status === 'failed' || status === 'skipped' || status === 'cancelled') return status
  return status === 'running' || status === 'waiting' || status === 'retrying' ? 'running' : 'idle'
}

function applyExecutionDetail(detail: ExecutionDetailVO) {
  const statuses: Record<string, CanvasNodeStatus> = {}
  const details: Record<string, { input?: unknown; output?: unknown; errorInfo?: string; durationMs?: number; nodeName?: string }> = {}
  for (const node of detail.nodeExecutions || []) {
    statuses[node.nodeId] = toCanvasNodeStatus(node.status)
    details[node.nodeId] = {
      input: node.input,
      output: node.output,
      errorInfo: node.errorInfo,
      durationMs: node.durationMs,
      nodeName: node.nodeName,
    }
    lf?.setProperties(node.nodeId, { runStatus: statuses[node.nodeId] })
  }
  nodeStatusMap.value = statuses
  nodeExecutionDetails.value = details
  // 运行中优先让 SSE 时间线驱动动画；否则轮询会把极快流程在首帧直接涂成完成态。
  if (!isRunning.value) executionFlowAnimator?.restoreCompletedPaths(statuses)
}

async function refreshExecutionDetail(executionId: string) {
  const res = await automationApi.getExecution(executionId)
  if (res.code !== 0) return
  applyExecutionDetail(res.data)
  if (isTerminalExecutionStatus(res.data.status)) finishExecutionTracking(res.data.status)
}

function isTerminalExecutionStatus(status: ExecutionDetailVO['status']): status is 'success' | 'failed' | 'cancelled' {
  return status === 'success' || status === 'failed' || status === 'cancelled'
}

function startExecutionPolling(executionId: string) {
  if (executionPollTimer) clearInterval(executionPollTimer)
  void refreshExecutionDetail(executionId)
  executionPollTimer = setInterval(() => {
    void refreshExecutionDetail(executionId).catch(() => undefined)
  }, 800)
}

function finishExecutionTracking(outcome: 'success' | 'failed' | 'cancelled' = 'cancelled') {
  isRunning.value = false
  if (executionPollTimer) {
    clearInterval(executionPollTimer)
    executionPollTimer = null
  }
  activeEvtSource?.close()
  activeEvtSource = null
  executionFlowAnimator?.settle(outcome)
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

onMounted(() => {
  setTimeout(() => {
    void initLogicFlow()
  }, 0)
})

onUnmounted(() => {
  finishExecutionTracking()
  executionFlowAnimator?.dispose()
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
  font-size: clamp(96px, 8vw, 144px);
  line-height: 1;
  font-weight: 800;
  letter-spacing: 0.12em;
  /* 字体主体与画布同色，只让槽口边缘显形 */
  color: var(--wf-canvas-bg);
  opacity: 0.55;
  -webkit-text-stroke: 1px var(--wf-canvas-watermark-edge);
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
  --wf-toolbar-active:      var(--tf-accent-medium);
  --wf-toolbar-active-text: var(--wf-edge-color);
  --wf-card-bg:             var(--wf-node-bg);
  --wf-card-border:         var(--wf-node-border);
  /* 试运行按钮（语义色，与主题无关） */
  --wf-run-bg:              var(--tf-success);
  --wf-run-hover:           color-mix(in srgb, var(--tf-success) 85%, black);
  --wf-run-running:         var(--tf-accent);
}

/* 完整流程的底部执行追踪抽屉；节点级调试始终在右侧检查器中展示。 */
.execution-dock {
  position: absolute;
  bottom: 72px;
  left: 24px;
  right: 24px;
  z-index: 25;
  height: min(42vh, 560px);
  min-height: 320px;
  background: var(--wf-canvas-bg, var(--tf-bg-body));
  border: 1px solid var(--wf-card-border);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.5);
  overflow: hidden;
  resize: vertical;
  pointer-events: all;
  display: flex;
  flex-direction: column;
  transition: right 0.2s ease, height 0.24s cubic-bezier(0.2, 0.8, 0.2, 1), min-height 0.24s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.execution-dock.right-panel-open {
  right: 482px;
}

.execution-dock.is-collapsed {
  height: 42px;
  min-height: 42px;
  resize: none;
}

@media (prefers-reduced-motion: reduce) {
  .execution-dock { transition: none; }
}

/* 悬浮面板公共样式 */
.config-panel {
  position: absolute;
  top: 12px;
  bottom: 12px;
  z-index: 10;
  display: flex;
  flex-direction: row-reverse;
  right: 12px;
  transition: transform 0.2s ease;
  pointer-events: none;
}

.config-panel > * {
  pointer-events: auto;
}

.config-panel.collapsed {
  transform: translateX(calc(100% - 28px));
}

/* 面板内容区 */
.panel-inner {
  width: 176px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--tf-shadow-xl);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.config-panel .panel-inner {
  width: 450px;
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
  box-shadow: var(--tf-shadow);
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

.inspector-header > div:first-child { min-width: 0; }
.inspector-tabs { display: flex; gap: 12px; margin-top: 8px; }
.inspector-tabs button {
  padding: 0 0 5px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--tf-text-secondary);
  cursor: pointer;
  font-size: 12px;
}
.inspector-tabs button:hover { color: var(--tf-text-primary); }
.inspector-tabs button.active { color: var(--tf-accent); border-bottom-color: var(--tf-accent); font-weight: 600; }

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

.node-test-result {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--tf-border);
  border-radius: 8px;
  background: var(--tf-bg-body);
  font-size: 12px;
}
.node-test-result.success { border-color: var(--tf-success); }
.node-test-result.failed { border-color: var(--tf-danger); }
.node-test-result p { margin: 8px 0; color: var(--tf-text-secondary); }
.node-test-result pre {
  max-height: 220px;
  margin: 8px 0 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--tf-text-secondary);
}
.node-test-error { color: var(--tf-danger) !important; }
/* LogicFlow 的选中态没有传入 Vue 节点属性，在画布层补上可感知的选择反馈。 */
:deep(.lf-node-selected) .node-card {
  border-color: color-mix(in srgb, var(--node-color) 76%, var(--wf-node-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--node-color) 18%, transparent), var(--wf-node-shadow-hover);
}

/* ── 节点配置面板：字段描述文字 ─────────────────────── */
/* Arco Design extra slot 渲染为 .arco-form-item-extra */
.panel-inner :deep(.arco-form-item-extra) {
  font-size: 10px;
  line-height: 1.5;
  color: var(--tf-text-tertiary);
  margin-top: 3px;
  opacity: 0.85;
}

/* label 字号保持 12px，与 extra 形成明显层级差 */
.panel-inner :deep(.arco-form-item-label) {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
}

/* 运行设置弹窗：字段下方的辅助说明 */
.settings-hint {
  display: block;
  font-size: 12px;
  line-height: 1.5;
  color: var(--tf-text-tertiary);
  margin-top: 2px;
  word-break: break-word;
}
</style>
