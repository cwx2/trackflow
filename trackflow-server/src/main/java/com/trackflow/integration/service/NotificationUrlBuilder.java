package com.trackflow.integration.service;

import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 通知资源 URL 构建器。
 * <p>
 * 根据 resourceType + resourceId 构建前端路由路径（相对路径），
 * 用于站内通知点击导航和邮件通知中的直链按钮。
 * <p>
 * 路由映射：
 * - issue → /issues/{issueKey}（需查 issue 表获取 key）
 * - project → /projects/{projectId}
 * - sprint → /sprints?projectId={projectId}
 * <p>
 * 所有路径为相对路径，前端直接使用 router.push()。
 * 邮件场景需拼接 baseUrl 生成完整链接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationUrlBuilder {

    private final IssueMapper issueMapper;

    /**
     * 前端基础 URL（用于邮件中的完整链接）。
     * 开发环境默认 http://localhost:3000
     */
    @Value("${trackflow.base-url:http://localhost:3000}")
    private String baseUrl;

    /**
     * 后端 API 基础 URL（用于邮件中的 token 回调链接）。
     * 开发环境默认 http://localhost:8090
     */
    @Value("${trackflow.server-url:http://localhost:8090}")
    private String serverUrl;

    /**
     * 构建前端路由相对路径（含可选的 hash 锚点）。
     *
     * @param resourceType 资源类型（issue/project/sprint）
     * @param resourceId   资源 ID
     * @param projectId    关联项目 ID（sprint 类型需要）
     * @param sourceId     来源子资源 ID（如评论 ID），非 null 时附加 hash 锚点 #c_{sourceId}
     * @return 前端路由路径，如 "/issues/DE4-123#c_456789"；无法构建时返回 null
     */
    public String buildPath(String resourceType, Long resourceId, Long projectId, Long sourceId) {
        String path = buildPath(resourceType, resourceId, projectId);
        if (path == null) {
            return null;
        }
        // 仅 issue 类型支持评论锚点
        if (sourceId != null && "issue".equals(resourceType)) {
            return path + "#c_" + sourceId;
        }
        return path;
    }

    /**
     * 构建前端路由相对路径（无 sourceId 的兼容版本）。
     *
     * @param resourceType 资源类型（issue/project/sprint）
     * @param resourceId   资源 ID
     * @param projectId    关联项目 ID（sprint 类型需要）
     * @return 前端路由路径，如 "/issues/DE4-123"；无法构建时返回 null
     */
    public String buildPath(String resourceType, Long resourceId, Long projectId) {
        if (resourceType == null || resourceId == null) {
            return null;
        }
        try {
            return switch (resourceType) {
                case "issue" -> buildIssuePath(resourceId);
                case "project" -> "/projects/" + resourceId;
                case "sprint" -> buildSprintPath(resourceId, projectId);
                default -> {
                    log.debug("[NotificationUrlBuilder] 未知资源类型: {}", resourceType);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.warn("[NotificationUrlBuilder] 构建 URL 失败: resourceType={}, resourceId={}, error={}",
                    resourceType, resourceId, e.getMessage());
            return null;
        }
    }

    /**
     * 构建邮件中的完整 URL（baseUrl + 相对路径 + 可选 hash 锚点）。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param projectId    关联项目 ID
     * @param sourceId     来源子资源 ID（如评论 ID），非 null 时附加 hash
     * @return 完整 URL；无法构建时返回 null
     */
    public String buildFullUrl(String resourceType, Long resourceId, Long projectId, Long sourceId) {
        String path = buildPath(resourceType, resourceId, projectId, sourceId);
        return buildFullUrl(path);
    }

    /**
     * 构建邮件中的完整 URL（baseUrl + 相对路径），无 hash 版。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param projectId    关联项目 ID
     * @return 完整 URL，如 "http://localhost:3000/issues/DE4-123"；无法构建时返回 null
     */
    public String buildFullUrl(String resourceType, Long resourceId, Long projectId) {
        String path = buildPath(resourceType, resourceId, projectId);
        return buildFullUrl(path);
    }

    /**
     * 根据已有的相对路径构建完整 URL。
     *
     * @param relativePath 前端路由相对路径（如 "/issues/DE4-123"）
     * @return 完整 URL；path 为 null 时返回 null
     */
    public String buildFullUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        // 确保 baseUrl 末尾无 /，path 开头有 /
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        return base + path;
    }

    /**
     * 构建 Issue 路由路径。使用 issueKey（如 DE4-123）而非 DB ID。
     */
    private String buildIssuePath(Long resourceId) {
        Issue issue = issueMapper.selectById(resourceId);
        if (issue != null && issue.getIssueKey() != null) {
            return "/issues/" + issue.getIssueKey();
        }
        // fallback：使用 DB ID（前端也支持）
        return "/issues/" + resourceId;
    }

    /**
     * 构建 Sprint 路由路径。Sprint 页面是项目级别的，需要 projectId 定位。
     */
    private String buildSprintPath(Long resourceId, Long projectId) {
        if (projectId != null) {
            return "/sprints?projectId=" + projectId;
        }
        // 无 projectId 时仅跳到 sprint 列表
        return "/sprints";
    }

    /**
     * 构建后端 API 的完整 URL（serverUrl + 相对路径）。
     * <p>
     * 用于邮件中的 token 回调链接（如静音 token 端点），
     * 与 buildFullUrl() 区别在于使用的是后端地址而非前端地址。
     *
     * @param apiPath 后端 API 路径（如 "/api/v1/notifications/mute-via-email?token=xxx"）
     * @return 完整 URL，如 "http://localhost:8090/api/v1/notifications/mute-via-email?token=xxx"
     */
    public String buildBackendUrl(String apiPath) {
        if (apiPath == null || apiPath.isBlank()) {
            return null;
        }
        String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        String path = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
        return base + path;
    }
}
