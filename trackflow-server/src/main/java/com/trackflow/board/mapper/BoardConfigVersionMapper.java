package com.trackflow.board.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.board.entity.BoardConfigVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 看板配置版本 Mapper。
 * 提供 CAS 乐观锁操作。
 */
@Mapper
public interface BoardConfigVersionMapper extends BaseMapper<BoardConfigVersion> {

    /**
     * CAS 更新版本号：只有当前版本等于 expectedVersion 时才递增。
     *
     * @param projectId       项目ID
     * @param expectedVersion 期望的当前版本
     * @param updatedBy       操作人ID
     * @return 更新行数（0 表示版本冲突或记录不存在）
     */
    @Update("""
        UPDATE board_config_version
        SET version = version + 1,
            updated_at = NOW(),
            updated_by = #{updatedBy}
        WHERE project_id = #{projectId}
          AND version = #{expectedVersion}
    """)
    int incrementVersionCAS(@Param("projectId") Long projectId,
                            @Param("expectedVersion") Integer expectedVersion,
                            @Param("updatedBy") Long updatedBy);
}
