package com.trackflow.sprint.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.result.SprintStatsRow;
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

    /**
     * 将 Mapper 查询结果（含统计数据）转为 API 响应 VO。
     * SprintStatsRow 与 SprintVO 字段一一对应，直接映射。
     */
    default SprintVO statsRowToVO(SprintStatsRow row) {
        if (row == null) {
            return null;
        }
        SprintVO vo = new SprintVO();
        vo.setId(row.getId());
        vo.setProjectId(row.getProjectId());
        vo.setProjectName(row.getProjectName());
        vo.setProjectKey(row.getProjectKey());
        vo.setName(row.getName());
        vo.setGoal(row.getGoal());
        vo.setStatus(row.getStatus());
        vo.setStartDate(row.getStartDate());
        vo.setEndDate(row.getEndDate());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setStatusHint(row.getStatusHint());
        vo.setOverdue(row.isOverdue());
        vo.setTotalIssues(row.getTotalIssues());
        vo.setDoneIssues(row.getDoneIssues());
        vo.setInProgressIssues(row.getInProgressIssues());
        vo.setTodoIssues(row.getTodoIssues());
        vo.setOverdueIssues(row.getOverdueIssues());
        vo.setUnassignedIssues(row.getUnassignedIssues());
        vo.setStartedAt(row.getStartedAt());
        vo.setStartScopeHours(row.getStartScopeHours());
        vo.setStartScopeIssues(row.getStartScopeIssues());
        vo.setTotalEstimatedHours(row.getTotalEstimatedHours());
        vo.setCompletedEstimatedHours(row.getCompletedEstimatedHours());
        return vo;
    }

    default List<SprintVO> statsRowToVOList(List<SprintStatsRow> rows) {
        if (rows == null) {
            return null;
        }
        List<SprintVO> result = new java.util.ArrayList<>(rows.size());
        for (SprintStatsRow row : rows) {
            result.add(statsRowToVO(row));
        }
        return result;
    }

    default String sprintStatusToString(SprintStatus status) {
        return status != null ? status.getValue() : null;
    }

    default Double bigDecimalToDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }
}
