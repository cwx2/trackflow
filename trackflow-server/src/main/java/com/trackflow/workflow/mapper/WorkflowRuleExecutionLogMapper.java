package com.trackflow.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.workflow.entity.WorkflowRuleExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 规则执行日志 Mapper。
 */
@Mapper
public interface WorkflowRuleExecutionLogMapper extends BaseMapper<WorkflowRuleExecutionLog> {

    /**
     * 查询指定规则最近 N 条执行日志（时间倒序）
     */
    @Select("""
        SELECT * FROM workflow_rule_execution_log
        WHERE rule_id = #{ruleId}
        ORDER BY executed_at DESC
        LIMIT #{limit}
        """)
    List<WorkflowRuleExecutionLog> findRecentLogs(@Param("ruleId") Long ruleId,
                                                   @Param("limit") int limit);
}
