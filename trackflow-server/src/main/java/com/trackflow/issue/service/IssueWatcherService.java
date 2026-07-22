package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.service.NotificationPreferenceService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueWatcher;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueWatcherMapper;
import com.trackflow.issue.vo.IssueWatcherVO;
import com.trackflow.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单关注（Watcher）服务。
 * <p>
 * 负责管理用户对工单的关注关系，以及自动关注（Auto-Watch）行为。
 * 关注后，工单的所有更新通知会推送给关注者（受 onWatchedUpdated 偏好控制）。
 * <p>
 * 参考 OpenProject Watcher 模型：
 * - watch 前校验用户对工单所在项目有查看权限
 * - 支持自动 watch 规则（创建/评论/修改/被分配）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueWatcherService {

    private final IssueWatcherMapper watcherMapper;
    private final IssueMapper issueMapper;
    private final ProjectService projectService;
    private final NotificationPreferenceService preferenceService;

    /**
     * 手动关注工单。
     * 校验：用户必须是工单所在项目的成员。
     */
    @Transactional(rollbackFor = Exception.class)
    public void watch(Long issueId, Long userId) {
        // 校验工单存在
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || issue.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工单不存在");
        }

        // 校验用户是项目成员
        validateProjectMembership(userId, issue.getProjectId());

        // 幂等处理：已关注则直接返回
        if (watcherMapper.isWatching(issueId, userId)) {
            return;
        }

        IssueWatcher watcher = new IssueWatcher();
        watcher.setIssueId(issueId);
        watcher.setUserId(userId);
        watcher.setCreatedAt(LocalDateTime.now());
        watcherMapper.insert(watcher);

        log.debug("[Watcher] 用户 {} 关注了工单 {}", userId, issueId);
    }

    /**
     * 取消关注工单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unwatch(Long issueId, Long userId) {
        watcherMapper.delete(new LambdaQueryWrapper<IssueWatcher>()
                .eq(IssueWatcher::getIssueId, issueId)
                .eq(IssueWatcher::getUserId, userId));
        log.debug("[Watcher] 用户 {} 取消关注工单 {}", userId, issueId);
    }

    /**
     * 查询工单的关注者用户ID列表
     */
    public List<Long> getWatcherUserIds(Long issueId) {
        return watcherMapper.selectWatcherUserIds(issueId);
    }

    /**
     * 查询工单的关注者列表（含用户信息），单次 JOIN 查询避免 N+1
     */
    public List<IssueWatcherVO> listWatchersWithUser(Long issueId) {
        return watcherMapper.selectWatchersWithUser(issueId);
    }

    /**
     * 查询工单的关注者数量
     */
    public int getWatcherCount(Long issueId) {
        return watcherMapper.countByIssueId(issueId);
    }

    /**
     * 检查用户是否已关注工单
     */
    public boolean isWatching(Long issueId, Long userId) {
        return watcherMapper.isWatching(issueId, userId);
    }

    /**
     * 查询某用户关注的所有工单ID
     */
    public List<Long> getWatchedIssueIds(Long userId) {
        List<IssueWatcher> watchers = watcherMapper.selectList(
                new LambdaQueryWrapper<IssueWatcher>()
                        .eq(IssueWatcher::getUserId, userId)
                        .select(IssueWatcher::getIssueId));
        return watchers.stream().map(IssueWatcher::getIssueId).toList();
    }

    // ==================== 自动关注（Auto-Watch）====================

    /**
     * 自动关注：用户创建工单后自动加入 watcher。
     * 异步执行，不阻塞主流程。
     */
    @Async("notificationExecutor")
    public void autoWatchOnCreate(Long issueId, Long userId) {
        try {
            if (shouldAutoWatch(userId, null, "create")) {
                addWatcherSilently(issueId, userId);
                log.debug("[AutoWatch] 用户 {} 创建工单 {} 后自动关注", userId, issueId);
            }
        } catch (Exception e) {
            log.warn("[AutoWatch] 自动关注失败（创建）: issueId={}, userId={}, error={}",
                    issueId, userId, e.getMessage());
        }
    }

    /**
     * 自动关注：用户评论工单后自动加入 watcher。
     */
    @Async("notificationExecutor")
    public void autoWatchOnComment(Long issueId, Long userId) {
        try {
            if (shouldAutoWatch(userId, null, "comment")) {
                addWatcherSilently(issueId, userId);
                log.debug("[AutoWatch] 用户 {} 评论工单 {} 后自动关注", userId, issueId);
            }
        } catch (Exception e) {
            log.warn("[AutoWatch] 自动关注失败（评论）: issueId={}, userId={}, error={}",
                    issueId, userId, e.getMessage());
        }
    }

    /**
     * 自动关注：用户修改工单后自动加入 watcher。
     */
    @Async("notificationExecutor")
    public void autoWatchOnUpdate(Long issueId, Long userId) {
        try {
            if (shouldAutoWatch(userId, null, "update")) {
                addWatcherSilently(issueId, userId);
                log.debug("[AutoWatch] 用户 {} 修改工单 {} 后自动关注", userId, issueId);
            }
        } catch (Exception e) {
            log.warn("[AutoWatch] 自动关注失败（修改）: issueId={}, userId={}, error={}",
                    issueId, userId, e.getMessage());
        }
    }

    /**
     * 自动关注：用户被分配为负责人后自动加入 watcher。
     */
    @Async("notificationExecutor")
    public void autoWatchOnAssign(Long issueId, Long assigneeId) {
        try {
            if (shouldAutoWatch(assigneeId, null, "assign")) {
                addWatcherSilently(issueId, assigneeId);
                log.debug("[AutoWatch] 用户 {} 被分配工单 {} 后自动关注", assigneeId, issueId);
            }
        } catch (Exception e) {
            log.warn("[AutoWatch] 自动关注失败（分配）: issueId={}, assigneeId={}, error={}",
                    issueId, assigneeId, e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 检查用户的自动关注偏好设置
     */
    private boolean shouldAutoWatch(Long userId, Long projectId, String trigger) {
        NotificationPreference pref = preferenceService.getApplicable(userId, projectId);
        if (pref == null) {
            // 无偏好记录时使用默认值
            return switch (trigger) {
                case "create", "comment", "assign" -> true;  // 默认开启
                case "update" -> false;  // 默认关闭
                default -> false;
            };
        }
        return switch (trigger) {
            case "create" -> pref.getAutoWatchOnCreate() != null ? pref.getAutoWatchOnCreate() : true;
            case "comment" -> pref.getAutoWatchOnComment() != null ? pref.getAutoWatchOnComment() : true;
            case "update" -> pref.getAutoWatchOnUpdate() != null ? pref.getAutoWatchOnUpdate() : false;
            case "assign" -> pref.getAutoWatchOnAssign() != null ? pref.getAutoWatchOnAssign() : true;
            default -> false;
        };
    }

    /**
     * 静默添加 watcher（幂等，不抛异常）
     */
    private void addWatcherSilently(Long issueId, Long userId) {
        if (watcherMapper.isWatching(issueId, userId)) {
            return;
        }
        IssueWatcher watcher = new IssueWatcher();
        watcher.setIssueId(issueId);
        watcher.setUserId(userId);
        watcher.setCreatedAt(LocalDateTime.now());
        watcherMapper.insert(watcher);
    }

    /**
     * 校验用户是否为工单所在项目的成员
     */
    private void validateProjectMembership(Long userId, Long projectId) {
        if (!projectService.isProjectMember(userId, projectId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "用户不是项目成员，无法关注此工单");
        }
    }
}
