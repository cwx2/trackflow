package com.trackflow.board.service;

import com.trackflow.board.dto.SaveBoardSettingsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看板设置聚合服务 - 协调多个子配置 Service 的批量保存操作，提供事务保证
 *
 * @author TrackFlow
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class BoardSettingsService {

    private final BoardConfigVersionService boardConfigVersionService;
    private final BoardColumnService boardColumnService;
    private final BoardCardConfigService boardCardConfigService;
    private final BoardSwimlaneConfigService boardSwimlaneConfigService;
    private final BoardColumnMergeService boardColumnMergeService;
    private final BoardGeneralConfigService boardGeneralConfigService;
    private final BoardChartConfigService boardChartConfigService;

    /**
     * 批量保存项目看板所有设置（原子操作 + 乐观锁）。
     * <p>
     * 将列设置、卡片配置、泳道配置、列合并、基本设置合并为一次事务，
     * 仅做一次版本检查，确保并发安全。任一子配置保存失败则全部回滚。
     *
     * @param projectId 项目ID
     * @param dto       批量保存请求体
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAllSettings(Long projectId, SaveBoardSettingsDTO dto) {
        // 一次性版本检查
        boardConfigVersionService.checkAndIncrement(projectId, dto.getConfigVersion());

        // 依次保存各配置（同一事务中）
        boardColumnService.saveColumns(projectId, dto.getColumns());
        boardCardConfigService.saveCardConfig(projectId, dto.getCardConfig());
        boardSwimlaneConfigService.saveSwimlaneConfig(projectId, dto.getSwimlaneConfig());
        boardColumnMergeService.saveColumnMerges(projectId, dto.getColumnMerges());
        boardGeneralConfigService.saveGeneralConfig(projectId, dto.getGeneralConfig());

        // 图表配置为可选，有值时才保存
        if (dto.getChartConfig() != null) {
            boardChartConfigService.saveChartConfig(projectId, dto.getChartConfig());
        }
    }
}
