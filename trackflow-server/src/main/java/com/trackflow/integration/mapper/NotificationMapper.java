package com.trackflow.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.integration.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 查询各通知类型的数量分布
     */
    @Select("SELECT type, COUNT(*) AS cnt FROM notification GROUP BY type ORDER BY cnt DESC")
    List<Map<String, Object>> selectTypeDistribution();

    /**
     * 原子 upsert 聚合通知（消除并发 TOCTOU 竞态）。
     * <p>
     * 若窗口期内已存在同一 user_id+type+resource_type+resource_id 的未读通知，
     * 则更新其 title/content/actor_id/updated_at，并递增 aggregation_count，重置 mail_sent=false；
     * 否则新插入一条通知。
     * <p>
     * 依赖 V304 创建的部分唯一索引：
     * {@code idx_notification_unread_agg ON notification(user_id, type, resource_type, resource_id)
     * WHERE is_read = false AND resource_id IS NOT NULL}
     *
     * @return 1=新插入，2=已聚合更新（PostgreSQL ON CONFLICT 返回受影响行数规则）
     */
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO notification(id, user_id, actor_id, project_id, title, content, type,
                                     resource_type, resource_id, source_id, reason,
                                     is_read, aggregation_count, mail_sent, created_at, updated_at)
            VALUES(#{id}, #{userId}, #{actorId}, #{projectId}, #{title}, #{content}, #{type},
                   #{resourceType}, #{resourceId}, #{sourceId}, #{reason},
                   false, 1, false, NOW(), NOW())
            ON CONFLICT (user_id, type, resource_type, resource_id)
            WHERE is_read = false AND resource_id IS NOT NULL
            DO UPDATE SET
                title             = EXCLUDED.title,
                content           = EXCLUDED.content,
                actor_id          = EXCLUDED.actor_id,
                source_id         = EXCLUDED.source_id,
                is_read           = false,
                mail_sent         = false,
                aggregation_count = COALESCE(notification.aggregation_count, 1) + 1,
                updated_at        = NOW()
            """)
    int upsertForAggregation(@Param("id") Long id,
                             @Param("userId") Long userId,
                             @Param("actorId") Long actorId,
                             @Param("projectId") Long projectId,
                             @Param("title") String title,
                             @Param("content") String content,
                             @Param("type") String type,
                             @Param("resourceType") String resourceType,
                             @Param("resourceId") Long resourceId,
                             @Param("sourceId") Long sourceId,
                             @Param("reason") String reason);
}
