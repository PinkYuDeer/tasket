package com.pinkyudeer.tasket.task;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;

import org.reflections.Reflections;

import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.db.EntityEventRecorder;
import com.pinkyudeer.tasket.db.SQLHelper;
import com.pinkyudeer.tasket.db.SQLiteManager;
import com.pinkyudeer.tasket.db.annotation.Table;
import com.pinkyudeer.tasket.task.dao.PlayerDao;
import com.pinkyudeer.tasket.task.entity.EntityEvent;

/**
 * 任务系统数据库操作助手类
 * 提供任务相关实体的CRUD操作
 */
public class TaskSqlHelper {

    /**
     * 初始化任务数据库
     * 扫描并创建所有任务相关的表
     */
    public static void initTaskDataBase() {
        Reflections reflections = new Reflections("com.pinkyudeer.tasket.task.entity");
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Table.class);
        try {
            SQLHelper.createTables(annotatedClasses);
        } catch (Exception e) {
            Tasket.LOG.error("初始化任务数据库失败", e);
            return;
        }
        Tasket.LOG.info("初始化任务数据库，共创建 {} 张表", annotatedClasses.size());

        migrateSchema();
    }

    public static void migrateSchema() {
        ensureSchemaVersionTable();
        Set<Integer> appliedIds = loadAppliedMigrationIds();
        applyMigration(
            appliedIds,
            1,
            "tasks.parent_task_id",
            () -> addColumnIfNotExists("tasks", "parent_task_id", "TEXT"));
        applyMigration(
            appliedIds,
            2,
            "teams.sync_source",
            () -> addColumnIfNotExists("teams", "sync_source", "TEXT DEFAULT 'LOCAL'"));
        applyMigration(
            appliedIds,
            3,
            "teams.external_party_id",
            () -> addColumnIfNotExists("teams", "external_party_id", "INTEGER DEFAULT -1"));
        applyMigration(
            appliedIds,
            4,
            "teams.sync_status",
            () -> addColumnIfNotExists("teams", "sync_status", "TEXT DEFAULT 'ACTIVE'"));
        applyMigration(
            appliedIds,
            5,
            "teams.last_sync_time",
            () -> addColumnIfNotExists("teams", "last_sync_time", "TIMESTAMP"));
        applyMigration(
            appliedIds,
            6,
            "teams.external_team_key",
            () -> addColumnIfNotExists("teams", "external_team_key", "TEXT"));
        applyMigration(
            appliedIds,
            7,
            "tags.owner_team_id",
            () -> addColumnIfNotExists("tags", "owner_team_id", "TEXT"));
        applyMigration(appliedIds, 8, "tasks.assignee_id", () -> addColumnIfNotExists("tasks", "assignee_id", "TEXT"));
        applyMigration(
            appliedIds,
            9,
            "tasks.status.UnStarted_to_Claimed",
            () -> SQLiteManager.executeUpdate("UPDATE tasks SET status = 'Claimed' WHERE status = 'UnStarted'"));
        applyMigration(appliedIds, 10, "entity_tables.version", TaskSqlHelper::addVersionColumns);
        applyMigration(appliedIds, 11, "entity_events", () -> {
            SQLHelper.createTable(EntityEvent.class);
            EntityEventRecorder.markEventTableReady();
        });
        applyMigration(appliedIds, 12, "sync_query_indices", TaskSqlHelper::addSyncQueryIndices);
        if (tableExists("entity_events")) EntityEventRecorder.markEventTableReady();
    }

    private static void addVersionColumns() {
        Reflections reflections = new Reflections("com.pinkyudeer.tasket.task.entity");
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Table.class);
        for (Class<?> entityClass : annotatedClasses) {
            Table table = entityClass.getAnnotation(Table.class);
            if (table == null) continue;
            if (!tableExists(table.name())) continue;
            addColumnIfNotExists(table.name(), "version", "INTEGER NOT NULL DEFAULT 1");
            SQLiteManager
                .executeUpdate("UPDATE " + table.name() + " SET version = 1 WHERE version IS NULL OR version < 1");
        }
    }

    private static void addColumnIfNotExists(String table, String column, String type) {
        try {
            if (columnExists(table, column)) {
                return;
            }
            SQLiteManager.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            Tasket.LOG.info("已迁移: {} 表添加列 {}", table, column);
        } catch (Exception e) {
            Tasket.LOG.error("数据库迁移失败: {}.{}", table, column, e);
            throw new RuntimeException(e);
        }
    }

    private static void addSyncQueryIndices() {
        createIndex("idx_tasks_status", "tasks", "status");
        createIndex("idx_tasks_team_id", "tasks", "team_id");
        createIndex("idx_tasks_creator", "tasks", "creator");
        createIndex("idx_tag_links_tag_id", "tag_links", "tag_id");
        createIndex("idx_team_members_team_id", "team_members", "team_id");
        createIndex("idx_team_members_player_status", "team_members", "player_id, status");
    }

    private static void createIndex(String indexName, String tableName, String columns) {
        SQLiteManager
            .executeUpdate("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + " (" + columns + ")");
    }

    private static void ensureSchemaVersionTable() {
        SQLiteManager.executeUpdate(
            "CREATE TABLE IF NOT EXISTS schema_version (" + "id INTEGER PRIMARY KEY, "
                + "description TEXT NOT NULL, "
                + "applied_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")");
    }

    private static void applyMigration(Set<Integer> appliedIds, int id, String description, Runnable migration) {
        if (appliedIds.contains(id)) return;
        SQLiteManager.transaction(() -> {
            migration.run();
            SQLiteManager.executeUpdate("INSERT INTO schema_version (id, description) VALUES (?, ?)", id, description);
            Tasket.LOG.info("数据库迁移完成: {} {}", id, description);
            return null;
        });
        appliedIds.add(id);
    }

    private static Set<Integer> loadAppliedMigrationIds() {
        Set<Integer> ids = SQLiteManager.query("SELECT id FROM schema_version", rs -> {
            Set<Integer> result = new HashSet<>();
            while (rs.next()) result.add(rs.getInt(1));
            return result;
        });
        return ids == null ? new HashSet<>() : ids;
    }

    private static boolean columnExists(String table, String column) {
        Boolean exists = SQLiteManager.query("PRAGMA table_info(" + table + ")", rs -> {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) return true;
            }
            return false;
        });
        return Boolean.TRUE.equals(exists);
    }

    private static boolean tableExists(String table) {
        Boolean exists = SQLiteManager
            .query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1", rs -> rs.next(), table);
        return Boolean.TRUE.equals(exists);
    }

    public static class player {

        public static void login(EntityPlayer player) {
            PlayerDao.updateOrInsert(player);
        }
    }
}
