package com.pinkyudeer.tasket.db;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;

import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.helper.ModFileHelper;
import com.pinkyudeer.tasket.task.TaskSqlHelper;

/**
 * SQLite 数据库管理类。
 * 负责连接、执行 SQL 和关闭数据库。
 * 默认直接使用世界目录中的文件数据库，并通过 ThreadLocal 连接支持多线程读取。
 */
public class SQLiteManager {

    private static final String JDBC_SQLITE_PREFIX = "jdbc:sqlite:";
    private static final ThreadLocal<Connection> THREAD_CONNECTION = new ThreadLocal<>();
    private static final Set<Connection> OPEN_CONNECTIONS = Collections
        .synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static volatile String databaseUrl;
    public static boolean isWorldLoaded = false;

    @FunctionalInterface
    public interface ResultSetHandler<T> {

        T handle(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface TransactionalWork<T> {

        T execute() throws Exception;
    }

    /**
     * 初始化世界文件数据库。
     */
    public static void initSqlite() {
        closeConnections();
        File databaseFile = getDatabaseFile();
        boolean databaseExists = databaseFile.exists();
        try {
            ModFileHelper.ensureWorldDirExist();
            databaseUrl = JDBC_SQLITE_PREFIX + databaseFile.getAbsolutePath()
                .replace(File.separatorChar, '/');
            THREAD_CONNECTION.set(openConnection(databaseUrl, false));
        } catch (IOException | SQLException e) {
            Tasket.LOG.error("SQLite 初始化失败", e);
            return;
        }
        isWorldLoaded = true;
        if (!databaseExists) {
            initNewDataBase();
        } else {
            TaskSqlHelper.migrateSchema();
        }
        Tasket.LOG.info("SQLite 初始化完成");
    }

    /**
     * 初始化新数据库。
     * 创建必要的表结构并初始化基础数据。
     */
    private static void initNewDataBase() {
        Tasket.LOG.info("初始化新 SQLite 数据库");

        // 在这里添加初始化 SQL 语句
        TaskSqlHelper.initTaskDataBase();
    }

    /**
     * WAL 检查点。文件数据库是主存储，不再执行内存库备份。
     */
    public static void checkpoint() {
        if (!isWorldLoaded) return;
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            Tasket.LOG.debug("SQLite WAL checkpoint completed");
        } catch (SQLException e) {
            Tasket.LOG.error("SQLite WAL checkpoint 失败", e);
        }
    }

    /**
     * @deprecated 文件数据库已是主存储。保留该方法作为旧调用点的 WAL checkpoint 兼容层。
     */
    @Deprecated
    public static void saveDataFromMemoryToFile() {
        checkpoint();
    }

    /**
     * 关闭数据库连接。
     */
    public static void close() {
        checkpoint();
        Tasket.LOG.info("关闭 SQLite 连接");
        closeConnections();
        databaseUrl = null;
        isWorldLoaded = false;
    }

    /**
     * 执行无参数 SQL。
     *
     * @param sql    SQL 语句
     * @param params SQL 参数列表
     * @return 执行结果, 若为查询则返回已脱离连接的 ResultSet, 否则返回影响的行数
     * @deprecated 新代码请使用 {@link #query(String, ResultSetHandler, Object...)} 或
     *             {@link #executeUpdate(String, Object...)}，以确保资源生命周期清晰。
     */
    @Deprecated
    @SuppressWarnings("SqlSourceToSinkFlow")
    public static Object executeSafeSQL(String sql, Object... params) {
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            if (params.length > 0) {
                setParameters(ps, Arrays.asList(params));
            }
            Tasket.LOG.debug("执行 SQL: {}", ps.toString());
            boolean resultIsRs = ps.execute();
            if (resultIsRs) {
                try (ResultSet rs = ps.getResultSet()) {
                    CachedRowSet rowSet = RowSetProvider.newFactory()
                        .createCachedRowSet();
                    rowSet.populate(rs);
                    return rowSet;
                }
            }
            Tasket.LOG.debug("影响行数: {}", ps.getUpdateCount());
            return ps.getUpdateCount();
        } catch (SQLException e) {
            Tasket.LOG.error("执行 SQL 失败: {}", sql, e);
        }
        return null;
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public static int executeUpdate(String sql, Object... params) {
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            if (params.length > 0) {
                setParameters(ps, Arrays.asList(params));
            }
            Tasket.LOG.debug("执行 SQL: {}", ps.toString());
            int count = ps.executeUpdate();
            Tasket.LOG.debug("影响行数: {}", count);
            return count;
        } catch (SQLException e) {
            Tasket.LOG.error("执行 SQL 更新失败: {}", sql, e);
            return 0;
        }
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public static <T> T query(String sql, ResultSetHandler<T> handler, Object... params) {
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            if (params.length > 0) {
                setParameters(ps, Arrays.asList(params));
            }
            Tasket.LOG.debug("执行 SQL: {}", ps.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return handler.handle(rs);
            }
        } catch (SQLException e) {
            Tasket.LOG.error("执行 SQL 查询失败: {}", sql, e);
            return null;
        }
    }

    public static <T> T transaction(TransactionalWork<T> work) {
        if (work == null) throw new IllegalArgumentException("事务内容不能为空");
        try {
            Connection connection = getConnection();
            boolean oldAutoCommit = connection.getAutoCommit();
            if (!oldAutoCommit) {
                return work.execute();
            }

            connection.setAutoCommit(false);
            try {
                T result = work.execute();
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            Tasket.LOG.error("SQLite 事务失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 测试专用：注入调用方管理的连接。
     */
    public static void setConnectionForTesting(Connection connection) throws SQLException {
        closeConnections();
        databaseUrl = null;
        configureConnection(connection, true);
        THREAD_CONNECTION.set(connection);
        OPEN_CONNECTIONS.add(connection);
    }

    private static File getDatabaseFile() {
        return ModFileHelper.getWorldFile("main.db", false)
            .getAbsoluteFile();
    }

    private static Connection getConnection() throws SQLException {
        Connection connection = THREAD_CONNECTION.get();
        if (connection != null && !connection.isClosed()) {
            return connection;
        }
        if (databaseUrl == null) {
            throw new SQLException("SQLite 尚未初始化");
        }
        connection = openConnection(databaseUrl, false);
        THREAD_CONNECTION.set(connection);
        return connection;
    }

    private static Connection openConnection(String url, boolean inMemory) throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try {
            configureConnection(connection, inMemory);
            OPEN_CONNECTIONS.add(connection);
            return connection;
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    private static void configureConnection(Connection connection, boolean inMemory) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            if (!inMemory) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
        }
    }

    private static void closeConnections() {
        AsyncSqlExecutor.INSTANCE.shutdown();
        EntityEventRecorder.resetCache();
        synchronized (OPEN_CONNECTIONS) {
            for (Connection connection : OPEN_CONNECTIONS) {
                try {
                    if (connection != null && !connection.isClosed()) {
                        connection.close();
                    }
                } catch (SQLException e) {
                    Tasket.LOG.error("关闭连接失败", e);
                }
            }
            OPEN_CONNECTIONS.clear();
        }
        THREAD_CONNECTION.remove();
    }

    /**
     * 设置 PreparedStatement 参数。
     *
     * @param ps     PreparedStatement 实例
     * @param params 参数列表
     * @throws SQLException 当设置参数失败时抛出
     */
    private static void setParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        if (params != null && !params.isEmpty()) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }
    }
}
