package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.mapper.IssueTagMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.workflow.converter.WorkflowRuleConverter;
import com.trackflow.workflow.dto.WorkflowRuleDTO;
import com.trackflow.workflow.dto.WorkflowRuleExportDTO;
import com.trackflow.workflow.dto.WorkflowRuleImportDTO;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.mapper.WorkflowRuleMapper;
import com.trackflow.workflow.vo.WorkflowRuleImportResultVO;
import com.trackflow.workflow.vo.WorkflowRuleVO;
import com.trackflow.workflow.vo.WorkflowRuleValidationVO;
import com.trackflow.workflow.vo.WorkflowRuleValidationVO.ValidationError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 工作流规则 CRUD 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRuleService {

    private final WorkflowRuleMapper ruleMapper;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final IssueTagMapper issueTagMapper;
    private final SprintMapper sprintMapper;
    private final ObjectMapper objectMapper;
    private final PermissionService permissionService;
    private final WorkflowRuleConverter workflowRuleConverter;
    private final @Lazy WorkflowRuleEngine ruleEngine;

    /**
     * 查询项目规则列表（含全局规则）
     */
    public List<WorkflowRuleVO> listRules(Long projectId) {
        List<WorkflowRule> rules;
        if (projectId != null && projectId > 0) {
            rules = ruleMapper.findByProjectIncludeGlobal(projectId);
        } else {
            // 仅全局
            rules = ruleMapper.selectList(
                    new LambdaQueryWrapper<WorkflowRule>()
                            .isNull(WorkflowRule::getProjectId)
                            .orderByAsc(WorkflowRule::getSortOrder)
                            .orderByAsc(WorkflowRule::getId)
            );
        }
        return workflowRuleConverter.toVOList(rules);
    }

    /**
     * 获取单个规则（带权限校验）
     */
    public WorkflowRuleVO getRule(Long id) {
        WorkflowRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }
        checkRuleViewPermission(rule);
        return workflowRuleConverter.toVO(rule);
    }

    /** 合法的规则类型 */
    private static final java.util.Set<String> VALID_RULE_TYPES =
            java.util.Set.of("on_change", "on_schedule", "action");

    /**
     * 创建规则
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRuleVO createRule(Long projectId, WorkflowRuleDTO dto) {
        String ruleType = dto.getRuleType() != null ? dto.getRuleType() : "on_change";
        validateRuleTypeAndFields(ruleType, dto);
        validateJson(dto.getConditionJson(), "条件");
        validateJson(dto.getActionJson(), "动作");

        WorkflowRule rule = new WorkflowRule();
        rule.setProjectId(projectId != null && projectId > 0 ? projectId : null);
        rule.setName(dto.getName().trim());
        rule.setDescription(dto.getDescription());
        rule.setRuleType(ruleType);
        // on_schedule 规则不使用 triggerEvent，强制设为 null 避免被 findEnabledRules 误匹配
        rule.setTriggerEvent("on_change".equals(ruleType) ? dto.getTriggerEvent() : null);
        rule.setTriggerField("on_change".equals(ruleType) ? dto.getTriggerField() : null);
        rule.setConditionJson(dto.getConditionJson());
        rule.setActionJson(dto.getActionJson());
        rule.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        rule.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        rule.setCronExpression(dto.getCronExpression());
        // Action Rule: set action_command
        if ("action".equals(ruleType)) {
            validateActionCommand(dto.getActionCommand(), null);
            rule.setActionCommand(dto.getActionCommand());
        }
        rule.setCreatedBy(SecurityUtils.getCurrentUserId());
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());

        ruleMapper.insert(rule);
        log.info("[WorkflowRule] Created {} rule '{}' (id={}) for project={}",
                ruleType, rule.getName(), rule.getId(), rule.getProjectId());
        return workflowRuleConverter.toVO(rule);
    }

    /**
     * 更新规则
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRuleVO updateRule(Long id, WorkflowRuleDTO dto) {
        WorkflowRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }
        checkRuleManagePermission(rule);

        String ruleType = dto.getRuleType() != null ? dto.getRuleType() : rule.getRuleType();
        validateRuleTypeAndFields(ruleType, dto);
        validateJson(dto.getConditionJson(), "条件");
        validateJson(dto.getActionJson(), "动作");

        rule.setName(dto.getName().trim());
        rule.setDescription(dto.getDescription());
        rule.setRuleType(ruleType);
        // on_schedule 规则不使用 triggerEvent，强制设为 null
        rule.setTriggerEvent("on_change".equals(ruleType) ? dto.getTriggerEvent() : null);
        rule.setTriggerField("on_change".equals(ruleType) ? dto.getTriggerField() : null);
        rule.setConditionJson(dto.getConditionJson());
        rule.setActionJson(dto.getActionJson());
        rule.setCronExpression(dto.getCronExpression());
        // Action Rule: update action_command
        if ("action".equals(ruleType)) {
            validateActionCommand(dto.getActionCommand(), rule.getId());
            rule.setActionCommand(dto.getActionCommand());
        } else {
            rule.setActionCommand(null);
        }
        if (dto.getEnabled() != null) {
            rule.setEnabled(dto.getEnabled());
        }
        if (dto.getSortOrder() != null) {
            rule.setSortOrder(dto.getSortOrder());
        }
        rule.setUpdatedAt(LocalDateTime.now());

        ruleMapper.updateById(rule);
        log.info("[WorkflowRule] Updated rule '{}' (id={})", rule.getName(), rule.getId());
        return workflowRuleConverter.toVO(rule);
    }

    /**
     * 删除规则
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        WorkflowRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }
        checkRuleManagePermission(rule);
        ruleMapper.deleteById(id);
        log.info("[WorkflowRule] Deleted rule '{}' (id={})", rule.getName(), id);
    }

    /**
     * 切换规则启用/禁用
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRuleVO toggleRule(Long id) {
        WorkflowRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }
        checkRuleManagePermission(rule);

        // 启用规则前必须通过有效性校验
        if (!rule.getEnabled()) {
            WorkflowRuleValidationVO validation = doValidateRule(rule);
            if (!validation.isValid()) {
                String errorSummary = validation.getErrors().stream()
                        .map(ValidationError::getMessage)
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("未知错误");
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "规则配置有误，无法启用: " + errorSummary);
            }
        }

        rule.setEnabled(!rule.getEnabled());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
        log.info("[WorkflowRule] Toggled rule '{}' (id={}) enabled={}", rule.getName(), id, rule.getEnabled());
        return workflowRuleConverter.toVO(rule);
    }

    /**
     * 校验当前用户是否有权限查看指定规则。
     * <ul>
     *   <li>全局规则（projectId == null）→ 需要 system:admin</li>
     *   <li>项目规则（projectId != null）→ 需要 project:manage_workflow</li>
     * </ul>
     */
    private void checkRuleViewPermission(WorkflowRule rule) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long projectId = rule.getProjectId();
        if (projectId == null) {
            // 全局规则 → 需要系统管理员
            if (!permissionService.isSystemAdmin(userId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "查看全局工作流规则需要系统管理员权限");
            }
        } else {
            // 项目规则 → 需要 project:manage_workflow
            if (!permissionService.hasPermission(userId, projectId, "project:manage_workflow")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限查看该项目的工作流规则");
            }
        }
    }

    /**
     * 校验当前用户是否有权限管理指定规则。
     * <ul>
     *   <li>全局规则（projectId == null）→ 需要 system:admin</li>
     *   <li>项目规则（projectId != null）→ 需要 project:manage_workflow</li>
     * </ul>
     */
    private void checkRuleManagePermission(WorkflowRule rule) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long projectId = rule.getProjectId();
        if (projectId == null) {
            // 全局规则 → 需要系统管理员
            if (!permissionService.isSystemAdmin(userId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "修改全局工作流规则需要系统管理员权限");
            }
        } else {
            // 项目规则 → 需要 project:manage_workflow
            if (!permissionService.hasPermission(userId, projectId, "project:manage_workflow")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限管理该项目的工作流规则");
            }
        }
    }

    // ============ 内部方法 ============

    /**
     * 统一校验规则类型和对应必填字段。
     * <ul>
     *   <li>on_change: triggerEvent 必填（issue_created / field_changed / comment_added）</li>
     *   <li>on_schedule: cronExpression 必填，triggerEvent 忽略</li>
     *   <li>action: actionCommand 必填</li>
     * </ul>
     */
    private void validateRuleTypeAndFields(String ruleType, WorkflowRuleDTO dto) {
        if (!VALID_RULE_TYPES.contains(ruleType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的规则类型: " + ruleType + "，仅支持 on_change / on_schedule / action");
        }
        if ("on_change".equals(ruleType)) {
            validateTriggerEvent(dto.getTriggerEvent());
        } else if ("on_schedule".equals(ruleType)) {
            validateCronExpression(dto.getCronExpression());
        } else if ("action".equals(ruleType)) {
            if (dto.getActionCommand() == null || dto.getActionCommand().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "action 规则的命令名不能为空");
            }
        }
    }

    private static final java.util.Set<String> VALID_TRIGGER_EVENTS = java.util.Set.of(
            "issue_created", "field_changed", "comment_added",
            "attachment_added", "attachment_removed",
            "link_added", "link_removed",
            "work_item_added", "work_item_deleted",
            "issue_resolved", "issue_unresolved"
    );

    private void validateTriggerEvent(String event) {
        if (event == null || event.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "on_change 规则的触发事件不能为空");
        }
        if (!VALID_TRIGGER_EVENTS.contains(event)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的触发事件: " + event + "，支持: " + String.join(" / ", VALID_TRIGGER_EVENTS));
        }
    }

    private static final java.util.Set<String> VALID_SCHEDULES =
            java.util.Set.of("hourly", "daily", "weekly");

    private void validateCronExpression(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "调度表达式不能为空");
        }
        // 支持预设值（hourly/daily/weekly）
        if (VALID_SCHEDULES.contains(cron.toLowerCase())) {
            return;
        }
        // 使用 Spring CronExpression 校验标准 cron 格式（6 段：秒 分 时 日 月 周）
        if (!CronExpression.isValidExpression(cron)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "无效的调度表达式: " + cron + "，请使用 hourly/daily/weekly 或标准 Spring cron 格式（秒 分 时 日 月 周）");
        }
    }

    /** 条件节点允许的 type 值 */
    private static final java.util.Set<String> VALID_CONDITION_NODE_TYPES =
            java.util.Set.of("and", "or", "not", "condition");

    private void validateJson(String json, String fieldLabel) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            var node = objectMapper.readTree(json);
            if (node.isArray()) {
                // 旧格式：条件数组，向后兼容
                return;
            }
            if (node.isObject()) {
                // 新格式：递归条件节点 {"type":"and"/"or"/"not"/"condition", ...}
                var typeNode = node.get("type");
                if (typeNode != null && typeNode.isTextual()
                        && VALID_CONDITION_NODE_TYPES.contains(typeNode.asText())) {
                    return;
                }
                // 动作 JSON 也可能是对象格式，允许通过
                if (node.has("action") || node.has("actions")) {
                    return;
                }
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    fieldLabel + "必须为 JSON 数组格式或有效的条件节点对象（type: and/or/not/condition）");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldLabel + " JSON 格式无效: " + e.getMessage());
        }
    }

    /**
     * 校验 Action Rule 的命令名全局唯一性。
     *
     * @param command  命令名
     * @param excludeId 排除的规则 ID（更新时排除自身）
     */
    private void validateActionCommand(String command, Long excludeId) {
        if (command == null || command.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "action 规则的命令名不能为空");
        }
        if (!command.matches("^[a-z][a-z0-9_-]*$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "命令名只能包含小写字母、数字、下划线和连字符，且必须以字母开头");
        }
        LambdaQueryWrapper<WorkflowRule> wrapper = new LambdaQueryWrapper<WorkflowRule>()
                .eq(WorkflowRule::getActionCommand, command);
        if (excludeId != null) {
            wrapper.ne(WorkflowRule::getId, excludeId);
        }
        Long count = ruleMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "命令名 '" + command + "' 已被其他规则使用");
        }
    }

    // ============ Action Rule 相关方法 ============

    /**
     * 获取工单信息（供 Controller 使用，不做权限校验的内部查询）。
     */
    public com.trackflow.issue.entity.Issue getIssueForActionRules(Long issueId) {
        return issueMapper.selectById(issueId);
    }

    /**
     * 获取指定工单可用的 Action Rule 列表（Guard 条件通过的）。
     */
    public List<WorkflowRuleVO> getAvailableActionRules(Long issueId, Long projectId) {
        List<WorkflowRule> available = ruleEngine.getAvailableActionRules(issueId, projectId);
        return workflowRuleConverter.toVOList(available);
    }

    /**
     * 执行 Action Rule。
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeActionRule(Long issueId, String command) {
        com.trackflow.issue.entity.Issue issue = issueMapper.selectById(issueId);
        if (issue == null || issue.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工单不存在");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        boolean executed = ruleEngine.fireActionRule(issueId, issue.getProjectId(), command, userId);
        if (!executed) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "命令 '" + command + "' 不可用（规则不存在、已禁用或条件不满足）");
        }
    }

    // ============ 规则有效性校验 ============

    /** set_field 动作支持的系统字段 */
    private static final Set<String> SUPPORTED_SET_FIELDS = Set.of(
            "priority", "assignee", "assignee_id", "type", "issue_type",
            "status", "status_id", "sprint", "sprint_id", "due_date", "dueDate"
    );

    /** 条件中 field 字段支持的值 */
    private static final Set<String> SUPPORTED_CONDITION_FIELDS = Set.of(
            "type", "issue_type", "priority", "status", "status_id",
            "assignee", "assignee_id", "reporter", "reporter_id",
            "sprint", "sprint_id", "due_date", "dueDate", "title", "description"
    );

    /**
     * 校验规则引用的资源在目标项目中是否存在。
     * <p>
     * 检查内容：
     * <ul>
     *   <li>action 中 set_field 引用的字段名是否合法</li>
     *   <li>action 中 set_field(status_id) 引用的状态 ID 是否存在</li>
     *   <li>action 中 set_field(sprint_id) 引用的 Sprint 是否存在于目标项目</li>
     *   <li>action 中 add_tag / remove_tag 引用的标签 ID 是否存在（项目级标签需匹配项目）</li>
     *   <li>condition 中 field 引用的字段名是否合法</li>
     *   <li>condition 中 issue_has_tag 引用的标签 ID 是否存在</li>
     * </ul>
     */
    public WorkflowRuleValidationVO validateRule(Long ruleId) {
        WorkflowRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }
        checkRuleViewPermission(rule);
        return doValidateRule(rule);
    }

    /**
     * 校验规则内容（供 toggle 时内部调用）。
     */
    public WorkflowRuleValidationVO doValidateRule(WorkflowRule rule) {
        List<ValidationError> errors = new ArrayList<>();
        Long projectId = rule.getProjectId();

        // 校验动作 JSON
        validateActions(rule.getActionJson(), projectId, errors);

        // 校验条件 JSON
        validateConditions(rule.getConditionJson(), projectId, errors);

        if (errors.isEmpty()) {
            return WorkflowRuleValidationVO.ok();
        }
        return WorkflowRuleValidationVO.fail(errors);
    }

    private void validateActions(String actionJson, Long projectId, List<ValidationError> errors) {
        if (actionJson == null || actionJson.isBlank() || "[]".equals(actionJson.trim())) return;
        try {
            JsonNode arr = objectMapper.readTree(actionJson);
            if (!arr.isArray()) return;
            for (JsonNode act : arr) {
                String type = textOf(act, "type");
                if ("set_field".equals(type)) {
                    validateSetFieldAction(act, projectId, errors);
                } else if ("add_tag".equals(type) || "remove_tag".equals(type)) {
                    validateTagAction(act, projectId, type, errors);
                }
            }
        } catch (Exception e) {
            errors.add(new ValidationError("action", "动作 JSON 解析失败: " + e.getMessage(), null, null));
        }
    }

    private void validateSetFieldAction(JsonNode act, Long projectId, List<ValidationError> errors) {
        String field = textOf(act, "field");
        if (field == null) return;

        // 检查字段名是否被系统支持
        if (!SUPPORTED_SET_FIELDS.contains(field)) {
            errors.add(new ValidationError("action",
                    "set_field 引用了不支持的字段 '" + field + "'",
                    "field", field));
            return;
        }

        String value = textOf(act, "value");
        if (value == null || value.isBlank() || "null".equals(value)) return;

        // 检查 status_id 引用的状态是否存在
        if ("status".equals(field) || "status_id".equals(field)) {
            try {
                Long statusId = Long.parseLong(value);
                IssueStatus status = issueStatusMapper.selectById(statusId);
                if (status == null) {
                    errors.add(new ValidationError("action",
                            "set_field(status) 引用了不存在的状态 ID=" + statusId,
                            "status", value));
                }
            } catch (NumberFormatException e) {
                errors.add(new ValidationError("action",
                        "set_field(status) 的值不是有效数字: '" + value + "'",
                        "status", value));
            }
        }

        // 检查 sprint_id 引用的 Sprint 是否存在且属于正确项目
        if ("sprint".equals(field) || "sprint_id".equals(field)) {
            try {
                Long sprintId = Long.parseLong(value);
                Sprint sprint = sprintMapper.selectById(sprintId);
                if (sprint == null) {
                    errors.add(new ValidationError("action",
                            "set_field(sprint) 引用了不存在的 Sprint ID=" + sprintId,
                            "sprint", value));
                } else if (projectId != null && !projectId.equals(sprint.getProjectId())) {
                    errors.add(new ValidationError("action",
                            "set_field(sprint) 引用的 Sprint ID=" + sprintId + " 不属于当前项目",
                            "sprint", value));
                }
            } catch (NumberFormatException e) {
                errors.add(new ValidationError("action",
                        "set_field(sprint) 的值不是有效数字: '" + value + "'",
                        "sprint", value));
            }
        }
    }

    private void validateTagAction(JsonNode act, Long projectId, String actionType, List<ValidationError> errors) {
        String tagIdStr = textOf(act, "tagId");
        if (tagIdStr == null || tagIdStr.isBlank()) return;
        try {
            Long tagId = Long.parseLong(tagIdStr);
            IssueTag tag = issueTagMapper.selectById(tagId);
            if (tag == null) {
                errors.add(new ValidationError("action",
                        actionType + " 引用了不存在的标签 ID=" + tagId,
                        "tag", tagIdStr));
            } else if (projectId != null && tag.getProjectId() != null
                    && !projectId.equals(tag.getProjectId())) {
                errors.add(new ValidationError("action",
                        actionType + " 引用的标签 '" + tag.getName() + "' 不属于当前项目",
                        "tag", tagIdStr));
            }
        } catch (NumberFormatException e) {
            errors.add(new ValidationError("action",
                    actionType + " 的 tagId 不是有效数字: '" + tagIdStr + "'",
                    "tag", tagIdStr));
        }
    }

    private void validateConditions(String conditionJson, Long projectId, List<ValidationError> errors) {
        if (conditionJson == null || conditionJson.isBlank() || "[]".equals(conditionJson.trim())) return;
        try {
            JsonNode root = objectMapper.readTree(conditionJson);
            if (root.isArray()) {
                for (JsonNode cond : root) {
                    validateConditionNode(cond, projectId, errors);
                }
            } else if (root.isObject()) {
                validateConditionNode(root, projectId, errors);
            }
        } catch (Exception e) {
            errors.add(new ValidationError("condition", "条件 JSON 解析失败: " + e.getMessage(), null, null));
        }
    }

    private void validateConditionNode(JsonNode node, Long projectId, List<ValidationError> errors) {
        String type = textOf(node, "type");
        if (type == null) {
            // 旧格式叶子节点
            validateConditionLeaf(node, projectId, errors);
            return;
        }
        switch (type) {
            case "and", "or" -> {
                JsonNode conditions = node.get("conditions");
                if (conditions != null && conditions.isArray()) {
                    for (JsonNode child : conditions) {
                        validateConditionNode(child, projectId, errors);
                    }
                }
            }
            case "not" -> {
                JsonNode condition = node.get("condition");
                if (condition != null) {
                    validateConditionNode(condition, projectId, errors);
                }
            }
            case "condition" -> validateConditionLeaf(node, projectId, errors);
            default -> validateConditionLeaf(node, projectId, errors);
        }
    }

    private void validateConditionLeaf(JsonNode cond, Long projectId, List<ValidationError> errors) {
        // 检查 conditionType 方式的条件
        String conditionType = textOf(cond, "conditionType");
        if (conditionType != null) {
            if ("issue_has_tag".equals(conditionType)) {
                String tagId = textOf(cond, "tagId");
                if (tagId != null && !tagId.isBlank()) {
                    try {
                        Long tid = Long.parseLong(tagId);
                        IssueTag tag = issueTagMapper.selectById(tid);
                        if (tag == null) {
                            errors.add(new ValidationError("condition",
                                    "issue_has_tag 引用了不存在的标签 ID=" + tid,
                                    "tag", tagId));
                        } else if (projectId != null && tag.getProjectId() != null
                                && !projectId.equals(tag.getProjectId())) {
                            errors.add(new ValidationError("condition",
                                    "issue_has_tag 引用的标签 '" + tag.getName() + "' 不属于当前项目",
                                    "tag", tagId));
                        }
                    } catch (NumberFormatException e) {
                        errors.add(new ValidationError("condition",
                                "issue_has_tag 的 tagId 不是有效数字: '" + tagId + "'",
                                "tag", tagId));
                    }
                }
            }
            return;
        }

        // 标准 field/operator/value 格式的条件
        String field = textOf(cond, "field");
        if (field == null) return;

        if (!SUPPORTED_CONDITION_FIELDS.contains(field)) {
            errors.add(new ValidationError("condition",
                    "条件引用了不支持的字段 '" + field + "'",
                    "field", field));
        }
    }

    private String textOf(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isTextual()) return null;
        return child.asText();
    }

    // ============ 导出/导入 ============

    /**
     * 导出单条规则为可移植 JSON 格式（不含 projectId、id 等绑定信息）。
     */
    public WorkflowRuleExportDTO exportRule(Long ruleId) {
        WorkflowRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在: " + ruleId);
        }
        checkRuleViewPermission(rule);

        WorkflowRuleExportDTO export = new WorkflowRuleExportDTO();
        export.setExportedAt(LocalDateTime.now());
        export.setRules(List.of(toExportItem(rule)));
        return export;
    }

    /**
     * 批量导出项目下的所有规则。
     */
    public WorkflowRuleExportDTO exportRules(Long projectId) {
        List<WorkflowRule> rules;
        if (projectId != null && projectId > 0) {
            rules = ruleMapper.selectList(
                    new LambdaQueryWrapper<WorkflowRule>()
                            .eq(WorkflowRule::getProjectId, projectId)
                            .orderByAsc(WorkflowRule::getSortOrder)
                            .orderByAsc(WorkflowRule::getId)
            );
        } else {
            rules = ruleMapper.selectList(
                    new LambdaQueryWrapper<WorkflowRule>()
                            .isNull(WorkflowRule::getProjectId)
                            .orderByAsc(WorkflowRule::getSortOrder)
                            .orderByAsc(WorkflowRule::getId)
            );
        }

        WorkflowRuleExportDTO export = new WorkflowRuleExportDTO();
        export.setExportedAt(LocalDateTime.now());
        export.setRules(rules.stream().map(this::toExportItem).toList());
        return export;
    }

    /**
     * 导入规则到指定项目。
     *
     * @param projectId        目标项目 ID（null 表示全局）
     * @param importDTO        导入请求（含冲突策略和规则列表）
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRuleImportResultVO importRules(Long projectId, WorkflowRuleImportDTO importDTO) {
        WorkflowRuleImportResultVO result = new WorkflowRuleImportResultVO();

        if (importDTO.getRules() == null || importDTO.getRules().isEmpty()) {
            result.getErrors().add("导入文件中没有规则数据");
            return result;
        }

        String strategy = importDTO.getConflictStrategy() != null
                ? importDTO.getConflictStrategy() : "skip";
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long effectiveProjectId = (projectId != null && projectId > 0) ? projectId : null;

        // 查询目标项目已有的规则名称
        LambdaQueryWrapper<WorkflowRule> existingWrapper = new LambdaQueryWrapper<WorkflowRule>();
        if (effectiveProjectId != null) {
            existingWrapper.eq(WorkflowRule::getProjectId, effectiveProjectId);
        } else {
            existingWrapper.isNull(WorkflowRule::getProjectId);
        }
        List<WorkflowRule> existingRules = ruleMapper.selectList(existingWrapper);
        java.util.Map<String, WorkflowRule> existingNameMap = existingRules.stream()
                .collect(java.util.stream.Collectors.toMap(WorkflowRule::getName, r -> r, (a, b) -> a));

        for (WorkflowRuleExportDTO.RuleItem item : importDTO.getRules()) {
            if (item.getName() == null || item.getName().isBlank()) {
                result.getErrors().add("规则名称不能为空");
                continue;
            }

            // 检查 ruleType 有效性
            String ruleType = item.getRuleType() != null ? item.getRuleType() : "on_change";
            if (!VALID_RULE_TYPES.contains(ruleType)) {
                result.getErrors().add("规则 '" + item.getName() + "' 的类型不合法: " + ruleType);
                continue;
            }

            WorkflowRule existing = existingNameMap.get(item.getName().trim());
            if (existing != null) {
                if ("overwrite".equals(strategy)) {
                    // 覆盖已有规则
                    applyImportItem(existing, item, ruleType);
                    existing.setUpdatedAt(LocalDateTime.now());
                    ruleMapper.updateById(existing);
                    result.setOverwrittenCount(result.getOverwrittenCount() + 1);
                    result.getImportedRules().add(item.getName());
                } else {
                    // skip
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getSkippedRules().add(item.getName());
                }
            } else {
                // 新建规则
                WorkflowRule newRule = new WorkflowRule();
                newRule.setProjectId(effectiveProjectId);
                newRule.setName(item.getName().trim());
                applyImportItem(newRule, item, ruleType);
                newRule.setEnabled(false); // 导入后默认禁用，需用户手动启用
                newRule.setCreatedBy(currentUserId);
                newRule.setCreatedAt(LocalDateTime.now());
                newRule.setUpdatedAt(LocalDateTime.now());
                ruleMapper.insert(newRule);
                result.setImportedCount(result.getImportedCount() + 1);
                result.getImportedRules().add(item.getName());
            }
        }

        log.info("[WorkflowRule] Import completed for project={}: imported={}, skipped={}, overwritten={}, errors={}",
                effectiveProjectId, result.getImportedCount(), result.getSkippedCount(),
                result.getOverwrittenCount(), result.getErrors().size());
        return result;
    }

    private WorkflowRuleExportDTO.RuleItem toExportItem(WorkflowRule rule) {
        WorkflowRuleExportDTO.RuleItem item = new WorkflowRuleExportDTO.RuleItem();
        item.setName(rule.getName());
        item.setDescription(rule.getDescription());
        item.setRuleType(rule.getRuleType());
        item.setTriggerEvent(rule.getTriggerEvent());
        item.setTriggerField(rule.getTriggerField());
        item.setConditionJson(rule.getConditionJson());
        item.setActionJson(rule.getActionJson());
        item.setSortOrder(rule.getSortOrder());
        item.setCronExpression(rule.getCronExpression());
        item.setActionCommand(rule.getActionCommand());
        return item;
    }

    private void applyImportItem(WorkflowRule target, WorkflowRuleExportDTO.RuleItem item, String ruleType) {
        target.setDescription(item.getDescription());
        target.setRuleType(ruleType);
        target.setTriggerEvent("on_change".equals(ruleType) ? item.getTriggerEvent() : null);
        target.setTriggerField("on_change".equals(ruleType) ? item.getTriggerField() : null);
        target.setConditionJson(item.getConditionJson());
        target.setActionJson(item.getActionJson());
        target.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
        target.setCronExpression(item.getCronExpression());
        if ("action".equals(ruleType)) {
            target.setActionCommand(item.getActionCommand());
        } else {
            target.setActionCommand(null);
        }
    }
}

