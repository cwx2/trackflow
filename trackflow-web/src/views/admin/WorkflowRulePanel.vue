<template>
  <div class="workflow-rules">
    <!-- 工具栏 -->
    <div class="rules-toolbar">
      <a-space>
        <a-input
          v-model="searchKeyword"
          placeholder="搜索规则..."
          style="width: 200px"
          allow-clear
        >
          <template #prefix><icon-search /></template>
        </a-input>
        <a-select
          v-model="filterEvent"
          placeholder="触发事件"
          allow-clear
          style="width: 160px"
        >
          <a-option value="issue_created">工单创建时</a-option>
          <a-option value="field_changed">字段变更时</a-option>
        </a-select>
      </a-space>
      <a-space>
        <a-tooltip content="导入规则 JSON 文件">
          <a-button @click="triggerImportFileInput">
            <template #icon><icon-upload /></template>
            导入
          </a-button>
        </a-tooltip>
        <a-tooltip content="导出所有规则为 JSON 文件">
          <a-button @click="handleExportAll">
            <template #icon><icon-download /></template>
            导出
          </a-button>
        </a-tooltip>
        <a-button type="primary" @click="showCreateModal">
          <template #icon><icon-plus /></template>
          创建规则
        </a-button>
      </a-space>
      <!-- 隐藏的文件上传 input -->
      <input
        ref="importFileInput"
        type="file"
        accept=".json"
        style="display: none"
        @change="handleImportFileChange"
      />
    </div>

    <!-- 规则列表 -->
    <a-spin :loading="loading">
      <div v-if="filteredRules.length > 0" class="rules-list">
        <div
          v-for="rule in filteredRules"
          :key="rule.id"
          class="rule-card"
          :class="{ disabled: !rule.enabled, 'has-errors': ruleValidationErrors.has(rule.id) }"
        >
          <div class="rule-card-top">
            <div class="rule-main">
            <div class="rule-header">
              <span class="rule-name">{{ rule.name }}</span>
              <a-tag :color="eventColor(rule.triggerEvent)" size="small">
                {{ eventLabel(rule.triggerEvent) }}
              </a-tag>
              <a-tag v-if="rule.triggerField" size="small" color="gray">
                字段: {{ fieldLabel(rule.triggerField) }}
              </a-tag>
              <a-tag v-if="rule.projectId === null" size="small" color="orangered">
                全局
              </a-tag>
            </div>
            <div v-if="rule.description" class="rule-description">{{ rule.description }}</div>
            <div class="rule-summary">
              <span class="summary-section">
                <span class="summary-label">条件:</span>
                {{ conditionSummary(rule.conditionJson) }}
              </span>
              <span class="summary-divider">→</span>
              <span class="summary-section">
                <span class="summary-label">动作:</span>
                {{ actionSummary(rule.actionJson) }}
              </span>
            </div>
          </div>
          <div class="rule-actions">
            <a-tooltip content="导出规则">
              <a-button size="mini" @click="handleExportSingle(rule)">
                <template #icon><icon-download /></template>
              </a-button>
            </a-tooltip>
            <a-tooltip content="校验规则引用资源有效性">
              <a-button
                size="mini"
                :loading="validatingRuleId === rule.id"
                @click="handleValidateRule(rule)"
              >
                <template #icon><icon-check-circle /></template>
              </a-button>
            </a-tooltip>
            <a-switch
              :model-value="rule.enabled"
              size="small"
              @change="handleToggle(rule)"
            />
            <a-button size="mini" @click="handleViewLogs(rule)">日志</a-button>
            <a-button size="mini" @click="handleEdit(rule)">编辑</a-button>
            <a-popconfirm
              content="确定删除此规则？"
              @ok="handleDelete(rule)"
            >
              <a-button size="mini" type="text" status="danger">删除</a-button>
            </a-popconfirm>
          </div>
          </div><!-- end rule-card-top -->
          <!-- 有效性错误提示 -->
          <div v-if="ruleValidationErrors.get(rule.id)" class="rule-validation-errors">
            <icon-exclamation-circle-fill style="color: var(--color-warning-6); flex-shrink: 0" />
            <div class="validation-error-list">
              <span class="validation-error-title">配置问题：</span>
              <ul>
                <li v-for="(err, idx) in ruleValidationErrors.get(rule.id)" :key="idx">{{ err }}</li>
              </ul>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <icon-thunderbolt style="font-size: 48px; color: var(--color-text-4)" />
        <h3>暂无自动化规则</h3>
        <p>创建 on-change 规则，在工单创建或字段变更时自动执行动作。</p>
        <a-button type="primary" @click="showCreateModal">
          <template #icon><icon-plus /></template>
          创建第一条规则
        </a-button>
      </div>
    </a-spin>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="modalVisible"
      :title="editingRule ? '编辑规则' : '创建规则'"
      :width="680"
      @ok="handleSubmit"
      @cancel="modalVisible = false"
      :ok-loading="submitting"
      ok-text="保存规则"
      cancel-text="取消"
      unmount-on-close
    >
      <a-form :model="formData" layout="vertical" ref="formRef">
        <!-- 基本信息 -->
        <a-form-item label="规则名称" field="name" :rules="[{ required: true, message: '请输入规则名称' }]">
          <a-input v-model="formData.name" placeholder="如：Bug 创建时自动设置高优先级" :max-length="100" />
        </a-form-item>

        <a-form-item label="描述" field="description">
          <a-textarea v-model="formData.description" placeholder="规则功能说明（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="触发事件" field="triggerEvent" :rules="[{ required: true, message: '请选择触发事件' }]">
              <a-select v-model="formData.triggerEvent">
                <a-option value="issue_created">工单创建时</a-option>
                <a-option value="field_changed">字段变更时</a-option>
                <a-option value="comment_added">评论添加时</a-option>
                <a-option value="attachment_added">附件被添加时</a-option>
                <a-option value="attachment_removed">附件被移除时</a-option>
                <a-option value="link_added">关联工单被添加时</a-option>
                <a-option value="link_removed">关联工单被移除时</a-option>
                <a-option value="work_item_added">工时被记录时</a-option>
                <a-option value="work_item_deleted">工时被删除时</a-option>
                <a-option value="issue_resolved">工单变为已解决时</a-option>
                <a-option value="issue_unresolved">工单变为未解决时</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              v-if="formData.triggerEvent === 'field_changed'"
              label="监听字段"
              field="triggerField"
            >
              <a-select v-model="formData.triggerField" placeholder="全部字段" allow-clear>
                <a-option value="status_id">状态</a-option>
                <a-option value="issue_type">工单类型</a-option>
                <a-option value="priority">优先级</a-option>
                <a-option value="assignee">负责人</a-option>
                <a-option value="sprint">迭代</a-option>
                <a-option value="title">标题</a-option>
                <a-option value="due_date">截止日期</a-option>
              </a-select>
              <template #extra>为空表示任意字段变更都触发</template>
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 前置条件 -->
        <a-form-item label="前置条件" field="conditions">
          <template #extra>条件组内为 {{ formData.conditionLogic === 'or' ? 'OR（任一满足即触发）' : 'AND（全部满足才触发）' }} 关系</template>
          <div class="condition-list">
            <!-- 顶层逻辑切换 -->
            <div class="condition-logic-toggle">
              <a-radio-group v-model="formData.conditionLogic" type="button" size="small">
                <a-radio value="and">AND（全部满足）</a-radio>
                <a-radio value="or">OR（任一满足）</a-radio>
              </a-radio-group>
            </div>

            <div v-for="(cond, idx) in formData.conditions" :key="idx" class="condition-row" :class="{ 'condition-negated': cond.negated }">
              <!-- NOT 切换 -->
              <a-tooltip content="取反（NOT）">
                <a-button
                  :type="cond.negated ? 'primary' : 'text'"
                  size="mini"
                  :class="{ 'not-active': cond.negated }"
                  @click="cond.negated = !cond.negated"
                >
                  NOT
                </a-button>
              </a-tooltip>

              <!-- 条件类型选择 -->
              <a-select v-model="cond.conditionType" style="width: 160px" placeholder="条件类型" @change="() => onConditionTypeChange(cond)">
                <a-optgroup label="字段匹配">
                  <a-option value="field_check">字段值检查</a-option>
                </a-optgroup>
                <a-optgroup label="工单状态类">
                  <a-option value="issue_resolved">工单已解决</a-option>
                  <a-option value="issue_has_tag">工单有标签</a-option>
                  <a-option value="issue_attribute_count">工单属性数量</a-option>
                  <a-option value="issue_created_within">创建于 N 天内</a-option>
                  <a-option value="issue_updated_within">更新于 N 天内</a-option>
                </a-optgroup>
                <a-optgroup label="用户类">
                  <a-option value="created_by">创建者是</a-option>
                  <a-option value="updated_by">更新者是</a-option>
                  <a-option value="user_has_role">触发者角色</a-option>
                </a-optgroup>
                <a-optgroup label="项目类">
                  <a-option value="issue_in_project">属于项目</a-option>
                </a-optgroup>
              </a-select>

              <!-- field_check: 旧的字段+操作符+值模式 -->
              <template v-if="cond.conditionType === 'field_check'">
                <a-select v-model="cond.field" style="width: 120px" placeholder="字段">
                  <a-option value="type">工单类型</a-option>
                  <a-option value="priority">优先级</a-option>
                  <a-option value="assignee">负责人</a-option>
                  <a-option value="status">状态</a-option>
                  <a-option value="sprint">迭代</a-option>
                  <a-option value="due_date">截止日期</a-option>
                </a-select>
                <a-select v-model="cond.operator" style="width: 140px" placeholder="操作符" @change="() => { cond.value = '' }">
                  <a-optgroup label="当前值匹配">
                    <a-option value="equals">等于</a-option>
                    <a-option value="not_equals">不等于</a-option>
                    <a-option value="contains">包含</a-option>
                    <a-option value="in">属于（逗号分隔）</a-option>
                    <a-option value="is_empty">为空</a-option>
                    <a-option value="is_not_empty">不为空</a-option>
                  </a-optgroup>
                  <a-optgroup label="时间条件">
                    <a-option value="overdue">已逾期</a-option>
                    <a-option value="due_within_days">N天内到期</a-option>
                  </a-optgroup>
                  <a-optgroup
                    v-if="formData.triggerEvent === 'field_changed' && formData.triggerField"
                    label="变更前值匹配（旧值）"
                  >
                    <a-option value="old_value_equals">旧值等于</a-option>
                    <a-option value="old_value_not_equals">旧值不等于</a-option>
                    <a-option value="old_value_in">旧值属于</a-option>
                    <a-option value="old_value_is_empty">旧值为空</a-option>
                    <a-option value="old_value_is_not_empty">旧值不为空</a-option>
                  </a-optgroup>
                </a-select>
                <a-input
                  v-if="!['is_empty', 'is_not_empty', 'old_value_is_empty', 'old_value_is_not_empty', 'overdue'].includes(cond.operator)"
                  v-model="cond.value"
                  style="flex: 1"
                  :placeholder="cond.operator === 'due_within_days' ? '天数（如 3）' : '值（如 Bug, Critical）'"
                />
              </template>

              <!-- issue_resolved: 无额外参数 -->
              <!-- issue_has_tag: 标签选择 -->
              <template v-else-if="cond.conditionType === 'issue_has_tag'">
                <a-input v-model="cond.tagId" style="flex: 1" placeholder="标签 ID" />
              </template>

              <!-- issue_attribute_count: 属性 + 操作符 + 值 -->
              <template v-else-if="cond.conditionType === 'issue_attribute_count'">
                <a-select v-model="cond.attribute" style="width: 100px" placeholder="属性">
                  <a-option value="comments">评论数</a-option>
                  <a-option value="links">关联数</a-option>
                  <a-option value="attachments">附件数</a-option>
                </a-select>
                <a-select v-model="cond.operator" style="width: 100px" placeholder="操作符">
                  <a-option value="greater_than">大于</a-option>
                  <a-option value="less_than">小于</a-option>
                  <a-option value="equals">等于</a-option>
                  <a-option value="greater_than_or_equals">≥</a-option>
                  <a-option value="less_than_or_equals">≤</a-option>
                </a-select>
                <a-input-number v-model="cond.value" style="width: 80px" placeholder="数量" :min="0" />
              </template>

              <!-- issue_created_within / issue_updated_within: 天数 -->
              <template v-else-if="cond.conditionType === 'issue_created_within' || cond.conditionType === 'issue_updated_within'">
                <a-input-number v-model="cond.days" style="width: 100px" placeholder="天数" :min="1" />
                <span class="condition-suffix">天内</span>
              </template>

              <!-- created_by / updated_by: 用户选择 -->
              <template v-else-if="cond.conditionType === 'created_by' || cond.conditionType === 'updated_by'">
                <a-select v-model="cond.userId" style="flex: 1" placeholder="选择用户" allow-search>
                  <a-option value="current_user">当前操作用户</a-option>
                </a-select>
              </template>

              <!-- user_has_role: 角色选择 -->
              <template v-else-if="cond.conditionType === 'user_has_role'">
                <a-select v-model="cond.role" style="flex: 1" placeholder="选择角色">
                  <a-option value="project_admin">项目管理员</a-option>
                  <a-option value="tech_lead">技术负责人</a-option>
                  <a-option value="developer">开发人员</a-option>
                  <a-option value="product_manager">产品经理</a-option>
                  <a-option value="tester">测试人员</a-option>
                  <a-option value="observer">观察者</a-option>
                </a-select>
              </template>

              <!-- issue_in_project: 项目选择 -->
              <template v-else-if="cond.conditionType === 'issue_in_project'">
                <a-input v-model="cond.projectId" style="flex: 1" placeholder="项目 ID" />
              </template>

              <a-button type="text" status="danger" size="mini" @click="removeCondition(idx)">
                <icon-delete />
              </a-button>
            </div>
            <a-button type="dashed" size="small" @click="addCondition" long>
              <template #icon><icon-plus /></template>
              添加条件
            </a-button>
          </div>
        </a-form-item>

        <!-- 执行动作 -->
        <a-form-item label="执行动作" field="actions" :rules="[{ validator: validateActions }]">
          <div class="action-list">
            <div v-for="(act, idx) in formData.actions" :key="idx" class="action-row">
              <a-select v-model="act.type" style="width: 140px" placeholder="动作类型">
                <a-option value="set_field">设置字段</a-option>
                <a-option value="add_tag">添加标签</a-option>
                <a-option value="remove_tag">移除标签</a-option>
                <a-option value="add_comment">添加评论</a-option>
                <a-option value="require_field">要求必填字段</a-option>
                <a-option value="send_email">发送邮件通知</a-option>
                <a-option value="show_alert">显示提示消息</a-option>
                <a-option value="update_summary">修改工单标题</a-option>
                <a-option value="update_description">修改工单描述</a-option>
                <a-option value="copy_issue">克隆工单</a-option>
                <a-option value="move_to_project">移动到项目</a-option>
                <a-option value="add_work_item">添加工时</a-option>
                <a-option value="add_vote">添加投票</a-option>
                <a-option value="remove_vote">移除投票</a-option>
              </a-select>

              <!-- set_field -->
              <template v-if="act.type === 'set_field'">
                <a-select v-model="act.field" style="width: 120px" placeholder="字段" @change="() => { act.value = '' }">
                  <a-option value="priority">优先级</a-option>
                  <a-option value="assignee">负责人</a-option>
                  <a-option value="issue_type">工单类型</a-option>
                  <a-option value="status">状态</a-option>
                  <a-option value="sprint">迭代</a-option>
                  <a-option value="due_date">截止日期</a-option>
                </a-select>
                <!-- 状态：下拉选择器 -->
                <a-select
                  v-if="act.field === 'status'"
                  v-model="act.value"
                  style="flex: 1"
                  placeholder="选择目标状态"
                  :loading="statusesLoading"
                  allow-search
                >
                  <a-option
                    v-for="s in availableStatuses"
                    :key="s.id"
                    :value="s.id"
                  >
                    <span class="status-option">
                      <span
                        class="status-dot"
                        :style="{ backgroundColor: s.color || '#999' }"
                      ></span>
                      {{ s.name }}
                    </span>
                  </a-option>
                </a-select>
                <!-- 迭代：下拉选择器 -->
                <a-select
                  v-else-if="act.field === 'sprint'"
                  v-model="act.value"
                  style="flex: 1"
                  placeholder="选择目标迭代"
                  :loading="sprintsLoading"
                  allow-search
                >
                  <a-option
                    v-for="sp in availableSprints"
                    :key="sp.id"
                    :value="sp.id"
                  >{{ sp.name }}</a-option>
                </a-select>
                <!-- 截止日期：支持绝对日期或相对值 (+7d) -->
                <a-input
                  v-else-if="act.field === 'due_date'"
                  v-model="act.value"
                  style="flex: 1"
                  placeholder="日期 (YYYY-MM-DD) 或相对值 (+7d, -3d)"
                />
                <!-- 其他字段：文本输入 -->
                <a-input
                  v-else
                  v-model="act.value"
                  style="flex: 1"
                  placeholder="新值"
                />
              </template>

              <!-- add_tag -->
              <template v-else-if="act.type === 'add_tag'">
                <a-input v-model="act.tagId" style="flex: 1" placeholder="标签 ID" />
              </template>

              <!-- add_comment -->
              <template v-else-if="act.type === 'add_comment'">
                <VariableInput
                  v-model="act.content"
                  placeholder="评论内容（支持变量插值，如 {issue.summary}）"
                  type="input"
                  style="flex: 1"
                />
              </template>

              <!-- remove_tag -->
              <template v-else-if="act.type === 'remove_tag'">
                <a-input v-model="act.tagId" style="flex: 1" placeholder="标签 ID" />
              </template>

              <!-- require_field -->
              <template v-else-if="act.type === 'require_field'">
                <a-select v-model="act.field" style="width: 120px" placeholder="检查字段">
                  <a-option value="assignee">负责人</a-option>
                  <a-option value="priority">优先级</a-option>
                  <a-option value="due_date">截止日期</a-option>
                  <a-option value="sprint">迭代</a-option>
                  <a-option value="description">描述</a-option>
                </a-select>
                <a-input v-model="act.errorMessage" style="flex: 1" placeholder="阻断提示消息（如：请先分配负责人）" />
              </template>

              <!-- send_email -->
              <template v-else-if="act.type === 'send_email'">
                <div style="display: flex; flex-direction: column; gap: 6px; flex: 1">
                  <a-select v-model="act.target" style="width: 100%" placeholder="收件人">
                    <a-option value="reporter">报告人</a-option>
                    <a-option value="assignee">负责人</a-option>
                    <a-option value="creator">规则创建者</a-option>
                  </a-select>
                  <VariableInput
                    v-model="act.subject"
                    placeholder="邮件主题（支持变量插值，如 {issue.summary}）"
                    type="input"
                  />
                  <VariableInput
                    v-model="act.body"
                    placeholder="邮件正文（支持变量插值）"
                    type="textarea"
                    :auto-size="{ minRows: 2, maxRows: 4 }"
                  />
                </div>
              </template>

              <!-- show_alert -->
              <template v-else-if="act.type === 'show_alert'">
                <a-select v-model="act.style" style="width: 100px" placeholder="样式">
                  <a-option value="acknowledgment">普通提示</a-option>
                  <a-option value="error">错误提示</a-option>
                </a-select>
                <VariableInput
                  v-model="act.message"
                  placeholder="提示消息（支持变量插值）"
                  type="input"
                  style="flex: 1"
                />
              </template>

              <!-- update_summary -->
              <template v-else-if="act.type === 'update_summary'">
                <VariableInput
                  v-model="act.value"
                  placeholder="新标题模板（支持变量插值，如 [{issue.type}] {issue.summary}）"
                  type="input"
                  style="flex: 1"
                />
              </template>

              <!-- update_description -->
              <template v-else-if="act.type === 'update_description'">
                <VariableInput
                  v-model="act.value"
                  placeholder="新描述模板（支持变量插值）"
                  type="textarea"
                  :auto-size="{ minRows: 2, maxRows: 4 }"
                  style="flex: 1"
                />
              </template>

              <!-- copy_issue -->
              <template v-else-if="act.type === 'copy_issue'">
                <div style="display: flex; flex-direction: column; gap: 6px; flex: 1">
                  <a-select v-model="act.targetProjectId" style="width: 100%" placeholder="目标项目（same=同项目）" allow-search>
                    <a-option value="same">同项目</a-option>
                    <a-option
                      v-for="p in allProjects"
                      :key="p.id"
                      :value="p.id"
                    >{{ p.name }} ({{ p.key }})</a-option>
                  </a-select>
                  <a-input v-model="act.summaryPrefix" placeholder="标题前缀（如 [COPY] ，支持变量）" />
                  <a-space>
                    <a-checkbox v-model="act.copyAttachments">复制附件</a-checkbox>
                    <a-checkbox v-model="act.copySprint">复制 Sprint</a-checkbox>
                  </a-space>
                </div>
              </template>

              <!-- move_to_project -->
              <template v-else-if="act.type === 'move_to_project'">
                <a-select v-model="act.targetProjectId" style="flex: 1" placeholder="目标项目" allow-search>
                  <a-option
                    v-for="p in allProjects"
                    :key="p.id"
                    :value="p.id"
                  >{{ p.name }} ({{ p.key }})</a-option>
                </a-select>
              </template>

              <!-- add_work_item -->
              <template v-else-if="act.type === 'add_work_item'">
                <div style="display: flex; gap: 8px; flex: 1">
                  <a-input-number v-model="act.duration" :min="1" :max="1440" style="width: 120px" placeholder="分钟" />
                  <a-input v-model="act.description" style="flex: 1" placeholder="工时描述（支持变量）" />
                </div>
              </template>

              <!-- add_vote / remove_vote 无需额外参数 -->
              <template v-else-if="act.type === 'add_vote' || act.type === 'remove_vote'">
                <span class="action-hint">以规则创建者身份{{ act.type === 'add_vote' ? '投票' : '取消投票' }}</span>
              </template>

              <a-button type="text" status="danger" size="mini" @click="removeAction(idx)">
                <icon-delete />
              </a-button>
            </div>
            <a-button type="dashed" size="small" @click="addAction" long>
              <template #icon><icon-plus /></template>
              添加动作
            </a-button>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 执行日志弹窗 -->
    <a-modal
      v-model:visible="logsVisible"
      :title="'执行日志 — ' + (logsRuleName || '')"
      :width="720"
      :footer="false"
      unmount-on-close
    >
      <div class="log-toolbar">
        <a-input
          v-model="logFilter"
          placeholder="按工单 ID 或错误信息过滤..."
          style="width: 240px"
          allow-clear
        >
          <template #prefix><icon-search /></template>
        </a-input>
        <a-space>
          <a-button size="small" @click="refreshLogs">
            <template #icon><icon-refresh /></template>
            刷新
          </a-button>
          <a-button size="small" @click="downloadLogs">
            <template #icon><icon-download /></template>
            下载
          </a-button>
          <a-popconfirm
            content="确定清空此规则的所有执行日志？此操作不可撤销。"
            @ok="clearLogs"
          >
            <a-button size="small" status="danger">
              <template #icon><icon-delete /></template>
              清空
            </a-button>
          </a-popconfirm>
        </a-space>
      </div>

      <a-spin :loading="logsLoading">
        <div v-if="filteredLogs.length > 0" class="logs-list">
          <div
            v-for="logEntry in filteredLogs"
            :key="logEntry.id"
            class="log-entry"
            :class="{ 'log-error-entry': logEntry.failureCount > 0, 'log-skipped-entry': logEntry.successCount === 0 && logEntry.failureCount === 0 }"
          >
            <div class="log-header">
              <span class="log-time">{{ formatLogTime(logEntry.executedAt) }}</span>
              <span class="log-result-icon">
                <template v-if="logEntry.failureCount > 0">❌</template>
                <template v-else-if="logEntry.successCount > 0">✅</template>
                <template v-else>⏭</template>
              </span>
              <span v-if="logEntry.issueKey" class="log-issue-key">
                <a :href="'/issues?key=' + logEntry.issueKey" target="_blank">{{ logEntry.issueKey }}</a>
              </span>
              <span v-else class="log-issue-key log-batch">
                匹配 {{ logEntry.matchedCount }} 个工单
              </span>
              <span class="log-duration">({{ logEntry.durationMs }}ms)</span>
            </div>
            <div v-if="logEntry.errorMessage" class="log-error-msg">
              {{ logEntry.errorMessage }}
            </div>
          </div>
        </div>
        <div v-else-if="!logsLoading" class="empty-logs">
          <icon-history style="font-size: 32px; color: var(--color-text-4)" />
          <p>暂无执行记录</p>
          <span class="empty-hint">规则触发后将自动记录执行日志</span>
        </div>
      </a-spin>
    </a-modal>

    <!-- 导入预览弹窗 -->
    <a-modal
      v-model:visible="importModalVisible"
      title="导入规则预览"
      :width="600"
      @ok="handleConfirmImport"
      @cancel="importModalVisible = false"
      :ok-loading="importing"
      ok-text="确认导入"
      cancel-text="取消"
      unmount-on-close
    >
      <div class="import-preview">
        <a-alert v-if="importPreviewConflicts.length > 0" type="warning" style="margin-bottom: 16px">
          <template #title>存在名称冲突</template>
          以下规则与当前项目已有规则同名，请选择处理方式。
        </a-alert>

        <div class="import-conflict-strategy" v-if="importPreviewConflicts.length > 0">
          <span style="margin-right: 8px">冲突策略：</span>
          <a-radio-group v-model="importConflictStrategy">
            <a-radio value="skip">跳过同名规则</a-radio>
            <a-radio value="overwrite">覆盖同名规则</a-radio>
          </a-radio-group>
        </div>

        <div class="import-rules-list">
          <div
            v-for="item in importPreviewRules"
            :key="item.name"
            class="import-rule-item"
            :class="{ 'import-conflict': importPreviewConflicts.includes(item.name) }"
          >
            <div class="import-rule-name">
              <icon-thunderbolt style="color: var(--color-primary-6)" />
              {{ item.name }}
              <a-tag v-if="importPreviewConflicts.includes(item.name)" color="orangered" size="small">冲突</a-tag>
            </div>
            <div class="import-rule-meta">
              <span>类型: {{ item.ruleType === 'on_schedule' ? '定时' : item.ruleType === 'action' ? '命令' : '事件触发' }}</span>
              <span v-if="item.description" style="margin-left: 12px; color: var(--color-text-3)">{{ item.description }}</span>
            </div>
          </div>
        </div>

        <div v-if="importPreviewRules.length === 0" class="import-empty">
          文件中没有规则数据
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { workflowRuleApi, issueApi, sprintApi, projectApi } from '@/api'
import type { WorkflowRuleVO, WorkflowRuleDTO, WorkflowRuleExecutionLogVO, WorkflowRuleExportDTO, WorkflowRuleExportItem } from '@/api/workflowRule'
import type { IssueStatusVO, SprintVO } from '@/api/types'
import VariableInput from './components/VariableInput.vue'

