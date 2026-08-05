package com.trackflow.common.handler;

import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL BIGINT[] 数组类型处理器。
 * 将 Java List&lt;Long&gt; 映射到 PostgreSQL 的 BIGINT[] 类型。
 */
public class LongArrayTypeHandler extends AbstractArrayTypeHandler<List<Long>> {

    @Override
    protected String getPgTypeName() {
        return "bigint";
    }

    @Override
    protected Object[] toJdbcArray(List<Long> parameter) {
        return parameter.toArray(new Long[0]);
    }

    @Override
    protected List<Long> fromJdbcArray(Object[] values) {
        List<Long> result = new ArrayList<>(values.length);
        for (Object val : values) {
            if (val instanceof Number num) {
                result.add(num.longValue());
            }
        }
        return result;
    }
}
