package com.pinkyudeer.tasket.db.builder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.pinkyudeer.tasket.db.EntityEventRecorder;
import com.pinkyudeer.tasket.db.SQLiteManager;
import com.pinkyudeer.tasket.db.annotation.Column;

/**
 * 更新操作构建器。
 * 用于构建和执行SQL UPDATE语句。
 *
 * @param <T> 实体类型
 */
public class UpdateBuilder<T> extends BaseBuilder<T, UpdateBuilder<T>> {

    /**
     * 基于实体对象的构造函数
     *
     * @param entity 实体对象
     */
    public UpdateBuilder(T entity) {
        super(entity);
        if (entity == null) {
            throw new IllegalArgumentException("更新操作的实体对象不能为空");
        }
    }

    /**
     * 基于实体类的构造函数
     *
     * @param entityClass 实体类
     */
    public UpdateBuilder(Class<T> entityClass) {
        super(entityClass);
        if (entityClass == null) {
            throw new IllegalArgumentException("更新操作的实体类不能为空");
        }
    }

    /**
     * 基于比对模式的构造函数
     *
     * @param entity    当前实体
     * @param oldEntity 旧实体（用于比对）
     */
    public UpdateBuilder(T entity, T oldEntity) {
        super(entity, oldEntity);
        if (entity == null) {
            throw new IllegalArgumentException("更新操作的实体对象不能为空");
        }
    }

    /**
     * 设置旧实体对象，用于比较并只更新变更的字段
     *
     * @param oldEntity 用于比较的实体对象
     * @return this
     */
    public UpdateBuilder<T> onlyChangesFrom(T oldEntity) {
        // 使用父类中的oldEntity，启用比较模式
        this.compareMode = true;
        return new UpdateBuilder<>(this.entity, oldEntity);
    }

    @Override
    public Integer execute() {
        Map<String, Object> columnValues;

        // 检查是否启用了比较模式
        if (compareMode && oldEntity != null) {
            // 使用基类的getDifferentValues方法获取差异字段
            columnValues = getDifferentValues();
            if (columnValues.isEmpty()) {
                return 0; // 没有变化，不执行更新
            }
        } else {
            // 使用所有字段（原始行为）
            columnValues = getColumnValues();

            // 确保有字段被更新
            if (columnValues.isEmpty()) {
                throw new IllegalStateException("没有可以更新的列值");
            }
        }

        VersionState versionState = prepareVersion(columnValues, null);
        Integer count = executeUpdate(columnValues, versionState);
        if (count != null && count == 0 && versionState != null) {
            Integer latestVersion = readLatestVersion();
            if (latestVersion != null && !latestVersion.equals(versionState.expectedVersion)) {
                versionState = prepareVersion(columnValues, latestVersion);
                count = executeUpdate(columnValues, versionState);
            }
        }
        if (count != null && count > 0 && versionState != null) {
            EntityEventRecorder.recordUpdate(
                getTableName(),
                entity,
                versionState.expectedVersion,
                versionState.newVersion,
                columnValues.keySet());
        }
        return count;
    }

    private Integer executeUpdate(Map<String, Object> columnValues, VersionState versionState) {
        StringBuilder setClause = new StringBuilder();
        List<Object> executeParams = new ArrayList<>();
        boolean first = true;

        for (Map.Entry<String, Object> entry : columnValues.entrySet()) {
            if (!first) setClause.append(", ");
            setClause.append(entry.getKey())
                .append(" = ?");
            executeParams.add(entry.getValue());
            first = false;
        }

        String sql = String.format("UPDATE %s SET %s", getTableName(), setClause);

        sql = addWhereClause(sql, executeParams, true, "更新");
        if (versionState != null) {
            sql += " AND version = ?";
            executeParams.add(versionState.expectedVersion);
        }

        return SQLiteManager.executeUpdate(sql, executeParams.toArray());
    }

    private VersionState prepareVersion(Map<String, Object> columnValues, Integer expectedVersionOverride) {
        Field versionField = getVersionField();
        if (versionField == null || entity == null) return null;

        Integer expectedVersion = expectedVersionOverride != null ? expectedVersionOverride
            : readVersion(oldEntity == null ? entity : oldEntity, versionField);
        if (expectedVersion == null) expectedVersion = 0;

        int nextVersion = expectedVersion + 1;
        writeVersion(entity, versionField, nextVersion);
        columnValues.put("version", nextVersion);
        return new VersionState(expectedVersion, nextVersion);
    }

    private Field getVersionField() {
        for (Field field : getColumnFields(entityClass)) {
            Column column = field.getAnnotation(Column.class);
            if (column != null && "version".equals(column.name())) return field;
        }
        return null;
    }

    private Integer readVersion(Object target, Field versionField) {
        if (target == null) return null;
        try {
            versionField.setAccessible(true);
            Object value = versionField.get(target);
            if (value instanceof Number number) return number.intValue();
            return value == null ? null : Integer.parseInt(value.toString());
        } catch (IllegalAccessException | NumberFormatException e) {
            throw new RuntimeException("无法读取 version 字段", e);
        }
    }

    private void writeVersion(Object target, Field versionField, int version) {
        try {
            versionField.setAccessible(true);
            versionField.set(target, version);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("无法写入 version 字段", e);
        }
    }

    private Integer readLatestVersion() {
        try {
            Field primaryKeyField = getPrimaryKeyField();
            primaryKeyField.setAccessible(true);
            Object primaryKey = primaryKeyField.get(entity);
            if (primaryKey == null) return null;
            String primaryKeyColumn = getColumnName(primaryKeyField);
            return SQLiteManager.query(
                "SELECT version FROM " + getTableName() + " WHERE " + primaryKeyColumn + " = ? LIMIT 1",
                rs -> rs.next() ? rs.getInt("version") : null,
                primaryKey);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("无法读取主键字段", e);
        }
    }

    private static class VersionState {

        private final Integer expectedVersion;
        private final Integer newVersion;

        private VersionState(Integer expectedVersion, Integer newVersion) {
            this.expectedVersion = expectedVersion;
            this.newVersion = newVersion;
        }
    }
}
