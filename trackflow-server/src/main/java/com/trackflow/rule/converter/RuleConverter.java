package com.trackflow.rule.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.rule.entity.RuleDefinition;
import com.trackflow.rule.entity.RuleExecutionLog;
import com.trackflow.rule.vo.RuleDefinitionVO;
import com.trackflow.rule.vo.RuleExecutionLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RuleConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "projectName", ignore = true)
    @Mapping(target = "createdByName", ignore = true)
    @Mapping(target = "lastExecutedAt", ignore = true)
    @Mapping(target = "executionCount", ignore = true)
    RuleDefinitionVO toDefinitionVO(RuleDefinition entity);

    List<RuleDefinitionVO> toDefinitionVOList(List<RuleDefinition> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "ruleId", expression = "java(longToString(entity.getRuleId()))")
    @Mapping(target = "issueId", expression = "java(longToString(entity.getIssueId()))")
    @Mapping(target = "targetUserId", expression = "java(longToString(entity.getTargetUserId()))")
    @Mapping(target = "ruleName", ignore = true)
    @Mapping(target = "issueKey", ignore = true)
    @Mapping(target = "issueTitle", ignore = true)
    @Mapping(target = "targetUserName", ignore = true)
    RuleExecutionLogVO toLogVO(RuleExecutionLog entity);

    List<RuleExecutionLogVO> toLogVOList(List<RuleExecutionLog> entities);
}
