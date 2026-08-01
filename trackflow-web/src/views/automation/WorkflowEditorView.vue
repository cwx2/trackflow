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
        <a-button :loading="isRunning" @click="handleRun">▶ 试运行</a-button>
        <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
      </div>
    </div>

    <!-- 编辑器主体：画布 + 悬浮面板 -->
    <div class="editor-content">
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
          </template>
          <template v-else>
            <div class="panel-header">
              <span class="panel-title">全局变量</span>
            </div>
            <GlobalVariablesConfig v-model:variables="globalVariables" />
          </template>
        </div>
      </div>
    </div>

    <!-- 底部执行日志面板 -->
    <ExecutionPanel
      :node-status-map="nodeStatusMap"
      :streaming-output="streamingOutput"
      :is-running="isRunning"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { pauseTracking, resetTracking } from '@vue/reactivity'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import LogicFlow, { HtmlNode, HtmlNodeModel } from '@logicflow/core'
import { Control, MiniMap } from '@logicflow/extension'
import { automationApi, type WorkflowDefinition, type WorkflowNode, type NodeType, type GlobalVariable } from '@/api'
import { NODE_DEFINITIONS, DRAGGABLE_NODES, getNodeDefinition } from './node-definitions'
import CliAgentConfig from './components/CliAgentConfig.vue'
import VariablesConfig from './components/VariablesConfig.vue'
import ConditionConfig from './components/ConditionConfig.vue'
import LoopConfig from './components/LoopConfig.vue'
import FileInputConfig from './components/FileInputConfig.vue'
import DelayConfig from './components/DelayConfig.vue'
import CodeConfig from './components/CodeConfig.vue'
import HttpRequestConfig from './components/HttpRequestConfig.vue'
import SubWorkflowConfig from './components/SubWorkflowConfig.vue'
import GlobalVariablesConfig from './components/GlobalVariablesConfig.vue'
import ExecutionPanel from './components/ExecutionPanel.vue'

// LogicFlow 样式
import '@logicflow/core/dist/index.css'
import '@logicflow/extension/lib/style/index.css'

/** 简单封装，让 migrateDefinition 可以调用注册表 */
function useNodeDefinitions() {
  return { getNodeDefinition }
}

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
const globalVariables = ref<Record<string, GlobalVariable>>({})
const selectedNode = ref<any>(null)

// 面板开关
const leftPanelOpen = ref(true)
const rightPanelOpen = ref(false)  // 默认收起，点击节点时自动打开

