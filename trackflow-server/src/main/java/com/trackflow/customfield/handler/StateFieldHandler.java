package com.trackflow.customfield.handler;

import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import org.springframework.stereotype.Component;

/**
 * 状态字段处理器 — 与 list 共享校验/展示/排序逻辑，仅 typeLabel 不同。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
public class StateFieldHandler extends ListFieldHandler {

    public StateFieldHandler(CustomFieldOptionMapper optionMapper) {
        super(optionMapper);
    }

    @Override
    public String fieldFormat() {
        return "state";
    }

    @Override
    public String typeLabel() {
        return "状态";
    }
}
