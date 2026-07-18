package com.trackflow.report.converter;
import com.trackflow.common.converter.BaseConverter;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.vo.ReportDefinitionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ReportConverter extends BaseConverter {
    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "createdBy", expression = "java(longToString(entity.getCreatedBy()))")
    @Mapping(target = "isSystem", source = "isSystem")
    ReportDefinitionVO toVO(ReportDefinition entity);
    List<ReportDefinitionVO> toVOList(List<ReportDefinition> entities);
}
