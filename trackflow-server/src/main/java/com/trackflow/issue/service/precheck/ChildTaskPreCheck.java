package com.trackflow.issue.service.precheck;

import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 关闭前置检查：未完成的子任务。
 * <p>
 * 当父工单有未关闭的子工单时，返回警告。
 */
@Component
@RequiredArgsConstructor
public class ChildTaskPreCheck implements ClosePreCheck {

    private final IssueService issueService;

    @Override
    public Optional<String> check(Issue issue) {
        long openChildren = issueService.countOpenChildren(issue.getId());
        if (openChildren > 0) {
            return Optional.of("有 " + openChildren + " 个未完成的子任务");
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 10;
    }
}
