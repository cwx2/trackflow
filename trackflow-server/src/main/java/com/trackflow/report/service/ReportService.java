package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.dto.CreateReportDTO;
import com.trackflow.report.dto.UpdateReportDTO;
import com.trackflow.report.entity.ReportConfig;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.entity.ReportGroupBy;
import com.trackflow.report.entity.ReportType;
import com.trackflow.report.mapper.ReportDefinitionMapper;
import com.trackflow.report.mapper.ReportStatisticsMapper;
import com.trackflow.report.mapper.result.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trackflow.report.vo.ReportExecuteResultVO;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDefinitionMapper reportMapper;
    private final ReportStatisticsMapper reportStatisticsMapper;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final PermissionService permissionService;

    /**
     * 报表列表（带项目成员过滤 + 私有报表隔离）
     * 返回条件：自己创建的 OR shared=true，且属于用户可访问的项目范围
     */
    public List<ReportDefinition> list(Long projectId, Long userId) {
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

        // 私有报表隔离：只能看到自己创建的私有报表，或共享的报表
        wrapper.and(w -> w.eq(ReportDefinition::getShared, true)
                .or().eq(ReportDefinition::getCreatedBy, userId));

        wrapper.orderByAsc(ReportDefinition::getName);
        return reportMapper.selectList(wrapper);
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

    /**
     * 克隆报表
     * 创建一份指定报表的副本，名称加"(副本)"后缀
     */
    @Transactional
    public ReportDefinition clone(Long id, Long userId) {
        ReportDefinition source = reportMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }

        // 项目可访问性检查（读操作）
        if (source.getProjectId() != null) {
            projectService.assertProjectAccessible(userId, source.getProjectId());
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
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }

        // 项目可访问性检查（读操作）
        if (report.getProjectId() != null) {
            projectService.assertProjectAccessible(userId, report.getProjectId());
        }

        // 私有报表访问控制
        if (!Boolean.TRUE.equals(report.getShared()) && !userId.equals(report.getCreatedBy())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问此私有报表");
        }

        ReportExecuteResultVO result = executeInternal(report);
        return buildCsv(result);
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
            // 权限校验：创建者可修改自己的报表
            if (!userId.equals(report.getCreatedBy())) {
                if (report.getProjectId() != null) {
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

        // 如果指定了 groupBy，必须合法
        if (groupBy != null && !groupBy.isBlank() && !ReportGroupBy.isValid(groupBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的分组维度: " + groupBy + "，允许值: " + ReportGroupBy.allowedValues());
        }

        // secondGroupBy 校验
        String secondGroupBy = (String) configMap.get("secondGroupBy");
        if (secondGroupBy != null && !secondGroupBy.isBlank() && !ReportGroupBy.isValid(secondGroupBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的第二分组维度: " + secondGroupBy + "，允许值: " + ReportGroupBy.allowedValues());
        }

        // 类型与 groupBy 的一致性校验
        ReportType reportType = ReportType.fromValue(type);
        if (reportType != null && reportType.getDefaultGroupBy() != null) {
            if (groupBy != null && !groupBy.isBlank() && !groupBy.equals(reportType.getDefaultGroupBy())) {
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
        String secondGroupBy = (String) configMap.get("secondGroupBy");
        if (secondGroupBy != null && !secondGroupBy.isBlank() && !ReportGroupBy.isValid(secondGroupBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的第二分组维度: " + secondGroupBy + "，允许值: " + ReportGroupBy.allowedValues());
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

        reportMapper.deleteById(id);
    }

    /**
     * 执行报表（带权限校验）
     */
    public ReportExecuteResultVO executeWithAccessCheck(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found");

        if (report.getProjectId() != null) {
            projectService.assertProjectAccessible(userId, report.getProjectId());
        }

        if (!Boolean.TRUE.equals(report.getShared()) && !userId.equals(report.getCreatedBy())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问此私有报表");
        }

        return executeInternal(report);
    }

    /**
     * 执行报表：根据报表配置生成数据
     */
    public ReportExecuteResultVO execute(Long id) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found");
        return executeInternal(report);
    }

    /**
     * 增强版执行引擎
     * 支持：timeRange筛选 + filters组合筛选 + 双维度交叉 + 按项目分组 + chartType
     */
    private ReportExecuteResultVO executeInternal(ReportDefinition report) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        // 校验并规范化 groupBy
        String groupBy = config.getGroupBy();
        if (!ReportGroupBy.isValid(groupBy)) {
            groupBy = "status";
        }

        // 构建项目 ID 列表
        List<Long> projectIds = report.getProjectId() != null
                ? List.of(report.getProjectId())
                : null;

        // 构建查询参数 Map
        Map<String, Object> params = buildQueryParams(config, groupBy, projectIds);

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
            executeCrossMode(params, result);
        } else {
            executeSingleMode(params, result);
        }

        // 设置应用的筛选摘要
        if (config.hasFilters() || config.getTimeRange() != null) {
            result.setAppliedFilters(buildFilterSummary(config));
        }

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
}
