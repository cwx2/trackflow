package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.trackflow.board.dto.UpdateBoardColumnsDTO;
import com.trackflow.board.entity.BoardColumnConfig;
import com.trackflow.board.mapper.BoardColumnConfigMapper;
import com.trackflow.board.vo.BoardColumnVO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardColumnService {

    /** 基础工作流状态 code 集合（V5__issue_schema.sql 中定义的种子状态） */
    private static final Set<String> SEED_STATUS_CODES = Set.of(
            "open", "in_progress", "code_review", "testing", "done", "cancelled"
    );

    private final BoardColumnConfigMapper boardColumnConfigMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final IssueMapper issueMapper;

    /**
     * 获取项目的看板列配置。
     * <p>
     * 如果项目没有自定义配置，基于该项目工作流中实际涉及的状态自动初始化配置
     * （仅工作流引用的状态为 visible=true，其余为 visible=false），
     * 并持久化到数据库以保证后续一致性。
     * <p>
     * 重要：如果项目中有工单处于某个"不可见"的状态，自动将该状态提升为可见，
     * 确保看板上不会有工单"消失"。
     */
    @Transactional
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

        // 如果项目没有配置，基于项目实际使用情况自动初始化
        if (configs.isEmpty()) {
            try {
                configs = initializeDefaultColumns(projectId, allStatuses);
            } catch (DataIntegrityViolationException e) {
                // 并发情况下可能出现唯一约束冲突，重新查询已写入的配置
                configs = boardColumnConfigMapper.selectList(
                        new LambdaQueryWrapper<BoardColumnConfig>()
                                .eq(BoardColumnConfig::getProjectId, projectId)
                                .orderByAsc(BoardColumnConfig::getSortOrder)
                );
            }
            // 初始化时已基于 usedStatusIds 设置了可见性，无需再次检查
        }

        // 查询项目中实际使用的状态 ID，确保有工单的状态一定可见
        Set<Long> usedStatusIds = getProjectUsedStatusIds(projectId);

        // 自动提升有工单但不可见的状态为可见（持久化到数据库）
        configs = ensureUsedStatusesVisible(projectId, configs, usedStatusIds);

        // 按配置返回
        Map<Long, BoardColumnConfig> configMap = configs.stream()
                .collect(Collectors.toMap(BoardColumnConfig::getStatusId, c -> c));

        // 构建结果列表
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
                vo.setWipMin(config.getWipMin());
                vo.setWipMax(config.getWipMax());
            } else {
                // 新增的全局状态：如果有工单使用则可见，否则不可见
                boolean hasIssues = usedStatusIds.contains(status.getId());
                vo.setVisible(hasIssues);
                vo.setSortOrder(status.getSortOrder() + 1000);
                vo.setCollapsed(false);

                // 如果有工单使用，也要持久化配置以避免每次都重新检查
                if (hasIssues) {
                    persistNewColumnConfig(projectId, status, status.getSortOrder() + 1000);
                }
            }
            result.add(vo);
        }

        // 按 sortOrder 排序
        result.sort(Comparator.comparingInt(BoardColumnVO::getSortOrder));
        return result;
    }

    /**
     * 确保项目中有工单使用的状态在看板列配置中为可见。
     * <p>
     * 如果某个状态有工单但当前配置为 visible=false，自动更新为 visible=true。
     * 这防止了工单在看板上"消失"的问题。
     *
     * @return 更新后的配置列表
     */
    private List<BoardColumnConfig> ensureUsedStatusesVisible(
            Long projectId,
            List<BoardColumnConfig> configs,
            Set<Long> usedStatusIds) {

        boolean updated = false;
        for (BoardColumnConfig config : configs) {
            if (!config.getVisible() && usedStatusIds.contains(config.getStatusId())) {
                // 有工单处于这个状态，但列被隐藏了 —— 自动提升为可见
                config.setVisible(true);
                config.setUpdatedAt(LocalDateTime.now());
                boardColumnConfigMapper.updateById(config);
                updated = true;
            }
        }

        if (updated) {
            // 重新查询确保顺序一致
            configs = boardColumnConfigMapper.selectList(
                    new LambdaQueryWrapper<BoardColumnConfig>()
                            .eq(BoardColumnConfig::getProjectId, projectId)
                            .orderByAsc(BoardColumnConfig::getSortOrder)
            );
        }

        return configs;
    }

    /**
     * 为项目持久化一个新的看板列配置（用于新增的全局状态有工单引用时）。
     */
    private void persistNewColumnConfig(Long projectId, IssueStatus status, int sortOrder) {
        try {
            BoardColumnConfig config = new BoardColumnConfig();
            config.setProjectId(projectId);
            config.setStatusId(status.getId());
            config.setVisible(true);
            config.setSortOrder(sortOrder);
            config.setCollapsed(false);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            boardColumnConfigMapper.insert(config);
        } catch (DataIntegrityViolationException e) {
            // 唯一约束冲突（并发或已存在），忽略
        }
    }

    /**
     * 基于项目实际使用情况，自动初始化看板列配置。
     * <p>
     * 默认可见规则：
     * 1. 基础工作流状态（open, in_progress, code_review, testing, done, cancelled）始终可见
     * 2. 项目中已有工单处于该状态的列可见
     * 3. 其余状态默认隐藏
     * <p>
     * 配置持久化到数据库，后续不再重复计算。
     */
    private List<BoardColumnConfig> initializeDefaultColumns(Long projectId, List<IssueStatus> allStatuses) {
        // 将 code 映射为 ID（不再硬编码 ID，对迁移脚本 ID 变更更健壮）
        Set<Long> seedStatusIds = allStatuses.stream()
                .filter(s -> SEED_STATUS_CODES.contains(s.getCode()))
                .map(IssueStatus::getId)
                .collect(Collectors.toSet());

        // 查询该项目已有工单涉及的状态 ID
        Set<Long> usedStatusIds = getProjectUsedStatusIds(projectId);

        LocalDateTime now = LocalDateTime.now();
        List<BoardColumnConfig> configs = new ArrayList<>();
        int order = 0;
        for (IssueStatus status : allStatuses) {
            BoardColumnConfig config = new BoardColumnConfig();
            config.setProjectId(projectId);
            config.setStatusId(status.getId());
            // 基础工作流状态或项目已有工单使用的状态默认可见
            config.setVisible(seedStatusIds.contains(status.getId()) || usedStatusIds.contains(status.getId()));
            config.setSortOrder(order);
            config.setCollapsed(false);
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            configs.add(config);
            order++;
        }

        // 持久化
        Db.saveBatch(configs);
        return configs;
    }

    /**
     * 查询项目中已有工单使用的状态 ID 集合。
     */
    private Set<Long> getProjectUsedStatusIds(Long projectId) {
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .isNull(Issue::getDeletedAt)
                        .select(Issue::getStatusId)
        );
        return issues.stream()
                .map(Issue::getStatusId)
                .collect(Collectors.toSet());
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

        // 批量构建新配置实体
        LocalDateTime now = LocalDateTime.now();
        List<BoardColumnConfig> configs = new ArrayList<>();
        int order = 0;
        for (UpdateBoardColumnsDTO.ColumnItem item : dto.getColumns()) {
            BoardColumnConfig config = new BoardColumnConfig();
            config.setProjectId(projectId);
            config.setStatusId(item.getStatusId());
            config.setVisible(item.getVisible());
            config.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order);
            config.setCollapsed(item.getCollapsed() != null ? item.getCollapsed() : false);
            config.setWipMin(item.getWipMin());
            config.setWipMax(item.getWipMax());
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            configs.add(config);
            order++;
        }

        // 批量插入（MyBatis-Plus Db 工具类，内部使用 SqlSession BATCH 模式）
        Db.saveBatch(configs);
    }
}
