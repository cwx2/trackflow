package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 祖先工单派生属性刷新服务。
 * <p>
 * 当子工单的关键属性（spent_hours、estimated_hours、status、parentId）变更时，
 * 沿 parent_id 链向上逐层刷新父工单的派生字段：
 * - child_count / child_closed_count（直接子任务统计）
 * - derived_spent_hours（自身 + 所有后代 spent_hours）
 * - derived_estimated_hours（自身 + 所有后代 estimated_hours）
 * <p>
 * 参考 OpenProject: WorkPackages::UpdateAncestorsService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AncestorRefreshService {

    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;

    /** 最大向上传播层级（与环路检测一致） */
    private static final int MAX_DEPTH = 10;

    /**
     * 刷新指定工单的所有祖先的派生属性。
     * 从 startIssueId 的 parent 开始，逐层向上更新直到根节点或达到最大深度。
     *
     * @param startIssueId 触发变更的子工单 ID
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void refreshAncestors(Long startIssueId) {
        if (startIssueId == null) return;

        Issue startIssue = issueMapper.selectOne(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getParentId)
                        .eq(Issue::getId, startIssueId)
                        .isNull(Issue::getDeletedAt)
        );
        if (startIssue == null || startIssue.getParentId() == null || startIssue.getParentId() == 0) {
            return;
        }

        refreshAncestorChain(startIssue.getParentId());
    }

    /**
     * 从指定的 parentId 开始向上刷新整条祖先链。
     * 适用于 parentId 变更场景，需要同时刷新旧父和新父。
     *
     * @param parentId 要开始刷新的父工单 ID
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void refreshAncestorChain(Long parentId) {
        if (parentId == null || parentId == 0) return;

        Set<Long> visited = new HashSet<>();
        Long current = parentId;
        int depth = 0;

        while (current != null && current != 0 && depth < MAX_DEPTH) {
            if (!visited.add(current)) {
                log.warn("Circular parent reference detected at issue ID {}, stopping propagation", current);
                break;
            }

            refreshSingleAncestor(current);
            depth++;

            // 获取下一层父节点
            Issue ancestor = issueMapper.selectOne(
                    new LambdaQueryWrapper<Issue>()
                            .select(Issue::getParentId)
                            .eq(Issue::getId, current)
                            .isNull(Issue::getDeletedAt)
            );
            current = (ancestor != null) ? ancestor.getParentId() : null;
        }

        if (depth >= MAX_DEPTH) {
            log.warn("Ancestor refresh reached max depth ({}) starting from parentId={}", MAX_DEPTH, parentId);
        }
    }

    /**
     * 刷新单个工单的派生属性（基于其直接子工单）。
     */
    private void refreshSingleAncestor(Long issueId) {
        // 查询所有未删除的直接子工单
        List<Issue> children = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId, Issue::getSpentHours, Issue::getEstimatedHours,
                                Issue::getStatusId, Issue::getDerivedSpentHours, Issue::getDerivedEstimatedHours)
                        .eq(Issue::getParentId, issueId)
                        .isNull(Issue::getDeletedAt)
        );

        int childCount = children.size();
        int childClosedCount = 0;
        BigDecimal totalChildSpent = BigDecimal.ZERO;
        BigDecimal totalChildEstimated = BigDecimal.ZERO;

        for (Issue child : children) {
            // 统计关闭状态
            if (isClosedStatus(child.getStatusId())) {
                childClosedCount++;
            }

            // 聚合工时：使用子工单的 derived 值（如果有），否则用自身值
            // 这样支持多层级传播（子工单的 derived 已包含其后代）
            BigDecimal childSpent = child.getDerivedSpentHours() != null
                    ? child.getDerivedSpentHours()
                    : (child.getSpentHours() != null ? child.getSpentHours() : BigDecimal.ZERO);
            BigDecimal childEstimated = child.getDerivedEstimatedHours() != null
                    ? child.getDerivedEstimatedHours()
                    : (child.getEstimatedHours() != null ? child.getEstimatedHours() : BigDecimal.ZERO);

            totalChildSpent = totalChildSpent.add(childSpent);
            totalChildEstimated = totalChildEstimated.add(childEstimated);
        }

        // 获取当前工单自身的 spent_hours 和 estimated_hours
        Issue self = issueMapper.selectOne(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getSpentHours, Issue::getEstimatedHours)
                        .eq(Issue::getId, issueId)
                        .isNull(Issue::getDeletedAt)
        );
        BigDecimal selfSpent = (self != null && self.getSpentHours() != null)
                ? self.getSpentHours() : BigDecimal.ZERO;
        BigDecimal selfEstimated = (self != null && self.getEstimatedHours() != null)
                ? self.getEstimatedHours() : BigDecimal.ZERO;

        BigDecimal derivedSpent = selfSpent.add(totalChildSpent);
        BigDecimal derivedEstimated = selfEstimated.add(totalChildEstimated);

        // 如果没有子工单且自身值为 0，设置为 null（表示没有派生数据）
        BigDecimal finalDerivedSpent = (childCount == 0)
                ? null : derivedSpent;
        BigDecimal finalDerivedEstimated = (childCount == 0)
                ? null : derivedEstimated;

        // 更新
        issueMapper.update(null,
                new LambdaUpdateWrapper<Issue>()
                        .eq(Issue::getId, issueId)
                        .set(Issue::getChildCount, childCount)
                        .set(Issue::getChildClosedCount, childClosedCount)
                        .set(Issue::getDerivedSpentHours, finalDerivedSpent)
                        .set(Issue::getDerivedEstimatedHours, finalDerivedEstimated)
        );

        log.debug("Refreshed ancestor issue {}: children={}/{}, derivedSpent={}, derivedEst={}",
                issueId, childClosedCount, childCount, finalDerivedSpent, finalDerivedEstimated);
    }

    /**
     * 判断状态是否为关闭状态
     */
    private boolean isClosedStatus(Long statusId) {
        if (statusId == null) return false;
        IssueStatus status = issueStatusMapper.selectById(statusId);
        return status != null && Boolean.TRUE.equals(status.getIsClosed());
    }
}
