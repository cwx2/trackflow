package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.UpdateBoardGeneralConfigDTO;
import com.trackflow.board.entity.BoardGeneralConfig;
import com.trackflow.board.mapper.BoardGeneralConfigMapper;
import com.trackflow.board.vo.BoardGeneralConfigVO;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 看板基本设置服务。
 * 管理看板名称、访问权限和 Board Behavior 配置（含查询过滤）。
 */
@Service
@RequiredArgsConstructor
public class BoardGeneralConfigService {

    /** 系统支持的角色代码集合 */
    private static final Set<String> VALID_ROLE_CODES = Set.of(
            "project_admin", "tech_lead", "developer", "product_manager", "tester", "observer"
    );

    /** 默认可查看角色：所有项目角色 */
    private static final List<String> DEFAULT_CAN_VIEW_ROLES = List.of(
            "project_admin", "tech_lead", "developer", "product_manager", "tester", "observer"
    );

    /** 默认可编辑角色：项目管理员 + 技术负责人 */
    private static final List<String> DEFAULT_CAN_EDIT_ROLES = List.of(
            "project_admin", "tech_lead"
    );

    /** 允许在 filter_query 中使用的字段名白名单 */
    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "status", "priority", "assignee", "reporter", "type",
            "sprint", "keyword", "dueDate", "createdAt", "updatedAt"
    );

    /** 允许的筛选操作符白名单 */
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "eq", "neq", "in", "not_in", "is_empty", "is_not_empty",
            "contains", "gt", "gte", "lt", "lte", "between",
            "open", "closed"
    );

    private final BoardGeneralConfigMapper boardGeneralConfigMapper;
    private final ObjectMapper objectMapper;
    private final BoardAccessService boardAccessService;
    private final BoardConfigVersionService boardConfigVersionService;

    /**
     * 获取项目的看板基本设置。
     * 如果没有配置记录，返回默认配置（不写入数据库）。
     * 同时计算当前用户的 canView / canEdit 权限。
     * <p>
     * 性能说明：board_general_config 表仅查询 1 次，查到的对象直接传给
     * {@link BoardAccessService#computePermissions} 以避免重复 SELECT，
     * 用户角色查询也在一次权限计算中完成。
     */
    @Transactional(readOnly = true)
    public BoardGeneralConfigVO getGeneralConfig(Long projectId) {
        BoardGeneralConfig config = boardGeneralConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardGeneralConfig>()
                        .eq(BoardGeneralConfig::getProjectId, projectId)
        );

        BoardGeneralConfigVO vo = new BoardGeneralConfigVO();
        if (config != null) {
            vo.setName(config.getName());
            vo.setCanViewRoles(parseRoles(config.getCanViewRoles()));
            vo.setCanEditRoles(parseRoles(config.getCanEditRoles()));
            vo.setFilterMode(config.getFilterMode() != null ? config.getFilterMode() : "all");
            vo.setFilterQuery(config.getFilterQuery());
            vo.setDoneRetentionDays(config.getDoneRetentionDays());
            vo.setColumnField(config.getColumnField() != null ? config.getColumnField() : "status");
        } else {
            vo.setName("");
            vo.setCanViewRoles(DEFAULT_CAN_VIEW_ROLES);
            vo.setCanEditRoles(DEFAULT_CAN_EDIT_ROLES);
            vo.setFilterMode("all");
            vo.setFilterQuery(null);
            vo.setDoneRetentionDays(null);
            vo.setColumnField("status");
        }

        // 一次性计算当前用户的看板权限（复用已查到的 config，避免 board_general_config 表被重复查询）
        BoardAccessService.BoardPermissions permissions = boardAccessService.computePermissions(projectId, config);
        vo.setCurrentUserCanView(permissions.canView());
        vo.setCurrentUserCanEdit(permissions.canEdit());

        // 附加看板配置版本号（用于乐观锁）
        vo.setConfigVersion(boardConfigVersionService.getCurrentVersion(projectId));

        return vo;
    }

    /**
     * 保存项目的看板基本设置（upsert 语义）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveGeneralConfig(Long projectId, UpdateBoardGeneralConfigDTO dto) {
        validateRoleCodes(dto.getCanViewRoles(), "查看权限");
        validateRoleCodes(dto.getCanEditRoles(), "编辑权限");

        String filterMode = dto.getFilterMode() != null ? dto.getFilterMode() : "all";
        String filterQuery = dto.getFilterQuery();

        if ("query".equals(filterMode)) {
            if (filterQuery == null || filterQuery.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "过滤模式为按查询过滤时查询条件不能为空");
            }
            validateFilterQuery(filterQuery);
        } else {
            filterQuery = null;
        }

        BoardGeneralConfig existing = boardGeneralConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardGeneralConfig>()
                        .eq(BoardGeneralConfig::getProjectId, projectId)
        );

        LocalDateTime now = LocalDateTime.now();
        String name = dto.getName() != null ? dto.getName().trim() : "";
        String canViewJson = serializeRoles(dto.getCanViewRoles());
        String canEditJson = serializeRoles(dto.getCanEditRoles());
        Integer doneRetentionDays = dto.getDoneRetentionDays();
        String columnField = dto.getColumnField() != null ? dto.getColumnField() : "status";

        if (existing != null) {
            existing.setName(name);
            existing.setCanViewRoles(canViewJson);
            existing.setCanEditRoles(canEditJson);
            existing.setFilterMode(filterMode);
            existing.setFilterQuery(filterQuery);
            existing.setDoneRetentionDays(doneRetentionDays);
            existing.setColumnField(columnField);
            existing.setUpdatedAt(now);
            boardGeneralConfigMapper.updateById(existing);
        } else {
            BoardGeneralConfig config = new BoardGeneralConfig();
            config.setProjectId(projectId);
            config.setName(name);
            config.setCanViewRoles(canViewJson);
            config.setCanEditRoles(canEditJson);
            config.setFilterMode(filterMode);
            config.setFilterQuery(filterQuery);
            config.setDoneRetentionDays(doneRetentionDays);
            config.setColumnField(columnField);
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            boardGeneralConfigMapper.insert(config);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateFilterQuery(String filterQueryJson) {
        List<?> filters;
        try {
            filters = objectMapper.readValue(filterQueryJson, List.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "查询条件格式错误必须是合法的JSON数组");
        }

        if (filters.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "查询条件不能为空数组");
        }

        for (Object item : filters) {
            if (!(item instanceof Map)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "查询条件每项必须是对象");
            }
            Map<?, ?> filter = (Map<?, ?>) item;
            Object fieldObj = filter.get("field");
            Object operatorObj = filter.get("operator");
            Object value = filter.get("value");

            String field = fieldObj != null ? fieldObj.toString() : null;
            String operator = operatorObj != null ? operatorObj.toString() : null;

            if (field == null || field.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "查询条件中field字段不能为空");
            }

            if (!ALLOWED_FILTER_FIELDS.contains(field)
                    && !field.startsWith("cf.")
                    && !field.startsWith("customField.")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的查询字段: " + field);
            }

            if (operator == null || !ALLOWED_OPERATORS.contains(operator)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的查询操作符: " + operator);
            }

            if (!"is_empty".equals(operator) && !"is_not_empty".equals(operator)
                    && !"open".equals(operator) && !"closed".equals(operator)) {
                if (value == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "查询条件的值不能为空: " + field);
                }
            }
        }
    }

    private void validateRoleCodes(List<String> roles, String context) {
        for (String role : roles) {
            if (!VALID_ROLE_CODES.contains(role)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        context + "包含无效的角色代码: " + role);
            }
        }
    }

    private List<String> parseRoles(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT_CAN_VIEW_ROLES;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return DEFAULT_CAN_VIEW_ROLES;
        }
    }

    private String serializeRoles(List<String> roles) {
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (Exception e) {
            return "[]";
        }
    }
}
