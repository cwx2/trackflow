package com.trackflow.automation.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.approval.entity.AutomationApproval;
import com.trackflow.automation.approval.mapper.AutomationApprovalMapper;
import com.trackflow.automation.execution.AutomationExecutionStatus;
import com.trackflow.automation.execution.DAGExecutor;
import com.trackflow.automation.execution.WorkflowExecutionCheckpoint;
import com.trackflow.automation.execution.entity.AutomationExecution;
import com.trackflow.automation.execution.mapper.AutomationExecutionMapper;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutomationApprovalService {
    private final AutomationApprovalMapper approvalMapper;
    private final AutomationExecutionMapper executionMapper;
    private final DAGExecutor dagExecutor;
    private final ObjectMapper objectMapper;

    public List<AutomationApproval> listPending() {
        return approvalMapper.selectList(new LambdaQueryWrapper<AutomationApproval>()
                .eq(AutomationApproval::getStatus, "pending")
                .orderByDesc(AutomationApproval::getRiskLevel)
                .orderByAsc(AutomationApproval::getCreatedAt));
    }

    @Transactional
    public AutomationApproval decide(Long id, boolean approved, String comment) {
        AutomationApproval approval = approvalMapper.selectById(id);
        if (approval == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "审批请求不存在: " + id);
        }
        if (!"pending".equals(approval.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "审批请求已处理");
        }
        boolean expired = approval.getExpiresAt() != null
                && approval.getExpiresAt().isBefore(LocalDateTime.now());
        if (expired) {
            approved = false;
            comment = comment != null ? comment : "审批已过期";
        }
        String decision = expired ? "expired" : approved ? "approved" : "rejected";
        Long decidedBy = SecurityUtils.getCurrentUserId();
        int approvalUpdated = approvalMapper.update(null,
                new LambdaUpdateWrapper<AutomationApproval>()
                        .set(AutomationApproval::getStatus, decision)
                        .set(AutomationApproval::getDecidedBy, decidedBy)
                        .set(AutomationApproval::getDecisionComment, comment)
                        .set(AutomationApproval::getDecidedAt, LocalDateTime.now())
                        .eq(AutomationApproval::getId, id)
                        .eq(AutomationApproval::getStatus, "pending"));
        if (approvalUpdated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "审批请求已被其他人处理");
        }

        AutomationExecution execution = executionMapper.selectById(approval.getExecutionId());
        if (execution == null || !AutomationExecutionStatus.WAITING_APPROVAL.getValue()
                .equals(execution.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "对应工作流已不在等待审批状态");
        }
        try {
            WorkflowExecutionCheckpoint checkpoint = objectMapper.readValue(
                    execution.getCheckpoint(), WorkflowExecutionCheckpoint.class);
            checkpoint.setWaitingResult(Map.of(
                    "approved", approved,
                    "rejected", !approved,
                    "comment", comment != null ? comment : ""));
            String checkpointJson = objectMapper.writeValueAsString(checkpoint);
            int executionUpdated = executionMapper.update(null,
                    new LambdaUpdateWrapper<AutomationExecution>()
                            .set(AutomationExecution::getStatus,
                                    AutomationExecutionStatus.RUNNING.getValue())
                            .set(AutomationExecution::getCheckpoint, checkpointJson)
                            .set(AutomationExecution::getHeartbeatAt, LocalDateTime.now())
                            .eq(AutomationExecution::getId, execution.getId())
                            .eq(AutomationExecution::getStatus,
                                    AutomationExecutionStatus.WAITING_APPROVAL.getValue()));
            if (executionUpdated != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "工作流已被其他操作恢复");
            }
            WorkflowDefinitionModel definition = objectMapper.readValue(
                    execution.getDefinitionSnapshot(), WorkflowDefinitionModel.class);
            Map<String, Object> inputs = execution.getInput() == null ? Map.of()
                    : objectMapper.readValue(execution.getInput(), new TypeReference<>() {});
            registerResumeAfterCommit(definition, execution, inputs, checkpointJson);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "恢复审批工作流失败: " + exception.getMessage());
        }
        return approvalMapper.selectById(id);
    }

    private void registerResumeAfterCommit(WorkflowDefinitionModel definition,
                                           AutomationExecution execution,
                                           Map<String, Object> inputs,
                                           String checkpointJson) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dagExecutor.resumeAsync(definition, execution.getId(), inputs,
                        execution.getActorUserId(), checkpointJson);
            }
        });
    }
}
