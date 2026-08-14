package com.trackflow.issuetemplate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issuetemplate.converter.IssueTemplateConverter;
import com.trackflow.issuetemplate.dto.SaveIssueTemplateDTO;
import com.trackflow.issuetemplate.entity.IssueTemplate;
import com.trackflow.issuetemplate.mapper.IssueTemplateMapper;
import com.trackflow.issuetemplate.vo.IssueTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工单模板 Service
 */
@Service
@RequiredArgsConstructor
public class IssueTemplateService {

    private final IssueTemplateMapper templateMapper;
    private final IssueTemplateConverter converter;

    /**
     * 查询项目下所有可用模板（按 sortOrder 排序）
     */
    public List<IssueTemplateVO> listByProject(Long projectId) {
        List<IssueTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<IssueTemplate>()
                        .eq(IssueTemplate::getProjectId, projectId)
                        .eq(IssueTemplate::getDeleted, false)
                        .orderByAsc(IssueTemplate::getSortOrder)
                        .orderByAsc(IssueTemplate::getId)
        );
        return converter.toVOList(templates);
    }

    /**
     * 获取单个模板详情
     */
    public IssueTemplateVO getById(Long id) {
        IssueTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeleted()) {
            throw BusinessException.notFound("模板不存在");
        }
        return converter.toVO(template);
    }

    /**
     * 创建模板
     */
    @Transactional
    public IssueTemplateVO create(SaveIssueTemplateDTO dto) {
        // 检查同项目下名称唯一性
        checkNameUnique(dto.getProjectId(), dto.getName(), null);

        IssueTemplate entity = converter.toEntity(dto);
        entity.setIsSystem(false);
        entity.setCreatedBy(SecurityUtils.getCurrentUserId());

        if (entity.getSortOrder() == null) {
            // 默认排到最后
            Long count = templateMapper.selectCount(
                    new LambdaQueryWrapper<IssueTemplate>()
                            .eq(IssueTemplate::getProjectId, dto.getProjectId())
                            .eq(IssueTemplate::getDeleted, false)
            );
            entity.setSortOrder(count.intValue() + 1);
        }

        templateMapper.insert(entity);
        return converter.toVO(entity);
    }

    /**
     * 更新模板
     */
    @Transactional
    public IssueTemplateVO update(Long id, SaveIssueTemplateDTO dto) {
        IssueTemplate entity = templateMapper.selectById(id);
        if (entity == null || entity.getDeleted()) {
            throw BusinessException.notFound("模板不存在");
        }
        if (entity.getIsSystem()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "系统预置模板不允许修改名称和类型");
        }

        // 检查名称唯一性（排除自身）
        checkNameUnique(entity.getProjectId(), dto.getName(), id);

        converter.updateEntity(dto, entity);
        templateMapper.updateById(entity);
        return converter.toVO(entity);
    }

    /**
     * 删除模板（软删除）
     */
    @Transactional
    public void delete(Long id) {
        IssueTemplate entity = templateMapper.selectById(id);
        if (entity == null || entity.getDeleted()) {
            throw BusinessException.notFound("模板不存在");
        }
        if (entity.getIsSystem()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "系统预置模板不允许删除");
        }

        entity.setDeleted(true);
        templateMapper.updateById(entity);
    }

    /**
     * 检查同项目下模板名称唯一性
     */
    private void checkNameUnique(Long projectId, String name, Long excludeId) {
        LambdaQueryWrapper<IssueTemplate> qw = new LambdaQueryWrapper<IssueTemplate>()
                .eq(IssueTemplate::getProjectId, projectId)
                .eq(IssueTemplate::getName, name)
                .eq(IssueTemplate::getDeleted, false);
        if (excludeId != null) {
            qw.ne(IssueTemplate::getId, excludeId);
        }
        if (templateMapper.selectCount(qw) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "该项目下已存在同名模板");
        }
    }
}
