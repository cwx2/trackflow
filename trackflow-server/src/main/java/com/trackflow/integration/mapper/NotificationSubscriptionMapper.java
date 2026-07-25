package com.trackflow.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.integration.dto.SavedQuerySubscriptionRow;
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
     * 使用 COALESCE 处理缺失键：键不存在时视为 true（防御未来新增事件类型时数据迁移遗漏）。
     *
     * @param tagId    标签ID
     * @param eventKey 事件键（如 "onCreated"、"onUpdated"、"onCommented" 等）
     */
    @Select("SELECT DISTINCT user_id FROM notification_subscription " +
            "WHERE source_type = 'tag' AND source_id = #{tagId} " +
            "AND COALESCE((events->>#{eventKey})::boolean, true) = true")
    List<Long> selectUserIdsByTagAndEvent(@Param("tagId") Long tagId, @Param("eventKey") String eventKey);

    /**
     * 查询订阅了指定保存搜索的所有用户ID列表。
     * 使用 COALESCE 处理缺失键：键不存在时视为 true。
     */
    @Select("SELECT DISTINCT user_id FROM notification_subscription " +
            "WHERE source_type = 'saved_query' AND source_id = #{savedQueryId} " +
            "AND COALESCE((events->>#{eventKey})::boolean, true) = true")
    List<Long> selectUserIdsBySavedQueryAndEvent(@Param("savedQueryId") Long savedQueryId, @Param("eventKey") String eventKey);

    /**
     * 查询拥有指定内建订阅的用户ID列表。
     * 使用 COALESCE 处理缺失键：键不存在时视为 true。
     */
    @Select("SELECT DISTINCT user_id FROM notification_subscription " +
            "WHERE source_type = 'builtin' AND builtin_key = #{builtinKey} " +
            "AND COALESCE((events->>#{eventKey})::boolean, true) = true")
    List<Long> selectUserIdsByBuiltinAndEvent(@Param("builtinKey") String builtinKey, @Param("eventKey") String eventKey);

    /**
     * 查询订阅了指定项目的所有用户ID列表。
     * 使用 COALESCE 处理缺失键：键不存在时视为 true。
     */
    @Select("SELECT DISTINCT user_id FROM notification_subscription " +
            "WHERE source_type = 'project' AND source_id = #{projectId} " +
            "AND COALESCE((events->>#{eventKey})::boolean, true) = true")
    List<Long> selectUserIdsByProjectAndEvent(@Param("projectId") Long projectId, @Param("eventKey") String eventKey);

    /**
     * 查询所有 saved_query 类型订阅中启用了指定事件的记录（含 source_id 和 user_id）。
     * 用于通知分发时批量评估哪些 saved search 订阅匹配当前工单。
     * 使用 COALESCE 处理缺失键：键不存在时视为 true。
     */
    @Select("SELECT id, user_id, source_id FROM notification_subscription " +
            "WHERE source_type = 'saved_query' " +
            "AND COALESCE((events->>#{eventKey})::boolean, true) = true")
    List<NotificationSubscription> selectSavedQuerySubscriptionsByEvent(@Param("eventKey") String eventKey);

    /**
     * 一次 JOIN 查询获取所有启用了指定事件的 saved_query 订阅及其关联的 filters。
     * 用于 collectSavedQuerySubscribers() 批量优化——避免 O(M) 次独立查询。
     * 使用 COALESCE 处理缺失键：键不存在时视为 true。
     *
     * @param eventKey 事件键（如 "onCreated"、"onUpdated"、"onCommented"）
     * @return 包含 userId、sourceId、filters 的结果行列表
     */
    @Select("SELECT ns.user_id, ns.source_id, sq.filters " +
            "FROM notification_subscription ns " +
            "JOIN saved_query sq ON ns.source_id = sq.id " +
            "WHERE ns.source_type = 'saved_query' " +
            "AND COALESCE((ns.events->>#{eventKey})::boolean, true) = true " +
            "AND sq.filters IS NOT NULL AND sq.filters != ''")
    List<SavedQuerySubscriptionRow> selectSavedQuerySubsWithFilters(@Param("eventKey") String eventKey);
}
