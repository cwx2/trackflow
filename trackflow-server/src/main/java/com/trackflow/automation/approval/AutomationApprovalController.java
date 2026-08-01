package com.trackflow.automation.approval;

import com.trackflow.automation.approval.dto.ApprovalDecisionDTO;
import com.trackflow.automation.approval.entity.AutomationApproval;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/automation/approvals")
@PreAuthorize("@perm.checkGlobal('system:admin')")
public class AutomationApprovalController {
    private final AutomationApprovalService service;

    @GetMapping
    public R<List<AutomationApproval>> listPending() { return R.ok(service.listPending()); }

    @PostMapping("/{id}/decision")
    public R<AutomationApproval> decide(@PathVariable Long id,
                                        @Valid @RequestBody ApprovalDecisionDTO dto) {
        boolean approved;
        if ("approved".equals(dto.getDecision())) approved = true;
        else if ("rejected".equals(dto.getDecision())) approved = false;
        else throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "decision 只能是 approved 或 rejected");
        return R.ok(service.decide(id, approved, dto.getComment()));
    }
}
