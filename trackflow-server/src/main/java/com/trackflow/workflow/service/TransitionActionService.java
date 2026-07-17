package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.workflow.dto.CreateTransitionActionDTO;
import com.trackflow.workflow.dto.UpdateTransitionActionDTO;
import com.trackflow.workflow.entity.TransitionAction;
import com.trackflow.workflow.mapper.TransitionActionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 转换动作管理服务 —— 提供 CRUD 操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitionActionService {

    private final TransitionActionMapper transitionActionMapper;
    private final ActionConfigValidator actionConfigValidator;
    private final IssueStatusMapper issueStatusMapper;
    private final ObjectMapper objectMapper;

    /**
     * 列表查询（支持 projectId + 可选过滤条件）
     */
    public List<TransitionAction> list(Long projectId, String issueType,
                                       Long oldStatusId, Long newStatusId) {
        LambdaQueryWrapper<TransitionAction> wrapper = new LambdaQueryWrapper<>();

        // projectId=0 查全局（project_id IS NULL）
        if (projectId == null || projectId == 0L) {
            wrapper.isNull(TransitionAction::getProjectId);
        } else {
            wrapper.eq(TransitionAction::getProjectId, projectId);
        }

        if (issueType != null && !issueType.isBlank()) {
            wrapper.eq(TransitionAction::getIssueType, issueType);
        }
        if (oldStatusId != null) {
            wrapper.eq(TransitionAction::getOldStatusId, oldStatusId);
        }
        if (newStatusId != null) {
            wrapper.eq(TransitionAction::getNewStatusId, newStatusId);
        }

        wrapper.orderByAsc(TransitionAction::getSortOrder);
        return transitionActionMapper.selectList(wrapper);
    }

    /**
     * 创建转换动作
     */
    public TransitionAction create(CreateTransitionActionDTO dto, Long currentUserId) {
        // 校验 actionConfig
        List<String> errors = actionConfigValidator.validate(dto.getActionConfig());
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, String.join("; ", errors));
        }

        // 校验状态 ID 存在（oldStatusId 为 null 时表示"创建时触发"，无需校验）
        if (dto.getOldStatusId() != null && issueStatusMapper.selectById(dto.getOldStatusId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "oldStatusId 对应的状态不存在");
        }
        if (issueStatusMapper.selectById(dto.getNewStatusId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "newStatusId 对应的状态不存在");
        }

        TransitionAction action = new TransitionAction();
        // projectId=0 表示全局，存为 null
        action.setProjectId(dto.getProjectId() == 0L ? null : dto.getProjectId());
        action.setIssueType(dto.getIssueType());
        action.setOldStatusId(dto.getOldStatusId()); // null = on-create trigger
        action.setNewStatusId(dto.getNewStatusId());
        action.setActionType(dto.getActionType());
        action.setActionConfig(serializeConfig(dto.getActionConfig()));
        action.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        action.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        action.setCreatedBy(currentUserId);
        action.setCreatedAt(LocalDateTime.now());
        action.setUpdatedAt(LocalDateTime.now());

        transitionActionMapper.insert(action);
        return action;
    }

    /**
     * 更新转换动作（部分更新，仅非 null 字段生效）
     */
    public TransitionAction update(Long id, UpdateTransitionActionDTO dto) {
        TransitionAction action = transitionActionMapper.selectById(id);
        if (action == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "转换动作不存在");
        }

        // 如果提供了 actionConfig，需要重新校验
        if (dto.getActionConfig() != null) {
            List<String> errors = actionConfigValidator.validate(dto.getActionConfig());
            if (!errors.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, String.join("; ", errors));
            }
            action.setActionConfig(serializeConfig(dto.getActionConfig()));
        }

        if (dto.getActionType() != null) {
            action.setActionType(dto.getActionType());
        }
        if (dto.getSortOrder() != null) {
            action.setSortOrder(dto.getSortOrder());
        }
        if (dto.getEnabled() != null) {
            action.setEnabled(dto.getEnabled());
        }

        action.setUpdatedAt(LocalDateTime.now());
        transitionActionMapper.updateById(action);
        return action;
    }

    /**
     * 删除转换动作
     */
    public void delete(Long id) {
        TransitionAction action = transitionActionMapper.selectById(id);
        if (action == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "转换动作不存在");
        }
        transitionActionMapper.deleteById(id);
    }

    /**
     * 切换启用状态
     */
    public void toggleEnabled(Long id) {
        TransitionAction action = transitionActionMapper.selectById(id);
        if (action == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "转换动作不存在");
        }
        action.setEnabled(!action.getEnabled());
        action.setUpdatedAt(LocalDateTime.now());
        transitionActionMapper.updateById(action);
    }

    /**
     * 获取动作的 projectId（用于 @PreAuthorize SpEL 表达式）
     * 返回 0L 表示全局（projectId IS NULL 时）
     */
    public Long getProjectId(Long actionId) {
        TransitionAction action = transitionActionMapper.selectById(actionId);
        if (action == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "转换动作不存在");
        }
        return action.getProjectId() != null ? action.getProjectId() : 0L;
    }

    /**
     * 将 Map 配置序列化为 JSON 字符串
     */
    private String serializeConfig(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "actionConfig 序列化失败");
        }
    }
}
