package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.time.LocalDateTime;
import java.time.LocalTime;
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
     * 分页查询审计日志
     */
    public PageResult<AuditLogVO> list(AuditLogQuery query) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getAction() != null && !query.getAction().isBlank()) {
            wrapper.eq(SysAuditLog::getAction, query.getAction());
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

        wrapper.orderByDesc(SysAuditLog::getCreatedAt);

        Page<SysAuditLog> page = auditLogMapper.selectPage(query.toPage(), wrapper);

        // 收集所有涉及的用户 ID 和角色 ID
        Set<Long> userIds = new HashSet<>();
        Set<Long> roleIds = new HashSet<>();

        for (SysAuditLog record : page.getRecords()) {
            userIds.add(record.getOperatorId());
            if ("user".equals(record.getTargetType())) {
                userIds.add(record.getTargetId());
            } else if ("role".equals(record.getTargetType())) {
                roleIds.add(record.getTargetId());
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

        // 转换为 VO
        Map<Long, String> finalUserNameMap = userNameMap;
        Map<Long, String> finalRoleNameMap = roleNameMap;

        List<AuditLogVO> voList = page.getRecords().stream().map(record -> {
            AuditLogVO vo = new AuditLogVO();
            vo.setId(String.valueOf(record.getId()));
            vo.setOperatorId(String.valueOf(record.getOperatorId()));
            vo.setOperatorName(finalUserNameMap.getOrDefault(record.getOperatorId(), ""));
            vo.setAction(record.getAction());
            vo.setTargetType(record.getTargetType());
            vo.setTargetId(String.valueOf(record.getTargetId()));
            vo.setDetails(record.getDetails());
            vo.setIpAddress(record.getIpAddress());
            vo.setCreatedAt(record.getCreatedAt());

            // 填充目标名称
            if ("user".equals(record.getTargetType())) {
                vo.setTargetName(finalUserNameMap.getOrDefault(record.getTargetId(), ""));
            } else if ("role".equals(record.getTargetType())) {
                vo.setTargetName(finalRoleNameMap.getOrDefault(record.getTargetId(), ""));
            }

            return vo;
        }).toList();

        return new PageResult<>(voList, page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }
}
