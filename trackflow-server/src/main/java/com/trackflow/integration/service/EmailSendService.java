package com.trackflow.integration.service;

import com.trackflow.integration.vo.EmailAvailabilityVO;
import com.trackflow.integration.vo.EmailConfigVO;
import com.trackflow.system.service.SystemSettingService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 邮件发送服务。
 * <p>
 * 从 email_config 动态读取 SMTP 配置创建 JavaMailSender 实例，
 * 支持发送通知邮件和测试邮件。
 * <p>
 * 参考 OpenProject: UserMailer#test_mail + Notifications::MailService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSendService {

    private final EmailConfigService emailConfigService;
    private final SystemSettingService systemSettingService;

    private static final String GLOBAL_EMAIL_ENABLED_KEY = "notification.email_enabled";

    /**
     * 检查全局邮件通知是否启用且 SMTP 已配置
     */
    public boolean isEmailAvailable() {
        // 全局开关
        String enabled = systemSettingService.getSettingValue(GLOBAL_EMAIL_ENABLED_KEY, "false");
        if (!Boolean.parseBoolean(enabled)) {
            return false;
        }
        // SMTP 配置完整性
        EmailConfigVO config = emailConfigService.getConfig();
        return Boolean.TRUE.equals(config.getConfigured());
    }

    /**
     * 获取邮件可用状态详情（供用户偏好页展示）
     */
    public EmailAvailabilityVO getEmailAvailability() {
        String enabled = systemSettingService.getSettingValue(GLOBAL_EMAIL_ENABLED_KEY, "false");
        boolean globalEnabled = Boolean.parseBoolean(enabled);

        EmailConfigVO config = emailConfigService.getConfig();
        boolean smtpConfigured = Boolean.TRUE.equals(config.getConfigured());

        boolean available = globalEnabled && smtpConfigured;
        String reason = null;
        if (!globalEnabled) {
            reason = "管理员尚未启用邮件通知渠道";
        } else if (!smtpConfigured) {
            reason = "邮件服务器尚未配置，请联系管理员";
        }

        return new EmailAvailabilityVO(available, globalEnabled, smtpConfigured, reason);
    }

    /**
     * 发送测试邮件（同步，调用方捕获异常判断结果）
     *
     * @param toAddress 目标邮件地址
     * @throws MessagingException 发送失败时抛出
     */
    public void sendTestEmail(String toAddress) throws MessagingException {
        JavaMailSender mailSender = createMailSender();
        EmailConfigVO config = emailConfigService.getConfig();

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

        helper.setFrom(config.getFromAddress());
        if (config.getReplyToAddress() != null && !config.getReplyToAddress().isEmpty()) {
            helper.setReplyTo(config.getReplyToAddress());
        }
        helper.setTo(toAddress);
        helper.setSubject("[TrackFlow] 测试邮件 - SMTP 配置验证");
        helper.setText(buildTestEmailContent(), true);

        mailSender.send(message);
        log.info("[EmailSend] 测试邮件已发送至: {}", toAddress);
    }

    /**
     * 异步发送通知邮件（不阻塞主流程，失败仅记录日志）
     *
     * @param toAddress 收件人地址
     * @param subject   邮件主题
     * @param content   邮件正文（HTML）
     */
    @Async("notificationExecutor")
    public void sendNotificationEmail(String toAddress, String subject, String content) {
        try {
            JavaMailSender mailSender = createMailSender();
            EmailConfigVO config = emailConfigService.getConfig();

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(config.getFromAddress());
            if (config.getReplyToAddress() != null && !config.getReplyToAddress().isEmpty()) {
                helper.setReplyTo(config.getReplyToAddress());
            }
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.debug("[EmailSend] 通知邮件已发送: to={}, subject={}", toAddress, subject);
        } catch (Exception e) {
            log.error("[EmailSend] 通知邮件发送失败: to={}, subject={}, error={}",
                    toAddress, subject, e.getMessage(), e);
        }
    }

    /**
     * 根据数据库中的 SMTP 配置动态创建 JavaMailSender 实例。
     * 每次调用都创建新实例，确保读取最新配置。
     */
    private JavaMailSender createMailSender() {
        EmailConfigVO config = emailConfigService.getConfig();
        String password = emailConfigService.getDecryptedPassword();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        sender.setDefaultEncoding("UTF-8");

        // 认证
        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            sender.setUsername(config.getUsername());
            sender.setPassword(password);
        }

        // 协议与加密
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");

        switch (config.getProtocol()) {
            case "ssl" -> {
                sender.setPort(config.getPort() != null ? config.getPort() : 465);
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            }
            case "starttls" -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            default -> {
                // plain - no encryption
            }
        }

        // 认证开关
        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            props.put("mail.smtp.auth", "true");
        }

        // 超时设置（防止连接挂起）
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }

    /**
     * 构建测试邮件 HTML 内容
     */
    private String buildTestEmailContent() {
        return """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #1f2328; margin: 0 0 16px 0;">✅ TrackFlow 邮件配置验证成功</h2>
                  <p style="color: #57606a; font-size: 14px; line-height: 1.6;">
                    这是一封测试邮件，用于验证您的 SMTP 服务器配置是否正确。
                  </p>
                  <p style="color: #57606a; font-size: 14px; line-height: 1.6;">
                    如果您收到了此邮件，说明邮件服务器配置无误，系统可以正常发送邮件通知。
                  </p>
                  <hr style="border: none; border-top: 1px solid #d1d9e0; margin: 24px 0;" />
                  <p style="color: #8b949e; font-size: 12px;">
                    此邮件由 TrackFlow 项目管理系统自动发送，请勿回复。
                  </p>
                </div>
                """;
    }
}
