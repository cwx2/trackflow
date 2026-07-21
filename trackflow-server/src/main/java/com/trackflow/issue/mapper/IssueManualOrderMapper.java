package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueManualOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface IssueManualOrderMapper extends BaseMapper<IssueManualOrder> {

    /**
     * 查询指定上下文的手动排序列表（按 position 升序）
     */
    @Select("""
        SELECT * FROM issue_manual_order
        WHERE context_type = #{contextType}
          AND context_id = #{contextId}
          AND COALESCE(user_id, 0) = COALESCE(#{userId}, 0)
        ORDER BY position ASC
        """)
    List<IssueManualOrder> selectByContext(
            @Param("contextType") String contextType,
            @Param("contextId") Long contextId,
            @Param("userId") Long userId);

    /**
     * 查询指定上下文的手动排序 issue ID 列表（按 position 升序）
     */
    @Select("""
        SELECT issue_id FROM issue_manual_order
        WHERE context_type = #{contextType}
          AND context_id = #{contextId}
          AND COALESCE(user_id, 0) = COALESCE(#{userId}, 0)
        ORDER BY position ASC
        """)
    List<Long> selectIssueIdsByContext(
            @Param("contextType") String contextType,
            @Param("contextId") Long contextId,
            @Param("userId") Long userId);

    /**
     * 删除指定上下文的所有手动排序记录
     */
    @Delete("""
        DELETE FROM issue_manual_order
        WHERE context_type = #{contextType}
          AND context_id = #{contextId}
          AND COALESCE(user_id, 0) = COALESCE(#{userId}, 0)
        """)
    int deleteByContext(
            @Param("contextType") String contextType,
            @Param("contextId") Long contextId,
            @Param("userId") Long userId);

    /**
     * 批量更新 position（用于插入/移动后重排）
     */
    @Update("""
        UPDATE issue_manual_order
        SET position = position + #{delta}, updated_at = now()
        WHERE context_type = #{contextType}
          AND context_id = #{contextId}
          AND COALESCE(user_id, 0) = COALESCE(#{userId}, 0)
          AND position >= #{fromPosition}
          AND position < #{toPosition}
        """)
    int shiftPositions(
            @Param("contextType") String contextType,
            @Param("contextId") Long contextId,
            @Param("userId") Long userId,
            @Param("fromPosition") int fromPosition,
            @Param("toPosition") int toPosition,
            @Param("delta") int delta);
}
