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
            @mousedown="(e) => onDragStart(e, node)"
          >
            <span class="node-icon">{{ node.icon }}</span>
            <span class="node-label">{{ node.label }}</span>
          </div>
        </div>
      </div>

      <!-- 中间：工作流画布 -->
      <div ref="containerRef" class="canvas-container"></div>

      <!-- 右侧：配置面板 -->
      <div class="config-panel">
        <template v-if="selectedNode">
          <div class="panel-header">
            <span class="panel-title">{{ getNodeTitle(selectedNode.properties?.nodeType) }}</span>
            <a-button type="text" size="small" status="danger" @click="deleteSelectedNode">删除</a-button>
          </div>
          
          <!-- CLI Agent 节点配置 -->
          <CliAgentConfig v-if="selectedNode.properties?.nodeType === 'cli-agent'" v-model:data="selectedNode.properties" />
          <VariablesConfig v-else-if="selectedNode.properties?.nodeType === 'variables'" v-model:data="selectedNode.properties" />
          <ConditionConfig v-else-if="selectedNode.properties?.nodeType === 'condition'" v-model:data="selectedNode.properties" />
          <LoopConfig v-else-if="selectedNode.properties?.nodeType === 'loop'" v-model:data="selectedNode.properties" />
          <FileInputConfig v-else-if="selectedNode.properties?.nodeType === 'file-input'" v-model:data="selectedNode.properties" />
          <DelayConfig v-else-if="selectedNode.properties?.nodeType === 'delay'" v-model:data="selectedNode.properties" />
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
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { pauseTracking, resetTracking } from '@vue/reactivity'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import LogicFlow from '@logicflow/core'
import { Control, MiniMap } from '@logicflow/extension'
import { automationApi, type WorkflowDefinition, type WorkflowNode } from '@/api'
import CliAgentConfig from './components/CliAgentConfig.vue'
import VariablesConfig from './components/VariablesConfig.vue'
import ConditionConfig from './components/ConditionConfig.vue'
import LoopConfig from './components/LoopConfig.vue'
import FileInputConfig from './components/FileInputConfig.vue'
import DelayConfig from './components/DelayConfig.vue'
import GlobalVariablesConfig from './components/GlobalVariablesConfig.vue'

// LogicFlow 样式
import '@logicflow/core/dist/index.css'
import '@logicflow/extension/lib/style/index.css'

const route = useRoute()
const router = useRouter()

// DOM 引用
const containerRef = ref<HTMLElement | null>(null)

// LogicFlow 实例
let lf: LogicFlow | null = null

// 工作流数据
const workflowId = ref('')
const workflowName = ref('加载中...')
const loading = ref(false)
const saving = ref(false)
const editingName = ref(false)
const nameInputRef = ref<HTMLInputElement | null>(null)

// 全局变量和选中节点
const globalVariables = ref<Record<string, string>>({})
const selectedNode = ref<any>(null)

// 节点模板
const basicNodes = [
  { type: 'cli-agent', label: 'CLI Agent', icon: '🤖' },
  { type: 'variables', label: '变量设置', icon: '📝' },
  { type: 'condition', label: '条件判断', icon: '🔀' },
  { type: 'loop', label: '重试循环', icon: '🔄' },
  { type: 'file-input', label: '文件输入', icon: '📁' },
  { type: 'delay', label: '延时等待', icon: '⏱' }
]

// 初始化 LogicFlow
function initLogicFlow() {
  if (!containerRef.value) return
  
  // 使用插件
  LogicFlow.use(Control)
  LogicFlow.use(MiniMap)
  
  // LogicFlow 初始化时暂停 Vue 响应式追踪
  pauseTracking()
  lf = new LogicFlow({
    container: containerRef.value,
    grid: {
      size: 20,
      visible: true,
      type: 'dot',
      config: {
        color: 'var(--tf-border)',
        thickness: 1
      }
    },
    background: {
      backgroundColor: 'var(--tf-bg-body)'
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
      polyline: {
        stroke: 'var(--tf-border)',
        strokeWidth: 2
      },
      anchor: {
        fill: 'var(--tf-accent)',
        stroke: 'var(--tf-bg-surface)',
        strokeWidth: 2,
        r: 5
      }
    }
  })
  resetTracking()

  // 监听节点点击
  lf.on('node:click', ({ data }) => {
    selectedNode.value = data
  })
  
  // 监听空白点击
  lf.on('blank:click', () => {
    selectedNode.value = null
  })
  
  // 监听节点删除
  lf.on('node:delete', () => {
    selectedNode.value = null
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
      const def: WorkflowDefinition = JSON.parse(res.data.definition || '{}')
      globalVariables.value = def.variables || {}
      
      // 转换为 LogicFlow 数据格式
      const graphData = {
        nodes: (def.nodes || []).map(n => ({
          id: n.id,
          type: 'rect',  // LogicFlow 内置类型
          x: n.position.x + 80, // LogicFlow 用中心点
          y: n.position.y + 20,
          text: n.label,
          properties: { ...n.data, nodeType: n.type }  // 保存原始类型
        })),
        edges: (def.edges || []).map(e => ({
          id: e.id,
          type: 'polyline',
          sourceNodeId: e.source,
          targetNodeId: e.target
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

// 保存工作流
async function handleSave() {
  if (!lf) return
  saving.value = true
  
  try {
    const graphData = lf.getGraphData() as { nodes: any[]; edges: any[] }
    
    const definition: WorkflowDefinition = {
      variables: globalVariables.value,
      nodes: graphData.nodes.map((n: any) => ({
        id: n.id,
        type: (n.properties?.nodeType || 'rect') as WorkflowNode['type'],
        label: n.text?.value || n.text || getNodeTitle(n.properties?.nodeType),
        position: { x: n.x - 80, y: n.y - 20 },
        data: n.properties || {}
      })),
      edges: graphData.edges.map((e: any) => ({
        id: e.id,
        source: e.sourceNodeId,
        target: e.targetNodeId
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
function onDragStart(_e: MouseEvent, node: { type: string; label: string; icon: string }) {
  if (!lf) return
  
  lf.dnd.startDrag({
    type: 'rect',  // 使用内置 rect 类型
    text: node.label,
    properties: {
      ...getDefaultNodeData(node.type, node.label),
      nodeType: node.type  // 保存原始类型用于配置面板
    }
  })
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
    case 'loop':
      return {
        label,
        maxRetries: 3,
        interval: 5,
        exitVariable: '{output}',
        exitOperator: 'contains',
        exitValue: 'PASS'
      }
    case 'file-input':
      return {
        label,
        filePath: '{workspace}/',
        readMode: 'full',
        encoding: 'utf-8',
        outputVar: 'file_content'
      }
    default:
      return { label }
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
  // 用 setTimeout 让 LogicFlow 初始化完全脱离 Vue 的响应式调度周期
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
  user-select: none;
}

.node-item:hover {
  background: var(--tf-bg-hover);
}

.node-item:active {
  cursor: grabbing;
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

/* LogicFlow 主题覆盖 */
:deep(.lf-graph) {
  background: var(--tf-bg-body) !important;
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
