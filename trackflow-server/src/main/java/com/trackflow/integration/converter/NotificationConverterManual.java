package com.trackflow.integration.converter;

import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.vo.NotificationVO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动实现的 NotificationConverter，覆盖 MapStruct 生成的旧版本。
 * 新增 updatedAt + aggregationCount 字段映射。
 * <p>
 * TODO: 当 MapStruct 注解处理器重新生成 NotificationConverterImpl 后（即运行 mvn compile），
 * 可删除此文件——生成的版本会自动包含新字段映射。
 */
@Component
@Primary
public class NotificationConverterManual implements NotificationConverter {

    @Override
    public NotificationVO toVO(Notification entity) {
        if (entity == null) {
            return null;
        }

        NotificationVO vo = new NotificationVO();
        vo.setId(longToString(entity.getId()));
        vo.setUserId(longToString(entity.getUserId()));
        vo.setActorId(longToString(entity.getActorId()));
        vo.setResourceId(longToString(entity.getResourceId()));
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setType(entity.getType());
        vo.setResourceType(entity.getResourceType());
        vo.setIsRead(entity.getIsRead());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setAggregationCount(entity.getAggregationCount());
        return vo;
    }

    @Override
    public List<NotificationVO> toVOList(List<Notification> entities) {
        if (entities == null) {
            return null;
        }
        List<NotificationVO> list = new ArrayList<>(entities.size());
        for (Notification notification : entities) {
            list.add(toVO(notification));
        }
        return list;
    }
}
