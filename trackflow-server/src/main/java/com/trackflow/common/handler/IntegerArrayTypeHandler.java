package com.trackflow.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;

/**
 * PostgreSQL INTEGER[] 数组类型处理器。
 * 将 Java Integer[] 映射到 PostgreSQL 的 INTEGER[] 类型。
 */
public class IntegerArrayTypeHandler extends BaseTypeHandler<Integer[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Integer[] parameter, JdbcType jdbcType) throws SQLException {
        Array pgArray = ps.getConnection().createArrayOf("integer", parameter);
        ps.setArray(i, pgArray);
    }

    @Override
    public Integer[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return extractArray(rs.getArray(columnName));
    }

    @Override
    public Integer[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return extractArray(rs.getArray(columnIndex));
    }

    @Override
    public Integer[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return extractArray(cs.getArray(columnIndex));
    }

    private Integer[] extractArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object[] values = (Object[]) array.getArray();
        Integer[] result = new Integer[values.length];
        for (int idx = 0; idx < values.length; idx++) {
            if (values[idx] instanceof Number num) {
                result[idx] = num.intValue();
            }
        }
        return result;
    }
}
