package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.service.StatusCacheHelper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.dto.CreateIssueLinkDTO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueLink;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueLinkMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.vo.IssueLinkVO;
import com.trackflow.issue.vo.IssueStatusVO;
import com.trackflow.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueLinkService {

    private final IssueLinkMapper linkMapper;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueConverter issueConverter;
    private final ProjectService projectService;
    private final StatusCacheHelper statusCacheHelper;

    /**
     * 获取 Issue 的所有关联（包括作为 source 和 target 的）
     */
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

        List<IssueLinkVO> result = new ArrayList<>();

        // source 关联：展示 target issue 信息
        for (IssueLink link : asSource) {
            IssueLinkVO vo = buildLinkVO(link.getId(), link.getLinkType(), link.getTargetIssueId());
            if (vo != null) result.add(vo);
        }

        // target 关联：展示 source issue 信息，link type 取反义
        for (IssueLink link : asTarget) {
            String reverseType = getReverseLinkType(link.getLinkType());
            IssueLinkVO vo = buildLinkVO(link.getId(), reverseType, link.getSourceIssueId());
            if (vo != null) result.add(vo);
        }

        return result;
    }

    /**
     * 创建关联
     */
    @Transactional
    public void createIssueLink(Long issueId, CreateIssueLinkDTO dto) {
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
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标 Issue 不存在");
        }

        // 检查是否已存在相同关联
        Long exists = linkMapper.selectCount(
                new LambdaQueryWrapper<IssueLink>()
                        .eq(IssueLink::getSourceIssueId, issueId)
                        .eq(IssueLink::getTargetIssueId, targetIssueId)
                        .eq(IssueLink::getLinkType, linkType)
        );
        if (exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关联已存在");
        }

        IssueLink link = new IssueLink();
        link.setSourceIssueId(issueId);
        link.setTargetIssueId(targetIssueId);
        link.setLinkType(linkType);
        link.setCreatedBy(SecurityUtils.getCurrentUserId());
        link.setCreatedAt(LocalDateTime.now());
        linkMapper.insert(link);
    }

    /**
     * 删除关联
     */
    @Transactional
    public void deleteIssueLink(Long linkId) {
        IssueLink link = linkMapper.selectById(linkId);
        if (link == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关联不存在");
        }
        // 归档项目不允许删除关联
        Issue issue = issueMapper.selectById(link.getSourceIssueId());
        if (issue != null) {
            projectService.assertProjectActive(issue.getProjectId());
        }
        linkMapper.deleteById(linkId);
    }

    /**
     * 获取未解决的阻塞方 issue key 列表。
     * 查找 issue_link 中所有 target=issueId && linkType='blocks' 的记录，
     * 然后筛选 source issue 状态未关闭的。
     *
     * @return 未解决阻塞方的 issueKey 列表（空列表表示无阻塞）
     */
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

    // ========== 私有方法 ==========

    private IssueLinkVO buildLinkVO(Long linkId, String linkType, Long linkedIssueId) {
        Issue issue = issueMapper.selectById(linkedIssueId);
        if (issue == null || issue.getDeletedAt() != null) {
            return null;
        }

        IssueStatus status = statusMapper.selectById(issue.getStatusId());

        IssueLinkVO vo = new IssueLinkVO();
        vo.setId(String.valueOf(linkId));
        vo.setLinkType(linkType);
        vo.setIssueId(String.valueOf(issue.getId()));
        vo.setIssueKey(issue.getIssueKey());
        vo.setIssueTitle(issue.getTitle());
        if (status != null) {
            vo.setIssueStatus(issueConverter.toStatusVO(status));
        }
        return vo;
    }

    /**
     * 获取反向关联类型
     */
    private String getReverseLinkType(String linkType) {
        return switch (linkType) {
            case "parent_of" -> "child_of";
            case "child_of" -> "parent_of";
            case "blocks" -> "blocked_by";
            case "blocked_by" -> "blocks";
            case "duplicates" -> "duplicated_by";
            case "duplicated_by" -> "duplicates";
            default -> linkType; // relates_to 是对称的
        };
    }
}
