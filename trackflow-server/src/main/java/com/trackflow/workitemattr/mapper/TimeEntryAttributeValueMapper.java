package com.trackflow.workitemattr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.workitemattr.entity.TimeEntryAttributeValue;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 批量查询工时记录的 Work type 属性值
     * 用于列表视图快速获取工作类型
     */
    @Select("""
        SELECT teav.time_entry_id, teav.value_id,
               wiav.name AS value_name, wiav.color AS value_color
        FROM time_entry_attribute_value teav
        JOIN work_item_attribute_value wiav ON wiav.id = teav.value_id
        WHERE teav.attribute_id = #{workTypeAttributeId}
          AND teav.time_entry_id IN (${timeEntryIds})
    """)
    List<Map<String, Object>> selectWorkTypeForEntries(
            @Param("workTypeAttributeId") Long workTypeAttributeId,
            @Param("timeEntryIds") String timeEntryIds);

    /**
     * 转移属性值引用：将所有引用 fromValueId 的工时记录迁移到 targetValueId
     * 参考 OpenProject TimeEntryActivity#transfer_relations(to)
     *
     * @return 受影响的行数
     */
    @Update("""
        UPDATE time_entry_attribute_value
        SET value_id = #{targetValueId}
        WHERE value_id = #{fromValueId}
    """)
    int transferValueReferences(@Param("fromValueId") Long fromValueId,
                                @Param("targetValueId") Long targetValueId);

    /**
     * 删除指定项目中引用某属性值的工时记录属性关联
     * 对标 YouTrack: 在项目中移除一个值时，永久删除本项目内使用该值的工时记录中的对应属性值
     *
     * @return 受影响的行数
     */
    @Delete("""
        DELETE FROM time_entry_attribute_value
        WHERE value_id = #{valueId}
          AND time_entry_id IN (
              SELECT id FROM time_entry WHERE project_id = #{projectId}
          )
    """)
    int deleteByProjectAndValue(@Param("projectId") Long projectId,
                                @Param("valueId") Long valueId);
}
