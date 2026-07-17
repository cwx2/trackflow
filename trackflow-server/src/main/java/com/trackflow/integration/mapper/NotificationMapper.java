package com.trackflow.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.integration.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 查询各通知类型的数量分布
     */
    @Select("SELECT type, COUNT(*) AS cnt FROM notification GROUP BY type ORDER BY cnt DESC")
    List<Map<String, Object>> selectTypeDistribution();
}
