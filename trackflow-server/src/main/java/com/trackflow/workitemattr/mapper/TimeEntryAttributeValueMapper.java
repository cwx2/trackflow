package com.trackflow.workitemattr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.workitemattr.entity.TimeEntryAttributeValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TimeEntryAttributeValueMapper extends BaseMapper<TimeEntryAttributeValue> {

    /**
     * 批量查询工时记录的属性值（含属性名和值名）
     */
    @Select("""
        SELECT teav.time_entry_id, teav.attribute_id, teav.value_id,
               wia.name AS attribute_name,
               wiav.name AS value_name, wiav.color AS value_color
        FROM time_entry_attribute_value teav
        JOIN work_item_attribute wia ON wia.id = teav.attribute_id
        JOIN work_item_attribute_value wiav ON wiav.id = teav.value_id
        WHERE teav.time_entry_id IN (${timeEntryIds})
        ORDER BY wia.position, wiav.position
    """)
    List<Map<String, Object>> selectAttributeValuesForEntries(@Param("timeEntryIds") String timeEntryIds);
}
