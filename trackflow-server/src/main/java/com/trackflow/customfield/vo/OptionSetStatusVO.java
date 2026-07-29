package com.trackflow.customfield.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 选项集状态 VO — 描述字段在某项目中的选项集状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionSetStatusVO {
    
    /** 是否为项目独立选项集 */
    private Boolean isIndependent;
    
    /** 选项集类型：shared / independent */
    private String optionSetType;
    
    /** 共享该选项集的项目数量（仅共享模式有意义） */
    private Integer sharedProjectCount;
    
    /** 共享该选项集的项目名称列表（用于编辑提示） */
    private List<String> sharedProjectNames;
    
    /** 是否可以创建独立副本 */
    private Boolean canMakeIndependent;
    
    /** 不能创建独立副本的原因（如已经是独立的） */
    private String cannotMakeIndependentReason;
}
