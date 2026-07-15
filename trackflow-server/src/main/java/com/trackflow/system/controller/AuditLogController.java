package com.trackflow.system.controller;

import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.system.dto.AuditLogQuery;
import com.trackflow.system.service.SystemAuditService;
import com.trackflow.system.vo.AuditLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统审计日志接口
 * <p>
 * 仅系统管理员可访问，审计日志只读不可删除。
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final SystemAuditService systemAuditService;

    /**
     * 分页查询审计日志
     * 支持按操作类型、目标类型、操作者、时间范围筛选
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<PageResult<AuditLogVO>> list(AuditLogQuery query) {
        return R.ok(systemAuditService.list(query));
    }
}
