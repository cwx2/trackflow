package com.trackflow.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.system.converter.OrgConverter;
import com.trackflow.system.dto.CreateOrgDTO;
import com.trackflow.system.dto.UpdateOrgDTO;
import com.trackflow.system.entity.Organization;
import com.trackflow.system.service.OrganizationService;
import com.trackflow.system.vo.OrgVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 组织管理接口
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
        return R.ok(orgConverter.toVO(org));
    }

    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<PageResult<OrgVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "sort", required = false) String sort) {

        Page<Organization> pageObj = PageHelper.buildPage(page, pageSize, sort);
        Page<Organization> result = organizationService.list(pageObj, keyword);

        PageResult<OrgVO> pageResult = new PageResult<>(
                orgConverter.toVOList(result.getRecords()), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<OrgVO> getById(@PathVariable Long id) {
        return R.ok(orgConverter.toVO(organizationService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<OrgVO> update(@PathVariable Long id, @Valid @RequestBody UpdateOrgDTO dto) {
        return R.ok(orgConverter.toVO(organizationService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_orgs')")
    public R<Void> delete(@PathVariable Long id) {
        organizationService.delete(id);
        return R.ok();
    }
}
