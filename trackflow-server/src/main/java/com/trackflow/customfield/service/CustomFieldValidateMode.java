package com.trackflow.customfield.service;

/**
 * 自定义字段验证模式。
 *
 * <p>区分"创建"和"更新"场景下对必填字段的校验行为：
 * <ul>
 *   <li>FULL — 创建场景：所有必填字段必须在请求中有值（或 EAV 表已有值）</li>
 *   <li>PARTIAL — 更新场景：仅验证本次传入的字段，未传入的字段跳过必填检查</li>
 * </ul>
 *
 * <p>参考 OpenProject 的 validate_custom_fields 机制：
 * 只有前端明确要求全量验证时才检查所有字段，否则只验证本次变更涉及的字段。
 */
public enum CustomFieldValidateMode {

    /**
     * 全量验证：所有适用的必填字段都必须有值。
     * 用于工单创建、前端表单完整提交等场景。
     */
    FULL,

    /**
     * 部分验证：仅验证请求中包含的字段。
     * 未在请求中的必填字段不校验（假定它们保持已有值不变）。
     * 用于 PATCH/部分更新、API 增量更新等场景。
     */
    PARTIAL
}
