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

        // 强制覆盖 updatedAt/updatedBy，无论字段是否已有值
        // strictUpdateFill 仅在字段为 null 时填充，不适用于从 DB 加载后再 updateById 的场景
        this.setFieldValByName("updatedAt", now, metaObject);
        this.setFieldValByName("updatedBy", currentUserId, metaObject);
    }
}
