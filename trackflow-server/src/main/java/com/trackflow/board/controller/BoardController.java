package com.trackflow.board.controller;

import com.trackflow.board.dto.BoardDataQuery;
import com.trackflow.board.dto.SaveBoardSettingsDTO;
import com.trackflow.board.dto.UpdateBoardCardConfigDTO;
import com.trackflow.board.dto.UpdateBoardChartConfigDTO;
import com.trackflow.board.dto.UpdateBoardColumnMergeDTO;
import com.trackflow.board.dto.UpdateBoardColumnsDTO;
import com.trackflow.board.dto.UpdateBoardGeneralConfigDTO;
import com.trackflow.board.dto.UpdateBoardSwimlaneConfigDTO;
import com.trackflow.board.service.BoardCardConfigService;
import com.trackflow.board.service.BoardChartConfigService;
import com.trackflow.board.service.BoardColumnMergeService;
import com.trackflow.board.service.BoardColumnService;
import com.trackflow.board.service.BoardConfigVersionService;
import com.trackflow.board.service.BoardDataService;
import com.trackflow.board.service.BoardFavoriteService;
import com.trackflow.board.service.BoardGeneralConfigService;
import com.trackflow.board.service.BoardSettingsService;
import com.trackflow.board.service.BoardSwimlaneConfigService;
import com.trackflow.board.vo.BoardCardConfigVO;
import com.trackflow.board.vo.BoardChartConfigVO;
import com.trackflow.board.vo.BoardColumnMergeGroupVO;
import com.trackflow.board.vo.BoardColumnVO;
import com.trackflow.board.vo.BoardDataVO;
import com.trackflow.board.vo.BoardGeneralConfigVO;
import com.trackflow.board.vo.BoardListItemVO;
import com.trackflow.board.vo.BoardSwimlaneConfigVO;
import com.trackflow.common.model.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final BoardConfigVersionService boardConfigVersionService;
    private final BoardSettingsService boardSettingsService;
    private final BoardDataService boardDataService;
    private final BoardFavoriteService boardFavoriteService;

    /**
     * 获取项目看板列配置（纯读取，不执行任何写操作）
     * 根据项目的 columnField 配置返回对应字段的列。
     * 需要看板查看权限
     */
    @GetMapping("/columns")
    @PreAuthorize("@perm.checkBoardView(#projectId)")
    public R<List<BoardColumnVO>> getColumns(@RequestParam("projectId") Long projectId) {
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
     * 获取看板聚合数据 — 一次请求返回按列分组的工单数据。
     * <p>
     * 替代前端循环调用通用 Issue 列表 API 的方式，
     * 服务端完成按列分组 + 统计，前端无需客户端 filter。
     * <p>
     * 需要看板查看权限。
     *
     * @param query 查询参数（projectId 必填，sprintId/assigneeId/keyword/excludeDoneBefore 可选）
     * @param collapsedStatusIds 已折叠的列状态 ID（逗号分隔），折叠列仅返回统计不返回具体工单
     * @return 按列分组的看板数据
     */
    @GetMapping("/data")
    @PreAuthorize("@perm.checkBoardView(#projectId)")
    public R<BoardDataVO> getBoardData(
            @Valid BoardDataQuery query,
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "collapsedStatusIds", required = false) String collapsedStatusIds,
            @RequestParam(value = "swimlaneValues", required = false) String swimlaneValues) {
        Set<Long> collapsed = parseCollapsedStatusIds(collapsedStatusIds);
        // REQ-386: 泳道服务端过滤 — 解析逗号分隔的泳道值列表
        if (swimlaneValues != null && !swimlaneValues.isBlank()) {
            List<String> valueList = Arrays.stream(swimlaneValues.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            query.setSwimlaneValues(valueList);
        }
        BoardDataVO data = boardDataService.aggregateBoardData(query, collapsed);
        return R.ok(data);
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
    @PreAuthorize("@perm.checkBoardEdit(#projectId)")
    public R<List<BoardColumnVO>> initializeColumns(@RequestParam("projectId") Long projectId) {
        List<BoardColumnVO> columns = boardColumnService.initializeColumns(projectId);
        return R.ok(columns);
    }

    /**
     * 获取项目看板状态列配置（始终返回状态模式列，不受 columnField 影响）。
     * 用于看板设置面板中的列配置 UI，无论当前是否为优先级模式。
     */
    @GetMapping("/columns/status")
    @PreAuthorize("@perm.checkBoardEdit(#projectId)")
    public R<List<BoardColumnVO>> getStatusColumns(@RequestParam("projectId") Long projectId) {
        List<BoardColumnVO> columns = boardColumnService.getColumns(projectId);
        return R.ok(columns);
    }

    /**
     * 保存项目看板列配置
     * 需要看板编辑权限
     */
    @PutMapping("/columns")
    @PreAuthorize("@perm.checkBoardEdit(#projectId)")
    public R<Void> saveColumns(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardColumnsDTO dto) {
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
    @PreAuthorize("@perm.checkBoardView(#projectId)")
    public R<BoardCardConfigVO> getCardConfig(@RequestParam("projectId") Long projectId) {
        BoardCardConfigVO config = boardCardConfigService.getCardConfig(projectId);
        return R.ok(config);
    }

    /**
     * 保存项目看板卡片配置。
     * 需要看板编辑权限。
     */
    @PutMapping("/card-config")
    @PreAuthorize("@perm.checkBoardEdit(#projectId)")
    public R<Void> saveCardConfig(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardCardConfigDTO dto) {
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
    @PreAuthorize("@perm.checkBoardView(#projectId)")
    public R<BoardSwimlaneConfigVO> getSwimlaneConfig(@RequestParam("projectId") Long projectId) {
        BoardSwimlaneConfigVO config = boardSwimlaneConfigService.getSwimlaneConfig(projectId);
        return R.ok(config);
    }

    /**
     * 保存项目看板泳道配置。
     * 需要看板编辑权限。
     */
    @PutMapping("/swimlane-config")
    @PreAuthorize("@perm.checkBoardEdit(#projectId)")
    public R<Void> saveSwimlaneConfig(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardSwimlaneConfigDTO dto) {
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
    @PreAuthorize("@perm.checkBoardView(#projectId)")
    public R<List<BoardColumnMergeGroupVO>> getColumnMerges(@RequestParam("projectId") Long projectId) {
        List<BoardColumnMergeGroupVO> merges = boardColumnMergeService.getColumnMerges(projectId);
        return R.ok(merges);
    }

    /**
     * 保存项目看板列合并配置（全量替换）。
     * 需要看板编辑权限。
     */
    @PutMapping("/column-merges")
    @PreAuthorize("@perm.checkBoardEdit(#projectId)")
    public R<Void> saveColumnMerges(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardColumnMergeDTO dto) {
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
    @PreAuthorize("@perm.checkBoardEdit(#projectId)")
    public R<Void> saveGeneralConfig(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardGeneralConfigDTO dto) {
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
    @PreAuthorize("@perm.checkBoardView(#projectId)")
    public R<BoardChartConfigVO> getChartConfig(@RequestParam("projectId") Long projectId) {
        BoardChartConfigVO config = boardChartConfigService.getChartConfig(projectId);
        return R.ok(config);
    }

    /**
     * 保存项目看板图表配置。
     * 需要看板编辑权限。
     */
    @PutMapping("/chart-config")
    @PreAuthorize("@perm.checkBoardEdit(#projectId)")
    public R<Void> saveChartConfig(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardChartConfigDTO dto) {
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
    @PreAuthorize("@perm.checkBoardEdit(#projectId)")
    public R<Void> saveBoardSettings(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody SaveBoardSettingsDTO dto) {
        boardSettingsService.saveAllSettings(projectId, dto);
        return R.ok();
    }

    // ========== 看板列表 & 收藏 ==========

    /**
     * 获取当前用户可访问的所有看板列表。
     * 用于 Board Selector 下拉面板。
     * 收藏的看板排在列表顶部。
     */
    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<BoardListItemVO>> listBoards() {
        List<BoardListItemVO> boards = boardFavoriteService.listBoards();
        return R.ok(boards);
    }

    /**
     * 收藏看板（项目）。
     * 幂等操作，重复收藏不会报错。
     */
    @PostMapping("/favorite")
    @PreAuthorize("isAuthenticated()")
    public R<Void> addFavorite(@RequestParam("projectId") Long projectId) {
        boardFavoriteService.addFavorite(projectId);
        return R.ok();
    }

    /**
     * 取消收藏看板（项目）。
     */
    @DeleteMapping("/favorite")
    @PreAuthorize("isAuthenticated()")
    public R<Void> removeFavorite(@RequestParam("projectId") Long projectId) {
        boardFavoriteService.removeFavorite(projectId);
        return R.ok();
    }

    // ========== Private helpers ==========

    /**
     * 解析逗号分隔的折叠列状态 ID 字符串为 Set<Long>
     */
    private Set<Long> parseCollapsedStatusIds(String collapsedStatusIds) {
        if (collapsedStatusIds == null || collapsedStatusIds.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> result = new HashSet<>();
        for (String idStr : collapsedStatusIds.split(",")) {
            String trimmed = idStr.trim();
            if (!trimmed.isEmpty()) {
                try {
                    result.add(Long.parseLong(trimmed));
                } catch (NumberFormatException e) {
                    // 忽略无效的 ID
                }
            }
        }
        return result;
    }
}
