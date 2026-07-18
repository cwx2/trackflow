package com.trackflow.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.rule.entity.RuleDefinition;
import com.trackflow.rule.mapper.RuleDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则定时执行调度器。
 * <p>
 * 每天 09:00 执行所有启用的定时规则。
 * 注意：当前版本不解析每个规则的 cron 表达式，统一在 09:00 执行。
 * 后续可改为动态注册 ScheduledTask。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleScheduler {

    private final RuleDefinitionMapper ruleMapper;
    private final RuleService ruleService;

    /**
     * 每天 09:00 执行所有启用的定时规则
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void executeScheduledRules() {
        log.info("[RuleScheduler] 开始执行定时规则扫描...");

        List<RuleDefinition> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<RuleDefinition>()
                        .eq(RuleDefinition::getEnabled, true)
                        .eq(RuleDefinition::getTriggerType, "scheduled")
        );

        if (rules.isEmpty()) {
            log.info("[RuleScheduler] 无启用的定时规则");
            return;
        }

        int totalExecuted = 0;
        int totalRules = rules.size();
        int failedRules = 0;

        for (RuleDefinition rule : rules) {
            try {
                int count = ruleService.executeScheduledRule(rule);
                totalExecuted += count;
            } catch (Exception e) {
                failedRules++;
                log.error("[RuleScheduler] 规则执行异常: id={}, name={}", rule.getId(), rule.getName(), e);
            }
        }

        log.info("[RuleScheduler] 定时规则执行完成: 共 {} 条规则, 新增记录 {} 条, 失败 {} 条",
                totalRules, totalExecuted, failedRules);
    }
}
