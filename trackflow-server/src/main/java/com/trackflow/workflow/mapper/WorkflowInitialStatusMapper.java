package com.trackflow.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.workflow.entity.WorkflowInitialStatus;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流初始状态配置 Mapper
 *
 * @author TrackFlow
 * @since 1.0
 */
@Mapper
public interface WorkflowInitialStatusMapper extends BaseMapper<WorkflowInitialStatus> {
}
