package com.trackflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.system.entity.SysAuditLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统审计日志 Mapper
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

    /**
     * 分批删除指定时间之前的审计日志
     *
     * @param cutoff    截止时间
     * @param batchSize 每批删除的最大数量
     * @return 实际删除的记录数
     */
    @Delete("DELETE FROM sys_audit_log WHERE id IN (" +
            "SELECT id FROM sys_audit_log WHERE created_at < #{cutoff} ORDER BY created_at ASC LIMIT #{batchSize})")
    int deleteBeforeCutoff(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    /**
     * 查询指定时间范围内的审计日志（用于导出）
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 审计日志列表
     */
    @Select("SELECT * FROM sys_audit_log WHERE created_at >= #{startTime} AND created_at <= #{endTime} " +
            "ORDER BY created_at DESC")
    List<SysAuditLog> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);
}
