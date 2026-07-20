package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
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
import com.trackflow.report.mapper.ReportShareMapper;
import com.trackflow.report.mapper.ReportStatisticsMapper;
import com.trackflow.report.mapper.result.*;
import com.trackflow.report.vo.ReportExecuteResultVO;
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
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final PermissionService permissionService;
    private final StatusCacheHelper statusCacheHelper;
    private final SprintMapper sprintMapper;
    private final SysUserMapper sysUserMapper;
    private final UserGroupMapper userGroupMapper;

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
     * 创建报表（带权限校验）
     * - projectId 非空：需要 project:edit 权限
     * - projectId 为空（全局报表）：需要系统管理员权限
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
        return create(dto);
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

        // 私有报表访问控制（含精细化共享）
        if (!Boolean.TRUE.equals(report.getShared())
                && !userId.equals(report.getCreatedBy())
                && reportShareMapper.countAccessByUser(id, userId) == 0) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问此私有报表");
        }

        List<Long> scopeProjectIds = resolveExecutionScope(report, userId);
        ReportExecuteResultVO result = executeInternal(report, scopeProjectIds);
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

        // 删除共享记录（DB 有 ON DELETE CASCADE，但显式删除更清晰）
        reportShareMapper.delete(new LambdaQueryWrapper<ReportShare>()
                .eq(ReportShare::getReportId, id));
        reportMapper.deleteById(id);
    }

    // ─── 共享管理 ────────────────────────────────────────

    /**
     * 设置报表共享（覆盖模式：传入全量共享列表）
     */
    @Transactional
    public List<ReportShareVO> setShares(Long reportId, ShareReportDTO dto, Long userId) {
        ReportDefinition report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }
        // 只有报表创建者可以管理共享
        if (!report.getCreatedBy().equals(userId)) {
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
     */
    public List<ReportShareVO> getShares(Long reportId, Long userId) {
        ReportDefinition report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }
        // 只有创建者可以查看完整共享列表
        if (!report.getCreatedBy().equals(userId)) {
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
     */
    @Transactional
    public void removeShare(Long reportId, Long shareId, Long userId) {
        ReportDefinition report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }
        if (!report.getCreatedBy().equals(userId)) {
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
     */
    public ReportExecuteResultVO executeWithAccessCheck(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found");

        if (report.getProjectId() != null) {
            projectService.assertProjectAccessible(userId, report.getProjectId());
        }

        if (!Boolean.TRUE.equals(report.getShared())
                && !userId.equals(report.getCreatedBy())
                && reportShareMapper.countAccessByUser(id, userId) == 0) {
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
     * 支持：timeRange筛选 + filters组合筛选 + 双维度交叉 + 按项目分组 + chartType
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
}
