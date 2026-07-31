package com.trackflow.automation.converter;

import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.vo.WorkflowDetailVO;
import com.trackflow.automation.vo.WorkflowVO;
import com.trackflow.common.converter.BaseConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 工作流 Converter
 */
@Mapper(componentModel = "spring")
public interface AutomationWorkflowConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "createdBy", expression = "java(longToString(entity.getCreatedBy()))")
    WorkflowVO toVO(AutomationWorkflow entity);

    List<WorkflowVO> toVOList(List<AutomationWorkflow> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "createdBy", expression = "java(longToString(entity.getCreatedBy()))")
    WorkflowDetailVO toDetailVO(AutomationWorkflow entity);
}
