<template>
  <div class="workflow-editor-view">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <a-button type="text" @click="goBack">
          <span class="back-icon">←</span> 返回
        </a-button>
      </div>
      <div class="toolbar-center">
        <div class="workflow-name-wrap" @click="startEditName">
          <span v-if="!editingName" class="workflow-name">{{ workflowName }}</span>
          <a-input
            v-else
            ref="nameInputRef"
            v-model="workflowName"
            size="small"
            class="name-input"
            @blur="finishEditName"
            @keyup.enter="finishEditName"
          />
          <span v-if="!editingName" class="edit-hint">✏️</span>
        </div>
      </div>
      <div class="toolbar-right">
        <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
      </div>
    </div>

    <!-- 三栏布局 -->
    <div class="editor-content">
      <!-- 左侧：节点面板 -->
      <div class="node-panel">
        <div class="panel-section">
          <div class="section-title">基础节点</div>
          <div
            v-for="node in basicNodes"
            :key="node.type"
            class="node-item"
            draggable="true"
            @dragstart="(e) => onDragStart(e, node)"
          >
            <span class="node-icon">{{ node.icon }}</span>
            <span class="node-label">{{ node.label }}</span>
          </div>
        </div>
        <div class="panel-section">
          <div class="section-title">即将推出</div>
          <div
            v-for="node in comingSoonNodes"
            :key="node.type"
            class="node-item disabled"
            @click="showComingSoon"
          >
            <span class="node-icon">{{ node.icon }}</span>
            <span class="node-label">{{ node.label }}</span>
          </div>
        </div>
      </div>

      <!-- 中间：工作流画布 -->
      <div class="canvas-container" @drop="onDrop" @dragover.prevent>
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-viewport="{ zoom: 1, x: 0, y: 0 }"
          :min-zoom="0.2"
          :max-zoom="2"
          fit-view-on-init
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          @connect="onConnect"
        >
          <template #node-cli-agent="nodeProps">
            <CliAgentNode :data="nodeProps.data" :selected="nodeProps.selected" />
          </template>
          <template #node-variables="nodeProps">
            <VariablesNode :data="nodeProps.data" :selected="nodeProps.selected" />
          </template>
          <template #node-condition="nodeProps">
            <ConditionNode :data="nodeProps.data" :selected="nodeProps.selected" />
          </template>
          <Background pattern-color="var(--tf-border)" :gap="20" />
          <Controls position="bottom-left" />
          <MiniMap position="bottom-right" />
        </VueFlow>
      </div>

      <!-- 右侧：配置面板 -->
      <div class="config-panel">
        <template v-if="selectedNode">
          <div class="panel-header">
            <span class="panel-title">{{ getNodeTitle(selectedNode.type) }}</span>
            <a-button type="text" size="small" status="danger" @click="deleteSelectedNode">删除</a-button>
          </div>
          
          <!-- CLI Agent 节点配置 -->
          <template v-if="selectedNode.type === 'cli-agent'">
            <CliAgentConfig v-model:data="selectedNode.data" />
          </template>
          
          <!-- 变量设置节点配置 -->
          <template v-else-if="selectedNode.type === 'variables'">
            <VariablesConfig v-model:data="selectedNode.data" />
          </template>
          
          <!-- 条件判断节点配置 -->
          <template v-else-if="selectedNode.type === 'condition'">
            <ConditionConfig v-model:data="selectedNode.data" />
          </template>
        </template>
        
        <!-- 未选中节点时显示全局变量 -->
        <template v-else>
          <div class="panel-header">
            <span class="panel-title">全局变量</span>
          </div>
          <GlobalVariablesConfig v-model:variables="globalVariables" />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { automationApi, type WorkflowDefinition, type WorkflowNode, type WorkflowEdge } from '@/api'
