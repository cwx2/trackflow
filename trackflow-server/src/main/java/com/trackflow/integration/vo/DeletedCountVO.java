package com.trackflow.integration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除操作结果计数 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeletedCountVO {

    /** 已删除的记录数 */
    private int deleted;
}
