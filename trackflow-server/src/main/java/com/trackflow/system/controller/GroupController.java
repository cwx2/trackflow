package com.trackflow.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.security.NoAuthorizationRequired;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.system.dto.CreateGroupDTO;
import com.trackflow.system.dto.GroupMembersDTO;
import com.trackflow.system.dto.GroupRoleDTO;
import com.trackflow.system.dto.UpdateGroupDTO;
import com.trackflow.system.entity.UserGroup;
import com.trackflow.system.service.UserGroupService;
import com.trackflow.system.vo.UserGroupDetailVO;
import com.trackflow.system.vo.UserGroupVO;
import com.trackflow.system.vo.GroupSimpleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 用户组管理接口
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final UserGroupService groupService;

    /**
     * 查询所有用户组简要信息（id + name），用于下拉选择器（如评论可见性）。
     * 不需要管理权限，任何已认证用户都可访问。
     */
    @NoAuthorizationRequired(reason = "下拉选择器数据，任何已认证用户可访问")
    @GetMapping("/simple")
    public R<List<GroupSimpleVO>> listSimple() {
        return R.ok(groupService.listSimple());
    }

    /**
     * 分页查询用户组列表
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_groups')")
    public R<PageResult<UserGroupVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        Page<UserGroup> pageObj = PageHelper.buildPage(page, pageSize, null,
                Set.of("id", "name", "created_at"));
        Page<UserGroupVO> result = groupService.list(pageObj, keyword);

        PageResult<UserGroupVO> pageResult = new PageResult<>(
                result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    /**
     * 获取用户组详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_groups')")
    public R<UserGroupDetailVO> getDetail(@PathVariable("id") Long id) {
        return R.ok(groupService.getDetail(id));
    }

    /**
     * 创建用户组
     */
    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_groups')")
    public R<UserGroupVO> create(@Valid @RequestBody CreateGroupDTO dto) {
        UserGroup group = groupService.create(dto);
        // 返回包含统计信息的 VO
        return R.ok(groupService.getDetail(group.getId()) != null ?
                toSimpleVO(groupService.getDetail(group.getId())) : null);
    }

    /**
     * 更新用户组
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_groups')")
    public R<Void> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateGroupDTO dto) {
        groupService.update(id, dto);
        return R.ok();
    }

    /**
     * 删除用户组
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_groups')")
    public R<Void> delete(@PathVariable("id") Long id) {
        groupService.delete(id);
        return R.ok();
    }

    /**
     * 添加成员到组
     */
    @PostMapping("/{id}/members")
    @PreAuthorize("@perm.checkGlobal('system:manage_groups')")
    public R<Void> addMembers(@PathVariable("id") Long id, @Valid @RequestBody GroupMembersDTO dto) {
        groupService.addMembers(id, dto.getUserIds());
        return R.ok();
    }

    /**
     * 从组中移除成员
     */
    @DeleteMapping("/{id}/members")
    @PreAuthorize("@perm.checkGlobal('system:manage_groups')")
    public R<Void> removeMembers(@PathVariable("id") Long id, @Valid @RequestBody GroupMembersDTO dto) {
        groupService.removeMembers(id, dto.getUserIds());
        return R.ok();
    }

    /**
     * 为组分配角色
     */
    @PostMapping("/{id}/roles")
    @PreAuthorize("@perm.checkGlobal('system:manage_groups')")
    public R<Void> assignRole(@PathVariable("id") Long id, @Valid @RequestBody GroupRoleDTO dto) {
        groupService.assignRole(id, dto);
        return R.ok();
    }

    /**
     * 移除组的角色分配
     */
    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("@perm.checkGlobal('system:manage_groups')")
    public R<Void> removeRole(@PathVariable("id") Long id, @PathVariable("roleId") Long roleId) {
        groupService.removeRole(id, roleId);
        return R.ok();
    }

    private UserGroupVO toSimpleVO(UserGroupDetailVO detail) {
        UserGroupVO vo = new UserGroupVO();
        vo.setId(detail.getId());
        vo.setName(detail.getName());
        vo.setDescription(detail.getDescription());
        vo.setMemberCount(detail.getMembers() != null ? detail.getMembers().size() : 0);
        vo.setRoleCount(detail.getRoles() != null ? detail.getRoles().size() : 0);
        vo.setCreatedAt(detail.getCreatedAt());
        vo.setUpdatedAt(detail.getUpdatedAt());
        return vo;
    }
}
