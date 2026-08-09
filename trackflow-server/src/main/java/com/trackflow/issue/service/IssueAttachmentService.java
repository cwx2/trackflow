package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.config.AttachmentConfig;
import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.service.MinioService;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.mapper.IssueAttachmentMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.vo.IssueAttachmentVO;
import com.trackflow.system.mapper.UserGroupMemberMapper;
import com.trackflow.system.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 工单附件服务 - 负责附件上传、删除、可见性控制和访问校验
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueAttachmentService {

    private final IssueAttachmentMapper attachmentMapper;
    private final IssueMapper issueMapper;
    private final MinioService minioService;
    private final ProjectService projectService;
    private final PermissionService permissionService;
    private final UserGroupMemberMapper userGroupMemberMapper;
    private final UserGroupService userGroupService;
    private final IssueConverter issueConverter;
    private final IssueActivityService activityService;
    private final AttachmentConfig attachmentConfig;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 查询附件列表（已按可见性过滤）
     */
    public List<IssueAttachment> listAttachments(Long issueId) {
        List<IssueAttachment> allAttachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, issueId)
        );
        return filterAttachmentsByVisibility(allAttachments, issueId);
    }

    /**
     * 检查当前用户是否有权访问指定附件
     */
    public boolean canAccessAttachment(Long attachmentId) {
        IssueAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            return false;
        }
        if (attachment.getVisibleToGroupIds() == null || attachment.getVisibleToGroupIds().isEmpty()) {
            return true;
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        if (attachment.getUploadedBy() != null && attachment.getUploadedBy().equals(currentUserId)) {
            return true;
        }
        Issue issue = getIssueById(attachment.getIssueId());
        if (permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:read_private")) {
            return true;
        }
        List<Long> userGroupIds = userGroupMemberMapper.selectGroupIdsByUserId(currentUserId);
        if (userGroupIds == null || userGroupIds.isEmpty()) {
            return false;
        }
        Set<Long> userGroupIdSet = Set.copyOf(userGroupIds);
        return attachment.getVisibleToGroupIds().stream().anyMatch(userGroupIdSet::contains);
    }

    /**
     * 为自动化节点取得当前执行身份可读取的附件。
     * 调用方必须运行在 AutomationActorRunner 建立的身份上下文内，不能绕过附件可见性规则。
     */
    public IssueAttachment requireReadableAttachment(Long attachmentId) {
        IssueAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "附件不存在: " + attachmentId);
        }
        if (!canAccessAttachment(attachmentId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "没有读取该附件的权限");
        }
        return attachment;
    }

    /**
     * 根据文件路径查找附件记录（用于文件下载时的权限校验）
     */
    public IssueAttachment findByFilePath(String filePath) {
        return attachmentMapper.selectOne(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getFilePath, filePath)
        );
    }

    /**
     * 上传附件（无可见性限制）
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueAttachment uploadAttachment(Long issueId, MultipartFile file) {
        return uploadAttachment(issueId, file, null);
    }

    /**
     * 上传附件（支持私有上传）
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueAttachment uploadAttachment(Long issueId, MultipartFile file, List<Long> visibleToGroupIds) {
        Issue issue = getIssueById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        validateAttachmentFile(file);
        validateAttachmentCount(issueId);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        String safeFileName = sanitizeFileName(file.getOriginalFilename());

        String folder = "issues/" + issueId + "/attachments";
        String filePath = minioService.upload(folder, safeFileName, file);

        IssueAttachment attachment = new IssueAttachment();
        attachment.setIssueId(issueId);
        attachment.setFileName(safeFileName);
        attachment.setFilePath(filePath);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        attachment.setUploadedBy(currentUserId);
        attachment.setVisibleToGroupIds(
                visibleToGroupIds != null && !visibleToGroupIds.isEmpty() ? visibleToGroupIds : null);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);

        Long attachActId = activityService.recordActivity(issueId, currentUserId, "attachment_added", "attachment", null, safeFileName);
        eventPublisher.publishEvent(new IssueNotificationEvent.AttachmentAdded(issue, safeFileName, currentUserId, attachActId));
        eventPublisher.publishEvent(new WorkflowRuleEvent.AttachmentAdded(issueId, issue.getProjectId(), attachment.getId()));

        return attachment;
    }

    /**
     * 删除附件
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(Long issueId, Long attachmentId) {
        Issue issue = getIssueById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !attachment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "附件不存在");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!attachment.getUploadedBy().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_attachments")) {
                throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只能删除自己上传的附件，或需要附件管理权限");
            }
        }

        minioService.delete(attachment.getFilePath());
        attachmentMapper.deleteById(attachmentId);

        activityService.recordActivity(issueId, currentUserId, "attachment_removed", "attachment", attachment.getFileName(), null);
        eventPublisher.publishEvent(new WorkflowRuleEvent.AttachmentRemoved(issueId, issue.getProjectId(), attachmentId));
    }

    /**
     * 更新附件可见性
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueAttachment updateAttachmentVisibility(Long issueId, Long attachmentId, List<Long> visibleToGroupIds) {
        Issue issue = getIssueById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !attachment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "附件不存在");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!attachment.getUploadedBy().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_attachments")) {
                throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只能修改自己上传的附件可见性，或需要附件管理权限");
            }
        }

        List<Long> newVisibility = (visibleToGroupIds != null && !visibleToGroupIds.isEmpty()) ? visibleToGroupIds : null;
        attachment.setVisibleToGroupIds(newVisibility);
        attachmentMapper.updateById(attachment);

        String visibilityDesc = newVisibility == null ? "公开" : "限制可见";
        activityService.recordActivity(issueId, currentUserId, "attachment_visibility_changed", "attachment_visibility",
                null, attachment.getFileName() + " → " + visibilityDesc);

        return attachment;
    }

    /**
     * 批量删除某工单的所有附件（用于永久删除工单时清理）
     */
    public void deleteAllByIssueId(Long issueId) {
        List<IssueAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, issueId)
        );
        for (IssueAttachment att : attachments) {
            minioService.delete(att.getFilePath());
        }
        attachmentMapper.delete(new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, issueId));
    }

    // ========== Private helpers ==========

    private List<IssueAttachment> filterAttachmentsByVisibility(List<IssueAttachment> attachments, Long issueId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return attachments.stream()
                    .filter(a -> a.getVisibleToGroupIds() == null || a.getVisibleToGroupIds().isEmpty())
                    .toList();
        }

        boolean hasPrivate = attachments.stream()
                .anyMatch(a -> a.getVisibleToGroupIds() != null && !a.getVisibleToGroupIds().isEmpty());
        if (!hasPrivate) {
            return attachments;
        }

        Issue issue = getIssueById(issueId);
        if (permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:read_private")) {
            return attachments;
        }

        List<Long> userGroupIds = userGroupMemberMapper.selectGroupIdsByUserId(currentUserId);
        Set<Long> userGroupIdSet = userGroupIds != null ? Set.copyOf(userGroupIds) : Set.of();

        return attachments.stream()
                .filter(a -> {
                    if (a.getVisibleToGroupIds() == null || a.getVisibleToGroupIds().isEmpty()) {
                        return true;
                    }
                    if (a.getUploadedBy() != null && a.getUploadedBy().equals(currentUserId)) {
                        return true;
                    }
                    return a.getVisibleToGroupIds().stream().anyMatch(userGroupIdSet::contains);
                })
                .toList();
    }

    private void validateAttachmentFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > attachmentConfig.getMaxFileSize()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "文件大小超出限制（最大 " + attachmentConfig.getMaxFileSizeReadable() + "）");
        }
        String extension = getFileExtension(file.getOriginalFilename());
        if (attachmentConfig.getBlockedExtensions().contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不允许上传此类型文件: ." + extension);
        }
    }

    private void validateAttachmentCount(Long issueId) {
        long count = attachmentMapper.selectCount(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, issueId));
        if (count >= attachmentConfig.getMaxAttachmentsPerIssue()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该工单附件数量已达上限（最多 " + attachmentConfig.getMaxAttachmentsPerIssue() + " 个）");
        }
    }

    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unnamed";
        }
        String name = originalName;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = name.replaceAll("\\.\\.", "_")
                   .replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.isBlank()) {
            return "unnamed";
        }
        return name;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private Issue getIssueById(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found: " + issueId);
        }
        return issue;
    }

    /**
     * 构建附件 VO（含可见性信息和组名称）
     */
    public IssueAttachmentVO buildAttachmentVO(IssueAttachment attachment) {
        IssueAttachmentVO vo = issueConverter.toAttachmentVO(attachment);
        vo.setIsPrivate(attachment.getVisibleToGroupIds() != null && !attachment.getVisibleToGroupIds().isEmpty());
        if (Boolean.TRUE.equals(vo.getIsPrivate())) {
            vo.setVisibleToGroupIds(attachment.getVisibleToGroupIds().stream()
                    .map(String::valueOf).toList());
            vo.setVisibleToGroupNames(userGroupService.getGroupNamesByIds(attachment.getVisibleToGroupIds()));
        }
        return vo;
    }

    /**
     * 批量构建附件 VO 列表
     */
    public List<IssueAttachmentVO> buildAttachmentVOList(List<IssueAttachment> attachments) {
        return attachments.stream().map(this::buildAttachmentVO).toList();
    }
}
