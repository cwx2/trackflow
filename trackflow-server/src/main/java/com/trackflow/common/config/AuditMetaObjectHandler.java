package com.trackflow.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.trackflow.common.util.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充处理器
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long currentUserId = SecurityUtils.getCurrentUserId();

        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createdBy", Long.class, currentUserId);
        this.strictInsertFill(metaObject, "updatedBy", Long.class, currentUserId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long currentUserId = SecurityUtils.getCurrentUserId();

        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updatedBy", Long.class, currentUserId);
    }
}
