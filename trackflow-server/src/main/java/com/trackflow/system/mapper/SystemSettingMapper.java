package com.trackflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.system.entity.SystemSetting;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统设置 Mapper
 */
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {

    @Select("SELECT * FROM system_setting WHERE category = #{category}")
    List<SystemSetting> selectByCategory(String category);

    @Select("SELECT * FROM system_setting WHERE setting_key = #{settingKey}")
    SystemSetting selectByKey(String settingKey);
}