const props = defineProps<{
  projectId: string
}>()

// ==================== State ====================
const loading = ref(false)
const rules = ref<WorkflowRuleVO[]>([])
const searchKeyword = ref('')
const filterEvent = ref<string | undefined>(undefined)
const modalVisible = ref(false)
const editingRule = ref<WorkflowRuleVO | null>(null)
const submitting = ref(false)
const formRef = ref()

// ==================== Execution Logs State ====================
const logsVisible = ref(false)
const logsLoading = ref(false)
const executionLogs = ref<WorkflowRuleExecutionLogVO[]>([])
const logFilter = ref('')
const logsRuleId = ref('')
const logsRuleName = ref('')

interface ConditionItem {
  conditionType: string
  field: string
  operator: string
  value: string
  negated: boolean
  // Advanced condition fields
  tagId?: string
  attribute?: string
  days?: number
  userId?: string
  role?: string
  projectId?: string
}

interface ActionItem {
  type: string
  field?: string
  value?: string
  tagId?: string
  content?: string
  errorMessage?: string
  target?: string
  subject?: string
  body?: string
  style?: string
  message?: string
  // copy_issue / move_to_project
  targetProjectId?: string
  summaryPrefix?: string
  copyAttachments?: boolean
  copySprint?: boolean
  // add_work_item
  duration?: number
  description?: string
}

