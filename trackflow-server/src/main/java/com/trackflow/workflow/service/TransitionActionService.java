package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.workflow.WorkflowScope;
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
import java.util.Set;

/**
 * 转换动作管理服务 —— 提供 CRUD 操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitionActionService {

    /**
     * 支持的动作类型白名单。
     * 未来新增动作类型时在此扩展，并同步更新：
     * 1. TransitionActionEngine 中的处理逻辑
     * 2. 数据库 CHECK 约束（新增 Flyway 迁移）
     * 3. 前端 TransitionActionForm.vue 中的下拉选项
     */
    private static final Set<String> VALID_ACTION_TYPES = Set.of("auto_assign");

    private final TransitionActionMapper transitionActionMapper;
    private final ActionConfigValidator actionConfigValidator;
    private final IssueStatusMapper issueStatusMapper;
    private final ObjectMapper objectMapper;

    /**
     * 列表查询（支持 projectId + 可选过滤条件）
     * <p>
     * projectId 已由 Controller 通过 {@link WorkflowScope#fromApi(Long)} 转换：
     * null 表示查全局（project_id IS NULL），非 null 查指定项目。
     */
    public List<TransitionAction> list(Long projectId, String issueType,
                                       Long oldStatusId, Long newStatusId) {
        LambdaQueryWrapper<TransitionAction> wrapper = new LambdaQueryWrapper<>();

        if (projectId == null) {
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
        // 校验 actionType 合法性
        validateActionType(dto.getActionType());

        // 校验 actionConfig 结构
        List<String> errors = actionConfigValidator.validate(dto.getActionConfig());
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, String.join("; ", errors));
        }

        // 统一将 API 层的 projectId 转为 Service 层语义（0→null）
        Long effectiveProjectId = WorkflowScope.fromApi(dto.getProjectId());

        // 校验 actionConfig 中引用实体的存在性（用户/角色）
        List<String> entityErrors = actionConfigValidator.validateEntityExistence(
                dto.getActionConfig(), effectiveProjectId);
        if (!entityErrors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, String.join("; ", entityErrors));
        }

        // 校验状态 ID 存在（oldStatusId 为 null 时表示"创建时触发"，无需校验）
        if (dto.getOldStatusId() != null && issueStatusMapper.selectById(dto.getOldStatusId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "oldStatusId 对应的状态不存在");
        }
        if (issueStatusMapper.selectById(dto.getNewStatusId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "newStatusId 对应的状态不存在");
        }

        // 唯一性校验：同一转换路径上不允许存在相同 action_type 的动作
        checkDuplicateAction(effectiveProjectId, dto.getIssueType(),
                dto.getOldStatusId(), dto.getNewStatusId(), dto.getActionType(), null);

        TransitionAction action = new TransitionAction();
        action.setProjectId(effectiveProjectId);
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
            // 校验引用实体存在性
            Long projectId = action.getProjectId();
            List<String> entityErrors = actionConfigValidator.validateEntityExistence(
                    dto.getActionConfig(), projectId);
            if (!entityErrors.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, String.join("; ", entityErrors));
            }
            action.setActionConfig(serializeConfig(dto.getActionConfig()));
        }

        if (dto.getActionType() != null) {
            // 校验 actionType 合法性
            validateActionType(dto.getActionType());
            // 如果 actionType 发生变化，需检查唯一性
            if (!dto.getActionType().equals(action.getActionType())) {
                checkDuplicateAction(action.getProjectId(), action.getIssueType(),
                        action.getOldStatusId(), action.getNewStatusId(),
                        dto.getActionType(), id);
            }
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
     * 获取动作的 projectId（用于 @PreAuthorize SpEL 表达式）。
     * <p>
     * 返回 API 层语义的 projectId：全局（DB 中 null）→ 0L，项目级 → 实际 ID。
     * 配合 {@link WorkflowScope#isGlobal(Long)} 在 SpEL 中判断。
     */
    public Long getProjectId(Long actionId) {
        TransitionAction action = transitionActionMapper.selectById(actionId);
        if (action == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "转换动作不存在");
        }
        return WorkflowScope.toApi(action.getProjectId());
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

    /**
     * 检查同一转换路径上是否已存在相同 action_type 的动作。
     * excludeId 不为 null 时排除该 ID（用于更新场景）。
     */
    private void checkDuplicateAction(Long projectId, String issueType,
                                      Long oldStatusId, Long newStatusId,
                                      String actionType, Long excludeId) {
        LambdaQueryWrapper<TransitionAction> wrapper = new LambdaQueryWrapper<>();

        if (projectId == null) {
            wrapper.isNull(TransitionAction::getProjectId);
        } else {
            wrapper.eq(TransitionAction::getProjectId, projectId);
        }

        wrapper.eq(TransitionAction::getIssueType, issueType);

        if (oldStatusId == null) {
            wrapper.isNull(TransitionAction::getOldStatusId);
        } else {
            wrapper.eq(TransitionAction::getOldStatusId, oldStatusId);
        }

        wrapper.eq(TransitionAction::getNewStatusId, newStatusId);
        wrapper.eq(TransitionAction::getActionType, actionType);

        if (excludeId != null) {
            wrapper.ne(TransitionAction::getId, excludeId);
        }

        if (transitionActionMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该转换路径已存在相同类型的自动化动作，不允许重复创建");
        }
    }

    /**
     * 校验 actionType 是否属于支持的白名单。
     * 非法值抛出 BusinessException。
     */
    private void validateActionType(String actionType) {
        if (!VALID_ACTION_TYPES.contains(actionType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    String.format("不支持的动作类型: %s，当前支持: %s",
                            actionType, String.join(", ", VALID_ACTION_TYPES)));
        }
    }
}
