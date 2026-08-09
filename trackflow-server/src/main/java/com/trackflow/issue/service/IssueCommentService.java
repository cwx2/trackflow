package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.automation.execution.AutomationActorRunner;
import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.result.CommentRow;
import com.trackflow.issue.vo.IssueCommentVO;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserGroupMemberMapper;
import com.trackflow.system.mapper.UserGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工单评论服务 - 负责评论的增删改查、可见性控制和通知触发
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueCommentService {

    private final IssueCommentMapper commentMapper;
    private final IssueMapper issueMapper;
    private final SysUserMapper sysUserMapper;
    private final UserGroupMemberMapper userGroupMemberMapper;
    private final UserGroupMapper userGroupMapper;
    private final ProjectService projectService;
    private final PermissionService permissionService;
    private final IssueActivityService activityService;
    private final IssueConverter issueConverter;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 查询评论列表（简单查询，不含用户信息）
     */
    public List<IssueComment> listComments(Long issueId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<IssueComment>()
                        .eq(IssueComment::getIssueId, issueId)
                        .isNull(IssueComment::getDeletedAt)
                        .orderByAsc(IssueComment::getCreatedAt)
        );
    }

    /**
     * 添加评论（无可见性限制）
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueComment addComment(Long issueId, String content) {
        return addComment(issueId, content, null);
    }

    /**
     * 添加评论（支持可见性限制）
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueComment addComment(Long issueId, String content, List<Long> visibleToGroupIds) {
        Issue issue = getIssueById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 权限校验：设置评论可见性需要 issue:edit 权限
        if (visibleToGroupIds != null && !visibleToGroupIds.isEmpty()) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:edit")) {
                visibleToGroupIds = null;
            }
        }

        IssueComment comment = new IssueComment();
        comment.setIssueId(issueId);
        comment.setUserId(currentUserId);
        comment.setContent(content);
        comment.setSource(AutomationActorRunner.isAutomationExecution() ? "automation" : "web");
        if (visibleToGroupIds != null && !visibleToGroupIds.isEmpty()) {
            comment.setVisibleToGroupIds(visibleToGroupIds);
        }
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        // 触摸 issue 的 updated_at，使评论操作反映在工单更新时间上（与 YouTrack 行为一致）
        issueMapper.update(null, new LambdaUpdateWrapper<Issue>()
                .eq(Issue::getId, issueId)
                .set(Issue::getUpdatedAt, LocalDateTime.now())
                .set(Issue::getUpdatedBy, currentUserId));

        activityService.recordActivity(issueId, currentUserId, "commented", null, null, null);

        // 通知报告人+负责人+之前评论者 — 事务提交后触发
        eventPublisher.publishEvent(new IssueNotificationEvent.Commented(issue, currentUserId, comment.getId()));

        // 解析评论中的 @mention 并通知被提及的用户 — 事务提交后触发
        eventPublisher.publishEvent(new IssueNotificationEvent.Mentioned(issue, content, currentUserId, comment.getId()));

        // 触发 comment_added 工作流规则 — 事务提交后异步执行
        eventPublisher.publishEvent(new WorkflowRuleEvent.CommentAdded(
                issueId, issue.getProjectId(), comment.getId(), content));

        return comment;
    }

    /**
     * 更新评论（简化版）
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueComment updateComment(Long issueId, Long commentId, String newContent) {
        return updateComment(issueId, commentId, newContent, null, false);
    }

    /**
     * 更新评论内容和/或可见性
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueComment updateComment(Long issueId, Long commentId, String newContent,
                                       List<Long> visibleToGroupIds, boolean updateVisibility) {
        Issue issue = getIssueById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不存在");
        }
        if (!comment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不属于该工单");
        }

        // 权限校验：作者本人 OR 拥有 issue:manage_comments 权限
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!comment.getUserId().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权编辑他人评论");
            }
        }

        comment.setContent(newContent);
        if (updateVisibility) {
            if (permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:edit")) {
                comment.setVisibleToGroupIds(
                        visibleToGroupIds != null && !visibleToGroupIds.isEmpty() ? visibleToGroupIds : null);
            }
        }
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);

        activityService.recordActivity(issueId, currentUserId, "comment_updated", null, null, null);

        return comment;
    }

    /**
     * 软删除评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long issueId, Long commentId) {
        Issue issue = getIssueById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不存在");
        }
        if (!comment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不属于该工单");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!comment.getUserId().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权删除他人评论");
            }
        }

        String deletedContent = stripHtmlForActivity(comment.getContent());
        commentMapper.deleteById(commentId);
        activityService.recordActivity(issueId, currentUserId, "comment_deleted", null, deletedContent, null);
    }

    /**
     * 还原已软删除的评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void restoreComment(Long issueId, Long commentId) {
        Issue issue = getIssueById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueComment comment = commentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不存在");
        }
        if (comment.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评论未被删除，无需还原");
        }
        if (!comment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不属于该工单");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!comment.getUserId().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权还原他人评论");
            }
        }

        commentMapper.restoreById(commentId);
        activityService.recordActivity(issueId, currentUserId, "comment_restored", null, null, null);
    }

    /**
     * 永久删除评论（物理删除，不可恢复）
     */
    @Transactional(rollbackFor = Exception.class)
    public void permanentlyDeleteComment(Long issueId, Long commentId) {
        Issue issue = getIssueById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueComment comment = commentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不存在");
        }
        if (!comment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不属于该工单");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权永久删除评论，需要 manage_comments 权限");
        }

        commentMapper.physicalDeleteById(commentId);
        activityService.recordActivity(issueId, currentUserId, "comment_permanently_deleted", null, null, null);
    }

    /**
     * 将评论实体转为完整 VO（含 userName、userAvatar、isEdited）
     */
    public IssueCommentVO toCommentVOWithUser(IssueComment comment) {
        IssueCommentVO vo = issueConverter.toCommentVO(comment);
        if (comment.getUserId() != null) {
            SysUser user = sysUserMapper.selectById(comment.getUserId());
            if (user != null) {
                vo.setUserName(user.getDisplayName());
                vo.setUserAvatar(user.getAvatarUrl());
            }
        }
        vo.setIsEdited(comment.getCreatedAt() != null && comment.getUpdatedAt() != null
                && comment.getUpdatedAt().isAfter(comment.getCreatedAt().plusSeconds(1)));
        if (comment.getVisibleToGroupIds() != null && !comment.getVisibleToGroupIds().isEmpty()) {
            vo.setVisibleToGroupIds(comment.getVisibleToGroupIds().stream()
                    .map(String::valueOf).toList());
            Map<Long, String> groupNameMap = userGroupMapper.selectBatchIds(comment.getVisibleToGroupIds()).stream()
                    .collect(Collectors.toMap(
                            com.trackflow.system.entity.UserGroup::getId,
                            com.trackflow.system.entity.UserGroup::getName));
            vo.setVisibleToGroupNames(comment.getVisibleToGroupIds().stream()
                    .map(gid -> groupNameMap.getOrDefault(gid, "未知组"))
                    .toList());
        }
        return vo;
    }

    /**
     * 获取评论列表 —— 单次 JOIN 查询（含可见性过滤）
     * 返回已过滤的 CommentRow 列表（业务逻辑：权限过滤），VO 转换交给 Controller。
     */
    @Transactional(readOnly = true)
    public List<CommentRow> listVisibleComments(Long issueId) {
        List<CommentRow> rows = issueMapper.selectCommentsWithUser(issueId);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<Long> currentUserGroupIds = userGroupMemberMapper.selectGroupIdsByUserId(currentUserId);

        Issue issue = getIssueById(issueId);
        boolean canManageComments = permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments");

        return rows.stream()
                .filter(row -> isCommentVisibleToUser(row, currentUserId, currentUserGroupIds, canManageComments))
                .toList();
    }

    /**
     * @deprecated 仅为向后兼容保留，新代码应使用 {@link #listVisibleComments(Long)} + Controller 层转换
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<IssueCommentVO> listCommentsWithUser(Long issueId) {
        List<CommentRow> visibleRows = listVisibleComments(issueId);

        Set<Long> allGroupIds = visibleRows.stream()
                .filter(row -> row.getVisibleToGroupIds() != null && !row.getVisibleToGroupIds().isEmpty())
                .flatMap(row -> row.getVisibleToGroupIds().stream())
                .collect(Collectors.toSet());

        Map<Long, String> groupNameMap = Collections.emptyMap();
        if (!allGroupIds.isEmpty()) {
            groupNameMap = userGroupMapper.selectBatchIds(allGroupIds).stream()
                    .collect(Collectors.toMap(
                            com.trackflow.system.entity.UserGroup::getId,
                            com.trackflow.system.entity.UserGroup::getName));
        }

        Map<Long, String> finalGroupNameMap = groupNameMap;
        return visibleRows.stream().map(row -> {
            IssueCommentVO vo = new IssueCommentVO();
            vo.setId(String.valueOf(row.getId()));
            vo.setIssueId(String.valueOf(row.getIssueId()));
            vo.setUserId(String.valueOf(row.getUserId()));
            vo.setUserName(row.getUserName());
            vo.setUserAvatar(row.getUserAvatar());
            vo.setDeletedAt(row.getDeletedAt());
            vo.setContent(row.getDeletedAt() != null ? null : row.getContent());
            vo.setSource(row.getSource());
            vo.setCreatedAt(row.getCreatedAt());
            vo.setUpdatedAt(row.getUpdatedAt());
            vo.setIsEdited(row.getCreatedAt() != null && row.getUpdatedAt() != null
                    && row.getUpdatedAt().isAfter(row.getCreatedAt().plusSeconds(1)));
            if (row.getVisibleToGroupIds() != null && !row.getVisibleToGroupIds().isEmpty()) {
                vo.setVisibleToGroupIds(row.getVisibleToGroupIds().stream()
                        .map(String::valueOf).toList());
                vo.setVisibleToGroupNames(row.getVisibleToGroupIds().stream()
                        .map(gid -> finalGroupNameMap.getOrDefault(gid, "未知组"))
                        .toList());
            }
            return vo;
        }).toList();
    }

    private boolean isCommentVisibleToUser(CommentRow row, Long currentUserId,
                                           List<Long> currentUserGroupIds, boolean canManageComments) {
        if (row.getVisibleToGroupIds() == null || row.getVisibleToGroupIds().isEmpty()) {
            return true;
        }
        if (row.getUserId().equals(currentUserId)) {
            return true;
        }
        if (canManageComments) {
            return true;
        }
        if (currentUserGroupIds == null || currentUserGroupIds.isEmpty()) {
            return false;
        }
        for (Long groupId : row.getVisibleToGroupIds()) {
            if (currentUserGroupIds.contains(groupId)) {
                return true;
            }
        }
        return false;
    }

    private String stripHtmlForActivity(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html.replaceAll("<[^>]+>", "").trim();
        text = text.replaceAll("\\s+", " ");
        if (text.length() > 500) {
            return text.substring(0, 497) + "...";
        }
        return text;
    }

    private Issue getIssueById(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found: " + issueId);
        }
        return issue;
    }
}
