package com.trackflow.issue.service.precheck;

import com.trackflow.issue.entity.Issue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 关闭前置检查链。
 * <p>
 * 自动收集所有 {@link ClosePreCheck} 实现，按 order() 排序后依次执行。
 * 返回所有失败的警告消息列表。
 * <p>
 * 扩展方式：只需新增一个 @Component 实现 ClosePreCheck 接口，无需修改此类或调用方。
 */
@Slf4j
@Component
public class ClosePreCheckChain {

    private final List<ClosePreCheck> checks;

    public ClosePreCheckChain(List<ClosePreCheck> checks) {
        // 按 order 排序（Spring 注入的 List 默认无序）
        this.checks = checks.stream()
                .sorted(Comparator.comparingInt(ClosePreCheck::order))
                .toList();
        log.info("ClosePreCheckChain initialized with {} checks: {}",
                this.checks.size(),
                this.checks.stream().map(c -> c.getClass().getSimpleName()).toList());
    }

    /**
     * 执行所有关闭前置检查，收集警告消息。
     *
     * @param issue 要关闭的工单
     * @return 警告消息列表（空列表表示全部通过）
     */
    public List<String> execute(Issue issue) {
        return checks.stream()
                .map(check -> {
                    try {
                        return check.check(issue);
                    } catch (Exception e) {
                        log.warn("ClosePreCheck [{}] failed with exception: {}",
                                check.getClass().getSimpleName(), e.getMessage());
                        return Optional.<String>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
