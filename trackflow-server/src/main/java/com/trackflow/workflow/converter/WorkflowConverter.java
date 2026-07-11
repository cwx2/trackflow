package com.trackflow.workflow.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.vo.WorkflowTransitionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WorkflowConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "roleId", expression = "java(longToString(entity.getRoleId()))")
    @Mapping(target = "oldStatusId", expression = "java(longToString(entity.getOldStatusId()))")
    @Mapping(target = "newStatusId", expression = "java(longToString(entity.getNewStatusId()))")
    WorkflowTransitionVO toVO(WorkflowTransition entity);

    List<WorkflowTransitionVO> toVOList(List<WorkflowTransition> entities);
}
