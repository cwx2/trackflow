package com.trackflow.common.handler;

/**
 * PostgreSQL INTEGER[] 数组类型处理器。
 * 将 Java Integer[] 映射到 PostgreSQL 的 INTEGER[] 类型。
 */
public class IntegerArrayTypeHandler extends AbstractArrayTypeHandler<Integer[]> {

    @Override
    protected String getPgTypeName() {
        return "integer";
    }

    @Override
    protected Object[] toJdbcArray(Integer[] parameter) {
        return parameter;
    }

    @Override
    protected Integer[] fromJdbcArray(Object[] values) {
        Integer[] result = new Integer[values.length];
        for (int idx = 0; idx < values.length; idx++) {
            if (values[idx] instanceof Number num) {
                result[idx] = num.intValue();
            }
        }
        return result;
    }
}
