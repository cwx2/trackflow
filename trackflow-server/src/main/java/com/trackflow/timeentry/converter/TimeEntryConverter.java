package com.trackflow.timeentry.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.vo.TimeEntryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 工时记录实体/VO 转换器 (MapStruct)
 *
 * 负责 TimeEntry Entity → TimeEntryVO 的基础字段映射。
 * 关联查询（issueKey/issueTitle/attributeValues 等）由 Service 层补充填充。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public interface TimeEntryConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "issueId", expression = "java(longToString(entity.getIssueId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    @Mapping(target = "workDate", expression = "java(entity.getWorkDate() != null ? entity.getWorkDate().toString() : null)")
    @Mapping(target = "createdAt", expression = "java(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)")
    @Mapping(target = "updatedAt", expression = "java(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)")
    @Mapping(target = "startedAt", expression = "java(Boolean.TRUE.equals(entity.getOngoing()) && entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)")
    @Mapping(target = "loggedBy", expression = "java(longToString(entity.getLoggedBy()))")
    // 以下字段由 Service 层补充填充
    @Mapping(target = "issueKey", ignore = true)
    @Mapping(target = "issueTitle", ignore = true)
    @Mapping(target = "issueDeleted", ignore = true)
    @Mapping(target = "loggedByName", ignore = true)
    @Mapping(target = "workType", ignore = true)
    @Mapping(target = "workTypeId", ignore = true)
    @Mapping(target = "workTypeColor", ignore = true)
    @Mapping(target = "attributeValues", ignore = true)
    @Mapping(target = "userName", ignore = true)
    TimeEntryVO toVO(TimeEntry entity);

    List<TimeEntryVO> toVOList(List<TimeEntry> entities);
}
