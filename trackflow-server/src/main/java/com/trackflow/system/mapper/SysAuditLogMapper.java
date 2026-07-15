package com.trackflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.system.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统审计日志 Mapper
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
