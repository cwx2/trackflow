<template>
  <a-modal
    :visible="visible"
    :title="editMode ? '编辑 Agent 角色' : '新建 Agent 角色'"
    :width="720"
    :footer="false"
    :mask-closable="false"
    @cancel="$emit('close')"
  >
    <div class="wizard-container">
      <a-steps :current="currentStep" size="small" class="wizard-steps">
        <a-step title="类型" />
        <a-step title="基本信息" />
        <a-step :title="form.providerType === 'cli' ? '命令配置' : '服务配置'" />
        <a-step title="输出格式" />
      </a-steps>

      <div class="wizard-body">
        <!-- Step 1: Provider Type -->
        <div v-if="currentStep === 0" class="step-content">
          <p class="step-desc">选择 Agent 角色与外部 AI/工具通信的方式</p>
          <div class="provider-cards">
            <div
              class="provider-card"
              :class="{ selected: form.providerType === 'http' || form.providerType === 'openai_compatible' }"
              @click="selectProvider('http')"
            >
              <div class="card-icon">🌐</div>
              <div class="card-title">HTTP / OpenAI</div>
              <div class="card-desc">调用外部 AI 服务（OpenAI、本地 Ollama 等）</div>
              <div class="card-hint">适合：代码分析、文本生成、问题解答</div>
            </div>
            <div
              class="provider-card"
              :class="{ selected: form.providerType === 'cli' }"
              @click="selectProvider('cli')"
            >
              <div class="card-icon">💻</div>
              <div class="card-title">CLI 工具</div>
              <div class="card-desc">执行本机命令行程序</div>
              <div class="card-hint">适合：kiro-cli、自定义脚本、代码工具</div>
            </div>
          </div>
        </div>

        <!-- Step 2: Basic Info -->
        <div v-if="currentStep === 1" class="step-content">
          <a-form :model="form" layout="vertical">
            <a-form-item label="角色名称" required>
              <a-input v-model="form.name" placeholder="如：代码审核 Agent、文档生成 Agent" :max-length="100" />
            </a-form-item>
            <a-form-item label="描述">
              <a-textarea v-model="form.description" placeholder="简要说明这个角色的职责" :auto-size="{ minRows: 2, maxRows: 4 }" :max-length="2000" />
            </a-form-item>
            <a-form-item label="系统提示词（System Prompt）">
              <a-textarea
                v-model="form.systemPrompt"
                placeholder="定义 AI 的行为和角色，例如：你是一个专业的代码审核员，负责检查 Java 后端代码..."
                :auto-size="{ minRows: 4, maxRows: 10 }"
              />
            </a-form-item>
            <a-form-item label="启用">
              <a-switch v-model="form.enabled" />
            </a-form-item>
          </a-form>
        </div>

        <!-- Step 3a: HTTP Config -->
        <div v-if="currentStep === 2 && (form.providerType === 'http' || form.providerType === 'openai_compatible')" class="step-content">
          <a-form :model="httpConfig" layout="vertical">
            <a-form-item label="服务地址" required>
              <a-input v-model="httpConfig.endpoint" placeholder="https://api.openai.com/v1/chat/completions" />
            </a-form-item>
            <a-form-item label="模型名称">
              <a-input v-model="form.model" placeholder="如 gpt-4o、qwen-turbo、deepseek-coder" />
            </a-form-item>
            <a-form-item label="API Key 环境变量名">
              <a-input v-model="httpConfig.apiKeyEnv" placeholder="服务器上的环境变量名，如 OPENAI_API_KEY" />
              <div class="field-hint">Agent 执行时从服务器环境变量读取密钥，无需在此输入实际 Key</div>
            </a-form-item>
            <a-form-item label="超时时间（秒）">
              <a-input-number v-model="httpConfig.timeoutSeconds" :min="10" :max="3600" :default-value="300" />
            </a-form-item>
            <a-form-item label="允许访问的域名">
              <a-input-tag v-model="httpConfig.allowedHosts" placeholder="回车添加，如 api.openai.com" allow-clear />
              <div class="field-hint">限定 Agent 只能请求这些域名，留空表示仅允许服务地址所在域</div>
            </a-form-item>
          </a-form>
        </div>

        <!-- Step 3b: CLI Config -->
        <div v-if="currentStep === 2 && form.providerType === 'cli'" class="step-content">
          <a-form :model="cliConfig" layout="vertical">
            <a-form-item label="命令" required>
              <a-input v-model="cliConfig.command" placeholder="如 kiro、python3、node" />
            </a-form-item>
            <a-form-item label="允许的命令白名单">
              <a-input-tag v-model="cliConfig.allowedCommands" placeholder="回车添加，如 kiro、git" allow-clear />
              <div class="field-hint">安全白名单，Agent 只能执行这些命令</div>
            </a-form-item>
            <a-form-item label="固定参数">
              <a-input v-model="cliConfig.arguments" placeholder="如 --no-interactive（多个用空格分隔）" />
              <div class="field-hint">每次执行时自动追加的固定命令行参数</div>
            </a-form-item>
            <a-form-item label="Prompt 传递方式">
              <a-radio-group v-model="cliConfig.promptMode" type="button">
                <a-radio value="argument">命令行参数</a-radio>
                <a-radio value="stdin">标准输入</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item label="工作目录">
              <a-input v-model="cliConfig.defaultWorkDir" placeholder="默认工作目录路径" />
            </a-form-item>
            <a-form-item label="允许的目录范围">
              <a-input-tag v-model="cliConfig.allowedRoots" placeholder="回车添加，如 D:/project/YT" allow-clear />
              <div class="field-hint">Agent 只能在这些目录下操作文件</div>
            </a-form-item>
            <a-form-item label="超时时间（秒）">
              <a-input-number v-model="cliConfig.timeoutSeconds" :min="10" :max="7200" :default-value="1800" />
            </a-form-item>
          </a-form>
        </div>

        <!-- Step 4: Output Schema -->
        <div v-if="currentStep === 3" class="step-content">
          <div class="output-schema-toggle">
            <a-switch v-model="outputSchemaEnabled" />
            <span class="toggle-label">要求结构化输出</span>
          </div>
          <div v-if="!outputSchemaEnabled" class="schema-hint">
            关闭时 Agent 自由输出文本，无格式约束。
          </div>
          <div v-if="outputSchemaEnabled" class="schema-fields">
            <p class="step-desc">定义 Agent 输出的结构化字段</p>
            <div class="field-table">
              <div class="field-table-header">
                <span class="col-name">字段名</span>
                <span class="col-type">类型</span>
                <span class="col-required">必填</span>
                <span class="col-action"></span>
              </div>
              <div v-for="(field, idx) in schemaFields" :key="idx" class="field-table-row">
                <a-input v-model="field.name" placeholder="字段名" size="small" class="col-name" />
                <a-select v-model="field.type" size="small" class="col-type">
                  <a-option value="string">string</a-option>
                  <a-option value="number">number</a-option>
                  <a-option value="boolean">boolean</a-option>
                  <a-option value="array">array</a-option>
                  <a-option value="object">object</a-option>
                </a-select>
                <a-checkbox v-model="field.required" class="col-required" />
                <a-button type="text" size="small" status="danger" class="col-action" @click="schemaFields.splice(idx, 1)">
                  删除
                </a-button>
              </div>
            </div>
            <a-button type="dashed" size="small" long @click="schemaFields.push({ name: '', type: 'string', required: false })">
              + 添加字段
            </a-button>
          </div>
        </div>
      </div>

      <div class="wizard-footer">
        <a-button v-if="currentStep > 0" @click="currentStep--">上一步</a-button>
        <div class="spacer" />
        <a-button v-if="currentStep < 3" type="primary" :disabled="!canNext" @click="currentStep++">下一步</a-button>
        <a-button v-if="currentStep === 3" type="primary" :loading="submitting" @click="submit">
          {{ editMode ? '保存修改' : '完成创建' }}
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { automationApi, type AutomationRoleProfile, type AutomationRoleProfileDTO } from '@/api/automation'

