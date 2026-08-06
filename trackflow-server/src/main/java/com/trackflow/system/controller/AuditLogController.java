package com.trackflow.system.controller;

import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.system.dto.AuditLogQuery;
import com.trackflow.system.service.SystemAuditService;
import com.trackflow.system.vo.AuditLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 系统审计日志接口
 * <p>
 * 仅系统管理员可访问，审计日志只读不可删除（由定时任务按保留策略自动清理）。
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final SystemAuditService systemAuditService;

    /**
     * 分页查询审计日志
     * <p>
     * 支持按操作类型、目标类型、操作者、时间范围筛选。
     * 支持 search 参数进行文本搜索（author:xxx / target:xxx / 纯文本模糊匹配）。
     * 时间范围强制限制：最大 365 天，未指定时默认最近 30 天。
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<PageResult<AuditLogVO>> list(AuditLogQuery query) {
        return R.ok(systemAuditService.list(query));
    }

    /**
     * 导出审计日志（CSV 格式）
     * <p>
     * 必须指定起止日期，时间范围不超过 365 天。
     *
     * @param startDate 开始日期（含）
     * @param endDate   结束日期（含）
     */
    @GetMapping("/export")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        String csvContent = systemAuditService.exportAuditLogsCsv(startDate, endDate);

        // BOM + UTF-8 确保 Excel 正确识别中文
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(csvBytes, 0, result, bom.length, csvBytes.length);

        String filename = "audit-log-" + startDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + endDate.format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(result);
    }

    /**
     * 导出审计日志（JSON 格式）
     * <p>
     * 按当前查询条件筛选，最多导出 1000 条记录。
     * 支持所有列表接口的筛选参数（action/targetType/startDate/endDate/search）。
     */
    @GetMapping("/export-json")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public ResponseEntity<byte[]> exportJson(AuditLogQuery query) {
        String jsonContent = systemAuditService.exportAuditLogsJson(query);
        byte[] jsonBytes = jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        LocalDate today = LocalDate.now();
        String filename = "audit-logs-" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + ".json";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBytes);
    }
}
