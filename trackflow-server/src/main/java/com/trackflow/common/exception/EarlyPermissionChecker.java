package com.trackflow.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

/**
 * 早期权限检查接口 — 在参数校验失败时检查用户是否有权限执行该操作。
 * <p>
 * 若用户无权限，异常处理器应返回 403（不暴露接口参数结构）；否则返回 400 和具体校验错误。
 * <p>
 * 此接口定义在 common 模块中，由 auth 模块提供实现，避免 common → auth 的跨层依赖。
 *
 * @author TrackFlow
 * @since 1.0
 */
public interface EarlyPermissionChecker {

    /**
     * 检查当前用户是否有权限执行指定 Handler 方法
     *
     * @param request       当前 HTTP 请求
     * @param handlerMethod 目标 Controller 方法
     * @return true 如果有权限或无法确定（保守处理）；false 如果明确无权限
     */
    boolean hasPermission(HttpServletRequest request, HandlerMethod handlerMethod);
}
