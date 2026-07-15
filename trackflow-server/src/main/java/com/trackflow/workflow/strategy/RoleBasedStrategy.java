package com.trackflow.workflow.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.service.StatusCacheHelper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.workflow.dto.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 基于角色的分配策略 —— 从指定角色的项目成员中选择一位分配。
 * 支持 round_robin（轮转）、least_loaded（最少负载）和 weighted_round_robin（加权轮转）三种模式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleBasedStrategy implements AssignmentStrategy {

    private final ProjectMemberMapper projectMemberMapper;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final StatusCacheHelper statusCacheHelper;

    /** 轮转计数器：key = "projectId:roleId"
     * TODO: 多实例部署时应改为 Redis 原子计数器（INCR），确保分布式环境下计数一致
     */
    private final ConcurrentHashMap<String, AtomicLong> roundRobinCounters = new ConcurrentHashMap<>();

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
     * 轮转模式：使用原子计数器在候选人间轮转
     */
    private Long resolveRoundRobin(List<Long> candidates, Long projectId, Long roleId) {
        String key = projectId + ":" + roleId;
        AtomicLong counter = roundRobinCounters.computeIfAbsent(key, k -> new AtomicLong(0));
        int index = (int) (counter.getAndIncrement() % candidates.size());
        // 处理 long 溢出后取模可能为负数的情况
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

        // 使用与 round_robin 相同的计数器机制，但基于加权列表
        String key = projectId + ":" + roleId + ":weighted";
        AtomicLong counter = roundRobinCounters.computeIfAbsent(key, k -> new AtomicLong(0));
        int index = (int) (counter.getAndIncrement() % weightedList.size());
        if (index < 0) {
            index += weightedList.size();
        }
        return weightedList.get(index);
    }
}
