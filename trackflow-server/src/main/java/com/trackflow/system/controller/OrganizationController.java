package com.trackflow.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.system.converter.OrgConverter;
import com.trackflow.system.dto.AddProjectsToOrgDTO;
import com.trackflow.system.dto.CreateOrgDTO;
import com.trackflow.system.dto.GrantOrgAccessDTO;
import com.trackflow.system.dto.UpdateOrgDTO;
import com.trackflow.system.entity.Organization;
import com.trackflow.system.service.OrganizationService;
import com.trackflow.system.vo.OrgAccessVO;
import com.trackflow.system.vo.OrgDetailVO;
import com.trackflow.system.vo.OrgProjectVO;
import com.trackflow.system.vo.OrgVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织管理接口 - CRUD + 项目归属 + 访问控制
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrgConverter orgConverter;

    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<OrgVO> create(@Valid @RequestBody CreateOrgDTO dto) {
        Organization org = organizationService.create(dto);
        OrgVO vo = orgConverter.toVO(org);
        vo.setProjectCount(organizationService.getProjectCount(org.getId()));
        return R.ok(vo);
    }

    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<PageResult<OrgVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "sort", required = false) String sort) {

        Page<Organization> pageObj = PageHelper.buildPage(page, pageSize, sort,
                Set.of("id", "name", "code", "created_at", "updated_at"));
        Page<Organization> result = organizationService.list(pageObj, keyword);

        List<OrgVO> voList = orgConverter.toVOList(result.getRecords());

        // 批量填充项目数量
        List<Long> orgIds = result.getRecords().stream().map(Organization::getId).collect(Collectors.toList());
        Map<Long, Integer> countMap = organizationService.getProjectCountMap(orgIds);
        for (int i = 0; i < voList.size(); i++) {
            Long orgId = result.getRecords().get(i).getId();
            voList.get(i).setProjectCount(countMap.getOrDefault(orgId, 0));
        }

        PageResult<OrgVO> pageResult = new PageResult<>(
                voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<OrgDetailVO> getById(@PathVariable("id") Long id) {
        Organization org = organizationService.getById(id);
        OrgDetailVO vo = orgConverter.toDetailVO(org);
        vo.setProjectCount(organizationService.getProjectCount(id));
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<OrgVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateOrgDTO dto) {
        Organization org = organizationService.update(id, dto);
        OrgVO vo = orgConverter.toVO(org);
        vo.setProjectCount(organizationService.getProjectCount(id));
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<Void> delete(@PathVariable("id") Long id) {
        organizationService.delete(id);
        return R.ok();
    }

    // ===== 项目归属管理 =====

    @GetMapping("/{id}/projects")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<List<OrgProjectVO>> getOrgProjects(@PathVariable("id") Long id) {
        return R.ok(organizationService.getOrgProjects(id));
    }

    @PostMapping("/{id}/projects")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<Void> addProjectsToOrg(@PathVariable("id") Long id, @Valid @RequestBody AddProjectsToOrgDTO dto) {
        organizationService.addProjectsToOrg(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<Void> removeProjectFromOrg(@PathVariable("id") Long id, @PathVariable("projectId") Long projectId) {
        organizationService.removeProjectFromOrg(id, projectId);
        return R.ok();
    }

    @GetMapping("/unassigned-projects")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<List<OrgProjectVO>> getUnassignedProjects() {
        return R.ok(organizationService.getUnassignedProjects());
    }

    // ===== 访问控制 =====

    @GetMapping("/{id}/access")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<List<OrgAccessVO>> getOrgAccessList(@PathVariable("id") Long id) {
        return R.ok(organizationService.getOrgAccessList(id));
    }

    @PostMapping("/{id}/access")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<OrgAccessVO> grantOrgAccess(@PathVariable("id") Long id, @Valid @RequestBody GrantOrgAccessDTO dto) {
        return R.ok(organizationService.grantOrgAccess(id, dto));
    }

    @DeleteMapping("/{id}/access/{accessId}")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<Void> revokeOrgAccess(@PathVariable("id") Long id, @PathVariable("accessId") Long accessId) {
        organizationService.revokeOrgAccess(id, accessId);
        return R.ok();
    }
}
