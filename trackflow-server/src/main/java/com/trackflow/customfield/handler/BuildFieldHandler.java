package com.trackflow.customfield.handler;

import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import org.springframework.stereotype.Component;

/**
 * 构建号字段处理器 — 与 list 共享校验/展示/排序逻辑。
 * <p>
 * 选项额外携带 assembleDate（构建生成日期），与 version 类似但侧重 CI/CD 构建产物。
 * 典型用途：Fixed in build、Affected build 等。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
public class BuildFieldHandler extends ListFieldHandler {

    public BuildFieldHandler(CustomFieldOptionMapper optionMapper) {
        super(optionMapper);
    }

    @Override
    public String fieldFormat() {
        return "build";
    }

    @Override
    public String typeLabel() {
        return "构建号";
    }
}
