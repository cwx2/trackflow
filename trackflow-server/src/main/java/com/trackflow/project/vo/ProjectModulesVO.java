package com.trackflow.project.vo;

import lombok.Data;

import java.util.List;

/**
 * 项目模块配置 VO
 */
@Data
public class ProjectModulesVO {

    /** 当前项目启用的模块列表 */
    private List<String> enabledModules;

    /** 系统支持的所有模块 */
    private List<String> allModules;

    /** 核心模块（不可禁用） */
    private List<String> coreModules;
}
