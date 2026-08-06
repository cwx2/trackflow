package com.trackflow.issue.controller;

import com.trackflow.common.model.R;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.service.IssueService;
import com.trackflow.issue.vo.SimilarIssueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 工单搜索相关 API — 独立 Controller 避免与 IssueController 的 /{id} 路径冲突。
 */
@RestController
@RequestMapping("/api/v1/issue-search")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class IssueSearchController {

    private final IssueService issueService;

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
        return R.ok(issueService.buildSimilarIssueVOs(issues));
    }
}
