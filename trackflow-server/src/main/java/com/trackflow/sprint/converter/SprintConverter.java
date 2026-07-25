package com.trackflow.sprint.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.vo.SprintVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SprintConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "status", expression = "java(sprintStatusToString(entity.getStatus()))")
    @Mapping(target = "startScopeHours", expression = "java(bigDecimalToDouble(entity.getStartScopeHours()))")
    // 以下统计字段由 Mapper XML 查询或 Service 层计算后设置，Converter 中不映射
    @Mapping(target = "statusHint", ignore = true)
    @Mapping(target = "overdue", ignore = true)
    @Mapping(target = "totalIssues", ignore = true)
    @Mapping(target = "doneIssues", ignore = true)
    @Mapping(target = "inProgressIssues", ignore = true)
    @Mapping(target = "todoIssues", ignore = true)
    @Mapping(target = "overdueIssues", ignore = true)
    @Mapping(target = "unassignedIssues", ignore = true)
    @Mapping(target = "totalEstimatedHours", ignore = true)
    @Mapping(target = "completedEstimatedHours", ignore = true)
    SprintVO toVO(Sprint entity);

    List<SprintVO> toVOList(List<Sprint> entities);

    default String sprintStatusToString(SprintStatus status) {
        return status != null ? status.getValue() : null;
    }

    default Double bigDecimalToDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }
}
