package com.trackflow.workflow.service.action;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 工作流动作注册表 — 通过 Spring 自动注入所有 {@link WorkflowActionExecutor} 实现。
 * <p>
 * 引擎通过 {@link #getExecutor(String)} 按动作类型查找对应执行器，
 * 实现动作分发与动作实现的解耦。新增动作类型只需创建新的 @Component，
 * 无需修改此类或引擎核心。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class WorkflowActionRegistry {

    private final Map<String, WorkflowActionExecutor> executors;

    public WorkflowActionRegistry(List<WorkflowActionExecutor> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(
                        WorkflowActionExecutor::actionType,
                        executor -> executor,
                        (existing, duplicate) -> {
                            log.warn("[ActionRegistry] Duplicate executor for action type '{}': {} vs {}. Using first.",
                                    existing.actionType(),
                                    existing.getClass().getSimpleName(),
                                    duplicate.getClass().getSimpleName());
                            return existing;
                        }
                ));
        log.info("[ActionRegistry] Registered {} action executors: {}", executors.size(), executors.keySet());
    }

    /**
     * 根据动作类型获取对应的执行器。
     *
     * @param actionType 动作类型标识
     * @return 执行器（如果存在）
     */
    public Optional<WorkflowActionExecutor> getExecutor(String actionType) {
        return Optional.ofNullable(executors.get(actionType));
    }

    /**
     * 获取所有已注册的动作类型。
     */
    public java.util.Set<String> getRegisteredTypes() {
        return executors.keySet();
    }
}
