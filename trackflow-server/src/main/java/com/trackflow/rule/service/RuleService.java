package com.trackflow.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.rule.converter.RuleConverter;
import com.trackflow.rule.dto.CreateExecutionLogDTO;
import com.trackflow.rule.dto.RuleExecutionLogQuery;
import com.trackflow.rule.dto.SaveRuleDefinitionDTO;
import com.trackflow.rule.entity.RuleDefinition;
import com.trackflow.rule.entity.RuleExecutionLog;
import com.trackflow.rule.mapper.RuleDefinitionMapper;
import com.trackflow.rule.mapper.RuleExecutionLogMapper;
import com.trackflow.rule.vo.RuleDefinitionVO;
import com.trackflow.rule.vo.RuleExecutionLogVO;
import com.trackflow.rule.vo.RuleStatisticsVO;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleDefinitionMapper ruleMapper;
    private final RuleExecutionLogMapper logMapper;
    private final RuleConverter ruleConverter;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final ScoreCalculator scoreCalculator;
    private final ObjectMapper objectMapper;

    // ==================== 规则定义 CRUD ====================

    public List<RuleDefinitionVO> listRules(Long projectId) {
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.and(w -> w.isNull(RuleDefinition::getProjectId).or().eq(RuleDefinition::getProjectId, projectId));
        }
        wrapper.orderByDesc(RuleDefinition::getCreatedAt);

        List<RuleDefinition> rules = ruleMapper.selectList(wrapper);
        List<RuleDefinitionVO> voList = ruleConverter.toDefinitionVOList(rules);

        // 填充附加信息
        enrichDefinitionVOList(voList, rules);
        return voList;
    }

    public RuleDefinitionVO getRuleDetail(Long id) {
        RuleDefinition rule = getRuleOrThrow(id);
        RuleDefinitionVO vo = ruleConverter.toDefinitionVO(rule);
        enrichDefinitionVO(vo, rule);
        return vo;
    }

    @Transactional
    public RuleDefinition createRule(SaveRuleDefinitionDTO dto) {
        validateRuleDTO(dto);

        RuleDefinition rule = new RuleDefinition();
        applyDTO(rule, dto);
        rule.setEnabled(true);
        rule.setCreatedBy(SecurityUtils.getCurrentUserId());
        rule.setUpdatedBy(SecurityUtils.getCurrentUserId());

        ruleMapper.insert(rule);
        log.info("[Rule] 创建规则: id={}, name={}", rule.getId(), rule.getName());

        return rule;
    }

    @Transactional
    public RuleDefinition updateRule(Long id, SaveRuleDefinitionDTO dto) {
        validateRuleDTO(dto);
        RuleDefinition rule = getRuleOrThrow(id);

        applyDTO(rule, dto);
        rule.setUpdatedBy(SecurityUtils.getCurrentUserId());

        ruleMapper.updateById(rule);
        log.info("[Rule] 更新规则: id={}, name={}", rule.getId(), rule.getName());

        return rule;
    }

    @Transactional
    public void deleteRule(Long id) {
        getRuleOrThrow(id);
        // 级联删除执行记录（DB 已配置 ON DELETE CASCADE）
        ruleMapper.deleteById(id);
        log.info("[Rule] 删除规则: id={}", id);
    }

    @Transactional
    public void toggleRule(Long id) {
        RuleDefinition rule = getRuleOrThrow(id);
        rule.setEnabled(!Boolean.TRUE.equals(rule.getEnabled()));
        rule.setUpdatedBy(SecurityUtils.getCurrentUserId());
        ruleMapper.updateById(rule);
        log.info("[Rule] 切换规则状态: id={}, enabled={}", id, rule.getEnabled());
    }

    // ==================== 规则执行 ====================

    /**
     * 手动立即执行某规则
     */
    @Transactional
    public int executeRuleNow(Long id) {
        RuleDefinition rule = getRuleOrThrow(id);
        if (!"scheduled".equals(rule.getTriggerType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有定时规则支持手动执行");
        }
        return executeScheduledRule(rule);
    }

    /**
     * 执行定时规则：扫描匹配的 Issue 并计算分数
     */
    public int executeScheduledRule(RuleDefinition rule) {
        log.info("[RuleExec] 开始执行规则: id={}, name={}", rule.getId(), rule.getName());

        List<Issue> matchedIssues = findMatchingIssues(rule);
        if (matchedIssues.isEmpty()) {
            log.info("[RuleExec] 规则 {} 无命中工单", rule.getName());
            return 0;
        }

        int count = 0;
        LocalDate today = LocalDate.now();

        for (Issue issue : matchedIssues) {
            Long targetUserId = resolveTargetUserId(rule, issue);
            if (targetUserId == null) continue;

            // 去重检查
            if (shouldSkipByDedup(rule, issue, today)) continue;

            // 计算分数
            ScoreCalculator.ScoreResult result = scoreCalculator.calculate(rule, issue);
            if (result.score().compareTo(BigDecimal.ZERO) <= 0) continue;

            // 写入执行记录
            RuleExecutionLog logEntry = new RuleExecutionLog();
            logEntry.setRuleId(rule.getId());
            logEntry.setIssueId(issue.getId());
            logEntry.setTargetUserId(targetUserId);
            logEntry.setScore(result.score());
            logEntry.setScoreDetail(result.scoreDetail());
            logEntry.setExecutedAt(LocalDateTime.now());
            logEntry.setExecutionDate(today);

            try {
                logMapper.insert(logEntry);
                count++;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 去重索引冲突，跳过（daily 策略：同一天已执行过）
                log.debug("[RuleExec] 去重跳过: rule={}, issue={}, date={}", rule.getId(), issue.getId(), today);
            }
        }

        log.info("[RuleExec] 规则 {} 执行完成: 命中 {} 条, 新增记录 {} 条",
                rule.getName(), matchedIssues.size(), count);
        return count;
    }

    // ==================== 执行记录管理 ====================

    public PageResult<RuleExecutionLogVO> listLogs(RuleExecutionLogQuery query) {
        LambdaQueryWrapper<RuleExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getRuleId() != null) wrapper.eq(RuleExecutionLog::getRuleId, query.getRuleId());
        if (query.getIssueId() != null) wrapper.eq(RuleExecutionLog::getIssueId, query.getIssueId());
        if (query.getTargetUserId() != null) wrapper.eq(RuleExecutionLog::getTargetUserId, query.getTargetUserId());
        if (query.getStartDate() != null) wrapper.ge(RuleExecutionLog::getExecutionDate, query.getStartDate());
        if (query.getEndDate() != null) wrapper.le(RuleExecutionLog::getExecutionDate, query.getEndDate());
        wrapper.orderByDesc(RuleExecutionLog::getExecutedAt);

        Page<RuleExecutionLog> page = logMapper.selectPage(query.toPage(), wrapper);
        List<RuleExecutionLogVO> voList = ruleConverter.toLogVOList(page.getRecords());

        // 填充关联信息
        enrichLogVOList(voList, page.getRecords());

        return new PageResult<>(voList, page.getTotal(), query.getPage(), query.getPageSize());
    }

    @Transactional
    public RuleExecutionLogVO createLog(CreateExecutionLogDTO dto) {
        // 验证关联实体存在
        getRuleOrThrow(dto.getRuleId());

        RuleExecutionLog logEntry = new RuleExecutionLog();
        logEntry.setRuleId(dto.getRuleId());
        logEntry.setIssueId(dto.getIssueId());
        logEntry.setTargetUserId(dto.getTargetUserId());
        logEntry.setScore(dto.getScore());
        logEntry.setScoreDetail(dto.getScoreDetail());
        logEntry.setExecutedAt(LocalDateTime.now());
        logEntry.setExecutionDate(LocalDate.now());
        logEntry.setNote(dto.getNote());

        logMapper.insert(logEntry);
        return ruleConverter.toLogVO(logEntry);
    }

    @Transactional
    public void deleteLog(Long id) {
        RuleExecutionLog existing = logMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "执行记录不存在");
        }
        logMapper.deleteById(id);
    }

    // ==================== 统计 ====================

    public RuleStatisticsVO getStatistics(Long ruleId, LocalDate startDate, LocalDate endDate, int rankLimit) {
        RuleStatisticsVO vo = new RuleStatisticsVO();

        // Build base filter conditions
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        // Get all matching logs
        LambdaQueryWrapper<RuleExecutionLog> baseWrapper = new LambdaQueryWrapper<RuleExecutionLog>()
                .ge(RuleExecutionLog::getExecutionDate, effectiveStart)
                .le(RuleExecutionLog::getExecutionDate, effectiveEnd);
        if (ruleId != null) baseWrapper.eq(RuleExecutionLog::getRuleId, ruleId);

        List<RuleExecutionLog> allLogs = logMapper.selectList(baseWrapper);

        // Ranking: group by target_user_id, sum score, sort desc
        Map<Long, BigDecimal> userScoreMap = new HashMap<>();
        Map<Long, Long> userCountMap = new HashMap<>();
        for (RuleExecutionLog log : allLogs) {
            userScoreMap.merge(log.getTargetUserId(), log.getScore(), BigDecimal::add);
            userCountMap.merge(log.getTargetUserId(), 1L, Long::sum);
        }

        Set<Long> userIds = userScoreMap.keySet();
        Map<Long, String> userNameMap = getUserNameMap(userIds);

        List<RuleStatisticsVO.RankingItem> ranking = userScoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(rankLimit)
                .map(entry -> {
                    RuleStatisticsVO.RankingItem item = new RuleStatisticsVO.RankingItem();
                    item.setUserId(String.valueOf(entry.getKey()));
                    item.setDisplayName(userNameMap.getOrDefault(entry.getKey(), ""));
                    item.setTotalScore(entry.getValue());
                    item.setExecutionCount(userCountMap.getOrDefault(entry.getKey(), 0L));
                    return item;
                }).collect(Collectors.toList());
        vo.setRanking(ranking);

        // Trend: group by execution_date
        Map<LocalDate, BigDecimal> dateTotalMap = new java.util.TreeMap<>();
        Map<LocalDate, Long> dateCountMap = new java.util.TreeMap<>();
        for (RuleExecutionLog log : allLogs) {
            dateTotalMap.merge(log.getExecutionDate(), log.getScore(), BigDecimal::add);
            dateCountMap.merge(log.getExecutionDate(), 1L, Long::sum);
        }

        List<RuleStatisticsVO.TrendItem> trend = dateTotalMap.entrySet().stream()
                .map(entry -> {
                    RuleStatisticsVO.TrendItem item = new RuleStatisticsVO.TrendItem();
                    item.setDate(entry.getKey().toString());
                    item.setDailyScore(entry.getValue());
                    item.setDailyCount(dateCountMap.getOrDefault(entry.getKey(), 0L));
                    return item;
                }).collect(Collectors.toList());
        vo.setTrend(trend);

        // Summary by rule: group by rule_id
        Map<Long, BigDecimal> ruleScoreMap = new HashMap<>();
        Map<Long, Long> ruleCountMap = new HashMap<>();
        for (RuleExecutionLog log : allLogs) {
            ruleScoreMap.merge(log.getRuleId(), log.getScore(), BigDecimal::add);
            ruleCountMap.merge(log.getRuleId(), 1L, Long::sum);
        }

        Set<Long> ruleIds = ruleScoreMap.keySet();
        Map<Long, String> ruleNameMap = ruleIds.isEmpty() ? Map.of() :
                ruleMapper.selectBatchIds(ruleIds).stream()
                        .collect(Collectors.toMap(RuleDefinition::getId, RuleDefinition::getName));

        List<RuleStatisticsVO.RuleSummaryItem> summaryByRule = ruleScoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .map(entry -> {
                    RuleStatisticsVO.RuleSummaryItem item = new RuleStatisticsVO.RuleSummaryItem();
                    item.setRuleId(String.valueOf(entry.getKey()));
                    item.setRuleName(ruleNameMap.getOrDefault(entry.getKey(), ""));
                    item.setTotalScore(entry.getValue());
                    item.setExecutionCount(ruleCountMap.getOrDefault(entry.getKey(), 0L));
                    return item;
                }).collect(Collectors.toList());
        vo.setSummaryByRule(summaryByRule);

        return vo;
    }

    /**
     * 获取某个 Issue 关联的规则执行记录
     */
    public List<RuleExecutionLogVO> getIssueRuleLogs(Long issueId) {
        LambdaQueryWrapper<RuleExecutionLog> wrapper = new LambdaQueryWrapper<RuleExecutionLog>()
                .eq(RuleExecutionLog::getIssueId, issueId)
                .orderByDesc(RuleExecutionLog::getExecutedAt);
        List<RuleExecutionLog> logs = logMapper.selectList(wrapper);
        List<RuleExecutionLogVO> voList = ruleConverter.toLogVOList(logs);
        enrichLogVOList(voList, logs);
        return voList;
    }

    /**
     * 获取某个 Issue 的累计罚分
     */
    public BigDecimal getIssueTotalScore(Long issueId) {
        return logMapper.sumScoreByIssue(issueId);
    }

    // ==================== 内部方法 ====================

    private RuleDefinition getRuleOrThrow(Long id) {
        RuleDefinition rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则不存在");
        }
        return rule;
    }

    private void validateRuleDTO(SaveRuleDefinitionDTO dto) {
        if (!Set.of("scheduled", "event", "manual").contains(dto.getTriggerType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的触发类型");
        }
        if (!Set.of("linear_daily", "fixed", "cumulative_increment", "custom").contains(dto.getScoreFormula())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的计算公式类型");
        }
        if (dto.getTargetField() != null && !Set.of("assignee", "reporter", "created_by").contains(dto.getTargetField())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的作用对象");
        }
        if ("scheduled".equals(dto.getTriggerType()) && (dto.getScheduleCron() == null || dto.getScheduleCron().isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "定时规则必须配置 cron 表达式");
        }
    }

    private void applyDTO(RuleDefinition rule, SaveRuleDefinitionDTO dto) {
        rule.setName(dto.getName());
        rule.setDescription(dto.getDescription());
        rule.setProjectId(dto.getProjectId());
        rule.setTriggerType(dto.getTriggerType());
        rule.setTriggerConfig(dto.getTriggerConfig());
        rule.setScoreFormula(dto.getScoreFormula());
        rule.setScoreConfig(dto.getScoreConfig());
        rule.setTargetField(dto.getTargetField() != null ? dto.getTargetField() : "assignee");
        rule.setScheduleCron(dto.getScheduleCron());
        rule.setDedupStrategy(dto.getDedupStrategy() != null ? dto.getDedupStrategy() : "daily");
        rule.setActions(dto.getActions());
    }

    /**
     * 查找符合规则触发条件的工单
     */
    @SuppressWarnings("unchecked")
    private List<Issue> findMatchingIssues(RuleDefinition rule) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                    rule.getTriggerConfig() != null ? rule.getTriggerConfig() : "{}", Map.class);
            Map<String, Object> issueFilter = (Map<String, Object>) config.getOrDefault("issueFilter", Map.of());

            LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<>();
            wrapper.isNull(Issue::getDeletedAt);

            // 状态过滤：排除已关闭状态
            Object statusesNotInCategory = issueFilter.get("statuses_not_in_category");
            if (statusesNotInCategory instanceof List<?> categories) {
                if (categories.contains("closed")) {
                    Set<Long> closedStatusIds = getClosedStatusIds();
                    if (!closedStatusIds.isEmpty()) {
                        wrapper.notIn(Issue::getStatusId, closedStatusIds);
                    }
                }
            }

            // due_date 过滤
            Object hasDueDate = issueFilter.get("has_due_date");
            if (Boolean.TRUE.equals(hasDueDate)) {
                wrapper.isNotNull(Issue::getDueDate);
            }

            Object dueDateBefore = issueFilter.get("due_date_before");
            if ("now".equals(dueDateBefore)) {
                wrapper.lt(Issue::getDueDate, LocalDate.now());
            }

            // 项目过滤
            if (rule.getProjectId() != null) {
                wrapper.eq(Issue::getProjectId, rule.getProjectId());
            }
            Object projects = issueFilter.get("projects");
            if (projects instanceof List<?> projectKeys) {
                // 暂不实现 project key 过滤，后续扩展
            }

            // 要求有负责人（target_field = assignee 时）
            if ("assignee".equals(rule.getTargetField())) {
                wrapper.isNotNull(Issue::getAssigneeId);
            }

            return issueMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[RuleExec] 解析触发条件失败: rule={}", rule.getId(), e);
            return List.of();
        }
    }

    private Long resolveTargetUserId(RuleDefinition rule, Issue issue) {
        return switch (rule.getTargetField()) {
            case "reporter" -> issue.getReporterId();
            case "created_by" -> issue.getCreatedBy();
            default -> issue.getAssigneeId();
        };
    }

    private boolean shouldSkipByDedup(RuleDefinition rule, Issue issue, LocalDate today) {
        if ("no_dedup".equals(rule.getDedupStrategy())) return false;

        if ("once_per_issue".equals(rule.getDedupStrategy())) {
            Long count = logMapper.selectCount(new LambdaQueryWrapper<RuleExecutionLog>()
                    .eq(RuleExecutionLog::getRuleId, rule.getId())
                    .eq(RuleExecutionLog::getIssueId, issue.getId()));
            return count > 0;
        }

        // daily: 依赖唯一索引去重，这里不做前置检查，让 insert 时 DuplicateKeyException 处理
        return false;
    }

    private Set<Long> getClosedStatusIds() {
        return issueStatusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>()
                        .eq(IssueStatus::getIsClosed, true)
                        .select(IssueStatus::getId)
        ).stream().map(IssueStatus::getId).collect(Collectors.toSet());
    }

    // ==================== VO 信息补充 ====================

    private void enrichDefinitionVOList(List<RuleDefinitionVO> voList, List<RuleDefinition> rules) {
        if (voList.isEmpty()) return;

        // 收集创建者 ID
        Set<Long> userIds = rules.stream()
                .map(RuleDefinition::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = getUserNameMap(userIds);

        // 收集项目 ID
        Set<Long> projectIds = rules.stream()
                .map(RuleDefinition::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 暂时不做项目名查询，前端可根据 projectId 匹配

        for (int i = 0; i < voList.size(); i++) {
            RuleDefinitionVO vo = voList.get(i);
            RuleDefinition rule = rules.get(i);
            vo.setCreatedByName(rule.getCreatedBy() != null ? userNameMap.getOrDefault(rule.getCreatedBy(), "") : "");

            // 最近执行时间和执行次数
            LambdaQueryWrapper<RuleExecutionLog> logWrapper = new LambdaQueryWrapper<RuleExecutionLog>()
                    .eq(RuleExecutionLog::getRuleId, rule.getId())
                    .orderByDesc(RuleExecutionLog::getExecutedAt)
                    .last("LIMIT 1");
            RuleExecutionLog latestLog = logMapper.selectOne(logWrapper);
            if (latestLog != null) {
                vo.setLastExecutedAt(latestLog.getExecutedAt());
            }
            vo.setExecutionCount(logMapper.selectCount(
                    new LambdaQueryWrapper<RuleExecutionLog>().eq(RuleExecutionLog::getRuleId, rule.getId())));
        }
    }

    private void enrichDefinitionVO(RuleDefinitionVO vo, RuleDefinition rule) {
        if (rule.getCreatedBy() != null) {
            SysUser user = sysUserMapper.selectById(rule.getCreatedBy());
            if (user != null) vo.setCreatedByName(user.getDisplayName());
        }
    }

    private void enrichLogVOList(List<RuleExecutionLogVO> voList, List<RuleExecutionLog> logs) {
        if (voList.isEmpty()) return;

        // 收集需要查询的 ID
        Set<Long> ruleIds = logs.stream().map(RuleExecutionLog::getRuleId).collect(Collectors.toSet());
        Set<Long> issueIds = logs.stream().map(RuleExecutionLog::getIssueId).collect(Collectors.toSet());
        Set<Long> userIds = logs.stream().map(RuleExecutionLog::getTargetUserId).collect(Collectors.toSet());

        // 批量查询
        Map<Long, String> ruleNameMap = ruleIds.isEmpty() ? Map.of() :
                ruleMapper.selectBatchIds(ruleIds).stream()
                        .collect(Collectors.toMap(RuleDefinition::getId, RuleDefinition::getName));

        Map<Long, Issue> issueMap = issueIds.isEmpty() ? Map.of() :
                issueMapper.selectBatchIds(issueIds).stream()
                        .collect(Collectors.toMap(Issue::getId, i -> i));

        Map<Long, String> userNameMap = getUserNameMap(userIds);

        // 填充
        for (int i = 0; i < voList.size(); i++) {
            RuleExecutionLogVO vo = voList.get(i);
            RuleExecutionLog logEntry = logs.get(i);

            vo.setRuleName(ruleNameMap.getOrDefault(logEntry.getRuleId(), ""));
            vo.setTargetUserName(userNameMap.getOrDefault(logEntry.getTargetUserId(), ""));

            Issue issue = issueMap.get(logEntry.getIssueId());
            if (issue != null) {
                vo.setIssueKey(issue.getIssueKey());
                vo.setIssueTitle(issue.getTitle());
            }
        }
    }

    private Map<Long, String> getUserNameMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return new HashMap<>();
        return sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
    }
}