const formData = reactive({
  name: '',
  description: '',
  triggerEvent: 'issue_created',
  triggerField: null as string | null,
  conditionLogic: 'and' as 'and' | 'or',
  conditions: [] as ConditionItem[],
  actions: [] as ActionItem[]
})

// ==================== Status & Sprint Data ====================
const availableStatuses = ref<IssueStatusVO[]>([])
const statusesLoading = ref(false)
const availableSprints = ref<SprintVO[]>([])
const sprintsLoading = ref(false)

async function loadStatuses() {
  if (availableStatuses.value.length > 0) return
  statusesLoading.value = true
  try {
    const res = await issueApi.listStatuses()
    if (res.code === 0) {
      availableStatuses.value = res.data
    }
  } catch {
    // silent
  } finally {
    statusesLoading.value = false
  }
}

async function loadSprints() {
  if (!props.projectId || availableSprints.value.length > 0) return
  sprintsLoading.value = true
  try {
    const res = await sprintApi.listByProject(props.projectId)
    if (res.code === 0) {
      availableSprints.value = res.data?.list || []
    }
  } catch {
    // silent
  } finally {
    sprintsLoading.value = false
  }
}

// ==================== Project Data (for copy_issue / move_to_project) ====================
const allProjects = ref<Array<{ id: string; name: string; key: string }>>([])

