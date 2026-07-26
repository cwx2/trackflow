package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.workflow.dto.WorkflowRuleDTO;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.mapper.WorkflowRuleMapper;
import com.trackflow.workflow.vo.WorkflowRuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ObjectMapper objectMapper;
    private final PermissionService permissionService;

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
        return rules.stream().map(this::toVO).toList();
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
        return toVO(rule);
    }

    /** 合法的规则类型 */
    private static final java.util.Set<String> VALID_RULE_TYPES =
            java.util.Set.of("on_change", "on_schedule");

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
        rule.setCreatedBy(SecurityUtils.getCurrentUserId());
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());

        ruleMapper.insert(rule);
        log.info("[WorkflowRule] Created {} rule '{}' (id={}) for project={}",
                ruleType, rule.getName(), rule.getId(), rule.getProjectId());
        return toVO(rule);
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
        if (dto.getEnabled() != null) {
            rule.setEnabled(dto.getEnabled());
        }
        if (dto.getSortOrder() != null) {
            rule.setSortOrder(dto.getSortOrder());
        }
        rule.setUpdatedAt(LocalDateTime.now());

        ruleMapper.updateById(rule);
        log.info("[WorkflowRule] Updated rule '{}' (id={})", rule.getName(), rule.getId());
        return toVO(rule);
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
        return toVO(rule);
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
     *   <li>on_change: triggerEvent 必填（issue_created / field_changed）</li>
     *   <li>on_schedule: cronExpression 必填，triggerEvent 忽略</li>
     * </ul>
     */
    private void validateRuleTypeAndFields(String ruleType, WorkflowRuleDTO dto) {
        if (!VALID_RULE_TYPES.contains(ruleType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的规则类型: " + ruleType + "，仅支持 on_change / on_schedule");
        }
        if ("on_change".equals(ruleType)) {
            validateTriggerEvent(dto.getTriggerEvent());
        } else if ("on_schedule".equals(ruleType)) {
            validateCronExpression(dto.getCronExpression());
        }
    }

    private void validateTriggerEvent(String event) {
        if (event == null || event.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "on_change 规则的触发事件不能为空，仅支持 issue_created / field_changed");
        }
        if (!"issue_created".equals(event) && !"field_changed".equals(event)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的触发事件: " + event + "，仅支持 issue_created / field_changed");
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

    private void validateJson(String json, String fieldLabel) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            var node = objectMapper.readTree(json);
            if (!node.isArray()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldLabel + "必须为 JSON 数组格式");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldLabel + " JSON 格式无效: " + e.getMessage());
        }
    }

    private WorkflowRuleVO toVO(WorkflowRule rule) {
        WorkflowRuleVO vo = new WorkflowRuleVO();
        vo.setId(String.valueOf(rule.getId()));
        vo.setProjectId(rule.getProjectId() != null ? String.valueOf(rule.getProjectId()) : null);
        vo.setName(rule.getName());
        vo.setDescription(rule.getDescription());
        vo.setRuleType(rule.getRuleType());
        vo.setTriggerEvent(rule.getTriggerEvent());
        vo.setTriggerField(rule.getTriggerField());
        vo.setConditionJson(rule.getConditionJson());
        vo.setActionJson(rule.getActionJson());
        vo.setEnabled(rule.getEnabled());
        vo.setSortOrder(rule.getSortOrder());
        vo.setCronExpression(rule.getCronExpression());
        vo.setLastExecutedAt(rule.getLastExecutedAt());
        vo.setCreatedBy(rule.getCreatedBy() != null ? String.valueOf(rule.getCreatedBy()) : null);
        vo.setCreatedAt(rule.getCreatedAt());
        vo.setUpdatedAt(rule.getUpdatedAt());
        return vo;
    }
}
