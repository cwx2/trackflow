package com.trackflow.automation.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.automation.execution.entity.AutomationExecution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AutomationExecutionMapper extends BaseMapper<AutomationExecution> {}