async function loadProjects() {
  if (allProjects.value.length > 0) return
  try {
    const res = await projectApi.list({ page: 1, pageSize: 200 })
    if (res.code === 0) {
      allProjects.value = (res.data?.list || []).map((p: any) => ({
        id: p.id,
        name: p.name,
        key: p.key
      }))
    }
  } catch {
    // silent
  }
}

// ==================== Computed ====================
const filteredRules = computed(() => {
  let list = rules.value.filter(r => r.ruleType !== 'on_schedule')
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(r => r.name.toLowerCase().includes(kw))
  }
  if (filterEvent.value) {
    list = list.filter(r => r.triggerEvent === filterEvent.value)
  }
  return list
})

// ==================== Data Loading ====================
async function loadRules() {
  loading.value = true
  try {
    const res = await workflowRuleApi.list(props.projectId)
    if (res.code === 0) {
      rules.value = res.data
    }
  } catch (e) {
    // silent
  } finally {
    loading.value = false
  }
}

// ==================== Form ====================
function showCreateModal() {
  editingRule.value = null
  Object.assign(formData, {
    name: '',
    description: '',
    triggerEvent: 'issue_created',
    triggerField: null,
    conditionLogic: 'and',
    conditions: [],
    actions: []
  })
  modalVisible.value = true
  // 预加载状态和迭代数据
  loadStatuses()
  loadSprints()
  loadProjects()
}

