package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueWatcher;
import com.trackflow.issue.vo.IssueWatcherVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单关注 Mapper
 */
@Mapper
public interface IssueWatcherMapper extends BaseMapper<IssueWatcher> {

    /**
     * 查询某工单的所有关注者用户ID
     */
    @Select("SELECT user_id FROM issue_watcher WHERE issue_id = #{issueId}")
    List<Long> selectWatcherUserIds(@Param("issueId") Long issueId);

    /**
     * 查询某工单的关注者（含用户信息），一次 JOIN 查询避免 N+1
     */
    @Select("""
            SELECT CAST(w.user_id AS VARCHAR) AS userId,
                   u.display_name AS displayName,
                   u.username AS username,
                   u.avatar_url AS avatarUrl
            FROM issue_watcher w
            LEFT JOIN sys_user u ON u.id = w.user_id
            WHERE w.issue_id = #{issueId}
            ORDER BY w.created_at ASC
            """)
    List<IssueWatcherVO> selectWatchersWithUser(@Param("issueId") Long issueId);

    /**
     * 查询某工单的关注者数量
     */
    @Select("SELECT COUNT(*) FROM issue_watcher WHERE issue_id = #{issueId}")
    int countByIssueId(@Param("issueId") Long issueId);

    /**
     * 检查用户是否已关注某工单
     */
    @Select("SELECT COUNT(*) > 0 FROM issue_watcher WHERE issue_id = #{issueId} AND user_id = #{userId}")
    boolean isWatching(@Param("issueId") Long issueId, @Param("userId") Long userId);
}
