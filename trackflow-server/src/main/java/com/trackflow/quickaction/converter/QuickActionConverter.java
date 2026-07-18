package com.trackflow.quickaction.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.quickaction.entity.MailTemplate;
import com.trackflow.quickaction.entity.QuickActionDefinition;
import com.trackflow.quickaction.entity.QuickActionLog;
import com.trackflow.quickaction.vo.MailTemplateVO;
import com.trackflow.quickaction.vo.QuickActionDefinitionVO;
import com.trackflow.quickaction.vo.QuickActionLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuickActionConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    QuickActionDefinitionVO toDefinitionVO(QuickActionDefinition entity);

    List<QuickActionDefinitionVO> toDefinitionVOList(List<QuickActionDefinition> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    MailTemplateVO toMailTemplateVO(MailTemplate entity);

    List<MailTemplateVO> toMailTemplateVOList(List<MailTemplate> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "issueId", expression = "java(longToString(entity.getIssueId()))")
    @Mapping(target = "operatorId", expression = "java(longToString(entity.getOperatorId()))")
    @Mapping(target = "mailTemplateId", expression = "java(longToString(entity.getMailTemplateId()))")
    @Mapping(target = "commentId", expression = "java(longToString(entity.getCommentId()))")
    @Mapping(target = "operatorName", ignore = true)
    @Mapping(target = "mailTemplateName", ignore = true)
    QuickActionLogVO toLogVO(QuickActionLog entity);

    List<QuickActionLogVO> toLogVOList(List<QuickActionLog> entities);
}