function handleEdit(rule: WorkflowRuleVO) {
  editingRule.value = rule
  const { conditions, logic } = parseConditionJson(rule.conditionJson)
  const actions = parseJson(rule.actionJson, [])
  Object.assign(formData, {
    name: rule.name,
    description: rule.description || '',
    triggerEvent: rule.triggerEvent,
    triggerField: rule.triggerField,
    conditionLogic: logic,
    conditions,
    actions
  })
  modalVisible.value = true
  // 预加载状态和迭代数据
  loadStatuses()
  loadSprints()
  loadProjects()
}

/**
 * 解析条件 JSON 为表单可编辑结构。
 * 支持旧格式（平铺数组）和新格式（递归逻辑节点）。
 */
function parseConditionJson(json: string): { conditions: ConditionItem[]; logic: 'and' | 'or' } {
  if (!json || json === '[]' || json === '{}') {
    return { conditions: [], logic: 'and' }
  }
  try {
    const parsed = JSON.parse(json)
    // 旧格式：直接是数组
    if (Array.isArray(parsed)) {
      return {
        conditions: parsed.map((c: any) => parseLeafToConditionItem(c, false)),
        logic: 'and'
      }
    }
    // 新格式：对象 { type: "and"/"or", conditions: [...] }
    if (parsed && typeof parsed === 'object' && parsed.type) {
      const logic = (parsed.type === 'or' ? 'or' : 'and') as 'and' | 'or'
      const childNodes: any[] = parsed.conditions || []
      const conditions: ConditionItem[] = childNodes.map((node: any) => {
        if (node.type === 'not' && node.condition) {
          return parseLeafToConditionItem(node.condition, true)
        }
        return parseLeafToConditionItem(node, false)
      })
      return { conditions, logic }
    }
    return { conditions: [], logic: 'and' }
  } catch {
    return { conditions: [], logic: 'and' }
  }
}

