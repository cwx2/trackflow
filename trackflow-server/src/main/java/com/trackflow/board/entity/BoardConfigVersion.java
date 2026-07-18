package com.trackflow.board.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 看板配置版本实体（乐观锁并发控制）。
 * <p>
 * 每个项目维护一个版本号，任何看板配置变更时通过 CAS 检查版本一致性。
 */
@Data
@TableName("board_config_version")
public class BoardConfigVersion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID，每项目一行 */
    private Long projectId;

    /** 版本号，每次保存递增 */
    private Integer version;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    /** 最后更新人 ID */
    private Long updatedBy;
}
