package com.trackflow.workflow.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.workflow.dto.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 基于角色的分配策略 —— 从指定角色的项目成员中选择一位分配。
 * 支持 round_robin（轮转）、least_loaded（最少负载）和 weighted_round_robin（加权轮转）三种模式。
 *
 * 轮转计数器持久化到 Redis（INCR 原子递增），确保：
 * 1. 服务重启后计数不丢失
 * 2. 多实例部署时全局一致
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleBasedStrategy implements AssignmentStrategy {

    private static final String REDIS_KEY_PREFIX = "workflow:rr:";
    private static final Duration COUNTER_TTL = Duration.ofDays(30);

    /**
     * 特殊返回值：表示"保留现有负责人"。
     * 区别于 null（分配失败），用于向 TransitionActionEngine 传递"跳过分配，保留现有"的语义。
     */
    public static final Long KEEP_EXISTING_ASSIGNEE = -1L;

    private final ProjectMemberMapper projectMemberMapper;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final StatusCacheHelper statusCacheHelper;
    private final StringRedisTemplate redisTemplate;

    /** 降级用内存计数器：仅在 Redis 不可用时启用 */
    private final ConcurrentHashMap<String, AtomicLong> fallbackCounters = new ConcurrentHashMap<>();

    @Override
    public String getKey() {
        return "role_based";
    }

    @Override
    public Long resolve(Issue issue, ActionConfig config, Long projectId) {
        Long roleId = config.getRoleId();
        if (roleId == null) {
            log.warn("[RoleBasedStrategy] config.roleId is null, issue={}", issue.getId());
            return null;
        }

        // 查询指定角色的项目成员
        List<Long> candidates = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getRoleId, roleId)
                        .select(ProjectMember::getUserId)
        ).stream().map(ProjectMember::getUserId).collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.warn("[RoleBasedStrategy] 项目 {} 中角色 {} 无成员，跳过分配", projectId, roleId);
            return null;
        }

        // 检查是否保留现有负责人（YouTrack 默认行为）
        // 除非显式设置 force_reassign = true，否则如果当前负责人已属于目标角色，保留不变
        Long currentAssigneeId = issue.getAssigneeId();
        if (currentAssigneeId != null && !Boolean.TRUE.equals(config.getForceReassign())) {
            if (candidates.contains(currentAssigneeId)) {
                log.info("[RoleBasedStrategy] 当前负责人 {} 已属于目标角色 {}，保留现有分配（issue={}）",
                        currentAssigneeId, roleId, issue.getId());
                // 返回特殊标记值 -1L，表示"保留现有负责人"（区别于"分配失败返回 null"）
                // TransitionActionEngine 会根据此标记决定是否记录 activity
                return KEEP_EXISTING_ASSIGNEE;
            }
        }

        // 排除报告人（默认行为，除非 exclude_reporter 显式设为 false）
        candidates = excludeReporterIfNeeded(candidates, issue, config);
        if (candidates.isEmpty()) {
            log.info("[RoleBasedStrategy] 排除报告人后候选池为空（报告人 {} 是唯一候选人），跳过分配",
                    issue.getReporterId());
            return null;
        }

        String mode = config.getMode();
        if ("round_robin".equals(mode)) {
            return resolveRoundRobin(candidates, projectId, roleId);
        } else if ("least_loaded".equals(mode)) {
            return resolveLeastLoaded(candidates, projectId);
        } else if ("weighted_round_robin".equals(mode)) {
            return resolveWeightedRoundRobin(candidates, config, projectId, roleId);
        } else {
            log.warn("[RoleBasedStrategy] 未知模式 '{}', issue={}", mode, issue.getId());
            return null;
        }
    }

    /**
     * 根据配置排除报告人。
     * <p>
     * 默认行为（excludeReporter == null 或 true）：从候选列表中移除报告人。
     * 如果显式配置 exclude_reporter = false，则保留报告人在候选池中。
     * <p>
     * 参考 YouTrack Workflow 的 excludeReporter 选项。
     *
     * @param candidates 原始候选人列表
     * @param issue      当前 Issue（用于获取 reporterId）
     * @param config     动作配置
     * @return 排除后的候选人列表（可能为空）
     */
    private List<Long> excludeReporterIfNeeded(List<Long> candidates, Issue issue, ActionConfig config) {
        // 显式设为 false 时不排除
        if (Boolean.FALSE.equals(config.getExcludeReporter())) {
            return candidates;
        }

        Long reporterId = issue.getReporterId();
        if (reporterId == null) {
            return candidates;
        }

        // 报告人不在候选池中（如产品经理），无需排除
        if (!candidates.contains(reporterId)) {
            return candidates;
        }

        // 排除报告人
        List<Long> filtered = candidates.stream()
                .filter(id -> !id.equals(reporterId))
                .collect(Collectors.toList());

        if (filtered.size() < candidates.size()) {
            log.debug("[RoleBasedStrategy] 已从候选池中排除报告人 {}，剩余 {} 人",
                    reporterId, filtered.size());
        }

        return filtered;
    }

    /**
     * 轮转模式：使用 Redis INCR 原子计数器在候选人间轮转。
     * Redis 不可用时降级为 JVM 内存计数器。
     */
    private Long resolveRoundRobin(List<Long> candidates, Long projectId, Long roleId) {
        String redisKey = REDIS_KEY_PREFIX + projectId + ":" + roleId;
        long counter = incrementCounter(redisKey);
        int index = (int) (counter % candidates.size());
        if (index < 0) {
            index += candidates.size();
        }
        return candidates.get(index);
    }

    /**
     * 最少负载模式：选择打开 Issue 最少的候选人（限定当前项目范围）
     */
    private Long resolveLeastLoaded(List<Long> candidates, Long projectId) {
        // 使用缓存获取所有"未关闭"状态 ID
        Set<Long> openStatusIds = statusCacheHelper.getOpenStatusIds();

        if (openStatusIds.isEmpty()) {
            // 没有 open 状态定义，退化为选第一个
            return candidates.get(0);
        }

        // 统计每个候选人在当前项目中的打开 Issue 数量
        Map<Long, Long> loadMap = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .in(Issue::getAssigneeId, candidates)
                        .eq(Issue::getProjectId, projectId)
                        .isNull(Issue::getDeletedAt)
                        .in(Issue::getStatusId, openStatusIds)
                        .select(Issue::getAssigneeId)
        ).stream().collect(Collectors.groupingBy(Issue::getAssigneeId, Collectors.counting()));

        // 选择负载最少的候选人（没有记录的候选人负载为 0）
        Long minLoadUser = null;
        long minLoad = Long.MAX_VALUE;
        for (Long candidate : candidates) {
            long load = loadMap.getOrDefault(candidate, 0L);
            if (load < minLoad) {
                minLoad = load;
                minLoadUser = candidate;
            }
        }

        return minLoadUser;
    }

    /**
     * 加权轮转模式：按权重比例分配。
     * 权重越高的成员被分配的频率越高。
     * 例如 weights = {A: 3, B: 1}，则每 4 次分配中 A 分到 3 次、B 分到 1 次。
     * 未在 weights 中配置的候选人默认权重为 1。
     */
    private Long resolveWeightedRoundRobin(List<Long> candidates, ActionConfig config,
                                            Long projectId, Long roleId) {
        Map<String, Integer> weights = config.getWeights();

        // 构建加权候选人列表（按权重展开）
        java.util.List<Long> weightedList = new java.util.ArrayList<>();
        for (Long candidate : candidates) {
            int weight = 1; // 默认权重
            if (weights != null && weights.containsKey(String.valueOf(candidate))) {
                weight = Math.max(1, weights.get(String.valueOf(candidate)));
            }
            for (int i = 0; i < weight; i++) {
                weightedList.add(candidate);
            }
        }

        if (weightedList.isEmpty()) {
            return candidates.get(0);
        }

        // 使用 Redis 计数器，基于加权列表
        String redisKey = REDIS_KEY_PREFIX + projectId + ":" + roleId + ":weighted";
        long counter = incrementCounter(redisKey);
        int index = (int) (counter % weightedList.size());
        if (index < 0) {
            index += weightedList.size();
        }
        return weightedList.get(index);
    }

    /**
     * 获取递增计数值。优先使用 Redis INCR（原子、持久化、多实例一致），
     * Redis 不可用时降级为 JVM 内存 AtomicLong。
     *
     * @param redisKey Redis key（如 "workflow:rr:1:3"）
     * @return 递增后的值（从 1 开始）
     */
    private long incrementCounter(String redisKey) {
        try {
            Long value = redisTemplate.opsForValue().increment(redisKey);
            if (value != null) {
                // 首次创建时设置 TTL，避免废弃项目的 key 永久占用
                if (value == 1L) {
                    redisTemplate.expire(redisKey, COUNTER_TTL);
                }
                // 每 1000 次刷新 TTL，防止活跃 key 过期
                else if (value % 1000 == 0) {
                    redisTemplate.expire(redisKey, COUNTER_TTL);
                }
                return value;
            }
        } catch (Exception e) {
            log.warn("[RoleBasedStrategy] Redis 不可用，降级为内存计数器: {}", e.getMessage());
        }
        // 降级：使用内存计数器
        AtomicLong fallback = fallbackCounters.computeIfAbsent(redisKey, k -> new AtomicLong(0));
        return fallback.incrementAndGet();
    }
}
