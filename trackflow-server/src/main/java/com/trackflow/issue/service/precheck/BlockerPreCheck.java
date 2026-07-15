package com.trackflow.issue.service.precheck;

import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.service.IssueLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 关闭前置检查：未解决的阻塞关系。
 * <p>
 * 当工单被其他未关闭的工单 blocks 时，返回警告。
 */
@Component
@RequiredArgsConstructor
public class BlockerPreCheck implements ClosePreCheck {

    private final IssueLinkService issueLinkService;

    @Override
    public Optional<String> check(Issue issue) {
        List<String> blockerKeys = issueLinkService.getUnresolvedBlockerKeys(issue.getId());
        if (!blockerKeys.isEmpty()) {
            return Optional.of("被 " + String.join("、", blockerKeys) + " 阻塞");
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 20;
    }
}
