package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.common.util.SqlUtils;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.issue.dto.IssueExportDTO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工单导出服务：支持 XLSX 和 CSV 格式导出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueExportService {

    private static final int MAX_EXPORT_LIMIT = 500;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectMapper projectMapper;
    private final SprintMapper sprintMapper;
    private final ProjectService projectService;
    private final CustomFieldService customFieldService;

    /**
     * 导出结果
     */
    public record ExportResult(byte[] content, String filename, String contentType) {}

    /**
     * 执行导出
     */
    public ExportResult export(IssueExportDTO dto) {
        String format = dto.getFormat().toLowerCase();
        if (!"xlsx".equals(format) && !"csv".equals(format)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的导出格式: " + format + "，仅支持 xlsx/csv");
        }

        // 1. 获取待导出的工单列表
        List<Issue> issues = queryIssues(dto);

        // 2. 检查数量限制
        if (issues.size() > MAX_EXPORT_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "导出数量超出限制（最大 " + MAX_EXPORT_LIMIT + " 条），当前匹配 " + issues.size() + " 条，请添加筛选条件缩小范围");
        }

        if (issues.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有符合条件的工单可导出");
        }

        // 3. 批量加载关联数据
        ExportContext ctx = buildExportContext(issues);

        // 4. 生成文件
        if ("xlsx".equals(format)) {
            return exportXlsx(issues, ctx);
        } else {
            return exportCsv(issues, ctx);
        }
    }

    /**
     * 查询待导出的工单
     */
    private List<Issue> queryIssues(IssueExportDTO dto) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 模式 A: 指定了 issueIds（选中导出）
        if (dto.getIssueIds() != null && !dto.getIssueIds().isEmpty()) {
            List<Issue> issues = issueMapper.selectBatchIds(dto.getIssueIds());
            // 过滤掉已删除的
            issues = issues.stream().filter(i -> i.getDeletedAt() == null).toList();
            // 数据隔离：只保留用户有权访问的项目中的工单
            List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(currentUserId);
            if (accessibleProjectIds != null) {
                Set<Long> accessibleSet = new HashSet<>(accessibleProjectIds);
                issues = issues.stream().filter(i -> accessibleSet.contains(i.getProjectId())).toList();
            }
            return issues;
        }

        // 模式 B: 按筛选条件查询（复用 IssueService 的筛选逻辑）
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (dto.getProjectId() != null) {
            projectService.assertProjectAccessible(currentUserId, dto.getProjectId());
            wrapper.eq("project_id", dto.getProjectId());
        } else {
            List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(currentUserId);
            if (accessibleProjectIds != null) {
                if (accessibleProjectIds.isEmpty()) {
                    return List.of();
                }
                wrapper.in("project_id", accessibleProjectIds);
            }
        }

        applyFilter(wrapper, "status_id", dto.getStatusId(), true);
        applyFilter(wrapper, "priority", dto.getPriority(), false);
        applyFilter(wrapper, "assignee_id", dto.getAssigneeId(), true);
        if (dto.getReporterId() != null) wrapper.eq("reporter_id", dto.getReporterId());
        applyFilter(wrapper, "sprint_id", dto.getSprintId(), true);
        applyFilter(wrapper, "issue_type", dto.getIssueType(), false);

        // Negative filters
        applyNegativeFilter(wrapper, "status_id", dto.getStatusIdNot(), true);
        applyNegativeFilter(wrapper, "priority", dto.getPriorityNot(), false);
        applyNegativeFilter(wrapper, "assignee_id", dto.getAssigneeIdNot(), true);
        applyNegativeFilter(wrapper, "sprint_id", dto.getSprintIdNot(), true);
        applyNegativeFilter(wrapper, "issue_type", dto.getIssueTypeNot(), false);

        // Tag filter
        if (dto.getTagId() != null && !dto.getTagId().isBlank()) {
            String tagIdValue = dto.getTagId().trim();
            if (tagIdValue.contains(",")) {
                List<Long> tagIds = Arrays.stream(tagIdValue.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .map(Long::parseLong).toList();
                wrapper.apply("EXISTS (SELECT 1 FROM issue_tag_relation itr WHERE itr.issue_id = issue.id AND itr.tag_id IN ("
                        + tagIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "))");
            } else {
                wrapper.apply("EXISTS (SELECT 1 FROM issue_tag_relation itr WHERE itr.issue_id = issue.id AND itr.tag_id = {0})",
                        Long.parseLong(tagIdValue));
            }
        }

        // Parent/child filters
        if (dto.getParentId() != null) {
            wrapper.eq("parent_id", dto.getParentId());
        }
        if ("true".equals(dto.getHasParent())) {
            wrapper.isNotNull("parent_id");
        } else if ("false".equals(dto.getHasParent())) {
            wrapper.isNull("parent_id");
        }

        // Date range filters
        if (dto.getCreatedAfter() != null && !dto.getCreatedAfter().isBlank()) {
            wrapper.ge("created_at", LocalDate.parse(dto.getCreatedAfter()).atStartOfDay());
        }
        if (dto.getCreatedBefore() != null && !dto.getCreatedBefore().isBlank()) {
            wrapper.le("created_at", LocalDate.parse(dto.getCreatedBefore()).atTime(23, 59, 59));
        }
        if (dto.getUpdatedAfter() != null && !dto.getUpdatedAfter().isBlank()) {
            wrapper.ge("updated_at", LocalDate.parse(dto.getUpdatedAfter()).atStartOfDay());
        }
        if (dto.getUpdatedBefore() != null && !dto.getUpdatedBefore().isBlank()) {
            wrapper.le("updated_at", LocalDate.parse(dto.getUpdatedBefore()).atTime(23, 59, 59));
        }

        // hideResolved
        if ("true".equals(dto.getHideResolved())) {
            Set<Long> closedStatusIds = issueStatusMapper.selectList(null).stream()
                    .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
                    .map(IssueStatus::getId).collect(Collectors.toSet());
            if (!closedStatusIds.isEmpty()) {
                wrapper.notIn("status_id", closedStatusIds);
            }
        }

        // Special filters
        boolean needClosedExclusion = "true".equals(dto.getOverdue())
                || "true".equals(dto.getDueSoon())
                || "true".equals(dto.getReportedByMe());
        if (needClosedExclusion) {
            Set<Long> closedIds = issueStatusMapper.selectList(null).stream()
                    .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
                    .map(IssueStatus::getId).collect(Collectors.toSet());
            if (!closedIds.isEmpty()) {
                wrapper.notIn("status_id", closedIds);
            }
        }
        if ("true".equals(dto.getOverdue()) || "true".equals(dto.getDueSoon())) {
            wrapper.isNotNull("due_date");
            if ("true".equals(dto.getOverdue())) wrapper.lt("due_date", LocalDate.now());
            if ("true".equals(dto.getDueSoon())) wrapper.le("due_date", LocalDate.now().plusDays(7));
        }
        if ("true".equals(dto.getReportedByMe())) {
            wrapper.eq("reporter_id", currentUserId);
        }

        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            String escaped = SqlUtils.escapeLikePattern(dto.getKeyword());
            String likePattern = "%" + escaped + "%";
            wrapper.and(w -> w
                    .apply("title LIKE {0} ESCAPE '\\'", likePattern)
                    .or().apply("description LIKE {0} ESCAPE '\\'", likePattern)
                    .or().apply("issue_key LIKE {0} ESCAPE '\\'", likePattern)
                    .or().apply("assignee_id IN (SELECT id FROM sys_user WHERE display_name LIKE {0} ESCAPE '\\' OR username LIKE {0} ESCAPE '\\')", likePattern)
            );
        }

        wrapper.orderByDesc("updated_at");
        // 限制最多查 MAX_EXPORT_LIMIT + 1 条，用于检测是否超限
        wrapper.last("LIMIT " + (MAX_EXPORT_LIMIT + 1));

        return issueMapper.selectList(wrapper);
    }

    private void applyFilter(QueryWrapper<Issue> wrapper, String column, String value, boolean isNumeric) {
        if (value == null || value.isBlank()) return;
        if ("none".equalsIgnoreCase(value.trim())) {
            wrapper.isNull(column);
            return;
        }
        if (value.contains(",")) {
            List<?> values = isNumeric
                    ? Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList()
                    : Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            wrapper.in(column, values);
        } else {
            if (isNumeric) {
                wrapper.eq(column, Long.parseLong(value.trim()));
            } else {
                wrapper.eq(column, value.trim());
            }
        }
    }

    private void applyNegativeFilter(QueryWrapper<Issue> wrapper, String column, String value, boolean isNumeric) {
        if (value == null || value.isBlank()) return;
        List<?> values = isNumeric
                ? Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList()
                : Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        wrapper.notIn(column, values);
    }

    // ========== 导出上下文 ==========

    private record ExportContext(
            Map<Long, String> projectNameMap,
            Map<Long, String> projectKeyMap,
            Map<Long, IssueStatus> statusMap,
            Map<Long, String> userNameMap,
            Map<Long, String> sprintNameMap,
            Map<Long, Map<String, String>> customFieldValues,
            List<String> customFieldHeaders
    ) {}

    private ExportContext buildExportContext(List<Issue> issues) {
        // 项目
        Set<Long> projectIds = issues.stream().map(Issue::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> projectNameMap = new HashMap<>();
        Map<Long, String> projectKeyMap = new HashMap<>();
        if (!projectIds.isEmpty()) {
            projectMapper.selectBatchIds(projectIds).forEach(p -> {
                projectNameMap.put(p.getId(), p.getName());
                projectKeyMap.put(p.getId(), p.getKey());
            });
        }

        // 状态
        Map<Long, IssueStatus> statusMap = issueStatusMapper.selectList(null).stream()
                .collect(Collectors.toMap(IssueStatus::getId, s -> s, (a, b) -> a));

        // 用户
        Set<Long> userIds = new HashSet<>();
        issues.forEach(i -> {
            if (i.getAssigneeId() != null) userIds.add(i.getAssigneeId());
            if (i.getReporterId() != null) userIds.add(i.getReporterId());
        });
        Map<Long, String> userNameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            sysUserMapper.selectBatchIds(userIds).forEach(u -> userNameMap.put(u.getId(), u.getDisplayName()));
        }

        // Sprint
        Set<Long> sprintIds = issues.stream().map(Issue::getSprintId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> sprintNameMap = new HashMap<>();
        if (!sprintIds.isEmpty()) {
            sprintMapper.selectBatchIds(sprintIds).forEach(s -> sprintNameMap.put(s.getId(), s.getName()));
        }

        // 自定义字段
        List<Long> issueIds = issues.stream().map(Issue::getId).toList();
        Map<Long, Map<String, String>> cfValues = customFieldService.getBatchDisplayValues(issueIds);

        // 收集所有自定义字段 header（key 格式 "cf_xxx"）
        Set<String> cfKeys = new TreeSet<>();
        cfValues.values().forEach(m -> cfKeys.addAll(m.keySet()));

        return new ExportContext(projectNameMap, projectKeyMap, statusMap, userNameMap, sprintNameMap, cfValues, new ArrayList<>(cfKeys));
    }

    // ========== XLSX 导出 ==========

    private ExportResult exportXlsx(List<Issue> issues, ExportContext ctx) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("工单数据");

            // 标题行样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 日期样式
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

            CellStyle dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

            // Header
            List<String> headers = buildHeaders(ctx);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (int rowIdx = 0; rowIdx < issues.size(); rowIdx++) {
                Issue issue = issues.get(rowIdx);
                Row row = sheet.createRow(rowIdx + 1);
                List<String> values = buildRow(issue, ctx);
                for (int col = 0; col < values.size(); col++) {
                    Cell cell = row.createCell(col);
                    String val = values.get(col);
                    cell.setCellValue(val != null ? val : "");
                }
            }

            // 自动调整列宽（最多 30 字符宽度）
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                if (width > 30 * 256) {
                    sheet.setColumnWidth(i, 30 * 256);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            String filename = "TrackFlow_Issues_" + LocalDate.now().format(DATE_FMT) + ".xlsx";
            return new ExportResult(out.toByteArray(), filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (IOException e) {
            log.error("XLSX 导出失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出文件生成失败");
        }
    }

    // ========== CSV 导出 ==========

    private ExportResult exportCsv(List<Issue> issues, ExportContext ctx) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 写入 UTF-8 BOM（让 Excel 正确识别编码）
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

            List<String> headers = buildHeaders(ctx);
            writer.write(escapeCsvRow(headers));
            writer.write("\r\n");

            for (Issue issue : issues) {
                List<String> values = buildRow(issue, ctx);
                writer.write(escapeCsvRow(values));
                writer.write("\r\n");
            }
            writer.flush();

            String filename = "TrackFlow_Issues_" + LocalDate.now().format(DATE_FMT) + ".csv";
            return new ExportResult(out.toByteArray(), filename, "text/csv; charset=UTF-8");
        } catch (IOException e) {
            log.error("CSV 导出失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出文件生成失败");
        }
    }

    // ========== 行数据构建 ==========

    private List<String> buildHeaders(ExportContext ctx) {
        List<String> headers = new ArrayList<>(List.of(
                "工单编号", "项目", "类型", "标题", "状态", "优先级",
                "负责人", "报告人", "Sprint", "截止日期",
                "创建时间", "更新时间", "解决时间", "描述"
        ));
        // 追加自定义字段列
        for (String cfKey : ctx.customFieldHeaders()) {
            // cfKey 格式为 "cf_xxx"，用作列头
            headers.add(cfKey);
        }
        return headers;
    }

    private List<String> buildRow(Issue issue, ExportContext ctx) {
        List<String> row = new ArrayList<>();

        row.add(issue.getIssueKey());
        row.add(ctx.projectKeyMap().getOrDefault(issue.getProjectId(), ""));
        row.add(localizeIssueType(issue.getIssueType()));
        row.add(issue.getTitle());

        // 状态
        IssueStatus status = ctx.statusMap().get(issue.getStatusId());
        row.add(status != null ? localizeStatusName(status.getName()) : "");

        row.add(localizePriority(issue.getPriority()));
        row.add(ctx.userNameMap().getOrDefault(issue.getAssigneeId(), ""));
        row.add(ctx.userNameMap().getOrDefault(issue.getReporterId(), ""));
        row.add(ctx.sprintNameMap().getOrDefault(issue.getSprintId(), ""));
        row.add(issue.getDueDate() != null ? issue.getDueDate().format(DATE_FMT) : "");
        row.add(issue.getCreatedAt() != null ? issue.getCreatedAt().format(DT_FMT) : "");
        row.add(issue.getUpdatedAt() != null ? issue.getUpdatedAt().format(DT_FMT) : "");
        row.add(issue.getResolvedAt() != null ? issue.getResolvedAt().format(DT_FMT) : "");
        row.add(stripHtml(issue.getDescription()));

        // 自定义字段
        Map<String, String> cfVals = ctx.customFieldValues().getOrDefault(issue.getId(), Map.of());
        for (String cfKey : ctx.customFieldHeaders()) {
            row.add(cfVals.getOrDefault(cfKey, ""));
        }

        return row;
    }

    // ========== 工具方法 ==========

    private String escapeCsvRow(List<String> fields) {
        return fields.stream().map(this::escapeCsvField).collect(Collectors.joining(","));
    }

    private String escapeCsvField(String field) {
        if (field == null) return "";
        // CSV formula sanitization: prevent injection via =, +, -, @, \t, \r
        if (!field.isEmpty() && "=+-@\t\r".indexOf(field.charAt(0)) >= 0) {
            field = "'" + field;
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        // 简单去除 HTML 标签
        return html.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
    }

    private String localizeStatusName(String name) {
        if (name == null) return "";
        return switch (name) {
            case "Open" -> "打开";
            case "In Progress" -> "进行中";
            case "Done" -> "已完成";
            case "Closed" -> "已关闭";
            case "Reopened" -> "重新打开";
            case "Resolved" -> "已解决";
            case "To Be Discussed" -> "待讨论";
            case "Submitted" -> "已提交";
            case "In Review" -> "评审中";
            case "Ready for Test" -> "待测试";
            case "Testing" -> "测试中";
            case "Verified" -> "已验证";
            case "Blocked" -> "已阻塞";
            case "Deferred" -> "已推迟";
            case "Cancelled" -> "已取消";
            case "Waiting for Reply" -> "等待回复";
            case "Under Investigation" -> "调查中";
            case "Draft" -> "草稿";
            default -> name;
        };
    }

    private String localizeIssueType(String type) {
        if (type == null) return "";
        return switch (type) {
            case "task" -> "任务";
            case "bug" -> "缺陷";
            case "feature" -> "功能";
            case "improvement" -> "改进";
            case "epic" -> "史诗";
            case "story" -> "用户故事";
            case "subtask" -> "子任务";
            default -> type;
        };
    }

    private String localizePriority(String priority) {
        if (priority == null) return "";
        return switch (priority) {
            case "critical" -> "紧急";
            case "major" -> "重要";
            case "normal" -> "普通";
            case "minor" -> "次要";
            default -> priority;
        };
    }
}
