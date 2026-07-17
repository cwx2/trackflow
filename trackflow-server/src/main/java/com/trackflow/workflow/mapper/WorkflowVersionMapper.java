package com.trackflow.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.workflow.entity.WorkflowVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 工作流版本 Mapper
 */
@Mapper
public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersion> {

    /**
     * CAS 更新版本号：只有当前版本等于 expectedVersion 时才递增
     *
     * @return 更新行数（0 表示版本冲突）
     */
    @Update("""
        UPDATE workflow_version
        SET version = version + 1,
            updated_at = NOW(),
            updated_by = #{updatedBy}
        WHERE id = #{id}
          AND version = #{expectedVersion}
    """)
    int incrementVersionCAS(@Param("id") Long id,
                            @Param("expectedVersion") Integer expectedVersion,
                            @Param("updatedBy") Long updatedBy);
}
