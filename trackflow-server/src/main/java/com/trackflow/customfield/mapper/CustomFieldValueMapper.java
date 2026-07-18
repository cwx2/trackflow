package com.trackflow.customfield.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.customfield.entity.CustomFieldValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CustomFieldValueMapper extends BaseMapper<CustomFieldValue> {

    /**
     * 获取事务级 advisory lock，用于序列化同一 (issueId, customFieldId) 的并发写入。
     * 锁在事务结束时自动释放，不需要手动释放。
     * <p>
     * 使用 hashtext 将 issueId:customFieldId 字符串哈希为 int4，
     * 第一个参数固定为 1（命名空间标识，避免与其他 advisory lock 冲突），
     * 第二个参数为组合 key 的哈希值。
     * <p>
     * 这确保了只有同一 (issueId, customFieldId) 组合的并发写入会被序列化，
     * 不同字段或不同工单的写入互不阻塞。
     * <p>
     * 注意：使用 @Update 注解而非 @Select，因为 pg_advisory_xact_lock 返回 void，
     * @Select 会因无法映射结果集导致 MyBatis 报错。
     */
    @Update("SELECT pg_advisory_xact_lock(1, hashtext(#{issueId} || ':' || #{customFieldId}))")
    void acquireSingleValueLock(@Param("issueId") long issueId, @Param("customFieldId") long customFieldId);
}
