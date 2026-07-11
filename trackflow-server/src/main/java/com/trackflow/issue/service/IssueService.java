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
import com.trackflow.issue.dto.IssueQuery;
import com.trackflow.issue.dto.UpdateIssueDTO;
import com.trackflow.issue.entity.*;
import com.trackflow.issue.mapper.*;
import com.trackflow.project.service.ProjectService;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private final IssueConverter issueConverter;
    private final IssueTagService tagService;

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
        return list(page, projectId, statusId, priority, assigneeId, reporterId, sprintId, issueType, keyword,
                null, null, null, null, null);
    }

    /**
     * Issue 列表（接受 IssueQuery，完整筛选支持）
     */
    public Page<Issue> listByQuery(IssueQuery query) {
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (query.getProjectId() != null) wrapper.eq("project_id", query.getProjectId());

        // statusId: supports single or comma-separated
        applyFilter(wrapper, "status_id", query.getStatusId(), true);
        // priority: supports single or comma-separated
        applyFilter(wrapper, "priority", query.getPriority(), false);
        // assigneeId: supports single or comma-separated
        applyFilter(wrapper, "assignee_id", query.getAssigneeId(), true);
        if (query.getReporterId() != null) wrapper.eq("reporter_id", query.getReporterId());
        // sprintId: supports single or comma-separated
        applyFilter(wrapper, "sprint_id", query.getSprintId(), true);
        // issueType: supports single or comma-separated
        applyFilter(wrapper, "issue_type", query.getIssueType(), false);

        // Negative filters
        applyNegativeFilter(wrapper, "status_id", query.getStatusIdNot(), true);
        applyNegativeFilter(wrapper, "priority", query.getPriorityNot(), false);
        applyNegativeFilter(wrapper, "assignee_id", query.getAssigneeIdNot(), true);
        applyNegativeFilter(wrapper, "sprint_id", query.getSprintIdNot(), true);
        applyNegativeFilter(wrapper, "issue_type", query.getIssueTypeNot(), false);

        // Special filters: overdue and dueSoon (auto-exclude done/cancelled statuses)
        if ("true".equals(query.getOverdue()) || "true".equals(query.getDueSoon())) {
            // Get done/cancelled status IDs to exclude
            List<IssueStatus> allStatuses = statusMapper.selectList(null);
            List<Long> closedIds = allStatuses.stream()
                    .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
                    .map(IssueStatus::getId).toList();
            if (!closedIds.isEmpty()) {
                wrapper.notIn("status_id", closedIds);
            }
            wrapper.isNotNull("due_date");
            if ("true".equals(query.getOverdue())) {
                wrapper.lt("due_date", LocalDate.now());
            }
            if ("true".equals(query.getDueSoon())) {
                wrapper.le("due_date", LocalDate.now().plusDays(7));
            }
        }

        String keyword = query.getKeyword();
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
        return issueMapper.selectPage(query.toPage(), wrapper);
    }

    private void applyFilter(QueryWrapper<Issue> wrapper, String column, String value, boolean isNumeric) {
        if (value == null || value.isBlank()) return;
        if (value.contains(",")) {
            List<?> values = isNumeric
                    ? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList()
                    : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            wrapper.in(column, values);
        } else {
            if (isNumeric) {
                wrapper.eq(column, Long.parseLong(value.trim()));
            } else {
                wrapper.eq(column, value.trim());
            }
        }
    }

    private void applyNegativeFilter(QueryWrapper<Issue> wrapper, String column, String value, boolean isNumeric) {
        if (value == null || value.isBlank()) return;
        List<?> values = isNumeric
                ? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList()
                : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        wrapper.notIn(column, values);
    }

    /**
     * Issue 列表（支持正向+否定筛选，支持逗号分隔多值）
     */
    public Page<Issue> list(Page<Issue> page, Long projectId, Long statusId, String priority,
                            Long assigneeId, Long reporterId, Long sprintId, String issueType,
                            String keyword,
                            String statusIdNot, String priorityNot, String assigneeIdNot,
                            String sprintIdNot, String issueTypeNot) {
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (projectId != null) wrapper.eq("project_id", projectId);
        if (statusId != null) wrapper.eq("status_id", statusId);
        if (priority != null) {
            // Support comma-separated values (any_of)
            if (priority.contains(",")) {
                wrapper.in("priority", java.util.Arrays.asList(priority.split(",")));
            } else {
                wrapper.eq("priority", priority);
            }
        }
        if (assigneeId != null) wrapper.eq("assignee_id", assigneeId);
        if (reporterId != null) wrapper.eq("reporter_id", reporterId);
        if (sprintId != null) wrapper.eq("sprint_id", sprintId);
        if (issueType != null) {
            if (issueType.contains(",")) {
                wrapper.in("issue_type", java.util.Arrays.asList(issueType.split(",")));
            } else {
                wrapper.eq("issue_type", issueType);
            }
        }

        // Negative filters
        if (statusIdNot != null && !statusIdNot.isBlank()) {
            List<Long> notIds = java.util.Arrays.stream(statusIdNot.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
            wrapper.notIn("status_id", notIds);
        }
        if (priorityNot != null && !priorityNot.isBlank()) {
            wrapper.notIn("priority", java.util.Arrays.asList(priorityNot.split(",")));
        }
        if (assigneeIdNot != null && !assigneeIdNot.isBlank()) {
            List<Long> notIds = java.util.Arrays.stream(assigneeIdNot.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
            wrapper.notIn("assignee_id", notIds);
        }
        if (sprintIdNot != null && !sprintIdNot.isBlank()) {
            List<Long> notIds = java.util.Arrays.stream(sprintIdNot.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
            wrapper.notIn("sprint_id", notIds);
        }
        if (issueTypeNot != null && !issueTypeNot.isBlank()) {
            wrapper.notIn("issue_type", java.util.Arrays.asList(issueTypeNot.split(",")));
        }

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

    // ========== 增强详情（性能优化：单次 JOIN 查询） ==========

    /**
     * 获取增强版 Issue 详情 —— 单次 SQL JOIN 替代 N+1 查询
     */
    public IssueDetailVO getDetail(Long id) {
        Map<String, Object> row = issueMapper.selectDetailById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }

        IssueDetailVO vo = new IssueDetailVO();
        vo.setId(String.valueOf(row.get("id")));
        vo.setProjectId(String.valueOf(row.get("project_id")));
        vo.setProjectName((String) row.get("project_name"));
        vo.setIssueKey((String) row.get("issue_key"));
        vo.setTitle((String) row.get("title"));
        vo.setDescription((String) row.get("description"));
        vo.setIssueType((String) row.get("issue_type"));
        vo.setStatusId(String.valueOf(row.get("status_id")));
        vo.setPriority((String) row.get("priority"));
        vo.setAssigneeId(row.get("assignee_id") != null ? String.valueOf(row.get("assignee_id")) : null);
        vo.setAssigneeName((String) row.get("assignee_name"));
        vo.setReporterId(String.valueOf(row.get("reporter_id")));
        vo.setReporterName((String) row.get("reporter_name"));
        vo.setSprintId(row.get("sprint_id") != null ? String.valueOf(row.get("sprint_id")) : null);
        vo.setSprintName((String) row.get("sprint_name"));
        vo.setParentId(row.get("parent_id") != null ? String.valueOf(row.get("parent_id")) : null);
        vo.setParentKey((String) row.get("parent_key"));
        vo.setCustomFields((String) row.get("custom_fields"));

        if (row.get("due_date") != null) {
            vo.setDueDate(((java.sql.Date) row.get("due_date")).toLocalDate());
        }
        if (row.get("estimated_hours") != null) {
            vo.setEstimatedHours((java.math.BigDecimal) row.get("estimated_hours"));
        }
        if (row.get("spent_hours") != null) {
            vo.setSpentHours((java.math.BigDecimal) row.get("spent_hours"));
        }
        if (row.get("resolved_at") != null) {
            vo.setResolvedAt(((java.sql.Timestamp) row.get("resolved_at")).toLocalDateTime());
        }
        if (row.get("created_at") != null) {
            vo.setCreatedAt(((java.sql.Timestamp) row.get("created_at")).toLocalDateTime());
        }
        if (row.get("updated_at") != null) {
            vo.setUpdatedAt(((java.sql.Timestamp) row.get("updated_at")).toLocalDateTime());
        }

        // 状态对象
        if (row.get("status_name") != null) {
            IssueStatusVO statusVO = new IssueStatusVO();
            statusVO.setId(String.valueOf(row.get("status_id")));
            statusVO.setName((String) row.get("status_name"));
            statusVO.setCode((String) row.get("status_code"));
            statusVO.setColor((String) row.get("status_color"));
            statusVO.setCategory((String) row.get("status_category"));
            statusVO.setIsDefault((Boolean) row.get("status_is_default"));
            statusVO.setIsClosed((Boolean) row.get("status_is_closed"));
            vo.setStatus(statusVO);
        }

        // 标签（单独查询，因为是多对多关系）
        List<IssueTag> tags = tagService.listIssueTags(id);
        vo.setTags(issueConverter.toTagVOList(tags));

        return vo;
    }

    /**
     * 获取评论列表 —— 单次 JOIN 查询（消除 N+1）
     */
    public List<IssueCommentVO> listCommentsWithUser(Long issueId) {
        List<Map<String, Object>> rows = issueMapper.selectCommentsWithUser(issueId);
        return rows.stream().map(row -> {
            IssueCommentVO vo = new IssueCommentVO();
            vo.setId(String.valueOf(row.get("id")));
            vo.setIssueId(String.valueOf(row.get("issue_id")));
            vo.setUserId(String.valueOf(row.get("user_id")));
            vo.setUserName((String) row.get("user_name"));
            vo.setUserAvatar((String) row.get("user_avatar"));
            vo.setContent((String) row.get("content"));
            vo.setSource((String) row.get("source"));
            if (row.get("created_at") != null) vo.setCreatedAt(((java.sql.Timestamp) row.get("created_at")).toLocalDateTime());
            if (row.get("updated_at") != null) vo.setUpdatedAt(((java.sql.Timestamp) row.get("updated_at")).toLocalDateTime());
            return vo;
        }).toList();
    }

    /**
     * 获取活动列表 —— 单次 JOIN 查询（消除 N+1）
     */
    public List<IssueActivityVO> listActivitiesWithUser(Long issueId) {
        List<Map<String, Object>> rows = issueMapper.selectActivitiesWithUser(issueId);
        return rows.stream().map(row -> {
            IssueActivityVO vo = new IssueActivityVO();
            vo.setId(String.valueOf(row.get("id")));
            vo.setIssueId(String.valueOf(row.get("issue_id")));
            vo.setUserId(String.valueOf(row.get("user_id")));
            vo.setUserName((String) row.get("user_name"));
            vo.setAction((String) row.get("action"));
            vo.setFieldName((String) row.get("field_name"));
            vo.setOldValue((String) row.get("old_value"));
            vo.setNewValue((String) row.get("new_value"));
            if (row.get("created_at") != null) vo.setCreatedAt(((java.sql.Timestamp) row.get("created_at")).toLocalDateTime());
            return vo;
        }).toList();
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
