package com.trackflow.board.controller;

import com.trackflow.board.dto.UpdateBoardCardConfigDTO;
import com.trackflow.board.dto.UpdateBoardColumnsDTO;
import com.trackflow.board.service.BoardCardConfigService;
import com.trackflow.board.service.BoardColumnService;
import com.trackflow.board.vo.BoardCardConfigVO;
import com.trackflow.board.vo.BoardColumnVO;
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

    /**
     * 获取项目看板列配置（纯读取，不执行任何写操作）
     * 需要项目查看权限（确保当前用户是项目成员）
     */
    @GetMapping("/columns")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<List<BoardColumnVO>> getColumns(@RequestParam("projectId") Long projectId) {
        List<BoardColumnVO> columns = boardColumnService.getColumns(projectId);
        return R.ok(columns);
    }

    /**
     * 显式初始化项目看板列配置。
     * <p>
     * 仅在用户首次打开看板设置面板时调用（幂等）。
     * 如果项目已有配置，直接返回现有配置不做修改。
     * <p>
     * 需要项目编辑权限（只有管理员/负责人能初始化看板配置）。
     */
    @PostMapping("/columns/init")
    @PreAuthorize("@perm.check(#projectId, 'project:edit')")
    public R<List<BoardColumnVO>> initializeColumns(@RequestParam("projectId") Long projectId) {
        List<BoardColumnVO> columns = boardColumnService.initializeColumns(projectId);
        return R.ok(columns);
    }

    /**
     * 保存项目看板列配置
     * 需要项目管理权限
     */
    @PutMapping("/columns")
    @PreAuthorize("@perm.check(#projectId, 'project:edit')")
    public R<Void> saveColumns(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardColumnsDTO dto) {
        boardColumnService.saveColumns(projectId, dto);
        return R.ok();
    }

    // ========== 卡片配置 ==========

    /**
     * 获取项目看板卡片配置（显示字段 + 颜色方案）。
     * 如果项目尚未配置，返回默认值。
     * 需要项目查看权限。
     */
    @GetMapping("/card-config")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<BoardCardConfigVO> getCardConfig(@RequestParam("projectId") Long projectId) {
        BoardCardConfigVO config = boardCardConfigService.getCardConfig(projectId);
        return R.ok(config);
    }

    /**
     * 保存项目看板卡片配置。
     * 需要项目编辑权限（仅管理员/负责人可修改）。
     */
    @PutMapping("/card-config")
    @PreAuthorize("@perm.check(#projectId, 'project:edit')")
    public R<Void> saveCardConfig(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody UpdateBoardCardConfigDTO dto) {
        boardCardConfigService.saveCardConfig(projectId, dto);
        return R.ok();
    }
}