function parseLeafToConditionItem(leaf: any, negated: boolean): ConditionItem {
  const conditionType = leaf.conditionType || 'field_check'
  const item: ConditionItem = {
    conditionType,
    field: leaf.field || '',
    operator: leaf.operator || 'equals',
    value: leaf.value || '',
    negated
  }
  // 解析高级字段
  if (leaf.tagId) item.tagId = leaf.tagId
  if (leaf.attribute) item.attribute = leaf.attribute
  if (leaf.days) item.days = Number(leaf.days)
  if (leaf.userId) item.userId = leaf.userId
  if (leaf.role) item.role = leaf.role
  if (leaf.projectId) item.projectId = leaf.projectId
  return item
}

function addCondition() {
  formData.conditions.push({ conditionType: 'field_check', field: '', operator: 'equals', value: '', negated: false })
}

function onConditionTypeChange(cond: ConditionItem) {
  // Reset all fields when condition type changes
  cond.field = ''
  cond.operator = ''
  cond.value = ''
  cond.tagId = undefined
  cond.attribute = undefined
  cond.days = undefined
  cond.userId = undefined
  cond.role = undefined
  cond.projectId = undefined
}

function removeCondition(idx: number) {
  formData.conditions.splice(idx, 1)
}

function addAction() {
  formData.actions.push({ type: 'set_field', field: '', value: '' })
}

function removeAction(idx: number) {
  formData.actions.splice(idx, 1)
}

