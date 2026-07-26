package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueVisibilityUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单可见性用户关联 Mapper
 */
@Mapper
public interface IssueVisibilityUserMapper extends BaseMapper<IssueVisibilityUser> {

    /**
     * 查询有权访问某受限工单的用户 ID 列表
     */
    @Select("SELECT user_id FROM issue_visibility_user WHERE issue_id = #{issueId}")
    List<Long> selectUserIdsByIssueId(@Param("issueId") Long issueId);

    /**
     * 判断指定用户是否在某工单的可见性用户列表中
     */
    @Select("SELECT COUNT(1) > 0 FROM issue_visibility_user WHERE issue_id = #{issueId} AND user_id = #{userId}")
    boolean existsByIssueIdAndUserId(@Param("issueId") Long issueId, @Param("userId") Long userId);

    /**
     * 删除工单的所有可见性用户（用于重置后重新设置）
     */
    @Delete("DELETE FROM issue_visibility_user WHERE issue_id = #{issueId}")
    int deleteByIssueId(@Param("issueId") Long issueId);
}
