package com.trackflow.integration.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.vo.NotificationPreferenceVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 通知偏好 Entity → VO 转换器
 */
@Mapper(componentModel = "spring")
public interface NotificationPreferenceConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    NotificationPreferenceVO toVO(NotificationPreference entity);

    List<NotificationPreferenceVO> toVOList(List<NotificationPreference> entities);
}

