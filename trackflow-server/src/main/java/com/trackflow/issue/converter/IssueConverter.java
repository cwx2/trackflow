package com.trackflow.issue.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.issue.entity.*;
import com.trackflow.issue.vo.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "statusId", expression = "java(longToString(entity.getStatusId()))")
    @Mapping(target = "assigneeId", expression = "java(longToString(entity.getAssigneeId()))")
    @Mapping(target = "reporterId", expression = "java(longToString(entity.getReporterId()))")
    @Mapping(target = "sprintId", expression = "java(longToString(entity.getSprintId()))")
    @Mapping(target = "assigneeName", ignore = true)
    IssueVO toVO(Issue entity);

    List<IssueVO> toVOList(List<Issue> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "statusId", expression = "java(longToString(entity.getStatusId()))")
    @Mapping(target = "assigneeId", expression = "java(longToString(entity.getAssigneeId()))")
    @Mapping(target = "reporterId", expression = "java(longToString(entity.getReporterId()))")
    @Mapping(target = "sprintId", expression = "java(longToString(entity.getSprintId()))")
    @Mapping(target = "parentId", expression = "java(longToString(entity.getParentId()))")
    @Mapping(target = "assigneeName", ignore = true)
    @Mapping(target = "projectName", ignore = true)
    @Mapping(target = "reporterName", ignore = true)
    @Mapping(target = "sprintName", ignore = true)
    @Mapping(target = "parentKey", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "tags", ignore = true)
    IssueDetailVO toDetailVO(Issue entity);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "issueId", expression = "java(longToString(entity.getIssueId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "userAvatar", ignore = true)
    IssueCommentVO toCommentVO(IssueComment entity);

    List<IssueCommentVO> toCommentVOList(List<IssueComment> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "issueId", expression = "java(longToString(entity.getIssueId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    @Mapping(target = "userName", ignore = true)
    IssueActivityVO toActivityVO(IssueActivity entity);

    List<IssueActivityVO> toActivityVOList(List<IssueActivity> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    IssueStatusVO toStatusVO(IssueStatus entity);

    List<IssueStatusVO> toStatusVOList(List<IssueStatus> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "issueId", expression = "java(longToString(entity.getIssueId()))")
    @Mapping(target = "uploadedBy", expression = "java(longToString(entity.getUploadedBy()))")
    IssueAttachmentVO toAttachmentVO(IssueAttachment entity);

    List<IssueAttachmentVO> toAttachmentVOList(List<IssueAttachment> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    IssueTagVO toTagVO(IssueTag entity);

    List<IssueTagVO> toTagVOList(List<IssueTag> entities);
}
