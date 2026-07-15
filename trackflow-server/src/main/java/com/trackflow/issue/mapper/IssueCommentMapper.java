package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IssueCommentMapper extends BaseMapper<IssueComment> {

    /**
     * 获取某个工单下所有评论者的去重 userId（排除已删除评论）
     */
    @Select("SELECT DISTINCT user_id FROM issue_comment WHERE issue_id = #{issueId} AND deleted_at IS NULL")
    List<Long> selectDistinctCommenterIds(Long issueId);
}
