package com.trackflow.external.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.external.dto.IntegrationLogQuery;
import com.trackflow.external.dto.ToggleAdapterDTO;
import com.trackflow.external.dto.UpdateIntegrationConfigDTO;
import com.trackflow.external.service.IntegrationAdminService;
import com.trackflow.external.vo.IntegrationAdapterVO;
import com.trackflow.external.vo.IntegrationConfigVO;
import com.trackflow.external.vo.IntegrationLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 第三方集成管理 Controller。
 * <p>
 * 仅 system_admin 可访问。提供适配器列表、启用/禁用、配置管理、集成日志查看和重试功能。
 */
@RestController
@RequestMapping("/api/v1/admin/integrations")
@RequiredArgsConstructor
public class IntegrationAdminController {

    private final IntegrationAdminService integrationAdminService;

    /**
     * 列出所有已注册的第三方适配器及其状态
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<List<IntegrationAdapterVO>> listAdapters() {
        return R.ok(integrationAdminService.listAdapters());
    }

    /**
     * 启用或禁用适配器
     */
    @PutMapping("/{adapterType}/toggle")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Void> toggleAdapter(
            @PathVariable("adapterType") String adapterType,
            @Valid @RequestBody ToggleAdapterDTO dto) {
        integrationAdminService.toggleAdapter(adapterType, dto.getEnabled());
        return R.ok();
    }

    /**
     * 获取适配器配置
     */
    @GetMapping("/{adapterType}/config")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<IntegrationConfigVO> getConfig(@PathVariable("adapterType") String adapterType) {
        return R.ok(integrationAdminService.getConfig(adapterType));
    }

    /**
     * 更新适配器配置
     */
    @PutMapping("/{adapterType}/config")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<IntegrationConfigVO> updateConfig(
            @PathVariable("adapterType") String adapterType,
            @Valid @RequestBody UpdateIntegrationConfigDTO dto) {
        return R.ok(integrationAdminService.updateConfig(adapterType, dto));
    }

    /**
     * 查询集成事件日志（分页 + 筛选）
     */
    @GetMapping("/logs")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<PageResult<IntegrationLogVO>> queryLogs(IntegrationLogQuery query) {
        Page<IntegrationLogVO> page = integrationAdminService.queryLogs(query);
        return R.ok(new PageResult<>(
                page.getRecords(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize()
        ));
    }

    /**
     * 手动重试失败的事件
     */
    @PostMapping("/logs/{id}/retry")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<IntegrationLogVO> retryEvent(@PathVariable("id") Long id) {
        return R.ok(integrationAdminService.retryEvent(id));
    }
}
