package com.trackflow.common.model;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.util.PageHelper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.Set;

/**
 * 分页查询基类
 * <p>
 * 所有分页查询 DTO 应继承此类，自动获得分页参数和校验能力。
 * 子类必须覆写 {@link #allowedSortFields()} 定义允许排序的字段白名单。
 */
@Data
public abstract class PageQuery {

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 20;

    private String sort;

    /**
     * 允许排序的数据库列名白名单（snake_case 格式）。
     * <p>
     * 子类必须覆写此方法，返回该模块允许的排序字段集合。
     * 不在白名单中的排序字段将被静默忽略。
     *
     * @return 允许排序的列名集合
     */
    protected Set<String> allowedSortFields() {
        return Set.of("id", "created_at", "updated_at");
    }

    /**
     * 将查询参数转换为 MyBatis-Plus 分页对象
     *
     * @param <T> 实体类型
     * @return 分页对象
     */
    public <T> Page<T> toPage() {
        return PageHelper.buildPage(page, pageSize, sort, allowedSortFields());
    }
}
