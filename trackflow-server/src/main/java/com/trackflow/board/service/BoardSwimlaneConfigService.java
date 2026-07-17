package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.UpdateBoardSwimlaneConfigDTO;
import com.trackflow.board.entity.BoardSwimlaneConfig;
import com.trackflow.board.mapper.BoardSwimlaneConfigMapper;
import com.trackflow.board.vo.BoardSwimlaneConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BoardSwimlaneConfigService {

    private static final String DEFAULT_GROUP_BY = "none";

    private final BoardSwimlaneConfigMapper swimlaneConfigMapper;

    /**
     * 获取项目的泳道配置。
     * 如果没有配置记录，返回默认值（不写入数据库）。
     */
    public BoardSwimlaneConfigVO getSwimlaneConfig(Long projectId) {
        BoardSwimlaneConfig config = swimlaneConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardSwimlaneConfig>()
                        .eq(BoardSwimlaneConfig::getProjectId, projectId)
        );

        BoardSwimlaneConfigVO vo = new BoardSwimlaneConfigVO();
        if (config != null) {
            vo.setGroupByField(config.getGroupByField());
        } else {
            vo.setGroupByField(DEFAULT_GROUP_BY);
        }
        return vo;
    }

    /**
     * 保存项目的泳道配置（upsert 语义）。
     */
    @Transactional
    public void saveSwimlaneConfig(Long projectId, UpdateBoardSwimlaneConfigDTO dto) {
        BoardSwimlaneConfig existing = swimlaneConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardSwimlaneConfig>()
                        .eq(BoardSwimlaneConfig::getProjectId, projectId)
        );

        LocalDateTime now = LocalDateTime.now();

        if (existing != null) {
            existing.setGroupByField(dto.getGroupByField());
            existing.setUpdatedAt(now);
            swimlaneConfigMapper.updateById(existing);
        } else {
            BoardSwimlaneConfig config = new BoardSwimlaneConfig();
            config.setProjectId(projectId);
            config.setGroupByField(dto.getGroupByField());
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            swimlaneConfigMapper.insert(config);
        }
    }
}
