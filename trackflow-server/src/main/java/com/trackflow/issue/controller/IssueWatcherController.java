package com.trackflow.issue.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.service.IssueWatcherService;
import com.trackflow.issue.vo.IssueWatcherStatusVO;
import com.trackflow.issue.vo.IssueWatcherVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单关注（Watcher）REST API。
 * <p>
 * 提供关注/取消关注/查询关注者/查询关注状态接口。
 * 所有接口要求已认证用户。
 */
@RestController
@RequestMapping("/api/v1/issues/{issueId}/watchers")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class IssueWatcherController {

    private final IssueWatcherService watcherService;

    /**
     * 关注工单
     */
    @PostMapping
    public R<IssueWatcherStatusVO> watch(@PathVariable("issueId") Long issueId) {
        Long userId = SecurityUtils.getCurrentUserId();
        watcherService.watch(issueId, userId);
        return R.ok(buildStatusVO(issueId, userId));
    }

    /**
     * 取消关注工单
     */
    @DeleteMapping
    public R<IssueWatcherStatusVO> unwatch(@PathVariable("issueId") Long issueId) {
        Long userId = SecurityUtils.getCurrentUserId();
        watcherService.unwatch(issueId, userId);
        return R.ok(buildStatusVO(issueId, userId));
    }

    /**
     * 查询当前用户是否已关注工单 + 关注者数量
     */
    @GetMapping("/status")
    public R<IssueWatcherStatusVO> getStatus(@PathVariable("issueId") Long issueId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(buildStatusVO(issueId, userId));
    }

    /**
     * 查询工单的关注者列表
     */
    @GetMapping
    public R<List<IssueWatcherVO>> listWatchers(@PathVariable("issueId") Long issueId) {
        return R.ok(watcherService.listWatchersWithUser(issueId));
    }

    private IssueWatcherStatusVO buildStatusVO(Long issueId, Long userId) {
        IssueWatcherStatusVO vo = new IssueWatcherStatusVO();
        vo.setWatching(watcherService.isWatching(issueId, userId));
        vo.setWatcherCount(watcherService.getWatcherCount(issueId));
        return vo;
    }
}
