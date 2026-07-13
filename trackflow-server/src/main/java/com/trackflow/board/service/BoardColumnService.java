package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.UpdateBoardColumnsDTO;
import com.trackflow.board.entity.BoardColumnConfig;
import com.trackflow.board.mapper.BoardColumnConfigMapper;
import com.trackflow.board.vo.BoardColumnVO;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardColumnService {

    private final BoardColumnConfigMapper boardColumnConfigMapper;
    private final IssueStatusMapper issueStatusMapper;

    /**
     * 获取项目的看板列配置
     * 如果项目没有自定义配置，返回所有状态的默认配置（全部可见，按 sortOrder 排序）
     */
    public List<BoardColumnVO> getColumns(Long projectId) {
        // 获取所有状态
        List<IssueStatus> allStatuses = issueStatusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>().orderByAsc(IssueStatus::getSortOrder)
        );

        // 获取项目的自定义配置
        List<BoardColumnConfig> configs = boardColumnConfigMapper.selectList(
                new LambdaQueryWrapper<BoardColumnConfig>()
                        .eq(BoardColumnConfig::getProjectId, projectId)
                        .orderByAsc(BoardColumnConfig::getSortOrder)
        );

        // 如果项目没有配置，返回默认配置（全部可见）
        if (configs.isEmpty()) {
            return allStatuses.stream().map(status -> {
                BoardColumnVO vo = new BoardColumnVO();
                vo.setStatusId(String.valueOf(status.getId()));
                vo.setStatusName(status.getName());
                vo.setStatusCode(status.getCode());
                vo.setStatusColor(status.getColor());
                vo.setStatusCategory(status.getCategory());
                vo.setVisible(true);
                vo.setSortOrder(status.getSortOrder());
                vo.setCollapsed(false);
                return vo;
            }).collect(Collectors.toList());
        }

        // 已有配置：按配置返回
        Map<Long, BoardColumnConfig> configMap = configs.stream()
                .collect(Collectors.toMap(BoardColumnConfig::getStatusId, c -> c));

        // 构建结果列表，按 config 的 sortOrder 排序
        List<BoardColumnVO> result = new ArrayList<>();
        for (IssueStatus status : allStatuses) {
            BoardColumnConfig config = configMap.get(status.getId());
            BoardColumnVO vo = new BoardColumnVO();
            vo.setStatusId(String.valueOf(status.getId()));
            vo.setStatusName(status.getName());
            vo.setStatusCode(status.getCode());
            vo.setStatusColor(status.getColor());
            vo.setStatusCategory(status.getCategory());

            if (config != null) {
                vo.setVisible(config.getVisible());
                vo.setSortOrder(config.getSortOrder());
                vo.setCollapsed(config.getCollapsed());
            } else {
                // 新增的状态默认可见
                vo.setVisible(true);
                vo.setSortOrder(status.getSortOrder() + 1000);
                vo.setCollapsed(false);
            }
            result.add(vo);
        }

        // 按 sortOrder 排序
        result.sort(Comparator.comparingInt(BoardColumnVO::getSortOrder));
        return result;
    }

    /**
     * 保存项目的看板列配置
     */
    @Transactional
    public void saveColumns(Long projectId, UpdateBoardColumnsDTO dto) {
        // 删除旧配置
        boardColumnConfigMapper.delete(
                new LambdaQueryWrapper<BoardColumnConfig>()
                        .eq(BoardColumnConfig::getProjectId, projectId)
        );

        // 插入新配置
        LocalDateTime now = LocalDateTime.now();
        int order = 0;
        for (UpdateBoardColumnsDTO.ColumnItem item : dto.getColumns()) {
            BoardColumnConfig config = new BoardColumnConfig();
            config.setProjectId(projectId);
            config.setStatusId(item.getStatusId());
            config.setVisible(item.getVisible());
            config.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order);
            config.setCollapsed(item.getCollapsed() != null ? item.getCollapsed() : false);
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            boardColumnConfigMapper.insert(config);
            order++;
        }
    }
}
