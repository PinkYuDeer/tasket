package com.pinkyudeer.tasket.db;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.bsideup.jabel.Desugar;
import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.db.annotation.Column;
import com.pinkyudeer.tasket.task.entity.EntityEvent;

public final class EntityEventRecorder {

    private static final String EVENT_TABLE = "entity_events";
    private static final int MAX_EVENTS_PER_ENTITY_TYPE = 1_000;
    private static final int PRUNE_INTERVAL = 100;
    private static final ThreadLocal<Boolean> RECORDING = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger EVENT_WRITE_COUNT = new AtomicInteger();
    private static volatile boolean eventTableKnown;

    private EntityEventRecorder() {}

    public static boolean isEventTable(String tableName) {
        return EVENT_TABLE.equals(tableName);
    }

    public static void markEventTableReady() {
        eventTableKnown = true;
    }

    public static void resetCache() {
        eventTableKnown = false;
        EVENT_WRITE_COUNT.set(0);
    }

    public static void recordInsert(String tableName, Object entity, Map<String, Object> values) {
        if (shouldSkip(tableName)) return;
        Object id = primaryKeyValue(entity);
        Integer version = integerValue(values.get("version"));
        record(tableName, id, EntityEvent.EventType.INSERT, null, version, values.keySet());
    }

    public static void recordUpdate(String tableName, Object entity, Integer oldVersion, Integer newVersion,
        Collection<String> changedFields) {
        if (shouldSkip(tableName)) return;
        Object id = primaryKeyValue(entity);
        record(tableName, id, EntityEvent.EventType.UPDATE, oldVersion, newVersion, changedFields);
    }

    public static void recordDeletes(String tableName, List<DeletedEntity> deletedEntities) {
        if (shouldSkip(tableName) || deletedEntities == null || deletedEntities.isEmpty()) return;
        for (DeletedEntity deleted : deletedEntities) {
            record(tableName, deleted.id(), EntityEvent.EventType.DELETE, deleted.version(), null, null);
        }
    }

    public static List<DeletedEntity> readDeletedEntities(String tableName, String whereClause, List<Object> params) {
        if (shouldSkip(tableName) || whereClause == null || whereClause.isEmpty()) return new ArrayList<>();
        return SQLiteManager.query("SELECT id, version FROM " + tableName + " WHERE " + whereClause, rs -> {
            List<DeletedEntity> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new DeletedEntity(rs.getObject("id"), rs.getInt("version")));
            }
            return result;
        }, params.toArray());
    }

    private static void record(String tableName, Object entityId, EntityEvent.EventType eventType, Integer oldVersion,
        Integer newVersion, Collection<String> changedFields) {
        if (entityId == null || !eventTableExists()) return;
        boolean alreadyRecording = Boolean.TRUE.equals(RECORDING.get());
        if (alreadyRecording) return;
        RECORDING.set(true);
        try {
            AsyncSqlExecutor.INSTANCE.invalidate(tableName);
            SQLiteManager.executeUpdate(
                "INSERT INTO entity_events "
                    + "(id, entity_type, entity_id, event_type, old_version, new_version, changed_fields, actor_id, timestamp) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                tableName,
                entityId.toString(),
                eventType.name(),
                oldVersion,
                newVersion,
                fieldsJson(changedFields),
                null,
                LocalDateTime.now());
            if (EVENT_WRITE_COUNT.incrementAndGet() % PRUNE_INTERVAL == 0) {
                prune(tableName);
            }
        } catch (Exception e) {
            Tasket.LOG.warn("Failed to record entity event: {} {} {}", tableName, entityId, eventType, e);
        } finally {
            RECORDING.set(false);
        }
    }

    private static void prune(String entityType) {
        SQLiteManager.executeUpdate(
            "DELETE FROM entity_events WHERE entity_type = ? AND rowid NOT IN ("
                + "SELECT rowid FROM entity_events WHERE entity_type = ? ORDER BY timestamp DESC LIMIT ?)",
            entityType,
            entityType,
            MAX_EVENTS_PER_ENTITY_TYPE);
    }

    private static boolean shouldSkip(String tableName) {
        return tableName == null || EVENT_TABLE.equals(tableName) || Boolean.TRUE.equals(RECORDING.get());
    }

    private static boolean eventTableExists() {
        if (eventTableKnown) return true;
        Boolean exists = SQLiteManager.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            rs -> rs.next(),
            EVENT_TABLE);
        eventTableKnown = Boolean.TRUE.equals(exists);
        return eventTableKnown;
    }

    private static Object primaryKeyValue(Object entity) {
        if (entity == null) return null;
        for (Field field : com.pinkyudeer.tasket.helper.UtilHelper.getAllFields(entity.getClass())) {
            Column column = field.getAnnotation(Column.class);
            if (column == null || !column.isPrimaryKey()) continue;
            try {
                field.setAccessible(true);
                return field.get(entity);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("无法读取主键字段", e);
            }
        }
        return null;
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fieldsJson(Collection<String> fields) {
        if (fields == null || fields.isEmpty()) return "[]";
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (String field : fields) {
            if (field == null || field.isEmpty()) continue;
            if (!first) out.append(',');
            out.append('"')
                .append(
                    field.replace("\\", "\\\\")
                        .replace("\"", "\\\""))
                .append('"');
            first = false;
        }
        out.append(']');
        return out.toString();
    }

    @Desugar
    public record DeletedEntity(Object id, Integer version) {}
}
