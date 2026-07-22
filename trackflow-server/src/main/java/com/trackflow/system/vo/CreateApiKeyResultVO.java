package com.trackflow.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * API Key 创建结果 VO - 仅在创建成功后返回一次，包含明文 Key
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class CreateApiKeyResultVO {
    private String id;
    private String name;
    private String key;
    private String prefix;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
