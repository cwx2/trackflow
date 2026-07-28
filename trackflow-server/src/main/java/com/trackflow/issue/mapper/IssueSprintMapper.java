package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueSprint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 工单-Sprint 关联 Mapper
 */
@Mapper
public interface IssueSprintMapper extends BaseMapper<IssueSprint> {

    /**
     * 查询工单关联的所有 Sprint ID
     */
    @Select("SELECT sprint_id FROM issue_sprint WHERE issue_id = #{issueId} ORDER BY created_at")
    List<Long> selectSprintIdsByIssueId(@Param("issueId") Long issueId);

    /**
     * 批量查询多个工单关联的 Sprint 记录
     */
    @Select("<script>" +
            "SELECT * FROM issue_sprint WHERE issue_id IN " +
            "<foreach collection='issueIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " ORDER BY created_at" +
            "</script>")
    List<IssueSprint> selectByIssueIds(@Param("issueIds") List<Long> issueIds);

    /**
     * 删除工单的所有 Sprint 关联
     */
    @Delete("DELETE FROM issue_sprint WHERE issue_id = #{issueId}")
    int deleteByIssueId(@Param("issueId") Long issueId);

    /**
     * 删除工单与指定 Sprint 的关联
     */
    @Delete("DELETE FROM issue_sprint WHERE issue_id = #{issueId} AND sprint_id = #{sprintId}")
    int deleteByIssueIdAndSprintId(@Param("issueId") Long issueId, @Param("sprintId") Long sprintId);
}
