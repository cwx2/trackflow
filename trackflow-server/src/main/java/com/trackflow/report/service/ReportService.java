package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.dto.CreateReportDTO;
import com.trackflow.report.dto.UpdateReportDTO;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.entity.ReportGroupBy;
import com.trackflow.report.entity.ReportType;
import com.trackflow.report.mapper.ReportDefinitionMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trackflow.report.vo.ReportExecuteResultVO;

import java.util.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDefinitionMapper reportMapper;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final SysUserMapper userMapper;
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
     * 更新报表（带权限校验）
     * 只有报表创建者或拥有 project:edit 权限的用户可以更新
     */
    @Transactional
    public ReportDefinition updateWithAccessCheck(Long id, UpdateReportDTO dto, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在");
        }

        // 权限校验：创建者可修改自己的报表
        if (!userId.equals(report.getCreatedBy())) {
            // 非创建者需要 project:edit 权限（项目管理员可修改任意报表）
            if (report.getProjectId() != null) {
                if (!permissionService.hasPermission(userId, report.getProjectId(), "project:edit")) {
                    throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只有报表创建者或项目管理员可以修改此报表");
                }
            } else {
                // 全局报表（无 projectId），只有系统管理员可修改他人的
                if (!permissionService.isSystemAdmin(userId)) {
                    throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只有报表创建者或系统管理员可以修改此报表");
                }
            }
        }

        // 校验报表类型合法性（仅当 dto 传入了 type）
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

        // 显式设置 updatedAt
        report.setUpdatedAt(LocalDateTime.now());

        reportMapper.updateById(report);
        return report;
    }

    /**
     * 校验报表配置的合法性
     * - groupBy 必须在白名单中
     * - 特定类型有固定 groupBy 要求时做一致性校验
     */
    private void validateConfig(String config, String type) {
        Map<String, Object> configMap = parseConfig(config);
        String groupBy = (String) configMap.get("groupBy");

        // 如果指定了 groupBy，必须合法
        if (groupBy != null && !groupBy.isBlank() && !ReportGroupBy.isValid(groupBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的分组维度: " + groupBy + "，允许值: " + ReportGroupBy.allowedValues());
        }

        // 类型与 groupBy 的一致性校验（有默认 groupBy 的类型不允许冲突）
        ReportType reportType = ReportType.fromValue(type);
        if (reportType != null && reportType.getDefaultGroupBy() != null) {
            if (groupBy != null && !groupBy.isBlank() && !groupBy.equals(reportType.getDefaultGroupBy())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "报表类型 " + type + " 的分组维度必须为 " + reportType.getDefaultGroupBy());
            }
        }
    }

    /**
     * 仅校验 groupBy 值的合法性（不校验与 type 的一致性）
     * 用于更新时只修改了 config 但没修改 type 的场景
     */
    private void validateGroupByLegality(String config) {
        Map<String, Object> configMap = parseConfig(config);
        String groupBy = (String) configMap.get("groupBy");
        if (groupBy != null && !groupBy.isBlank() && !ReportGroupBy.isValid(groupBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的分组维度: " + groupBy + "，允许值: " + ReportGroupBy.allowedValues());
        }
    }

    @Transactional
    public void delete(Long id) {
        reportMapper.deleteById(id);
    }

    /**
     * 删除报表（带权限校验）
     * 只有报表创建者或拥有 project:edit 权限的用户可以删除
     */
    @Transactional
    public void deleteWithAccessCheck(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found");

        // 校验所有权：创建者可删除自己的报表
        if (!userId.equals(report.getCreatedBy())) {
            // 非创建者需要 project:edit 权限（项目管理员可删除任意报表）
            if (report.getProjectId() != null) {
                if (!permissionService.hasPermission(userId, report.getProjectId(), "project:edit")) {
                    throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只有报表创建者或项目管理员可以删除此报表");
                }
            } else {
                // 全局报表（无 projectId），只有系统管理员可删除他人的
                if (!permissionService.isSystemAdmin(userId)) {
                    throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只有报表创建者或系统管理员可以删除此报表");
                }
            }
        }

        reportMapper.deleteById(id);
    }

    /**
     * 执行报表（带权限校验）
     * 共享报表：项目成员可执行
     * 私有报表：只有创建者可执行
     */
    public ReportExecuteResultVO executeWithAccessCheck(Long id, Long userId) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found");

        // 项目成员检查
        if (report.getProjectId() != null) {
            projectService.assertProjectMember(userId, report.getProjectId());
        }

        // 私有报表访问控制：非共享报表只有创建者可执行
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

    private ReportExecuteResultVO executeInternal(ReportDefinition report) {
        Map<String, Object> config = parseConfig(report.getConfig());
        String rawGroupBy = (String) config.getOrDefault("groupBy", "status");

        // 执行时校验 groupBy 合法性（防御已存在的脏数据）
        final String groupBy = ReportGroupBy.isValid(rawGroupBy) ? rawGroupBy : "status";

        // 构建查询条件
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Issue::getDeletedAt);
        if (report.getProjectId() != null) {
            wrapper.eq(Issue::getProjectId, report.getProjectId());
        }

        List<Issue> issues = issueMapper.selectList(wrapper);

        // 构建 ID→名称映射
        Map<Long, String> nameMap = buildNameMap(issues, groupBy);

        // 按 groupBy 分组统计（使用可读名称）
        Map<String, Long> grouped = issues.stream()
                .collect(Collectors.groupingBy(issue -> getGroupValue(issue, groupBy, nameMap), Collectors.counting()));

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setGroupBy(groupBy);
        result.setLabels(new ArrayList<>(grouped.keySet()));
        result.setData(new ArrayList<>(grouped.values()));
        result.setTotal(issues.size());
        return result;
    }

    /**
     * 构建 ID→可读名称映射（按需查询数据库）
     */
    private Map<Long, String> buildNameMap(List<Issue> issues, String groupBy) {
        return switch (groupBy) {
            case "status" -> {
                Set<Long> statusIds = issues.stream()
                        .map(Issue::getStatusId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (statusIds.isEmpty()) yield Map.of();
                yield statusMapper.selectBatchIds(statusIds).stream()
                        .collect(Collectors.toMap(IssueStatus::getId, IssueStatus::getName, (a, b) -> a));
            }
            case "assignee" -> {
                Set<Long> userIds = issues.stream()
                        .map(Issue::getAssigneeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (userIds.isEmpty()) yield Map.of();
                yield userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
            }
            default -> Map.of();
        };
    }

    private String getGroupValue(Issue issue, String groupBy, Map<Long, String> nameMap) {
        return switch (groupBy) {
            case "status" -> {
                Long statusId = issue.getStatusId();
                yield nameMap.getOrDefault(statusId, "未知状态");
            }
            case "priority" -> issue.getPriority() != null ? issue.getPriority() : "无";
            case "type" -> issue.getIssueType() != null ? issue.getIssueType() : "未分类";
            case "assignee" -> {
                Long assigneeId = issue.getAssigneeId();
                if (assigneeId == null) yield "未分配";
                yield nameMap.getOrDefault(assigneeId, "未知用户");
            }
            default -> "其他";
        };
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