const props = defineProps<{
  visible: boolean
  editRole?: AutomationRoleProfile | null
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const editMode = computed(() => !!props.editRole)
const currentStep = ref(0)
const submitting = ref(false)

const form = reactive({
  name: '',
  description: '',
  providerType: '' as '' | 'cli' | 'http' | 'openai_compatible',
  model: '',
  systemPrompt: '',
  enabled: true,
})

const httpConfig = reactive({
  endpoint: '',
  apiKeyEnv: '',
  timeoutSeconds: 300,
  allowedHosts: [] as string[],
})

const cliConfig = reactive({
  command: '',
  allowedCommands: [] as string[],
  arguments: '',
  promptMode: 'argument' as 'argument' | 'stdin',
  defaultWorkDir: '',
  allowedRoots: [] as string[],
  timeoutSeconds: 1800,
})

const outputSchemaEnabled = ref(false)
const schemaFields = reactive<Array<{ name: string; type: string; required: boolean }>>([])

// Reset state when modal opens
watch(() => props.visible, (val) => {
  if (val) {
    currentStep.value = 0
    if (props.editRole) {
      loadFromRole(props.editRole)
    } else {
      resetForm()
    }
  }
})

function resetForm() {
  form.name = ''
  form.description = ''
  form.providerType = ''
  form.model = ''
  form.systemPrompt = ''
  form.enabled = true
  Object.assign(httpConfig, { endpoint: '', apiKeyEnv: '', timeoutSeconds: 300, allowedHosts: [] })
  Object.assign(cliConfig, { command: '', allowedCommands: [], arguments: '', promptMode: 'argument', defaultWorkDir: '', allowedRoots: [], timeoutSeconds: 1800 })
  outputSchemaEnabled.value = false
  schemaFields.splice(0)
}

function loadFromRole(role: AutomationRoleProfile) {
  form.name = role.name
  form.description = role.description || ''
  form.providerType = role.providerType
  form.model = role.model || ''
  form.systemPrompt = role.systemPrompt || ''
  form.enabled = role.enabled

  // Parse toolPolicy JSON back to form fields
  try {
    const policy = JSON.parse(role.toolPolicy || '{}')
    if (role.providerType === 'cli') {
      cliConfig.command = policy.command || ''
      cliConfig.allowedCommands = Array.isArray(policy.allowedCommands) ? policy.allowedCommands : []
      cliConfig.arguments = Array.isArray(policy.arguments) ? policy.arguments.join(' ') : (policy.arguments || '')
      cliConfig.promptMode = policy.promptMode || 'argument'
      cliConfig.timeoutSeconds = policy.timeoutSeconds || 1800
      // workspacePolicy
      try {
        const ws = JSON.parse(role.workspacePolicy || '{}')
        cliConfig.defaultWorkDir = ws.defaultWorkDir || ''
        cliConfig.allowedRoots = Array.isArray(ws.allowedRoots) ? ws.allowedRoots : []
      } catch { /* ignore */ }
    } else {
      httpConfig.endpoint = policy.endpoint || ''
      httpConfig.apiKeyEnv = policy.apiKeyEnv || ''
      httpConfig.timeoutSeconds = policy.timeoutSeconds || 300
      httpConfig.allowedHosts = Array.isArray(policy.allowedHosts) ? policy.allowedHosts : []
    }
  } catch { /* ignore */ }

  // Parse outputSchema
  try {
    const schema = JSON.parse(role.outputSchema || '{}')
    if (schema.properties && Object.keys(schema.properties).length > 0) {
      outputSchemaEnabled.value = true
      schemaFields.splice(0)
      const required = Array.isArray(schema.required) ? schema.required : []
      for (const [name, def] of Object.entries(schema.properties)) {
        schemaFields.push({
          name,
          type: (def as any)?.type || 'string',
          required: required.includes(name),
        })
      }
    } else {
      outputSchemaEnabled.value = false
      schemaFields.splice(0)
    }
  } catch {
    outputSchemaEnabled.value = false
    schemaFields.splice(0)
  }
}

function selectProvider(type: 'http' | 'cli') {
  form.providerType = type === 'http' ? 'openai_compatible' : 'cli'
}

const canNext = computed(() => {
  if (currentStep.value === 0) return form.providerType !== ''
  if (currentStep.value === 1) return form.name.trim() !== ''
  if (currentStep.value === 2) {
    if (form.providerType === 'cli') return cliConfig.command.trim() !== ''
    return httpConfig.endpoint.trim() !== ''
  }
  return true
})

function buildToolPolicy(): string {
  if (form.providerType === 'cli') {
    const args = cliConfig.arguments.trim()
      ? cliConfig.arguments.trim().split(/\s+/)
      : []
    return JSON.stringify({
      command: cliConfig.command,
      arguments: args,
      allowedCommands: cliConfig.allowedCommands,
      promptMode: cliConfig.promptMode,
      timeoutSeconds: cliConfig.timeoutSeconds,
    })
  } else {
    return JSON.stringify({
      endpoint: httpConfig.endpoint,
      apiKeyEnv: httpConfig.apiKeyEnv,
      allowedHosts: httpConfig.allowedHosts,
      timeoutSeconds: httpConfig.timeoutSeconds,
    })
  }
}

function buildWorkspacePolicy(): string {
  if (form.providerType === 'cli') {
    return JSON.stringify({
      allowedRoots: cliConfig.allowedRoots,
      defaultWorkDir: cliConfig.defaultWorkDir,
    })
  }
  return '{}'
}

function buildOutputSchema(): string {
  if (!outputSchemaEnabled.value || schemaFields.length === 0) return '{}'
  const properties: Record<string, any> = {}
  const required: string[] = []
  for (const field of schemaFields) {
    if (!field.name.trim()) continue
    properties[field.name.trim()] = { type: field.type }
    if (field.required) required.push(field.name.trim())
  }
  if (Object.keys(properties).length === 0) return '{}'
  const schema: any = { type: 'object', properties }
  if (required.length > 0) schema.required = required
  return JSON.stringify(schema)
}

async function submit() {
  if (!form.name.trim()) {
    Message.warning('请输入角色名称')
    return
  }
  submitting.value = true
  try {
    const dto: AutomationRoleProfileDTO = {
      name: form.name.trim(),
      description: form.description || undefined,
      providerType: form.providerType as 'cli' | 'http' | 'openai_compatible',
      model: form.model || undefined,
      systemPrompt: form.systemPrompt || undefined,
      toolPolicy: buildToolPolicy(),
      workspacePolicy: buildWorkspacePolicy(),
      outputSchema: buildOutputSchema(),
      enabled: form.enabled,
    }

    const res = props.editRole
      ? await automationApi.updateRole(props.editRole.id, dto)
      : await automationApi.createRole(dto)

    if (res.code === 0) {
      Message.success(props.editRole ? '角色已更新' : '角色已创建')
      emit('saved')
      emit('close')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.wizard-container {
  display: flex;
  flex-direction: column;
  min-height: 460px;
}

.wizard-steps {
  margin-bottom: 24px;
}

.wizard-body {
  flex: 1;
  overflow-y: auto;
}

.step-content {
  padding: 0 4px;
}

.step-desc {
  color: var(--tf-text-secondary, var(--color-text-2));
  font-size: 13px;
  margin-bottom: 16px;
}

.provider-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.provider-card {
  border: 2px solid var(--color-border-2);
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}

.provider-card:hover {
  border-color: var(--color-primary-light-2);
  background: var(--color-fill-1);
}

.provider-card.selected {
  border-color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
}

.card-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: var(--tf-text-secondary, var(--color-text-2));
  margin-bottom: 6px;
}

.card-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary, var(--color-text-3));
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--tf-text-tertiary, var(--color-text-3));
}

.output-schema-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.toggle-label {
  font-size: 14px;
  font-weight: 500;
}

.schema-hint {
  color: var(--tf-text-tertiary, var(--color-text-3));
  font-size: 13px;
}

.schema-fields {
  margin-top: 8px;
}

.field-table {
  margin-bottom: 12px;
}

.field-table-header,
.field-table-row {
  display: grid;
  grid-template-columns: 1fr 120px 60px 60px;
  gap: 8px;
  align-items: center;
}

.field-table-header {
  font-size: 12px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  font-weight: 500;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--color-border-1);
  margin-bottom: 8px;
}

.field-table-row {
  margin-bottom: 6px;
}

.col-required {
  justify-self: center;
}

.col-action {
  justify-self: center;
}

.wizard-footer {
  display: flex;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--color-border-1);
  margin-top: 16px;
}

.spacer {
  flex: 1;
}
</style>
