package com.trackflow.issue.controller;

import com.trackflow.common.model.R;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.service.IssueService;
import com.trackflow.issue.vo.SimilarIssueVO;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工单搜索相关 API — 独立 Controller 避免与 IssueController 的 /{id} 路径冲突。
 */
@RestController
@RequestMapping("/api/v1/issue-search")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class IssueSearchController {

    private final IssueService issueService;
    private final IssueStatusMapper statusMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 查找相似工单 — 创建工单时用于重复检测。
     * 根据用户输入的标题关键词搜索已有相似工单，返回轻量结果。
     */
    @GetMapping("/similar")
    public R<List<SimilarIssueVO>> findSimilar(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        if (limit > 10) limit = 10;
        List<Issue> issues = issueService.findSimilarIssues(keyword, projectId, limit);
        return R.ok(buildSimilarVOs(issues));
    }

    private List<SimilarIssueVO> buildSimilarVOs(List<Issue> issues) {
        if (issues.isEmpty()) return List.of();

        Map<Long, IssueStatus> statusMap = statusMapper.selectList(null).stream()
                .collect(Collectors.toMap(IssueStatus::getId, s -> s, (a, b) -> a));

        Set<Long> userIds = issues.stream()
                .map(Issue::getAssigneeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        return issues.stream().map(issue -> {
            SimilarIssueVO vo = new SimilarIssueVO();
            vo.setId(String.valueOf(issue.getId()));
            vo.setIssueKey(issue.getIssueKey());
            vo.setTitle(issue.getTitle());
            if (issue.getStatusId() != null) {
                IssueStatus status = statusMap.get(issue.getStatusId());
                if (status != null) {
                    vo.setStatusName(status.getName());
                    vo.setStatusColor(status.getColor());
                }
            }
            if (issue.getAssigneeId() != null) {
                SysUser user = userMap.get(issue.getAssigneeId());
                if (user != null) {
                    vo.setAssigneeName(user.getDisplayName());
                }
            }
            return vo;
        }).toList();
    }
}