function validateActions(_value: any, callback: (msg?: string) => void) {
  if (formData.actions.length === 0) {
    callback('请至少添加一个动作')
  } else {
    callback()
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate()
  if (valid) return

  submitting.value = true
  try {
    const dto: WorkflowRuleDTO = {
      name: formData.name,
      description: formData.description || undefined,
      ruleType: 'on_change',
      triggerEvent: formData.triggerEvent,
      triggerField: formData.triggerField || undefined,
      conditionJson: buildConditionJson(),
      actionJson: JSON.stringify(formData.actions.filter(a => a.type)),
      enabled: true
    }

    if (editingRule.value) {
      await workflowRuleApi.update(editingRule.value.id, dto)
      Message.success('规则已更新')
    } else {
      await workflowRuleApi.create(props.projectId, dto)
      Message.success('规则已创建')
    }
    modalVisible.value = false
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

/**
 * 构建条件 JSON。
 * - field_check 类型：保留旧的 field/operator/value 格式
 * - 高级类型：使用 conditionType + 类型专属字段
 */
function buildConditionJson(): string {
  const validConditions = formData.conditions.filter(c => c.conditionType)
  if (validConditions.length === 0) return '[]'

  const hasNegated = validConditions.some(c => c.negated)
  const isOr = formData.conditionLogic === 'or'
  const hasAdvanced = validConditions.some(c => c.conditionType !== 'field_check')

  // 如果有高级条件或 OR 逻辑或 NOT，统一使用新格式
  if (isOr || hasNegated || hasAdvanced) {
    const leafNodes = validConditions.map(c => {
      const leaf = buildConditionLeaf(c)
      if (c.negated) {
        return { type: 'not', condition: leaf }
      }
      return leaf
    })
    return JSON.stringify({
      type: formData.conditionLogic,
      conditions: leafNodes
    })
  }

  // 全部是 field_check + AND + 无 NOT = 旧格式（向后兼容）
  return JSON.stringify(validConditions.map(c => ({
    field: c.field,
    operator: c.operator,
    value: c.value
  })))
}

function buildConditionLeaf(c: ConditionItem): any {
  if (c.conditionType === 'field_check') {
    return { type: 'condition', field: c.field, operator: c.operator, value: c.value }
  }
  // 高级条件类型
  const leaf: any = { type: 'condition', conditionType: c.conditionType }
  switch (c.conditionType) {
    case 'issue_has_tag':
      leaf.tagId = c.tagId || ''
      break
    case 'issue_attribute_count':
      leaf.attribute = c.attribute || ''
      leaf.operator = c.operator || 'greater_than'
      leaf.value = String(c.value ?? '')
      break
    case 'issue_created_within':
    case 'issue_updated_within':
      leaf.days = String(c.days ?? '')
      break
    case 'created_by':
    case 'updated_by':
      leaf.userId = c.userId || 'current_user'
      break
    case 'user_has_role':
      leaf.role = c.role || ''
      break
    case 'issue_in_project':
      leaf.projectId = c.projectId || ''
      break
    // issue_resolved: 无额外参数
  }
  return leaf
}

// ==================== Actions ====================
async function handleToggle(rule: WorkflowRuleVO) {
  try {
    const res = await workflowRuleApi.toggle(rule.id)
    if (res.code === 0) {
      // 清除该规则的验证缓存
      ruleValidationErrors.value.delete(rule.id)
    }
    await loadRules()
  } catch (e: any) {
    const msg = e.response?.data?.message || '操作失败'
    Message.error(msg)
    // 如果启用失败（验证问题），自动触发验证来显示详情
    if (!rule.enabled) {
      await handleValidateRule(rule)
    }
  }
}

// ==================== Rule Validation ====================
const ruleValidationErrors = ref<Map<string, string[]>>(new Map())
const validatingRuleId = ref<string | null>(null)

async function handleValidateRule(rule: WorkflowRuleVO) {
  validatingRuleId.value = rule.id
  try {
    const res = await workflowRuleApi.validate(rule.id)
    if (res.code === 0) {
      if (res.data.valid) {
        ruleValidationErrors.value.delete(rule.id)
        Message.success(`规则 "${rule.name}" 配置有效`)
      } else {
        const messages = res.data.errors.map((err: any) => err.message)
        ruleValidationErrors.value.set(rule.id, messages)
      }
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '校验请求失败')
  } finally {
    validatingRuleId.value = null
  }
}

async function handleDelete(rule: WorkflowRuleVO) {
  try {
    await workflowRuleApi.delete(rule.id)
    Message.success('规则已删除')
    await loadRules()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

// ==================== Execution Logs ====================
const filteredLogs = computed(() => {
  if (!logFilter.value) return executionLogs.value
  const kw = logFilter.value.toLowerCase()
  return executionLogs.value.filter(log =>
    (log.issueKey && log.issueKey.toLowerCase().includes(kw)) ||
    (log.errorMessage && log.errorMessage.toLowerCase().includes(kw))
  )
})

async function handleViewLogs(rule: WorkflowRuleVO) {
  logsRuleId.value = rule.id
  logsRuleName.value = rule.name
  logsVisible.value = true
  logFilter.value = ''
  await refreshLogs()
}

async function refreshLogs() {
  logsLoading.value = true
  try {
    const res = await workflowRuleApi.getExecutionLogs(logsRuleId.value, 50)
    if (res.code === 0) {
      executionLogs.value = res.data
    }
  } catch {
    // silent
  } finally {
    logsLoading.value = false
  }
}

function downloadLogs() {
  const logs = filteredLogs.value
  if (logs.length === 0) {
    Message.warning('没有日志可下载')
    return
  }
  const lines = logs.map(log => {
    const time = formatLogTime(log.executedAt)
    const result = log.failureCount > 0 ? 'FAILED' : log.successCount > 0 ? 'SUCCESS' : 'SKIPPED'
    const issue = log.issueKey || `batch(${log.matchedCount})`
    const error = log.errorMessage ? ` — ${log.errorMessage}` : ''
    return `${time}  ${result}  ${issue}  (${log.durationMs}ms)${error}`
  })
  const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `rule-logs-${logsRuleName.value}-${new Date().toISOString().slice(0, 10)}.txt`
  link.click()
  URL.revokeObjectURL(url)
}

async function clearLogs() {
  try {
    await workflowRuleApi.clearExecutionLogs(logsRuleId.value)
    executionLogs.value = []
    Message.success('执行日志已清空')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '清空失败')
  }
}

function formatLogTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

// ==================== Helpers ====================
function eventLabel(event: string) {
  const map: Record<string, string> = {
    issue_created: '创建时',
    field_changed: '变更时',
    comment_added: '评论时',
    attachment_added: '附件添加',
    attachment_removed: '附件移除',
    link_added: '关联添加',
    link_removed: '关联移除',
    work_item_added: '工时记录',
    work_item_deleted: '工时删除',
    issue_resolved: '已解决',
    issue_unresolved: '未解决'
  }
  return map[event] || event
}

function eventColor(event: string) {
  const map: Record<string, string> = {
    issue_created: 'green',
    field_changed: 'blue',
    comment_added: 'cyan',
    attachment_added: 'orange',
    attachment_removed: 'orange',
    link_added: 'purple',
    link_removed: 'purple',
    work_item_added: 'gold',
    work_item_deleted: 'gold',
    issue_resolved: 'green',
    issue_unresolved: 'red'
  }
  return map[event] || 'gray'
}

function fieldLabel(field: string) {
  const map: Record<string, string> = {
    status_id: '状态', issue_type: '类型', priority: '优先级', assignee: '负责人',
    sprint: '迭代', title: '标题', due_date: '截止日期',
    type: '类型', status: '状态'
  }
  return map[field] || field
}

function operatorLabel(op: string) {
  const map: Record<string, string> = {
    equals: '等于', not_equals: '不等于', contains: '包含', in: '属于',
    is_empty: '为空', is_not_empty: '不为空',
    overdue: '已逾期', due_within_days: 'N天内到期',
    old_value_equals: '旧值等于', old_value_not_equals: '旧值不等于',
    old_value_in: '旧值属于', old_value_is_empty: '旧值为空', old_value_is_not_empty: '旧值不为空'
  }
  return map[op] || op
}

function conditionSummary(json: string): string {
  if (!json || json === '[]' || json === '{}') return '无条件（始终触发）'
  try {
    const parsed = JSON.parse(json)
    // 旧格式：平铺数组
    if (Array.isArray(parsed)) {
      if (parsed.length === 0) return '无条件（始终触发）'
      return parsed.map((c: any) => formatLeafCondition(c)).join(' AND ')
    }
    // 新格式：递归逻辑节点
    if (parsed && typeof parsed === 'object' && parsed.type) {
      return formatLogicNode(parsed)
    }
    return '无条件（始终触发）'
  } catch {
    return '无条件（始终触发）'
  }
}

function formatLogicNode(node: any): string {
  if (!node) return ''
  const type = node.type
  if (type === 'not') {
    return `NOT(${formatLogicNode(node.condition)})`
  }
  if (type === 'and' || type === 'or') {
    const children: any[] = node.conditions || []
    if (children.length === 0) return '无条件'
    const separator = type === 'and' ? ' AND ' : ' OR '
    const parts = children.map((child: any) => formatLogicNode(child))
    return parts.length > 1 ? `(${parts.join(separator)})` : parts[0]
  }
  // 叶子节点（type=condition 或其他）
  return formatLeafCondition(node)
}

function formatLeafCondition(c: any): string {
  // Advanced condition types
  if (c.conditionType) {
    switch (c.conditionType) {
      case 'issue_resolved': return '工单已解决'
      case 'issue_has_tag': return `有标签 #${c.tagId || '?'}`
      case 'issue_attribute_count': {
        const attrLabel: Record<string, string> = { comments: '评论数', links: '关联数', attachments: '附件数' }
        const opLabel: Record<string, string> = { greater_than: '>', less_than: '<', equals: '=', greater_than_or_equals: '≥', less_than_or_equals: '≤' }
        return `${attrLabel[c.attribute] || c.attribute} ${opLabel[c.operator] || c.operator} ${c.value || '?'}`
      }
      case 'issue_created_within': return `创建于 ${c.days || '?'} 天内`
      case 'issue_updated_within': return `更新于 ${c.days || '?'} 天内`
      case 'created_by': return `创建者是 ${c.userId === 'current_user' ? '当前用户' : c.userId || '?'}`
      case 'updated_by': return `更新者是 ${c.userId === 'current_user' ? '当前用户' : c.userId || '?'}`
      case 'user_has_role': return `触发者角色 = ${c.role || '?'}`
      case 'issue_in_project': return `属于项目 ${c.projectId || '?'}`
    }
  }
  // field_check or legacy format
  if (c.operator === 'overdue') return `${fieldLabel(c.field)} 已逾期`
  if (c.operator === 'due_within_days') return `${fieldLabel(c.field)} ${c.value || '?'}天内到期`
  const isEmptyOp = ['is_empty', 'is_not_empty', 'old_value_is_empty', 'old_value_is_not_empty'].includes(c.operator)
  if (isEmptyOp) {
    return `${fieldLabel(c.field)} ${operatorLabel(c.operator)}`
  }
  return `${fieldLabel(c.field)} ${operatorLabel(c.operator)} "${c.value}"`
}

function actionSummary(json: string): string {
  const actions = parseJson(json, [])
  if (actions.length === 0) return '无动作'
  return actions.map((a: any) => {
    switch (a.type) {
      case 'set_field': return `设置 ${fieldLabel(a.field)}=${a.value}`
      case 'add_tag': return `添加标签 #${a.tagId}`
      case 'remove_tag': return `移除标签 #${a.tagId}`
      case 'add_comment': return '添加评论'
      case 'require_field': return `要求 ${fieldLabel(a.field)} 必填`
      case 'send_email': return `发邮件→${a.target || '?'}`
      case 'show_alert': return `提示: ${a.message || '?'}`
      case 'update_summary': return '修改标题'
      case 'update_description': return '修改描述'
      default: return a.type
    }
  }).join(', ')
}

function parseJson(str: string, fallback: any[]): any[] {
  try {
    const parsed = JSON.parse(str)
    return Array.isArray(parsed) ? parsed : fallback
  } catch {
    return fallback
  }
}

// ==================== Export / Import ====================
const importFileInput = ref<HTMLInputElement | null>(null)
const importModalVisible = ref(false)
const importing = ref(false)
const importPreviewRules = ref<WorkflowRuleExportItem[]>([])
const importPreviewConflicts = ref<string[]>([])
const importConflictStrategy = ref<'skip' | 'overwrite'>('skip')

function triggerImportFileInput() {
  importFileInput.value?.click()
}

function handleImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const content = JSON.parse(e.target?.result as string) as WorkflowRuleExportDTO
      if (!content.rules || !Array.isArray(content.rules) || content.rules.length === 0) {
        Message.error('JSON 文件中没有规则数据')
        return
      }
      importPreviewRules.value = content.rules
      // 检查名称冲突
      const existingNames = new Set(rules.value.map(r => r.name))
      importPreviewConflicts.value = content.rules
        .map(r => r.name)
        .filter(name => existingNames.has(name))
      importConflictStrategy.value = 'skip'
      importModalVisible.value = true
    } catch {
      Message.error('无法解析 JSON 文件，请检查文件格式')
    }
  }
  reader.readAsText(file)
  // 重置 input 以便重复选择同一文件
  input.value = ''
}

async function handleConfirmImport() {
  importing.value = true
  try {
    const res = await workflowRuleApi.importRules(props.projectId, {
      conflictStrategy: importConflictStrategy.value,
      rules: importPreviewRules.value
    })
    if (res.code === 0) {
      const r = res.data
      const parts: string[] = []
      if (r.importedCount > 0) parts.push(`导入 ${r.importedCount} 条`)
      if (r.overwrittenCount > 0) parts.push(`覆盖 ${r.overwrittenCount} 条`)
      if (r.skippedCount > 0) parts.push(`跳过 ${r.skippedCount} 条`)
      Message.success(parts.join('，') || '导入完成')
      importModalVisible.value = false
      await loadRules()
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

async function handleExportSingle(rule: WorkflowRuleVO) {
  try {
    const res = await workflowRuleApi.exportRule(rule.id)
    if (res.code === 0) {
      downloadJson(res.data, `rule-${rule.name}.json`)
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '导出失败')
  }
}

async function handleExportAll() {
  try {
    const res = await workflowRuleApi.exportProjectRules(props.projectId)
    if (res.code === 0) {
      downloadJson(res.data, `workflow-rules-export.json`)
      Message.success(`已导出 ${res.data.rules.length} 条规则`)
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '导出失败')
  }
}

function downloadJson(data: any, filename: string) {
  const json = JSON.stringify(data, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// ==================== Lifecycle ====================
onMounted(loadRules)

watch(() => props.projectId, () => {
  availableSprints.value = [] // 切换项目时清空缓存，下次打开弹窗时重新加载
  loadRules()
})
</script>

<style scoped>
.workflow-rules {
  padding: 16px 0;
}

.rules-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

/* Rule cards */
.rules-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rule-card {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  border-radius: 6px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  transition: border-color 150ms;
}

.rule-card:hover {
  border-color: var(--color-primary-light-4);
}

.rule-card.disabled {
  opacity: 0.5;
}

.rule-card.has-errors {
  border-color: var(--color-warning-6);
}

.rule-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rule-main {
  flex: 1;
  min-width: 0;
}

.rule-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.rule-name {
  font-weight: 500;
  font-size: 14px;
  color: var(--color-text-1);
}

.rule-description {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 6px;
}

.rule-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-2);
}

.summary-label {
  color: var(--color-text-3);
}

.summary-divider {
  color: var(--color-text-4);
  font-weight: 600;
}

.rule-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 16px;
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-state h3 {
  margin: 16px 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
}

.empty-state p {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 16px;
}

/* Form */
.condition-list,
.action-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-logic-toggle {
  margin-bottom: 4px;
}

.condition-row,
.action-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-hint {
  color: var(--color-text-3);
  font-size: 12px;
  font-style: italic;
}

.condition-row.condition-negated {
  border-left: 2px solid rgb(var(--red-6));
  padding-left: 8px;
  border-radius: 2px;
}

.not-active {
  font-weight: 600;
  font-size: 11px;
}

/* Status option in dropdown */
.status-option {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.condition-suffix {
  font-size: 13px;
  color: var(--color-text-3);
  white-space: nowrap;
}

/* Execution Logs */
.log-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.logs-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 400px;
  overflow-y: auto;
}

.log-entry {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 12px;
  border-radius: 4px;
  background: var(--color-fill-2);
  border-left: 3px solid var(--color-success-6);
}

.log-entry.log-error-entry {
  border-left-color: var(--color-danger-6);
}

.log-entry.log-skipped-entry {
  border-left-color: var(--color-text-4);
  opacity: 0.7;
}

.log-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.log-time {
  color: var(--color-text-2);
  font-family: monospace;
  font-size: 12px;
}

.log-result-icon {
  font-size: 12px;
}

.log-issue-key a {
  color: var(--color-primary-6);
  text-decoration: none;
  font-weight: 500;
}

.log-issue-key a:hover {
  text-decoration: underline;
}

.log-issue-key.log-batch {
  color: var(--color-text-3);
  font-size: 12px;
}

.log-duration {
  color: var(--color-text-4);
  font-size: 11px;
}

.log-error-msg {
  font-size: 12px;
  color: var(--color-danger-6);
  padding-left: 24px;
}

.empty-logs {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  text-align: center;
}

.empty-logs p {
  margin: 12px 0 4px;
  font-size: 14px;
  color: var(--color-text-2);
}

.empty-hint {
  font-size: 12px;
  color: var(--color-text-4);
}

/* Validation errors */
.rule-validation-errors {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  background: var(--color-warning-light-1);
  font-size: 12px;
}

.validation-error-list {
  flex: 1;
}

.validation-error-title {
  font-weight: 500;
  color: var(--color-warning-6);
}

.validation-error-list ul {
  margin: 4px 0 0;
  padding-left: 16px;
  color: var(--color-text-2);
}

.validation-error-list ul li {
  margin-bottom: 2px;
}

/* Import preview modal */
.import-preview {
  max-height: 400px;
  overflow-y: auto;
}

.import-conflict-strategy {
  margin-bottom: 16px;
  padding: 8px 12px;
  background: var(--color-fill-2);
  border-radius: 6px;
  display: flex;
  align-items: center;
}

.import-rules-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.import-rule-item {
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  transition: border-color 0.15s;
}

.import-rule-item.import-conflict {
  border-color: var(--color-warning-4);
  background: var(--color-warning-1);
}

.import-rule-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  font-size: 13px;
}

.import-rule-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-3);
}

.import-empty {
  text-align: center;
  padding: 32px;
  color: var(--color-text-3);
}
</style>
