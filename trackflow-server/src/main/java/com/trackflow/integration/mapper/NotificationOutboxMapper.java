package com.trackflow.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.integration.entity.NotificationOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知发件箱 Mapper
 */
@Mapper
public interface NotificationOutboxMapper extends BaseMapper<NotificationOutbox> {

    /**
     * 查询到期的待重试记录（status=pending 且 next_retry_at <= now）
     */
    @Select("""
            SELECT * FROM notification_outbox
            WHERE status = 'pending' AND next_retry_at <= #{now}
            ORDER BY next_retry_at ASC
            LIMIT #{limit}
            """)
    List<NotificationOutbox> selectPendingForRetry(LocalDateTime now, int limit);

    /**
     * 统计各状态的数量
     */
    @Select("""
            SELECT status, COUNT(*) AS cnt FROM notification_outbox
            GROUP BY status
            """)
    List<java.util.Map<String, Object>> selectStatusCounts();

    /**
     * 清理已完成的旧记录（保留期以内的完成记录）
     */
    @Update("""
            DELETE FROM notification_outbox
            WHERE status = 'completed' AND completed_at < #{cutoff}
            """)
    int cleanupCompleted(LocalDateTime cutoff);
}
