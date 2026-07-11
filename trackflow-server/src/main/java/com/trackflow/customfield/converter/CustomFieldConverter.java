package com.trackflow.customfield.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.vo.CustomFieldDefinitionVO;
import com.trackflow.customfield.vo.CustomFieldOptionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomFieldConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "options", ignore = true)
    @Mapping(target = "projectIds", ignore = true)
    @Mapping(target = "issueTypes", ignore = true)
    CustomFieldDefinitionVO toVO(CustomFieldDefinition entity);

    List<CustomFieldDefinitionVO> toVOList(List<CustomFieldDefinition> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "customFieldId", expression = "java(longToString(entity.getCustomFieldId()))")
    CustomFieldOptionVO toOptionVO(CustomFieldOption entity);

    List<CustomFieldOptionVO> toOptionVOList(List<CustomFieldOption> entities);
}
