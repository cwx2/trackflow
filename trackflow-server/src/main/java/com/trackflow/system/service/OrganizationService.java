package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.system.dto.CreateOrgDTO;
import com.trackflow.system.dto.UpdateOrgDTO;
import com.trackflow.system.entity.Organization;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.OrganizationMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织管理服务
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationMapper organizationMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectMapper projectMapper;

    /**
     * 创建组织
     */
    @Transactional(rollbackFor = Exception.class)
    public Organization create(CreateOrgDTO dto) {
        // 检查 code 唯一性
        Long count = organizationMapper.selectCount(
                new LambdaQueryWrapper<Organization>().eq(Organization::getCode, dto.getCode())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.ORG_CODE_DUPLICATE);
        }

        Organization org = new Organization();
        org.setName(dto.getName());
        org.setCode(dto.getCode());
        org.setDescription(dto.getDescription());
        organizationMapper.insert(org);
        return org;
    }

    /**
     * 分页查询组织列表
     */
    public Page<Organization> list(Page<Organization> page, String keyword) {
        LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Organization::getName, keyword)
                    .or()
                    .like(Organization::getCode, keyword);
        }
        wrapper.orderByAsc(Organization::getCode);
        return organizationMapper.selectPage(page, wrapper);
    }

    /**
     * 获取组织详情
     */
    public Organization getById(Long id) {
        Organization org = organizationMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Organization not found");
        }
        return org;
    }

    /**
     * 更新组织
     */
    @Transactional(rollbackFor = Exception.class)
    public Organization update(Long id, UpdateOrgDTO dto) {
        Organization org = getById(id);
        if (dto.getName() != null) {
            String trimmedName = dto.getName().trim();
            if (trimmedName.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "组织名称不能为空");
            }
            org.setName(trimmedName);
        }
        if (dto.getDescription() != null) {
            org.setDescription(dto.getDescription().trim());
        }
        organizationMapper.updateById(org);
        return org;
    }

    /**
     * 删除组织（有引用时拒绝）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Organization org = getById(id);

        // 检查是否有用户引用
        Long userCount = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getOrgId, id)
        );
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.ORG_HAS_REFERENCES);
        }

        // 检查是否有项目引用
        Long projectCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getOrgId, id)
        );
        if (projectCount > 0) {
            throw new BusinessException(ErrorCode.ORG_HAS_REFERENCES, "该组织下还有项目关联，无法删除");
        }

        organizationMapper.deleteById(id);
    }
}
