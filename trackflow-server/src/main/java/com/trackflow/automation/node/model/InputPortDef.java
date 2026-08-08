package com.trackflow.automation.node.model;

/**
 * 输入端口定义
 *
 * @param name        端口名称
 * @param valueType   值类型 (string/number/boolean/object/array/any)
 * @param required    是否必填
 * @param description 描述
 * @param optional    是否可选折叠（true=默认折叠，用户主动展开才显示）
 */
public record InputPortDef(String name, String valueType, boolean required, String description, boolean optional) {
    public InputPortDef(String name, String valueType, boolean required) {
        this(name, valueType, required, "", false);
    }

    public InputPortDef(String name, String valueType, boolean required, String description) {
        this(name, valueType, required, description, false);
    }
}
