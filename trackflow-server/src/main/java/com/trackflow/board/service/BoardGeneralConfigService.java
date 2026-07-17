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
import java.util.Set;

/**
 * 看板基本设置服务。
 * 管理看板名称和访问权限配置。
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

    private final BoardGeneralConfigMapper boardGeneralConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * 获取项目的看板基本设置。
     * 如果没有配置记录，返回默认配置（不写入数据库）。
     */
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
        } else {
            vo.setName("");
            vo.setCanViewRoles(DEFAULT_CAN_VIEW_ROLES);
            vo.setCanEditRoles(DEFAULT_CAN_EDIT_ROLES);
        }
        return vo;
    }

    /**
     * 保存项目的看板基本设置（upsert 语义）。
     */
    @Transactional
    public void saveGeneralConfig(Long projectId, UpdateBoardGeneralConfigDTO dto) {
        // 校验角色代码合法性
        validateRoleCodes(dto.getCanViewRoles(), "查看权限");
        validateRoleCodes(dto.getCanEditRoles(), "编辑权限");

        BoardGeneralConfig existing = boardGeneralConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardGeneralConfig>()
                        .eq(BoardGeneralConfig::getProjectId, projectId)
        );

        LocalDateTime now = LocalDateTime.now();
        String name = dto.getName() != null ? dto.getName().trim() : "";
        String canViewJson = serializeRoles(dto.getCanViewRoles());
        String canEditJson = serializeRoles(dto.getCanEditRoles());

        if (existing != null) {
            existing.setName(name);
            existing.setCanViewRoles(canViewJson);
            existing.setCanEditRoles(canEditJson);
            existing.setUpdatedAt(now);
            boardGeneralConfigMapper.updateById(existing);
        } else {
            BoardGeneralConfig config = new BoardGeneralConfig();
            config.setProjectId(projectId);
            config.setName(name);
            config.setCanViewRoles(canViewJson);
            config.setCanEditRoles(canEditJson);
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            boardGeneralConfigMapper.insert(config);
        }
    }

    // ========== Private helpers ==========

    private void validateRoleCodes(List<String> roles, String context) {
        for (String role : roles) {
            if (!VALID_ROLE_CODES.contains(role)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        context + "包含无效的角色代码: " + role + "，允许的角色: " + VALID_ROLE_CODES);
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
