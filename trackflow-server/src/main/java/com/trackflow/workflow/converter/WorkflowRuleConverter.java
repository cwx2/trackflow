package com.trackflow.workflow.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.vo.WorkflowRuleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 工作流规则 Converter（Entity ↔ VO）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public interface WorkflowRuleConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(rule.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(rule.getProjectId()))")
    @Mapping(target = "createdBy", expression = "java(longToString(rule.getCreatedBy()))")
    WorkflowRuleVO toVO(WorkflowRule rule);

    List<WorkflowRuleVO> toVOList(List<WorkflowRule> rules);
}
