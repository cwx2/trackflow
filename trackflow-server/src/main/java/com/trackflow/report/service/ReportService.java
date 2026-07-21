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
import com.trackflow.report.dto.CreateReportDTO;
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
import com.trackflow.report.vo.ReportExecuteResultVO;
import com.trackflow.report.vo.ReportGroupByOptionVO;
import com.trackflow.report.vo.ReportShareVO;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
        return new ReportListMetadata(reports, shareCountMap, favoriteIds);
    }

    /**
     * 报表列表查询结果元数据
     */
    public record ReportListMetadata(
            List<ReportDefinition> reports,
            Map<Long, Integer> shareCountMap,
            Set<Long> favoriteIds
    ) {}

    /**
     * 创建报表（带权限校验）
     * - projectId 非空：需要 project:edit 权限
     * - projectId 为空（全局报表）：需要系统管理员权限
     * 创建后自动添加到创建者的收藏列表（参照 YouTrack 行为）
     */
    @Transactional
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

    @Transactional
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

    // ─── 收藏管理 ────────────────────────────────────────

    /**
     * 切换报表收藏状态（收藏/取消收藏）
     * 需要对报表有查看权限才能收藏
     *
     * @param reportId 报表ID
     * @param userId   当前用户ID
     * @return true=已收藏, false=已取消收藏
     */
    @Transactional
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
    @Transactional
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

        List<Long> scopeProjectIds = resolveExecutionScope(report, userId);
        ReportExecuteResultVO result = executeInternal(report, scopeProjectIds);

        org.apache.poi.xssf.streaming.SXSSFWorkbook workbook = buildExcelWorkbook(result, report.getName());
        return new ExcelExportResult(report.getName(), workbook);
    }

    /**
     * Excel 导出结果封装
     */
    public record ExcelExportResult(String reportName, org.apache.poi.xssf.streaming.SXSSFWorkbook workbook) {}

    /**
     * 执行报表并返回结果（带权限校验），供导出使用
     */
    private ReportExecuteResultVO executeForExport(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }
        assertExportAccess(report, id, userId);

        List<Long> scopeProjectIds = resolveExecutionScope(report, userId);
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
    @Transactional
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
        if (dto.getType() != null) {
            report.setType(dto.getType());
        }
        if (dto.getConfig() != null) {
            report.setConfig(dto.getConfig());
        }
        if (dto.getShared() != null) {
            report.setShared(dto.getShared());
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

    @Transactional
    public void delete(Long id) {
        reportMapper.deleteById(id);
    }

    /**
     * 删除报表（带权限校验）
     */
    @Transactional
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
    @Transactional
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
    @Transactional
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
     * 执行报表（带权限校验）
     * 对全局报表（projectId=null），按当前用户可访问的项目范围限制数据。
     * 系统管理员可查看任何报表。
     */
    public ReportExecuteResultVO executeWithAccessCheck(Long id, Long userId) {
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

        List<Long> scopeProjectIds = resolveExecutionScope(report, userId);
        return executeInternal(report, scopeProjectIds);
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
     *
     * @param report     报表定义
     * @param projectIds 项目范围限制（null 表示不限制——仅内部调用允许）
     */
    private ReportExecuteResultVO executeInternal(ReportDefinition report, List<Long> projectIds) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        // 校验并规范化 groupBy
        String groupBy = config.getGroupBy();
        if (!ReportGroupBy.isValid(groupBy)) {
            groupBy = "status";
        }

        // 构建查询参数 Map（使用传入的 projectIds 限制范围）
        Map<String, Object> params = buildQueryParams(config, groupBy, projectIds);

        // 如果是自定义字段分组，解析 fieldId 并添加到 params
        if (ReportGroupBy.isCustomFieldFormat(groupBy)) {
            Long cfId = ReportGroupBy.extractCustomFieldId(groupBy);
            params.put("customFieldId", cfId);
            params.put("isCustomFieldGroupBy", true);
            // 获取字段定义，判断是否为 list 类型（需要 JOIN option 表获取显示值）
            CustomFieldDefinition cfDef = customFieldService.getDefinitionById(cfId);
            if (cfDef != null && "list".equals(cfDef.getFieldFormat())) {
                params.put("customFieldIsListType", true);
            }
            if (cfDef != null && "user".equals(cfDef.getFieldFormat())) {
                params.put("customFieldIsUserType", true);
            }
        }

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setGroupBy(groupBy);
        result.setChartType(config.getChartType());

        // 是否为双维度交叉模式
        String secondGroupBy = config.getSecondGroupBy();
        boolean isCrossMode = secondGroupBy != null && !secondGroupBy.isBlank()
                && ReportGroupBy.isValid(secondGroupBy);

        if (isCrossMode) {
            result.setSecondGroupBy(secondGroupBy);
            params.put("secondGroupBy", secondGroupBy);
            // 如果第二维度也是自定义字段
            if (ReportGroupBy.isCustomFieldFormat(secondGroupBy)) {
                Long cfId2 = ReportGroupBy.extractCustomFieldId(secondGroupBy);
                params.put("secondCustomFieldId", cfId2);
                params.put("isSecondCustomFieldGroupBy", true);
                CustomFieldDefinition cfDef2 = customFieldService.getDefinitionById(cfId2);
                if (cfDef2 != null && "list".equals(cfDef2.getFieldFormat())) {
                    params.put("secondCustomFieldIsListType", true);
                }
                if (cfDef2 != null && "user".equals(cfDef2.getFieldFormat())) {
                    params.put("secondCustomFieldIsUserType", true);
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
     * 单维度执行
     */
    private void executeSingleMode(Map<String, Object> params, ReportExecuteResultVO result) {
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
    private void executeCrossMode(Map<String, Object> params, ReportExecuteResultVO result) {
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
     * 构建传给 Mapper 的查询参数 Map
     */
    private Map<String, Object> buildQueryParams(ReportConfig config, String groupBy, List<Long> projectIds) {
        Map<String, Object> params = new HashMap<>();
        params.put("groupBy", groupBy);
        params.put("sortBy", config.getSortBy());

        if (projectIds != null) {
            params.put("projectIds", projectIds);
        }

        // 时间范围
        LocalDateTime[] timeRange = config.resolveTimeRange();
        if (timeRange != null) {
            params.put("timeStart", timeRange[0]);
            params.put("timeEnd", timeRange[1]);
            params.put("timeField", config.getTimeField());
        }

        // 筛选条件
        ReportConfig.ReportFilters filters = config.getFilters();
        if (filters != null) {
            if (filters.getStatuses() != null && !filters.getStatuses().isEmpty()) {
                params.put("statuses", filters.getStatuses());
            }
            if (filters.getStatusesExclude() != null && !filters.getStatusesExclude().isEmpty()) {
                params.put("statusesExclude", filters.getStatusesExclude());
            }
            if (filters.getPriorities() != null && !filters.getPriorities().isEmpty()) {
                params.put("priorities", filters.getPriorities());
            }
            if (filters.getIssueTypes() != null && !filters.getIssueTypes().isEmpty()) {
                params.put("issueTypes", filters.getIssueTypes());
            }
            if (filters.getSprintId() != null) {
                try {
                    params.put("sprintId", Long.parseLong(filters.getSprintId()));
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
                    params.put("assigneeIds", assigneeIds);
                }
            }

            // ── 语义快捷筛选 ──

            // statusClosed: true=仅已关闭, false=排除已关闭
            if (filters.getStatusClosed() != null) {
                Set<Long> closedIds = statusCacheHelper.getClosedStatusIds();
                if (!closedIds.isEmpty()) {
                    if (Boolean.TRUE.equals(filters.getStatusClosed())) {
                        params.put("closedStatusIds", closedIds);
                        params.put("onlyClosedStatus", true);
                    } else {
                        params.put("closedStatusIds", closedIds);
                        params.put("excludeClosedStatus", true);
                    }
                }
            }

            // unassigned: true=仅未分配
            if (Boolean.TRUE.equals(filters.getUnassigned())) {
                params.put("unassigned", true);
            }

            // overdue: true=仅逾期（due_date < today 且未关闭）
            if (Boolean.TRUE.equals(filters.getOverdue())) {
                params.put("overdue", true);
                // 需要排除已关闭的工单
                Set<Long> closedIds = statusCacheHelper.getClosedStatusIds();
                if (!closedIds.isEmpty()) {
                    params.put("closedStatusIds", closedIds);
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
                    params.put("activeSprintIds", activeSprintIds);
                } else {
                    // 没有活跃 Sprint，返回空结果（sprintId 设为不存在的值）
                    params.put("sprintId", -1L);
                }
            }
        }

        return params;
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
        } else {
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
