package com.trackflow.board.controller;

import com.trackflow.board.dto.UpdateBoardColumnsDTO;
import com.trackflow.board.service.BoardColumnService;
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

    /**
     * 获取项目看板列配置
     * 需要项目查看权限（确保当前用户是项目成员）
     */
    @GetMapping("/columns")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<List<BoardColumnVO>> getColumns(@RequestParam Long projectId) {
        List<BoardColumnVO> columns = boardColumnService.getColumns(projectId);
        return R.ok(columns);
    }

    /**
     * 保存项目看板列配置
     * 需要项目管理权限
     */
    @PutMapping("/columns")
    @PreAuthorize("@perm.check(#projectId, 'project:edit')")
    public R<Void> saveColumns(
            @RequestParam Long projectId,
            @Valid @RequestBody UpdateBoardColumnsDTO dto) {
        boardColumnService.saveColumns(projectId, dto);
        return R.ok();
    }
}
