package com.trackflow.integration.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.vo.NotificationPreferenceVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 通知偏好 Entity → VO 转换器
 */
@Mapper(componentModel = "spring")
public interface NotificationPreferenceConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    NotificationPreferenceVO toVO(NotificationPreference entity);
}

