package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.UserTagFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserTagFavoriteMapper extends BaseMapper<UserTagFavorite> {

    /**
     * 获取用户收藏标签及每个标签的工单匹配数量
     * 仅统计用户可访问项目中的未删除工单
     */
    @Select("""
        SELECT t.id AS tag_id, t.name, t.color, t.project_id,
               COALESCE(cnt.issue_count, 0) AS issue_count
        FROM user_tag_favorite f
        JOIN issue_tag t ON t.id = f.tag_id
        LEFT JOIN (
            SELECT r.tag_id, COUNT(*) AS issue_count
            FROM issue_tag_relation r
            JOIN issue i ON i.id = r.issue_id AND i.deleted_at IS NULL
            GROUP BY r.tag_id
        ) cnt ON cnt.tag_id = t.id
        WHERE f.user_id = #{userId}
        ORDER BY f.sort_order ASC, f.created_at ASC
    """)
    List<Map<String, Object>> selectFavoriteTagsWithCount(@Param("userId") Long userId);

    /**
     * 获取用户收藏标签及每个标签的工单匹配数量（限定项目范围）
     */
    @Select("""
        SELECT t.id AS tag_id, t.name, t.color, t.project_id,
               COALESCE(cnt.issue_count, 0) AS issue_count
        FROM user_tag_favorite f
        JOIN issue_tag t ON t.id = f.tag_id
        LEFT JOIN (
            SELECT r.tag_id, COUNT(*) AS issue_count
            FROM issue_tag_relation r
            JOIN issue i ON i.id = r.issue_id AND i.deleted_at IS NULL
                        AND i.project_id = #{projectId}
            GROUP BY r.tag_id
        ) cnt ON cnt.tag_id = t.id
        WHERE f.user_id = #{userId}
          AND t.project_id = #{projectId}
        ORDER BY f.sort_order ASC, f.created_at ASC
    """)
    List<Map<String, Object>> selectFavoriteTagsWithCountByProject(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
