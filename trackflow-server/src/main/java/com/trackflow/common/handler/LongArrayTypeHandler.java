package com.trackflow.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL BIGINT[] 数组类型处理器
 * 将 Java List<Long> 映射到 PostgreSQL 的 BIGINT[] 类型
 */
public class LongArrayTypeHandler extends BaseTypeHandler<List<Long>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Long> parameter, JdbcType jdbcType) throws SQLException {
        Long[] arr = parameter.toArray(new Long[0]);
        Array pgArray = ps.getConnection().createArrayOf("bigint", arr);
        ps.setArray(i, pgArray);
    }

    @Override
    public List<Long> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return extractList(rs.getArray(columnName));
    }

    @Override
    public List<Long> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return extractList(rs.getArray(columnIndex));
    }

    @Override
    public List<Long> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return extractList(cs.getArray(columnIndex));
    }

    private List<Long> extractList(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object[] values = (Object[]) array.getArray();
        List<Long> result = new ArrayList<>(values.length);
        for (Object val : values) {
            if (val instanceof Number num) {
                result.add(num.longValue());
            }
        }
        return result;
    }
}
