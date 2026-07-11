package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.service.MinioService;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.dto.CreateIssueDTO;
import com.trackflow.issue.dto.UpdateIssueDTO;
import com.trackflow.issue.entity.*;
import com.trackflow.issue.mapper.*;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueAttachmentMapper attachmentMapper;
    private final IssueActivityMapper activityMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;
    private final SysUserMapper userMapper;
    private final IssueConverter issueConverter;
    private final IssueTagService tagService;
    private final SprintMapper sprintMapper;

    /**
     * 创建 Issue
     */
    @Transactional
    public Issue create(CreateIssueDTO dto) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 生成 Issue Key
        int seq = projectService.nextIssueSequence(dto.getProjectId());
        var project = projectService.getById(dto.getProjectId());
        String issueKey = project.getKey() + "-" + seq;

        // 获取默认状态
        IssueStatus defaultStatus = statusMapper.selectOne(
                new LambdaQueryWrapper<IssueStatus>().eq(IssueStatus::getIsDefault, true)
        );

        Issue issue = new Issue();
        issue.setProjectId(dto.getProjectId());
        issue.setIssueKey(issueKey);
        issue.setTitle(dto.getTitle());
        issue.setDescription(dto.getDescription());
        issue.setIssueType(dto.getIssueType() != null ? dto.getIssueType() : "Task");
        issue.setStatusId(defaultStatus != null ? defaultStatus.getId() : 1L);
        issue.setPriority(dto.getPriority() != null ? dto.getPriority() : "Normal");
        issue.setAssigneeId(dto.getAssigneeId());
        issue.setReporterId(currentUserId);
        issue.setSprintId(dto.getSprintId());
        issue.setParentId(dto.getParentId());
        issue.setDueDate(dto.getDueDate());
        issue.setEstimatedHours(dto.getEstimatedHours());
        issue.setCreatedBy(currentUserId);

        if (dto.getCustomFields() != null) {
            try {
                issue.setCustomFields(objectMapper.writeValueAsString(dto.getCustomFields()));
            } catch (JsonProcessingException e) {
                issue.setCustomFields("{}");
            }
        }

        issueMapper.insert(issue);

        // 记录活动
        recordActivity(issue.getId(), currentUserId, "created", null, null, null);

        return issue;
    }

    /**
     * Issue 列表（支持多条件筛选）
     */
    public Page<Issue> list(Page<Issue> page, Long projectId, Long statusId, String priority,
                            Long assigneeId, Long reporterId, Long sprintId, String issueType,
                            String keyword) {
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (projectId != null) wrapper.eq("project_id", projectId);
        if (statusId != null) wrapper.eq("status_id", statusId);
        if (priority != null) wrapper.eq("priority", priority);
        if (assigneeId != null) wrapper.eq("assignee_id", assigneeId);
        if (reporterId != null) wrapper.eq("reporter_id", reporterId);
        if (sprintId != null) wrapper.eq("sprint_id", sprintId);
        if (issueType != null) wrapper.eq("issue_type", issueType);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like("title", keyword)
                    .or()
                    .like("description", keyword)
                    .or()
                    .like("issue_key", keyword)
            );
        }

        wrapper.orderByDesc("updated_at");
        return issueMapper.selectPage(page, wrapper);
    }

    /**
     * Issue 详情
     */
    public Issue getById(Long id) {
        Issue issue = issueMapper.selectById(id);
        if (issue == null || issue.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        return issue;
    }

    /**
     * 通过 issueKey 获取
     */
    public Issue getByKey(String issueKey) {
        Issue issue = issueMapper.selectOne(
                new LambdaQueryWrapper<Issue>().eq(Issue::getIssueKey, issueKey).isNull(Issue::getDeletedAt)
        );
        if (issue == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        return issue;
    }

    /**
     * 更新 Issue
     */
    @Transactional
    public Issue update(Long id, UpdateIssueDTO dto) {
        Issue issue = getById(id);
        Long currentUserId = SecurityUtils.getCurrentUserId();

        if (dto.getTitle() != null) {
            recordActivity(id, currentUserId, "updated", "title", issue.getTitle(), dto.getTitle());
            issue.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) issue.setDescription(dto.getDescription());
        if (dto.getIssueType() != null) issue.setIssueType(dto.getIssueType());
        if (dto.getPriority() != null) {
            recordActivity(id, currentUserId, "updated", "priority", issue.getPriority(), dto.getPriority());
            issue.setPriority(dto.getPriority());
        }
        if (dto.getAssigneeId() != null) {
            recordActivity(id, currentUserId, "assigned", "assignee",
                    String.valueOf(issue.getAssigneeId()), String.valueOf(dto.getAssigneeId()));
            issue.setAssigneeId(dto.getAssigneeId());
        }
        if (dto.getSprintId() != null) issue.setSprintId(dto.getSprintId());
        if (dto.getParentId() != null) issue.setParentId(dto.getParentId());
        if (dto.getDueDate() != null) issue.setDueDate(dto.getDueDate());
        if (dto.getEstimatedHours() != null) issue.setEstimatedHours(dto.getEstimatedHours());
        if (dto.getCustomFields() != null) {
            try {
                issue.setCustomFields(objectMapper.writeValueAsString(dto.getCustomFields()));
            } catch (JsonProcessingException e) {
                // keep existing
            }
        }

        issueMapper.updateById(issue);
        return issue;
    }

    /**
     * 软删除 Issue
     */
    @Transactional
    public void delete(Long id) {
        Issue issue = getById(id);
        issue.setDeletedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
        recordActivity(id, SecurityUtils.getCurrentUserId(), "deleted", null, null, null);
    }

    /**
     * 状态变更
     */
    @Transactional
    public void transitStatus(Long id, Long newStatusId, String comment) {
        Issue issue = getById(id);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long oldStatusId = issue.getStatusId();

        // 验证目标状态存在
        IssueStatus newStatus = statusMapper.selectById(newStatusId);
        if (newStatus == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid target status");
        }

        issue.setStatusId(newStatusId);
        if (newStatus.getIsClosed()) {
            issue.setResolvedAt(LocalDateTime.now());
        }
        issueMapper.updateById(issue);

        // 记录状态变更活动
        IssueStatus oldStatus = statusMapper.selectById(oldStatusId);
        recordActivity(id, currentUserId, "status_changed", "status",
                oldStatus != null ? oldStatus.getName() : String.valueOf(oldStatusId),
                newStatus.getName());

        // 如果带了评论，同时添加评论
        if (comment != null && !comment.isBlank()) {
            addComment(id, comment);
        }
    }

    /**
     * 分配 Issue
     */
    @Transactional
    public void assign(Long id, Long assigneeId) {
        Issue issue = getById(id);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        recordActivity(id, currentUserId, "assigned", "assignee",
                String.valueOf(issue.getAssigneeId()), String.valueOf(assigneeId));
        issue.setAssigneeId(assigneeId);
        issueMapper.updateById(issue);
    }

    // ========== 评论 ==========

    public List<IssueComment> listComments(Long issueId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<IssueComment>()
                        .eq(IssueComment::getIssueId, issueId)
                        .isNull(IssueComment::getDeletedAt)
                        .orderByAsc(IssueComment::getCreatedAt)
        );
    }

    @Transactional
    public IssueComment addComment(Long issueId, String content) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        IssueComment comment = new IssueComment();
        comment.setIssueId(issueId);
        comment.setUserId(currentUserId);
        comment.setContent(content);
        comment.setSource("web");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        recordActivity(issueId, currentUserId, "commented", null, null, null);
        return comment;
    }

    // ========== 附件 ==========

    public List<IssueAttachment> listAttachments(Long issueId) {
        return attachmentMapper.selectList(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, issueId)
        );
    }

    // ========== 活动记录 ==========

    public List<IssueActivity> listActivities(Long issueId) {
        return activityMapper.selectList(
                new LambdaQueryWrapper<IssueActivity>()
                        .eq(IssueActivity::getIssueId, issueId)
                        .orderByDesc(IssueActivity::getCreatedAt)
        );
    }

    // ========== 状态 ==========

    public List<IssueStatus> listStatuses() {
        return statusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>().orderByAsc(IssueStatus::getSortOrder)
        );
    }

    // ========== 增强详情 ==========

    /**
     * 获取增强版 Issue 详情（含关联信息）
     */
    public IssueDetailVO getDetail(Long id) {
        Issue issue = getById(id);
        IssueDetailVO vo = issueConverter.toDetailVO(issue);

        // 补充项目名称
        Project project = projectService.getById(issue.getProjectId());
        if (project != null) {
            vo.setProjectName(project.getName());
        }

        // 补充状态对象
        IssueStatus status = statusMapper.selectById(issue.getStatusId());
        if (status != null) {
            vo.setStatus(issueConverter.toStatusVO(status));
        }

        // 补充负责人名称
        if (issue.getAssigneeId() != null) {
            SysUser assignee = userMapper.selectById(issue.getAssigneeId());
            if (assignee != null) {
                vo.setAssigneeName(assignee.getDisplayName());
            }
        }

        // 补充报告人名称
        if (issue.getReporterId() != null) {
            SysUser reporter = userMapper.selectById(issue.getReporterId());
            if (reporter != null) {
                vo.setReporterName(reporter.getDisplayName());
            }
        }

        // 补充 Sprint 名称
        if (issue.getSprintId() != null) {
            Sprint sprint = sprintMapper.selectById(issue.getSprintId());
            if (sprint != null) {
                vo.setSprintName(sprint.getName());
            }
        }

        // 补充父 Issue Key
        if (issue.getParentId() != null) {
            Issue parent = issueMapper.selectById(issue.getParentId());
            if (parent != null) {
                vo.setParentKey(parent.getIssueKey());
            }
        }

        // 补充标签
        List<IssueTag> tags = tagService.listIssueTags(issue.getId());
        vo.setTags(issueConverter.toTagVOList(tags));

        return vo;
    }

    /**
     * 获取带用户名的评论列表
     */
    public List<IssueCommentVO> listCommentsWithUser(Long issueId) {
        List<IssueComment> comments = listComments(issueId);
        List<IssueCommentVO> voList = issueConverter.toCommentVOList(comments);
        for (IssueCommentVO vo : voList) {
            if (vo.getUserId() != null) {
                SysUser user = userMapper.selectById(Long.valueOf(vo.getUserId()));
                if (user != null) {
                    vo.setUserName(user.getDisplayName());
                    vo.setUserAvatar(user.getAvatarUrl());
                }
            }
        }
        return voList;
    }

    /**
     * 获取带用户名的活动列表
     */
    public List<IssueActivityVO> listActivitiesWithUser(Long issueId) {
        List<IssueActivity> activities = listActivities(issueId);
        List<IssueActivityVO> voList = issueConverter.toActivityVOList(activities);
        for (IssueActivityVO vo : voList) {
            if (vo.getUserId() != null) {
                SysUser user = userMapper.selectById(Long.valueOf(vo.getUserId()));
                if (user != null) {
                    vo.setUserName(user.getDisplayName());
                }
            }
        }
        return voList;
    }

    // ========== 附件上传 ==========

    /**
     * 上传附件
     */
    @Transactional
    public IssueAttachment uploadAttachment(Long issueId, MultipartFile file) {
        // 验证 Issue 存在
        getById(issueId);
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 上传到 MinIO
        String folder = "issues/" + issueId + "/attachments";
        String filePath = minioService.upload(folder, file.getOriginalFilename(), file);

        // 保存 DB 记录
        IssueAttachment attachment = new IssueAttachment();
        attachment.setIssueId(issueId);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFilePath(filePath);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        attachment.setUploadedBy(currentUserId);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);

        // 记录活动
        recordActivity(issueId, currentUserId, "attachment_added", "attachment", null, file.getOriginalFilename());

        return attachment;
    }

    /**
     * 删除附件
     */
    @Transactional
    public void deleteAttachment(Long issueId, Long attachmentId) {
        IssueAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !attachment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "附件不存在");
        }

        // 从 MinIO 删除
        minioService.delete(attachment.getFilePath());
        // 从 DB 删除
        attachmentMapper.deleteById(attachmentId);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        recordActivity(issueId, currentUserId, "attachment_removed", "attachment", attachment.getFileName(), null);
    }

    // ========== 内部方法 ==========

    private void recordActivity(Long issueId, Long userId, String action,
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
    }
}
