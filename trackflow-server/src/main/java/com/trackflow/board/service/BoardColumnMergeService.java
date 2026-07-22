package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.UpdateBoardColumnMergeDTO;
import com.trackflow.board.entity.BoardColumnMerge;
import com.trackflow.board.mapper.BoardColumnMergeMapper;
import com.trackflow.board.vo.BoardColumnMergeGroupVO;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardColumnMergeService {

    private final BoardColumnMergeMapper columnMergeMapper;

    /**
     * 获取项目的列合并配置。
     * 返回合并组列表（无合并则返回空列表）。
     */
    public List<BoardColumnMergeGroupVO> getColumnMerges(Long projectId) {
        List<BoardColumnMerge> merges = columnMergeMapper.selectList(
                new LambdaQueryWrapper<BoardColumnMerge>()
                        .eq(BoardColumnMerge::getProjectId, projectId)
                        .orderByAsc(BoardColumnMerge::getMergeGroupId)
                        .orderByAsc(BoardColumnMerge::getSortOrder)
        );

        if (merges.isEmpty()) {
            return Collections.emptyList();
        }

        // 按 mergeGroupId 分组
        Map<String, List<BoardColumnMerge>> groupMap = merges.stream()
                .collect(Collectors.groupingBy(BoardColumnMerge::getMergeGroupId,
                        LinkedHashMap::new, Collectors.toList()));

        List<BoardColumnMergeGroupVO> result = new ArrayList<>();
        for (Map.Entry<String, List<BoardColumnMerge>> entry : groupMap.entrySet()) {
            BoardColumnMergeGroupVO vo = new BoardColumnMergeGroupVO();
            vo.setMergeGroupId(entry.getKey());
            vo.setMergeTitle(entry.getValue().get(0).getMergeTitle());
            vo.setStatusIds(entry.getValue().stream()
                    .map(m -> String.valueOf(m.getStatusId()))
                    .collect(Collectors.toList()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 保存项目的列合并配置（全量替换语义）。
     * 删除旧配置，插入新配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveColumnMerges(Long projectId, UpdateBoardColumnMergeDTO dto) {
        // 校验：同一状态不能出现在多个合并组中
        Set<Long> allStatusIds = new HashSet<>();
        for (UpdateBoardColumnMergeDTO.MergeGroupItem group : dto.getMergeGroups()) {
            if (group.getStatusIds().size() < 2) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "合并组「" + group.getMergeTitle() + "」至少需要包含 2 个状态");
            }
            for (Long statusId : group.getStatusIds()) {
                if (!allStatusIds.add(statusId)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "状态 ID " + statusId + " 不能同时出现在多个合并组中");
                }
            }
        }

        // 删除旧配置
        columnMergeMapper.delete(
                new LambdaQueryWrapper<BoardColumnMerge>()
                        .eq(BoardColumnMerge::getProjectId, projectId)
        );

        // 插入新配置
        LocalDateTime now = LocalDateTime.now();
        for (UpdateBoardColumnMergeDTO.MergeGroupItem group : dto.getMergeGroups()) {
            int sortOrder = 0;
            for (Long statusId : group.getStatusIds()) {
                BoardColumnMerge merge = new BoardColumnMerge();
                merge.setProjectId(projectId);
                merge.setMergeGroupId(group.getMergeGroupId());
                merge.setMergeTitle(group.getMergeTitle());
                merge.setStatusId(statusId);
                merge.setSortOrder(sortOrder++);
                merge.setCreatedAt(now);
                columnMergeMapper.insert(merge);
            }
        }
    }
}
