package com.trackflow.issue.controller;

import com.trackflow.common.model.R;
import com.trackflow.issue.service.IssueLinkTypeService;
import com.trackflow.issue.vo.IssueLinkTypeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单关联类型管理 Controller - 管理员 CRUD 操作
 * 参考 YouTrack Administration > Link Types 管理功能
 *
 * @author TrackFlow
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/link-types")
@RequiredArgsConstructor
public class IssueLinkTypeController {

    private final IssueLinkTypeService linkTypeService;

    /**
     * 获取所有链接类型
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<List<IssueLinkTypeVO>> list() {
        return R.ok(linkTypeService.listAllVO());
    }

    /**
     * 创建链接类型
     */
    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<IssueLinkTypeVO> create(@Valid @RequestBody CreateLinkTypeDTO dto) {
        IssueLinkTypeVO vo = linkTypeService.createLinkType(
                dto.getName(), dto.getOutwardName(), dto.getInwardName(), dto.getDirection());
        return R.ok(vo);
    }

    /**
     * 更新链接类型
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<IssueLinkTypeVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateLinkTypeDTO dto) {
        IssueLinkTypeVO vo = linkTypeService.updateLinkType(
                id, dto.getOutwardName(), dto.getInwardName(), dto.getDirection());
        return R.ok(vo);
    }

    /**
     * 删除链接类型
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<Void> delete(@PathVariable("id") Long id) {
        linkTypeService.deleteLinkType(id);
        return R.ok();
    }

    // ========== DTO 定义 ==========

    @Data
    public static class CreateLinkTypeDTO {
        @NotBlank(message = "名称不能为空")
        private String name;
        @NotBlank(message = "正向显示名不能为空")
        private String outwardName;
        @NotBlank(message = "反向显示名不能为空")
        private String inwardName;
        @NotBlank(message = "方向类型不能为空")
        private String direction;
    }

    @Data
    public static class UpdateLinkTypeDTO {
        @NotBlank(message = "正向显示名不能为空")
        private String outwardName;
        @NotBlank(message = "反向显示名不能为空")
        private String inwardName;
        @NotBlank(message = "方向类型不能为空")
        private String direction;
    }
}
