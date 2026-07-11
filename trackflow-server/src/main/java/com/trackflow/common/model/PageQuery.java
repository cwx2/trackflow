package com.trackflow.common.model;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.util.PageHelper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询基类
 * <p>
 * 所有分页查询 DTO 应继承此类，自动获得分页参数和校验能力。
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
     * 将查询参数转换为 MyBatis-Plus 分页对象
     *
     * @param <T> 实体类型
     * @return 分页对象
     */
    public <T> Page<T> toPage() {
        return PageHelper.buildPage(page, pageSize, sort);
    }
}
