package com.trackflow.issue.controller;

import com.trackflow.common.model.R;
import com.trackflow.issue.service.IssueVoteService;
import com.trackflow.issue.vo.IssueVoteStatusVO;
import com.trackflow.issue.vo.IssueVoterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单投票 Controller - 提供投票/取消投票/查询状态的 REST API
 * <p>
 * API 设计参照 YouTrack：
 * - POST   /api/v1/issues/{issueId}/voters       投票
 * - DELETE /api/v1/issues/{issueId}/voters       取消投票
 * - GET    /api/v1/issues/{issueId}/voters/status 获取投票状态
 * - GET    /api/v1/issues/{issueId}/voters       获取投票者列表
 *
 * @author TrackFlow
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/issues/{issueId}/voters")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class IssueVoteController {

    private final IssueVoteService voteService;

    /**
     * 对工单投票
     */
    @PostMapping
    public R<IssueVoteStatusVO> vote(@PathVariable("issueId") Long issueId) {
        IssueVoteStatusVO status = voteService.vote(issueId);
        return R.ok(status);
    }

    /**
     * 取消对工单的投票
     */
    @DeleteMapping
    public R<IssueVoteStatusVO> unvote(@PathVariable("issueId") Long issueId) {
        IssueVoteStatusVO status = voteService.unvote(issueId);
        return R.ok(status);
    }

    /**
     * 获取当前用户的投票状态
     */
    @GetMapping("/status")
    public R<IssueVoteStatusVO> getStatus(@PathVariable("issueId") Long issueId) {
        IssueVoteStatusVO status = voteService.getVoteStatus(issueId);
        return R.ok(status);
    }

    /**
     * 获取工单的投票者列表
     */
    @GetMapping
    public R<List<IssueVoterVO>> listVoters(@PathVariable("issueId") Long issueId) {
        List<IssueVoterVO> voters = voteService.listVoters(issueId);
        return R.ok(voters);
    }
}
