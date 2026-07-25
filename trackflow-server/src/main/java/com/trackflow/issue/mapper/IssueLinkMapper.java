package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueLink;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IssueLinkMapper extends BaseMapper<IssueLink> {

    /**
     * 统计使用指定关联类型的关联记录数量（用于删除前影响预检）
     */
    @Select("SELECT COUNT(*) FROM issue_link WHERE link_type = #{linkTypeName}")
    long countByLinkType(@Param("linkTypeName") String linkTypeName);

    /**
     * 删除所有使用指定关联类型的关联记录（级联清理，对标 YouTrack 行为）
     */
    @Delete("DELETE FROM issue_link WHERE link_type = #{linkTypeName}")
    long deleteByLinkType(@Param("linkTypeName") String linkTypeName);
}
