package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.UpdateBoardCardConfigDTO;
import com.trackflow.board.entity.BoardCardConfig;
import com.trackflow.board.mapper.BoardCardConfigMapper;
import com.trackflow.board.vo.BoardCardConfigVO;
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

@Service
@RequiredArgsConstructor
public class BoardCardConfigService {

    /** 允许的卡片字段名称集合 */
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "assignee", "priority", "type", "tags", "dueDate", "sprint", "estimatedHours"
    );

    /** 默认显示的字段 */
    private static final List<String> DEFAULT_VISIBLE_FIELDS = List.of("assignee", "priority", "type");

    /** 默认颜色方案 */
    private static final String DEFAULT_COLOR_SCHEME = "none";

    private final BoardCardConfigMapper boardCardConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * 获取项目的看板卡片配置。
     * 如果没有配置记录，返回默认配置（不写入数据库）。
     */
    public BoardCardConfigVO getCardConfig(Long projectId) {
        BoardCardConfig config = boardCardConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardCardConfig>()
                        .eq(BoardCardConfig::getProjectId, projectId)
        );

        BoardCardConfigVO vo = new BoardCardConfigVO();
        if (config != null) {
            vo.setVisibleFields(parseVisibleFields(config.getVisibleFields()));
            vo.setColorScheme(config.getColorScheme());
        } else {
            vo.setVisibleFields(DEFAULT_VISIBLE_FIELDS);
            vo.setColorScheme(DEFAULT_COLOR_SCHEME);
        }
        return vo;
    }

    /**
     * 保存项目的看板卡片配置（upsert 语义）。
     */
    @Transactional
    public void saveCardConfig(Long projectId, UpdateBoardCardConfigDTO dto) {
        // 校验字段名称合法性
        for (String field : dto.getVisibleFields()) {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "无效的卡片字段: " + field + "，允许的字段: " + ALLOWED_FIELDS);
            }
        }

        BoardCardConfig existing = boardCardConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardCardConfig>()
                        .eq(BoardCardConfig::getProjectId, projectId)
        );

        LocalDateTime now = LocalDateTime.now();
        String fieldsJson = serializeVisibleFields(dto.getVisibleFields());

        if (existing != null) {
            existing.setVisibleFields(fieldsJson);
            existing.setColorScheme(dto.getColorScheme());
            existing.setUpdatedAt(now);
            boardCardConfigMapper.updateById(existing);
        } else {
            BoardCardConfig config = new BoardCardConfig();
            config.setProjectId(projectId);
            config.setVisibleFields(fieldsJson);
            config.setColorScheme(dto.getColorScheme());
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            boardCardConfigMapper.insert(config);
        }
    }

    // ========== Private helpers ==========

    private List<String> parseVisibleFields(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT_VISIBLE_FIELDS;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return DEFAULT_VISIBLE_FIELDS;
        }
    }

    private String serializeVisibleFields(List<String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            return "[\"assignee\",\"priority\",\"type\"]";
        }
    }
}
