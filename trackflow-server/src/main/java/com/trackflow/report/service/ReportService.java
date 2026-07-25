package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.query.engine.QueryExecutor;
import com.trackflow.report.dto.CreateReportDTO;
import com.trackflow.report.dto.ReportQueryParams;
import com.trackflow.report.dto.ShareReportDTO;
import com.trackflow.report.dto.UpdateReportDTO;
import com.trackflow.report.entity.ReportConfig;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.entity.ReportGroupBy;
import com.trackflow.report.entity.ReportShare;
import com.trackflow.report.entity.ReportType;
import com.trackflow.report.mapper.ReportDefinitionMapper;
import com.trackflow.report.mapper.ReportFavoriteMapper;
import com.trackflow.report.mapper.ReportShareMapper;
import com.trackflow.report.mapper.ReportStatisticsMapper;
import com.trackflow.report.entity.ReportFavorite;
import com.trackflow.report.mapper.result.*;
import com.trackflow.report.vo.AverageIssueAgeVO;
import com.trackflow.report.vo.BurndownVO;
import com.trackflow.report.vo.CumulativeFlowVO;
import com.trackflow.report.vo.ReportExecuteResultVO;
import com.trackflow.report.vo.ReportGroupByOptionVO;
import com.trackflow.report.vo.ReportShareVO;
import com.trackflow.report.vo.ResolutionTimeVO;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.entity.UserGroup;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表服务 - 处理报表的创建、执行、缓存、导出、分享等业务逻辑
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDefinitionMapper reportMapper;
    private final ReportStatisticsMapper reportStatisticsMapper;
    private final ReportShareMapper reportShareMapper;
    private final ReportFavoriteMapper reportFavoriteMapper;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final PermissionService permissionService;
    private final StatusCacheHelper statusCacheHelper;
    private final SprintMapper sprintMapper;
    private final SysUserMapper sysUserMapper;
    private final UserGroupMapper userGroupMapper;
    private final CustomFieldService customFieldService;
    private final ReportStatisticsService reportStatisticsService;
    private final QueryExecutor queryExecutor;
    private final com.trackflow.report.converter.ReportConverter reportConverter;

    /**
     * 报表列表（带项目成员过滤 + 私有报表隔离 + 精细化共享）
     * 返回条件：自己创建的 OR shared=true OR 通过 report_share 共享给自己，且属于用户可访问的项目范围
     */
    public List<ReportDefinition> list(Long projectId, Long userId) {
        // 查询用户通过精细化共享可访问的报表 ID
        List<Long> sharedToMeIds = reportShareMapper.selectAccessibleReportIds(userId);

        LambdaQueryWrapper<ReportDefinition> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.and(w -> w.eq(ReportDefinition::getProjectId, projectId)
                    .or().isNull(ReportDefinition::getProjectId));
        } else {
            // 未指定项目时，只返回用户所属项目的报表 + 全局报表
            List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(userId);
            if (accessibleProjectIds != null) {
                // 非系统管理员
                if (accessibleProjectIds.isEmpty()) {
                    wrapper.isNull(ReportDefinition::getProjectId);
                } else {
                    wrapper.and(w -> w.in(ReportDefinition::getProjectId, accessibleProjectIds)
                            .or().isNull(ReportDefinition::getProjectId));
                }
            }
            // 系统管理员不加项目限制
        }

        // 私有报表隔离：自己创建 OR 公开共享 OR 精细化共享给自己
        wrapper.and(w -> {
            w.eq(ReportDefinition::getShared, true)
                    .or().eq(ReportDefinition::getCreatedBy, userId);
            if (!sharedToMeIds.isEmpty()) {
                w.or().in(ReportDefinition::getId, sharedToMeIds);
            }
        });

        wrapper.orderByAsc(ReportDefinition::getName);
        return reportMapper.selectList(wrapper);
    }

    /**
     * 获取报表列表及元数据（共享数量 + 收藏状态），避免 Controller 层 N+1 查询
     *
     * @param projectId 项目ID（可选）
     * @param userId    当前用户ID
     * @return 报表列表元数据（reports + shareCountMap + favoriteIds）
     */
    public ReportListMetadata listWithMetadata(Long projectId, Long userId) {
        List<ReportDefinition> reports = list(projectId, userId);
        List<Long> reportIds = reports.stream().map(ReportDefinition::getId).collect(Collectors.toList());
        Map<Long, Integer> shareCountMap = getShareCountMap(reportIds);
        Set<Long> favoriteIds = getUserFavoriteReportIds(userId);
        Map<Long, String> ownerNameMap = getOwnerNameMap(reports);
        return new ReportListMetadata(reports, shareCountMap, favoriteIds, ownerNameMap);
    }

    /**
     * 获取报表列表 VO（含元数据填充和排序），供 Controller 直接调用
     * 收藏优先 + 字母序；每个 VO 包含 shareCount、favorited、ownerDisplayName
     *
     * @param projectId 项目ID（可选）
     * @param userId    当前用户ID
     * @return 已填充元数据并排序的 VO 列表
     */
    public List<com.trackflow.report.vo.ReportDefinitionVO> listReportsVO(Long projectId, Long userId) {
        ReportListMetadata metadata = listWithMetadata(projectId, userId);
        List<com.trackflow.report.vo.ReportDefinitionVO> voList = reportConverter.toVOList(metadata.reports());
        for (com.trackflow.report.vo.ReportDefinitionVO vo : voList) {
            Long reportId = Long.parseLong(vo.getId());
            vo.setShareCount(metadata.shareCountMap().getOrDefault(reportId, 0));
            vo.setFavorited(metadata.favoriteIds().contains(reportId));
            if (vo.getCreatedBy() != null) {
                Long ownerId = Long.parseLong(vo.getCreatedBy());
                vo.setOwnerDisplayName(metadata.ownerNameMap().getOrDefault(ownerId, null));
            }
        }
        voList.sort((a, b) -> {
            boolean aFav = Boolean.TRUE.equals(a.getFavorited());
            boolean bFav = Boolean.TRUE.equals(b.getFavorited());
            if (aFav != bFav) return aFav ? -1 : 1;
            return (a.getName() != null ? a.getName() : "").compareTo(b.getName() != null ? b.getName() : "");
        });
        return voList;
    }

    /**
     * 批量获取报表创建者的显示名称（避免 N+1 查询）
     */
    private Map<Long, String> getOwnerNameMap(List<ReportDefinition> reports) {
        Set<Long> ownerIds = reports.stream()
                .map(ReportDefinition::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ownerIds.isEmpty()) return Map.of();
        return sysUserMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
    }

    /**
     * 报表列表查询结果元数据
     */
    public record ReportListMetadata(
            List<ReportDefinition> reports,
            Map<Long, Integer> shareCountMap,
            Set<Long> favoriteIds,
            Map<Long, String> ownerNameMap
    ) {}

    /**
     * 创建报表（带权限校验）
     * - projectId 非空：需要 project:edit 权限
     * - projectId 为空（全局报表）：需要系统管理员权限
     * 创建后自动添加到创建者的收藏列表（参照 YouTrack 行为）
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportDefinition createWithAccessCheck(CreateReportDTO dto, Long userId) {
        if (dto.getProjectId() != null) {
            // 项目级报表：需要 project:edit 权限
            if (!permissionService.hasPermission(userId, dto.getProjectId(), "project:edit")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "需要项目编辑权限才能创建项目报表");
            }
        } else {
            // 全局报表：需要系统管理员权限
            if (!permissionService.isSystemAdmin(userId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "全局报表仅系统管理员可创建");
            }
        }
        ReportDefinition report = create(dto);
        // 新创建的报表自动添加到创建者收藏
        addFavorite(report.getId(), userId);
        return report;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReportDefinition create(CreateReportDTO dto) {
        // 校验报表类型合法性
        if (!ReportType.isValid(dto.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的报表类型: " + dto.getType() + "，允许值: " + ReportType.allowedValues());
        }

        // 校验 config 中 groupBy 的合法性
        validateConfig(dto.getConfig(), dto.getType());

        ReportDefinition report = new ReportDefinition();
        report.setName(dto.getName());
        report.setProjectId(dto.getProjectId());
        report.setType(dto.getType());
        report.setConfig(dto.getConfig());
        report.setShared(dto.getShared() != null ? dto.getShared() : false);
        reportMapper.insert(report);
        return report;
    }

    // ─── 单个报表查询 ──────────────────────────────────────

    /**
     * 获取单个报表定义（带权限校验）
     */
    public ReportDefinition getWithAccessCheck(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在: " + id);
        }
        if (report.getProjectId() != null) {
            projectService.assertProjectAccessible(userId, report.getProjectId());
        }
        // 私有报表隔离
        if (!Boolean.TRUE.equals(report.getShared())
                && !userId.equals(report.getCreatedBy())
                && reportShareMapper.countAccessByUser(id, userId) == 0
                && !permissionService.isSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问此私有报表");
        }
        return report;
    }

    /**
     * 判断用户是否收藏了指定报表
     */
    public boolean isFavorited(Long reportId, Long userId) {
        return reportFavoriteMapper.selectCount(
                new LambdaQueryWrapper<ReportFavorite>()
                        .eq(ReportFavorite::getUserId, userId)
                        .eq(ReportFavorite::getReportId, reportId)) > 0;
    }

    /**
     * 获取报表创建者的显示名称
     */
    public String getOwnerDisplayName(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? user.getDisplayName() : null;
    }

    // ─── 收藏管理 ────────────────────────────────────────

    /**
     * 切换报表收藏状态（收藏/取消收藏）
     * 需要对报表有查看权限才能收藏
     *
     * @param reportId 报表ID
     * @param userId   当前用户ID
     * @return true=已收藏, false=已取消收藏
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long reportId, Long userId) {
        // 确认报表存在
        ReportDefinition report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }

        // 权限校验：需要对报表有查看权限
        if (!Boolean.TRUE.equals(report.getShared())
                && !userId.equals(report.getCreatedBy())
                && reportShareMapper.countAccessByUser(reportId, userId) == 0
                && !permissionService.isSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问此私有报表");
        }

        ReportFavorite existing = reportFavoriteMapper.selectOne(
                new LambdaQueryWrapper<ReportFavorite>()
                        .eq(ReportFavorite::getUserId, userId)
                        .eq(ReportFavorite::getReportId, reportId));

        if (existing != null) {
            reportFavoriteMapper.deleteById(existing.getId());
            log.info("Report unfavorited: reportId={}, userId={}", reportId, userId);
            return false;
        } else {
            ReportFavorite fav = new ReportFavorite();
            fav.setUserId(userId);
            fav.setReportId(reportId);
            reportFavoriteMapper.insert(fav);
            log.info("Report favorited: reportId={}, userId={}", reportId, userId);
            return true;
        }
    }

    /**
     * 添加报表到用户收藏（内部使用，如创建报表时自动收藏）
     */
    public void addFavorite(Long reportId, Long userId) {
        ReportFavorite existing = reportFavoriteMapper.selectOne(
                new LambdaQueryWrapper<ReportFavorite>()
                        .eq(ReportFavorite::getUserId, userId)
                        .eq(ReportFavorite::getReportId, reportId));
        if (existing == null) {
            ReportFavorite fav = new ReportFavorite();
            fav.setUserId(userId);
            fav.setReportId(reportId);
            reportFavoriteMapper.insert(fav);
        }
    }

    /**
     * 获取用户收藏的报表ID集合
     */
    public Set<Long> getUserFavoriteReportIds(Long userId) {
        List<ReportFavorite> favorites = reportFavoriteMapper.selectList(
                new LambdaQueryWrapper<ReportFavorite>()
                        .eq(ReportFavorite::getUserId, userId)
                        .select(ReportFavorite::getReportId));
        return favorites.stream()
                .map(ReportFavorite::getReportId)
                .collect(Collectors.toSet());
    }

    /**
     * 克隆报表（带权限校验）
     * 创建一份指定报表的副本，名称加"(副本)"后缀
     * 需要：用户对源报表有查看权限 + report:create 权限
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportDefinition clone(Long id, Long userId) {
        ReportDefinition source = reportMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }

        // 项目可访问性检查
        if (source.getProjectId() != null) {
            projectService.assertProjectAccessible(userId, source.getProjectId());
        }

        // 私有报表隔离检查（owner / shared=true / report_share 中有权限 / 系统管理员）
        if (!Boolean.TRUE.equals(source.getShared())
                && !userId.equals(source.getCreatedBy())
                && reportShareMapper.countAccessByUser(id, userId) == 0
                && !permissionService.isSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问此私有报表");
        }

        ReportDefinition cloned = new ReportDefinition();
        cloned.setName(source.getName() + " (副本)");
        cloned.setProjectId(source.getProjectId());
        cloned.setType(source.getType());
        cloned.setConfig(source.getConfig());
        cloned.setShared(false); // 克隆的报表默认私有
        cloned.setIsSystem(false); // 克隆不保留系统标记
        reportMapper.insert(cloned);
        return cloned;
    }

    /**
     * 导出报表为 CSV 格式
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
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }
        assertExportAccess(report, id, userId);

        // 使用 Owner 权限决定数据范围，与 executeWithAccessCheck 一致
        // （对标 YouTrack：导出数据等同于页面显示的数据，均按 Owner 权限范围计算）
        Long executionOwner = report.getCreatedBy();
        List<Long> scopeProjectIds = resolveExecutionScope(report, executionOwner);
        ReportExecuteResultVO result = executeInternal(report, scopeProjectIds);

        org.apache.poi.xssf.streaming.SXSSFWorkbook workbook = buildExcelWorkbook(result, report.getName());
        return new ExcelExportResult(report.getName(), workbook);
    }

    /**
     * Excel 导出结果封装
     */
    public record ExcelExportResult(String reportName, org.apache.poi.xssf.streaming.SXSSFWorkbook workbook) {}

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
     * 写入 Excel 格式导出响应
     */
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

    /**
     * 写入 CSV 格式导出响应（含 BOM 以兼容 Excel 打开 UTF-8）
     */
    private void writeCsvResponse(Long id, Long userId, HttpServletResponse response) throws IOException {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
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
     * 使用 Owner 权限决定数据范围，与 executeWithAccessCheck 一致。
     */
    private ReportExecuteResultVO executeForExport(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }
        assertExportAccess(report, id, userId);

        // 对标 YouTrack：导出数据与页面查看一致，均按 Owner 权限范围计算
        Long executionOwner = report.getCreatedBy();
        List<Long> scopeProjectIds = resolveExecutionScope(report, executionOwner);
        return executeInternal(report, scopeProjectIds);
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
    private org.apache.poi.xssf.streaming.SXSSFWorkbook buildExcelWorkbook(ReportExecuteResultVO result, String sheetName) {
        org.apache.poi.xssf.streaming.SXSSFWorkbook workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100);
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(
                sheetName != null && !sheetName.isBlank() ? sheetName.substring(0, Math.min(sheetName.length(), 31)) : "报表数据");

        // 标题行样式
        org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        int rowIdx = 0;

        if (result.getMatrix() != null && result.getSecondLabels() != null) {
            // 双维度矩阵模式
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIdx++);
            headerRow.createCell(0).setCellValue("");
            for (int j = 0; j < result.getSecondLabels().size(); j++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(j + 1);
                cell.setCellValue(result.getSecondLabels().get(j));
                cell.setCellStyle(headerStyle);
            }
            org.apache.poi.ss.usermodel.Cell totalHeader = headerRow.createCell(result.getSecondLabels().size() + 1);
            totalHeader.setCellValue("合计");
            totalHeader.setCellStyle(headerStyle);

            for (int i = 0; i < result.getLabels().size(); i++) {
                org.apache.poi.ss.usermodel.Row dataRow = sheet.createRow(rowIdx++);
                org.apache.poi.ss.usermodel.Cell labelCell = dataRow.createCell(0);
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
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIdx++);
            org.apache.poi.ss.usermodel.Cell h1 = headerRow.createCell(0);
            h1.setCellValue("分组");
            h1.setCellStyle(headerStyle);
            org.apache.poi.ss.usermodel.Cell h2 = headerRow.createCell(1);
            h2.setCellValue("数量");
            h2.setCellStyle(headerStyle);

            for (int i = 0; i < result.getLabels().size(); i++) {
                org.apache.poi.ss.usermodel.Row dataRow = sheet.createRow(rowIdx++);
                dataRow.createCell(0).setCellValue(result.getLabels().get(i));
                dataRow.createCell(1).setCellValue(result.getData().get(i));
            }

            // 合计行
            org.apache.poi.ss.usermodel.Row totalRow = sheet.createRow(rowIdx);
            org.apache.poi.ss.usermodel.Cell totalLabelCell = totalRow.createCell(0);
            totalLabelCell.setCellValue("合计");
            totalLabelCell.setCellStyle(headerStyle);
            totalRow.createCell(1).setCellValue(result.getTotal());
        }

        return workbook;
    }

    /**
     * 更新报表（带权限校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportDefinition updateWithAccessCheck(Long id, UpdateReportDTO dto, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }

        // 系统预置报表只有系统管理员可以修改
        if (Boolean.TRUE.equals(report.getIsSystem())) {
            if (!permissionService.isSystemAdmin(userId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "系统预置报表只有系统管理员可以修改");
            }
        } else {
            // 权限校验：创建者可修改，精细化共享 edit 权限用户可修改
            if (!userId.equals(report.getCreatedBy())) {
                // 先检查是否有 edit 共享权限
                if (reportShareMapper.countEditAccessByUser(id, userId) > 0) {
                    // 有 edit 权限，允许修改（但不允许修改共享设置本身）
                    // 共享设置只能由创建者管理
                } else if (report.getProjectId() != null) {
                    if (!permissionService.hasPermission(userId, report.getProjectId(), "project:edit")) {
                        throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只有报表创建者或项目管理员可以修改此报表");
                    }
                } else {
                    if (!permissionService.isSystemAdmin(userId)) {
                        throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只有报表创建者或系统管理员可以修改此报表");
                    }
                }
            }
        }

        // 校验报表类型合法性
        if (dto.getType() != null && !ReportType.isValid(dto.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的报表类型: " + dto.getType() + "，允许值: " + ReportType.allowedValues());
        }

        // 校验 config 合法性
        if (dto.getConfig() != null) {
            validateGroupByLegality(dto.getConfig());
        }
        // type-groupBy 一致性校验：仅在 type 发生实质变化时触发
        boolean typeChanged = dto.getType() != null && !dto.getType().equals(report.getType());
        if (typeChanged) {
            String effectiveConfig = dto.getConfig() != null ? dto.getConfig() : report.getConfig();
            validateConfig(effectiveConfig, dto.getType());
        }

        // 部分更新——只更新传入的字段
        if (dto.getName() != null && !dto.getName().isBlank()) {
            report.setName(dto.getName().trim());
        }
        boolean computationFieldChanged = false;
        if (dto.getType() != null) {
            report.setType(dto.getType());
            computationFieldChanged = true;
        }
        if (dto.getConfig() != null) {
            report.setConfig(dto.getConfig());
            computationFieldChanged = true;
        }
        if (dto.getShared() != null) {
            report.setShared(dto.getShared());
        }

        // 配置变更时清除持久化缓存，确保下次查看返回新配置计算的结果
        if (computationFieldChanged) {
            report.setCachedResult(null);
            report.setLastCalculatedAt(null);
        }

        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
        return report;
    }

    /**
     * 校验报表配置的合法性
     */
    private void validateConfig(String config, String type) {
        Map<String, Object> configMap = parseConfig(config);
        String groupBy = (String) configMap.get("groupBy");

        // 如果指定了 groupBy，必须合法（内置维度或 cf_{fieldId} 格式）
        if (groupBy != null && !groupBy.isBlank() && !ReportGroupBy.isValid(groupBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的分组维度: " + groupBy + "，允许值: " + ReportGroupBy.allowedValues());
        }

        // 自定义字段存在性校验
        if (groupBy != null && ReportGroupBy.isCustomFieldFormat(groupBy)) {
            validateCustomFieldExists(groupBy);
        }

        // secondGroupBy 校验
        String secondGroupBy = (String) configMap.get("secondGroupBy");
        if (secondGroupBy != null && !secondGroupBy.isBlank() && !ReportGroupBy.isValid(secondGroupBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的第二分组维度: " + secondGroupBy + "，允许值: " + ReportGroupBy.allowedValues());
        }
        if (secondGroupBy != null && ReportGroupBy.isCustomFieldFormat(secondGroupBy)) {
            validateCustomFieldExists(secondGroupBy);
        }

        // 类型与 groupBy 的一致性校验
        // 注意：自定义字段分组维度(cf_*)不受类型限制——"按状态分布"报表也可以切换为按自定义字段分组
        ReportType reportType = ReportType.fromValue(type);
        if (reportType != null && reportType.getDefaultGroupBy() != null) {
            if (groupBy != null && !groupBy.isBlank()
                    && !groupBy.equals(reportType.getDefaultGroupBy())
                    && !ReportGroupBy.isCustomFieldFormat(groupBy)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "报表类型 " + type + " 的分组维度必须为 " + reportType.getDefaultGroupBy());
            }
        }
    }

    private void validateGroupByLegality(String config) {
        Map<String, Object> configMap = parseConfig(config);
        String groupBy = (String) configMap.get("groupBy");
        if (groupBy != null && !groupBy.isBlank() && !ReportGroupBy.isValid(groupBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的分组维度: " + groupBy + "，允许值: " + ReportGroupBy.allowedValues());
        }
        if (groupBy != null && ReportGroupBy.isCustomFieldFormat(groupBy)) {
            validateCustomFieldExists(groupBy);
        }
        String secondGroupBy = (String) configMap.get("secondGroupBy");
        if (secondGroupBy != null && !secondGroupBy.isBlank() && !ReportGroupBy.isValid(secondGroupBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的第二分组维度: " + secondGroupBy + "，允许值: " + ReportGroupBy.allowedValues());
        }
        if (secondGroupBy != null && ReportGroupBy.isCustomFieldFormat(secondGroupBy)) {
            validateCustomFieldExists(secondGroupBy);
        }
    }

    /**
     * 校验自定义字段是否存在
     */
    private void validateCustomFieldExists(String groupByValue) {
        Long fieldId = ReportGroupBy.extractCustomFieldId(groupByValue);
        if (fieldId == null) return;
        CustomFieldDefinition field = customFieldService.getDefinitionById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "自定义字段不存在: " + groupByValue);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        reportMapper.deleteById(id);
    }

    /**
     * 删除报表（带权限校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithAccessCheck(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found");

        if (Boolean.TRUE.equals(report.getIsSystem())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统预置报表不允许删除");
        }

        if (!userId.equals(report.getCreatedBy())) {
            if (report.getProjectId() != null) {
                if (!permissionService.hasPermission(userId, report.getProjectId(), "project:edit")) {
                    throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只有报表创建者或项目管理员可以删除此报表");
                }
            } else {
                if (!permissionService.isSystemAdmin(userId)) {
                    throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只有报表创建者或系统管理员可以删除此报表");
                }
            }
        }

        // 删除共享记录（DB 有 ON DELETE CASCADE，但显式删除更清晰）
        reportShareMapper.delete(new LambdaQueryWrapper<ReportShare>()
                .eq(ReportShare::getReportId, id));
        reportMapper.deleteById(id);
    }

    // ─── 共享管理 ────────────────────────────────────────

    /**
     * 设置报表共享（覆盖模式：传入全量共享列表）
     * 只有报表创建者或系统管理员可以管理共享
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ReportShareVO> setShares(Long reportId, ShareReportDTO dto, Long userId) {
        ReportDefinition report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }
        // 只有报表创建者或系统管理员可以管理共享
        if (!report.getCreatedBy().equals(userId) && !permissionService.isSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有报表创建者可以管理共享");
        }

        // 删除现有共享记录
        reportShareMapper.delete(new LambdaQueryWrapper<ReportShare>()
                .eq(ReportShare::getReportId, reportId));

        // 批量新增
        List<ReportShare> shares = dto.getTargets().stream().map(t -> {
            ReportShare share = new ReportShare();
            share.setReportId(reportId);
            share.setTargetType(t.getTargetType());
            share.setTargetId(t.getTargetId());
            share.setPermission(t.getPermission() != null ? t.getPermission() : "view");
            share.setCreatedBy(userId);
            return share;
        }).toList();

        if (!shares.isEmpty()) {
            Db.saveBatch(shares);
        }

        log.info("Report shares updated: reportId={}, targets={}", reportId, shares.size());
        return getShares(reportId, userId);
    }

    /**
     * 获取报表的共享列表
     * 创建者或系统管理员可以查看完整共享列表
     */
    public List<ReportShareVO> getShares(Long reportId, Long userId) {
        ReportDefinition report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }
        // 只有创建者或系统管理员可以查看完整共享列表
        if (!report.getCreatedBy().equals(userId) && !permissionService.isSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有报表创建者可以查看共享设置");
        }

        List<ReportShare> shares = reportShareMapper.selectList(
                new LambdaQueryWrapper<ReportShare>()
                        .eq(ReportShare::getReportId, reportId)
                        .orderByAsc(ReportShare::getTargetType)
                        .orderByAsc(ReportShare::getCreatedAt));

        // 批量查询目标名称
        Set<Long> userIds = shares.stream()
                .filter(s -> "user".equals(s.getTargetType()))
                .map(ReportShare::getTargetId)
                .collect(Collectors.toSet());
        Set<Long> groupIds = shares.stream()
                .filter(s -> "group".equals(s.getTargetType()))
                .map(ReportShare::getTargetId)
                .collect(Collectors.toSet());

        Map<Long, String> userNames = Map.of();
        if (!userIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
            userNames = users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
        }

        Map<Long, String> groupNames = Map.of();
        if (!groupIds.isEmpty()) {
            List<UserGroup> groups = userGroupMapper.selectBatchIds(groupIds);
            groupNames = groups.stream().collect(Collectors.toMap(UserGroup::getId, UserGroup::getName, (a, b) -> a));
        }

        Map<Long, String> finalUserNames = userNames;
        Map<Long, String> finalGroupNames = groupNames;

        return shares.stream().map(s -> {
            ReportShareVO vo = new ReportShareVO();
            vo.setId(String.valueOf(s.getId()));
            vo.setTargetType(s.getTargetType());
            vo.setTargetId(String.valueOf(s.getTargetId()));
            vo.setPermission(s.getPermission());
            vo.setCreatedAt(s.getCreatedAt());
            if ("user".equals(s.getTargetType())) {
                vo.setTargetName(finalUserNames.getOrDefault(s.getTargetId(), "未知用户"));
            } else {
                vo.setTargetName(finalGroupNames.getOrDefault(s.getTargetId(), "未知用户组"));
            }
            return vo;
        }).toList();
    }

    /**
     * 移除单条共享
     * 只有报表创建者或系统管理员可以管理共享
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeShare(Long reportId, Long shareId, Long userId) {
        ReportDefinition report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }
        if (!report.getCreatedBy().equals(userId) && !permissionService.isSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有报表创建者可以管理共享");
        }

        ReportShare share = reportShareMapper.selectById(shareId);
        if (share == null || !share.getReportId().equals(reportId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "共享记录不存在");
        }
        reportShareMapper.deleteById(shareId);
        log.info("Report share removed: reportId={}, shareId={}", reportId, shareId);
    }

    /**
     * 获取报表的共享数量
     */
    public int getShareCount(Long reportId) {
        Long count = reportShareMapper.selectCount(
                new LambdaQueryWrapper<ReportShare>().eq(ReportShare::getReportId, reportId));
        return count != null ? count.intValue() : 0;
    }

    /**
     * 批量获取多个报表的共享数量（单次 SQL，避免 N+1 查询）
     *
     * @param reportIds 报表 ID 列表
     * @return reportId → shareCount 映射
     */
    public Map<Long, Integer> getShareCountMap(List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> results = reportShareMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ReportShare>()
                        .select("report_id", "COUNT(*) as cnt")
                        .in("report_id", reportIds)
                        .groupBy("report_id")
        );
        return results.stream().collect(Collectors.toMap(
                m -> ((Number) m.get("report_id")).longValue(),
                m -> ((Number) m.get("cnt")).intValue()
        ));
    }

    /**
     * 检查用户是否拥有报表的编辑权限（创建者 / edit 共享 / 项目管理员 / 系统管理员）
     */
    public boolean hasEditPermission(Long reportId, Long userId) {
        ReportDefinition report = reportMapper.selectById(reportId);
        if (report == null) return false;
        if (userId.equals(report.getCreatedBy())) return true;
        if (reportShareMapper.countEditAccessByUser(reportId, userId) > 0) return true;
        if (report.getProjectId() != null) {
            return permissionService.hasPermission(userId, report.getProjectId(), "project:edit");
        }
        return permissionService.isSystemAdmin(userId);
    }

    /**
     * 带权限校验的报表执行（支持结果缓存）。
     * <p>
     * 缓存策略（对标 YouTrack）：
     * - 如果缓存未过期且非强制刷新：直接返回缓存结果
     * - 如果缓存过期或强制刷新：重新计算并持久化结果
     * - 缓存过期判断：lastCalculatedAt + refreshInterval > now
     * - 默认缓存 TTL：600 秒（10 分钟），如果报表未配置 refreshInterval
     *
     * @param id     报表 ID
     * @param userId 当前用户 ID
     * @param force  是否强制重新计算（忽略缓存）
     */
    public ReportExecuteResultVO executeWithAccessCheck(Long id, Long userId, boolean force) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found");

        if (report.getProjectId() != null) {
            projectService.assertProjectAccessible(userId, report.getProjectId());
        }

        // 私有报表隔离：owner / shared / report_share / 系统管理员
        if (!Boolean.TRUE.equals(report.getShared())
                && !userId.equals(report.getCreatedBy())
                && reportShareMapper.countAccessByUser(id, userId) == 0
                && !permissionService.isSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问此私有报表");
        }

        // 缓存命中判断（非强制刷新时）
        if (!force && isCacheValid(report)) {
            ReportExecuteResultVO cached = deserializeCachedResult(report.getCachedResult());
            if (cached != null) {
                log.debug("Report cache hit: id={}, lastCalculatedAt={}", id, report.getLastCalculatedAt());
                return cached;
            }
        }

        // 缓存未命中或强制刷新：执行计算
        // 使用 Owner 权限决定数据范围（对标 YouTrack：Report data is retrieved according to
        // the access rights of the report owner），所有查看者看到相同数据
        Long executionOwner = report.getCreatedBy();
        List<Long> scopeProjectIds = resolveExecutionScope(report, executionOwner);
        ReportExecuteResultVO result = executeInternal(report, scopeProjectIds);

        // 持久化计算结果
        persistCachedResult(report, result);

        return result;
    }

    /**
     * 带权限校验的报表执行（默认不强制刷新）
     */
    public ReportExecuteResultVO executeWithAccessCheck(Long id, Long userId) {
        return executeWithAccessCheck(id, userId, false);
    }

    /**
     * 判断报表缓存是否仍然有效。
     * 缓存有效条件：lastCalculatedAt 非空 且 未超过 refreshInterval（默认 600 秒）
     */
    private boolean isCacheValid(ReportDefinition report) {
        if (report.getLastCalculatedAt() == null || report.getCachedResult() == null) {
            return false;
        }
        int ttlSeconds = getEffectiveTtl(report);
        LocalDateTime expireAt = report.getLastCalculatedAt().plusSeconds(ttlSeconds);
        return LocalDateTime.now().isBefore(expireAt);
    }

    /**
     * 获取报表的有效缓存 TTL（秒）。
     * 优先使用报表配置中的 refreshInterval，否则使用默认值 600 秒（10 分钟）。
     */
    private int getEffectiveTtl(ReportDefinition report) {
        Map<String, Object> configMap = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(configMap);
        Integer interval = config.getRefreshInterval();
        return (interval != null && interval > 0) ? interval : DEFAULT_CACHE_TTL_SECONDS;
    }

    /** 默认缓存 TTL：10 分钟 */
    private static final int DEFAULT_CACHE_TTL_SECONDS = 600;

    /**
     * 反序列化缓存的报表结果
     */
    private ReportExecuteResultVO deserializeCachedResult(String cachedJson) {
        if (cachedJson == null || cachedJson.isBlank()) return null;
        try {
            return objectMapper.readValue(cachedJson, ReportExecuteResultVO.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize report cached result, will recalculate: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 持久化报表计算结果到数据库
     */
    private void persistCachedResult(ReportDefinition report, ReportExecuteResultVO result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            ReportDefinition update = new ReportDefinition();
            update.setId(report.getId());
            update.setLastCalculatedAt(LocalDateTime.now());
            update.setCachedResult(resultJson);
            reportMapper.updateById(update);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize report result for caching: id={}, error={}", report.getId(), e.getMessage());
            // 缓存持久化失败不影响正常返回
        }
    }

    /**
     * 执行报表（系统内部调用，无用户权限限制）。
     * 用于定时任务、系统通知等无用户上下文的场景。
     */
    public ReportExecuteResultVO execute(Long id) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found");
        // 内部调用不限制项目范围，传入 null 表示无限制
        List<Long> projectIds = report.getProjectId() != null
                ? List.of(report.getProjectId())
                : null;
        return executeInternal(report, projectIds);
    }

    /**
     * 解析报表执行的项目范围。
     * <p>
     * - 项目级报表：返回 [projectId]
     * - 全局报表 + 系统管理员：返回所有活跃项目 ID（确保 SQL 走索引）
     * - 全局报表 + 普通用户：返回用户可访问的项目 ID 列表
     */
    private List<Long> resolveExecutionScope(ReportDefinition report, Long userId) {
        if (report.getProjectId() != null) {
            return List.of(report.getProjectId());
        }
        // 全局报表：按用户权限限制项目范围
        List<Long> ids = projectService.getAccessibleProjectIds(userId);
        if (ids == null) {
            // 系统管理员：显式查询所有活跃项目 ID（走索引而非全表扫描）
            ids = projectService.getAllActiveProjectIds();
        }
        // 防护：用户无任何可访问项目时，使用 sentinel 值确保 SQL IN (-1) 返回空集
        // 而非跳过项目过滤条件导致全表扫描数据泄露
        if (ids.isEmpty()) {
            return List.of(-1L);
        }
        return ids;
    }

    /**
     * 增强版执行引擎
     * 支持：timeRange筛选 + filters组合筛选 + 双维度交叉 + 按项目分组 + chartType + 自定义字段分组
     * 以及 Timeline 类报表（燃尽图、累积流图、解决时间）和 State Transition 类报表
     *
     * @param report     报表定义
     * @param projectIds 项目范围限制（null 表示不限制——仅内部调用允许）
     */
    private ReportExecuteResultVO executeInternal(ReportDefinition report, List<Long> projectIds) {
        ReportType reportType = ReportType.fromValue(report.getType());

        // 类型路由：时间线类和状态转换类报表委托给专门的执行方法
        if (reportType != null && reportType.isTimeline()) {
            return executeTimelineReport(report, reportType, projectIds);
        }
        if (reportType != null && reportType.isStateTransition()) {
            return executeStateTransitionReport(report, projectIds);
        }
        if (reportType != null && reportType.isTimeManagement()) {
            return executeTimeManagementReport(report, reportType, projectIds);
        }

        // 分布类报表：原有逻辑
        return executeDistributionReport(report, projectIds);
    }

    /**
     * 执行分布类报表（Issue Distribution 类）
     */
    private ReportExecuteResultVO executeDistributionReport(ReportDefinition report, List<Long> projectIds) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        // 校验并规范化 groupBy
        String groupBy = config.getGroupBy();
        if (!ReportGroupBy.isValid(groupBy)) {
            groupBy = "status";
        }

        // 构建查询参数（使用传入的 projectIds 限制范围）
        ReportQueryParams params = buildQueryParams(config, groupBy, projectIds);

        // 如果是自定义字段分组，解析 fieldId 并添加到 params
        if (ReportGroupBy.isCustomFieldFormat(groupBy)) {
            Long cfId = ReportGroupBy.extractCustomFieldId(groupBy);
            params.setCustomFieldId(cfId);
            params.setIsCustomFieldGroupBy(true);
            // 获取字段定义，判断是否为 list 类型（需要 JOIN option 表获取显示值）
            CustomFieldDefinition cfDef = customFieldService.getDefinitionById(cfId);
            if (cfDef != null && "list".equals(cfDef.getFieldFormat())) {
                params.setCustomFieldIsListType(true);
            }
            if (cfDef != null && "user".equals(cfDef.getFieldFormat())) {
                params.setCustomFieldIsUserType(true);
            }
        }

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setCategory("distribution");
        result.setGroupBy(groupBy);
        result.setChartType(config.getChartType());

        // 是否为双维度交叉模式
        String secondGroupBy = config.getSecondGroupBy();
        boolean isCrossMode = secondGroupBy != null && !secondGroupBy.isBlank()
                && ReportGroupBy.isValid(secondGroupBy);

        if (isCrossMode) {
            result.setSecondGroupBy(secondGroupBy);
            params.setSecondGroupBy(secondGroupBy);
            // 如果第二维度也是自定义字段
            if (ReportGroupBy.isCustomFieldFormat(secondGroupBy)) {
                Long cfId2 = ReportGroupBy.extractCustomFieldId(secondGroupBy);
                params.setSecondCustomFieldId(cfId2);
                params.setIsSecondCustomFieldGroupBy(true);
                CustomFieldDefinition cfDef2 = customFieldService.getDefinitionById(cfId2);
                if (cfDef2 != null && "list".equals(cfDef2.getFieldFormat())) {
                    params.setSecondCustomFieldIsListType(true);
                }
                if (cfDef2 != null && "user".equals(cfDef2.getFieldFormat())) {
                    params.setSecondCustomFieldIsUserType(true);
                }
            }
            executeCrossMode(params, result);
        } else {
            executeSingleMode(params, result);
        }

        // 设置应用的筛选摘要
        if (config.hasFilters() || config.getTimeRange() != null) {
            result.setAppliedFilters(buildFilterSummary(config));
        }

        // 设置计算时间和刷新间隔
        result.setCalculatedAt(LocalDateTime.now());
        result.setRefreshInterval(config.getRefreshInterval());

        return result;
    }

    /**
     * 执行时间线类报表（燃尽图、累积流图、解决时间分析）
     */
    private ReportExecuteResultVO executeTimelineReport(ReportDefinition report, ReportType reportType, List<Long> projectIds) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setCategory("timeline");
        result.setCalculatedAt(LocalDateTime.now());
        result.setRefreshInterval(config.getRefreshInterval());

        // 解析时间范围（时间线类报表的核心参数）
        LocalDateTime[] timeRange = config.resolveTimeRange();
        java.time.LocalDate startDate = timeRange != null ? timeRange[0].toLocalDate() : java.time.LocalDate.now().minusDays(29);
        java.time.LocalDate endDate = timeRange != null ? timeRange[1].toLocalDate() : java.time.LocalDate.now();

        // 解析 Sprint ID（燃尽图需要）
        Long sprintId = null;
        if (config.getFilters() != null && config.getFilters().getSprintId() != null) {
            try {
                sprintId = Long.parseLong(config.getFilters().getSprintId());
            } catch (NumberFormatException ignore) {}
        }

        // 解析 issueFilter（自由查询语法）
        List<Long> issueIds = resolveIssueFilterIds(config, projectIds);

        switch (reportType) {
            case BURNDOWN, BURNDOWN_CHART -> {
                if (sprintId == null) {
                    // 燃尽图必须指定 Sprint，尝试查找活跃 Sprint
                    sprintId = findActiveSprintId(projectIds);
                }
                if (sprintId == null) {
                    // 无可用 Sprint，返回空数据
                    result.setChartType("line");
                    result.setDates(List.of());
                    result.setSeries(List.of());
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("message", "没有找到可用的 Sprint");
                    result.setSummary(summary);
                    return result;
                }
                executeBurndownReport(result, report.getProjectId(), sprintId);
            }
            case CUMULATIVE_FLOW -> executeCumulativeFlowReport(result, projectIds, startDate, endDate, issueIds);
            case RESOLUTION_TIME -> executeResolutionTimeReport(result, projectIds, startDate, endDate, config, issueIds);
            case AVERAGE_ISSUE_AGE -> executeAverageIssueAgeReport(result, projectIds, startDate, endDate, config, issueIds);
            case FIXED_VS_REPORTED, VERIFIED_VS_REOPENED, RESOLVED_VS_NEW -> {
                // 比率对比报表：委托给 ReportStatisticsService
                ReportExecuteResultVO rateResult = reportStatisticsService.getRateComparisonData(
                        reportType, projectIds, startDate, endDate, issueIds);
                result.setChartType(rateResult.getChartType());
                result.setDates(rateResult.getDates());
                result.setSeries(rateResult.getSeries());
                result.setSummary(rateResult.getSummary());
            }
            default -> {
                // Fallback to distribution for unknown timeline types
                return executeDistributionReport(report, projectIds);
            }
        }

        // 设置应用的筛选摘要
        if (config.hasFilters() || config.getTimeRange() != null) {
            result.setAppliedFilters(buildFilterSummary(config));
        }

        return result;
    }

    /**
     * 执行燃尽图报表
     */
    private void executeBurndownReport(ReportExecuteResultVO result, Long projectId, Long sprintId) {
        result.setChartType("line");

        BurndownVO burndown = reportStatisticsService.getBurndownData(projectId, sprintId);

        result.setDates(burndown.getDates());
        result.setIdealLine(burndown.getIdeal());

        // 构建 series
        List<ReportExecuteResultVO.TimeSeriesData> series = new ArrayList<>();

        ReportExecuteResultVO.TimeSeriesData actualSeries = new ReportExecuteResultVO.TimeSeriesData();
        actualSeries.setName("剩余工单");
        actualSeries.setColor("#58a6ff");
        actualSeries.setData(burndown.getActual() != null
                ? burndown.getActual().stream().map(v -> (Number) v).collect(Collectors.toList())
                : List.of());
        actualSeries.setSeriesType("line");
        series.add(actualSeries);

        ReportExecuteResultVO.TimeSeriesData idealSeries = new ReportExecuteResultVO.TimeSeriesData();
        idealSeries.setName("理想线");
        idealSeries.setColor("#6b7280");
        idealSeries.setData(burndown.getIdeal() != null
                ? burndown.getIdeal().stream().map(v -> (Number) v).collect(Collectors.toList())
                : List.of());
        idealSeries.setSeriesType("line");
        series.add(idealSeries);

        result.setSeries(series);
        result.setTotal(burndown.getTotalIssues());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sprintName", burndown.getSprintName());
        summary.put("totalIssues", burndown.getTotalIssues());
        result.setSummary(summary);
    }

    /**
     * 执行累积流图报表
     */
    private void executeCumulativeFlowReport(ReportExecuteResultVO result, List<Long> projectIds,
                                              java.time.LocalDate startDate, java.time.LocalDate endDate,
                                              List<Long> issueIds) {
        result.setChartType("stacked_area");

        CumulativeFlowVO cfd = reportStatisticsService.getCumulativeFlowData(projectIds, startDate, endDate, issueIds);

        result.setDates(cfd.getDates());

        List<ReportExecuteResultVO.TimeSeriesData> series = new ArrayList<>();
        if (cfd.getSeries() != null) {
            for (CumulativeFlowVO.StatusSeries statusSeries : cfd.getSeries()) {
                ReportExecuteResultVO.TimeSeriesData ts = new ReportExecuteResultVO.TimeSeriesData();
                ts.setName(statusSeries.getName());
                ts.setColor(statusSeries.getColor());
                ts.setData(statusSeries.getData() != null
                        ? statusSeries.getData().stream().map(v -> (Number) v).collect(Collectors.toList())
                        : List.of());
                ts.setSeriesType("area");
                series.add(ts);
            }
        }
        result.setSeries(series);
    }

    /**
     * 执行解决时间分析报表
     */
    private void executeResolutionTimeReport(ReportExecuteResultVO result, List<Long> projectIds,
                                              java.time.LocalDate startDate, java.time.LocalDate endDate,
                                              ReportConfig config, List<Long> issueIds) {
        result.setChartType("line");

        // 从 config 的 groupBy 中获取解决时间的分组（可选）
        String groupBy = config.getGroupBy();
        ResolutionTimeVO rt = reportStatisticsService.getResolutionTimeData(projectIds, startDate, endDate, groupBy, issueIds);

        result.setDates(rt.getDates());

        List<ReportExecuteResultVO.TimeSeriesData> series = new ArrayList<>();

        // 平均解决时间
        ReportExecuteResultVO.TimeSeriesData avgSeries = new ReportExecuteResultVO.TimeSeriesData();
        avgSeries.setName("平均解决时间(h)");
        avgSeries.setColor("#58a6ff");
        avgSeries.setData(rt.getAvgHours() != null
                ? rt.getAvgHours().stream().map(v -> (Number) v).collect(Collectors.toList())
                : List.of());
        avgSeries.setSeriesType("line");
        series.add(avgSeries);

        // 中位解决时间
        ReportExecuteResultVO.TimeSeriesData medianSeries = new ReportExecuteResultVO.TimeSeriesData();
        medianSeries.setName("中位解决时间(h)");
        medianSeries.setColor("#3fb950");
        medianSeries.setData(rt.getMedianHours() != null
                ? rt.getMedianHours().stream().map(v -> (Number) v).collect(Collectors.toList())
                : List.of());
        medianSeries.setSeriesType("line");
        series.add(medianSeries);

        // P90 解决时间
        ReportExecuteResultVO.TimeSeriesData p90Series = new ReportExecuteResultVO.TimeSeriesData();
        p90Series.setName("P90 解决时间(h)");
        p90Series.setColor("#d29922");
        p90Series.setData(rt.getP90Hours() != null
                ? rt.getP90Hours().stream().map(v -> (Number) v).collect(Collectors.toList())
                : List.of());
        p90Series.setSeriesType("line");
        series.add(p90Series);

        result.setSeries(series);

        // 概览信息
        Map<String, Object> summary = new LinkedHashMap<>();
        long totalResolved = rt.getResolvedCount() != null
                ? rt.getResolvedCount().stream().mapToLong(Long::longValue).sum()
                : 0L;
        summary.put("totalResolved", totalResolved);
        if (rt.getGroupDetails() != null && !rt.getGroupDetails().isEmpty()) {
            summary.put("groupDetails", rt.getGroupDetails());
        }
        result.setSummary(summary);
        result.setTotal(totalResolved);
    }

    /**
     * 执行平均工单年龄报表（Average Issue Age）
     * 追踪工单在指定状态中的平均停留时间趋势
     */
    private void executeAverageIssueAgeReport(ReportExecuteResultVO result, List<Long> projectIds,
                                               java.time.LocalDate startDate, java.time.LocalDate endDate,
                                               ReportConfig config, List<Long> issueIds) {
        result.setChartType("line");

        List<String> trackedStatuses = config.getTrackedStatuses();
        String granularity = config.getGranularity();
        Integer movingPeriod = config.getMovingPeriod();

        AverageIssueAgeVO ageData = reportStatisticsService.getAverageIssueAgeData(
                projectIds, startDate, endDate, trackedStatuses, granularity, movingPeriod, issueIds);

        result.setDates(ageData.getDates());

        List<ReportExecuteResultVO.TimeSeriesData> series = new ArrayList<>();

        // 当日平均停留时间
        ReportExecuteResultVO.TimeSeriesData avgSeries = new ReportExecuteResultVO.TimeSeriesData();
        avgSeries.setName("平均停留时间(h)");
        avgSeries.setColor("#58a6ff");
        avgSeries.setData(ageData.getAvgAgeHours() != null
                ? ageData.getAvgAgeHours().stream().map(v -> (Number) v).collect(Collectors.toList())
                : List.of());
        avgSeries.setSeriesType("line");
        series.add(avgSeries);

        // 滑动平均
        ReportExecuteResultVO.TimeSeriesData movingAvgSeries = new ReportExecuteResultVO.TimeSeriesData();
        movingAvgSeries.setName("滑动平均(h)");
        movingAvgSeries.setColor("#3fb950");
        movingAvgSeries.setData(ageData.getMovingAvgHours() != null
                ? ageData.getMovingAvgHours().stream().map(v -> (Number) v).collect(Collectors.toList())
                : List.of());
        movingAvgSeries.setSeriesType("line");
        series.add(movingAvgSeries);

        // 滑动最小
        ReportExecuteResultVO.TimeSeriesData movingMinSeries = new ReportExecuteResultVO.TimeSeriesData();
        movingMinSeries.setName("滑动最小(h)");
        movingMinSeries.setColor("#6b7280");
        movingMinSeries.setData(ageData.getMovingMinHours() != null
                ? ageData.getMovingMinHours().stream().map(v -> (Number) v).collect(Collectors.toList())
                : List.of());
        movingMinSeries.setSeriesType("line");
        series.add(movingMinSeries);

        // 滑动最大
        ReportExecuteResultVO.TimeSeriesData movingMaxSeries = new ReportExecuteResultVO.TimeSeriesData();
        movingMaxSeries.setName("滑动最大(h)");
        movingMaxSeries.setColor("#d29922");
        movingMaxSeries.setData(ageData.getMovingMaxHours() != null
                ? ageData.getMovingMaxHours().stream().map(v -> (Number) v).collect(Collectors.toList())
                : List.of());
        movingMaxSeries.setSeriesType("line");
        series.add(movingMaxSeries);

        result.setSeries(series);

        // 概览信息
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("trackedStatuses", ageData.getTrackedStatuses());
        summary.put("granularity", granularity != null ? granularity : "day");
        summary.put("movingPeriod", movingPeriod != null ? movingPeriod : 7);
        // 流入/流出统计
        long totalOutflow = ageData.getOutflowCount() != null
                ? ageData.getOutflowCount().stream().mapToLong(Long::longValue).sum() : 0L;
        long totalStaying = ageData.getStayingCount() != null && !ageData.getStayingCount().isEmpty()
                ? ageData.getStayingCount().get(ageData.getStayingCount().size() - 1) : 0L;
        summary.put("totalOutflow", totalOutflow);
        summary.put("currentStaying", totalStaying);
        summary.put("outflowByDate", ageData.getOutflowCount());
        summary.put("stayingByDate", ageData.getStayingCount());
        result.setSummary(summary);
    }

    /**
     * 执行状态转换统计报表
     */
    private ReportExecuteResultVO executeStateTransitionReport(ReportDefinition report, List<Long> projectIds) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setCategory("state_transition");
        result.setChartType("bar_horizontal");
        result.setCalculatedAt(LocalDateTime.now());
        result.setRefreshInterval(config.getRefreshInterval());

        // 解析时间范围
        LocalDateTime[] timeRange = config.resolveTimeRange();
        LocalDateTime start = timeRange != null ? timeRange[0] : LocalDateTime.now().minusDays(30);
        LocalDateTime end = timeRange != null ? timeRange[1] : LocalDateTime.now();

        // 解析 issueFilter（自由查询语法）
        List<Long> issueIds = resolveIssueFilterIds(config, projectIds);

        // 查询状态转换数据（基于 issue_activity 表）
        List<ReportExecuteResultVO.StateTransitionItem> transitions =
                reportStatisticsService.getStateTransitionData(projectIds, start, end, issueIds);

        result.setTransitions(transitions);

        // 同时提供 labels + data 给前端用于条形图渲染
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        long total = 0;
        for (ReportExecuteResultVO.StateTransitionItem item : transitions) {
            labels.add(item.getFromStatus() + " → " + item.getToStatus());
            data.add(item.getCount());
            total += item.getCount();
        }
        result.setLabels(labels);
        result.setData(data);
        result.setTotal(total);

        // 设置筛选摘要
        if (config.hasFilters() || config.getTimeRange() != null) {
            result.setAppliedFilters(buildFilterSummary(config));
        }

        return result;
    }

    /**
     * 执行时间管理类报表（时间报表、预估对比）
     * 委托给 ReportStatisticsService 执行实际数据计算，然后适配为 ReportExecuteResultVO 格式
     */
    private ReportExecuteResultVO executeTimeManagementReport(ReportDefinition report, ReportType reportType, List<Long> projectIds) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setCategory("time_management");
        result.setCalculatedAt(LocalDateTime.now());
        result.setRefreshInterval(config.getRefreshInterval());

        switch (reportType) {
            case TIME_REPORT -> executeTimeReport(result, config, projectIds);
            case ESTIMATION_REPORT -> executeEstimationReport(result, projectIds);
            default -> {
                // Fallback to distribution
                return executeDistributionReport(report, projectIds);
            }
        }

        // 设置筛选摘要
        if (config.hasFilters() || config.getTimeRange() != null) {
            result.setAppliedFilters(buildFilterSummary(config));
        }

        return result;
    }

    /**
     * 执行时间报表：按人员/项目/工作类型汇总工时，生成趋势数据
     */
    private void executeTimeReport(ReportExecuteResultVO result, ReportConfig config, List<Long> projectIds) {
        result.setChartType("bar_horizontal");

        // 解析时间范围
        LocalDateTime[] timeRange = config.resolveTimeRange();
        java.time.LocalDate startDate = timeRange != null ? timeRange[0].toLocalDate() : java.time.LocalDate.now().minusDays(29);
        java.time.LocalDate endDate = timeRange != null ? timeRange[1].toLocalDate() : java.time.LocalDate.now();

        String startStr = startDate.toString();
        String endStr = endDate.toString();

        // 按人员分组数据（作为主维度）
        List<TimeByUserRow> byUserRows = reportStatisticsMapper.selectTimeByUser(projectIds, startStr, endStr);
        int totalMinutes = byUserRows.stream().mapToInt(r -> r.getTotalMinutes() != null ? r.getTotalMinutes() : 0).sum();

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        for (TimeByUserRow row : byUserRows) {
            labels.add(row.getUserName());
            data.add((long) (row.getTotalMinutes() != null ? row.getTotalMinutes() : 0));
        }
        result.setLabels(labels);
        result.setData(data);
        result.setTotal(totalMinutes);

        // 每日工时趋势（作为时间序列）
        List<TimeTrendRow> trendRows = reportStatisticsMapper.selectTimeTrend(projectIds, startStr, endStr);
        Map<String, Integer> trendByDay = new HashMap<>();
        for (TimeTrendRow row : trendRows) {
            trendByDay.put(row.getWorkDate(), row.getTotalMinutes() != null ? row.getTotalMinutes() : 0);
        }
        List<String> trendDates = new ArrayList<>();
        List<Number> trendMinutes = new ArrayList<>();
        java.time.LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            trendDates.add(current.toString());
            trendMinutes.add(trendByDay.getOrDefault(current.toString(), 0));
            current = current.plusDays(1);
        }
        result.setDates(trendDates);

        ReportExecuteResultVO.TimeSeriesData trendSeries = new ReportExecuteResultVO.TimeSeriesData();
        trendSeries.setName("每日工时(分钟)");
        trendSeries.setColor("#58a6ff");
        trendSeries.setData(trendMinutes);
        trendSeries.setSeriesType("area");
        result.setSeries(List.of(trendSeries));

        // summary 信息
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalMinutes", totalMinutes);
        summary.put("totalHours", Math.round(totalMinutes / 60.0 * 100.0) / 100.0);
        summary.put("userCount", byUserRows.size());
        summary.put("dateRange", startStr + " ~ " + endStr);
        result.setSummary(summary);
    }

    /**
     * 执行预估对比报表：估时 vs 实际花费
     */
    private void executeEstimationReport(ReportExecuteResultVO result, List<Long> projectIds) {
        result.setChartType("bar_horizontal");

        // 按项目汇总
        List<EstimationSummaryRow> summaryRows = reportStatisticsMapper.selectEstimationSummary(projectIds);

        double totalEstimated = 0;
        double totalSpent = 0;
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();

        for (EstimationSummaryRow row : summaryRows) {
            double est = row.getEstimatedHoursSum() != null ? row.getEstimatedHoursSum() : 0;
            double spt = row.getSpentHoursSum() != null ? row.getSpentHoursSum() : 0;
            totalEstimated += est;
            totalSpent += spt;
            labels.add(row.getProjectName());
            data.add(Math.round(spt * 100) / 100L);  // 实际花费小时数（四舍五入到整数）
        }

        result.setLabels(labels);
        result.setData(data);
        result.setTotal(Math.round(totalSpent));

        // summary 信息
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEstimatedHours", Math.round(totalEstimated * 100.0) / 100.0);
        summary.put("totalSpentHours", Math.round(totalSpent * 100.0) / 100.0);
        summary.put("overallDeviationRate", totalEstimated > 0 ? Math.round((totalSpent / totalEstimated - 1) * 1000.0) / 1000.0 : 0);
        summary.put("projectCount", summaryRows.size());
        result.setSummary(summary);
    }

    /**
     * 查找给定项目范围内的活跃 Sprint ID
     */
    private Long findActiveSprintId(List<Long> projectIds) {
        LambdaQueryWrapper<Sprint> query = new LambdaQueryWrapper<>();
        query.eq(Sprint::getStatus, SprintStatus.ACTIVE);
        if (projectIds != null && !projectIds.isEmpty()) {
            query.in(Sprint::getProjectId, projectIds);
        }
        query.last("LIMIT 1");
        Sprint sprint = sprintMapper.selectOne(query);
        return sprint != null ? sprint.getId() : null;
    }

    /**
     * 单维度执行
     */
    private void executeSingleMode(ReportQueryParams params, ReportExecuteResultVO result) {
        List<ReportGroupRow> rows = reportStatisticsMapper.selectReportGrouped(params);

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        long total = 0;

        for (ReportGroupRow row : rows) {
            labels.add(row.getLabel());
            long cnt = row.getCnt() != null ? row.getCnt() : 0L;
            data.add(cnt);
            total += cnt;
        }

        result.setLabels(labels);
        result.setData(data);
        result.setTotal(total);
    }

    /**
     * 双维度交叉执行
     * 将 (primaryLabel, secondaryLabel, cnt) 行数据转为矩阵
     */
    private void executeCrossMode(ReportQueryParams params, ReportExecuteResultVO result) {
        List<ReportCrossRow> rows = reportStatisticsMapper.selectReportCross(params);

        // 收集所有唯一的 primary 和 secondary labels（保持出现顺序）
        LinkedHashSet<String> primarySet = new LinkedHashSet<>();
        LinkedHashSet<String> secondarySet = new LinkedHashSet<>();
        for (ReportCrossRow row : rows) {
            primarySet.add(row.getPrimaryLabel());
            secondarySet.add(row.getSecondaryLabel());
        }

        List<String> primaryLabels = new ArrayList<>(primarySet);
        List<String> secondaryLabels = new ArrayList<>(secondarySet);

        // 构建 lookup map
        Map<String, Map<String, Long>> lookupMap = new HashMap<>();
        for (ReportCrossRow row : rows) {
            lookupMap.computeIfAbsent(row.getPrimaryLabel(), k -> new HashMap<>())
                    .put(row.getSecondaryLabel(), row.getCnt() != null ? row.getCnt() : 0L);
        }

        // 构建矩阵 matrix[i][j]
        List<List<Long>> matrix = new ArrayList<>();
        long total = 0;
        for (String primary : primaryLabels) {
            List<Long> matrixRow = new ArrayList<>();
            Map<String, Long> secondaryMap = lookupMap.getOrDefault(primary, Map.of());
            for (String secondary : secondaryLabels) {
                long cnt = secondaryMap.getOrDefault(secondary, 0L);
                matrixRow.add(cnt);
                total += cnt;
            }
            matrix.add(matrixRow);
        }

        result.setLabels(primaryLabels);
        result.setSecondLabels(secondaryLabels);
        result.setMatrix(matrix);
        result.setTotal(total);
    }

    /**
     * 构建传给 Mapper 的报表查询参数
     */
    private ReportQueryParams buildQueryParams(ReportConfig config, String groupBy, List<Long> projectIds) {
        ReportQueryParams params = new ReportQueryParams();
        params.setGroupBy(groupBy);
        params.setSortBy(config.getSortBy());

        if (projectIds != null) {
            params.setProjectIds(projectIds);
        }

        // 时间范围
        LocalDateTime[] timeRange = config.resolveTimeRange();
        if (timeRange != null) {
            params.setTimeStart(timeRange[0]);
            params.setTimeEnd(timeRange[1]);
            params.setTimeField(config.getTimeField());
        }

        // 筛选条件
        ReportConfig.ReportFilters filters = config.getFilters();
        if (filters != null) {
            if (filters.getStatuses() != null && !filters.getStatuses().isEmpty()) {
                params.setStatuses(filters.getStatuses());
            }
            if (filters.getStatusesExclude() != null && !filters.getStatusesExclude().isEmpty()) {
                params.setStatusesExclude(filters.getStatusesExclude());
            }
            if (filters.getPriorities() != null && !filters.getPriorities().isEmpty()) {
                params.setPriorities(filters.getPriorities());
            }
            if (filters.getIssueTypes() != null && !filters.getIssueTypes().isEmpty()) {
                params.setIssueTypes(filters.getIssueTypes());
            }
            if (filters.getSprintId() != null) {
                try {
                    params.setSprintId(Long.parseLong(filters.getSprintId()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid sprintId in report config: {}", filters.getSprintId());
                }
            }
            if (filters.getAssignees() != null && !filters.getAssignees().isEmpty()) {
                // assignees 可能是 userId 字符串列表，转为 Long
                List<Long> assigneeIds = filters.getAssignees().stream()
                        .map(s -> {
                            try { return Long.parseLong(s); }
                            catch (NumberFormatException e) { return null; }
                        })
                        .filter(Objects::nonNull)
                        .toList();
                if (!assigneeIds.isEmpty()) {
                    params.setAssigneeIds(assigneeIds);
                }
            }

            // ── 语义快捷筛选 ──

            // statusClosed: true=仅已关闭, false=排除已关闭
            if (filters.getStatusClosed() != null) {
                Set<Long> closedIds = statusCacheHelper.getClosedStatusIds();
                if (!closedIds.isEmpty()) {
                    params.setClosedStatusIds(closedIds);
                    if (Boolean.TRUE.equals(filters.getStatusClosed())) {
                        params.setOnlyClosedStatus(true);
                    } else {
                        params.setExcludeClosedStatus(true);
                    }
                }
            }

            // unassigned: true=仅未分配
            if (Boolean.TRUE.equals(filters.getUnassigned())) {
                params.setUnassigned(true);
            }

            // overdue: true=仅逾期（due_date < today 且未关闭）
            if (Boolean.TRUE.equals(filters.getOverdue())) {
                params.setOverdue(true);
                // 需要排除已关闭的工单
                Set<Long> closedIds = statusCacheHelper.getClosedStatusIds();
                if (!closedIds.isEmpty()) {
                    params.setClosedStatusIds(closedIds);
                }
            }

            // activeSprint: true=仅当前活跃 Sprint 的工单
            if (Boolean.TRUE.equals(filters.getActiveSprint())) {
                // 查询所有活跃 Sprint（跨项目），取第一个
                LambdaQueryWrapper<Sprint> sprintQuery = new LambdaQueryWrapper<>();
                sprintQuery.eq(Sprint::getStatus, SprintStatus.ACTIVE);
                if (projectIds != null && !projectIds.isEmpty()) {
                    sprintQuery.in(Sprint::getProjectId, projectIds);
                }
                List<Sprint> activeSprints = sprintMapper.selectList(sprintQuery);
                if (!activeSprints.isEmpty()) {
                    List<Long> activeSprintIds = activeSprints.stream()
                            .map(Sprint::getId).toList();
                    params.setActiveSprintIds(activeSprintIds);
                } else {
                    // 没有活跃 Sprint，返回空结果（sprintId 设为不存在的值）
                    params.setSprintId(-1L);
                }
            }
        }

        // ── Issue Filter 自由查询语法 ──
        resolveIssueFilterToParams(config, projectIds, params);

        return params;
    }

    /**
     * 解析 config.issueFilter（自由查询语法 JSON 数组），通过 QueryExecutor 得到匹配的 Issue ID 列表，
     * 注入到 params.issueIds 中。与 ReportStatisticsController.dashboard 使用相同逻辑。
     */
    @SuppressWarnings("unchecked")
    private void resolveIssueFilterToParams(ReportConfig config, List<Long> projectIds, ReportQueryParams params) {
        List<Long> issueIds = resolveIssueFilterIds(config, projectIds);
        if (issueIds != null) {
            params.setIssueIds(issueIds);
        }
    }

    /**
     * 解析 config.issueFilter 为 Issue ID 列表。
     * @return 匹配的 Issue ID 列表，null 表示无筛选
     */
    @SuppressWarnings("unchecked")
    private List<Long> resolveIssueFilterIds(ReportConfig config, List<Long> projectIds) {
        String issueFilter = config.getIssueFilter();
        if (issueFilter == null || issueFilter.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> filters = objectMapper.readValue(issueFilter,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            if (filters.isEmpty()) {
                return null;
            }
            return queryExecutor.executeFilterToIds(filters, projectIds);
        } catch (Exception e) {
            log.warn("Failed to parse issueFilter in report config: {}", issueFilter, e);
            return null;
        }
    }

    /**
     * 构建筛选条件摘要（用于前端展示）
     */
    private Map<String, Object> buildFilterSummary(ReportConfig config) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (config.getTimeRange() != null) {
            ReportConfig.ReportTimeRange tr = config.getTimeRange();
            if ("dynamic".equals(tr.getType())) {
                summary.put("timeRange", tr.getPreset());
            } else {
                summary.put("timeRange", tr.getStartDate() + " ~ " + tr.getEndDate());
            }
            summary.put("timeField", tr.getField());
        }
        // issueFilter 自由查询语法
        if (config.getIssueFilter() != null && !config.getIssueFilter().isBlank()) {
            summary.put("issueFilter", config.getIssueFilter());
        }
        ReportConfig.ReportFilters filters = config.getFilters();
        if (filters != null) {
            if (filters.getStatuses() != null) summary.put("statuses", filters.getStatuses());
            if (filters.getStatusesExclude() != null) summary.put("statusesExclude", filters.getStatusesExclude());
            if (filters.getPriorities() != null) summary.put("priorities", filters.getPriorities());
            if (filters.getIssueTypes() != null) summary.put("issueTypes", filters.getIssueTypes());
            if (filters.getAssignees() != null) summary.put("assignees", filters.getAssignees());
            if (filters.getSprintId() != null) summary.put("sprintId", filters.getSprintId());
            if (filters.getStatusClosed() != null) summary.put("statusClosed", filters.getStatusClosed());
            if (Boolean.TRUE.equals(filters.getUnassigned())) summary.put("unassigned", true);
            if (Boolean.TRUE.equals(filters.getOverdue())) summary.put("overdue", true);
            if (Boolean.TRUE.equals(filters.getActiveSprint())) summary.put("activeSprint", true);
        }
        return summary;
    }

    /**
     * 将执行结果转为 CSV 字符串
     */
    private String buildCsv(ReportExecuteResultVO result) {
        StringBuilder sb = new StringBuilder();

        // 时间序列模式（Timeline 报表）
        if (result.getDates() != null && result.getSeries() != null && !result.getSeries().isEmpty()) {
            // Header: "日期", series1.name, series2.name, ...
            sb.append("\"日期\"");
            for (ReportExecuteResultVO.TimeSeriesData series : result.getSeries()) {
                sb.append(",\"").append(escapeCsv(series.getName())).append("\"");
            }
            sb.append("\n");

            // Data rows
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
            // 双维度模式：行=primary, 列=secondary
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    /**
     * 获取可用的分组维度列表（内置 + 项目的自定义字段）
     *
     * @param projectId 项目 ID（可选），传入时额外返回该项目可用的自定义字段维度
     * @return 可用维度列表
     */
    public List<ReportGroupByOptionVO> getAvailableGroupByDimensions(Long projectId) {
        List<ReportGroupByOptionVO> dimensions = new ArrayList<>();

        // 内置维度
        for (ReportGroupBy builtin : ReportGroupBy.values()) {
            dimensions.add(new ReportGroupByOptionVO(
                    builtin.getValue(), builtin.getLabel(), "builtin", null));
        }

        // 自定义字段维度
        List<CustomFieldDefinition> fields;
        if (projectId != null) {
            fields = customFieldService.listByProject(projectId, null);
        } else {
            // 全局报表：只返回 is_for_all=true 的全局字段
            fields = customFieldService.listGlobalFields();
        }

        // 可分组的字段类型：list, string, user, int（排除 text, datetime, bool 等不适合分组的类型）
        Set<String> groupableFormats = Set.of("list", "string", "user", "int");

        for (CustomFieldDefinition field : fields) {
            if (groupableFormats.contains(field.getFieldFormat())) {
                dimensions.add(new ReportGroupByOptionVO(
                        ReportGroupBy.CUSTOM_FIELD_PREFIX + field.getId(),
                        field.getName(),
                        "custom_field",
                        field.getFieldFormat()));
            }
        }

        return dimensions;
    }
}
