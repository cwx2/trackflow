package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IssueAttachmentMapper extends BaseMapper<IssueAttachment> {

    /**
     * 查询项目下所有附件的文件存储路径（通过 issue 表关联）。
     * 用于项目删除时清理 MinIO 中的物理文件。
     * 包含已软删除工单的附件（deleted_at IS NOT NULL），确保不留孤立文件。
     */
    @Select("""
            SELECT a.file_path
            FROM issue_attachment a
            JOIN issue i ON a.issue_id = i.id
            WHERE i.project_id = #{projectId}
            """)
    List<String> selectFilePathsByProjectId(@Param("projectId") Long projectId);
}
