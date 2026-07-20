package com.trackflow.system.controller;

import com.trackflow.common.model.R;
import com.trackflow.system.dto.GlobalMemberDTO;
import com.trackflow.system.entity.GlobalMember;
import com.trackflow.system.service.GlobalMemberService;
import com.trackflow.system.vo.GlobalMemberVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 全局项目角色分配管理接口。
 * <p>
 * 允许系统管理员将项目级角色（如"观察者""开发人员"）以全局作用域分配给用户，
 * 使其在所有现有项目和未来新建项目中自动拥有该角色。
 */
@RestController
@RequestMapping("/api/v1/global-members")
@RequiredArgsConstructor
public class GlobalMemberController {

    private final GlobalMemberService globalMemberService;

    /**
     * 分配全局项目角色
     */
    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<GlobalMemberVO> assign(@Valid @RequestBody GlobalMemberDTO dto) {
        GlobalMember gm = globalMemberService.assign(dto);
        // 返回简化的 VO
        GlobalMemberVO vo = new GlobalMemberVO();
        vo.setId(String.valueOf(gm.getId()));
        vo.setUserId(String.valueOf(gm.getUserId()));
        vo.setRoleId(String.valueOf(gm.getRoleId()));
        vo.setCreatedAt(gm.getCreatedAt());
        return R.ok(vo);
    }

    /**
     * 撤销全局项目角色
     */
    @DeleteMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<Void> revoke(@RequestParam("userId") Long userId, @RequestParam("roleId") Long roleId) {
        globalMemberService.revoke(userId, roleId);
        return R.ok();
    }

    /**
     * 列出所有全局分配记录
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<List<GlobalMemberVO>> listAll() {
        return R.ok(globalMemberService.listAll());
    }

    /**
     * 列出指定用户的全局角色分配
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<List<GlobalMemberVO>> listByUser(@PathVariable("userId") Long userId) {
        return R.ok(globalMemberService.listByUser(userId));
    }
}
