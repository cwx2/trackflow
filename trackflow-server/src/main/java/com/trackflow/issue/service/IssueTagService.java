package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.dto.CreateTagDTO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.mapper.IssueTagMapper;
import com.trackflow.issue.mapper.IssueTagRelationMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueTagService {

    private final IssueTagMapper tagMapper;
    private final IssueTagRelationMapper tagRelationMapper;
    private final IssueMapper issueMapper;
    private final ProjectService projectService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 获取项目下所有标签
     */
    public List<IssueTag> listProjectTags(Long projectId) {
        return tagMapper.selectList(
                new LambdaQueryWrapper<IssueTag>()
                        .eq(IssueTag::getProjectId, projectId)
                        .orderByAsc(IssueTag::getName)
        );
    }

    /**
     * 创建标签
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueTag createTag(Long projectId, CreateTagDTO dto) {
        // 归档项目不允许创建标签
        projectService.assertProjectActive(projectId);

        // 检查同名标签是否已存在
        Long exists = tagMapper.selectCount(
                new LambdaQueryWrapper<IssueTag>()
                        .eq(IssueTag::getProjectId, projectId)
                        .eq(IssueTag::getName, dto.getName())
        );
        if (exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名称已存在");
        }

        IssueTag tag = new IssueTag();
        tag.setProjectId(projectId);
        tag.setName(dto.getName());
        tag.setColor(dto.getColor() != null ? dto.getColor() : "#808080");
        tag.setCreatedBy(SecurityUtils.getCurrentUserId());
        tag.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(tag);
        return tag;
    }

    /**
     * 获取 Issue 的标签列表
     */
    public List<IssueTag> listIssueTags(Long issueId) {
        List<IssueTagRelation> relations = tagRelationMapper.selectList(
                new LambdaQueryWrapper<IssueTagRelation>()
                        .eq(IssueTagRelation::getIssueId, issueId)
        );
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = relations.stream().map(IssueTagRelation::getTagId).toList();
        return tagMapper.selectBatchIds(tagIds);
    }

    /**
     * 为 Issue 添加标签
     */
    @Transactional(rollbackFor = Exception.class)
    public void addTagToIssue(Long issueId, Long tagId) {
        assertIssueProjectActive(issueId);

        // 检查标签是否存在
        IssueTag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "标签不存在");
        }

        // 检查是否已关联
        Long exists = tagRelationMapper.selectCount(
                new LambdaQueryWrapper<IssueTagRelation>()
                        .eq(IssueTagRelation::getIssueId, issueId)
                        .eq(IssueTagRelation::getTagId, tagId)
        );
        if (exists > 0) {
            return; // 已存在，幂等处理
        }

        IssueTagRelation relation = new IssueTagRelation();
        relation.setIssueId(issueId);
        relation.setTagId(tagId);
        relation.setCreatedAt(LocalDateTime.now());
        tagRelationMapper.insert(relation);

        // 通知报告人和负责人标签变更
        Issue issue = issueMapper.selectById(issueId);
        if (issue != null) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            eventPublisher.publishEvent(new IssueNotificationEvent.FieldUpdated(
                    issue, "tags", null, tag.getName(), currentUserId));
        }
    }

    /**
     * 移除 Issue 上的标签
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeTagFromIssue(Long issueId, Long tagId) {
        assertIssueProjectActive(issueId);

        // 先获取标签名用于通知
        IssueTag tag = tagMapper.selectById(tagId);

        tagRelationMapper.delete(
                new LambdaQueryWrapper<IssueTagRelation>()
                        .eq(IssueTagRelation::getIssueId, issueId)
                        .eq(IssueTagRelation::getTagId, tagId)
        );

        // 通知报告人和负责人标签移除
        Issue issue = issueMapper.selectById(issueId);
        if (issue != null && tag != null) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            eventPublisher.publishEvent(new IssueNotificationEvent.FieldUpdated(
                    issue, "tags", tag.getName(), null, currentUserId));
        }
    }

    /**
     * 校验 Issue 所属项目未归档
     */
    private void assertIssueProjectActive(Long issueId) {
        var issue = issueMapper.selectById(issueId);
        if (issue != null) {
            projectService.assertProjectActive(issue.getProjectId());
        }
    }
}
