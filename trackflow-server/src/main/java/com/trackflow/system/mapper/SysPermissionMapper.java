package com.trackflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.system.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询所有项目级权限的 code → category 映射。
     * 用于项目模块过滤：只有启用模块下的权限才生效。
     * 返回 List<Map>，每个 Map 包含 code 和 category。
     */
    @Select("SELECT code, category FROM sys_permission WHERE scope = 'project'")
    List<Map<String, String>> selectProjectPermissionCategories();
}