import CliAgentNode from './components/CliAgentNode.vue'
import VariablesNode from './components/VariablesNode.vue'
import ConditionNode from './components/ConditionNode.vue'
import CliAgentConfig from './components/CliAgentConfig.vue'
import VariablesConfig from './components/VariablesConfig.vue'
import ConditionConfig from './components/ConditionConfig.vue'
import GlobalVariablesConfig from './components/GlobalVariablesConfig.vue'

// Vue Flow 样式
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'

const route = useRoute()
const router = useRouter()
const { addNodes, addEdges, project } = useVueFlow()

// 工作流数据
const workflowId = ref('')
const workflowName = ref('加载中...')
const loading = ref(false)
const saving = ref(false)
const editingName = ref(false)
const nameInputRef = ref<HTMLInputElement | null>(null)

// Vue Flow 数据
const nodes = ref<any[]>([])
const edges = ref<any[]>([])
const globalVariables = ref<Record<string, string>>({})
const selectedNode = ref<any>(null)

// 节点模板
const basicNodes = [
  { type: 'cli-agent', label: 'CLI Agent', icon: '🤖' },
  { type: 'variables', label: '变量设置', icon: '📝' },
  { type: 'condition', label: '条件判断', icon: '🔀' }
]

const comingSoonNodes = [
  { type: 'loop', label: '重试循环', icon: '🔄' },
  { type: 'file-input', label: '文件输入', icon: '📁' },
  { type: 'delay', label: '延时等待', icon: '⏱' }
]

// 节点 ID 生成器
let nodeIdCounter = 1
function generateNodeId(): string {
  return `node-${Date.now()}-${nodeIdCounter++}`
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
      // 解析 definition
      const def: WorkflowDefinition = JSON.parse(res.data.definition || '{}')
      globalVariables.value = def.variables || {}
      
      // 转换节点（添加 Vue Flow 所需的类型和位置）
      nodes.value = (def.nodes || []).map(n => ({
        id: n.id,
        type: n.type,
        position: n.position,
        data: { ...n.data, label: n.label }
      }))
      
      edges.value = (def.edges || []).map(e => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle
      }))
    } else {
      Message.error(res.message || '加载失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 保存工作流
async function handleSave() {
  saving.value = true
  try {
    // 构建 definition
    const definition: WorkflowDefinition = {
      variables: globalVariables.value,
      nodes: nodes.value.map(n => ({
        id: n.id,
        type: n.type,
        label: n.data?.label || getNodeTitle(n.type),
        position: n.position,
        data: { ...n.data }
      })),
      edges: edges.value.map(e => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle
      }))
    }
    
    const res = await automationApi.update(workflowId.value, {
      name: workflowName.value,
      definition: JSON.stringify(definition)
    })
    
    if (res.code === 0) {
      Message.success('保存成功')
    } else {
      Message.error(res.message || '保存失败')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 返回列表
function goBack() {
  router.push('/automation')
}

// 编辑名称
function startEditName() {
  editingName.value = true
  nextTick(() => {
    nameInputRef.value?.focus()
  })
}

function finishEditName() {
  editingName.value = false
}

// 拖拽添加节点
function onDragStart(event: DragEvent, node: { type: string; label: string; icon: string }) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vueflow', JSON.stringify(node))
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDrop(event: DragEvent) {
  const data = event.dataTransfer?.getData('application/vueflow')
  if (!data) return
  
  const nodeInfo = JSON.parse(data)
  
  // 计算画布中的位置
  const canvasRect = (event.target as HTMLElement).closest('.canvas-container')?.getBoundingClientRect()
  if (!canvasRect) return
  
  const position = project({
    x: event.clientX - canvasRect.left,
    y: event.clientY - canvasRect.top
  })
  
  // 创建新节点
  const newNode: any = {
    id: generateNodeId(),
    type: nodeInfo.type,
    position,
    data: getDefaultNodeData(nodeInfo.type, nodeInfo.label)
  }
  
  addNodes([newNode])
}

// 获取默认节点数据
function getDefaultNodeData(type: string, label: string): Record<string, unknown> {
  switch (type) {
    case 'cli-agent':
      return {
        label,
        command: 'kiro-cli',
        args: '--no-interactive --trust-all-tools',
        prompt_template: '',
        model: '',
        timeout: 2400,
        work_dir: '{workspace}',
        output_var: 'output'
      }
    case 'variables':
      return {
        label,
        vars: [{ key: 'workspace', value: '/project/YT' }]
      }
    case 'condition':
      return {
        label,
        variable: '{output}',
        operator: 'contains',
        value: ''
      }
    default:
      return { label }
  }
}

// 节点点击
function onNodeClick({ node }: { node: any }) {
  selectedNode.value = node
}

// 画布点击（取消选中）
function onPaneClick() {
  selectedNode.value = null
}

// 连线
function onConnect(connection: any) {
  const edgeId = `e-${connection.source}-${connection.target}`
  addEdges([{
    id: edgeId,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle
  }])
}

// 删除选中节点
function deleteSelectedNode() {
  if (!selectedNode.value) return
  const nodeId = selectedNode.value.id
  nodes.value = nodes.value.filter(n => n.id !== nodeId)
  edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
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

// 即将推出提示
function showComingSoon() {
  Message.info('此节点类型即将推出')
}

onMounted(() => {
  loadWorkflow()
})

// 监听节点数据变化，同步到 selectedNode
watch(nodes, (newNodes) => {
  if (selectedNode.value) {
    const found = newNodes.find(n => n.id === selectedNode.value.id)
    if (found) {
      selectedNode.value = found
    }
  }
}, { deep: true })
</script>

<style scoped>
.workflow-editor-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--tf-bg-body);
}

/* 顶部工具栏 */
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  background: var(--tf-bg-surface);
  border-bottom: 1px solid var(--tf-border);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.back-icon {
  margin-right: 4px;
}

.workflow-name-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}

.workflow-name-wrap:hover {
  background: var(--tf-bg-hover);
}

.workflow-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.edit-hint {
  font-size: 12px;
  opacity: 0.5;
}

.name-input {
  width: 240px;
  text-align: center;
}

/* 三栏布局 */
.editor-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧节点面板 */
.node-panel {
  width: 200px;
  background: var(--tf-bg-surface);
  border-right: 1px solid var(--tf-border);
  padding: 12px;
  overflow-y: auto;
}

.panel-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
  padding: 0 8px;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: grab;
  transition: background 0.15s;
  margin-bottom: 4px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
}

