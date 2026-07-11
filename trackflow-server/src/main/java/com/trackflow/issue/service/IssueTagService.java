package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.dto.CreateTagDTO;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.mapper.IssueTagMapper;
import com.trackflow.issue.mapper.IssueTagRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @Transactional
    public IssueTag createTag(Long projectId, CreateTagDTO dto) {
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
    @Transactional
    public void addTagToIssue(Long issueId, Long tagId) {
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
    }

    /**
     * 移除 Issue 上的标签
     */
    @Transactional
    public void removeTagFromIssue(Long issueId, Long tagId) {
        tagRelationMapper.delete(
                new LambdaQueryWrapper<IssueTagRelation>()
                        .eq(IssueTagRelation::getIssueId, issueId)
                        .eq(IssueTagRelation::getTagId, tagId)
        );
    }
}
