package com.trackflow.board.controller;

import com.trackflow.board.dto.SaveBoardSettingsDTO;
import com.trackflow.board.dto.UpdateBoardCardConfigDTO;
import com.trackflow.board.dto.UpdateBoardChartConfigDTO;
import com.trackflow.board.dto.UpdateBoardColumnMergeDTO;
import com.trackflow.board.dto.UpdateBoardColumnsDTO;
import com.trackflow.board.dto.UpdateBoardGeneralConfigDTO;
import com.trackflow.board.dto.UpdateBoardSwimlaneConfigDTO;
import com.trackflow.board.service.BoardAccessService;
import com.trackflow.board.service.BoardCardConfigService;
import com.trackflow.board.service.BoardChartConfigService;
import com.trackflow.board.service.BoardColumnMergeService;
import com.trackflow.board.service.BoardColumnService;
import com.trackflow.board.service.BoardConfigVersionService;
import com.trackflow.board.service.BoardGeneralConfigService;
import com.trackflow.board.service.BoardSettingsService;
import com.trackflow.board.service.BoardSwimlaneConfigService;
import com.trackflow.board.vo.BoardCardConfigVO;
import com.trackflow.board.vo.BoardChartConfigVO;
import com.trackflow.board.vo.BoardColumnMergeGroupVO;
import com.trackflow.board.vo.BoardColumnVO;
import com.trackflow.board.vo.BoardGeneralConfigVO;
import com.trackflow.board.vo.BoardSwimlaneConfigVO;
import com.trackflow.common.model.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardColumnService boardColumnService;
    private final BoardCardConfigService boardCardConfigService;
    private final BoardChartConfigService boardChartConfigService;
    private final BoardSwimlaneConfigService boardSwimlaneConfigService;
    private final BoardColumnMergeService boardColumnMergeService;
    private final BoardGeneralConfigService boardGeneralConfigService;
    private final BoardAccessService boardAccessService;
    private final BoardConfigVersionService boardConfigVersionService;
    private final BoardSettingsService boardSettingsService;

    /**
     * 获取项目看板列配置（纯读取，不执行任何写操作）
     * 根据项目的 columnField 配置返回对应字段的列。
     * 需要项目查看权限 + 看板查看权限
     */
    @GetMapping("/columns")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<List<BoardColumnVO>> getColumns(@RequestParam("projectId") Long projectId) {
        boardAccessService.checkViewAccess(projectId);
        // 检查 columnField 配置决定返回哪种列
        BoardGeneralConfigVO generalConfig = boardGeneralConfigService.getGeneralConfig(projectId);
        String columnField = generalConfig.getColumnField() != null ? generalConfig.getColumnField() : "status";
        List<BoardColumnVO> columns;
        if ("priority".equals(columnField)) {
            columns = boardColumnService.getPriorityColumns(projectId);
        } else {
            columns = boardColumnService.getColumns(projectId);
        }
        return R.ok(columns);
    }

    /**
     * 显式初始化项目看板列配置。
     * <p>
     * 仅在用户首次打开看板设置面板时调用（幂等）。
     * 如果项目已有配置，直接返回现有配置不做修改。
     * <p>
     * 需要看板编辑权限。
     */
    @PostMapping("/columns/init")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<List<BoardColumnVO>> initializeColumns(@RequestParam("projectId") Long projectId) {
        boardAccessService.checkEditAccess(projectId);
        List<BoardColumnVO> columns = boardColumnService.initializeColumns(projectId);
        return R.ok(columns);
    }

    /**
     * 保存项目看板列配置
     * 需要看板编辑权限
     */
    @PutMapping("/columns")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<Void> saveColumns(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardColumnsDTO dto) {
        boardAccessService.checkEditAccess(projectId);
        boardConfigVersionService.checkAndIncrement(projectId, dto.getConfigVersion());
        boardColumnService.saveColumns(projectId, dto);
        return R.ok();
    }

    // ========== 卡片配置 ==========

    /**
     * 获取项目看板卡片配置（显示字段 + 颜色方案）。
     * 如果项目尚未配置，返回默认值。
     * 需要看板查看权限。
     */
    @GetMapping("/card-config")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<BoardCardConfigVO> getCardConfig(@RequestParam("projectId") Long projectId) {
        boardAccessService.checkViewAccess(projectId);
        BoardCardConfigVO config = boardCardConfigService.getCardConfig(projectId);
        return R.ok(config);
    }

    /**
     * 保存项目看板卡片配置。
     * 需要看板编辑权限。
     */
    @PutMapping("/card-config")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<Void> saveCardConfig(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardCardConfigDTO dto) {
        boardAccessService.checkEditAccess(projectId);
        boardConfigVersionService.checkAndIncrement(projectId, dto.getConfigVersion());
        boardCardConfigService.saveCardConfig(projectId, dto);
        return R.ok();
    }

    // ========== 泳道配置 ==========

    /**
     * 获取项目看板泳道配置（分组字段）。
     * 如果项目尚未配置，返回默认值（无分组）。
     * 需要看板查看权限。
     */
    @GetMapping("/swimlane-config")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<BoardSwimlaneConfigVO> getSwimlaneConfig(@RequestParam("projectId") Long projectId) {
        boardAccessService.checkViewAccess(projectId);
        BoardSwimlaneConfigVO config = boardSwimlaneConfigService.getSwimlaneConfig(projectId);
        return R.ok(config);
    }

    /**
     * 保存项目看板泳道配置。
     * 需要看板编辑权限。
     */
    @PutMapping("/swimlane-config")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<Void> saveSwimlaneConfig(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardSwimlaneConfigDTO dto) {
        boardAccessService.checkEditAccess(projectId);
        boardConfigVersionService.checkAndIncrement(projectId, dto.getConfigVersion());
        boardSwimlaneConfigService.saveSwimlaneConfig(projectId, dto);
        return R.ok();
    }

    // ========== 列合并配置 ==========

    /**
     * 获取项目看板列合并配置。
     * 返回合并组列表（无合并则返回空列表）。
     * 需要看板查看权限。
     */
    @GetMapping("/column-merges")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<List<BoardColumnMergeGroupVO>> getColumnMerges(@RequestParam("projectId") Long projectId) {
        boardAccessService.checkViewAccess(projectId);
        List<BoardColumnMergeGroupVO> merges = boardColumnMergeService.getColumnMerges(projectId);
        return R.ok(merges);
    }

    /**
     * 保存项目看板列合并配置（全量替换）。
     * 需要看板编辑权限。
     */
    @PutMapping("/column-merges")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<Void> saveColumnMerges(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardColumnMergeDTO dto) {
        boardAccessService.checkEditAccess(projectId);
        boardConfigVersionService.checkAndIncrement(projectId, dto.getConfigVersion());
        boardColumnMergeService.saveColumnMerges(projectId, dto);
        return R.ok();
    }

    // ========== 基本设置 ==========

    /**
     * 获取项目看板基本设置（名称 + 访问权限）。
     * 如果项目尚未配置，返回默认值。
     * 需要项目查看权限（基线），返回中包含 canEdit 字段供前端判断。
     */
    @GetMapping("/general-config")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<BoardGeneralConfigVO> getGeneralConfig(@RequestParam("projectId") Long projectId) {
        BoardGeneralConfigVO config = boardGeneralConfigService.getGeneralConfig(projectId);
        return R.ok(config);
    }

    /**
     * 保存项目看板基本设置。
     * 需要看板编辑权限。
     */
    @PutMapping("/general-config")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<Void> saveGeneralConfig(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardGeneralConfigDTO dto) {
        boardAccessService.checkEditAccess(projectId);
        boardConfigVersionService.checkAndIncrement(projectId, dto.getConfigVersion());
        boardGeneralConfigService.saveGeneralConfig(projectId, dto);
        return R.ok();
    }

    // ========== 图表配置 ==========

    /**
     * 获取项目看板图表配置（图表类型 + 计算方式 + 过滤器）。
     * 如果项目尚未配置，返回默认值。
     * 需要看板查看权限。
     */
    @GetMapping("/chart-config")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<BoardChartConfigVO> getChartConfig(@RequestParam("projectId") Long projectId) {
        boardAccessService.checkViewAccess(projectId);
        BoardChartConfigVO config = boardChartConfigService.getChartConfig(projectId);
        return R.ok(config);
    }

    /**
     * 保存项目看板图表配置。
     * 需要看板编辑权限。
     */
    @PutMapping("/chart-config")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<Void> saveChartConfig(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardChartConfigDTO dto) {
        boardAccessService.checkEditAccess(projectId);
        boardChartConfigService.saveChartConfig(projectId, dto);
        return R.ok();
    }

    // ========== 批量保存 ==========

    /**
     * 批量保存项目看板所有设置（原子操作 + 乐观锁）。
     * <p>
     * 将列设置、卡片配置、泳道配置、列合并、基本设置合并为一次请求，
     * 仅做一次版本检查，确保并发安全。
     * <p>
     * 推荐前端优先使用此接口，避免 5 个并行请求的竞态条件。
     */
    @PutMapping("/settings")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<Void> saveBoardSettings(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody SaveBoardSettingsDTO dto) {
        boardAccessService.checkEditAccess(projectId);
        boardSettingsService.saveAllSettings(projectId, dto);
        return R.ok();
    }
}
