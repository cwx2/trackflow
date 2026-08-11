package com.trackflow.automation.node.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 输入端口定义
 *
 * @param name        端口名称
 * @param valueType   值类型 (string/number/boolean/object/array/any)
 * @param required    是否必填
 * @param description 描述
 * @param optional    是否可选折叠（true=默认折叠，用户主动展开才显示）
 */
public record InputPortDef(String name, String label, String valueType, boolean required,
                           String description, boolean optional, Set<InputBindingMode> bindingModes) {
    public InputPortDef {
        bindingModes = bindingModes == null || bindingModes.isEmpty()
                ? defaultBindingModes(valueType) : Set.copyOf(bindingModes);
    }

    public InputPortDef(String name, String valueType, boolean required) {
        this(name, name, valueType, required, "", false, defaultBindingModes(valueType));
    }

    public InputPortDef(String name, String valueType, boolean required, String description) {
        this(name, name, valueType, required, description, false, defaultBindingModes(valueType));
    }

    public InputPortDef(String name, String valueType, boolean required,
                        String description, boolean optional) {
        this(name, name, valueType, required, description, optional, defaultBindingModes(valueType));
    }

    public InputPortDef(String name, String label, String valueType, boolean required,
                        String description, boolean optional) {
        this(name, label, valueType, required, description, optional, defaultBindingModes(valueType));
    }

    private static Set<InputBindingMode> defaultBindingModes(String valueType) {
        EnumSet<InputBindingMode> modes = EnumSet.of(InputBindingMode.literal, InputBindingMode.reference);
        if ("string".equals(valueType) || "any".equals(valueType)) modes.add(InputBindingMode.template);
        return modes;
    }
}
