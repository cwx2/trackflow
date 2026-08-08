package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.system.dto.AuditLogQuery;
import com.trackflow.system.entity.SysAuditLog;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysAuditLogMapper;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.vo.AuditLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统审计日志服务
 * <p>
 * 记录权限变更等敏感操作，不可删除/篡改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAuditService {

    private final SysAuditLogMapper auditLogMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final com.trackflow.project.mapper.ProjectMapper projectMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 记录审计日志
     *
     * @param action     操作类型
     * @param targetType 目标类型（user/role）
     * @param targetId   目标 ID
     * @param details    变更详情 Map
     */
    public void log(String action, String targetType, Long targetId, Map<String, Object> details) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        if (operatorId == null) {
            log.warn("审计日志记录失败：无法获取当前操作者 ID，action={}, targetType={}, targetId={}",
                    action, targetType, targetId);
            return;
        }
        log(operatorId, action, targetType, targetId, details);
    }

    /**
     * 记录审计日志（由调用方显式提供 operatorId）。
     * <p>
     * 用于异步场景（如 AOP 切面在异步线程中写审计日志）——此时 SecurityContext 不可用，
     * 需由主线程提前捕获 userId 后传入。
     *
     * @param operatorId 操作者用户 ID
     * @param action     操作类型
     * @param targetType 目标类型
     * @param targetId   目标 ID
     * @param details    变更详情 Map
     */
    public void log(Long operatorId, String action, String targetType, Long targetId, Map<String, Object> details) {
        if (operatorId == null) {
            log.warn("审计日志记录失败：operatorId 为空，action={}, targetType={}, targetId={}",
                    action, targetType, targetId);
            return;
        }

        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setOperatorId(operatorId);
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setCreatedAt(LocalDateTime.now());

        if (details != null && !details.isEmpty()) {
            try {
                auditLog.setDetails(objectMapper.writeValueAsString(details));
            } catch (Exception e) {
                log.error("审计日志序列化 details 失败", e);
                auditLog.setDetails(null);
            }
        }

        auditLogMapper.insert(auditLog);
    }

    /**
     * 记录认证安全事件审计日志。
     * <p>
     * 与 {@link #log} 不同，此方法不依赖 SecurityContext 获取 operatorId，
     * 因为认证事件发生时 SecurityContext 可能尚未设置或认证已失败。
     *
     * @param action     操作类型：login, login_failed, first_login, api_key_used, api_key_failed
     * @param userId     用户 ID（认证失败且无法识别用户时可为 null）
     * @param ipAddress  客户端 IP 地址
     * @param userAgent  客户端 User-Agent
     * @param details    附加详情（如登录方式、失败原因等）
     */
    public void logAuthEvent(String action, Long userId, String ipAddress,
                             String userAgent, Map<String, Object> details) {
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setOperatorId(userId);
        auditLog.setAction(action);
        auditLog.setTargetType("auth");
        auditLog.setTargetId(userId);
        auditLog.setIpAddress(ipAddress);
        auditLog.setUserAgent(userAgent);
        auditLog.setCreatedAt(LocalDateTime.now());

        if (details != null && !details.isEmpty()) {
            try {
                auditLog.setDetails(objectMapper.writeValueAsString(details));
            } catch (Exception e) {
                log.error("认证审计日志序列化 details 失败", e);
                auditLog.setDetails(null);
            }
        }

        try {
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // 审计日志写入失败不应阻塞认证流程
            log.error("认证审计日志写入失败: action={}, userId={}", action, userId, e);
        }
    }

    /**
     * 分页查询审计日志
     * <p>
     * 强制时间范围限制：查询范围不能超过 365 天。
     * 如果未指定时间范围，默认查询最近 30 天。
     * <p>
     * 支持文本搜索：
     * <ul>
     *   <li><code>author:xxx</code> — 按操作者名称筛选</li>
     *   <li><code>target:xxx</code> — 按目标名称筛选</li>
     *   <li>纯文本 — 模糊匹配操作者名称或目标名称</li>
     * </ul>
     */
    public PageResult<AuditLogVO> list(AuditLogQuery query) {
        // 强制时间范围限制
        enforceTimeRangeLimit(query);

        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getAction() != null && !query.getAction().isBlank()) {
            // 支持逗号分隔的多 action 筛选（如 "login,login_failed,api_key_used"）
            String[] actions = query.getAction().split(",");
            if (actions.length == 1) {
                wrapper.eq(SysAuditLog::getAction, actions[0].trim());
            } else {
                wrapper.in(SysAuditLog::getAction,
                        java.util.Arrays.stream(actions).map(String::trim).toList());
            }
        }
        if (query.getTargetType() != null && !query.getTargetType().isBlank()) {
            wrapper.eq(SysAuditLog::getTargetType, query.getTargetType());
        }
        if (query.getOperatorId() != null) {
            wrapper.eq(SysAuditLog::getOperatorId, query.getOperatorId());
        }
        if (query.getTargetId() != null) {
            wrapper.eq(SysAuditLog::getTargetId, query.getTargetId());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(SysAuditLog::getCreatedAt, query.getStartDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.le(SysAuditLog::getCreatedAt, query.getEndDate().atTime(LocalTime.MAX));
        }

        // 文本搜索处理
        applySearchFilter(wrapper, query.getSearch());

        wrapper.orderByDesc(SysAuditLog::getCreatedAt);

        Page<SysAuditLog> page = auditLogMapper.selectPage(query.toPage(), wrapper);

        // 收集所有涉及的用户 ID 和角色 ID（跳过 null）
        Set<Long> userIds = new HashSet<>();
        Set<Long> roleIds = new HashSet<>();
        Set<Long> projectIds = new HashSet<>();

        for (SysAuditLog record : page.getRecords()) {
            if (record.getOperatorId() != null) {
                userIds.add(record.getOperatorId());
            }
            if ("user".equals(record.getTargetType()) && record.getTargetId() != null) {
                userIds.add(record.getTargetId());
            } else if ("role".equals(record.getTargetType()) && record.getTargetId() != null) {
                roleIds.add(record.getTargetId());
            } else if ("auth".equals(record.getTargetType()) && record.getTargetId() != null) {
                userIds.add(record.getTargetId());
            } else if ("project".equals(record.getTargetType()) && record.getTargetId() != null) {
                projectIds.add(record.getTargetId());
            }
        }

        // 批量查询用户名
        Map<Long, String> userNameMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            userNameMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(SysUser::getId,
                            u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
        }

        // 批量查询角色名
        Map<Long, String> roleNameMap = Collections.emptyMap();
        if (!roleIds.isEmpty()) {
            roleNameMap = roleMapper.selectBatchIds(roleIds).stream()
                    .collect(Collectors.toMap(SysRole::getId, SysRole::getName));
        }

        // 批量查询项目名
        Map<Long, String> projectNameMap = Collections.emptyMap();
        if (!projectIds.isEmpty()) {
            projectNameMap = projectMapper.selectBatchIds(projectIds).stream()
                    .collect(Collectors.toMap(
                            com.trackflow.project.entity.Project::getId,
                            p -> p.getName() + " (" + p.getKey() + ")"));
        }

        // 转换为 VO
        Map<Long, String> finalUserNameMap = userNameMap;
        Map<Long, String> finalRoleNameMap = roleNameMap;
        Map<Long, String> finalProjectNameMap = projectNameMap;

        List<AuditLogVO> voList = page.getRecords().stream().map(record -> {
            AuditLogVO vo = new AuditLogVO();
            vo.setId(String.valueOf(record.getId()));
            vo.setOperatorId(record.getOperatorId() != null ? String.valueOf(record.getOperatorId()) : null);
            vo.setOperatorName(record.getOperatorId() != null
                    ? finalUserNameMap.getOrDefault(record.getOperatorId(), "") : null);
            vo.setAction(record.getAction());
            vo.setTargetType(record.getTargetType());
            vo.setTargetId(record.getTargetId() != null ? String.valueOf(record.getTargetId()) : null);
            vo.setDetails(record.getDetails());
            vo.setIpAddress(record.getIpAddress());
            vo.setUserAgent(record.getUserAgent());
            vo.setCreatedAt(record.getCreatedAt());

            // 填充目标名称
            if ("user".equals(record.getTargetType()) && record.getTargetId() != null) {
                vo.setTargetName(finalUserNameMap.getOrDefault(record.getTargetId(), ""));
            } else if ("role".equals(record.getTargetType()) && record.getTargetId() != null) {
                vo.setTargetName(finalRoleNameMap.getOrDefault(record.getTargetId(), ""));
            } else if ("auth".equals(record.getTargetType()) && record.getTargetId() != null) {
                vo.setTargetName(finalUserNameMap.getOrDefault(record.getTargetId(), ""));
            } else if ("project".equals(record.getTargetType()) && record.getTargetId() != null) {
                vo.setTargetName(finalProjectNameMap.getOrDefault(record.getTargetId(), ""));
            }

            return vo;
        }).toList();

        return new PageResult<>(voList, page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 导出指定时间范围的审计日志为 CSV 格式字符串
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return CSV 内容
     */
    public String exportAuditLogsCsv(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出审计日志必须指定开始日期和结束日期");
        }

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > 365) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出时间范围不能超过 365 天");
        }
        if (daysBetween < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }

        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        List<SysAuditLog> records = auditLogMapper.selectByTimeRange(startTime, endTime);

        // 批量查询用户名（用于 operator 列）
        Set<Long> userIds = new HashSet<>();
        for (SysAuditLog record : records) {
            if (record.getOperatorId() != null) {
                userIds.add(record.getOperatorId());
            }
        }
        Map<Long, String> userNameMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            userNameMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(SysUser::getId,
                            u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
        }

        // 构建 CSV
        StringBuilder csv = new StringBuilder();
        csv.append("ID,操作者,操作者ID,操作类型,目标类型,目标ID,IP地址,User-Agent,时间,详情\n");

        Map<Long, String> finalUserNameMap = userNameMap;
        for (SysAuditLog record : records) {
            csv.append(escapeCsvField(String.valueOf(record.getId()))).append(',');
            csv.append(escapeCsvField(record.getOperatorId() != null
                    ? finalUserNameMap.getOrDefault(record.getOperatorId(), "") : "")).append(',');
            csv.append(escapeCsvField(record.getOperatorId() != null
                    ? String.valueOf(record.getOperatorId()) : "")).append(',');
            csv.append(escapeCsvField(record.getAction())).append(',');
            csv.append(escapeCsvField(record.getTargetType() != null ? record.getTargetType() : "")).append(',');
            csv.append(escapeCsvField(record.getTargetId() != null
                    ? String.valueOf(record.getTargetId()) : "")).append(',');
            csv.append(escapeCsvField(record.getIpAddress() != null ? record.getIpAddress() : "")).append(',');
            csv.append(escapeCsvField(record.getUserAgent() != null ? record.getUserAgent() : "")).append(',');
            csv.append(escapeCsvField(record.getCreatedAt() != null
                    ? record.getCreatedAt().toString() : "")).append(',');
            csv.append(escapeCsvField(record.getDetails() != null ? record.getDetails() : ""));
            csv.append('\n');
        }

        return csv.toString();
    }

    /**
     * 获取审计日志导出记录总数（用于前端提示）
     */
    public long countByTimeRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);
        return auditLogMapper.selectCount(
                new LambdaQueryWrapper<SysAuditLog>()
                        .ge(SysAuditLog::getCreatedAt, startTime)
                        .le(SysAuditLog::getCreatedAt, endTime));
    }

    // --- private helpers ---

    /**
     * 应用文本搜索过滤。
     * <p>
     * 支持键值对语法和纯文本模糊匹配：
     * <ul>
     *   <li><code>author:xxx</code> — 按操作者 display_name/username 模糊匹配</li>
     *   <li><code>target:xxx</code> — 按目标用户 display_name/username 模糊匹配（target_type=user/auth 时）</li>
     *   <li>纯文本 — 同时模糊匹配操作者名称和目标名称（OR）</li>
     * </ul>
     */
    private void applySearchFilter(LambdaQueryWrapper<SysAuditLog> wrapper, String search) {
        if (search == null || search.isBlank()) {
            return;
        }

        String trimmed = search.trim();

        // 解析键值对语法
        if (trimmed.toLowerCase().startsWith("author:")) {
            String keyword = trimmed.substring(7).trim();
            if (!keyword.isEmpty()) {
                Set<Long> matchedUserIds = auditLogMapper.findUserIdsByNameLike(keyword);
                if (matchedUserIds.isEmpty()) {
                    // 无匹配：返回空结果
                    wrapper.eq(SysAuditLog::getId, -1L);
                } else {
                    wrapper.in(SysAuditLog::getOperatorId, matchedUserIds);
                }
            }
        } else if (trimmed.toLowerCase().startsWith("target:")) {
            String keyword = trimmed.substring(7).trim();
            if (!keyword.isEmpty()) {
                Set<Long> matchedUserIds = auditLogMapper.findUserIdsByNameLike(keyword);
                if (matchedUserIds.isEmpty()) {
                    wrapper.eq(SysAuditLog::getId, -1L);
                } else {
                    wrapper.in(SysAuditLog::getTargetId, matchedUserIds);
                }
            }
        } else {
            // 纯文本模式：匹配操作者名称 OR 目标名称
            Set<Long> matchedUserIds = auditLogMapper.findUserIdsByNameLike(trimmed);
            if (matchedUserIds.isEmpty()) {
                // 无用户名匹配：返回空结果
                wrapper.eq(SysAuditLog::getId, -1L);
            } else {
                wrapper.and(w -> w
                        .in(SysAuditLog::getOperatorId, matchedUserIds)
                        .or()
                        .in(SysAuditLog::getTargetId, matchedUserIds));
            }
        }
    }

    /**
     * 导出审计日志为 JSON 格式（最多 1000 条）
     * <p>
     * 按当前查询条件筛选导出。
     *
     * @param query 查询条件（复用现有筛选逻辑）
     * @return JSON 字符串
     */
    public String exportAuditLogsJson(AuditLogQuery query) {
        // 强制时间范围
        enforceTimeRangeLimit(query);
        // 限制导出数量
        query.setPage(1);
        query.setPageSize(1000);

        PageResult<AuditLogVO> result = list(query);
        try {
            return objectMapper.writeValueAsString(result.getList());
        } catch (Exception e) {
            log.error("导出审计日志 JSON 序列化失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    /**
     * 强制时间范围限制：
     * - 如果未指定日期范围，默认最近 30 天
     * - 如果指定的范围超过 365 天，截断为 365 天
     */
    private void enforceTimeRangeLimit(AuditLogQuery query) {
        LocalDate today = LocalDate.now();

        if (query.getStartDate() == null && query.getEndDate() == null) {
            // 未指定范围：默认最近 30 天
            query.setStartDate(today.minusDays(30));
            query.setEndDate(today);
        } else if (query.getStartDate() == null) {
            // 只指定了结束日期：从结束日期往前推 365 天
            query.setStartDate(query.getEndDate().minusDays(365));
        } else if (query.getEndDate() == null) {
            // 只指定了开始日期：到今天为止
            query.setEndDate(today);
        }

        // 检查范围不超过 365 天
        long daysBetween = ChronoUnit.DAYS.between(query.getStartDate(), query.getEndDate());
        if (daysBetween > 365) {
            // 截断：保留 endDate 不变，调整 startDate
            query.setStartDate(query.getEndDate().minusDays(365));
        }
    }

    /**
     * CSV 字段转义：包含逗号、引号或换行的字段需要用引号包裹
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
