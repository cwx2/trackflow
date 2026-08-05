package com.trackflow.common.controller;

import com.trackflow.common.service.MinioService;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.service.IssueAttachmentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FileController {

    private final MinioService minioService;
    private final IssueAttachmentService attachmentService;

    /**
     * 文件下载/预览（支持任意深度路径）
     * GET /api/v1/files/issues/123/attachments/uuid.png
     *
     * 增加附件可见性校验：如果请求的文件路径对应一个有可见性限制的附件，
     * 校验当前用户是否有权访问。
     */
    @GetMapping("/**")
    public void download(HttpServletResponse response,
                         jakarta.servlet.http.HttpServletRequest request) {
        // 提取完整路径（去掉 /api/v1/files/ 前缀）
        String fullPath = request.getRequestURI();
        String objectName = fullPath.substring("/api/v1/files/".length());

        // 附件可见性校验：通过文件路径查找附件记录
        if (objectName.startsWith("issues/") && objectName.contains("/attachments/")) {
            IssueAttachment attachment = attachmentService.findByFilePath(objectName);
            if (attachment != null
                    && attachment.getVisibleToGroupIds() != null
                    && !attachment.getVisibleToGroupIds().isEmpty()) {
                // 私有附件——检查当前用户是否有权访问
                if (!attachmentService.canAccessAttachment(attachment.getId())) {
                    response.setStatus(403);
                    return;
                }
            }
        }

        try (InputStream is = minioService.getObject(objectName)) {
            // 根据文件扩展名设置 Content-Type
            String contentType = guessContentType(objectName);
            response.setContentType(contentType);

            // 仅图片和 PDF 允许内联预览；其余全部强制下载（防止 XSS）
            if (isInlinePreviewAllowed(contentType)) {
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            } else {
                // 尝试从 DB 获取原始文件名（附件可能使用 UUID 存储路径）
                String fileName = resolveOriginalFileName(objectName);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
            }

            OutputStream os = response.getOutputStream();
            is.transferTo(os);
            os.flush();
        } catch (Exception e) {
            log.error("File download failed: {}", objectName, e);
            response.setStatus(404);
        }
    }

    /**
     * 判断是否允许内联预览（仅图片和 PDF）
     * HTML/JS/SVG 等可执行内容不允许内联（防 XSS）
     */
    private boolean isInlinePreviewAllowed(String contentType) {
        if (contentType == null) return false;
        // 允许标准图片格式内联（SVG 除外，SVG 可包含脚本）
        if (contentType.startsWith("image/") && !contentType.contains("svg")) {
            return true;
        }
        // 允许 PDF 内联
        return "application/pdf".equals(contentType);
    }

    /**
     * 从数据库查询附件的原始文件名。
     * 如果路径不是附件路径或 DB 中找不到记录，则降级为从路径截取。
     */
    private String resolveOriginalFileName(String objectName) {
        if (objectName.startsWith("issues/") && objectName.contains("/attachments/")) {
            IssueAttachment attachment = attachmentService.findByFilePath(objectName);
            if (attachment != null && attachment.getFileName() != null && !attachment.getFileName().isBlank()) {
                return attachment.getFileName();
            }
        }
        // 降级：从路径截取文件名
        return objectName.substring(objectName.lastIndexOf('/') + 1);
    }

    private String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF_VALUE;
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON_VALUE;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return MediaType.TEXT_HTML_VALUE;
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".patch")) return MediaType.TEXT_PLAIN_VALUE;
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".zip")) return "application/zip";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