.node-item:hover {
  background: var(--tf-bg-hover);
}

.node-item:active {
  cursor: grabbing;
}

.node-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.node-icon {
  font-size: 16px;
}

.node-label {
  font-size: 13px;
  color: var(--tf-text-primary);
}

/* 中间画布 */
.canvas-container {
  flex: 1;
  background: var(--tf-bg-body);
  position: relative;
}

/* 右侧配置面板 */
.config-panel {
  width: 280px;
  background: var(--tf-bg-surface);
  border-left: 1px solid var(--tf-border);
  overflow-y: auto;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--tf-border);
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

/* Vue Flow 主题覆盖 */
:deep(.vue-flow) {
  background: var(--tf-bg-body);
}

:deep(.vue-flow__background) {
  background: var(--tf-bg-body);
}

:deep(.vue-flow__controls) {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  box-shadow: none;
}

:deep(.vue-flow__controls-button) {
  background: var(--tf-bg-surface);
  border-bottom: 1px solid var(--tf-border);
  color: var(--tf-text-primary);
}

:deep(.vue-flow__controls-button:hover) {
  background: var(--tf-bg-hover);
}

:deep(.vue-flow__minimap) {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
}

:deep(.vue-flow__edge-path) {
  stroke: var(--tf-border);
  stroke-width: 2;
}

:deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke: var(--tf-accent);
}

:deep(.vue-flow__handle) {
  background: var(--tf-accent);
  border: 2px solid var(--tf-bg-surface);
  width: 10px;
  height: 10px;
}
</style>
