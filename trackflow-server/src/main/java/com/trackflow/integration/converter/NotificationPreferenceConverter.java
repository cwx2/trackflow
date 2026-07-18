package com.trackflow.integration.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.vo.NotificationPreferenceVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 通知偏好 Entity → VO 转换器。
 * 自动映射同名字段（包含 notifyOwnChanges 等布尔开关字段）。
 * 新增字段需在此处明确列出以确保增量编译时正确映射。
 */
@Mapper(componentModel = "spring")
public interface NotificationPreferenceConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "onDueDate", source = "onDueDate")
    @Mapping(target = "onOverdue", source = "onOverdue")
    @Mapping(target = "dueDateAdvanceDays", source = "dueDateAdvanceDays")
    NotificationPreferenceVO toVO(NotificationPreference entity);

    List<NotificationPreferenceVO> toVOList(List<NotificationPreference> entities);
}

