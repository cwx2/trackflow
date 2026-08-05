package com.trackflow.integration.converter;
import com.trackflow.common.converter.BaseConverter;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.vo.NotificationVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationConverter extends BaseConverter {
    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    @Mapping(target = "actorId", expression = "java(longToString(entity.getActorId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "resourceId", expression = "java(longToString(entity.getResourceId()))")
    @Mapping(target = "actorName", ignore = true)
    @Mapping(target = "actorAvatar", ignore = true)
    @Mapping(target = "reasonLabel", ignore = true)
    @Mapping(target = "resourceMuted", ignore = true)
    // updatedAt + aggregationCount auto-mapped by name
    NotificationVO toVO(Notification entity);
    List<NotificationVO> toVOList(List<Notification> entities);
}
