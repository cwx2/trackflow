package com.trackflow.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求工具类
 */
public final class WebUtils {

    private WebUtils() {
    }

    /**
     * 提取客户端真实 IP 地址。
     * 支持 X-Forwarded-For 和 X-Real-IP 代理转发头。
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    public static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // 取第一个 IP（最接近客户端的）
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
