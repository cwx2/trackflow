package com.trackflow.workflow.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.workflow.entity.WorkflowActivity;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.vo.WorkflowActivityVO;
import com.trackflow.workflow.vo.WorkflowTransitionVO;

import java.util.List;

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
}
