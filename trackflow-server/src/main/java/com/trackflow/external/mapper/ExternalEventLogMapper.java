package com.trackflow.external.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.external.common.ExternalEventLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * ExternalEventLog Mapper。
 * <p>
 * 位于 mapper 子包以被 @MapperScan("com.trackflow.**.mapper") 正确扫描。
 */
@Mapper
public interface ExternalEventLogMapper extends BaseMapper<ExternalEventLog> {
}
