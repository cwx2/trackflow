package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.CloneBoardDTO;
import com.trackflow.board.entity.*;
import com.trackflow.board.mapper.*;
import com.trackflow.board.vo.CloneBoardResultVO;
import com.trackflow.common.constant.SystemRoleIds;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.entity.ProjectStatus;
import com.trackflow.project.entity.ProjectVisibility;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.project.service.ProjectModuleService;
import com.trackflow.common.annotation.AuditLog;
import com.trackflow.common.audit.AuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 看板克隆服务。
 * <p>
 * 以当前项目为模板创建新项目，并将所有看板配置（列、卡片字段、
 * 泳道、图表、基本设置）复制到新项目中。
 * <p>
 * 与 {@link com.trackflow.project.service.ProjectCopyService} 的区别：
 * - 只复制看板相关配置（不复制工作流、标签、成员、自定义字段等）
 * - 看板权限重置为默认（仅克隆者可查看和编辑）
 * - 专为看板 UI 的克隆操作场景设计
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardCloneService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final BoardColumnConfigMapper boardColumnConfigMapper;
    private final BoardColumnMergeMapper boardColumnMergeMapper;
    private final BoardCardConfigMapper boardCardConfigMapper;
    private final BoardGeneralConfigMapper boardGeneralConfigMapper;
    private final BoardSwimlaneConfigMapper boardSwimlaneConfigMapper;
    private final BoardChartConfigMapper boardChartConfigMapper;
    private final ProjectModuleService projectModuleService;

    /** project_admin 角色 ID */
    private static final Long PROJECT_ADMIN_ROLE_ID = SystemRoleIds.PROJECT_ADMIN;

    /**
     * 克隆看板（创建新项目并复制所有看板配置）。
     *
     * @param dto 克隆请求
     * @return 新创建的看板基本信息
     */
    @AuditLog(action = "board_clone", targetType = "board", targetId = "#result.projectId")
    @Transactional(rollbackFor = Exception.class)
    public CloneBoardResultVO cloneBoard(CloneBoardDTO dto) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 验证源项目存在
        Project source = projectMapper.selectById(dto.getSourceProjectId());
        if (source == null) {
            throw BusinessException.notFound("源项目不存在");
        }

        // 2. 验证 Key 唯一性
        Long keyCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getKey, dto.getNewKey())
        );
        if (keyCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目标识 " + dto.getNewKey() + " 已存在");
        }

        // 3. 验证 Name 唯一性（不区分大小写）
        Long nameCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().apply("LOWER(name) = LOWER({0})", dto.getNewName().trim())
        );
        if (nameCount > 0) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_DUPLICATE);
        }

        // 4. 创建新项目
        LocalDateTime now = LocalDateTime.now();
        Project newProject = new Project();
        newProject.setName(dto.getNewName().trim());
        newProject.setKey(dto.getNewKey());
        newProject.setDescription(source.getDescription());
        newProject.setOrgId(source.getOrgId());
        newProject.setLeadId(currentUserId);
        newProject.setStatus(ProjectStatus.ACTIVE);
        newProject.setVisibility(ProjectVisibility.PRIVATE);
        newProject.setIssueSequence(0);
        newProject.setSettings(source.getSettings());
        newProject.setCreatedBy(currentUserId);
        newProject.setUpdatedBy(currentUserId);
        newProject.setCreatedAt(now);
        newProject.setUpdatedAt(now);
        projectMapper.insert(newProject);

        Long newProjectId = newProject.getId();
        log.info("Board clone: created new project {} (id={}) from project {} (id={})",
                dto.getNewKey(), newProjectId, source.getKey(), source.getId());

        // 5. 将克隆者添加为项目管理员
        ProjectMember admin = new ProjectMember();
        admin.setProjectId(newProjectId);
        admin.setUserId(currentUserId);
        admin.setRoleId(PROJECT_ADMIN_ROLE_ID);
        admin.setJoinedAt(now);
        projectMemberMapper.insert(admin);

        // 6. 复制源项目的启用模块配置
        projectModuleService.copyEnabledModules(source.getId(), newProjectId);

        // 7. 复制所有看板配置
        copyBoardConfigWithResetPermissions(source.getId(), newProjectId, dto.getNewName().trim(), now);

        // 8. 记录审计日志
        AuditContext.put("sourceProjectId", source.getId());
        AuditContext.put("sourceProjectKey", source.getKey());
        AuditContext.put("sourceProjectName", source.getName());
        AuditContext.put("newProjectKey", dto.getNewKey());
        AuditContext.put("newBoardName", dto.getNewName());

        log.info("Board clone completed: {} → {} (newProjectId={})",
                source.getKey(), dto.getNewKey(), newProjectId);

        // 9. 构建返回 VO
        CloneBoardResultVO vo = new CloneBoardResultVO();
        vo.setProjectId(String.valueOf(newProjectId));
        vo.setName(dto.getNewName().trim());
        vo.setProjectKey(dto.getNewKey());
        return vo;
    }

    /**
     * 复制所有看板配置到新项目，并重置访问权限为仅克隆者。
     */
    private void copyBoardConfigWithResetPermissions(Long sourceId, Long targetId,
                                                      String newName, LocalDateTime now) {
        // 1. 看板列配置
        List<BoardColumnConfig> columns = boardColumnConfigMapper.selectList(
                new LambdaQueryWrapper<BoardColumnConfig>().eq(BoardColumnConfig::getProjectId, sourceId));
        for (BoardColumnConfig src : columns) {
            BoardColumnConfig col = new BoardColumnConfig();
            col.setProjectId(targetId);
            col.setStatusId(src.getStatusId());
            col.setFieldValue(src.getFieldValue());
            col.setVisible(src.getVisible());
            col.setSortOrder(src.getSortOrder());
            col.setCollapsed(src.getCollapsed());
            col.setWipMin(src.getWipMin());
            col.setWipMax(src.getWipMax());
            col.setCreatedAt(now);
            col.setUpdatedAt(now);
            boardColumnConfigMapper.insert(col);
        }

        // 2. 看板列合并配置
        List<BoardColumnMerge> merges = boardColumnMergeMapper.selectList(
                new LambdaQueryWrapper<BoardColumnMerge>().eq(BoardColumnMerge::getProjectId, sourceId));
        for (BoardColumnMerge src : merges) {
            BoardColumnMerge merge = new BoardColumnMerge();
            merge.setProjectId(targetId);
            merge.setMergeGroupId(src.getMergeGroupId());
            merge.setMergeTitle(src.getMergeTitle());
            merge.setStatusId(src.getStatusId());
            merge.setSortOrder(src.getSortOrder());
            merge.setCreatedAt(now);
            boardColumnMergeMapper.insert(merge);
        }

        // 3. 看板卡片配置
        BoardCardConfig cardConfig = boardCardConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardCardConfig>().eq(BoardCardConfig::getProjectId, sourceId));
        if (cardConfig != null) {
            BoardCardConfig card = new BoardCardConfig();
            card.setProjectId(targetId);
            card.setVisibleFields(cardConfig.getVisibleFields());
            card.setColorScheme(cardConfig.getColorScheme());
            card.setCurrentEstimationFieldId(cardConfig.getCurrentEstimationFieldId());
            card.setOriginalEstimationFieldId(cardConfig.getOriginalEstimationFieldId());
            card.setFieldDisplayModes(cardConfig.getFieldDisplayModes());
            card.setShowCustomFieldColors(cardConfig.getShowCustomFieldColors());
            card.setCreatedAt(now);
            card.setUpdatedAt(now);
            boardCardConfigMapper.insert(card);
        }

        // 4. 看板泳道配置
        BoardSwimlaneConfig swimlaneConfig = boardSwimlaneConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardSwimlaneConfig>().eq(BoardSwimlaneConfig::getProjectId, sourceId));
        if (swimlaneConfig != null) {
            BoardSwimlaneConfig swim = new BoardSwimlaneConfig();
            swim.setProjectId(targetId);
            swim.setGroupByField(swimlaneConfig.getGroupByField());
            // selectedValues 不复制（泳道筛选值是用户会话状态，不应随克隆传播）
            swim.setShowUncategorized(swimlaneConfig.getShowUncategorized());
            swim.setUncategorizedPosition(swimlaneConfig.getUncategorizedPosition());
            swim.setSwimlaneIssueType(swimlaneConfig.getSwimlaneIssueType());
            swim.setCreatedAt(now);
            swim.setUpdatedAt(now);
            boardSwimlaneConfigMapper.insert(swim);
        }

        // 5. 看板图表配置
        BoardChartConfig chartConfig = boardChartConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardChartConfig>().eq(BoardChartConfig::getProjectId, sourceId));
        if (chartConfig != null) {
            BoardChartConfig chart = new BoardChartConfig();
            chart.setProjectId(targetId);
            chart.setChartType(chartConfig.getChartType());
            chart.setBurndownCalculation(chartConfig.getBurndownCalculation());
            chart.setIssueFilterMode(chartConfig.getIssueFilterMode());
            chart.setIssueFilterQuery(chartConfig.getIssueFilterQuery());
            chart.setEstimationFieldId(chartConfig.getEstimationFieldId());
            chart.setOriginalEstimationFieldId(chartConfig.getOriginalEstimationFieldId());
            chart.setCreatedAt(now);
            chart.setUpdatedAt(now);
            boardChartConfigMapper.insert(chart);
        }

        // 6. 看板基本设置（权限重置为默认：所有角色可查看，admin+tech_lead可编辑）
        // 不从源复制 canViewRoles/canEditRoles，确保新看板权限为默认值
        BoardGeneralConfig sourceGeneral = boardGeneralConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardGeneralConfig>().eq(BoardGeneralConfig::getProjectId, sourceId));

        BoardGeneralConfig gen = new BoardGeneralConfig();
        gen.setProjectId(targetId);
        // 新名称使用克隆时指定的名称，不复制原名
        gen.setName(newName);
        // 权限重置为 null（使用系统默认：所有角色可查看，admin+tech_lead 可编辑）
        gen.setCanViewRoles(null);
        gen.setCanEditRoles(null);
        // 复制看板行为配置
        if (sourceGeneral != null) {
            gen.setFilterMode(sourceGeneral.getFilterMode());
            gen.setFilterQuery(sourceGeneral.getFilterQuery());
            gen.setDoneRetentionDays(sourceGeneral.getDoneRetentionDays());
            gen.setColumnField(sourceGeneral.getColumnField());
            gen.setAllowMultipleSprints(sourceGeneral.getAllowMultipleSprints());
            gen.setBacklogViewMode(sourceGeneral.getBacklogViewMode());
            // backlogSavedQueryId 指向特定的 SavedQuery，不复制（不同项目的 query 不通用）
            gen.setBacklogSavedQueryId(null);
        } else {
            gen.setFilterMode("all");
            gen.setColumnField("status");
            gen.setAllowMultipleSprints(false);
            gen.setBacklogViewMode("list");
        }
        gen.setCreatedAt(now);
        gen.setUpdatedAt(now);
        boardGeneralConfigMapper.insert(gen);

        log.debug("Board clone config copied: sourceId={}, targetId={}, columns={}, merges={}, card={}, swimlane={}, chart={}, general={}",
                sourceId, targetId, columns.size(), merges.size(),
                cardConfig != null, swimlaneConfig != null, chartConfig != null, true);
    }
}