// 执行状态
const nodeStatusMap = ref<Record<string, 'idle'|'running'|'success'|'failed'>>({})
const streamingOutput = ref<Record<string, string>>({})
const isRunning = ref(false)
const currentExecutionId = ref<string | null>(null)

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
  
  // LogicFlow 初始化时暂停 Vue 响应式追踪
  pauseTracking()
  lf = new LogicFlow({
    container: containerRef.value,
    grid: {
      size: 20,
      visible: true,
      type: 'dot',
      config: {
        color: '#252a3d',
        thickness: 2
      }
    },
    background: {
      backgroundColor: '#131623'
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

  // ── 注册所有自定义节点（Dify 风格卡片 + 具名锚点）──────────────────
  const PORT_ROW_H = 28  // 每个端口行高度（px）
  const CARD_TOP   = 44  // 卡片顶部标题区高度
  const CARD_PAD   = 12  // 上下内边距

  function calcNodeHeight(inputCount: number, outputCount: number) {
    const rows = Math.max(inputCount, 1) + Math.max(outputCount, 1)
    return CARD_TOP + rows * PORT_ROW_H + CARD_PAD * 2
  }

  // 辅助：根据节点 properties 或注册表取 inputPorts / outputPorts
  function getPortsFromProps(props: any, nodeType: string) {
    const def = getNodeDefinition(nodeType)
    const inputs  = props?.inputs  || def?.inputPorts  || []
    const outputs = props?.outputs || def?.outputPorts || []
    return { inputs, outputs }
  }

  // ── 通用普通节点注册（cli-agent / variables / condition / loop / file-input / delay）
  Object.values(NODE_DEFINITIONS)
    .filter(def => def.type !== 'start' && def.type !== 'end')
    .forEach(nodeDef => {
      const { type: nodeType, meta } = nodeDef

      class NodeView extends HtmlNode {
        getText() { return null }
        setHtml(rootEl: SVGForeignObjectElement) {
          const model  = (this as any).props?.model
          const props  = model?.properties || {}
          const status = props.runStatus || 'idle'
          const { inputs, outputs } = getPortsFromProps(props, nodeType)
          const title  = props.nodeMeta?.title || meta.title
          const color  = props.nodeMeta?.color  || meta.color
          const icon   = props.nodeMeta?.icon   || meta.icon

          const borderColor =
            status === 'running' ? '#3b82f6' :
            status === 'success' ? '#10b981' :
            status === 'failed'  ? '#ef4444' : '#2d3148'
          const pulse = status === 'running'
            ? 'animation:pulse 1.2s infinite;' : ''

          const TYPE_LABEL: Record<string, string> = {
            string: '文本', number: '数字', boolean: '布尔',
            object: '对象', array: '数组',
          }
          const portRow = (p: any, side: 'in'|'out') => `
            <div style="display:flex;align-items:center;gap:6px;height:${PORT_ROW_H}px;
              padding:0 10px;${side==='out'?'justify-content:flex-end;':''}">
              ${side==='in' ? `<div style="width:8px;height:8px;border-radius:50%;
                background:#3b82f6;flex-shrink:0;"></div>` : ''}
              <span style="font-size:11px;color:#94a3b8;flex:1;
                ${side==='out'?'text-align:right;':''}
                white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                ${p.label || p.name}<span style="color:#4b5568;margin-left:4px;font-size:10px;">${TYPE_LABEL[p.valueType] || p.valueType || ''}</span>
              </span>
              ${side==='out' ? `<div style="width:8px;height:8px;border-radius:50%;
                background:#f59e0b;flex-shrink:0;"></div>` : ''}
            </div>`

          rootEl.innerHTML = `
            <style>@keyframes pulse{0%,100%{opacity:1}50%{opacity:.5}}</style>
            <div style="width:260px;border-radius:10px;background:#1e2130;
              border:1.5px solid ${borderColor};${pulse}
              box-shadow:0 2px 12px rgba(0,0,0,.3);overflow:hidden;">
              <div style="display:flex;align-items:center;gap:8px;padding:10px 12px 8px;
                border-bottom:1px solid #2d3148;">
                <div style="width:4px;height:26px;border-radius:2px;background:${color};flex-shrink:0;"></div>
                <div style="width:26px;height:26px;border-radius:7px;flex-shrink:0;
                  background:${color}33;display:flex;align-items:center;
                  justify-content:center;font-size:14px;">${icon}</div>
                <span style="font-size:13px;font-weight:600;color:#e2e8f0;
                  flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${title}</span>
                ${status==='success'?'<span style="color:#10b981;font-size:12px;">✓</span>':''}
                ${status==='failed' ?'<span style="color:#ef4444;font-size:12px;">✗</span>':''}
              </div>
              ${inputs.length  ? `<div style="border-bottom:1px solid #2d3148;">${inputs.map((p:any)=>portRow(p,'in')).join('')}</div>` : ''}
              ${outputs.length ? `<div>${outputs.map((p:any)=>portRow(p,'out')).join('')}</div>` : ''}
            </div>`
        }
      }

      class NodeModel extends HtmlNodeModel {
        initNodeData(data: any) {
          super.initNodeData(data)
          const def2 = getNodeDefinition(nodeType)
          const inputs  = data.properties?.inputs  || def2?.inputPorts  || []
          const outputs = data.properties?.outputs || def2?.outputPorts || []
          this.width  = 260
          this.height = calcNodeHeight(inputs.length, outputs.length)
          const textStr = typeof data.text === 'string' ? data.text : (data.text?.value || '')
          this.text = { value: textStr, x: 0, y: 0, draggable: false, editable: false }
        }

        // 具名锚点：输入端口左侧，输出端口右侧
        getDefaultAnchor() {
          const { x, y, width, height, id, properties } = this
          const def2 = getNodeDefinition(nodeType)
          const inputs  = (properties as any)?.inputs  || def2?.inputPorts  || []
          const outputs = (properties as any)?.outputs || def2?.outputPorts || []
          const anchors: any[] = []
          const startY = y - height / 2 + CARD_TOP + CARD_PAD + PORT_ROW_H / 2

          inputs.forEach((p: any, i: number) => {
            anchors.push({
              id: `${id}-input-${p.name}`,
              x: x - width / 2,
              y: startY + i * PORT_ROW_H,
              type: 'input',
              edgeAddable: true,
              connectable: true,
            })
          })
          const outStartY = startY + inputs.length * PORT_ROW_H
          outputs.forEach((p: any, i: number) => {
            anchors.push({
              id: `${id}-output-${p.name}`,
              x: x + width / 2,
              y: outStartY + i * PORT_ROW_H,
              type: 'output',
              edgeAddable: true,
              connectable: true,
            })
          })
          return anchors
        }
      }

      lf!.register({ type: nodeType, view: NodeView, model: NodeModel })
    })

  // ── Start 节点（绿色圆形，只有输出端口）
  ;(() => {
    class StartView extends HtmlNode {
      getText() { return null }
      setHtml(rootEl: SVGForeignObjectElement) {
        rootEl.innerHTML = `
          <div style="width:80px;height:80px;border-radius:50%;
            background:#064e3b;border:2.5px solid #10b981;
            display:flex;flex-direction:column;align-items:center;
            justify-content:center;box-shadow:0 0 16px #10b98140;">
            <span style="font-size:20px;">▶</span>
            <span style="font-size:10px;color:#6ee7b7;margin-top:2px;">开始</span>
          </div>`
      }
    }
    class StartModel extends HtmlNodeModel {
      initNodeData(data: any) {
        super.initNodeData(data)
        this.width = 80; this.height = 80
        this.text = { value: '', x: 0, y: 0, draggable: false, editable: false }
      }
      getDefaultAnchor() {
        return [{ id: `${this.id}-output-trigger`, x: this.x + 40, y: this.y, type: 'output', edgeAddable: true, connectable: true }]
      }
    }
    lf!.register({ type: 'start', view: StartView, model: StartModel })
  })()

  // ── End 节点（红色圆形，只有输入端口）
  ;(() => {
    class EndView extends HtmlNode {
      getText() { return null }
      setHtml(rootEl: SVGForeignObjectElement) {
        rootEl.innerHTML = `
          <div style="width:80px;height:80px;border-radius:50%;
            background:#450a0a;border:2.5px solid #ef4444;
            display:flex;flex-direction:column;align-items:center;
            justify-content:center;box-shadow:0 0 16px #ef444440;">
            <span style="font-size:20px;">⏹</span>
            <span style="font-size:10px;color:#fca5a5;margin-top:2px;">结束</span>
          </div>`
      }
    }
    class EndModel extends HtmlNodeModel {
      initNodeData(data: any) {
        super.initNodeData(data)
        this.width = 80; this.height = 80
        this.text = { value: '', x: 0, y: 0, draggable: false, editable: false }
      }
      getDefaultAnchor() {
        return [{ id: `${this.id}-input-result`, x: this.x - 40, y: this.y, type: 'input', edgeAddable: true, connectable: true }]
      }
    }
    lf!.register({ type: 'end', view: EndView, model: EndModel })
  })()

  // 监听节点点击
  lf.on('node:click', ({ data }) => {
    // 深拷贝避免直接引用 LogicFlow 内部对象导致的递归更新
    selectedNode.value = JSON.parse(JSON.stringify(data))
    rightPanelOpen.value = true  // 点击节点自动展开右侧面板
  })
  
  // 监听空白点击
  lf.on('blank:click', () => {
    selectedNode.value = null
    // 不自动收起，用户可以手动收起
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
          properties: { ...n.config, nodeType: n.type, inputs: n.inputs, outputs: n.outputs, nodeMeta: n.nodeMeta }
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
  const { getNodeDefinition } = useNodeDefinitions()
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
  if (!lf) return
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
        config:  n.properties?.config  || {},
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
  await handleSave()  // 先保存
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

/* 编辑器主体：画布全屏 + 悬浮面板 */
.editor-content {
  flex: 1;
  position: relative;
  overflow: hidden;
}

/* 画布全屏 */
.canvas-container {
  width: 100%;
  height: 100%;
  /* 编辑器固定深色背景，不跟随主题（节点卡片硬编码深色） */
  background-color: #131623;
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
