package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.UpdateBoardChartConfigDTO;
import com.trackflow.board.entity.BoardChartConfig;
import com.trackflow.board.mapper.BoardChartConfigMapper;
import com.trackflow.board.vo.BoardChartConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BoardChartConfigService {

    /** 默认图表类型 */
    private static final String DEFAULT_CHART_TYPE = "burndown";

    /** 默认计算方式 */
    private static final String DEFAULT_BURNDOWN_CALCULATION = "issue_count";

    /** 默认过滤模式 */
    private static final String DEFAULT_ISSUE_FILTER_MODE = "all_cards";

    private final BoardChartConfigMapper boardChartConfigMapper;

    /**
     * 获取项目的看板图表配置。
     * 如果没有配置记录，返回默认配置（不写入数据库）。
     */
    public BoardChartConfigVO getChartConfig(Long projectId) {
        BoardChartConfig config = boardChartConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardChartConfig>()
                        .eq(BoardChartConfig::getProjectId, projectId)
        );

        BoardChartConfigVO vo = new BoardChartConfigVO();
        if (config != null) {
            vo.setChartType(config.getChartType());
            vo.setBurndownCalculation(config.getBurndownCalculation());
            vo.setIssueFilterMode(config.getIssueFilterMode());
            vo.setIssueFilterQuery(config.getIssueFilterQuery());
            vo.setEstimationFieldId(config.getEstimationFieldId() != null
                    ? String.valueOf(config.getEstimationFieldId()) : null);
            vo.setOriginalEstimationFieldId(config.getOriginalEstimationFieldId() != null
                    ? String.valueOf(config.getOriginalEstimationFieldId()) : null);
        } else {
            vo.setChartType(DEFAULT_CHART_TYPE);
            vo.setBurndownCalculation(DEFAULT_BURNDOWN_CALCULATION);
            vo.setIssueFilterMode(DEFAULT_ISSUE_FILTER_MODE);
            vo.setIssueFilterQuery(null);
            vo.setEstimationFieldId(null);
            vo.setOriginalEstimationFieldId(null);
        }
        return vo;
    }

    /**
     * 保存项目的看板图表配置（upsert 语义）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveChartConfig(Long projectId, UpdateBoardChartConfigDTO dto) {
        BoardChartConfig existing = boardChartConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardChartConfig>()
                        .eq(BoardChartConfig::getProjectId, projectId)
        );

        LocalDateTime now = LocalDateTime.now();

        if (existing != null) {
            existing.setChartType(dto.getChartType());
            existing.setBurndownCalculation(dto.getBurndownCalculation());
            existing.setIssueFilterMode(dto.getIssueFilterMode());
            existing.setIssueFilterQuery(dto.getIssueFilterQuery());
            existing.setEstimationFieldId(dto.getEstimationFieldId());
            existing.setOriginalEstimationFieldId(dto.getOriginalEstimationFieldId());
            existing.setUpdatedAt(now);
            boardChartConfigMapper.updateById(existing);
        } else {
            BoardChartConfig config = new BoardChartConfig();
            config.setProjectId(projectId);
            config.setChartType(dto.getChartType());
            config.setBurndownCalculation(dto.getBurndownCalculation());
            config.setIssueFilterMode(dto.getIssueFilterMode());
            config.setIssueFilterQuery(dto.getIssueFilterQuery());
            config.setEstimationFieldId(dto.getEstimationFieldId());
            config.setOriginalEstimationFieldId(dto.getOriginalEstimationFieldId());
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            boardChartConfigMapper.insert(config);
        }
    }
}
