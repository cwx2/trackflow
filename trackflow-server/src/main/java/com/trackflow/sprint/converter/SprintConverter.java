package com.trackflow.sprint.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.vo.SprintVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SprintConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "status", expression = "java(sprintStatusToString(entity.getStatus()))")
    SprintVO toVO(Sprint entity);

    List<SprintVO> toVOList(List<Sprint> entities);

    default String sprintStatusToString(SprintStatus status) {
        return status != null ? status.getValue() : null;
    }
}
