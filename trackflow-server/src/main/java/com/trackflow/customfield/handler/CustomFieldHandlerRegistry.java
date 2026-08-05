package com.trackflow.customfield.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自定义字段类型处理器注册表 — 通过 Spring 自动注入所有 {@link CustomFieldTypeHandler} 实现。
 * <p>
 * 各 Service 通过 {@link #getHandler(String)} 按字段类型查找对应处理器，
 * 实现类型分发与类型处理逻辑的解耦。新增字段类型只需创建新的 @Component，
 * 无需修改此类或任何现有 Service。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class CustomFieldHandlerRegistry {

    private final Map<String, CustomFieldTypeHandler> handlers;

    public CustomFieldHandlerRegistry(List<CustomFieldTypeHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(
                        CustomFieldTypeHandler::fieldFormat,
                        handler -> handler,
                        (existing, duplicate) -> {
                            log.warn("[CustomFieldHandlerRegistry] Duplicate handler for format '{}': {} vs {}. Using first.",
                                    existing.fieldFormat(),
                                    existing.getClass().getSimpleName(),
                                    duplicate.getClass().getSimpleName());
                            return existing;
                        }
                ));
        log.info("[CustomFieldHandlerRegistry] Registered {} type handlers: {}", handlers.size(), handlers.keySet());
    }

    /**
     * 根据 fieldFormat 获取对应的类型处理器。
     *
     * @param fieldFormat 字段类型标识
     * @return 处理器（如果存在）
     */
    public Optional<CustomFieldTypeHandler> getHandler(String fieldFormat) {
        return Optional.ofNullable(handlers.get(fieldFormat));
    }

    /**
     * 根据 fieldFormat 获取对应的类型处理器，不存在时抛异常。
     *
     * @param fieldFormat 字段类型标识
     * @return 处理器
     * @throws IllegalArgumentException 如果字段类型不支持
     */
    public CustomFieldTypeHandler getHandlerOrThrow(String fieldFormat) {
        CustomFieldTypeHandler handler = handlers.get(fieldFormat);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported field format: " + fieldFormat);
        }
        return handler;
    }

    /**
     * 获取所有已注册的字段类型。
     */
    public Set<String> getSupportedFormats() {
        return handlers.keySet();
    }

    /**
     * 获取所有支持排序的字段类型。
     */
    public Set<String> getSortableFormats() {
        return handlers.entrySet().stream()
                .filter(e -> e.getValue().isSortable())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
