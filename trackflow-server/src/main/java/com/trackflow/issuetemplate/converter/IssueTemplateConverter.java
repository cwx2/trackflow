package com.trackflow.issuetemplate.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.issuetemplate.entity.IssueTemplate;
import com.trackflow.issuetemplate.dto.SaveIssueTemplateDTO;
import com.trackflow.issuetemplate.vo.IssueTemplateVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 工单模板对象转换器
 */
@Mapper(componentModel = "spring")
public interface IssueTemplateConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "createdBy", expression = "java(longToString(entity.getCreatedBy()))")
    IssueTemplateVO toVO(IssueTemplate entity);

    List<IssueTemplateVO> toVOList(List<IssueTemplate> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    IssueTemplate toEntity(SaveIssueTemplateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(SaveIssueTemplateDTO dto, @MappingTarget IssueTemplate entity);
}
