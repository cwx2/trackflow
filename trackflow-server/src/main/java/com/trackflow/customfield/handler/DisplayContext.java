package com.trackflow.customfield.handler;

import java.util.Map;

/**
 * 展示值解析上下文 — 携带预加载的映射表供 Handler 使用。
 * <p>
 * 批量场景下，调用方预加载 option/user 映射表后注入此上下文，
 * 避免每个 Handler 单独查库。
 *
 * @author TrackFlow
 * @since 1.0
 */
public record DisplayContext(
        /** 选项 ID → 选项文本（预加载）*/
        Map<Long, String> optionTextMap,
        /** 用户 ID → 用户显示名（预加载）*/
        Map<Long, String> userNameMap
) {

    public static final DisplayContext EMPTY = new DisplayContext(Map.of(), Map.of());

    public static DisplayContext of(Map<Long, String> optionTextMap, Map<Long, String> userNameMap) {
        return new DisplayContext(
                optionTextMap != null ? optionTextMap : Map.of(),
                userNameMap != null ? userNameMap : Map.of()
        );
    }
}
