package com.trackflow.project.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.project.dto.CreateProjectDTO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectStatus;
import com.trackflow.project.entity.ProjectVisibility;
import com.trackflow.project.vo.ProjectVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "orgId", expression = "java(longToString(entity.getOrgId()))")
    @Mapping(target = "leadId", expression = "java(longToString(entity.getLeadId()))")
    @Mapping(target = "status", expression = "java(projectStatusToString(entity.getStatus()))")
    @Mapping(target = "visibility", expression = "java(projectVisibilityToString(entity.getVisibility()))")
    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "topMembers", ignore = true)
    @Mapping(target = "favorited", ignore = true)
    ProjectVO toVO(Project entity);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "orgId", expression = "java(longToString(entity.getOrgId()))")
    @Mapping(target = "leadId", expression = "java(longToString(entity.getLeadId()))")
    @Mapping(target = "status", expression = "java(projectStatusToString(entity.getStatus()))")
    @Mapping(target = "visibility", expression = "java(projectVisibilityToString(entity.getVisibility()))")
    @Mapping(target = "myRoleName", ignore = true)
    @Mapping(target = "myRoleCode", ignore = true)
    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "topMembers", ignore = true)
    @Mapping(target = "leadName", ignore = true)
    com.trackflow.project.vo.ProjectDetailVO toDetailVO(Project entity);

    List<ProjectVO> toVOList(List<Project> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orgId", ignore = true)
    @Mapping(target = "issueSequence", ignore = true)
    @Mapping(target = "settings", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "visibility", ignore = true)
    Project toEntity(CreateProjectDTO dto);

    default String projectStatusToString(ProjectStatus status) {
        return status != null ? status.getValue() : null;
    }

    default String projectVisibilityToString(ProjectVisibility visibility) {
        return visibility != null ? visibility.getValue() : null;
    }
}
