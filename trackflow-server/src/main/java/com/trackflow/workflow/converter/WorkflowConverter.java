package com.trackflow.workflow.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.workflow.entity.WorkflowActivity;
import com.trackflow.workflow.entity.WorkflowInitialStatus;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.vo.WorkflowActivityVO;
import com.trackflow.workflow.vo.WorkflowInitialStatusVO;
import com.trackflow.workflow.vo.WorkflowTransitionVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作流模块转换器接口
 */
public interface WorkflowConverter extends BaseConverter {

    WorkflowTransitionVO toVO(WorkflowTransition entity);

    List<WorkflowTransitionVO> toVOList(List<WorkflowTransition> entities);

    /**
     * 批量转换工作流变更活动记录为 VO（含关联数据解析）
     */
    List<WorkflowActivityVO> toActivityVOList(List<WorkflowActivity> activities);

    /**
     * WorkflowInitialStatus Entity → VO（Long ID 转 String）
     */
    default WorkflowInitialStatusVO toInitialStatusVO(WorkflowInitialStatus entity) {
        if (entity == null) return null;
        WorkflowInitialStatusVO vo = new WorkflowInitialStatusVO();
        vo.setId(longToString(entity.getId()));
        vo.setProjectId(longToString(entity.getProjectId()));
        vo.setIssueType(entity.getIssueType());
        vo.setStatusId(longToString(entity.getStatusId()));
        return vo;
    }

    default List<WorkflowInitialStatusVO> toInitialStatusVOList(List<WorkflowInitialStatus> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(this::toInitialStatusVO).collect(Collectors.toList());
    }
}
