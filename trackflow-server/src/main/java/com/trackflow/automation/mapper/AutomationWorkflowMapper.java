package com.trackflow.automation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流 Mapper
 */
@Mapper
public interface AutomationWorkflowMapper extends BaseMapper<AutomationWorkflow> {
}
