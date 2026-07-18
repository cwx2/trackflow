package com.trackflow.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.rule.entity.RuleExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 规则执行记录 Mapper
 */
@Mapper
public interface RuleExecutionLogMapper extends BaseMapper<RuleExecutionLog> {

    /**
     * 获取某个用户在某规则下的累计执行次数
     */
    @Select("SELECT COUNT(*) FROM rule_execution_log WHERE rule_id = #{ruleId} AND target_user_id = #{userId}")
    int countByRuleAndUser(@Param("ruleId") Long ruleId, @Param("userId") Long userId);

    /**
     * 获取某个 Issue 的累计罚分
     */
    @Select("SELECT COALESCE(SUM(score), 0) FROM rule_execution_log WHERE issue_id = #{issueId}")
    BigDecimal sumScoreByIssue(@Param("issueId") Long issueId);
}
