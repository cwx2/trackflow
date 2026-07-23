package com.trackflow.system.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户数据导出 VO — 包含用户的所有个人数据
 * 用于满足 GDPR 数据可携权（Right to Data Portability）要求
 */
public class UserDataExportVO {

    private String exportDate;
    private String exportedBy;
    private UserBasicInfo userInfo;
    private List<String> globalRoles;
    private List<ProjectMembership> projectMemberships;
    private List<IssueData> createdIssues;
    private List<IssueData> assignedIssues;
    private List<CommentData> comments;
    private List<ActivityData> activities;
    private List<AttachmentData> attachments;

    // --- 内部类 ---

    public static class UserBasicInfo {
        private String id;
        private String username;
        private String displayName;
        private String email;
        private String phone;
        private String avatarUrl;
        private String status;
        private String banStatus;
        private String banReason;
        private LocalDateTime bannedAt;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getBanStatus() { return banStatus; }
        public void setBanStatus(String banStatus) { this.banStatus = banStatus; }
        public String getBanReason() { return banReason; }
        public void setBanReason(String banReason) { this.banReason = banReason; }
        public LocalDateTime getBannedAt() { return bannedAt; }
        public void setBannedAt(LocalDateTime bannedAt) { this.bannedAt = bannedAt; }
        public LocalDateTime getLastLoginAt() { return lastLoginAt; }
        public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class ProjectMembership {
        private String projectId;
        private String projectName;
        private String projectKey;
        private String roleName;
        private LocalDateTime joinedAt;

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        public String getProjectKey() { return projectKey; }
        public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public LocalDateTime getJoinedAt() { return joinedAt; }
        public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    }

    public static class IssueData {
        private String id;
        private String issueKey;
        private String title;
        private String issueType;
        private String priority;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getIssueKey() { return issueKey; }
        public void setIssueKey(String issueKey) { this.issueKey = issueKey; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getIssueType() { return issueType; }
        public void setIssueType(String issueType) { this.issueType = issueType; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class CommentData {
        private String id;
        private String issueId;
        private String content;
        private String source;
        private LocalDateTime createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getIssueId() { return issueId; }
        public void setIssueId(String issueId) { this.issueId = issueId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class ActivityData {
        private String id;
        private String issueId;
        private String action;
        private String fieldName;
        private String oldValue;
        private String newValue;
        private LocalDateTime createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getIssueId() { return issueId; }
        public void setIssueId(String issueId) { this.issueId = issueId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getOldValue() { return oldValue; }
        public void setOldValue(String oldValue) { this.oldValue = oldValue; }
        public String getNewValue() { return newValue; }
        public void setNewValue(String newValue) { this.newValue = newValue; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class AttachmentData {
        private String id;
        private String issueId;
        private String fileName;
        private Long fileSize;
        private String contentType;
        private LocalDateTime createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getIssueId() { return issueId; }
        public void setIssueId(String issueId) { this.issueId = issueId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    // --- 顶层 getters and setters ---
    public String getExportDate() { return exportDate; }
    public void setExportDate(String exportDate) { this.exportDate = exportDate; }
    public String getExportedBy() { return exportedBy; }
    public void setExportedBy(String exportedBy) { this.exportedBy = exportedBy; }
    public UserBasicInfo getUserInfo() { return userInfo; }
    public void setUserInfo(UserBasicInfo userInfo) { this.userInfo = userInfo; }
    public List<String> getGlobalRoles() { return globalRoles; }
    public void setGlobalRoles(List<String> globalRoles) { this.globalRoles = globalRoles; }
    public List<ProjectMembership> getProjectMemberships() { return projectMemberships; }
    public void setProjectMemberships(List<ProjectMembership> projectMemberships) { this.projectMemberships = projectMemberships; }
    public List<IssueData> getCreatedIssues() { return createdIssues; }
    public void setCreatedIssues(List<IssueData> createdIssues) { this.createdIssues = createdIssues; }
    public List<IssueData> getAssignedIssues() { return assignedIssues; }
    public void setAssignedIssues(List<IssueData> assignedIssues) { this.assignedIssues = assignedIssues; }
    public List<CommentData> getComments() { return comments; }
    public void setComments(List<CommentData> comments) { this.comments = comments; }
    public List<ActivityData> getActivities() { return activities; }
    public void setActivities(List<ActivityData> activities) { this.activities = activities; }
    public List<AttachmentData> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentData> attachments) { this.attachments = attachments; }
}
