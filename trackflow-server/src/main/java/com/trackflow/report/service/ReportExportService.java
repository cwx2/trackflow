package com.trackflow.report.service;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.mapper.ReportDefinitionMapper;
import com.trackflow.report.mapper.ReportShareMapper;
import com.trackflow.report.vo.ReportExecuteResultVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 报表导出服务 — 负责 CSV/Excel 导出逻辑
 * <p>
 * 从 ReportService 中抽取，职责单一：将报表执行结果序列化为不同格式并写入 HTTP 响应。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final ReportDefinitionMapper reportMapper;
    private final ReportShareMapper reportShareMapper;
    private final ProjectService projectService;
    private final PermissionService permissionService;
    private final ReportExecutionService reportExecutionService;

    /**
     * 导出报表到 HTTP 响应流（统一入口）
     * <p>
     * 处理格式路由、响应头构建、流式写入等逻辑。支持 csv 和 xlsx 两种格式。
     *
     * @param id       报表 ID
     * @param format   导出格式（csv / xlsx）
     * @param userId   当前用户 ID
     * @param response HTTP 响应对象
     */
    public void exportToResponse(Long id, String format, Long userId, HttpServletResponse response) throws IOException {
        if ("xlsx".equalsIgnoreCase(format)) {
            writeExcelResponse(id, userId, response);
        } else if ("csv".equalsIgnoreCase(format)) {
            writeCsvResponse(id, userId, response);
        } else {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅支持 csv 和 xlsx 格式导出");
        }
    }

    /**
     * 导出报表为 CSV 字符串
     */
    public String exportCsv(Long id, Long userId) {
        ReportExecuteResultVO result = executeForExport(id, userId);
        return buildCsv(result);
    }

    /**
     * 导出报表为 Excel (XLSX) 格式，使用 Apache POI 流式写入
     *
     * @return 报表名称和生成的工作簿
     */
    public ExcelExportResult exportExcel(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw BusinessException.notFound("报表不存在");
        }
        assertExportAccess(report, id, userId);

        ReportExecuteResultVO result = reportExecutionService.executeByOwnerScope(report);
        SXSSFWorkbook workbook = buildExcelWorkbook(result, report.getName());
        return new ExcelExportResult(report.getName(), workbook);
    }

    /**
     * Excel 导出结果封装
     */
    public record ExcelExportResult(String reportName, SXSSFWorkbook workbook) {}

    // ─── 私有方法 ──────────────────────────────────────────

    private void writeExcelResponse(Long id, Long userId, HttpServletResponse response) throws IOException {
        ExcelExportResult excelResult = exportExcel(id, userId);
        String fileName = excelResult.reportName() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        try (var workbook = excelResult.workbook()) {
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();
        }
    }

    private void writeCsvResponse(Long id, Long userId, HttpServletResponse response) throws IOException {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw BusinessException.notFound("报表不存在");
        }
        String reportName = report.getName() != null ? report.getName() : "report-" + id;
        String csvContent = exportCsv(id, userId);

        String fileName = reportName + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        // BOM for Excel UTF-8 recognition
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(csvContent);
            writer.flush();
        }
    }

    /**
     * 执行报表并返回结果（带权限校验），供导出使用。
     */
    private ReportExecuteResultVO executeForExport(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw BusinessException.notFound("报表不存在");
        }
        assertExportAccess(report, id, userId);
        return reportExecutionService.executeByOwnerScope(report);
    }

    /**
     * 导出权限校验（项目可访问 + 私有报表隔离 + 系统管理员覆盖）
     */
    private void assertExportAccess(ReportDefinition report, Long id, Long userId) {
        if (report.getProjectId() != null) {
            projectService.assertProjectAccessible(userId, report.getProjectId());
        }
        if (!Boolean.TRUE.equals(report.getShared())
                && !userId.equals(report.getCreatedBy())
                && reportShareMapper.countAccessByUser(id, userId) == 0
                && !permissionService.isSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问此私有报表");
        }
    }

    /**
     * 使用 Apache POI SXSSFWorkbook 构建 Excel 工作簿（流式写入，适合大数据量）
     */
    private SXSSFWorkbook buildExcelWorkbook(ReportExecuteResultVO result, String sheetName) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        Sheet sheet = workbook.createSheet(
                sheetName != null && !sheetName.isBlank() ? sheetName.substring(0, Math.min(sheetName.length(), 31)) : "报表数据");

        // 标题行样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        int rowIdx = 0;

        if (result.getMatrix() != null && result.getSecondLabels() != null) {
            // 双维度矩阵模式
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.createCell(0).setCellValue("");
            for (int j = 0; j < result.getSecondLabels().size(); j++) {
                Cell cell = headerRow.createCell(j + 1);
                cell.setCellValue(result.getSecondLabels().get(j));
                cell.setCellStyle(headerStyle);
            }
            Cell totalHeader = headerRow.createCell(result.getSecondLabels().size() + 1);
            totalHeader.setCellValue("合计");
            totalHeader.setCellStyle(headerStyle);

            for (int i = 0; i < result.getLabels().size(); i++) {
                Row dataRow = sheet.createRow(rowIdx++);
                Cell labelCell = dataRow.createCell(0);
                labelCell.setCellValue(result.getLabels().get(i));
                labelCell.setCellStyle(headerStyle);

                long rowTotal = 0;
                List<Long> matrixRow = result.getMatrix().get(i);
                for (int j = 0; j < matrixRow.size(); j++) {
                    long val = matrixRow.get(j);
                    dataRow.createCell(j + 1).setCellValue(val);
                    rowTotal += val;
                }
                dataRow.createCell(matrixRow.size() + 1).setCellValue(rowTotal);
            }
        } else {
            // 单维度模式
            Row headerRow = sheet.createRow(rowIdx++);
            Cell h1 = headerRow.createCell(0);
            h1.setCellValue("分组");
            h1.setCellStyle(headerStyle);
            Cell h2 = headerRow.createCell(1);
            h2.setCellValue("数量");
            h2.setCellStyle(headerStyle);

            for (int i = 0; i < result.getLabels().size(); i++) {
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.createCell(0).setCellValue(result.getLabels().get(i));
                dataRow.createCell(1).setCellValue(result.getData().get(i));
            }

            // 合计行
            Row totalRow = sheet.createRow(rowIdx);
            Cell totalLabelCell = totalRow.createCell(0);
            totalLabelCell.setCellValue("合计");
            totalLabelCell.setCellStyle(headerStyle);
            totalRow.createCell(1).setCellValue(result.getTotal());
        }

        return workbook;
    }

    /**
     * 将执行结果转为 CSV 字符串
     */
    private String buildCsv(ReportExecuteResultVO result) {
        StringBuilder sb = new StringBuilder();

        // 时间序列模式（Timeline 报表）
        if (result.getDates() != null && result.getSeries() != null && !result.getSeries().isEmpty()) {
            sb.append("\"日期\"");
            for (ReportExecuteResultVO.TimeSeriesData series : result.getSeries()) {
                sb.append(",\"").append(escapeCsv(series.getName())).append("\"");
            }
            sb.append("\n");

            for (int i = 0; i < result.getDates().size(); i++) {
                sb.append("\"").append(escapeCsv(result.getDates().get(i))).append("\"");
                for (ReportExecuteResultVO.TimeSeriesData series : result.getSeries()) {
                    Number val = (series.getData() != null && i < series.getData().size())
                            ? series.getData().get(i) : null;
                    sb.append(",").append(val != null ? val : "");
                }
                sb.append("\n");
            }
            return sb.toString();
        }

        // 状态转换模式
        if (result.getTransitions() != null && !result.getTransitions().isEmpty()) {
            sb.append("\"源状态\",\"目标状态\",\"转换次数\",\"平均停留时间(h)\"\n");
            for (ReportExecuteResultVO.StateTransitionItem item : result.getTransitions()) {
                sb.append("\"").append(escapeCsv(item.getFromStatus())).append("\",");
                sb.append("\"").append(escapeCsv(item.getToStatus())).append("\",");
                sb.append(item.getCount()).append(",");
                sb.append(item.getAvgDurationHours() != null ? String.format("%.1f", item.getAvgDurationHours()) : "");
                sb.append("\n");
            }
            sb.append("\"合计\",,").append(result.getTotal()).append(",\n");
            return sb.toString();
        }

        if (result.getMatrix() != null && result.getSecondLabels() != null) {
            // 双维度模式
            sb.append("\"\"");
            for (String sec : result.getSecondLabels()) {
                sb.append(",\"").append(escapeCsv(sec)).append("\"");
            }
            sb.append(",\"合计\"\n");

            for (int i = 0; i < result.getLabels().size(); i++) {
                sb.append("\"").append(escapeCsv(result.getLabels().get(i))).append("\"");
                long rowTotal = 0;
                for (int j = 0; j < result.getSecondLabels().size(); j++) {
                    long val = result.getMatrix().get(i).get(j);
                    sb.append(",").append(val);
                    rowTotal += val;
                }
                sb.append(",").append(rowTotal).append("\n");
            }
        } else if (result.getLabels() != null && result.getData() != null) {
            // 单维度模式
            sb.append("\"分组\",\"数量\"\n");
            for (int i = 0; i < result.getLabels().size(); i++) {
                sb.append("\"").append(escapeCsv(result.getLabels().get(i))).append("\",")
                        .append(result.getData().get(i)).append("\n");
            }
            sb.append("\"合计\",").append(result.getTotal()).append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}
