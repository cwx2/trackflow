package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueVote;
import com.trackflow.issue.vo.IssueVoterVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 工单投票 Mapper - 纯数据库操作，不含业务逻辑
 *
 * @author TrackFlow
 * @since 1.0
 */
@Mapper
public interface IssueVoteMapper extends BaseMapper<IssueVote> {

    @Select("SELECT COUNT(*) FROM issue_vote WHERE issue_id = #{issueId}")
    int countByIssueId(@Param("issueId") Long issueId);

    @Select("SELECT COUNT(*) > 0 FROM issue_vote WHERE issue_id = #{issueId} AND user_id = #{userId}")
    boolean isVoted(@Param("issueId") Long issueId, @Param("userId") Long userId);

    @Select("""
            SELECT CAST(v.user_id AS VARCHAR) AS userId,
                   u.display_name AS displayName,
                   u.username,
                   u.avatar_url AS avatarUrl
            FROM issue_vote v
            LEFT JOIN sys_user u ON u.id = v.user_id
            WHERE v.issue_id = #{issueId}
            ORDER BY v.created_at ASC
            """)
    List<IssueVoterVO> selectVotersWithUser(@Param("issueId") Long issueId);

    @Select("SELECT user_id FROM issue_vote WHERE issue_id = #{issueId}")
    List<Long> selectVoterUserIds(@Param("issueId") Long issueId);

    @Update("UPDATE issue SET vote_count = (SELECT COUNT(*) FROM issue_vote WHERE issue_id = #{issueId}) WHERE id = #{issueId}")
    void refreshVoteCount(@Param("issueId") Long issueId);
}
