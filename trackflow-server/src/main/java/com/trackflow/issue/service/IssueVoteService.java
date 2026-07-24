package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueVote;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueVoteMapper;
import com.trackflow.issue.vo.IssueVoteStatusVO;
import com.trackflow.issue.vo.IssueVoterVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单投票服务 - 处理投票/取消投票/查询投票状态等业务逻辑
 * <p>
 * 业务规则（参照 YouTrack）：
 * 1. 工单创建者（reporter）不能投票给自己的工单
 * 2. 投票操作是 toggle 逻辑（已投票则取消，未投票则添加）
 * 3. 投票会自动添加用户为 Watcher
 * 4. 投票数作为冗余字段维护在 issue 表中，用于排序
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueVoteService {

    private final IssueVoteMapper voteMapper;
    private final IssueMapper issueMapper;
    private final IssueWatcherService watcherService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 投票 - 对工单进行投票
     *
     * @param issueId 工单ID
     * @return 投票状态
     * @throws BusinessException 当工单不存在或投自己的工单时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueVoteStatusVO vote(Long issueId) {
        Long userId = SecurityUtils.getCurrentUserId();

        // 校验工单存在
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || issue.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工单不存在: " + issueId);
        }

        // 校验：不能投票给自己创建的工单
        if (issue.getReporterId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能投票给自己创建的工单");
        }

        // 幂等处理：已投票则忽略
        if (voteMapper.isVoted(issueId, userId)) {
            return buildStatusVO(issueId, userId);
        }

        // 创建投票记录
        IssueVote vote = new IssueVote();
        vote.setIssueId(issueId);
        vote.setUserId(userId);
        vote.setCreatedAt(LocalDateTime.now());
        voteMapper.insert(vote);

        // 更新 issue 表的 vote_count 冗余字段
        voteMapper.refreshVoteCount(issueId);

        // 投票后自动添加为 Watcher
        try {
            watcherService.watch(issueId, userId);
        } catch (Exception e) {
            // Watcher 添加失败不影响投票操作
            log.warn("投票后自动关注失败: issueId={}, userId={}, error={}", issueId, userId, e.getMessage());
        }

        log.info("用户 {} 对工单 {} 进行了投票", userId, issueId);

        // 发布投票通知事件
        int voteCount = voteMapper.countByIssueId(issueId);
        eventPublisher.publishEvent(new IssueNotificationEvent.Voted(issue, userId, voteCount));

        return buildStatusVO(issueId, userId);
    }

    /**
     * 取消投票
     *
     * @param issueId 工单ID
     * @return 投票状态
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueVoteStatusVO unvote(Long issueId) {
        Long userId = SecurityUtils.getCurrentUserId();

        // 幂等处理：未投票则忽略
        if (!voteMapper.isVoted(issueId, userId)) {
            return buildStatusVO(issueId, userId);
        }

        // 删除投票记录
        LambdaQueryWrapper<IssueVote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IssueVote::getIssueId, issueId)
                .eq(IssueVote::getUserId, userId);
        voteMapper.delete(wrapper);

        // 更新 issue 表的 vote_count 冗余字段
        voteMapper.refreshVoteCount(issueId);

        log.info("用户 {} 取消了对工单 {} 的投票", userId, issueId);
        return buildStatusVO(issueId, userId);
    }

    /**
     * 获取投票状态
     *
     * @param issueId 工单ID
     * @return 投票状态（当前用户是否已投票 + 投票总数）
     */
    public IssueVoteStatusVO getVoteStatus(Long issueId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return buildStatusVO(issueId, userId);
    }

    /**
     * 获取投票者列表
     *
     * @param issueId 工单ID
     * @return 投票者信息列表
     */
    public List<IssueVoterVO> listVoters(Long issueId) {
        return voteMapper.selectVotersWithUser(issueId);
    }

    /**
     * 获取投票数
     *
     * @param issueId 工单ID
     * @return 投票数
     */
    public int getVoteCount(Long issueId) {
        return voteMapper.countByIssueId(issueId);
    }

    /**
     * 当前用户是否已投票
     *
     * @param issueId 工单ID
     * @param userId 用户ID
     * @return 是否已投票
     */
    public boolean isVoted(Long issueId, Long userId) {
        return voteMapper.isVoted(issueId, userId);
    }

    /**
     * 构建投票状态 VO
     */
    private IssueVoteStatusVO buildStatusVO(Long issueId, Long userId) {
        IssueVoteStatusVO status = new IssueVoteStatusVO();
        status.setVoted(voteMapper.isVoted(issueId, userId));
        status.setVoteCount(voteMapper.countByIssueId(issueId));
        return status;
    }
}
