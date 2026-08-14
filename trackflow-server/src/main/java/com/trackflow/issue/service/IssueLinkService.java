package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.dto.CreateIssueLinkDTO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueLink;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueLinkMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.vo.IssueLinkVO;
import com.trackflow.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueLinkService {

    private final IssueLinkMapper linkMapper;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueActivityMapper activityMapper;
    private final IssueConverter issueConverter;
    private final ProjectService projectService;
    private final StatusCacheHelper statusCacheHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final IssueLinkTypeService linkTypeService;
    private final PriorityFieldService priorityFieldService;

    /**
     * 获取 Issue 的所有关联（包括作为 source 和 target 的）。
     * 使用批量查询避免 N+1 性能问题。
     */
    @Transactional(readOnly = true)
    public List<IssueLinkVO> listIssueLinks(Long issueId) {
        // 查询作为 source 的链接
        List<IssueLink> asSource = linkMapper.selectList(
                new LambdaQueryWrapper<IssueLink>()
                        .eq(IssueLink::getSourceIssueId, issueId)
        );

        // 查询作为 target 的链接
        List<IssueLink> asTarget = linkMapper.selectList(
                new LambdaQueryWrapper<IssueLink>()
                        .eq(IssueLink::getTargetIssueId, issueId)
        );

        if (asSource.isEmpty() && asTarget.isEmpty()) {
            return List.of();
        }

        // 收集所有关联的 Issue ID，一次性批量查询
        Set<Long> linkedIssueIds = new HashSet<>();
        for (IssueLink link : asSource) {
            linkedIssueIds.add(link.getTargetIssueId());
        }
        for (IssueLink link : asTarget) {
            linkedIssueIds.add(link.getSourceIssueId());
        }

        // 批量查询关联 Issue（1 次 DB 查询）
        Map<Long, Issue> issueMap = issueMapper.selectBatchIds(linkedIssueIds).stream()
                .collect(Collectors.toMap(Issue::getId, issue -> issue, (a, b) -> a));

        // 批量获取所有状态（status 表数据极少，全量缓存）
        Map<Long, IssueStatus> statusMap = statusMapper.selectList(null).stream()
                .collect(Collectors.toMap(IssueStatus::getId, s -> s, (a, b) -> a));

        // 构建优先级选项 Map（value → option），用于填充颜色和序号
        Map<String, CustomFieldOption> priorityOptionMap = buildPriorityOptionMap();

        List<IssueLinkVO> result = new ArrayList<>();

        // source 关联：展示 target issue 信息
        for (IssueLink link : asSource) {
            IssueLinkVO vo = buildLinkVOFromMaps(link.getId(), link.getLinkType(),
                    link.getTargetIssueId(), issueMap, statusMap, priorityOptionMap);
            if (vo != null) result.add(vo);
        }

        // target 关联：展示 source issue 信息，link type 取反义
        for (IssueLink link : asTarget) {
            String reverseType = getReverseLinkType(link.getLinkType());
            IssueLinkVO vo = buildLinkVOFromMaps(link.getId(), reverseType,
                    link.getSourceIssueId(), issueMap, statusMap, priorityOptionMap);
            if (vo != null) result.add(vo);
        }

        return result;
    }

    /**
     * 创建关联
     */
    @Transactional(rollbackFor = Exception.class)
    public void createIssueLink(Long issueId, CreateIssueLinkDTO dto) {
        // 校验 linkType 是否为合法类型（从数据库动态加载验证）
        if (!linkTypeService.isValidLinkType(dto.getLinkType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "非法的关联类型: " + dto.getLinkType());
        }

        // 归档项目不允许创建关联
        Issue sourceIssue = issueMapper.selectById(issueId);
        if (sourceIssue != null) {
            projectService.assertProjectActive(sourceIssue.getProjectId());
        }

        Long targetIssueId = dto.getTargetIssueId();
        String linkType = dto.getLinkType();

        // 不能关联自己
        if (issueId.equals(targetIssueId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能关联自身");
        }

        // 验证目标 Issue 存在
        Issue targetIssue = issueMapper.selectById(targetIssueId);
        if (targetIssue == null || targetIssue.getDeletedAt() != null) {
            throw BusinessException.notFound("目标 Issue 不存在");
        }

        // 检查是否已存在相同关联（同方向同类型）
        Long exists = linkMapper.selectCount(
                new LambdaQueryWrapper<IssueLink>()
                        .eq(IssueLink::getSourceIssueId, issueId)
                        .eq(IssueLink::getTargetIssueId, targetIssueId)
                        .eq(IssueLink::getLinkType, linkType)
        );
        if (exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关联已存在");
        }

        // 检查反向关系是否已存在（防止 A blocks B + B blocks A 互相阻塞）
        validateNoReverseRelation(issueId, targetIssueId, linkType);

        // 检查循环依赖（仅对有向关联类型：blocks, parent_of）
        validateNoCircularDependency(issueId, targetIssueId, linkType);

        IssueLink link = new IssueLink();
        link.setSourceIssueId(issueId);
        link.setTargetIssueId(targetIssueId);
        link.setLinkType(linkType);
        link.setCreatedBy(SecurityUtils.getCurrentUserId());
        link.setCreatedAt(LocalDateTime.now());
        linkMapper.insert(link);

        // 记录双向活动日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String reverseType = getReverseLinkType(linkType);

        Long sourceActivityId = recordActivity(issueId, currentUserId, "link_added", "link",
                null, linkType + " " + targetIssue.getIssueKey());
        Long targetActivityId = recordActivity(targetIssueId, currentUserId, "link_added", "link",
                null, reverseType + " " + sourceIssue.getIssueKey());

        // 发布通知事件（双向：source 和 target 的负责人/关注者都应收到通知）
        eventPublisher.publishEvent(new IssueNotificationEvent.LinkChanged(
                sourceIssue, targetIssue.getIssueKey(), linkType, true, currentUserId, sourceActivityId));
        eventPublisher.publishEvent(new IssueNotificationEvent.LinkChanged(
                targetIssue, sourceIssue.getIssueKey(), reverseType, true, currentUserId, targetActivityId));

        // 触发 link_added 工作流规则（双向：source 和 target 都触发）
        eventPublisher.publishEvent(new WorkflowRuleEvent.LinkAdded(
                issueId, sourceIssue.getProjectId(), linkType, targetIssueId));
        eventPublisher.publishEvent(new WorkflowRuleEvent.LinkAdded(
                targetIssueId, targetIssue.getProjectId(), reverseType, issueId));
    }

    /**
     * 删除关联
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteIssueLink(Long linkId) {
        IssueLink link = linkMapper.selectById(linkId);
        if (link == null) {
            throw BusinessException.notFound("关联不存在");
        }
        // 归档项目不允许删除关联
        Issue sourceIssue = issueMapper.selectById(link.getSourceIssueId());
        if (sourceIssue != null) {
            projectService.assertProjectActive(sourceIssue.getProjectId());
        }

        Issue targetIssue = issueMapper.selectById(link.getTargetIssueId());
        linkMapper.deleteById(linkId);

        // 记录双向活动日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String linkType = link.getLinkType();
        String reverseType = getReverseLinkType(linkType);

        String targetKey = targetIssue != null ? targetIssue.getIssueKey() : "unknown";
        String sourceKey = sourceIssue != null ? sourceIssue.getIssueKey() : "unknown";

        Long srcActId = recordActivity(link.getSourceIssueId(), currentUserId, "link_removed", "link",
                linkType + " " + targetKey, null);
        Long tgtActId = recordActivity(link.getTargetIssueId(), currentUserId, "link_removed", "link",
                reverseType + " " + sourceKey, null);

        // 发布通知事件（双向）
        if (sourceIssue != null && targetIssue != null) {
            eventPublisher.publishEvent(new IssueNotificationEvent.LinkChanged(
                    sourceIssue, targetKey, linkType, false, currentUserId, srcActId));
            eventPublisher.publishEvent(new IssueNotificationEvent.LinkChanged(
                    targetIssue, sourceKey, reverseType, false, currentUserId, tgtActId));

            // 触发 link_removed 工作流规则（双向）
            eventPublisher.publishEvent(new WorkflowRuleEvent.LinkRemoved(
                    link.getSourceIssueId(), sourceIssue.getProjectId(), linkType, link.getTargetIssueId()));
            eventPublisher.publishEvent(new WorkflowRuleEvent.LinkRemoved(
                    link.getTargetIssueId(), targetIssue.getProjectId(), reverseType, link.getSourceIssueId()));
        }
    }

    /**
     * 获取未解决的阻塞方 issue key 列表。
     * 查找 issue_link 中所有 target=issueId && linkType='blocks' 的记录，
     * 然后筛选 source issue 状态未关闭的。
     *
     * @return 未解决阻塞方的 issueKey 列表（空列表表示无阻塞）
     */
    @Transactional(readOnly = true)
    public List<String> getUnresolvedBlockerKeys(Long issueId) {
        // 查询所有"X blocks issueId"的链接
        List<IssueLink> blockingLinks = linkMapper.selectList(
                new LambdaQueryWrapper<IssueLink>()
                        .eq(IssueLink::getTargetIssueId, issueId)
                        .eq(IssueLink::getLinkType, "blocks")
        );

        if (blockingLinks.isEmpty()) {
            return List.of();
        }

        // 使用缓存获取关闭状态 ID
        Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();

        // 批量查询所有 blocker issue（避免 N+1）
        List<Long> sourceIds = blockingLinks.stream()
                .map(IssueLink::getSourceIssueId)
                .toList();
        List<Issue> blockers = issueMapper.selectBatchIds(sourceIds);

        // 筛选出未关闭且未删除的
        return blockers.stream()
                .filter(b -> b.getDeletedAt() == null && !closedStatusIds.contains(b.getStatusId()))
                .map(Issue::getIssueKey)
                .toList();
    }

    // ========== 校验方法 ==========

    /**
     * 检查反向关系是否已存在。
     * 在新模型中，issue_link 表只存储正向类型（outward 方向），
     * 所以只需检查 target→source 的同类型是否已存在。
     */
    private void validateNoReverseRelation(Long sourceId, Long targetId, String linkType) {
        // 检查反方向是否已存在同类型
        Long reverseExists = linkMapper.selectCount(
                new LambdaQueryWrapper<IssueLink>()
                        .eq(IssueLink::getSourceIssueId, targetId)
                        .eq(IssueLink::getTargetIssueId, sourceId)
                        .eq(IssueLink::getLinkType, linkType)
        );
        if (reverseExists > 0) {
            if (linkTypeService.isUndirected(linkType)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "关联已存在（对称关联）");
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "反向关联已存在，不能互相关联");
            }
        }
    }

    /**
     * 循环依赖检测（BFS）。
     * 仅对有向关联类型（blocks, parent_of）执行。
     * 从 targetId 出发沿同类型链遍历，如果能到达 sourceId 则说明形成循环。
     */
    private void validateNoCircularDependency(Long sourceId, Long targetId, String linkType) {
        if (!linkTypeService.isDirected(linkType)) {
            return;
        }

        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(targetId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(sourceId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "创建此关联会形成循环依赖");
            }
            if (!visited.add(current)) {
                continue;
            }
            // 查询从 current 出发的同类型链接
            List<IssueLink> outgoing = linkMapper.selectList(
                    new LambdaQueryWrapper<IssueLink>()
                            .eq(IssueLink::getSourceIssueId, current)
                            .eq(IssueLink::getLinkType, linkType)
            );
            for (IssueLink l : outgoing) {
                if (!visited.contains(l.getTargetIssueId())) {
                    queue.add(l.getTargetIssueId());
                }
            }
        }
    }

    // ========== 私有方法 ==========

    /**
     * 从预加载的 Map 中构建 IssueLinkVO（批量模式，避免 N+1）
     */
    private IssueLinkVO buildLinkVOFromMaps(Long linkId, String linkType, Long linkedIssueId,
                                            Map<Long, Issue> issueMap, Map<Long, IssueStatus> statusMap,
                                            Map<String, CustomFieldOption> priorityOptionMap) {
        Issue issue = issueMap.get(linkedIssueId);
        if (issue == null || issue.getDeletedAt() != null) {
            return null;
        }

        IssueLinkVO vo = new IssueLinkVO();
        vo.setId(String.valueOf(linkId));
        vo.setLinkType(linkType);
        vo.setIssueId(String.valueOf(issue.getId()));
        vo.setIssueKey(issue.getIssueKey());
        vo.setIssueTitle(issue.getTitle());
        if (issue.getStatusId() != null) {
            IssueStatus status = statusMap.get(issue.getStatusId());
            if (status != null) {
                vo.setIssueStatus(issueConverter.toStatusVO(status));
            }
        }
        // 填充优先级信息
        if (issue.getPriority() != null && !issue.getPriority().isBlank()) {
            vo.setPriority(issue.getPriority());
            CustomFieldOption opt = priorityOptionMap.get(issue.getPriority());
            if (opt != null) {
                vo.setPriorityColor(opt.getColor());
                // position 从 0 开始，展示数字从 1 开始
                vo.setPriorityOrder(opt.getPosition() != null ? opt.getPosition() + 1 : null);
            }
        }
        return vo;
    }

    /**
     * 构建优先级选项查找 Map（value → CustomFieldOption）
     */
    private Map<String, CustomFieldOption> buildPriorityOptionMap() {
        try {
            List<CustomFieldOption> options = priorityFieldService.getGlobalPriorityOptions();
            return options.stream()
                    .collect(Collectors.toMap(CustomFieldOption::getValue, opt -> opt, (a, b) -> a));
        } catch (Exception e) {
            log.warn("加载全局优先级选项失败，关联工单将不显示优先级信息", e);
            return Map.of();
        }
    }

    /**
     * 获取反向关联类型展示名（从 issue_link_type 表动态查询）
     */
    String getReverseLinkType(String linkType) {
        return linkTypeService.getInwardName(linkType);
    }

    /**
     * 记录活动日志
     *
     * @return 新创建的活动记录 ID
     */
    private Long recordActivity(Long issueId, Long userId, String action,
                                String fieldName, String oldValue, String newValue) {
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issueId);
        activity.setUserId(userId);
        activity.setAction(action);
        activity.setFieldName(fieldName);
        activity.setOldValue(oldValue);
        activity.setNewValue(newValue);
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
        return activity.getId();
    }
}
