package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.workflow.converter.WorkflowRuleConverter;
import com.trackflow.workflow.dto.WorkflowRuleDTO;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.mapper.WorkflowRuleMapper;
import com.trackflow.workflow.vo.WorkflowRuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流规则 CRUD 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRuleService {

    private final WorkflowRuleMapper ruleMapper;
    private final IssueMapper issueMapper;
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

    private void validateTriggerEvent(String event) {
        if (event == null || event.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "on_change 规则的触发事件不能为空，支持 issue_created / field_changed / comment_added");
        }
        if (!"issue_created".equals(event) && !"field_changed".equals(event) && !"comment_added".equals(event)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的触发事件: " + event + "，支持 issue_created / field_changed / comment_added");
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
}

