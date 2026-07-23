package com.trackflow.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.integration.entity.NotificationSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NotificationSubscriptionMapper extends BaseMapper<NotificationSubscription> {

    /**
     * 查询订阅了指定标签的所有用户ID列表（用于通知匹配）。
     * 仅返回对应事件开关为 true 的订阅者。
     *
     * @param tagId    标签ID
     * @param eventKey 事件键（如 "onCreated"、"onUpdated"、"onCommented" 等）
     */
    @Select("SELECT DISTINCT user_id FROM notification_subscription " +
            "WHERE source_type = 'tag' AND source_id = #{tagId} " +
            "AND (events->>#{eventKey})::boolean = true")
    List<Long> selectUserIdsByTagAndEvent(@Param("tagId") Long tagId, @Param("eventKey") String eventKey);

    /**
     * 查询订阅了指定保存搜索的所有用户ID列表。
     */
    @Select("SELECT DISTINCT user_id FROM notification_subscription " +
            "WHERE source_type = 'saved_query' AND source_id = #{savedQueryId} " +
            "AND (events->>#{eventKey})::boolean = true")
    List<Long> selectUserIdsBySavedQueryAndEvent(@Param("savedQueryId") Long savedQueryId, @Param("eventKey") String eventKey);

    /**
     * 查询拥有指定内建订阅的用户ID列表。
     */
    @Select("SELECT DISTINCT user_id FROM notification_subscription " +
            "WHERE source_type = 'builtin' AND builtin_key = #{builtinKey} " +
            "AND (events->>#{eventKey})::boolean = true")
    List<Long> selectUserIdsByBuiltinAndEvent(@Param("builtinKey") String builtinKey, @Param("eventKey") String eventKey);

    /**
     * 查询订阅了指定项目的所有用户ID列表。
     */
    @Select("SELECT DISTINCT user_id FROM notification_subscription " +
            "WHERE source_type = 'project' AND source_id = #{projectId} " +
            "AND (events->>#{eventKey})::boolean = true")
    List<Long> selectUserIdsByProjectAndEvent(@Param("projectId") Long projectId, @Param("eventKey") String eventKey);

    /**
     * 查询所有 saved_query 类型订阅中启用了指定事件的记录（含 source_id 和 user_id）。
     * 用于通知分发时批量评估哪些 saved search 订阅匹配当前工单。
     */
    @Select("SELECT id, user_id, source_id FROM notification_subscription " +
            "WHERE source_type = 'saved_query' " +
            "AND (events->>#{eventKey})::boolean = true")
    List<NotificationSubscription> selectSavedQuerySubscriptionsByEvent(@Param("eventKey") String eventKey);
}
