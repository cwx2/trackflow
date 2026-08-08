package com.trackflow.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

/**
 * SpEL 表达式解析工具类。
 * <p>
 * 提供通用的方法参数绑定 + SpEL 表达式求值能力，
 * 被 {@code @AuditLog}、{@code @DistributedLock} 等注解切面复用。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
public final class SpELUtils {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAM_DISCOVERER = new DefaultParameterNameDiscoverer();

    private SpELUtils() {
    }

    /**
     * 解析 SpEL 表达式，支持方法参数和返回值引用。
     *
     * @param expression SpEL 表达式（如 "#userId"、"#dto.projectId"、"#result?.id"）
     * @param method     当前方法
     * @param args       方法参数值数组
     * @param result     方法返回值（可为 null）
     * @return 解析结果的字符串表示，解析失败返回 null
     */
    public static String parseToString(String expression, Method method, Object[] args, Object result) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        Object value = parse(expression, method, args, result);
        return value != null ? value.toString() : null;
    }

    /**
     * 解析 SpEL 表达式，返回原始对象。
     *
     * @param expression SpEL 表达式
     * @param method     当前方法
     * @param args       方法参数值数组
     * @param result     方法返回值（可为 null）
     * @return 解析结果对象，解析失败返回 null
     */
    public static Object parse(String expression, Method method, Object[] args, Object result) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            StandardEvaluationContext context = buildContext(method, args, result);
            return PARSER.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            log.warn("[SpELUtils] SpEL 解析失败: expression='{}', error={}", expression, e.getMessage());
            return null;
        }
    }

    /**
     * 构建 SpEL 求值上下文，绑定方法参数名和返回值。
     *
     * @param method 方法
     * @param args   参数值
     * @param result 返回值
     * @return 配置好的 EvaluationContext
     */
    public static StandardEvaluationContext buildContext(Method method, Object[] args, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 绑定方法参数
        String[] paramNames = PARAM_DISCOVERER.getParameterNames(method);
        if (paramNames != null && args != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        // 绑定返回值（通过 #result 引用）
        if (result != null) {
            context.setVariable("result", result);
        }

        return context;
    }
}
