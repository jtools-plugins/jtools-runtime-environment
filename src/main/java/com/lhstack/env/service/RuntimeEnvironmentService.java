package com.lhstack.env.service;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runtime environment persistence backed directly by SQLite JDBC.
 *
 * MyBatis-Plus is intentionally not used here. The plugin owns a small fixed
 * schema, so direct SQL keeps the database boundary explicit and avoids ORM
 * lambda metadata/reflection issues on newer JDKs.
 */
public class RuntimeEnvironmentService {

    private static volatile HikariDataSource dataSource;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean destroyed = new AtomicBoolean(false);
    private static final Object lock = new Object();

    private final Connection connection;

    private RuntimeEnvironmentService(Connection connection) {
        this.connection = connection;
    }

    public static void init() {
        if (!initialized.compareAndSet(false, true)) return;
        synchronized (lock) {
            try {
                destroyed.set(false);
                initDataSource();
                initializeSchema();
            } catch (Throwable error) {
                initialized.set(false);
                closeDataSource();
                throw initializationFailure(error);
            }
        }
    }

    private static IllegalStateException initializationFailure(Throwable error) {
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) detail = error.getClass().getName();
        return new IllegalStateException("Failed to initialize RuntimeEnvironmentService: " + detail, error);
    }

    private static void initDataSource() {
        dataSource = new HikariDataSource();
        File directory = new File(System.getProperty("user.home"), ".jtools/jtools-runtime-environment");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建数据库目录: " + directory);
        }
        String dbPath = new File(directory, "data.db").getAbsolutePath().replace('\\', '/');
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setJdbcUrl("jdbc:sqlite:" + dbPath);
        Properties properties = new Properties();
        properties.setProperty("busy_timeout", "5000");
        dataSource.setDataSourceProperties(properties);
        dataSource.setAutoCommit(false);
        dataSource.setMinimumIdle(1);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMaxLifetime(60000);
        dataSource.setIdleTimeout(30000);
    }

    private static void initializeSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS runtime_environment(" +
                    "id INTEGER PRIMARY KEY NOT NULL," +
                    "project_hash TEXT NOT NULL," +
                    "project_path TEXT NOT NULL," +
                    "project_name TEXT NOT NULL," +
                    "module TEXT NOT NULL," +
                    "name TEXT NOT NULL," +
                    "remark TEXT," +
                    "args_value TEXT," +
                    "env_value TEXT," +
                    "vm_value TEXT," +
                    "is_default INTEGER," +
                    "created DATETIME NOT NULL," +
                    "updated DATETIME NOT NULL" +
                    ")");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS i_p_m ON runtime_environment(project_hash, module)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS runtime_environment_active(" +
                    "id INTEGER PRIMARY KEY NOT NULL," +
                    "project_hash TEXT," +
                    "module TEXT," +
                    "enabled INTEGER NOT NULL DEFAULT 0," +
                    "created DATETIME NOT NULL," +
                    "updated DATETIME NOT NULL," +
                    "env_id INTEGER" +
                    ")");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS i_p_m_2 ON runtime_environment_active(project_hash, module)");
            connection.commit();
        }
    }

    public static void getService(Consumer<RuntimeEnvironmentService> consumer) {
        withConnection(service -> {
            consumer.accept(service);
            return null;
        });
    }

    public static <T> T execute(Function<RuntimeEnvironmentService, T> function) {
        return withConnection(function);
    }

    private static <T> T withConnection(Function<RuntimeEnvironmentService, T> function) {
        if (!initialized.get() || destroyed.get() || dataSource == null) return null;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            T result = function.apply(new RuntimeEnvironmentService(connection));
            connection.commit();
            return result;
        } catch (Throwable error) {
            throw new IllegalStateException("Runtime environment database operation failed", error);
        }
    }

    public void save(RuntimeEnvironment environment) {
        LocalDateTime now = LocalDateTime.now();
        environment.setCreated(now).setUpdated(now);
        String sqlWithId = "INSERT INTO runtime_environment(" + ENVIRONMENT_COLUMNS_WITH_ID + ") VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        String sqlWithoutId = "INSERT INTO runtime_environment(" + ENVIRONMENT_COLUMNS + ") VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = environment.getId() == null
                ? connection.prepareStatement(sqlWithoutId, Statement.RETURN_GENERATED_KEYS)
                : connection.prepareStatement(sqlWithId)) {
            int index = 1;
            if (environment.getId() != null) statement.setInt(index++, environment.getId());
            bindEnvironment(statement, environment, index);
            statement.executeUpdate();
            if (environment.getId() == null) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) environment.setId(keys.getInt(1));
                    else throw new IllegalStateException("插入环境后未获得 id");
                }
            }
        } catch (SQLException error) {
            throw sqlError("保存运行环境失败", error);
        }
    }

    public void updateById(RuntimeEnvironment environment) {
        if (environment.getId() == null) throw new IllegalArgumentException("更新运行环境缺少 id");
        environment.setUpdated(LocalDateTime.now());
        String sql = "UPDATE runtime_environment SET project_hash=?, project_path=?, project_name=?, module=?, name=?, " +
                "remark=?, args_value=?, env_value=?, vm_value=?, is_default=?, created=?, updated=? WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindEnvironment(statement, environment, 1);
            statement.setInt(13, environment.getId());
            requireUpdated(statement.executeUpdate(), "运行环境不存在: " + environment.getId());
        } catch (SQLException error) {
            throw sqlError("更新运行环境失败", error);
        }
    }

    public RuntimeEnvironment getById(Object rawId) {
        if (rawId == null) return null;
        String sql = "SELECT " + ENVIRONMENT_COLUMNS_WITH_ID + " FROM runtime_environment WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Integer.parseInt(String.valueOf(rawId)));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readEnvironment(result) : null;
            }
        } catch (SQLException error) {
            throw sqlError("读取运行环境失败", error);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("运行环境 id 不是有效整数: " + rawId, error);
        }
    }

    public List<RuntimeEnvironment> list() {
        List<RuntimeEnvironment> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + ENVIRONMENT_COLUMNS_WITH_ID + " FROM runtime_environment ORDER BY id")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readEnvironment(rows));
            }
            return result;
        } catch (SQLException error) {
            throw sqlError("读取运行环境列表失败", error);
        }
    }

    public long countByName(Project project, Module module, RuntimeEnvironment environment) {
        String sql = "SELECT COUNT(*) FROM runtime_environment WHERE project_hash=? AND module=? AND name=?" +
                (environment.getId() == null ? "" : " AND id<>?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, project.getLocationHash());
            statement.setString(2, module.toString());
            statement.setString(3, environment.getName());
            if (environment.getId() != null) statement.setInt(4, environment.getId());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException error) {
            throw sqlError("检查环境名称失败", error);
        }
    }

    public void removeBatchByIds(Collection<?> rawIds) {
        for (Object rawId : rawIds) removeById(rawId);
    }

    public void removeById(Object rawId) {
        RuntimeEnvironment environment = getById(rawId);
        if (environment != null) deleteById(environment.getId());
    }

    private void deleteById(Integer id) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM runtime_environment WHERE id=?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw sqlError("删除运行环境失败", error);
        }
    }

    public RuntimeEnvironment getGlobalEnvironment() {
        RuntimeEnvironment environment = getById(-1);
        if (environment == null) {
            environment = new RuntimeEnvironment().setId(-1).setName("Global").setModule("Global")
                    .setProjectPath("Global").setProjectName("Global").setProjectHash("Global").setIsDefault(0);
            save(environment);
        }
        return environment;
    }

    public boolean globalEnvironmentActive() {
        return getGlobalEnvironment().getIsDefault() == 1;
    }

    public void globalEnvironmentUpdateActive() {
        RuntimeEnvironment environment = getGlobalEnvironment();
        environment.setIsDefault(environment.getIsDefault() == 0 ? 1 : 0);
        updateById(environment);
    }

    public List<RuntimeEnvironment> getRuntimeEnvironments(Project project, Module module) {
        List<RuntimeEnvironment> result = new ArrayList<>();
        String sql = "SELECT " + ENVIRONMENT_COLUMNS_WITH_ID + " FROM runtime_environment WHERE project_hash=? AND module=? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, project.getLocationHash());
            statement.setString(2, module.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readEnvironment(rows));
            }
        } catch (SQLException error) {
            throw sqlError("读取模块环境失败", error);
        }
        if (result.isEmpty()) {
            result = RuntimeEnvironment.buildInitList(project, module);
            for (RuntimeEnvironment environment : result) save(environment);
        }
        return result;
    }

    public Boolean isActive(Project project, Module module) {
        RuntimeEnvironmentActive active = findActive(project.getLocationHash(), module.toString());
        return active != null && Integer.valueOf(1).equals(active.getEnabled());
    }

    public void updateActive(RuntimeEnvironment environment, boolean enabled) {
        RuntimeEnvironmentActive active = findActive(environment.getProjectHash(), environment.getModule());
        if (active == null) {
            active = new RuntimeEnvironmentActive().setProjectHash(environment.getProjectHash()).setModule(environment.getModule());
            active.setCreated(LocalDateTime.now());
            insertActive(active);
        }
        active.setEnabled(enabled ? 1 : 0).setEnvId(environment.getId()).setUpdated(LocalDateTime.now());
        updateActiveRow(active);
    }

    public Integer getSelectEnvId(Project project, Module module) {
        RuntimeEnvironmentActive active = findActive(project.getLocationHash(), module.toString());
        return active == null ? null : active.getEnvId();
    }

    public void updateSelectEnv(Integer runtimeEnvironmentId) {
        RuntimeEnvironment environment = getById(runtimeEnvironmentId);
        if (environment == null) throw new IllegalArgumentException("运行环境不存在: " + runtimeEnvironmentId);
        RuntimeEnvironmentActive active = findActive(environment.getProjectHash(), environment.getModule());
        if (active == null) {
            active = new RuntimeEnvironmentActive().setProjectHash(environment.getProjectHash()).setModule(environment.getModule())
                    .setEnvId(environment.getId()).setEnabled(0).setCreated(LocalDateTime.now()).setUpdated(LocalDateTime.now());
            insertActive(active);
        } else {
            active.setEnvId(environment.getId()).setUpdated(LocalDateTime.now());
            updateActiveRow(active);
        }
    }

    private RuntimeEnvironmentActive findActive(String projectHash, String module) {
        String sql = "SELECT id, project_hash, module, enabled, env_id, created, updated FROM runtime_environment_active WHERE project_hash=? AND module=? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectHash);
            statement.setString(2, module);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return readActive(result);
            }
        } catch (SQLException error) {
            throw sqlError("读取激活环境失败", error);
        }
    }

    private void insertActive(RuntimeEnvironmentActive active) {
        String sql = "INSERT INTO runtime_environment_active(project_hash,module,enabled,created,updated,env_id) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, active.getProjectHash());
            statement.setString(2, active.getModule());
            statement.setInt(3, active.getEnabled() == null ? 0 : active.getEnabled());
            statement.setString(4, active.getCreated().toString());
            statement.setString(5, active.getUpdated() == null ? LocalDateTime.now().toString() : active.getUpdated().toString());
            if (active.getEnvId() == null) statement.setObject(6, null); else statement.setInt(6, active.getEnvId());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) active.setId(keys.getInt(1));
            }
        } catch (SQLException error) {
            throw sqlError("保存激活环境失败", error);
        }
    }

    private void updateActiveRow(RuntimeEnvironmentActive active) {
        if (active.getId() == null) throw new IllegalArgumentException("激活环境缺少 id");
        String sql = "UPDATE runtime_environment_active SET project_hash=?, module=?, enabled=?, created=?, updated=?, env_id=? WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, active.getProjectHash());
            statement.setString(2, active.getModule());
            statement.setInt(3, active.getEnabled() == null ? 0 : active.getEnabled());
            statement.setString(4, active.getCreated().toString());
            statement.setString(5, active.getUpdated().toString());
            if (active.getEnvId() == null) statement.setObject(6, null); else statement.setInt(6, active.getEnvId());
            statement.setInt(7, active.getId());
            requireUpdated(statement.executeUpdate(), "激活环境不存在: " + active.getId());
        } catch (SQLException error) {
            throw sqlError("更新激活环境失败", error);
        }
    }

    private static void bindEnvironment(PreparedStatement statement, RuntimeEnvironment environment, int start) throws SQLException {
        int index = start;
        statement.setString(index++, environment.getProjectHash());
        statement.setString(index++, environment.getProjectPath());
        statement.setString(index++, environment.getProjectName());
        statement.setString(index++, environment.getModule());
        statement.setString(index++, environment.getName());
        statement.setString(index++, environment.getRemark());
        statement.setString(index++, environment.getArgsValue());
        statement.setString(index++, environment.getEnvValue());
        statement.setString(index++, environment.getVmValue());
        if (environment.getIsDefault() == null) statement.setObject(index++, null); else statement.setInt(index++, environment.getIsDefault());
        statement.setString(index++, environment.getCreated().toString());
        statement.setString(index, environment.getUpdated().toString());
    }

    private static RuntimeEnvironment readEnvironment(ResultSet result) throws SQLException {
        return new RuntimeEnvironment().setId(result.getInt("id"))
                .setProjectHash(result.getString("project_hash")).setProjectPath(result.getString("project_path"))
                .setProjectName(result.getString("project_name")).setModule(result.getString("module"))
                .setName(result.getString("name")).setRemark(result.getString("remark"))
                .setArgsValue(result.getString("args_value")).setEnvValue(result.getString("env_value"))
                .setVmValue(result.getString("vm_value")).setIsDefault(nullableInteger(result, "is_default"))
                .setCreated(parseTime(result.getString("created"))).setUpdated(parseTime(result.getString("updated")));
    }

    private static RuntimeEnvironmentActive readActive(ResultSet result) throws SQLException {
        return new RuntimeEnvironmentActive().setId(result.getInt("id")).setProjectHash(result.getString("project_hash"))
                .setModule(result.getString("module")).setEnabled(nullableInteger(result, "enabled"))
                .setEnvId(nullableInteger(result, "env_id")).setCreated(parseTime(result.getString("created")))
                .setUpdated(parseTime(result.getString("updated")));
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static LocalDateTime parseTime(String value) {
        return value == null ? null : LocalDateTime.parse(value.replace(' ', 'T'));
    }

    private static void requireUpdated(int count, String message) {
        if (count == 0) throw new IllegalStateException(message);
    }

    private static IllegalStateException sqlError(String message, SQLException error) {
        return new IllegalStateException(message, error);
    }

    public static void destroy() {
        if (!destroyed.compareAndSet(false, true)) return;
        synchronized (lock) {
            closeDataSource();
            initialized.set(false);
        }
    }

    private static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        dataSource = null;
    }

    private static final String ENVIRONMENT_COLUMNS =
            "project_hash,project_path,project_name,module,name,remark,args_value,env_value,vm_value,is_default,created,updated";
    private static final String ENVIRONMENT_COLUMNS_WITH_ID = "id," + ENVIRONMENT_COLUMNS;
}
