package com.trackflow.automation.converter;

import com.trackflow.automation.entity.AutomationWorkflowTemplate;
import com.trackflow.automation.vo.WorkflowTemplateVO;
import com.trackflow.common.converter.BaseConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 工作流模板 Converter
 */
@Mapper(componentModel = "spring")
public interface AutomationWorkflowTemplateConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    WorkflowTemplateVO toVO(AutomationWorkflowTemplate entity);

    List<WorkflowTemplateVO> toVOList(List<AutomationWorkflowTemplate> entities);
}
