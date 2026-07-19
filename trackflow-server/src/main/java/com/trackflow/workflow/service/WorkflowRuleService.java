package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.workflow.dto.WorkflowRuleDTO;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.mapper.WorkflowRuleMapper;
import com.trackflow.workflow.vo.WorkflowRuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 获取单个规则
     */
    public WorkflowRuleVO getRule(Long id) {
        WorkflowRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }
        return toVO(rule);
    }

    /**
     * 创建规则
     */
    @Transactional
    public WorkflowRuleVO createRule(Long projectId, WorkflowRuleDTO dto) {
        String ruleType = dto.getRuleType() != null ? dto.getRuleType() : "on_change";
        if ("on_change".equals(ruleType)) {
            validateTriggerEvent(dto.getTriggerEvent());
        } else if ("on_schedule".equals(ruleType)) {
            validateCronExpression(dto.getCronExpression());
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的规则类型: " + ruleType);
        }
        validateJson(dto.getConditionJson(), "条件");
        validateJson(dto.getActionJson(), "动作");

        WorkflowRule rule = new WorkflowRule();
        rule.setProjectId(projectId != null && projectId > 0 ? projectId : null);
        rule.setName(dto.getName().trim());
        rule.setDescription(dto.getDescription());
        rule.setRuleType(ruleType);
        rule.setTriggerEvent(dto.getTriggerEvent());
        rule.setTriggerField(dto.getTriggerField());
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
    @Transactional
    public WorkflowRuleVO updateRule(Long id, WorkflowRuleDTO dto) {
        WorkflowRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }

        String ruleType = dto.getRuleType() != null ? dto.getRuleType() : rule.getRuleType();
        if ("on_change".equals(ruleType)) {
            validateTriggerEvent(dto.getTriggerEvent());
        } else if ("on_schedule".equals(ruleType)) {
            validateCronExpression(dto.getCronExpression());
        }
        validateJson(dto.getConditionJson(), "条件");
        validateJson(dto.getActionJson(), "动作");

        rule.setName(dto.getName().trim());
        rule.setDescription(dto.getDescription());
        rule.setRuleType(ruleType);
        rule.setTriggerEvent(dto.getTriggerEvent());
        rule.setTriggerField(dto.getTriggerField());
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
    @Transactional
    public void deleteRule(Long id) {
        WorkflowRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }
        ruleMapper.deleteById(id);
        log.info("[WorkflowRule] Deleted rule '{}' (id={})", rule.getName(), id);
    }

    /**
     * 切换规则启用/禁用
     */
    @Transactional
    public WorkflowRuleVO toggleRule(Long id) {
        WorkflowRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }
        rule.setEnabled(!rule.getEnabled());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
        log.info("[WorkflowRule] Toggled rule '{}' (id={}) enabled={}", rule.getName(), id, rule.getEnabled());
        return toVO(rule);
    }

    // ============ 内部方法 ============

    private void validateTriggerEvent(String event) {
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
        // 支持预设值和标准 cron（5 段或 6 段）
        if (VALID_SCHEDULES.contains(cron.toLowerCase())) {
            return;
        }
        // 简单校验 cron 格式（5-6 个段，空格分隔）
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "无效的调度表达式: " + cron + "，支持 hourly/daily/weekly 或标准 cron 格式");
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
