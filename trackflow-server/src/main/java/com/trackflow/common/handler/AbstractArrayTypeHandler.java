package com.trackflow.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;

/**
 * PostgreSQL 数组类型处理器的抽象基类。
 * 封装 JDBC Array 的读写逻辑，子类只需声明 PG 类型名和元素转换方法。
 *
 * @param <T> Java 端的容器类型（如 List&lt;Long&gt; 或 Integer[]）
 */
public abstract class AbstractArrayTypeHandler<T> extends BaseTypeHandler<T> {

    /**
     * 返回 PostgreSQL 数组元素类型名（如 "bigint"、"integer"）
     */
    protected abstract String getPgTypeName();

    /**
     * 将 Java 容器转为 Object[]，用于写入 JDBC Array
     */
    protected abstract Object[] toJdbcArray(T parameter);

    /**
     * 将从 JDBC Array 取出的 Object[] 转为目标 Java 容器
     */
    protected abstract T fromJdbcArray(Object[] values);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        Object[] arr = toJdbcArray(parameter);
        Array pgArray = ps.getConnection().createArrayOf(getPgTypeName(), arr);
        ps.setArray(i, pgArray);
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return extractResult(rs.getArray(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return extractResult(rs.getArray(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return extractResult(cs.getArray(columnIndex));
    }

    private T extractResult(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object[] values = (Object[]) array.getArray();
        return fromJdbcArray(values);
    }
}
