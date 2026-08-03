package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueComment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IssueCommentMapper extends BaseMapper<IssueComment> {

    /**
     * 获取某个工单下所有评论者的去重 userId（排除已删除评论）
     */
    @Select("SELECT DISTINCT user_id FROM issue_comment WHERE issue_id = #{issueId} AND deleted_at IS NULL")
    List<Long> selectDistinctCommenterIds(Long issueId);

    /**
     * 查询评论（忽略逻辑删除过滤），用于还原/永久删除操作。
     * MyBatis-Plus 全局逻辑删除会让 selectById 自动加 WHERE deleted_at IS NULL，
     * 此方法绕过该限制。
     */
    @Select("SELECT * FROM issue_comment WHERE id = #{id}")
    IssueComment selectByIdIgnoreDeleted(Long id);

    /**
     * 还原已软删除的评论：将 deleted_at 置为 NULL。
     * MyBatis-Plus updateById 不会更新逻辑删除字段，需用原生 SQL。
     */
    @Update("UPDATE issue_comment SET deleted_at = NULL, updated_at = NOW() WHERE id = #{id}")
    int restoreById(Long id);

    /**
     * 物理删除评论（不可恢复）。
     * 全局逻辑删除下 deleteById 会转为 UPDATE SET deleted_at=NOW()，
     * 此方法执行真正的 DELETE。
     */
    @Delete("DELETE FROM issue_comment WHERE id = #{id}")
    int physicalDeleteById(Long id);
}
