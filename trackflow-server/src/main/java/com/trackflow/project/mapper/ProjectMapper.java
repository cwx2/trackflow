package com.trackflow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 查询指定项目中 issue_key 序号的实际最大值
     */
    @Select("SELECT MAX(CAST(SUBSTRING(issue_key FROM '([0-9]+)$') AS INT)) FROM issue WHERE project_id = #{projectId} AND deleted_at IS NULL AND issue_key ~ '^[A-Z0-9]+-[0-9]+$'")
    Integer selectMaxIssueSequence(@Param("projectId") Long projectId);

    /**
     * 查询指定可见性的所有活跃项目 ID（用于 NonMember 访问控制）
     */
    @Select("<script>" +
            "SELECT id FROM project WHERE status = 'active' AND visibility IN " +
            "<foreach item='v' collection='visibilities' open='(' separator=',' close=')'>" +
            "#{v}" +
            "</foreach>" +
            "</script>")
    List<Long> selectProjectIdsByVisibility(@Param("visibilities") List<String> visibilities);

    /**
     * 查询所有活跃项目的 ID（用于系统管理员全项目模式，走索引而非全表扫描）
     */
    @Select("SELECT id FROM project WHERE status = 'active'")
    List<Long> selectAllActiveProjectIds();

    /**
     * 原子更新 settings JSONB 中的单个 key（使用 PostgreSQL jsonb_set，无竞态条件）。
     * 如果 settings 列为 NULL，先初始化为空 JSON 对象再设置。
     */
    @Update("UPDATE project SET settings = jsonb_set(COALESCE(settings, '{}')::jsonb, ARRAY[#{key}], #{value}::jsonb), " +
            "updated_at = NOW(), updated_by = #{updatedBy} WHERE id = #{projectId}")
    int updateSettingKey(@Param("projectId") Long projectId,
                         @Param("key") String key,
                         @Param("value") String value,
                         @Param("updatedBy") Long updatedBy);

    /**
     * 原子删除 settings JSONB 中的单个 key（使用 PostgreSQL - 操作符）。
     */
    @Update("UPDATE project SET settings = COALESCE(settings, '{}')::jsonb - #{key}, " +
            "updated_at = NOW(), updated_by = #{updatedBy} WHERE id = #{projectId}")
    int removeSettingKey(@Param("projectId") Long projectId,
                         @Param("key") String key,
                         @Param("updatedBy") Long updatedBy);
}
