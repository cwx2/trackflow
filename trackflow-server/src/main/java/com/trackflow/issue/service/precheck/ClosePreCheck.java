package com.trackflow.issue.service.precheck;

import com.trackflow.issue.entity.Issue;

import java.util.Optional;

/**
 * 关闭前置检查接口。
 * <p>
 * 当 Issue 尝试转换到关闭状态时，会依次执行所有注册的 ClosePreCheck 实现。
 * 每个检查可以返回一条警告消息（存在问题时），或返回空（无问题时）。
 * <p>
 * 扩展方式：新增一个 @Component 实现此接口即可自动加入检查链，无需修改调用方代码。
 */
public interface ClosePreCheck {

    /**
     * 执行检查。
     *
     * @param issue 当前要关闭的工单
     * @return 警告消息（如"有 3 个未完成的子任务"），空表示该检查通过
     */
    Optional<String> check(Issue issue);

    /**
     * 检查执行优先级（数字越小越先执行）。
     * 默认 100，子任务检查为 10，阻塞检查为 20。
     */
    default int order() {
        return 100;
    }
}
